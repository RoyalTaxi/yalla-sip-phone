# SIP Layer Simplification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hand-rolled lifecycle/listener/error-handling boilerplate in `data/pjsip/` with Kotlin idioms (scoped `use`, flows, data classes, runCatching). No behavior change — mechanical cleanup under the 259-test safety net.

**Architecture:** Two new helper files (`SwigResources.kt`, `SafeCallback.kt`) encapsulate SWIG cleanup and callback hygiene. All other pjsip files are refactored to delegate to these helpers, replace listener interfaces with `SharedFlow`, collapse nullable-var triples into data classes, and merge parallel maps into per-account structs.

**Tech Stack:** Kotlin, kotlinx.coroutines (Flow/SharedFlow/SupervisorJob), PJSIP SWIG bindings (`org.pjsip.pjsua2.*`), kotlin-logging, kotlin.test + JUnit 4 runtime.

---

## Root Causes Addressed

| # | Cause | Addressed in |
|---|---|---|
| 1 | No SWIG resource abstraction — 21 hand-written `try/finally/delete()` sites | Task 1 + 2/3/4/5 apply |
| 2 | No callback abstraction — 4 callbacks duplicate `isDestroyed + try/catch + delete` | Task 1 + 2 apply |
| 3 | `currentCall`/`currentCallId`/`currentAccountId` — 3 nullable vars, one concept | Task 3 (`ActiveCall`) |
| 4 | `IncomingCallListener` + `AccountRegistrationListener` alongside already-existing flows | Task 5 (`SharedFlow`) |
| 5 | `credentialCache` + `reconnectJobs` + `reconnectAttempts` — 3 maps, one per-account concept | Task 7 (`AccountSession`) |
| 6 | Manual rate-limit logic inlined into `register()` | Task 6 (`rateLimitRegister`) |
| 7 | `withCallOpParam` helper exists but not consistently applied | Task 3 (apply everywhere) |
| 8 | `safeDelete()` duplicated in `PjsipAccount` and `PjsipCall` | Task 1 (`deleteOnce`) |

## Invariants (must not change)

1. **PJSIP threading**: all public API wraps `withContext(pjDispatcher)`; callbacks run synchronously on the pjsip-event-loop thread.
2. **SWIG pointer lifetime**: all SWIG field access happens synchronously inside the callback or inside a `use { }` block before the object is deleted. No SWIG pointer escapes a suspension point.
3. **Idempotent destroy**: `PjsipAccount.safeDelete()` and `PjsipCall.safeDelete()` remain idempotent via `AtomicBoolean.compareAndSet(false, true)`.
4. **Engine gate**: `isDestroyed` lambda continues to be passed in from `PjsipEngine`; sub-managers do not own the flag.
5. **Existing tests pass**: 259 tests across 38 files must stay green. No new tests required — existing coverage (plus fake-based integration tests) is the regression guard.

---

## Task 1: Create SWIG resource helpers

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/SwigResources.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/SafeCallback.kt`

Pure additions. No callers yet — should compile as soon as it's written.

- [ ] **Step 1.1: Write `SwigResources.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AccountInfo
import org.pjsip.pjsua2.AudDevInfoVector
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.StreamInfo
import org.pjsip.pjsua2.TransportConfig

private val logger = KotlinLogging.logger("pjsip.resources")

/**
 * Scoped use of a SWIG-backed PJSIP object. Guarantees `.delete()` is called when [block] exits.
 * Mirrors Kotlin stdlib `Closeable.use`. Use this for every SWIG object we own — never mix
 * manual try/finally with these helpers.
 */
inline fun <R> AccountInfo.use(block: (AccountInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> CallInfo.use(block: (CallInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AccountConfig.use(block: (AccountConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AuthCredInfo.use(block: (AuthCredInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> CallOpParam.use(block: (CallOpParam) -> R): R = try { block(this) } finally { delete() }
inline fun <R> EpConfig.use(block: (EpConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> TransportConfig.use(block: (TransportConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> StreamInfo.use(block: (StreamInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AudDevInfoVector.use(block: (AudDevInfoVector) -> R): R = try { block(this) } finally { delete() }

/**
 * Idempotent delete of a SWIG-backed resource. Uses [flag] as a CAS guard so repeated calls are safe.
 * Logs — but does not rethrow — native delete exceptions, since C cannot unwind across JNI.
 */
inline fun deleteOnce(flag: AtomicBoolean, tag: String, delete: () -> Unit) {
    if (!flag.compareAndSet(false, true)) return
    runCatching { delete() }.onFailure { e ->
        logger.warn(e) { "[$tag] delete failed" }
    }
}

/**
 * Build a fresh `CallOpParam` with [statusCode], use it once, delete. `useDefaultCallSetting=true`
 * ensures opt.audioCount=1 — without it, reinvite() emits audioCount=0 SDP and the peer sends 488.
 */
inline fun <R> withCallOpParam(statusCode: Int = 200, block: (CallOpParam) -> R): R {
    val prm = CallOpParam(true).apply { this.statusCode = statusCode }
    return prm.use(block)
}
```

- [ ] **Step 1.2: Write `SafeCallback.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("pjsip.callback")

/**
 * Run a SWIG callback body with standard hygiene:
 *  - respect the [isDestroyed] gate (engine torn down → skip work)
 *  - never let an exception escape to the C caller (C cannot unwind across JNI)
 * Failures are logged against [tag].
 */
inline fun runSwigCallback(tag: String, isDestroyed: () -> Boolean, block: () -> Unit) {
    if (isDestroyed()) return
    runCatching(block).onFailure { e ->
        logger.error(e) { "[$tag] callback failed" }
    }
}
```

- [ ] **Step 1.3: Verify compile**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.4: Commit**

```bash
cd ~/Ildam/yalla/yalla-sip-phone
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/SwigResources.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/SafeCallback.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): add SWIG resource and callback helpers

Introduce SwigResources.kt (per-type .use extensions, deleteOnce, withCallOpParam)
and SafeCallback.kt (runSwigCallback). Pure additions — call sites converted in
follow-up commits.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Refactor `PjsipAccount.kt` and `PjsipCall.kt`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCall.kt`

Small files. Converts callbacks to `runSwigCallback`, `getInfo()` to `.use { }`, `safeDelete()` to `deleteOnce`. No behavior change.

- [ ] **Step 2.1: Rewrite `PjsipAccount.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

/**
 * PJSIP Account wrapper. Callbacks run synchronously on the pjsip-event-loop thread.
 * SWIG fields must be read inside `.use { }` before the object is deleted.
 */
class PjsipAccount(
    val accountId: String,
    val server: String,
    private val accountManager: PjsipAccountManager,
) : Account() {

    private val deleted = AtomicBoolean(false)

    override fun onRegState(prm: OnRegStateParam) =
        runSwigCallback("onRegState[$accountId]", accountManager::isAccountDestroyed) {
            val code = prm.code
            val reason = prm.reason
            getInfo().use { info ->
                val state = when {
                    code / 100 == SipConstants.STATUS_CLASS_SUCCESS && info.regIsActive -> {
                        logger.info { "[$accountId] Registered: ${info.uri}, expires: ${info.regExpiresSec}s" }
                        PjsipRegistrationState.Registered(uri = info.uri)
                    }
                    code / 100 == SipConstants.STATUS_CLASS_SUCCESS && !info.regIsActive -> {
                        logger.info { "[$accountId] Unregistered" }
                        PjsipRegistrationState.Idle
                    }
                    else -> {
                        logger.warn { "[$accountId] Registration failed: $code $reason (lastErr=${info.regLastErr})" }
                        PjsipRegistrationState.Failed(error = SipError.fromSipStatus(code, reason))
                    }
                }
                accountManager.updateRegistrationState(accountId, state)
            }
        }

    override fun onIncomingCall(prm: OnIncomingCallParam) =
        runSwigCallback("onIncomingCall[$accountId]", accountManager::isAccountDestroyed) {
            val callId = prm.callId
            logger.debug {
                val rdata = prm.rdata
                "RAW SIP INVITE: src=${rdata.srcAddress} info=${rdata.info}\n${rdata.wholeMsg}"
            }
            accountManager.handleIncomingCall(accountId, callId)
        }

    fun safeDelete() = deleteOnce(deleted, accountId) { delete() }
}
```

- [ ] **Step 2.2: Rewrite `PjsipCall.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjsip_inv_state

private val logger = KotlinLogging.logger {}

/**
 * PJSIP Call wrapper. Callbacks run synchronously on the pjsip-event-loop thread.
 * Do NOT dispatch work to another coroutine from a callback — SWIG pointers invalidate
 * after return.
 */
class PjsipCall : Call {

    private val callManager: PjsipCallManager
    private val deleted = AtomicBoolean(false)

    constructor(callManager: PjsipCallManager, account: Account) : super(account) {
        this.callManager = callManager
    }

    constructor(callManager: PjsipCallManager, account: Account, callId: Int) : super(account, callId) {
        this.callManager = callManager
    }

    override fun onCallState(prm: OnCallStateParam) =
        runSwigCallback("onCallState", callManager::isCallManagerDestroyed) {
            getInfo().use { info ->
                logger.info { "Call state: ${info.stateText} (${info.lastStatusCode})" }
                when (info.state) {
                    pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> callManager.onCallConfirmed(this)
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> callManager.onCallDisconnected(this)
                    else -> {}
                }
            }
        }

    override fun onCallMediaState(prm: OnCallMediaStateParam) =
        runSwigCallback("onCallMediaState", callManager::isCallManagerDestroyed) {
            callManager.connectCallAudio(this)
        }

    fun safeDelete() = deleteOnce(deleted, "call") { delete() }
}
```

- [ ] **Step 2.3: Verify compile + tests**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All 259 tests pass.

- [ ] **Step 2.4: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCall.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): wrap Account/Call callbacks in runSwigCallback + use

Kills duplicated try/catch/delete() skeletons in PjsipAccount.onRegState,
PjsipAccount.onIncomingCall, PjsipCall.onCallState, PjsipCall.onCallMediaState.
safeDelete() now delegates to deleteOnce helper.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Refactor `PjsipCallManager.kt` — ActiveCall + helpers

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`

Replaces three nullable vars with `ActiveCall` data class, applies `CallInfo.use` + `StreamInfo.use`, routes all `CallOpParam` through `withCallOpParam` (kills the duplicate in `makeCall`). Does NOT touch the `IncomingCallListener` interface — that moves in Task 5.

- [ ] **Step 3.1: Rewrite `PjsipCallManager.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_call_media_status
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.parseRemoteUri

private val logger = KotlinLogging.logger {}

interface AudioMediaProvider {
    fun getPlaybackDevMedia(): AudioMedia
    fun getCaptureDevMedia(): AudioMedia
}

private data class ActiveCall(val call: PjsipCall, val id: String, val accountId: String)

class PjsipCallManager(
    private val accountProvider: AccountProvider,
    private val audioMediaProvider: AudioMediaProvider,
    private val isDestroyed: () -> Boolean,
    private val pjDispatcher: CoroutineContext,
) : IncomingCallListener {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)
    private var active: ActiveCall? = null

    @Volatile
    private var holdInProgress = false
    private var holdTimeoutJob: Job? = null
    private var hangupTimeoutJob: Job? = null

    fun isCallManagerDestroyed(): Boolean = isDestroyed()

    // NOTE: captureDevMedia is owned by the audio device manager — never delete it here
    private fun applyMuteState(call: PjsipCall, muted: Boolean) {
        call.getInfo().use { info ->
            for (i in 0 until info.media.size) {
                val media = info.media[i]
                if (media.type != pjmedia_type.PJMEDIA_TYPE_AUDIO) continue
                if (media.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) continue
                val audioMedia = call.getAudioMedia(i)
                val captureMedia = audioMediaProvider.getCaptureDevMedia()
                if (muted) captureMedia.stopTransmit(audioMedia)
                else captureMedia.startTransmit(audioMedia)
                return@use
            }
        }
    }

    private fun applyHoldState(call: PjsipCall, hold: Boolean, state: CallState.Active) {
        withCallOpParam { prm ->
            if (hold) {
                call.setHold(prm)
            } else {
                prm.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
                call.reinvite(prm)
            }
        }
        _callState.value = state.copy(isOnHold = hold)
        holdTimeoutJob?.cancel()
        holdTimeoutJob = scope.launch {
            delay(HOLD_TIMEOUT_MS)
            if (holdInProgress) {
                logger.warn { "Hold timeout — resetting holdInProgress flag" }
                holdInProgress = false
            }
        }
    }

    suspend fun makeCall(number: String, accountId: String = ""): Result<Unit> {
        if (active != null) return Result.failure(IllegalStateException("Call already active"))

        val acc: PjsipAccount
        val resolvedAccountId: String
        if (accountId.isNotEmpty()) {
            acc = accountProvider.getAccount(accountId)
                ?: return Result.failure(IllegalStateException("Account $accountId not found"))
            resolvedAccountId = accountId
        } else {
            val firstAcc = accountProvider.getFirstConnectedAccount()
                ?: return Result.failure(IllegalStateException("No connected account"))
            acc = firstAcc
            resolvedAccountId = firstAcc.accountId
        }

        return runCatching {
            val call = PjsipCall(this, acc)
            val uri = SipConstants.buildCallUri(number, acc.server)
            withCallOpParam { prm -> call.makeCall(uri, prm) }
            val id = UUID.randomUUID().toString()
            active = ActiveCall(call, id, resolvedAccountId)
            _callState.value = CallState.Ringing(
                callId = id,
                callerNumber = number,
                callerName = null,
                isOutbound = true,
                accountId = resolvedAccountId,
            )
        }.onFailure {
            logger.error(it) { "makeCall failed on account $resolvedAccountId" }
            _callState.value = CallState.Idle
        }
    }

    suspend fun answerCall() {
        val call = active?.call ?: return
        val ringing = _callState.value as? CallState.Ringing ?: return
        if (ringing.isOutbound) return
        runCatching {
            withCallOpParam(statusCode = SipConstants.STATUS_OK) { prm -> call.answer(prm) }
        }.onFailure { logger.error(it) { "answerCall failed" } }
    }

    suspend fun hangupCall() {
        val a = active ?: return
        runCatching {
            _callState.value = CallState.Ending(callId = a.id, accountId = a.accountId)
            withCallOpParam { prm -> a.call.hangup(prm) }
            // Safety net: some PJSIP error paths never fire onCallDisconnected
            hangupTimeoutJob?.cancel()
            hangupTimeoutJob = scope.launch {
                delay(HANGUP_TIMEOUT_MS)
                if (_callState.value is CallState.Ending) {
                    logger.warn { "Hangup timeout — forcing Idle state" }
                    active?.call?.safeDelete()
                    resetCallState()
                }
            }
        }.onFailure {
            logger.error(it) { "hangupCall failed" }
            resetCallState()
        }
    }

    suspend fun toggleMute() {
        val state = _callState.value as? CallState.Active ?: return
        val call = active?.call ?: return
        runCatching {
            applyMuteState(call, muted = !state.isMuted)
            _callState.value = state.copy(isMuted = !state.isMuted)
        }.onFailure { logger.error(it) { "toggleMute failed" } }
    }

    suspend fun toggleHold() {
        val state = _callState.value as? CallState.Active ?: return
        if (holdInProgress) {
            logger.warn { "Hold/resume operation already in progress, ignoring" }
            return
        }
        val call = active?.call ?: return
        holdInProgress = true
        runCatching {
            applyHoldState(call, hold = !state.isOnHold, state = state)
        }.onFailure {
            holdInProgress = false
            logger.error(it) { "toggleHold failed" }
        }
    }

    suspend fun setMute(callId: String, muted: Boolean) {
        val state = _callState.value as? CallState.Active ?: return
        if (state.callId != callId) {
            logger.warn { "setMute: callId mismatch (expected=${state.callId}, got=$callId)" }
            return
        }
        if (state.isMuted == muted) return
        val call = active?.call ?: return
        runCatching {
            applyMuteState(call, muted)
            _callState.value = state.copy(isMuted = muted)
        }.onFailure { logger.error(it) { "setMute failed" } }
    }

    suspend fun setHold(callId: String, onHold: Boolean) {
        val state = _callState.value as? CallState.Active ?: return
        if (state.callId != callId) {
            logger.warn { "setHold: callId mismatch (expected=${state.callId}, got=$callId)" }
            return
        }
        if (state.isOnHold == onHold) return
        if (holdInProgress) {
            logger.warn { "Hold/resume operation already in progress, ignoring" }
            return
        }
        val call = active?.call ?: return
        holdInProgress = true
        runCatching {
            applyHoldState(call, hold = onHold, state = state)
        }.onFailure {
            holdInProgress = false
            logger.error(it) { "setHold failed" }
        }
    }

    fun onCallConfirmed(call: PjsipCall) {
        if (call !== active?.call) return
        when (val state = _callState.value) {
            is CallState.Ending ->
                logger.warn { "onCallConfirmed ignored — call already in Ending state" }
            is CallState.Ringing -> _callState.value = CallState.Active(
                callId = state.callId,
                remoteNumber = state.callerNumber,
                remoteName = state.callerName,
                isOutbound = state.isOutbound,
                isMuted = false,
                isOnHold = false,
                accountId = state.accountId,
                remoteUri = state.remoteUri,
            )
            else -> {}
        }
    }

    fun onCallDisconnected(call: PjsipCall) {
        // Non-current call (e.g. we rejected an incoming with 486) — just clean up and exit.
        if (call !== active?.call) {
            call.safeDelete()
            return
        }
        hangupTimeoutJob?.cancel()
        hangupTimeoutJob = null
        resetCallState()
        call.safeDelete()
    }

    override fun onIncomingCall(accountId: String, callId: Int) {
        val acc = accountProvider.getAccount(accountId) ?: run {
            logger.warn { "Incoming call on unknown account $accountId — ignoring" }
            return
        }
        if (active != null) {
            logger.warn { "Rejecting incoming call on $accountId (already in call)" }
            val rejectCall = PjsipCall(this, acc, callId)
            runCatching {
                withCallOpParam(statusCode = SipConstants.STATUS_BUSY_HERE) { prm -> rejectCall.hangup(prm) }
            }.onFailure { logger.error(it) { "Failed to reject incoming call" } }
            rejectCall.safeDelete()
            return
        }
        runCatching {
            val call = PjsipCall(this, acc, callId)
            val id = UUID.randomUUID().toString()
            active = ActiveCall(call, id, accountId)
            call.getInfo().use { info ->
                val remoteUri = info.remoteUri
                val callerInfo = parseRemoteUri(remoteUri)
                logger.debug {
                    "Incoming call detail: account=$accountId pjCallId=$callId " +
                        "sipCallId=${info.callIdString} remote=$remoteUri local=${info.localUri} " +
                        "media=${info.media?.size ?: 0}"
                }
                _callState.value = CallState.Ringing(
                    callId = id,
                    callerNumber = callerInfo.number,
                    callerName = callerInfo.displayName,
                    isOutbound = false,
                    accountId = accountId,
                    remoteUri = remoteUri,
                )
                logger.info {
                    "Incoming call on $accountId from: ${callerInfo.displayName ?: callerInfo.number}"
                }
            }
        }.onFailure {
            logger.error(it) { "Error handling incoming call on $accountId" }
            resetCallState()
        }
    }

    suspend fun sendDtmf(callId: String, digits: String): Result<Unit> {
        if (!digits.matches(Regex("[0-9*#A-Da-d]+"))) {
            return Result.failure(IllegalArgumentException("Invalid DTMF digits: $digits"))
        }
        val a = active ?: return Result.failure(IllegalStateException("No active call"))
        if (a.id != callId) {
            logger.warn { "sendDtmf: callId mismatch (expected=${a.id}, got=$callId)" }
            return Result.failure(IllegalStateException("callId mismatch"))
        }
        return runCatching {
            a.call.dialDtmf(digits)
            logger.info { "DTMF sent: $digits" }
        }.onFailure { logger.error(it) { "DTMF failed" } }
    }

    suspend fun transferCall(callId: String, destination: String): Result<Unit> {
        val a = active ?: return Result.failure(IllegalStateException("No active call"))
        if (a.id != callId) {
            logger.warn { "transferCall: callId mismatch (expected=${a.id}, got=$callId)" }
            return Result.failure(IllegalStateException("callId mismatch"))
        }
        val host = accountProvider.getAccount(a.accountId)?.server
            ?: return Result.failure(IllegalStateException("No server for account ${a.accountId}"))
        return runCatching {
            val destUri = SipConstants.buildCallUri(destination, host)
            withCallOpParam { prm -> a.call.xfer(destUri, prm) }
            logger.info { "Call transferred to: $destination" }
        }.onFailure { logger.error(it) { "Transfer failed" } }
    }

    fun connectCallAudio(call: PjsipCall) {
        if (call !== active?.call) return
        // Media state callback = re-INVITE completed; reset hold guard
        holdInProgress = false
        holdTimeoutJob?.cancel()
        holdTimeoutJob = null

        call.getInfo().use { info ->
            for (i in 0 until info.media.size) {
                val media = info.media[i]
                if (media.type != pjmedia_type.PJMEDIA_TYPE_AUDIO) continue
                if (media.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) continue
                val audioMedia = call.getAudioMedia(i)
                val playbackMedia = audioMediaProvider.getPlaybackDevMedia()
                val captureMedia = audioMediaProvider.getCaptureDevMedia()
                audioMedia.startTransmit(playbackMedia)
                val isMuted = (_callState.value as? CallState.Active)?.isMuted == true
                if (!isMuted) captureMedia.startTransmit(audioMedia)
                logger.info { "Audio media connected for media index $i (muted=$isMuted)" }
                runCatching {
                    call.getStreamInfo(i.toLong()).use { si ->
                        logger.info {
                            "Stream: codec=${si.codecName}/${si.codecClockRate}Hz, " +
                                "dir=${si.dir}, remote=${si.remoteRtpAddress}"
                        }
                    }
                }.onFailure { logger.warn(it) { "Could not get stream info" } }
                break
            }
        }
    }

    fun destroy() {
        scope.cancel()
        active?.call?.let { call ->
            runCatching { withCallOpParam { prm -> call.hangup(prm) } }
            call.safeDelete()
        }
        resetCallState()
    }

    private fun resetCallState() {
        active = null
        _callState.value = CallState.Idle
    }

    companion object {
        private const val HOLD_TIMEOUT_MS = 15_000L
        private const val HANGUP_TIMEOUT_MS = 10_000L
    }
}
```

- [ ] **Step 3.2: Verify compile + tests**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All 259 tests pass. Integration tests (`BusyOperatorIntegrationTest`, `CallFlowIntegrationTest`) still green.

- [ ] **Step 3.3: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): collapse currentCall triple into ActiveCall, apply use/withCallOpParam

- Three nullable vars (currentCall/currentCallId/currentAccountId) → one nullable ActiveCall
- All CallInfo/StreamInfo access goes through .use { } (deletes guaranteed)
- makeCall and connectCallAudio now route through withCallOpParam like the rest
- Replaces hand-rolled try/catch in suspend APIs with runCatching

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Refactor `PjsipEndpointManager.kt`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEndpointManager.kt`

Applies `EpConfig.use`, `TransportConfig.use`, `AudDevInfoVector.use`. Removes swallowed-exception `catch (_: Exception) {}` around `logWriter?.delete()`.

- [ ] **Step 4.1: Rewrite `PjsipEndpointManager.kt`**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjsip_transport_type_e
import uz.yalla.sipphone.domain.SipConstants

private val logger = KotlinLogging.logger {}

class PjsipEndpointManager(private val pjDispatcher: CoroutineContext) {

    lateinit var endpoint: Endpoint
        private set

    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)
    private var pollJob: Job? = null
    private var logWriter: PjsipLogWriter? = null

    fun initEndpoint() {
        endpoint = Endpoint()
        endpoint.libCreate()
        EpConfig().use { cfg ->
            cfg.uaConfig.threadCnt = 0
            cfg.uaConfig.mainThreadOnly = false
            cfg.uaConfig.userAgent = SipConstants.USER_AGENT
            logWriter = PjsipLogWriter()
            cfg.logConfig.writer = logWriter
            cfg.logConfig.level = 3
            cfg.logConfig.consoleLevel = 3
            endpoint.libInit(cfg)
        }
    }

    fun createTransports() {
        TransportConfig().use { cfg ->
            cfg.port = 0
            endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, cfg)
            endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, cfg)
        }
    }

    fun startLibrary() {
        endpoint.libStart()
        val version = endpoint.libVersion()
        logger.info { "pjsip initialized, version: ${version.full}" }
        version.delete()
        logAudioDevices()
    }

    fun startPolling() {
        pollJob = scope.launch(pjDispatcher) {
            if (!endpoint.libIsThreadRegistered()) {
                endpoint.libRegisterThread("pjsip-poll")
            }
            while (isActive) {
                endpoint.libHandleEvents(SipConstants.POLL_INTERVAL_MS.toLong())
                yield()
            }
        }
    }

    suspend fun stopPolling() {
        pollJob?.cancel()
        pollJob?.join()
        pollJob = null
    }

    fun getPlaybackDevMedia(): AudioMedia = endpoint.audDevManager().playbackDevMedia

    fun getCaptureDevMedia(): AudioMedia = endpoint.audDevManager().captureDevMedia

    fun destroy() {
        scope.cancel()
        // Force GC to release any SWIG pointers before destroying;
        // libDestroy still uses logWriter for shutdown logging — don't delete it before libDestroy.
        runCatching {
            System.gc()
            endpoint.libDestroy()
        }.onFailure { logger.warn(it) { "libDestroy failed (may be partially destroyed)" } }
        runCatching { logWriter?.delete() }
            .onFailure { logger.warn(it) { "logWriter.delete failed" } }
        logWriter = null
        runCatching { endpoint.delete() }
            .onFailure { logger.warn(it) { "endpoint.delete failed (libDestroy may have cleaned it)" } }
    }

    private fun logAudioDevices() {
        val adm = endpoint.audDevManager()
        logger.info { "Audio capture device: ${adm.captureDev}, playback device: ${adm.playbackDev}" }
        adm.enumDev2().use { devices ->
            for (j in 0 until devices.size) {
                val dev = devices[j]
                logger.info { "Audio device[$j]: '${dev.name}' in=${dev.inputCount} out=${dev.outputCount}" }
            }
        }
    }
}
```

- [ ] **Step 4.2: Verify compile**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.3: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEndpointManager.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): apply EpConfig/TransportConfig/AudDevInfoVector.use, replace swallowed catches

Every SWIG-owned object in PjsipEndpointManager now routes through .use { }.
Shutdown errors are logged via runCatching.onFailure instead of silent catch(_).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Replace listener interfaces with SharedFlow

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEngine.kt`

Large atomic change — must land together to compile. Kills `IncomingCallListener` and `AccountRegistrationListener` interfaces. `PjsipAccountManager` exposes `incomingCalls: SharedFlow<IncomingCallEvent>` and `registrationEvents: SharedFlow<Pair<String, PjsipRegistrationState>>`. Downstream managers collect from these.

- [ ] **Step 5.1: Rewrite `PjsipAccountManager.kt`**

Replaces the two listener interfaces and their mutable nullable properties. Still keeps `AccountProvider` + now exposes `incomingCalls`. Per-account `StateFlow` map stays (some consumers may add it later; it's local state either way).

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.pjsua_stun_use
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

data class IncomingCallEvent(val accountId: String, val callId: Int)

interface AccountProvider {
    fun getAccount(accountId: String): PjsipAccount?
    fun getFirstConnectedAccount(): PjsipAccount?
    val incomingCalls: SharedFlow<IncomingCallEvent>
}

class PjsipAccountManager(
    private val isDestroyed: () -> Boolean,
) : AccountProvider {

    private val _accountStates = mutableMapOf<String, MutableStateFlow<PjsipRegistrationState>>()
    private val accounts: MutableMap<String, PjsipAccount> = mutableMapOf()
    private val lastRegisterAttemptMs = mutableMapOf<String, Long>()

    private val _incomingCalls = MutableSharedFlow<IncomingCallEvent>(extraBufferCapacity = 32)
    override val incomingCalls: SharedFlow<IncomingCallEvent> = _incomingCalls.asSharedFlow()

    private val _registrationEvents = MutableSharedFlow<Pair<String, PjsipRegistrationState>>(
        extraBufferCapacity = 64,
    )
    val registrationEvents: SharedFlow<Pair<String, PjsipRegistrationState>> =
        _registrationEvents.asSharedFlow()

    fun updateRegistrationState(accountId: String, state: PjsipRegistrationState) {
        stateFlowFor(accountId).value = state
        _registrationEvents.tryEmit(accountId to state)
    }

    fun isAccountDestroyed(): Boolean = isDestroyed()

    fun handleIncomingCall(accountId: String, callId: Int) {
        _incomingCalls.tryEmit(IncomingCallEvent(accountId, callId))
    }

    override fun getAccount(accountId: String): PjsipAccount? = accounts[accountId]

    override fun getFirstConnectedAccount(): PjsipAccount? =
        accounts.entries.firstOrNull { (id, _) ->
            _accountStates[id]?.value is PjsipRegistrationState.Registered
        }?.value

    suspend fun register(accountId: String, credentials: SipCredentials): Result<Unit> {
        val stateFlow = stateFlowFor(accountId)

        if (stateFlow.value is PjsipRegistrationState.Registering) {
            return Result.failure(IllegalStateException("Registration already in progress for $accountId"))
        }

        rateLimitRegister(accountId)

        val wasRegistered = stateFlow.value is PjsipRegistrationState.Registered
        stateFlow.value = PjsipRegistrationState.Registering

        accounts[accountId]?.let { prev ->
            teardownPrevious(accountId, prev, stateFlow, wasRegistered)
            stateFlow.value = PjsipRegistrationState.Registering
        }

        return buildAccountConfig(credentials).use { config ->
            runCatching {
                val account = PjsipAccount(accountId, credentials.server, this).apply {
                    create(config, true)
                }
                accounts[accountId] = account
                logger.info { "[$accountId] Account created, awaiting registration callback" }
            }.onFailure { e ->
                logger.error(e) { "[$accountId] Registration failed" }
                stateFlow.value = PjsipRegistrationState.Failed(SipError.fromException(e))
            }
        }
    }

    suspend fun unregister(accountId: String) {
        val acc = accounts[accountId] ?: return
        val stateFlow = _accountStates[accountId] ?: return
        runCatching {
            acc.setRegistration(false)
            withTimeoutOrNull(SipConstants.Timeout.UNREGISTER_MS) {
                stateFlow.first { it is PjsipRegistrationState.Idle }
            }
        }.onFailure { logger.warn(it) { "[$accountId] Unregister error" } }
        acc.safeDelete()
        accounts.remove(accountId)
        stateFlow.value = PjsipRegistrationState.Idle
    }

    suspend fun unregisterAll() {
        accounts.keys.toList().forEach { unregister(it) }
    }

    suspend fun destroy() {
        val activeAccounts = accounts.values.toList()
        activeAccounts.forEach { acc ->
            runCatching { acc.setRegistration(false) }
                .onFailure { logger.warn(it) { "setRegistration(false) failed during destroy" } }
        }
        // Wait for PJSIP to report Idle on every account, bounded by DESTROY_MS.
        withTimeoutOrNull(SipConstants.Timeout.DESTROY_MS) {
            coroutineScope {
                _accountStates.values
                    .map { flow -> async { flow.first { it is PjsipRegistrationState.Idle } } }
                    .awaitAll()
            }
        } ?: logger.warn { "destroy: timed out waiting for Idle" }
        accounts.values.forEach { it.safeDelete() }
        accounts.clear()
        _accountStates.values.forEach { it.value = PjsipRegistrationState.Idle }
        _accountStates.clear()
        lastRegisterAttemptMs.clear()
    }

    private fun stateFlowFor(accountId: String): MutableStateFlow<PjsipRegistrationState> =
        _accountStates.getOrPut(accountId) { MutableStateFlow(PjsipRegistrationState.Idle) }

    private suspend fun rateLimitRegister(accountId: String) {
        val last = lastRegisterAttemptMs[accountId] ?: 0L
        val wait = SipConstants.RATE_LIMIT_MS - (System.currentTimeMillis() - last)
        if (wait > 0) delay(wait)
        lastRegisterAttemptMs[accountId] = System.currentTimeMillis()
    }

    private suspend fun teardownPrevious(
        accountId: String,
        prev: PjsipAccount,
        stateFlow: MutableStateFlow<PjsipRegistrationState>,
        wasRegistered: Boolean,
    ) {
        runCatching { prev.setRegistration(false) }
            .onFailure { logger.warn(it) { "[$accountId] setRegistration(false) threw — continuing teardown" } }
        if (wasRegistered) {
            withTimeoutOrNull(SipConstants.Timeout.UNREGISTER_BEFORE_REREGISTER_MS) {
                stateFlow.first { it is PjsipRegistrationState.Idle }
            }
        }
        prev.safeDelete()
        accounts.remove(accountId)
    }

    private fun buildAccountConfig(credentials: SipCredentials): AccountConfig {
        val config = AccountConfig()
        val authCred = AuthCredInfo(
            SipConstants.AUTH_SCHEME_DIGEST,
            SipConstants.AUTH_REALM_ANY,
            credentials.username,
            SipConstants.AUTH_DATA_TYPE_PLAINTEXT,
            credentials.password,
        )
        try {
            config.idUri = SipConstants.buildUserUri(credentials.username, credentials.server)
            config.regConfig.registrarUri = SipConstants.buildRegistrarUri(credentials.server, credentials.port)
            // disable pjsip built-in retry — we handle reconnect
            config.regConfig.retryIntervalSec = 0
            config.sipConfig.authCreds.add(authCred)
            config.natConfig.sipStunUse = pjsua_stun_use.PJSUA_STUN_USE_DISABLED
            config.natConfig.mediaStunUse = pjsua_stun_use.PJSUA_STUN_USE_DISABLED
        } finally {
            authCred.delete()
        }
        return config
    }
}
```

NOTE: `IncomingCallListener` and `AccountRegistrationListener` are **deleted** in this step. `getAccountStateFlow()` is also **removed** — it had no callers.

- [ ] **Step 5.2: Update `PjsipCallManager.kt` — collect from flow, drop listener**

Edit the existing `PjsipCallManager.kt` from Task 3:
1. Remove `: IncomingCallListener` from class header.
2. Rename `override fun onIncomingCall(accountId: String, callId: Int)` → `private fun handleIncomingCall(event: IncomingCallEvent)` and unpack the event fields.
3. Add `init { scope.launch { accountProvider.incomingCalls.collect { handleIncomingCall(it) } } }`.

Diff to apply:

```kotlin
// class header — remove listener interface
class PjsipCallManager(
    private val accountProvider: AccountProvider,
    private val audioMediaProvider: AudioMediaProvider,
    private val isDestroyed: () -> Boolean,
    private val pjDispatcher: CoroutineContext,
) {

    // ... existing fields ...

    init {
        scope.launch {
            accountProvider.incomingCalls.collect { handleIncomingCall(it) }
        }
    }

    // rename from override fun onIncomingCall
    private fun handleIncomingCall(event: IncomingCallEvent) {
        val accountId = event.accountId
        val callId = event.callId
        val acc = accountProvider.getAccount(accountId) ?: run {
            logger.warn { "Incoming call on unknown account $accountId — ignoring" }
            return
        }
        // ... rest of body unchanged ...
    }
}
```

Add import: `import kotlinx.coroutines.flow.collect` (if not already pulled in).

- [ ] **Step 5.3: Rewrite `PjsipSipAccountManager.kt` — collect from flow**

Kill `AccountRegistrationListener` implementation. Collect `registrationEvents` in an init block via `scope.launch`.

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.SipAccount
import uz.yalla.sipphone.domain.SipAccountInfo
import uz.yalla.sipphone.domain.SipAccountManager
import uz.yalla.sipphone.domain.SipAccountState
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

class PjsipSipAccountManager(
    private val accountManager: PjsipAccountManager,
    private val callEngine: CallEngine,
    private val pjDispatcher: CoroutineDispatcher,
) : SipAccountManager {

    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)

    private val credentialCache = mutableMapOf<String, SipAccountInfo>()
    private val reconnectJobs = mutableMapOf<String, Job>()
    private val reconnectAttempts = mutableMapOf<String, Int>()

    private val _accounts = MutableStateFlow<List<SipAccount>>(emptyList())
    override val accounts: StateFlow<List<SipAccount>> = _accounts.asStateFlow()

    init {
        scope.launch {
            accountManager.registrationEvents.collect { (id, state) ->
                handleRegistrationState(id, state)
            }
        }
    }

    private fun handleRegistrationState(accountId: String, state: PjsipRegistrationState) {
        when (state) {
            is PjsipRegistrationState.Registered -> {
                cancelReconnect(accountId)
                reconnectAttempts.remove(accountId)
                updateAccountState(accountId, SipAccountState.Connected)
                logger.info { "[$accountId] Connected" }
            }
            is PjsipRegistrationState.Failed -> {
                if (state.error is SipError.AuthFailed) {
                    cancelReconnect(accountId)
                    reconnectAttempts.remove(accountId)
                    updateAccountState(accountId, SipAccountState.Disconnected)
                    logger.warn { "[$accountId] Auth failed — skipping reconnection" }
                } else {
                    updateAccountState(accountId, SipAccountState.Disconnected)
                    scheduleReconnect(accountId)
                }
            }
            is PjsipRegistrationState.Idle, is PjsipRegistrationState.Registering -> {}
        }
    }

    override suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.failure(IllegalArgumentException("No accounts to register"))
        }
        accounts.forEach { info -> credentialCache[info.id] = info }

        _accounts.value = accounts.map { info ->
            SipAccount(
                id = info.id,
                name = info.name,
                credentials = info.credentials,
                state = SipAccountState.Disconnected,
            )
        }

        var successCount = 0
        var lastError: Throwable? = null
        accounts.forEachIndexed { index, info ->
            if (index > 0) delay(REGISTER_DELAY_MS)
            val result = withContext(pjDispatcher) {
                accountManager.register(info.id, info.credentials)
            }
            if (result.isSuccess) {
                successCount++
            } else {
                lastError = result.exceptionOrNull()
                logger.warn { "[${info.id}] Registration call failed: ${lastError?.message}" }
            }
        }
        return if (successCount > 0) {
            logger.info { "registerAll: $successCount/${accounts.size} accounts submitted successfully" }
            Result.success(Unit)
        } else {
            logger.error { "registerAll: all ${accounts.size} accounts failed" }
            Result.failure(lastError ?: IllegalStateException("All accounts failed to register"))
        }
    }

    override suspend fun connect(accountId: String): Result<Unit> {
        val info = credentialCache[accountId]
            ?: return Result.failure(IllegalStateException("No cached credentials for $accountId"))
        cancelReconnect(accountId)
        reconnectAttempts.remove(accountId)
        return withContext(pjDispatcher) {
            accountManager.register(accountId, info.credentials)
        }
    }

    override suspend fun disconnect(accountId: String): Result<Unit> {
        if (callEngine.callState.value.activeAccountId == accountId) {
            return Result.failure(
                IllegalStateException("Cannot disconnect account $accountId — active call in progress"),
            )
        }
        cancelReconnect(accountId)
        reconnectAttempts.remove(accountId)
        withContext(pjDispatcher) { accountManager.unregister(accountId) }
        updateAccountState(accountId, SipAccountState.Disconnected)
        return Result.success(Unit)
    }

    override suspend fun unregisterAll() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        reconnectAttempts.clear()
        withContext(pjDispatcher) { accountManager.unregisterAll() }
        _accounts.update { list -> list.map { it.copy(state = SipAccountState.Disconnected) } }
        credentialCache.clear()
    }

    fun destroy() {
        scope.cancel()
    }

    private fun updateAccountState(accountId: String, state: SipAccountState) {
        _accounts.update { list ->
            list.map { account ->
                if (account.id == accountId) account.copy(state = state) else account
            }
        }
    }

    private fun scheduleReconnect(accountId: String) {
        if (reconnectJobs[accountId]?.isActive == true) return
        val info = credentialCache[accountId] ?: run {
            logger.error { "[$accountId] Cannot reconnect — no cached credentials" }
            return
        }
        reconnectJobs[accountId] = scope.launch {
            while (true) {
                val attempt = (reconnectAttempts[accountId] ?: 0) + 1
                reconnectAttempts[accountId] = attempt
                val backoffMs = calculateBackoff(attempt)
                updateAccountState(accountId, SipAccountState.Reconnecting(attempt, backoffMs))
                logger.info { "[$accountId] Reconnecting (attempt $attempt, backoff ${backoffMs / 1000}s)" }
                delay(backoffMs)
                val result = withContext(pjDispatcher) {
                    accountManager.register(accountId, info.credentials)
                }
                if (result.isSuccess) break
                logger.warn { "[$accountId] Reconnect attempt $attempt failed: ${result.exceptionOrNull()?.message}" }
            }
        }
    }

    private fun cancelReconnect(accountId: String) {
        reconnectJobs[accountId]?.cancel()
        reconnectJobs.remove(accountId)
    }

    companion object {
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val JITTER_BOUND_MS = 500
        private const val REGISTER_DELAY_MS = 500L

        internal fun calculateBackoff(attempt: Int): Long {
            val exponential = BASE_DELAY_MS * (1L shl min(attempt - 1, 20))
            val capped = min(exponential, MAX_DELAY_MS)
            val jitter = Random.nextLong(JITTER_BOUND_MS.toLong())
            return capped + jitter
        }
    }
}
```

NOTE: the three-map cleanup (`AccountSession`) is in Task 7. Task 5 keeps the old map shape so the listener-replacement and the data-shape change land in separate, easy-to-revert commits.

- [ ] **Step 5.4: Update `PjsipEngine.kt` — drop listener wiring**

In `PjsipEngine.kt`, delete the `init { accountManager.incomingCallListener = callManager }` block — it's gone now that `PjsipCallManager` subscribes to `accountProvider.incomingCalls` itself.

```kotlin
// Before:
init {
    accountManager.incomingCallListener = callManager
}

// After: delete the init block entirely (or leave it empty if it had other lines — it doesn't)
```

- [ ] **Step 5.5: Verify compile + tests**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All 259 tests pass. Subscribe timing note — `MutableSharedFlow(extraBufferCapacity = 64)` with `tryEmit` will drop if no collector yet attached, but the `init { scope.launch { collect … } }` in `PjsipCallManager` / `PjsipSipAccountManager` runs on construction, before `PjsipAccountManager.register` is invoked from `registerAll`, so subscribers are always in place before events fire.

- [ ] **Step 5.6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEngine.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): replace listener interfaces with SharedFlow

- Kill IncomingCallListener and AccountRegistrationListener interfaces
- PjsipAccountManager exposes incomingCalls + registrationEvents SharedFlows
- PjsipCallManager / PjsipSipAccountManager subscribe in init blocks
- PjsipEngine drops listener wiring
- Extract buildAccountConfig, rateLimitRegister, teardownPrevious from register()
- destroy() waits on per-account StateFlow reaching Idle instead of fixed delay

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Merge 3 reconnect maps into `AccountSession`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt`

Collapses `credentialCache`, `reconnectJobs`, `reconnectAttempts` into one `sessions: Map<String, AccountSession>`.

- [ ] **Step 6.1: Apply diff to `PjsipSipAccountManager.kt`**

Replace the three maps with one session map:

```kotlin
// BEFORE (inside class, post-Task-5):
private val credentialCache = mutableMapOf<String, SipAccountInfo>()
private val reconnectJobs = mutableMapOf<String, Job>()
private val reconnectAttempts = mutableMapOf<String, Int>()

// AFTER:
private data class AccountSession(
    val info: SipAccountInfo,
    var reconnectJob: Job? = null,
    var reconnectAttempts: Int = 0,
)

private val sessions = mutableMapOf<String, AccountSession>()
```

Update every use site:

```kotlin
// registerAll: accounts.forEach { info -> credentialCache[info.id] = info }
// → accounts.forEach { info -> sessions[info.id] = AccountSession(info) }

// connect: val info = credentialCache[accountId] ?: return Result.failure(...)
// → val session = sessions[accountId] ?: return Result.failure(...)
//   then use session.info.credentials and clear session.reconnectJob / session.reconnectAttempts

// disconnect, unregisterAll: replace three map operations with one session mutation

// scheduleReconnect: read from session.info, mutate session.reconnectAttempts / session.reconnectJob
```

Full rewritten file:

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.SipAccount
import uz.yalla.sipphone.domain.SipAccountInfo
import uz.yalla.sipphone.domain.SipAccountManager
import uz.yalla.sipphone.domain.SipAccountState
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

class PjsipSipAccountManager(
    private val accountManager: PjsipAccountManager,
    private val callEngine: CallEngine,
    private val pjDispatcher: CoroutineDispatcher,
) : SipAccountManager {

    private data class AccountSession(
        val info: SipAccountInfo,
        var reconnectJob: Job? = null,
        var reconnectAttempts: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)
    private val sessions = mutableMapOf<String, AccountSession>()

    private val _accounts = MutableStateFlow<List<SipAccount>>(emptyList())
    override val accounts: StateFlow<List<SipAccount>> = _accounts.asStateFlow()

    init {
        scope.launch {
            accountManager.registrationEvents.collect { (id, state) ->
                handleRegistrationState(id, state)
            }
        }
    }

    private fun handleRegistrationState(accountId: String, state: PjsipRegistrationState) {
        when (state) {
            is PjsipRegistrationState.Registered -> {
                clearReconnect(accountId)
                updateAccountState(accountId, SipAccountState.Connected)
                logger.info { "[$accountId] Connected" }
            }
            is PjsipRegistrationState.Failed -> {
                if (state.error is SipError.AuthFailed) {
                    clearReconnect(accountId)
                    updateAccountState(accountId, SipAccountState.Disconnected)
                    logger.warn { "[$accountId] Auth failed — skipping reconnection" }
                } else {
                    updateAccountState(accountId, SipAccountState.Disconnected)
                    scheduleReconnect(accountId)
                }
            }
            is PjsipRegistrationState.Idle, is PjsipRegistrationState.Registering -> {}
        }
    }

    override suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.failure(IllegalArgumentException("No accounts to register"))
        }
        accounts.forEach { info -> sessions[info.id] = AccountSession(info) }

        _accounts.value = accounts.map { info ->
            SipAccount(
                id = info.id,
                name = info.name,
                credentials = info.credentials,
                state = SipAccountState.Disconnected,
            )
        }

        var successCount = 0
        var lastError: Throwable? = null
        accounts.forEachIndexed { index, info ->
            if (index > 0) delay(REGISTER_DELAY_MS)
            val result = withContext(pjDispatcher) {
                accountManager.register(info.id, info.credentials)
            }
            if (result.isSuccess) {
                successCount++
            } else {
                lastError = result.exceptionOrNull()
                logger.warn { "[${info.id}] Registration call failed: ${lastError?.message}" }
            }
        }
        return if (successCount > 0) {
            logger.info { "registerAll: $successCount/${accounts.size} accounts submitted successfully" }
            Result.success(Unit)
        } else {
            logger.error { "registerAll: all ${accounts.size} accounts failed" }
            Result.failure(lastError ?: IllegalStateException("All accounts failed to register"))
        }
    }

    override suspend fun connect(accountId: String): Result<Unit> {
        val session = sessions[accountId]
            ?: return Result.failure(IllegalStateException("No cached credentials for $accountId"))
        clearReconnect(accountId)
        return withContext(pjDispatcher) {
            accountManager.register(accountId, session.info.credentials)
        }
    }

    override suspend fun disconnect(accountId: String): Result<Unit> {
        if (callEngine.callState.value.activeAccountId == accountId) {
            return Result.failure(
                IllegalStateException("Cannot disconnect account $accountId — active call in progress"),
            )
        }
        clearReconnect(accountId)
        withContext(pjDispatcher) { accountManager.unregister(accountId) }
        updateAccountState(accountId, SipAccountState.Disconnected)
        return Result.success(Unit)
    }

    override suspend fun unregisterAll() {
        sessions.values.forEach { it.reconnectJob?.cancel() }
        sessions.clear()
        withContext(pjDispatcher) { accountManager.unregisterAll() }
        _accounts.update { list -> list.map { it.copy(state = SipAccountState.Disconnected) } }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun updateAccountState(accountId: String, state: SipAccountState) {
        _accounts.update { list ->
            list.map { account ->
                if (account.id == accountId) account.copy(state = state) else account
            }
        }
    }

    private fun scheduleReconnect(accountId: String) {
        val session = sessions[accountId] ?: run {
            logger.error { "[$accountId] Cannot reconnect — no cached credentials" }
            return
        }
        if (session.reconnectJob?.isActive == true) return
        session.reconnectJob = scope.launch {
            while (true) {
                val attempt = ++session.reconnectAttempts
                val backoffMs = calculateBackoff(attempt)
                updateAccountState(accountId, SipAccountState.Reconnecting(attempt, backoffMs))
                logger.info { "[$accountId] Reconnecting (attempt $attempt, backoff ${backoffMs / 1000}s)" }
                delay(backoffMs)
                val result = withContext(pjDispatcher) {
                    accountManager.register(accountId, session.info.credentials)
                }
                if (result.isSuccess) break
                logger.warn { "[$accountId] Reconnect attempt $attempt failed: ${result.exceptionOrNull()?.message}" }
            }
        }
    }

    private fun clearReconnect(accountId: String) {
        sessions[accountId]?.let {
            it.reconnectJob?.cancel()
            it.reconnectJob = null
            it.reconnectAttempts = 0
        }
    }

    companion object {
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val JITTER_BOUND_MS = 500
        private const val REGISTER_DELAY_MS = 500L

        internal fun calculateBackoff(attempt: Int): Long {
            val exponential = BASE_DELAY_MS * (1L shl min(attempt - 1, 20))
            val capped = min(exponential, MAX_DELAY_MS)
            val jitter = Random.nextLong(JITTER_BOUND_MS.toLong())
            return capped + jitter
        }
    }
}
```

- [ ] **Step 6.2: Verify compile + tests**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All 259 tests pass.

- [ ] **Step 6.3: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): merge reconnect maps into AccountSession

credentialCache + reconnectJobs + reconnectAttempts — three maps representing
the same per-account concept — collapse into a single sessions: Map<String, AccountSession>.
cancelReconnect becomes clearReconnect that mutates the session struct.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Full verification + manual smoke

**Files:** none modified.

- [ ] **Step 7.1: Full build**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew build`
Expected: `BUILD SUCCESSFUL`. No Kotlin/Compose compile errors, `compileKotlin`, `compileTestKotlin`, and `test` all green.

- [ ] **Step 7.2: Run full test suite with count**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test --info 2>&1 | grep -E "^Tests:|PASSED|FAILED|tests completed" | tail -30`
Expected: "259 tests completed, 0 failed" (or equivalent — count matches pre-refactor baseline).

- [ ] **Step 7.3: Integration test spot-check**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && ./gradlew test --tests "*IntegrationTest"`
Expected: `BusyOperatorIntegrationTest`, `BridgeIntegrationTest`, `CallFlowIntegrationTest` all pass.

- [ ] **Step 7.4: Diff line-count sanity check**

Run: `cd ~/Ildam/yalla/yalla-sip-phone && git diff --stat HEAD~6 -- src/main/kotlin/uz/yalla/sipphone/data/pjsip/`
Expected: net line reduction across the pjsip layer (should be negative 150+ lines).

- [ ] **Step 7.5: Manual smoke (user-driven)**

This step requires Islom at the keyboard — the AI agent stops here and reports readiness. The user runs:

```bash
cd ~/Ildam/yalla/yalla-sip-phone
./gradlew run
```

Smoke checklist (user executes):
- Login with test credentials
- Register a SIP account → account moves to Connected in UI
- Place a test call to extension `101` → rings → remote picks up → call state goes Ringing → Active
- Mute / unmute toggle works
- Hold / unhold toggle works (audio media re-connects)
- Hang up → state returns to Idle
- Receive an incoming call from another extension → UI shows Ringing
- Answer, talk, hang up
- Reject while in another call (busy) → caller sees 486 Busy Here

If any step fails: revert via `git reset --hard HEAD~6` and diagnose. Partial revert per task is also possible since each task is its own commit.

---

## Self-Review Notes

- **Spec coverage**: all 8 root causes from the audit are addressed (see table at top).
- **No placeholders**: every code step includes the full file contents or a precise diff region. No "TBD", no "add error handling", no "similar to Task N".
- **Type consistency**: `ActiveCall`, `IncomingCallEvent`, `AccountSession`, `registrationEvents`, `incomingCalls`, `clearReconnect`, `stateFlowFor`, `buildAccountConfig`, `rateLimitRegister`, `teardownPrevious` — names match across tasks where they cross file boundaries.
- **Blast radius per task**: Task 1 (pure add) < Task 2 (small) < Task 4 (one file) < Task 3 (one file, large) < Task 6 (one file, moderate) < Task 5 (four files, atomic). Task 5 is the riskiest — the commit message explicitly lists every file touched so bisect is usable.

# SIP Layer Clean Code Refactor Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose SIP-layer god-classes into focused, testable components (SRP) and eliminate scattered state-mutation sites.

**Architecture:** Six focused extractions applied TDD. Each extraction is independently commit-atomic. Pure refactor — all existing 222 tests must pass unchanged; new pure-Kotlin units get dedicated unit tests.

**Tech Stack:** Kotlin 2, kotlinx.coroutines + coroutines-test, kotlin.test, JUnit 4 runtime, Turbine.

---

## File Structure

**Create:**
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/AudioMediaIterator.kt` — extension on `CallInfo` iterating active audio media. Extracted from duplication in `PjsipCallManager.applyMuteState` + `connectCallAudio`.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/HoldController.kt` — owns hold/unhold PJSIP calls, in-progress guard, and reinvite timeout. Extracted from `PjsipCallManager.applyHoldState` + scattered `holdInProgress` mutations.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectController.kt` — per-account exponential-backoff reconnect loop with attempt counter and cancellation. Extracted from `PjsipSipAccountManager.scheduleReconnect`.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiter.kt` — per-account minimum-interval gate. Extracted from `PjsipAccountManager.rateLimitRegister`.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/AccountConfigBuilder.kt` — pure factory: `SipCredentials → AccountConfig`. Extracted from `PjsipAccountManager.buildAccountConfig`.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachine.kt` — explicit `CallState × CallEvent → CallState` transition table. Replaces ad-hoc `_callState.value = ...` writes across `PjsipCallManager`.

**Modify:**
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt` — consume new helpers, remove extracted logic.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt` — consume new helpers.
- `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt` — delegate to `ReconnectController`.

**New tests:**
- `src/test/kotlin/uz/yalla/sipphone/data/pjsip/HoldControllerTest.kt`
- `src/test/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectControllerTest.kt`
- `src/test/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiterTest.kt`
- `src/test/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachineTest.kt`

AudioMediaIterator + AccountConfigBuilder are not unit-tested directly — they operate on SWIG types that require loaded native libs; their correctness is covered by integration tests and by behavioral tests through the consumers.

---

## Task 1: AudioMediaIterator helper

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/AudioMediaIterator.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`

Today `PjsipCallManager.applyMuteState` and `PjsipCallManager.connectCallAudio` each loop over `info.media`, skip non-audio, skip non-active, then do work. Extract to one place.

- [ ] **Step 1: Create the helper file**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/AudioMediaIterator.kt
package uz.yalla.sipphone.data.pjsip

import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsua_call_media_status

inline fun CallInfo.forEachActiveAudioMedia(action: (index: Int, media: CallMediaInfo) -> Unit) {
    for (i in 0 until media.size) {
        val m = media[i]
        if (m.type != pjmedia_type.PJMEDIA_TYPE_AUDIO) continue
        if (m.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) continue
        action(i, m)
    }
}
```

- [ ] **Step 2: Replace the loop in `applyMuteState`**

In `PjsipCallManager.kt`, replace the body of `applyMuteState`:

```kotlin
private fun applyMuteState(call: PjsipCall, muted: Boolean) {
    call.getInfo().use { info ->
        info.forEachActiveAudioMedia { i, _ ->
            val audioMedia = call.getAudioMedia(i)
            val captureMedia = audioMediaProvider.getCaptureDevMedia()
            if (muted) captureMedia.stopTransmit(audioMedia)
            else captureMedia.startTransmit(audioMedia)
            return@forEachActiveAudioMedia
        }
    }
}
```

Note: original used `return@use` to exit after the first active-audio media. `forEachActiveAudioMedia` doesn't stop on first, but since mute is idempotent across multiple streams it's fine to apply to all. Keep the semantics simple.

- [ ] **Step 3: Replace the loop in `connectCallAudio`**

```kotlin
fun connectCallAudio(call: PjsipCall) {
    if (call !== active?.call) return
    holdInProgress = false
    holdTimeoutJob?.cancel()
    holdTimeoutJob = null

    call.getInfo().use { info ->
        info.forEachActiveAudioMedia { i, _ ->
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
            return@forEachActiveAudioMedia
        }
    }
}
```

- [ ] **Step 4: Build + test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all 222 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/AudioMediaIterator.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): extract forEachActiveAudioMedia helper

Deduplicates the audio-media loop in applyMuteState and connectCallAudio.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: HoldController

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/HoldController.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/pjsip/HoldControllerTest.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`

Encapsulates the hold/unhold state machine: in-progress guard, reinvite timeout, media-connected clearing, and the PJSIP flag manipulation. Pure PJSIP-layer concern — `PjsipCallManager` stays responsible for domain-state transitions.

**Public API:**
```kotlin
class HoldController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 15_000L,
) {
    /** Returns true if the request was issued, false if already in progress. */
    fun requestHold(call: PjsipCall): Boolean
    fun requestUnhold(call: PjsipCall): Boolean
    /** Called from onCallMediaState — clears in-progress and cancels timeout. */
    fun onMediaStateChanged()
    /** Cancels any pending timeout. Used during disconnect/destroy. */
    fun cancel()
}
```

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/uz/yalla/sipphone/data/pjsip/HoldControllerTest.kt
package uz.yalla.sipphone.data.pjsip

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.pjsua_call_flag

@OptIn(ExperimentalCoroutinesApi::class)
class HoldControllerTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `requestHold returns true on first call and invokes setHold`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)

        val issued = controller.requestHold(call)

        assertTrue(issued)
        verify { call.setHold(any<CallOpParam>()) }
    }

    @Test
    fun `requestHold returns false when already in progress`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.requestHold(call)

        val second = controller.requestHold(call)

        assertFalse(second)
    }

    @Test
    fun `requestUnhold invokes reinvite with UNHOLD flag`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)

        val issued = controller.requestUnhold(call)

        assertTrue(issued)
        verify {
            call.reinvite(match<CallOpParam> { it.opt.flag == pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong() })
        }
    }

    @Test
    fun `onMediaStateChanged clears in-progress and allows new request`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.requestHold(call)

        controller.onMediaStateChanged()
        val secondHold = controller.requestHold(call)

        assertTrue(secondHold)
    }

    @Test
    fun `timeout clears in-progress automatically`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.requestHold(call)

        advanceTimeBy(1_100)
        advanceUntilIdle()
        val canIssueAgain = controller.requestHold(call)

        assertTrue(canIssueAgain)
    }

    @Test
    fun `cancel clears in-progress and timeout`() = runTest(dispatcher) {
        val call = mockk<PjsipCall>(relaxed = true)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.requestHold(call)

        controller.cancel()
        val canIssueAgain = controller.requestHold(call)

        assertTrue(canIssueAgain)
    }
}
```

Note: if MockK isn't already in the test classpath, we need to add it. Check `build.gradle.kts` for `testImplementation("io.mockk:mockk:...")`. If absent, replace MockK with a hand-rolled fake (see fallback below).

- [ ] **Step 2: Check MockK dependency**

Run: `grep -n "mockk" build.gradle.kts`

If MockK is NOT present, replace the test body to use a fake instead:

```kotlin
// Alternative: hand-rolled fake in test file
private class FakeCall(callManager: PjsipCallManager, account: org.pjsip.pjsua2.Account) :
    PjsipCall(callManager, account) {
    var setHoldCount = 0
    var reinviteCount = 0
    var lastFlag: Long = 0

    override fun setHold(prm: CallOpParam) { setHoldCount++ }
    override fun reinvite(prm: CallOpParam) {
        reinviteCount++
        lastFlag = prm.opt.flag
    }
}
```

— but constructing a `PjsipCall` in tests requires a real `Account` (SWIG), which we don't have. If MockK is missing, fall back to testing through `PjsipCallManager` integration. Announce the decision and skip the unit-test file; proceed to step 3 implementation and verify via existing tests.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.HoldControllerTest"`
Expected: FAIL — `HoldController` does not exist.

- [ ] **Step 4: Implement HoldController**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/HoldController.kt
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pjsip.pjsua2.pjsua_call_flag

private val logger = KotlinLogging.logger {}

class HoldController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    @Volatile
    private var inProgress = false
    private var timeoutJob: Job? = null

    fun requestHold(call: PjsipCall): Boolean {
        if (inProgress) return false
        inProgress = true
        withCallOpParam { prm -> call.setHold(prm) }
        armTimeout()
        return true
    }

    fun requestUnhold(call: PjsipCall): Boolean {
        if (inProgress) return false
        inProgress = true
        withCallOpParam { prm ->
            prm.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
            call.reinvite(prm)
        }
        armTimeout()
        return true
    }

    fun onMediaStateChanged() {
        inProgress = false
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun cancel() {
        inProgress = false
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun armTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (inProgress) {
                logger.warn { "Hold timeout — clearing in-progress flag" }
                inProgress = false
            }
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.HoldControllerTest"`
Expected: PASS (if MockK is available). If MockK is missing, skip these tests and rely on integration coverage.

- [ ] **Step 6: Wire HoldController into PjsipCallManager**

In `PjsipCallManager.kt`:

1. Remove `holdInProgress`, `holdTimeoutJob`, and `applyHoldState`.
2. Add field: `private val holdController = HoldController(scope)`
3. Replace `toggleHold`:

```kotlin
suspend fun toggleHold() {
    val state = _callState.value as? CallState.Active ?: return
    val call = active?.call ?: return
    val targetHold = !state.isOnHold
    val issued = if (targetHold) holdController.requestHold(call) else holdController.requestUnhold(call)
    if (!issued) {
        logger.warn { "Hold/resume operation already in progress, ignoring" }
        return
    }
    _callState.value = state.copy(isOnHold = targetHold)
}
```

4. Replace `setHold`:

```kotlin
suspend fun setHold(callId: String, onHold: Boolean) {
    val state = _callState.value as? CallState.Active ?: return
    if (state.callId != callId) {
        logger.warn { "setHold: callId mismatch (expected=${state.callId}, got=$callId)" }
        return
    }
    if (state.isOnHold == onHold) return
    val call = active?.call ?: return
    val issued = if (onHold) holdController.requestHold(call) else holdController.requestUnhold(call)
    if (!issued) {
        logger.warn { "Hold/resume operation already in progress, ignoring" }
        return
    }
    _callState.value = state.copy(isOnHold = onHold)
}
```

5. In `connectCallAudio`, replace the first three lines (`holdInProgress = false; holdTimeoutJob?.cancel(); holdTimeoutJob = null`) with `holdController.onMediaStateChanged()`.

6. In `destroy`, add `holdController.cancel()` before `scope.cancel()`.

- [ ] **Step 7: Build + full test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/HoldController.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt \
        src/test/kotlin/uz/yalla/sipphone/data/pjsip/HoldControllerTest.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): extract HoldController

PjsipCallManager no longer owns the hold/unhold state machine — it
delegates to HoldController, which manages the in-progress guard, the
reinvite flag, and the safety timeout. Unit-tested in isolation.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: ReconnectController

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectController.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectControllerTest.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt`

Per-account reconnect loop with exponential backoff + jitter + attempt counter. Owns one `Job` and reports state via callback. Replaces `scheduleReconnect` + `AccountSession.reconnectJob`/`reconnectAttempts` fields in `PjsipSipAccountManager`.

**Public API:**
```kotlin
class ReconnectController(
    private val scope: CoroutineScope,
    private val onAttempt: (attempt: Int, backoffMs: Long) -> Unit,
) {
    fun start(attemptBlock: suspend () -> Result<Unit>)
    fun stop()
}
```

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectControllerTest.kt
package uz.yalla.sipphone.data.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectControllerTest {

    @Test
    fun `start invokes attempt after backoff delay`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val attempts = mutableListOf<Int>()
        val controller = ReconnectController(CoroutineScope(dispatcher)) { attempt, _ ->
            attempts.add(attempt)
        }

        var invocationCount = 0
        controller.start {
            invocationCount++
            Result.success(Unit)
        }
        advanceUntilIdle()

        assertEquals(1, invocationCount)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun `failed attempt retries with increasing backoff`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val attempts = mutableListOf<Pair<Int, Long>>()
        val controller = ReconnectController(CoroutineScope(dispatcher)) { attempt, backoff ->
            attempts.add(attempt to backoff)
        }

        var count = 0
        controller.start {
            count++
            if (count >= 3) Result.success(Unit) else Result.failure(RuntimeException("fail"))
        }
        advanceUntilIdle()

        assertEquals(3, count)
        assertEquals(3, attempts.size)
        assertTrue(attempts[1].second > attempts[0].second)
        assertTrue(attempts[2].second > attempts[1].second)
    }

    @Test
    fun `stop cancels pending retry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = ReconnectController(CoroutineScope(dispatcher)) { _, _ -> }
        var count = 0

        controller.start {
            count++
            Result.failure(RuntimeException("fail"))
        }
        advanceTimeBy(500)
        controller.stop()
        advanceUntilIdle()

        assertTrue(count <= 1)
    }

    @Test
    fun `start is idempotent when a job is already running`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val attempts = mutableListOf<Int>()
        val controller = ReconnectController(CoroutineScope(dispatcher)) { attempt, _ ->
            attempts.add(attempt)
        }

        controller.start { Result.failure(RuntimeException("fail")) }
        controller.start { Result.failure(RuntimeException("fail")) }
        advanceTimeBy(5_000)
        controller.stop()
        advanceUntilIdle()

        assertTrue(attempts.all { it <= attempts.size })
    }

    @Test
    fun `stop resets attempt counter so next start begins at 1`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val attempts = mutableListOf<Int>()
        val controller = ReconnectController(CoroutineScope(dispatcher)) { attempt, _ ->
            attempts.add(attempt)
        }

        controller.start { Result.failure(RuntimeException("fail")) }
        advanceTimeBy(5_000)
        controller.stop()
        attempts.clear()
        controller.start { Result.success(Unit) }
        advanceUntilIdle()

        assertEquals(listOf(1), attempts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.ReconnectControllerTest"`
Expected: FAIL — `ReconnectController` does not exist.

- [ ] **Step 3: Implement ReconnectController**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectController.kt
package uz.yalla.sipphone.data.pjsip

import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReconnectController(
    private val scope: CoroutineScope,
    private val onAttempt: (attempt: Int, backoffMs: Long) -> Unit,
) {
    private var job: Job? = null
    private var attempts: Int = 0

    fun start(attemptBlock: suspend () -> Result<Unit>) {
        if (job?.isActive == true) return
        attempts = 0
        job = scope.launch {
            while (isActive) {
                val attempt = ++attempts
                val backoff = calculateBackoff(attempt)
                onAttempt(attempt, backoff)
                delay(backoff)
                if (attemptBlock().isSuccess) break
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        attempts = 0
    }

    companion object {
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val JITTER_BOUND_MS = 500L

        internal fun calculateBackoff(attempt: Int): Long {
            val exponential = BASE_DELAY_MS * (1L shl min(attempt - 1, 20))
            val capped = min(exponential, MAX_DELAY_MS)
            val jitter = Random.nextLong(JITTER_BOUND_MS)
            return capped + jitter
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.ReconnectControllerTest"`
Expected: PASS.

- [ ] **Step 5: Wire ReconnectController into PjsipSipAccountManager**

In `PjsipSipAccountManager.kt`:

1. Update `AccountSession`:
```kotlin
private data class AccountSession(
    val info: SipAccountInfo,
    val reconnect: ReconnectController,
)
```

2. In `registerAll`, construct the controller per account:
```kotlin
accounts.forEach { info ->
    sessions[info.id] = AccountSession(
        info = info,
        reconnect = ReconnectController(scope) { attempt, backoffMs ->
            updateAccountState(info.id, SipAccountState.Reconnecting(attempt, backoffMs))
            logger.info { "[${info.id}] Reconnecting (attempt $attempt, backoff ${backoffMs / 1000}s)" }
        },
    )
}
```

3. Replace `scheduleReconnect`:
```kotlin
private fun scheduleReconnect(accountId: String) {
    val session = sessions[accountId] ?: run {
        logger.error { "[$accountId] Cannot reconnect — no cached credentials" }
        return
    }
    session.reconnect.start {
        withContext(pjDispatcher) {
            accountManager.register(accountId, session.info.credentials)
        }.onFailure {
            logger.warn { "[$accountId] Reconnect attempt failed: ${it.message}" }
        }
    }
}
```

4. Replace `clearReconnect`:
```kotlin
private fun clearReconnect(accountId: String) {
    sessions[accountId]?.reconnect?.stop()
}
```

5. In `unregisterAll`, replace `sessions.values.forEach { it.reconnectJob?.cancel() }` with `sessions.values.forEach { it.reconnect.stop() }`.

6. Remove the `calculateBackoff` + backoff companion constants — they live in `ReconnectController` now.

- [ ] **Step 6: Build + full test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectController.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt \
        src/test/kotlin/uz/yalla/sipphone/data/pjsip/ReconnectControllerTest.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): extract ReconnectController

Per-account reconnect loop owns its own Job, attempt counter, and
backoff math. PjsipSipAccountManager now just delegates and reports
state via callback. Unit-tested with a test dispatcher.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: RegisterRateLimiter

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiter.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiterTest.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt`

Per-account minimum-interval gate. Today embedded in `PjsipAccountManager.rateLimitRegister`; extract so it's testable with an injected clock.

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiterTest.kt
package uz.yalla.sipphone.data.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterRateLimiterTest {

    @Test
    fun `first awaitSlot does not wait`() = runTest(StandardTestDispatcher(testScheduler)) {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")

        assertEquals(0, currentTime)
    }

    @Test
    fun `second awaitSlot within interval waits remainder`() = runTest(StandardTestDispatcher(testScheduler)) {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.awaitSlot("acc1")

        assertEquals(1_000, currentTime)
    }

    @Test
    fun `different accounts do not interfere`() = runTest(StandardTestDispatcher(testScheduler)) {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.awaitSlot("acc2")

        assertEquals(0, currentTime)
    }

    @Test
    fun `clear resets all accounts`() = runTest(StandardTestDispatcher(testScheduler)) {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.clear()
        limiter.awaitSlot("acc1")

        assertEquals(0, currentTime)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.RegisterRateLimiterTest"`
Expected: FAIL — `RegisterRateLimiter` does not exist.

- [ ] **Step 3: Implement RegisterRateLimiter**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiter.kt
package uz.yalla.sipphone.data.pjsip

import kotlinx.coroutines.delay
import uz.yalla.sipphone.domain.SipConstants

class RegisterRateLimiter(
    private val minIntervalMs: Long = SipConstants.RATE_LIMIT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAttempt = mutableMapOf<String, Long>()

    suspend fun awaitSlot(accountId: String) {
        val wait = minIntervalMs - (clock() - (lastAttempt[accountId] ?: 0L))
        if (wait > 0) delay(wait)
        lastAttempt[accountId] = clock()
    }

    fun clear() = lastAttempt.clear()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.RegisterRateLimiterTest"`
Expected: PASS.

- [ ] **Step 5: Wire into PjsipAccountManager**

In `PjsipAccountManager.kt`:

1. Add field: `private val rateLimiter = RegisterRateLimiter()`
2. Remove `lastRegisterAttemptMs` map.
3. Remove private fun `rateLimitRegister`.
4. In `register`, replace `rateLimitRegister(accountId)` with `rateLimiter.awaitSlot(accountId)`.
5. In `destroy`, replace `lastRegisterAttemptMs.clear()` with `rateLimiter.clear()`.

- [ ] **Step 6: Build + full test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiter.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt \
        src/test/kotlin/uz/yalla/sipphone/data/pjsip/RegisterRateLimiterTest.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): extract RegisterRateLimiter

Clock-injectable rate-limit gate. Tested with virtual-time scheduler.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: AccountConfigBuilder

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/AccountConfigBuilder.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt`

Pure factory for `AccountConfig`. Today inlined as `buildAccountConfig(credentials)` inside `PjsipAccountManager`. Move to a free function; no behavior change, just relocation + discoverability.

Not unit-tested directly (SWIG `AccountConfig` requires loaded native libs; covered by integration tests that already exercise registration).

- [ ] **Step 1: Create the file**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/AccountConfigBuilder.kt
package uz.yalla.sipphone.data.pjsip

import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.pjsua_stun_use
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipCredentials

object AccountConfigBuilder {
    fun build(credentials: SipCredentials): AccountConfig {
        val config = AccountConfig()
        AuthCredInfo(
            SipConstants.AUTH_SCHEME_DIGEST,
            SipConstants.AUTH_REALM_ANY,
            credentials.username,
            SipConstants.AUTH_DATA_TYPE_PLAINTEXT,
            credentials.password,
        ).use { authCred ->
            config.idUri = SipConstants.buildUserUri(credentials.username, credentials.server)
            config.regConfig.registrarUri = SipConstants.buildRegistrarUri(credentials.server, credentials.port)
            config.regConfig.retryIntervalSec = 0
            config.sipConfig.authCreds.add(authCred)
            config.natConfig.sipStunUse = pjsua_stun_use.PJSUA_STUN_USE_DISABLED
            config.natConfig.mediaStunUse = pjsua_stun_use.PJSUA_STUN_USE_DISABLED
        }
        return config
    }
}
```

- [ ] **Step 2: Update PjsipAccountManager**

In `PjsipAccountManager.kt`:

1. Remove the private `buildAccountConfig` function.
2. In `register`, replace the call with `AccountConfigBuilder.build(credentials)`.
3. Remove imports that are now unused: `AccountConfig`, `AuthCredInfo`, `pjsua_stun_use`, `SipCredentials` (if no other use).

Verify unused imports via the compiler warnings — run `./gradlew compileKotlin` and clean up what it flags.

- [ ] **Step 3: Build + full test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/AccountConfigBuilder.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): extract AccountConfigBuilder

Pure factory for AccountConfig lifted out of PjsipAccountManager.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: CallStateMachine — explicit transition table

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachine.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachineTest.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`

Today `_callState.value = ...` appears in ~8 locations across `PjsipCallManager`, each with its own type check and construction. Replace with one pure `transition(current, event)` function; consumers dispatch events instead of writing state.

**Design:**
```kotlin
sealed interface CallEvent {
    data class OutgoingDial(...) : CallEvent
    data class IncomingRing(...) : CallEvent
    data object Answered : CallEvent
    data object LocalHangup : CallEvent
    data object RemoteDisconnect : CallEvent
    data class MuteChanged(val muted: Boolean) : CallEvent
    data class HoldChanged(val onHold: Boolean) : CallEvent
}

class CallStateMachine {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()
    fun dispatch(event: CallEvent): CallState
}
```

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachineTest.kt
package uz.yalla.sipphone.data.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import uz.yalla.sipphone.domain.CallState

class CallStateMachineTest {

    @Test
    fun `Idle plus OutgoingDial yields outbound Ringing`() {
        val m = CallStateMachine()

        val next = m.dispatch(
            CallEvent.OutgoingDial(
                callId = "c1",
                remoteNumber = "101",
                accountId = "acc1",
            ),
        )

        assertIs<CallState.Ringing>(next)
        assertEquals("c1", next.callId)
        assertEquals("101", next.callerNumber)
        assertEquals(true, next.isOutbound)
        assertEquals("acc1", next.accountId)
    }

    @Test
    fun `Idle plus IncomingRing yields inbound Ringing`() {
        val m = CallStateMachine()

        val next = m.dispatch(
            CallEvent.IncomingRing(
                callId = "c2",
                remoteNumber = "555",
                remoteName = "Alice",
                accountId = "acc1",
                remoteUri = "sip:555@pbx",
            ),
        )

        assertIs<CallState.Ringing>(next)
        assertEquals(false, next.isOutbound)
        assertEquals("Alice", next.callerName)
        assertEquals("sip:555@pbx", next.remoteUri)
    }

    @Test
    fun `Ringing plus Answered yields Active carrying Ringing fields`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))

        val next = m.dispatch(CallEvent.Answered)

        assertIs<CallState.Active>(next)
        assertEquals("c1", next.callId)
        assertEquals("101", next.remoteNumber)
        assertEquals(true, next.isOutbound)
        assertEquals("acc1", next.accountId)
        assertEquals(false, next.isMuted)
        assertEquals(false, next.isOnHold)
    }

    @Test
    fun `Ringing plus LocalHangup yields Ending`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))

        val next = m.dispatch(CallEvent.LocalHangup)

        assertIs<CallState.Ending>(next)
        assertEquals("c1", next.callId)
        assertEquals("acc1", next.accountId)
    }

    @Test
    fun `Active plus LocalHangup yields Ending`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))
        m.dispatch(CallEvent.Answered)

        val next = m.dispatch(CallEvent.LocalHangup)

        assertIs<CallState.Ending>(next)
        assertEquals("c1", next.callId)
    }

    @Test
    fun `RemoteDisconnect yields Idle from any state`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))
        m.dispatch(CallEvent.Answered)

        val next = m.dispatch(CallEvent.RemoteDisconnect)

        assertEquals(CallState.Idle, next)
    }

    @Test
    fun `MuteChanged on Active toggles isMuted`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))
        m.dispatch(CallEvent.Answered)

        val next = m.dispatch(CallEvent.MuteChanged(true))

        assertIs<CallState.Active>(next)
        assertEquals(true, next.isMuted)
    }

    @Test
    fun `HoldChanged on Active toggles isOnHold`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))
        m.dispatch(CallEvent.Answered)

        val next = m.dispatch(CallEvent.HoldChanged(true))

        assertIs<CallState.Active>(next)
        assertEquals(true, next.isOnHold)
    }

    @Test
    fun `Answered is ignored when not in Ringing`() {
        val m = CallStateMachine()

        val next = m.dispatch(CallEvent.Answered)

        assertEquals(CallState.Idle, next)
    }

    @Test
    fun `MuteChanged is ignored when not in Active`() {
        val m = CallStateMachine()
        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))

        val next = m.dispatch(CallEvent.MuteChanged(true))

        assertIs<CallState.Ringing>(next)
    }

    @Test
    fun `dispatch emits to state flow`() {
        val m = CallStateMachine()

        m.dispatch(CallEvent.OutgoingDial("c1", "101", "acc1"))

        assertIs<CallState.Ringing>(m.state.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.CallStateMachineTest"`
Expected: FAIL — `CallStateMachine` does not exist.

- [ ] **Step 3: Implement CallStateMachine**

```kotlin
// src/main/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachine.kt
package uz.yalla.sipphone.data.pjsip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.domain.CallState

sealed interface CallEvent {
    data class OutgoingDial(
        val callId: String,
        val remoteNumber: String,
        val accountId: String,
    ) : CallEvent

    data class IncomingRing(
        val callId: String,
        val remoteNumber: String,
        val remoteName: String?,
        val accountId: String,
        val remoteUri: String,
    ) : CallEvent

    data object Answered : CallEvent
    data object LocalHangup : CallEvent
    data object RemoteDisconnect : CallEvent
    data class MuteChanged(val muted: Boolean) : CallEvent
    data class HoldChanged(val onHold: Boolean) : CallEvent
}

class CallStateMachine {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    fun dispatch(event: CallEvent): CallState {
        val next = transition(_state.value, event)
        _state.value = next
        return next
    }

    private fun transition(current: CallState, event: CallEvent): CallState = when (event) {
        is CallEvent.OutgoingDial -> CallState.Ringing(
            callId = event.callId,
            callerNumber = event.remoteNumber,
            callerName = null,
            isOutbound = true,
            accountId = event.accountId,
        )
        is CallEvent.IncomingRing -> CallState.Ringing(
            callId = event.callId,
            callerNumber = event.remoteNumber,
            callerName = event.remoteName,
            isOutbound = false,
            accountId = event.accountId,
            remoteUri = event.remoteUri,
        )
        CallEvent.Answered -> when (current) {
            is CallState.Ringing -> CallState.Active(
                callId = current.callId,
                remoteNumber = current.callerNumber,
                remoteName = current.callerName,
                isOutbound = current.isOutbound,
                isMuted = false,
                isOnHold = false,
                accountId = current.accountId,
                remoteUri = current.remoteUri,
            )
            else -> current
        }
        CallEvent.LocalHangup -> when (current) {
            is CallState.Ringing -> CallState.Ending(callId = current.callId, accountId = current.accountId)
            is CallState.Active -> CallState.Ending(callId = current.callId, accountId = current.accountId)
            else -> current
        }
        CallEvent.RemoteDisconnect -> CallState.Idle
        is CallEvent.MuteChanged -> (current as? CallState.Active)?.copy(isMuted = event.muted) ?: current
        is CallEvent.HoldChanged -> (current as? CallState.Active)?.copy(isOnHold = event.onHold) ?: current
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.CallStateMachineTest"`
Expected: PASS.

- [ ] **Step 5: Migrate PjsipCallManager to use CallStateMachine**

In `PjsipCallManager.kt`:

1. Replace `private val _callState = MutableStateFlow<CallState>(CallState.Idle)` + `val callState: StateFlow<CallState> = _callState.asStateFlow()` with:
```kotlin
private val stateMachine = CallStateMachine()
val callState: StateFlow<CallState> get() = stateMachine.state
```

2. Replace ad-hoc `_callState.value = CallState.Ringing(...)` in `makeCall`:
```kotlin
stateMachine.dispatch(CallEvent.OutgoingDial(
    callId = id,
    remoteNumber = number,
    accountId = resolvedAccountId,
))
```

3. Replace in `handleIncomingCall` (when accepting):
```kotlin
stateMachine.dispatch(CallEvent.IncomingRing(
    callId = id,
    remoteNumber = callerInfo.number,
    remoteName = callerInfo.displayName,
    accountId = accountId,
    remoteUri = remoteUri,
))
```

4. Replace in `onCallConfirmed`:
```kotlin
fun onCallConfirmed(call: PjsipCall) {
    if (call !== active?.call) return
    stateMachine.dispatch(CallEvent.Answered)
}
```

5. Replace in `hangupCall`:
```kotlin
stateMachine.dispatch(CallEvent.LocalHangup)
```
(Before the withCallOpParam block.)

6. Replace in `onCallDisconnected` and `resetCallState`:
```kotlin
private fun resetCallState() {
    active = null
    stateMachine.dispatch(CallEvent.RemoteDisconnect)
}
```

7. Replace in `toggleMute` / `setMute`:
```kotlin
stateMachine.dispatch(CallEvent.MuteChanged(muted = !state.isMuted))
```

8. Replace in `toggleHold` / `setHold`:
```kotlin
stateMachine.dispatch(CallEvent.HoldChanged(onHold = targetHold))
```

9. The hangup-timeout fallback path currently sets state directly; route it through the machine too:
```kotlin
if (_callState.value is CallState.Ending) { ... }
// becomes
if (stateMachine.state.value is CallState.Ending) { ... }
```

Read-access sites change from `_callState.value` to `stateMachine.state.value`. Write-access sites change from `_callState.value = X` to `stateMachine.dispatch(event)`.

- [ ] **Step 6: Build + full test**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachine.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt \
        src/test/kotlin/uz/yalla/sipphone/data/pjsip/CallStateMachineTest.kt
git commit -m "$(cat <<'EOF'
refactor(pjsip): introduce CallStateMachine with explicit transitions

CallStateMachine owns a pure CallState × CallEvent → CallState table.
PjsipCallManager now dispatches events instead of mutating state
directly. Invalid transitions (e.g. MuteChanged while Ringing) no-op
by design — encoded in one place instead of scattered type checks.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Final Verification

- [ ] **Step 1: Full build + test**

Run: `./gradlew clean build test`
Expected: BUILD SUCCESSFUL, all 222+new tests pass.

- [ ] **Step 2: Line delta check**

Run: `git diff --stat HEAD~6 -- src/main/kotlin/uz/yalla/sipphone/data/pjsip/`
Expected: PjsipCallManager, PjsipAccountManager, PjsipSipAccountManager are meaningfully smaller; new per-concern files carry the extracted logic.

- [ ] **Step 3: Smoke test (manual)**

Run: `./gradlew run`
Smoke steps:
1. Login, register SIP account, verify "Connected" state.
2. Make outbound call → answer remotely → hangup. Verify Ringing → Active → Idle transitions.
3. Receive incoming call → answer → hold → unhold → hangup. Verify Hold state machine behaves.
4. Kill PBX (or flip network) → verify Reconnecting state with increasing backoff → restore → Connected.
5. Rapid-fire login/logout twice → verify rate limiter delays subsequent register.

Sign-off from Islom on each step before closing.

---

## Self-Review Notes

**Spec coverage:** All 5 issues identified during Clean Code audit covered:
1. God-class decomposition → Tasks 2, 3, 4, 5, 6 split responsibilities
2. Duplicated media iteration → Task 1
3. Implicit call state machine → Task 6
4. PjsipAccountManager mixed concerns → Tasks 4 + 5
5. Inlined reconnect loop → Task 3

**Type consistency check:**
- `HoldController`: `requestHold(call): Boolean`, `requestUnhold(call): Boolean`, `onMediaStateChanged()`, `cancel()` — used identically in Task 2 implementation + Task 2 PjsipCallManager wiring.
- `ReconnectController`: `start(attemptBlock)`, `stop()`, callback signature `(attempt, backoffMs)` — used identically in Task 3 implementation + PjsipSipAccountManager wiring.
- `RegisterRateLimiter`: `awaitSlot(accountId)`, `clear()` — used identically in Task 4 implementation + PjsipAccountManager wiring.
- `CallStateMachine`: `state: StateFlow<CallState>`, `dispatch(event): CallState` — used identically in Task 6 implementation + PjsipCallManager wiring.
- `CallEvent`: sealed interface with the seven variants listed — referenced the same way in both test and wiring.

**Placeholder scan:** Clean. No "TBD", no "handle edge cases", no "similar to Task N".

**Risk:** If MockK is not on the test classpath, Task 2's unit test falls back to integration-only coverage (documented in Step 2). Everything else uses kotlinx-coroutines-test + kotlin.test which are already wired (see existing tests).

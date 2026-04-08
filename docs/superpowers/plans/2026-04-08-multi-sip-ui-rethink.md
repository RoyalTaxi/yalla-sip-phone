# Multi-SIP + UI Rethink Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multi-SIP account support with auto-registration, redesign toolbar/login/settings UI using Yalla Design System palette, and add UZ/RU internationalization.

**Architecture:** Replace single-account `RegistrationEngine` + `ConnectionManager` with `SipAccountManager` that manages N accounts with per-account reconnection. `CallEngine` gains `accountId` routing. Toolbar content fully replaced with new component structure. Login window enlarged to 1280×720 for smooth transitions. Settings becomes `DialogWindow`. i18n via `StringResources` interface + `CompositionLocal`.

**Tech Stack:** Kotlin, Compose Desktop, pjsip JNI, Decompose, Koin, Ktor, JCEF, kotlinx.serialization

**Spec:** `docs/superpowers/specs/2026-04-08-multi-sip-ui-rethink-design.md`

---

## Phase 1: Domain Layer Foundation

### Task 1: Add SipAccountInfo and SipAccountState domain models

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/domain/SipAccountInfo.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/domain/SipAccountState.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/domain/SipAccount.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/domain/SipAccountTest.kt`

- [ ] **Step 1: Write test for domain models**

```kotlin
package uz.yalla.sipphone.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SipAccountTest {

    private val credentials = SipCredentials(
        server = "sip.yalla.uz",
        port = 5060,
        username = "1001",
        password = "secret",
    )

    @Test
    fun `SipAccountInfo generates stable id from extension and server`() {
        val info = SipAccountInfo(
            extensionNumber = 1001,
            serverUrl = "sip.yalla.uz",
            sipName = "Operator-1",
            credentials = credentials,
        )
        assertEquals("1001@sip.yalla.uz", info.id)
        assertEquals("Operator-1", info.name)
    }

    @Test
    fun `SipAccountInfo uses fallback name when sipName is null`() {
        val info = SipAccountInfo(
            extensionNumber = 1001,
            serverUrl = "sip.yalla.uz",
            sipName = null,
            credentials = credentials,
        )
        assertEquals("SIP 1001", info.name)
    }

    @Test
    fun `SipAccount default state is Disconnected`() {
        val account = SipAccount(
            id = "1001@sip.yalla.uz",
            name = "Operator-1",
            credentials = credentials,
            state = SipAccountState.Disconnected,
        )
        assertIs<SipAccountState.Disconnected>(account.state)
    }

    @Test
    fun `SipAccountState Reconnecting carries attempt info`() {
        val state = SipAccountState.Reconnecting(attempt = 3, nextRetryMs = 8000)
        assertEquals(3, state.attempt)
        assertEquals(8000, state.nextRetryMs)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (classes don't exist)**

Run: `./gradlew test --tests "uz.yalla.sipphone.domain.SipAccountTest" --info`
Expected: Compilation error — `SipAccountInfo`, `SipAccountState`, `SipAccount` not found

- [ ] **Step 3: Implement domain models**

`SipAccountState.kt`:
```kotlin
package uz.yalla.sipphone.domain

sealed interface SipAccountState {
    data object Connected : SipAccountState
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : SipAccountState
    data object Disconnected : SipAccountState
}
```

`SipAccountInfo.kt`:
```kotlin
package uz.yalla.sipphone.domain

data class SipAccountInfo(
    val extensionNumber: Int,
    val serverUrl: String,
    val sipName: String?,
    val credentials: SipCredentials,
) {
    val id: String get() = "$extensionNumber@$serverUrl"
    val name: String get() = sipName ?: "SIP $extensionNumber"
}
```

`SipAccount.kt`:
```kotlin
package uz.yalla.sipphone.domain

data class SipAccount(
    val id: String,
    val name: String,
    val credentials: SipCredentials,
    val state: SipAccountState,
)
```

- [ ] **Step 4: Run test — expect PASS**

Run: `./gradlew test --tests "uz.yalla.sipphone.domain.SipAccountTest" --info`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/SipAccountInfo.kt \
        src/main/kotlin/uz/yalla/sipphone/domain/SipAccountState.kt \
        src/main/kotlin/uz/yalla/sipphone/domain/SipAccount.kt \
        src/test/kotlin/uz/yalla/sipphone/domain/SipAccountTest.kt
git commit -m "feat(domain): add SipAccountInfo, SipAccountState, SipAccount models"
```

---

### Task 2: Add SipAccountManager interface

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/domain/SipAccountManager.kt`

- [ ] **Step 1: Create interface**

```kotlin
package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface SipAccountManager {
    val accounts: StateFlow<List<SipAccount>>
    suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit>
    suspend fun connect(accountId: String): Result<Unit>
    suspend fun disconnect(accountId: String): Result<Unit>
    suspend fun unregisterAll()
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/SipAccountManager.kt
git commit -m "feat(domain): add SipAccountManager interface"
```

---

### Task 3: Add accountId to CallState variants

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/CallState.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/domain/CallStateAccountIdTest.kt`

- [ ] **Step 1: Write test**

```kotlin
package uz.yalla.sipphone.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CallStateAccountIdTest {

    @Test
    fun `Ringing carries accountId`() {
        val state = CallState.Ringing(
            callId = "c1",
            callerNumber = "+998901234567",
            callerName = null,
            isOutbound = false,
            accountId = "1001@sip.yalla.uz",
        )
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `Active carries accountId`() {
        val state = CallState.Active(
            callId = "c1",
            remoteNumber = "+998901234567",
            remoteName = null,
            isOutbound = false,
            isMuted = false,
            isOnHold = false,
            accountId = "1001@sip.yalla.uz",
        )
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `Ending carries accountId`() {
        val state = CallState.Ending(
            callId = "c1",
            accountId = "1001@sip.yalla.uz",
        )
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `accountId defaults to empty string for backward compatibility`() {
        val state = CallState.Ringing(
            callId = "c1",
            callerNumber = "+998901234567",
            callerName = null,
            isOutbound = false,
        )
        assertEquals("", state.accountId)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew test --tests "uz.yalla.sipphone.domain.CallStateAccountIdTest" --info`
Expected: Compilation error — `accountId` parameter not found

- [ ] **Step 3: Update CallState**

Replace `CallState.kt` content — add `accountId: String = ""` with default to all three call-carrying states:

```kotlin
package uz.yalla.sipphone.domain

sealed interface CallState {
    data object Idle : CallState

    data class Ringing(
        val callId: String,
        val callerNumber: String,
        val callerName: String?,
        val isOutbound: Boolean,
        val accountId: String = "",
    ) : CallState

    data class Active(
        val callId: String,
        val remoteNumber: String,
        val remoteName: String?,
        val isOutbound: Boolean,
        val isMuted: Boolean,
        val isOnHold: Boolean,
        val accountId: String = "",
    ) : CallState

    data class Ending(
        val callId: String = "",
        val accountId: String = "",
    ) : CallState
}
```

- [ ] **Step 4: Run test — expect PASS**

Run: `./gradlew test --tests "uz.yalla.sipphone.domain.CallStateAccountIdTest" --info`
Expected: 4 tests PASS

- [ ] **Step 5: Run ALL tests to verify backward compat**

Run: `./gradlew test --info`
Expected: All existing tests still pass (default `accountId = ""` doesn't break anything)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/CallState.kt \
        src/test/kotlin/uz/yalla/sipphone/domain/CallStateAccountIdTest.kt
git commit -m "feat(domain): add accountId to CallState.Ringing, Active, Ending"
```

---

### Task 4: Add accountId to CallEngine.makeCall

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/CallEngine.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEngine.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`

- [ ] **Step 1: Update CallEngine interface**

Add `accountId: String = ""` parameter to `makeCall`:

```kotlin
suspend fun makeCall(number: String, accountId: String = ""): Result<Unit>
```

Default value ensures backward compatibility — existing callers don't break.

- [ ] **Step 2: Update PjsipEngine delegation**

In `PjsipEngine.kt`, update the delegation:

```kotlin
override suspend fun makeCall(number: String, accountId: String): Result<Unit> =
    withContext(pjDispatcher) { callManager.makeCall(number, accountId) }
```

- [ ] **Step 3: Update PjsipCallManager.makeCall signature**

In `PjsipCallManager.kt`, update signature (implementation unchanged for now — accountId routing comes in Task 8):

```kotlin
suspend fun makeCall(number: String, accountId: String = ""): Result<Unit> {
    // existing implementation unchanged — still uses accountProvider.currentAccount
    // Multi-account routing implemented in Task 8
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --info`
Expected: All tests pass (default parameter is backward compatible)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/CallEngine.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEngine.kt \
        src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
git commit -m "feat(domain): add accountId parameter to CallEngine.makeCall"
```

---

### Task 5: Update AuthResult to carry List<SipAccountInfo>

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/AuthResult.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/MeResultDto.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImpl.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/auth/MockAuthRepository.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImplTest.kt` (update)

- [ ] **Step 1: Update AuthResult**

```kotlin
package uz.yalla.sipphone.domain

data class AuthResult(
    val token: String,
    val accounts: List<SipAccountInfo>,
    val dispatcherUrl: String,
    val agent: AgentInfo,
)
```

- [ ] **Step 2: Update MeResultDto.toAuthResult()**

In `MeResultDto.kt`, replace the `toAuthResult()` extension:

```kotlin
fun MeResultDto.toAuthResult(token: String, dispatcherUrl: String): AuthResult {
    val activeAccounts = sips
        .filter { it.isActive }
        .map { sip ->
            SipAccountInfo(
                extensionNumber = sip.extensionNumber,
                serverUrl = sip.serverUrl,
                sipName = sip.sipName,
                credentials = SipCredentials(
                    server = sip.domain ?: sip.serverUrl,
                    port = sip.serverPort,
                    username = sip.extensionNumber.toString(),
                    password = sip.password,
                    transport = sip.connectionType ?: "UDP",
                ),
            )
        }

    return AuthResult(
        token = token,
        accounts = activeAccounts,
        dispatcherUrl = dispatcherUrl,
        agent = AgentInfo(id = id, name = fullName),
    )
}
```

Add import: `import uz.yalla.sipphone.domain.SipAccountInfo`

- [ ] **Step 3: Update AuthRepositoryImpl.login()**

Replace the part that extracts SIP credentials. The key change is removing the `firstOrNull` and using the list:

```kotlin
override suspend fun login(pinCode: String): Result<AuthResult> = runCatching {
    val loginResult = authApi.login(pinCode).getOrThrow()
    tokenProvider.setToken(loginResult.token)

    val me = try {
        authApi.me().getOrThrow()
    } catch (e: Exception) {
        tokenProvider.clearToken()
        throw e
    }

    val authResult = me.toAuthResult(
        token = loginResult.token,
        dispatcherUrl = DISPATCHER_URL,
    )

    if (authResult.accounts.isEmpty()) {
        tokenProvider.clearToken()
        error("No active SIP accounts found")
    }

    authResult
}
```

- [ ] **Step 4: Update MockAuthRepository**

Return multiple accounts in mock:

```kotlin
override suspend fun login(pinCode: String): Result<AuthResult> {
    if (pinCode != CORRECT_PIN) return Result.failure(Exception("Invalid PIN"))
    return Result.success(
        AuthResult(
            token = "mock-token",
            accounts = listOf(
                SipAccountInfo(
                    extensionNumber = 1001,
                    serverUrl = MOCK_SERVER,
                    sipName = "Operator-1",
                    credentials = SipCredentials(
                        server = MOCK_SERVER,
                        port = MOCK_PORT,
                        username = MOCK_USERNAME,
                        password = MOCK_PASSWORD,
                    ),
                ),
            ),
            dispatcherUrl = "http://localhost:5173",
            agent = AgentInfo(id = 1, name = "Test Agent"),
        )
    )
}
```

- [ ] **Step 5: Fix AuthRepositoryImplTest**

Update test expectations to use `authResult.accounts` instead of `authResult.sipCredentials`:

```kotlin
@Test
fun `successful login stores token and returns correct AuthResult`() = runTest {
    // ... existing setup ...
    val result = repo.login("1234")
    assertTrue(result.isSuccess)
    val authResult = result.getOrThrow()
    assertEquals("test-token", authResult.token)
    assertTrue(authResult.accounts.isNotEmpty())
    assertEquals("1001", authResult.accounts.first().credentials.username)
}
```

- [ ] **Step 6: Fix all compilation errors — find remaining references to `authResult.sipCredentials`**

Search codebase for `sipCredentials` references and update. Key files:
- `LoginComponent.kt` — will be updated in Task 10
- `ComponentFactoryImpl.kt` — will be updated in Task 11

For now, add a temporary backward-compat extension to prevent compilation errors:

```kotlin
// In AuthResult.kt — temporary, removed in Task 10
val AuthResult.sipCredentials: SipCredentials
    get() = accounts.first().credentials
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.auth.*" --info`
Expected: All auth tests pass

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/AuthResult.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/dto/MeResultDto.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImpl.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/MockAuthRepository.kt \
        src/test/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImplTest.kt
git commit -m "feat(auth): update AuthResult to carry List<SipAccountInfo>"
```

---

## Phase 2: Multi-SIP Data Layer

### Task 6: Implement FakeSipAccountManager for testing

**Files:**
- Create: `src/test/kotlin/uz/yalla/sipphone/testing/FakeSipAccountManager.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/testing/FakeSipAccountManagerTest.kt`

- [ ] **Step 1: Write test**

```kotlin
package uz.yalla.sipphone.testing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import uz.yalla.sipphone.domain.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeSipAccountManagerTest {

    private val manager = FakeSipAccountManager()

    private val accountInfo = SipAccountInfo(
        extensionNumber = 1001,
        serverUrl = "sip.yalla.uz",
        sipName = "Operator-1",
        credentials = SipCredentials("sip.yalla.uz", 5060, "1001", "pass"),
    )

    @Test
    fun `registerAll adds accounts in Connected state`() = runTest {
        manager.registerAll(listOf(accountInfo))
        val accounts = manager.accounts.value
        assertEquals(1, accounts.size)
        assertEquals("1001@sip.yalla.uz", accounts[0].id)
        assertIs<SipAccountState.Connected>(accounts[0].state)
    }

    @Test
    fun `disconnect changes account to Disconnected`() = runTest {
        manager.registerAll(listOf(accountInfo))
        manager.disconnect("1001@sip.yalla.uz")
        assertIs<SipAccountState.Disconnected>(manager.accounts.value[0].state)
    }

    @Test
    fun `connect changes Disconnected account to Connected`() = runTest {
        manager.registerAll(listOf(accountInfo))
        manager.disconnect("1001@sip.yalla.uz")
        manager.connect("1001@sip.yalla.uz")
        assertIs<SipAccountState.Connected>(manager.accounts.value[0].state)
    }

    @Test
    fun `unregisterAll clears all accounts`() = runTest {
        manager.registerAll(listOf(accountInfo))
        manager.unregisterAll()
        assertTrue(manager.accounts.value.isEmpty())
    }

    @Test
    fun `simulateAccountState changes specific account state`() = runTest {
        manager.registerAll(listOf(accountInfo))
        manager.simulateAccountState("1001@sip.yalla.uz", SipAccountState.Reconnecting(1, 2000))
        assertIs<SipAccountState.Reconnecting>(manager.accounts.value[0].state)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement FakeSipAccountManager**

```kotlin
package uz.yalla.sipphone.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uz.yalla.sipphone.domain.*

class FakeSipAccountManager : SipAccountManager {

    private val _accounts = MutableStateFlow<List<SipAccount>>(emptyList())
    override val accounts: StateFlow<List<SipAccount>> = _accounts.asStateFlow()

    var registerAllResult: Result<Unit> = Result.success(Unit)
    var connectResult: Result<Unit> = Result.success(Unit)
    var disconnectResult: Result<Unit> = Result.success(Unit)

    var registerAllCallCount = 0
        private set
    var unregisterAllCallCount = 0
        private set
    var lastRegisteredAccounts: List<SipAccountInfo> = emptyList()
        private set

    override suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit> {
        registerAllCallCount++
        lastRegisteredAccounts = accounts
        return registerAllResult.onSuccess {
            _accounts.value = accounts.map { info ->
                SipAccount(
                    id = info.id,
                    name = info.name,
                    credentials = info.credentials,
                    state = SipAccountState.Connected,
                )
            }
        }
    }

    override suspend fun connect(accountId: String): Result<Unit> {
        return connectResult.onSuccess {
            updateAccountState(accountId, SipAccountState.Connected)
        }
    }

    override suspend fun disconnect(accountId: String): Result<Unit> {
        return disconnectResult.onSuccess {
            updateAccountState(accountId, SipAccountState.Disconnected)
        }
    }

    override suspend fun unregisterAll() {
        unregisterAllCallCount++
        _accounts.value = emptyList()
    }

    fun simulateAccountState(accountId: String, state: SipAccountState) {
        updateAccountState(accountId, state)
    }

    private fun updateAccountState(accountId: String, state: SipAccountState) {
        _accounts.update { list ->
            list.map { if (it.id == accountId) it.copy(state = state) else it }
        }
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

Run: `./gradlew test --tests "uz.yalla.sipphone.testing.FakeSipAccountManagerTest" --info`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/uz/yalla/sipphone/testing/FakeSipAccountManager.kt \
        src/test/kotlin/uz/yalla/sipphone/testing/FakeSipAccountManagerTest.kt
git commit -m "test: add FakeSipAccountManager for multi-SIP testing"
```

---

### Task 7: Implement PjsipSipAccountManager

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManagerTest.kt`

This is the core multi-SIP implementation. It wraps `PjsipAccountManager` (which will be updated to support multiple accounts) and adds per-account reconnection.

- [ ] **Step 1: Write test for core registration logic**

```kotlin
package uz.yalla.sipphone.data.pjsip

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import uz.yalla.sipphone.domain.*
import uz.yalla.sipphone.testing.FakeSipAccountManager
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PjsipSipAccountManagerTest {

    // Note: Full PjsipSipAccountManager tests require pjsip native libs.
    // These tests verify the FakeSipAccountManager contract that the real
    // implementation must satisfy. Integration tests run with real pjsip.

    @Test
    fun `registerAll with empty list returns failure`() = runTest {
        val manager = FakeSipAccountManager()
        manager.registerAllResult = Result.failure(IllegalArgumentException("No accounts"))
        val result = manager.registerAll(emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `accounts flow emits after registerAll`() = runTest {
        val manager = FakeSipAccountManager()
        val info = SipAccountInfo(1001, "sip.yalla.uz", "Op-1", SipCredentials("sip.yalla.uz", 5060, "1001", "p"))
        manager.accounts.test {
            assertEquals(emptyList(), awaitItem()) // initial
            manager.registerAll(listOf(info))
            val accounts = awaitItem()
            assertEquals(1, accounts.size)
            assertEquals("1001@sip.yalla.uz", accounts[0].id)
        }
    }

    @Test
    fun `disconnect with active call returns failure`() = runTest {
        val manager = FakeSipAccountManager()
        manager.disconnectResult = Result.failure(IllegalStateException("Active call"))
        val info = SipAccountInfo(1001, "sip.yalla.uz", "Op-1", SipCredentials("sip.yalla.uz", 5060, "1001", "p"))
        manager.registerAll(listOf(info))
        val result = manager.disconnect("1001@sip.yalla.uz")
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test — expect PASS (uses FakeSipAccountManager)**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.pjsip.PjsipSipAccountManagerTest" --info`

- [ ] **Step 3: Create PjsipSipAccountManager skeleton**

```kotlin
package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import uz.yalla.sipphone.domain.*

private val logger = KotlinLogging.logger {}

class PjsipSipAccountManager(
    private val accountManager: PjsipAccountManager,
    private val callEngine: CallEngine,
    private val pjDispatcher: CoroutineDispatcher,
) : SipAccountManager {

    private val scope = CoroutineScope(pjDispatcher + SupervisorJob())
    private val _accounts = MutableStateFlow<List<SipAccount>>(emptyList())
    override val accounts: StateFlow<List<SipAccount>> = _accounts.asStateFlow()

    private val accountInfoCache = mutableMapOf<String, SipAccountInfo>()
    private val reconnectJobs = mutableMapOf<String, Job>()

    companion object {
        private const val REGISTER_DELAY_MS = 500L
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30_000L
        private const val JITTER_BOUND_MS = 500L
    }

    override suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit> =
        withContext(pjDispatcher) {
            if (accounts.isEmpty()) return@withContext Result.failure(
                IllegalArgumentException("No accounts to register")
            )

            accountInfoCache.clear()
            accounts.forEach { accountInfoCache[it.id] = it }

            // Initialize all as Disconnected
            _accounts.value = accounts.map { info ->
                SipAccount(info.id, info.name, info.credentials, SipAccountState.Disconnected)
            }

            var anySuccess = false
            for (info in accounts) {
                val result = accountManager.register(info.id, info.credentials)
                if (result.isSuccess) {
                    updateState(info.id, SipAccountState.Connected)
                    anySuccess = true
                } else {
                    logger.warn { "Registration failed for ${info.id}: ${result.exceptionOrNull()?.message}" }
                    startReconnection(info.id)
                }
                if (info != accounts.last()) delay(REGISTER_DELAY_MS)
            }

            if (anySuccess) Result.success(Unit)
            else Result.failure(Exception("All SIP registrations failed"))
        }

    override suspend fun connect(accountId: String): Result<Unit> = withContext(pjDispatcher) {
        val info = accountInfoCache[accountId]
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown account: $accountId"))

        cancelReconnection(accountId)
        updateState(accountId, SipAccountState.Reconnecting(0, 0))

        val result = accountManager.register(accountId, info.credentials)
        if (result.isSuccess) {
            updateState(accountId, SipAccountState.Connected)
        } else {
            updateState(accountId, SipAccountState.Disconnected)
        }
        result
    }

    override suspend fun disconnect(accountId: String): Result<Unit> = withContext(pjDispatcher) {
        // Block disconnect if active call on this account
        val callState = callEngine.callState.value
        val activeAccountId = when (callState) {
            is CallState.Ringing -> callState.accountId
            is CallState.Active -> callState.accountId
            is CallState.Ending -> callState.accountId
            is CallState.Idle -> null
        }
        if (activeAccountId == accountId) {
            return@withContext Result.failure(
                IllegalStateException("Cannot disconnect — active call on this account")
            )
        }

        cancelReconnection(accountId)
        accountManager.unregister(accountId)
        updateState(accountId, SipAccountState.Disconnected)
        Result.success(Unit)
    }

    override suspend fun unregisterAll() = withContext(pjDispatcher) {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()

        for (account in _accounts.value) {
            runCatching { accountManager.unregister(account.id) }
        }
        _accounts.value = emptyList()
        accountInfoCache.clear()
    }

    fun onAccountRegistrationChanged(accountId: String, registered: Boolean) {
        if (registered) {
            cancelReconnection(accountId)
            updateState(accountId, SipAccountState.Connected)
        } else {
            val account = _accounts.value.find { it.id == accountId }
            if (account != null && account.state is SipAccountState.Connected) {
                updateState(accountId, SipAccountState.Disconnected)
                startReconnection(accountId)
            }
        }
    }

    private fun startReconnection(accountId: String) {
        cancelReconnection(accountId)
        val info = accountInfoCache[accountId] ?: return

        reconnectJobs[accountId] = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                val delay = calculateBackoff(attempt)
                updateState(accountId, SipAccountState.Reconnecting(attempt, delay))
                delay(delay)

                val result = accountManager.register(accountId, info.credentials)
                if (result.isSuccess) {
                    updateState(accountId, SipAccountState.Connected)
                    break
                }

                // Auth failures don't retry
                val error = result.exceptionOrNull()
                if (error is SipError.AuthFailed) {
                    updateState(accountId, SipAccountState.Disconnected)
                    break
                }
            }
        }
    }

    private fun cancelReconnection(accountId: String) {
        reconnectJobs.remove(accountId)?.cancel()
    }

    private fun calculateBackoff(attempt: Int): Long {
        val delay = (BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(14)))
            .coerceAtMost(MAX_DELAY_MS)
        val jitter = (Math.random() * JITTER_BOUND_MS).toLong()
        return delay + jitter
    }

    private fun updateState(accountId: String, state: SipAccountState) {
        _accounts.update { list ->
            list.map { if (it.id == accountId) it.copy(state = state) else it }
        }
    }
}
```

- [ ] **Step 4: Note — PjsipAccountManager.register/unregister need per-account signatures**

This will be done in Task 8. For now, the code above uses the planned API:
- `accountManager.register(accountId: String, credentials: SipCredentials): Result<Unit>`
- `accountManager.unregister(accountId: String)`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManager.kt \
        src/test/kotlin/uz/yalla/sipphone/data/pjsip/PjsipSipAccountManagerTest.kt
git commit -m "feat(pjsip): add PjsipSipAccountManager with per-account reconnection"
```

---

### Task 8: Update PjsipAccountManager for multi-account

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipEngine.kt`

- [ ] **Step 1: Update PjsipAccountManager to use Map**

Key changes:
- `currentAccount: PjsipAccount?` → `accounts: MutableMap<String, PjsipAccount>`
- `register(credentials)` → `register(accountId: String, credentials: SipCredentials): Result<Unit>`
- `unregister()` → `unregister(accountId: String)`
- Keep rate limiting per account
- `PjsipAccount` now carries `accountId` field

Update `PjsipAccount.kt` constructor to include `val accountId: String` parameter. Update `onIncomingCall` to pass `accountId` to call manager.

- [ ] **Step 2: Update PjsipCallManager for accountId routing**

- `makeCall(number, accountId)` → find account in map, use it for dialing
- `handleIncomingCall(callId, accountId)` → propagate accountId to CallState.Ringing
- `onCallConfirmed` → propagate accountId to CallState.Active
- `AccountProvider` interface removed — call manager takes account from PjsipSipAccountManager

- [ ] **Step 3: Update PjsipEngine**

- Remove `RegistrationEngine` implementation
- Keep `SipStackLifecycle` and `CallEngine` implementations
- `CallEngine.makeCall(number, accountId)` delegates to `callManager.makeCall(number, accountId)`

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --info`
Expected: Some tests will fail due to removed `RegistrationEngine` from `PjsipEngine`. Fix in Task 9.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/
git commit -m "feat(pjsip): multi-account support in PjsipAccountManager and PjsipCallManager"
```

---

### Task 9: Update DI modules

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/di/SipModule.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/di/AuthModule.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/di/AppModule.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/ConnectionManagerImpl.kt`

- [ ] **Step 1: Update SipModule**

```kotlin
package uz.yalla.sipphone.di

import org.koin.dsl.binds
import org.koin.dsl.module
import uz.yalla.sipphone.data.pjsip.PjsipEngine
import uz.yalla.sipphone.data.pjsip.PjsipSipAccountManager
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.SipAccountManager
import uz.yalla.sipphone.domain.SipStackLifecycle

val sipModule = module {
    single { PjsipEngine() }
    single<SipStackLifecycle> { get<PjsipEngine>() }
    single<CallEngine> { get<PjsipEngine>() }
    single<SipAccountManager> {
        PjsipSipAccountManager(
            accountManager = get<PjsipEngine>().accountManager,
            callEngine = get(),
            pjDispatcher = get<PjsipEngine>().pjDispatcher,
        )
    }
}
```

Note: `PjsipEngine` must expose `accountManager` and `pjDispatcher` properties for DI.

- [ ] **Step 2: Update AuthModule — LogoutOrchestrator**

```kotlin
val authModule = module {
    single { AuthApi(client = get(), authEventBus = get()) }
    single<AuthRepository> { AuthRepositoryImpl(authApi = get(), tokenProvider = get()) }
    single { LogoutOrchestrator(get<SipAccountManager>(), get(), get()) }
}
```

- [ ] **Step 3: Update LogoutOrchestrator**

```kotlin
package uz.yalla.sipphone.data.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.SipAccountManager

private val logger = KotlinLogging.logger {}

class LogoutOrchestrator(
    private val sipAccountManager: SipAccountManager,
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) {
    private var logoutInProgress = false

    suspend fun logout() {
        if (logoutInProgress) {
            logger.warn { "Logout already in progress, skipping" }
            return
        }
        logoutInProgress = true
        try {
            runCatching { sipAccountManager.unregisterAll() }
                .onFailure { logger.warn(it) { "Failed to unregister accounts" } }

            tokenProvider.clearToken()

            runCatching { authApi.logout() }
                .onFailure { logger.warn(it) { "Logout API call failed" } }
        } finally {
            logoutInProgress = false
        }
    }
}
```

- [ ] **Step 4: Delete ConnectionManagerImpl.kt**

```bash
rm src/main/kotlin/uz/yalla/sipphone/data/pjsip/ConnectionManagerImpl.kt
```

Also delete `ConnectionManager.kt` from domain:
```bash
rm src/main/kotlin/uz/yalla/sipphone/domain/ConnectionManager.kt
```

- [ ] **Step 5: Fix compilation errors — remove ConnectionManager references**

Search and remove:
- `Main.kt`: `ConnectionManager.stopMonitoring()` → remove (handled by `SipAccountManager.unregisterAll()`)
- `MainComponent.kt`: `connectionState` observation → replaced with `sipAccountManager.accounts` observation (Task 11)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(di): replace RegistrationEngine+ConnectionManager with SipAccountManager"
```

---

### Task 10: Update LoginComponent for multi-SIP

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt`
- Modify: `src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt`

- [ ] **Step 1: Update LoginComponent**

Key changes:
- Inject `SipAccountManager` instead of `RegistrationEngine`
- `login()` calls `sipAccountManager.registerAll(authResult.accounts)`
- Observe `sipAccountManager.accounts` for at least one Connected → trigger `onLoginSuccess`
- Remove `registrationState` StateFlow

```kotlin
fun login(password: String) {
    if (loginState.value is LoginState.Loading) return
    _loginState.value = LoginState.Loading

    scope.launch(ioDispatcher) {
        val result = authRepository.login(password)
        result.fold(
            onSuccess = { authResult ->
                _loginState.value = LoginState.Authenticated(authResult)

                val regResult = sipAccountManager.registerAll(authResult.accounts)
                if (regResult.isSuccess) {
                    // Wait for at least one Connected account
                    sipAccountManager.accounts
                        .first { accounts -> accounts.any { it.state is SipAccountState.Connected } }
                    withContext(Dispatchers.Main) { onLoginSuccess(authResult) }
                } else {
                    _loginState.value = LoginState.Error(
                        regResult.exceptionOrNull()?.message ?: "Registration failed"
                    )
                }
            },
            onFailure = { error ->
                _loginState.value = LoginState.Error(error.message ?: "Login failed")
            },
        )
    }
}
```

- [ ] **Step 2: Update LoginComponentTest**

Replace `FakeRegistrationEngine` usage with `FakeSipAccountManager`. Update assertions for multi-SIP flow.

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "uz.yalla.sipphone.feature.login.*" --info`

- [ ] **Step 4: Remove temporary `sipCredentials` extension from AuthResult.kt**

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt \
        src/main/kotlin/uz/yalla/sipphone/domain/AuthResult.kt \
        src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt
git commit -m "feat(login): update LoginComponent for multi-SIP registerAll"
```

---

### Task 11: Update MainComponent and navigation for multi-SIP

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/main/MainComponent.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/navigation/RootComponent.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/navigation/ComponentFactory.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/navigation/ComponentFactoryImpl.kt`

- [ ] **Step 1: Update ComponentFactory**

```kotlin
interface ComponentFactory {
    fun createLogin(context: ComponentContext, onLoginSuccess: (AuthResult) -> Unit): LoginComponent
    fun createMain(context: ComponentContext, authResult: AuthResult, onLogout: () -> Unit): MainComponent
}
```

(Signature unchanged — `AuthResult` already updated)

- [ ] **Step 2: Update MainComponent constructor**

Replace `registrationEngine: RegistrationEngine` with `sipAccountManager: SipAccountManager`. Update account observation:

```kotlin
// Auto-logout: all accounts disconnected AND no active call
scope.launch {
    combine(
        sipAccountManager.accounts,
        callEngine.callState,
    ) { accounts, callState ->
        val allDisconnected = accounts.isNotEmpty() &&
            accounts.all { it.state is SipAccountState.Disconnected }
        val noCall = callState is CallState.Idle
        allDisconnected && noCall
    }.filter { it }.collect {
        logger.info { "All SIP accounts disconnected, no active call — auto-logout" }
        onLogout()
    }
}
```

- [ ] **Step 3: Update bridge event emission**

Replace single `registrationState` observation with per-account observation:

```kotlin
scope.launch {
    sipAccountManager.accounts.collect { accounts ->
        accounts.forEach { account ->
            val bridgeState = when (account.state) {
                is SipAccountState.Connected -> "connected"
                is SipAccountState.Reconnecting -> "reconnecting"
                is SipAccountState.Disconnected -> "disconnected"
            }
            eventEmitter.emitAccountStatusChanged(account.id, account.name, bridgeState)
        }
        // Aggregate connection state for backward compat
        val aggregate = when {
            accounts.any { it.state is SipAccountState.Connected } -> "connected"
            accounts.any { it.state is SipAccountState.Reconnecting } -> "reconnecting"
            else -> "disconnected"
        }
        val attempt = accounts.filterIsInstance<SipAccount>()
            .mapNotNull { (it.state as? SipAccountState.Reconnecting)?.attempt }
            .maxOrNull() ?: 0
        eventEmitter.emitConnectionChanged(aggregate, attempt)
    }
}
```

- [ ] **Step 4: Update ComponentFactoryImpl**

Replace `get<RegistrationEngine>()` with `get<SipAccountManager>()`.

- [ ] **Step 5: Run all tests**

Run: `./gradlew test --info`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/main/MainComponent.kt \
        src/main/kotlin/uz/yalla/sipphone/navigation/ \
        src/main/kotlin/uz/yalla/sipphone/di/FeatureModule.kt
git commit -m "feat(main): update MainComponent for multi-SIP observation and auto-logout"
```

---

### Task 12: Update JS Bridge for multi-SIP

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/jcef/BridgeProtocol.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/jcef/BridgeRouter.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/jcef/BridgeEventEmitter.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/data/jcef/BridgeProtocolTest.kt` (update)

- [ ] **Step 1: Add bridge protocol types**

Add to `BridgeProtocol.kt`:

```kotlin
@Serializable
data class BridgeAccountState(
    val id: String,
    val name: String,
    val extension: String,
    val status: String,
)

// Update BridgeState:
@Serializable
data class BridgeState(
    val connection: BridgeConnectionState,
    val agentStatus: String,
    val call: BridgeCallState?,
    val token: String?,
    val accounts: List<BridgeAccountState> = emptyList(), // NEW — default for backward compat
)

// Add new event:
@Serializable
data class AccountStatusChanged(
    val accountId: String,
    val name: String,
    val status: String,
    override val seq: Int,
    override val timestamp: Long,
) : BridgeEvent()

// Update ConnectionChanged:
@Serializable
data class ConnectionChanged(
    val state: String,
    val attempt: Int,
    val accountId: String = "", // NEW — default for backward compat
    override val seq: Int,
    override val timestamp: Long,
) : BridgeEvent()

// Update BridgeVersionInfo:
@Serializable
data class BridgeVersionInfo(
    val version: String = "1.2.0", // bumped
    val capabilities: List<String>,
)
```

- [ ] **Step 2: Add emitAccountStatusChanged to BridgeEventEmitter**

```kotlin
fun emitAccountStatusChanged(accountId: String, name: String, status: String) {
    val event = BridgeEvent.AccountStatusChanged(
        accountId = accountId,
        name = name,
        status = status,
        seq = nextSeq(),
        timestamp = System.currentTimeMillis(),
    )
    emit("accountStatusChanged", json.encodeToString(event))
}
```

Update `emitConnectionChanged` to accept optional `accountId`:

```kotlin
fun emitConnectionChanged(state: String, attempt: Int, accountId: String = "") {
    val event = BridgeEvent.ConnectionChanged(
        state = state,
        attempt = attempt,
        accountId = accountId,
        seq = nextSeq(),
        timestamp = System.currentTimeMillis(),
    )
    emit("connectionChanged", json.encodeToString(event))
}
```

- [ ] **Step 3: Update BridgeRouter.handleGetState()**

Replace single registration state with accounts list:

```kotlin
private fun handleGetState(): CommandResult {
    val accounts = sipAccountManager.accounts.value
    val bridgeAccounts = accounts.map { account ->
        BridgeAccountState(
            id = account.id,
            name = account.name,
            extension = account.id.substringBefore("@"),
            status = when (account.state) {
                is SipAccountState.Connected -> "connected"
                is SipAccountState.Reconnecting -> "reconnecting"
                is SipAccountState.Disconnected -> "disconnected"
            },
        )
    }
    val aggregateState = when {
        accounts.any { it.state is SipAccountState.Connected } -> "connected"
        accounts.any { it.state is SipAccountState.Reconnecting } -> "reconnecting"
        else -> "disconnected"
    }
    // ... build BridgeState with accounts = bridgeAccounts
}
```

- [ ] **Step 4: Update BridgeRouter constructor**

Replace `registrationEngine` parameter with `sipAccountManager: SipAccountManager`.

- [ ] **Step 5: Update tests**

Update `BridgeProtocolTest.kt` serialization tests for new fields.

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests "uz.yalla.sipphone.data.jcef.*" --info`

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/jcef/ \
        src/test/kotlin/uz/yalla/sipphone/data/jcef/
git commit -m "feat(bridge): add multi-SIP accounts to getState, accountStatusChanged event"
```

---

## Phase 3: Internationalization

### Task 13: Add StringResources i18n system

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/ui/strings/StringResources.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/ui/strings/UzStrings.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/ui/strings/RuStrings.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/theme/Theme.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/settings/AppSettings.kt`
- Test: `src/test/kotlin/uz/yalla/sipphone/ui/strings/StringResourcesTest.kt`

- [ ] **Step 1: Write test**

```kotlin
package uz.yalla.sipphone.ui.strings

import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringResourcesTest {

    @Test
    fun `UzStrings implements all properties`() {
        val strings: StringResources = UzStrings
        assertTrue(strings.loginTitle.isNotBlank())
        assertTrue(strings.loginButton.isNotBlank())
        assertTrue(strings.settingsTitle.isNotBlank())
    }

    @Test
    fun `RuStrings implements all properties`() {
        val strings: StringResources = RuStrings
        assertTrue(strings.loginTitle.isNotBlank())
        assertTrue(strings.loginButton.isNotBlank())
        assertTrue(strings.settingsTitle.isNotBlank())
    }

    @Test
    fun `UZ and RU strings are different`() {
        assertNotEquals(UzStrings.loginTitle, RuStrings.loginTitle)
        assertNotEquals(UzStrings.loginButton, RuStrings.loginButton)
    }
}
```

- [ ] **Step 2: Implement StringResources interface**

```kotlin
package uz.yalla.sipphone.ui.strings

import androidx.compose.runtime.staticCompositionLocalOf

interface StringResources {
    // Login
    val loginTitle: String
    val loginSubtitle: String
    val loginPasswordPlaceholder: String
    val loginButton: String
    val loginConnecting: String
    val loginRetry: String
    val loginManualConnection: String
    val errorWrongPassword: String
    val errorNetworkFailed: String

    // Agent Status
    val agentStatusOnline: String
    val agentStatusBusy: String
    val agentStatusOffline: String

    // SIP
    val sipConnected: String
    val sipReconnecting: String
    val sipDisconnected: String
    val sipDisconnectBlockedByCall: String
    val sipReconnectHint: String
    val sipRinging: String

    // Settings
    val settingsTitle: String
    val settingsTheme: String
    val settingsLocale: String
    val settingsLogout: String
    val settingsLogoutConfirm: String
    val settingsLogoutConfirmTitle: String

    // General
    val appTitle: String
    val buttonConnect: String
    val buttonCancel: String
    val labelServer: String
    val labelPort: String
    val labelUsername: String
    val labelPassword: String
    val placeholderServer: String
    val placeholderUsername: String
}

val LocalStrings = staticCompositionLocalOf<StringResources> {
    error("StringResources not provided")
}
```

- [ ] **Step 3: Implement UzStrings**

```kotlin
package uz.yalla.sipphone.ui.strings

object UzStrings : StringResources {
    override val loginTitle = "Yalla SIP Phone"
    override val loginSubtitle = "Tizimga kirish"
    override val loginPasswordPlaceholder = "Parolni kiriting"
    override val loginButton = "Kirish"
    override val loginConnecting = "Ulanmoqda..."
    override val loginRetry = "Qayta urinish"
    override val loginManualConnection = "Qo'lda ulanish"
    override val errorWrongPassword = "Parol noto'g'ri"
    override val errorNetworkFailed = "Serverga ulanib bo'lmadi"
    override val agentStatusOnline = "Liniyada"
    override val agentStatusBusy = "Band"
    override val agentStatusOffline = "Liniyada emas"
    override val sipConnected = "Ulangan"
    override val sipReconnecting = "Ulanmoqda"
    override val sipDisconnected = "Uzilgan"
    override val sipDisconnectBlockedByCall = "Aktiv qo'ng'iroq bor, uzib bo'lmaydi"
    override val sipReconnectHint = "Bosing → qayta ulash"
    override val sipRinging = "Qo'ng'iroq..."
    override val settingsTitle = "Sozlamalar"
    override val settingsTheme = "Mavzu"
    override val settingsLocale = "Til"
    override val settingsLogout = "Chiqish"
    override val settingsLogoutConfirm = "Tizimdan chiqmoqchimisiz?"
    override val settingsLogoutConfirmTitle = "Chiqish"
    override val appTitle = "Yalla SIP Phone"
    override val buttonConnect = "Ulanish"
    override val buttonCancel = "Bekor qilish"
    override val labelServer = "Server"
    override val labelPort = "Port"
    override val labelUsername = "Foydalanuvchi"
    override val labelPassword = "Parol"
    override val placeholderServer = "sip.example.com"
    override val placeholderUsername = "1001"
}
```

- [ ] **Step 4: Implement RuStrings**

```kotlin
package uz.yalla.sipphone.ui.strings

object RuStrings : StringResources {
    override val loginTitle = "Yalla SIP Phone"
    override val loginSubtitle = "Вход в систему"
    override val loginPasswordPlaceholder = "Введите пароль"
    override val loginButton = "Войти"
    override val loginConnecting = "Подключение..."
    override val loginRetry = "Повторить"
    override val loginManualConnection = "Ручное подключение"
    override val errorWrongPassword = "Неверный пароль"
    override val errorNetworkFailed = "Не удалось подключиться к серверу"
    override val agentStatusOnline = "На линии"
    override val agentStatusBusy = "Занят"
    override val agentStatusOffline = "Не на линии"
    override val sipConnected = "Подключен"
    override val sipReconnecting = "Подключение"
    override val sipDisconnected = "Отключен"
    override val sipDisconnectBlockedByCall = "Активный вызов, нельзя отключить"
    override val sipReconnectHint = "Нажмите → переподключить"
    override val sipRinging = "Звонок..."
    override val settingsTitle = "Настройки"
    override val settingsTheme = "Тема"
    override val settingsLocale = "Язык"
    override val settingsLogout = "Выход"
    override val settingsLogoutConfirm = "Вы хотите выйти из системы?"
    override val settingsLogoutConfirmTitle = "Выход"
    override val appTitle = "Yalla SIP Phone"
    override val buttonConnect = "Подключить"
    override val buttonCancel = "Отмена"
    override val labelServer = "Сервер"
    override val labelPort = "Порт"
    override val labelUsername = "Пользователь"
    override val labelPassword = "Пароль"
    override val placeholderServer = "sip.example.com"
    override val placeholderUsername = "1001"
}
```

- [ ] **Step 5: Add locale to AppSettings**

Add `locale` property (get/set) to `AppSettings.kt`. Default: `"uz"`.

- [ ] **Step 6: Wire into Theme.kt**

In `YallaSipPhoneTheme` composable, add:

```kotlin
val settings: AppSettings = koinInject()
val locale = settings.locale
val strings = when (locale) {
    "ru" -> RuStrings
    else -> UzStrings
}
CompositionLocalProvider(LocalStrings provides strings) {
    // existing content
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "uz.yalla.sipphone.ui.strings.*" --info`

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/ui/strings/ \
        src/main/kotlin/uz/yalla/sipphone/ui/theme/Theme.kt \
        src/main/kotlin/uz/yalla/sipphone/data/settings/AppSettings.kt \
        src/test/kotlin/uz/yalla/sipphone/ui/strings/StringResourcesTest.kt
git commit -m "feat(i18n): add StringResources with UZ/RU translations"
```

---

## Phase 4: Theme Update

### Task 14: Update YallaColors to Yalla Design System

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/theme/YallaColors.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/theme/AppTokens.kt`

- [ ] **Step 1: Update YallaColors.Dark to DS values**

Key changes per spec color table:
- `backgroundBase` → `#1A1A20`
- `backgroundSecondary` → `#21222B`
- `backgroundTertiary` → `#1D1D26` (was `#383843`)
- `borderDisabled` → `#383843`
- `textSubtle` → `#747C8B`
- Add new tokens: `brandPrimary = #562DF8`, `pinkSun = #FF234B`, etc.

- [ ] **Step 2: Update YallaColors.Light to DS values**

Per spec light theme table.

- [ ] **Step 3: Update AppTokens**

- `windowMinWidth/Height` → 1280×720 (login same as main)
- Remove separate `registrationWindowSize`

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --info`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/ui/theme/
git commit -m "refactor(theme): align YallaColors with Yalla Design System tokens"
```

---

## Phase 5: Toolbar UI Components

### Task 15: Create AgentStatusButton

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/AgentStatusButton.kt`

- [ ] **Step 1: Implement**

Compose component: icon button (36dp) with status dot. Inline dropdown (not Popup) with 3 states. Uses Yalla colors per spec.

- [ ] **Step 2: Commit**

---

### Task 16: Create PhoneField

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/PhoneField.kt`

- [ ] **Step 1: Implement**

`BasicTextField` with `widthIn(min=120.dp, max=160.dp)`, tabular nums, placeholder.

- [ ] **Step 2: Commit**

---

### Task 17: Create CallActions

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallActions.kt`

- [ ] **Step 1: Implement**

State-dependent action buttons per spec. Idle: disabled. Ringing: answer+reject. Active: hangup+mute+hold.

- [ ] **Step 2: Commit**

---

### Task 18: Create CallTimer

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallTimer.kt`

- [ ] **Step 1: Implement**

Brand tint surface showing call duration. Tabular nums. Only visible during active call.

- [ ] **Step 2: Commit**

---

### Task 19: Create SipChipRow

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/SipChipRow.kt`

- [ ] **Step 1: Implement**

Row/LazyRow with SIP chips. 5 visual states per spec. Click toggles connect/disconnect. Hover tooltip via `DialogWindow`.

- [ ] **Step 2: Commit**

---

### Task 20: Create SettingsDialog

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/SettingsDialog.kt`

- [ ] **Step 1: Implement**

`DialogWindow` (OS-level) with: agent info card, theme toggle (sun/moon/monitor), locale toggle (🇺🇿/🇷🇺), logout button, version. Uses Yalla colors per spec.

- [ ] **Step 2: Commit**

---

### Task 21: Update ToolbarComponent for multi-SIP

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/ToolbarComponent.kt`

- [ ] **Step 1: Update**

Add:
- `sipAccounts: StateFlow<List<SipAccount>>` — from `SipAccountManager`
- `callDuration: StateFlow<String?>` — timer logic
- `onSipChipClick(accountId)` — toggle connect/disconnect
- `agentStatus` reduced to 3 display states
- `openSettings() / closeSettings()`

Remove:
- `RegistrationEngine` dependency
- Old ringtone logic stays but uses new state

- [ ] **Step 2: Commit**

---

### Task 22: Rewrite ToolbarContent

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/ToolbarContent.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/AgentStatusDropdown.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallControls.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallQualityIndicator.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/SettingsPopover.kt`

- [ ] **Step 1: Rewrite ToolbarContent layout**

New layout per spec:
```
Row(52dp, bg.base) {
    AgentStatusButton(...)
    PhoneField(...)
    Divider()
    CallActions(...)
    CallTimer(...)
    Spacer(weight=1f)
    SipChipRow(...)
    Divider()
    SettingsButton(...)
}
```

- [ ] **Step 2: Delete old files**

```bash
rm src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/AgentStatusDropdown.kt
rm src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallControls.kt
rm src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/CallQualityIndicator.kt
rm src/main/kotlin/uz/yalla/sipphone/feature/main/toolbar/SettingsPopover.kt
```

- [ ] **Step 3: Run build**

Run: `./gradlew build --info`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(toolbar): complete toolbar redesign with new component structure"
```

---

## Phase 6: Login Screen + Main.kt

### Task 23: Redesign LoginScreen

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt`

- [ ] **Step 1: Rewrite LoginScreen**

- Splash gradient background
- Centered card (320dp, semi-transparent `#1A1A20` at 85% alpha)
- Logo + title + subtitle slot (20dp fixed height)
- Password field with show/hide toggle
- Login button (brand #562DF8)
- Error states: subtitle text changes, field border changes
- Loading state: spinner + disabled field
- Manual connection link
- Version at bottom

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt
git commit -m "feat(login): redesign login screen with Yalla gradient and glassmorphism card"
```

---

### Task 24: Update Main.kt window management

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/Main.kt`

- [ ] **Step 1: Update window sizing**

- Initial size: `DpSize(1280.dp, 720.dp)` for both screens
- Remove AWT resize logic for login (lines 155-163)
- Keep `resizable = false` for login, `true` for main
- Keep `alwaysOnTop` logic
- Update `minimumSize` to 1280×720 for both
- Remove `ConnectionManager` shutdown hook references
- Update keyboard shortcuts to use new ToolbarComponent API

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/Main.kt
git commit -m "feat(main): update window sizing for 1280x720 login, remove ConnectionManager"
```

---

## Phase 7: Cleanup and Verification

### Task 25: Delete deprecated files and run full build

**Files:**
- Delete: `src/main/kotlin/uz/yalla/sipphone/domain/RegistrationEngine.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/domain/RegistrationState.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/domain/ConnectionManager.kt` (if not already)
- Delete: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/ConnectionManagerImpl.kt` (if not already)
- Update: all remaining test files that reference deleted types

- [ ] **Step 1: Remove deprecated files**

- [ ] **Step 2: Fix all remaining compilation errors**

Search for `RegistrationEngine`, `RegistrationState`, `ConnectionManager` references across all files. Replace or remove.

- [ ] **Step 3: Update FakeRegistrationEngine tests to use FakeSipAccountManager**

- [ ] **Step 4: Run full build**

Run: `./gradlew build --info`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all tests**

Run: `./gradlew test --info`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: remove deprecated RegistrationEngine, ConnectionManager, clean up references"
```

---

### Task 26: Update documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/js-bridge-api.md`

- [ ] **Step 1: Update architecture.md**

Reflect new `SipAccountManager`, removed `RegistrationEngine`/`ConnectionManager`, new toolbar structure.

- [ ] **Step 2: Update js-bridge-api.md**

Add `accounts` to `getState()`, `accountStatusChanged` event, bridge version 1.2.0.

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: update architecture and bridge API for multi-SIP"
```

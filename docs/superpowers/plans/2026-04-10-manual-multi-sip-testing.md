# Manual Multi-SIP Connection Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable testing the SIP connection layer without a backend by supporting multiple SIP account management through the manual connection dialog, and fix the `lastRegisteredServer` global state bug.

**Architecture:** Two independent parts. Part 1 fixes per-account server routing by storing `server` on `PjsipAccount` and removing the global `lastRegisteredServer`. Part 2 enhances `ManualConnectionDialog` to support adding/removing multiple accounts before connecting. Both follow existing MVI + Decompose patterns.

**Tech Stack:** Kotlin, Compose Desktop, pjsip (JNI), Decompose, Koin, kotlinx.coroutines, kotlin.test

**Spec:** `docs/superpowers/specs/2026-04-10-manual-multi-sip-testing-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `data/pjsip/PjsipRegistrationState.kt` | Modify | Rename `Registered.server` → `Registered.uri` |
| `data/pjsip/PjsipAccount.kt` | Modify | Add `server: String` constructor param |
| `data/pjsip/PjsipAccountManager.kt` | Modify | Remove `lastRegisteredServer`, pass server to PjsipAccount, remove from `AccountProvider` |
| `data/pjsip/PjsipCallManager.kt` | Modify | Use `acc.server` / `getAccount(id)?.server` instead of global |
| `feature/login/LoginComponent.kt` | Modify | New `ManualAccountEntry`, new `manualConnect(List, String)` |
| `feature/login/LoginScreen.kt` | Modify | Multi-account dialog with list + add form |
| `ui/strings/StringResources.kt` | Modify | 4 new string keys |
| `ui/strings/UzStrings.kt` | Modify | 4 new UZ translations |
| `ui/strings/RuStrings.kt` | Modify | 4 new RU translations |
| `test/.../engine/ScriptableRegistrationEngine.kt` | Modify | Rename `Registered(server)` → `Registered(uri)` |
| `test/.../scenario/ScenarioRunner.kt` | Modify | Rename `Registered(server)` → `Registered(uri)` |
| `test/.../feature/login/LoginComponentTest.kt` | Modify | Update manualConnect test, add multi-account test |

All paths relative to `src/main/kotlin/uz/yalla/sipphone/` (production) or `src/test/kotlin/uz/yalla/sipphone/` (test).

---

## Task 1: Rename `PjsipRegistrationState.Registered.server` → `uri`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipRegistrationState.kt:8`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt:38`
- Modify: `src/test/kotlin/uz/yalla/sipphone/testing/engine/ScriptableRegistrationEngine.kt:66-67`
- Modify: `src/test/kotlin/uz/yalla/sipphone/testing/scenario/ScenarioRunner.kt:42`

- [ ] **Step 1: Rename field in `PjsipRegistrationState.kt`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipRegistrationState.kt
// Line 8 — change:
data class Registered(val server: String) : PjsipRegistrationState
// To:
data class Registered(val uri: String) : PjsipRegistrationState
```

- [ ] **Step 2: Update `PjsipAccount.kt` — named argument**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt
// Line 38 — change:
PjsipRegistrationState.Registered(server = uri),
// To:
PjsipRegistrationState.Registered(uri = uri),
```

- [ ] **Step 3: Update `ScriptableRegistrationEngine.kt`**

```kotlin
// File: src/test/kotlin/uz/yalla/sipphone/testing/engine/ScriptableRegistrationEngine.kt
// Line 66-67 — change:
fun emitRegistered(server: String = "sip:102@192.168.0.22") {
    emit(PjsipRegistrationState.Registered(server))
}
// To:
fun emitRegistered(uri: String = "sip:102@192.168.0.22") {
    emit(PjsipRegistrationState.Registered(uri))
}
```

- [ ] **Step 4: Update `ScenarioRunner.kt`**

```kotlin
// File: src/test/kotlin/uz/yalla/sipphone/testing/scenario/ScenarioRunner.kt
// Line 42 — change:
registrationEngine.emitRegistered(server)
// To:
registrationEngine.emitRegistered(uri = server)
```

Note: The `register(server: String)` parameter name in ScenarioRunner stays as `server` — it's a user-facing DSL param that describes what the test author is passing. Only the internal call uses the renamed `uri` argument.

- [ ] **Step 5: Run tests**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All tests pass — this is a pure rename with no behavior change.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipRegistrationState.kt \
  src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt \
  src/test/kotlin/uz/yalla/sipphone/testing/engine/ScriptableRegistrationEngine.kt \
  src/test/kotlin/uz/yalla/sipphone/testing/scenario/ScenarioRunner.kt
git commit -m "refactor(pjsip): rename Registered.server to Registered.uri for clarity"
```

---

## Task 2: Add `server` property to `PjsipAccount`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt:14-18`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt:134`

- [ ] **Step 1: Add `server` constructor parameter to `PjsipAccount`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt
// Lines 14-18 — change:
class PjsipAccount(
    val accountId: String,
    private val accountManager: PjsipAccountManager,
    private val pjScope: CoroutineScope,
) : Account() {
// To:
class PjsipAccount(
    val accountId: String,
    val server: String,
    private val accountManager: PjsipAccountManager,
    private val pjScope: CoroutineScope,
) : Account() {
```

- [ ] **Step 2: Pass `credentials.server` in `PjsipAccountManager.register()`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt
// Line 134 — change:
val account = PjsipAccount(accountId, this, pjScope).apply {
// To:
val account = PjsipAccount(accountId, credentials.server, this, pjScope).apply {
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All tests pass — `server` is additive, no behavior change yet.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccount.kt \
  src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt
git commit -m "feat(pjsip): add server property to PjsipAccount"
```

---

## Task 3: Remove `lastRegisteredServer` and use per-account server

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt:28-32,43,57-60`
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt:122,357`

- [ ] **Step 1: Remove `lastRegisteredServer` from `AccountProvider` interface**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt
// Lines 28-32 — change:
interface AccountProvider {
    fun getAccount(accountId: String): PjsipAccount?
    fun getFirstConnectedAccount(): PjsipAccount?
    val lastRegisteredServer: String?
}
// To:
interface AccountProvider {
    fun getAccount(accountId: String): PjsipAccount?
    fun getFirstConnectedAccount(): PjsipAccount?
}
```

- [ ] **Step 2: Remove `lastRegisteredServer` property and assignment from `PjsipAccountManager`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt
// Line 43 — DELETE this line:
override var lastRegisteredServer: String? = null

// Lines 57-60 in updateRegistrationState() — change (note: .uri after Task 1 rename):
fun updateRegistrationState(accountId: String, state: PjsipRegistrationState) {
    if (state is PjsipRegistrationState.Registered) {
        lastRegisteredServer = state.uri
    }
    val flow = _accountStates.getOrPut(accountId) { MutableStateFlow(PjsipRegistrationState.Idle) }
// To:
fun updateRegistrationState(accountId: String, state: PjsipRegistrationState) {
    val flow = _accountStates.getOrPut(accountId) { MutableStateFlow(PjsipRegistrationState.Idle) }
```

- [ ] **Step 3: Update `PjsipCallManager.makeCall()` — use `acc.server`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
// Lines 122-123 — change:
val host = SipConstants.extractHostFromUri(accountProvider.lastRegisteredServer)
if (host.isBlank()) return Result.failure(IllegalStateException("No server address"))
// To:
val host = acc.server
```

- [ ] **Step 4: Update `PjsipCallManager.transferCall()` — use account lookup**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
// Lines 357-358 — change:
val host = SipConstants.extractHostFromUri(accountProvider.lastRegisteredServer)
if (host.isBlank()) return Result.failure(IllegalStateException("No server address"))
// To:
val callAccountId = currentAccountId
    ?: return Result.failure(IllegalStateException("No active call account"))
val host = accountProvider.getAccount(callAccountId)?.server
    ?: return Result.failure(IllegalStateException("No server for account $callAccountId"))
```

- [ ] **Step 5: Run tests**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All tests pass. No test directly exercises `lastRegisteredServer` — the integration tests use `ScriptableCallEngine` which bypasses `PjsipCallManager`.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipAccountManager.kt \
  src/main/kotlin/uz/yalla/sipphone/data/pjsip/PjsipCallManager.kt
git commit -m "fix(pjsip): use per-account server instead of global lastRegisteredServer

Fixes incorrect call routing when multiple accounts are registered on
different SIP servers."
```

---

## Task 4: Add string resources for multi-account dialog

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/strings/StringResources.kt:58-60`
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/strings/UzStrings.kt:46-47`
- Modify: `src/main/kotlin/uz/yalla/sipphone/ui/strings/RuStrings.kt:46-47`

- [ ] **Step 1: Add keys to `StringResources` interface**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/ui/strings/StringResources.kt
// After line 60 (placeholderDispatcherUrl), add:
    val manualAddAccount: String
    val manualConnectAll: String
    val manualNoAccounts: String
    val manualDuplicateAccount: String
```

- [ ] **Step 2: Add Uzbek translations in `UzStrings`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/ui/strings/UzStrings.kt
// After line 47 (placeholderDispatcherUrl), add:
    override val manualAddAccount = "Qo'shish"
    override val manualConnectAll = "Hammasini ulash"
    override val manualNoAccounts = "Account qo'shilmagan"
    override val manualDuplicateAccount = "Bu account allaqachon qo'shilgan"
```

- [ ] **Step 3: Add Russian translations in `RuStrings`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/ui/strings/RuStrings.kt
// After line 47 (placeholderDispatcherUrl), add:
    override val manualAddAccount = "Добавить"
    override val manualConnectAll = "Подключить все"
    override val manualNoAccounts = "Аккаунты не добавлены"
    override val manualDuplicateAccount = "Этот аккаунт уже добавлен"
```

- [ ] **Step 4: Run build**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew compileKotlin`
Expected: Compiles successfully — all implementations override new interface members.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/ui/strings/StringResources.kt \
  src/main/kotlin/uz/yalla/sipphone/ui/strings/UzStrings.kt \
  src/main/kotlin/uz/yalla/sipphone/ui/strings/RuStrings.kt
git commit -m "feat(i18n): add string resources for multi-account manual connection"
```

---

## Task 5: Add `ManualAccountEntry` and update `LoginComponent`

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt:77-95`
- Test: `src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt`

- [ ] **Step 1: Write failing test — multi-account manualConnect**

```kotlin
// File: src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt
// Add new test after the existing manualConnect test (after line 123):

@Test
fun `manualConnect with multiple accounts registers all`() = runTest(testDispatcher) {
    val accounts = listOf(
        ManualAccountEntry("192.168.0.22", 5060, "102", "pass1"),
        ManualAccountEntry("192.168.0.22", 5060, "103", "pass2"),
        ManualAccountEntry("10.0.0.5", 5060, "200", "pass3"),
    )
    component.manualConnect(accounts, "http://localhost:5173")
    advanceUntilIdle()
    assertEquals(1, fakeSipAccountManager.registerAllCallCount)
    val registered = fakeSipAccountManager.lastRegisteredAccounts
    assertEquals(3, registered.size)
    assertEquals("192.168.0.22", registered[0].serverUrl)
    assertEquals(102, registered[0].extensionNumber)
    assertEquals("10.0.0.5", registered[2].serverUrl)
    assertEquals(200, registered[2].extensionNumber)
}
```

Also add the import at the top of the test file:
```kotlin
import uz.yalla.sipphone.feature.login.ManualAccountEntry
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test --tests "uz.yalla.sipphone.feature.login.LoginComponentTest"`
Expected: FAIL — `ManualAccountEntry` not found, `manualConnect(List, String)` not found.

- [ ] **Step 3: Add `ManualAccountEntry` and new `manualConnect` to `LoginComponent`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt
// Add after the imports (after line 22), before LoginErrorType:

data class ManualAccountEntry(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
)
```

```kotlin
// In LoginComponent class, REPLACE the existing manualConnect method (lines 77-95) with:

fun manualConnect(accounts: List<ManualAccountEntry>, dispatcherUrl: String = "") {
    if (accounts.isEmpty()) return
    val accountInfos = accounts.map { entry ->
        val credentials = SipCredentials(
            server = entry.server,
            port = entry.port,
            username = entry.username,
            password = entry.password,
        )
        SipAccountInfo(
            extensionNumber = entry.username.toIntOrNull() ?: 0,
            serverUrl = entry.server,
            sipName = null,
            credentials = credentials,
        )
    }
    val authResult = AuthResult(
        token = "",
        accounts = accountInfos,
        dispatcherUrl = dispatcherUrl,
        agent = AgentInfo("manual", accounts.first().username),
    )
    _loginState.value = LoginState.Loading
    scope.launch(ioDispatcher) {
        registerAndNavigate(authResult, accountInfos)
    }
}
```

- [ ] **Step 4: Update existing manualConnect test to use new API**

```kotlin
// File: src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt
// Replace the existing manualConnect test (lines 109-123) with:

@Test
fun `manualConnect registers SIP accounts`() = runTest(testDispatcher) {
    val accounts = listOf(
        ManualAccountEntry("192.168.1.1", 5060, "102", "secret"),
    )
    component.manualConnect(accounts)
    advanceUntilIdle()
    assertEquals(1, fakeSipAccountManager.registerAllCallCount)
    val registered = fakeSipAccountManager.lastRegisteredAccounts
    assertEquals(1, registered.size)
    assertEquals("192.168.1.1", registered.first().serverUrl)
    assertEquals(102, registered.first().extensionNumber)
    assertEquals("102", registered.first().credentials.username)
}
```

- [ ] **Step 5: Run tests**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test --tests "uz.yalla.sipphone.feature.login.LoginComponentTest"`
Expected: All 8 tests pass (6 existing + 1 updated + 1 new).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt \
  src/test/kotlin/uz/yalla/sipphone/feature/login/LoginComponentTest.kt
git commit -m "feat(login): support multiple accounts in manualConnect

Add ManualAccountEntry data class. Replace single-account manualConnect
with list-based version. Add test for multi-account registration."
```

---

## Task 6: Rewrite `ManualConnectionDialog` for multiple accounts

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt:206-214,224-301`

This is the largest task — the entire dialog composable is replaced.

- [ ] **Step 1: Update dialog invocation in `LoginScreen`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt
// Lines 206-214 — change:
if (showManualDialog) {
    ManualConnectionDialog(
        isLoading = isLoading,
        onConnect = { server, port, username, pwd, dispatcher ->
            showManualDialog = false
            component.manualConnect(server, port, username, pwd, dispatcher)
        },
        onDismiss = { showManualDialog = false },
    )
}
// To:
if (showManualDialog) {
    ManualConnectionDialog(
        isLoading = isLoading,
        onConnect = { accounts, dispatcherUrl ->
            showManualDialog = false
            component.manualConnect(accounts, dispatcherUrl)
        },
        onDismiss = { showManualDialog = false },
    )
}
```

- [ ] **Step 2: Rewrite `ManualConnectionDialog` composable**

Replace the entire `ManualConnectionDialog` function (lines 224-301) with:

```kotlin
@Composable
private fun ManualConnectionDialog(
    isLoading: Boolean,
    onConnect: (accounts: List<ManualAccountEntry>, dispatcherUrl: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val colors = LocalYallaColors.current

    var accounts by remember { mutableStateOf(listOf<ManualAccountEntry>()) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5060") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var dispatcherUrl by remember { mutableStateOf("") }
    var duplicateWarning by remember { mutableStateOf(false) }

    val canAdd = server.isNotBlank() && username.isNotBlank() && !isLoading
    val canConnect = accounts.isNotEmpty() && !isLoading

    fun addAccount() {
        val entry = ManualAccountEntry(server, port.toIntOrNull() ?: 5060, username, password)
        val key = "${entry.username}@${entry.server}:${entry.port}"
        val exists = accounts.any { "${it.username}@${it.server}:${it.port}" == key }
        if (exists) {
            duplicateWarning = true
            return
        }
        accounts = accounts + entry
        username = ""
        password = ""
        duplicateWarning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.loginManualConnection) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                // Account list
                if (accounts.isEmpty()) {
                    Text(
                        strings.manualNoAccounts,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSubtle,
                        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacingSm),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((accounts.size * 40).coerceAtMost(200).dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        accounts.forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${entry.username}@${entry.server}:${entry.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { accounts = accounts.toMutableList().apply { removeAt(index) } },
                                    modifier = Modifier.size(24.dp),
                                    enabled = !isLoading,
                                ) {
                                    Text("x", style = MaterialTheme.typography.bodySmall, color = colors.textSubtle)
                                }
                            }
                        }
                    }
                }

                // Divider
                Spacer(Modifier.height(tokens.spacingXs))

                // Add form
                OutlinedTextField(
                    value = server, onValueChange = { server = it; duplicateWarning = false },
                    label = { Text(strings.labelServer) },
                    placeholder = {
                        Text(strings.placeholderServer, style = MaterialTheme.typography.bodySmall,
                            color = colors.textSubtle.copy(alpha = tokens.alphaDisabled))
                    },
                    singleLine = true, enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(), shape = tokens.shapeMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(strings.labelPort) },
                        singleLine = true, enabled = !isLoading,
                        modifier = Modifier.width(100.dp), shape = tokens.shapeMedium,
                    )
                    OutlinedTextField(
                        value = username, onValueChange = { username = it; duplicateWarning = false },
                        label = { Text(strings.labelUsername) },
                        placeholder = {
                            Text(strings.placeholderUsername, style = MaterialTheme.typography.bodySmall,
                                color = colors.textSubtle.copy(alpha = tokens.alphaDisabled))
                        },
                        singleLine = true, enabled = !isLoading,
                        modifier = Modifier.weight(1f), shape = tokens.shapeMedium,
                    )
                }
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(strings.labelPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(), shape = tokens.shapeMedium,
                )

                if (duplicateWarning) {
                    Text(
                        strings.manualDuplicateAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.statusWarning,
                    )
                }

                // Dispatcher URL — applies to session
                OutlinedTextField(
                    value = dispatcherUrl, onValueChange = { dispatcherUrl = it },
                    label = { Text(strings.labelDispatcherUrl) },
                    placeholder = {
                        Text(strings.placeholderDispatcherUrl, style = MaterialTheme.typography.bodySmall,
                            color = colors.textSubtle.copy(alpha = tokens.alphaDisabled))
                    },
                    singleLine = true, enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(), shape = tokens.shapeMedium,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                TextButton(
                    onClick = { addAccount() },
                    enabled = canAdd,
                ) { Text(strings.manualAddAccount) }
                Button(
                    onClick = { onConnect(accounts, dispatcherUrl) },
                    enabled = canConnect,
                    shape = tokens.shapeMedium,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(tokens.iconDefault), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(tokens.spacingSm))
                    }
                    Text(strings.manualConnectAll)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.buttonCancel) } },
    )
}
```

- [ ] **Step 3: Add missing import for `ManualAccountEntry`**

```kotlin
// File: src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt
// Add to imports section:
import uz.yalla.sipphone.feature.login.ManualAccountEntry
```

Note: This import may be unnecessary if `ManualAccountEntry` is in the same package. Check after build.

- [ ] **Step 4: Run build**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew compileKotlin`
Expected: Compiles successfully.

- [ ] **Step 5: Run all tests**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/login/LoginScreen.kt
git commit -m "feat(login): multi-account ManualConnectionDialog

Replace single-account dialog with list-based UI. Users can add multiple
SIP accounts before connecting. Includes duplicate detection and
per-account remove buttons."
```

---

## Task 7: Full verification

- [ ] **Step 1: Run full test suite**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew test`
Expected: All tests pass.

- [ ] **Step 2: Run full build (compile + package)**

Run: `cd /Users/macbookpro/Ildam/yalla/yalla-sip-phone && ./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify no references to `lastRegisteredServer` remain in production code**

Run: `grep -r "lastRegisteredServer" src/main/`
Expected: No output (zero matches).

- [ ] **Step 4: Verify no references to old `manualConnect(server, port, ...)` signature remain**

Run: `grep -rn "manualConnect(server" src/ && grep -rn 'manualConnect("' src/test/`
Expected: No matches for old 5-param signature. Only new `manualConnect(accounts` and `manualConnect(listOf` patterns.

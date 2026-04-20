package uz.yalla.sipphone.showcase

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uz.yalla.sipphone.data.auth.MockAuthRepository
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.domain.AgentInfo
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.SipAccount
import uz.yalla.sipphone.domain.SipAccountInfo
import uz.yalla.sipphone.domain.SipAccountState
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.feature.login.LoginComponent
import uz.yalla.sipphone.feature.main.toolbar.ToolbarComponent
import uz.yalla.sipphone.testing.FakeSipAccountManager

/**
 * Canned fake data for showcase previews. Intentionally thin — the showcase doesn't exercise
 * behaviour, it only pins visual states so designers / developers can look at every variant.
 */

val sampleAgent = AgentInfo(id = "42", name = "Islombek")

val sampleCredentials = SipCredentials(
    server = "sip.example.uz",
    port = 5060,
    username = "1001",
    password = "",
    transport = "UDP",
)

val sampleAccountInfo = SipAccountInfo(
    extensionNumber = 1001,
    serverUrl = "sip.example.uz",
    sipName = "Operator 1001",
    credentials = sampleCredentials,
)

val sampleAuthResult = AuthResult(
    token = "demo-token",
    accounts = listOf(sampleAccountInfo),
    dispatcherUrl = "https://dispatch.example.uz",
    backendUrl = "https://api.example.uz",
    agent = sampleAgent,
)

fun accountsConnected(): List<SipAccount> = listOf(
    SipAccount("1001@sip.example.uz", "Operator 1001", sampleCredentials, SipAccountState.Connected),
    SipAccount("1002@sip.example.uz", "Operator 1002", sampleCredentials.copy(username = "1002"), SipAccountState.Connected),
)

fun accountsMixed(): List<SipAccount> = listOf(
    SipAccount("1001@sip.example.uz", "Operator 1001", sampleCredentials, SipAccountState.Connected),
    SipAccount("1002@sip.example.uz", "Operator 1002", sampleCredentials.copy(username = "1002"), SipAccountState.Reconnecting(attempt = 2, nextRetryMs = 4_000)),
    SipAccount("1003@sip.example.uz", "Operator 1003", sampleCredentials.copy(username = "1003"), SipAccountState.Disconnected),
)

fun accountsAllDown(): List<SipAccount> = listOf(
    SipAccount("1001@sip.example.uz", "Operator 1001", sampleCredentials, SipAccountState.Disconnected),
    SipAccount("1002@sip.example.uz", "Operator 1002", sampleCredentials.copy(username = "1002"), SipAccountState.Disconnected),
)

val incomingRinging = CallState.Ringing(
    callId = "call-42",
    callerNumber = "+998901234567",
    callerName = "Anvar",
    isOutbound = false,
    accountId = "1001@sip.example.uz",
    remoteUri = "sip:+998901234567@sip.example.uz",
)

val activeCall = CallState.Active(
    callId = "call-42",
    remoteNumber = "+998901234567",
    remoteName = "Anvar",
    isOutbound = false,
    isMuted = false,
    isOnHold = false,
    accountId = "1001@sip.example.uz",
)

val activeMuted = activeCall.copy(isMuted = true)
val activeOnHold = activeCall.copy(isOnHold = true)

/** Build a live ToolbarComponent wired to fakes, pre-seeded with the given state. */
fun toolbarComponentFor(
    callState: CallState = CallState.Idle,
    accounts: List<SipAccount> = accountsConnected(),
    phoneInput: String = "",
): ToolbarComponent {
    val engine = FakeCallEngine().apply {
        when (callState) {
            is CallState.Ringing -> simulateRinging(
                callerNumber = callState.callerNumber,
                callerName = callState.callerName,
                isOutbound = callState.isOutbound,
            )
            is CallState.Active -> simulateActive(
                remoteNumber = callState.remoteNumber,
                remoteName = callState.remoteName,
                isOutbound = callState.isOutbound,
                isMuted = callState.isMuted,
                isOnHold = callState.isOnHold,
            )
            else -> Unit
        }
    }
    val accountManager = FakeSipAccountManager().apply { seedAccounts(accounts) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return ToolbarComponent(engine, accountManager, scope).also {
        if (phoneInput.isNotEmpty()) it.updatePhoneInput(phoneInput)
    }
}

/** Build a LoginComponent wired to fakes. */
fun loginComponentFor(settings: AppSettings = AppSettings()): LoginComponent {
    val lifecycle = LifecycleRegistry()
    return LoginComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        authRepository = MockAuthRepository(),
        sipAccountManager = FakeSipAccountManager(),
        appSettings = settings,
        onLoginSuccess = {},
    )
}

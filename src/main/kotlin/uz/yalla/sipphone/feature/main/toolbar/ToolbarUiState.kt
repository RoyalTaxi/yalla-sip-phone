package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.runtime.Immutable
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.sip.SipAccount

@Immutable
data class ToolbarUiState(
    val call: CallState = CallState.Idle,
    val accounts: List<SipAccount> = emptyList(),
    val agent: AgentStatus = AgentStatus.READY,
    val phoneInput: String = "",
    val callDuration: String? = null,
    val settingsVisible: Boolean = false,
) {
    val activeCallAccountId: String?
        get() = when (val c = call) {
            is CallState.Ringing -> c.accountId
            is CallState.Active -> c.accountId
            is CallState.Ending -> c.accountId
            else -> null
        }
}

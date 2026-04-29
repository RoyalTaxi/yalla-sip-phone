package uz.yalla.sipphone.feature.workstation.presentation.intent

import androidx.compose.runtime.Immutable
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.update.UpdateState

@Immutable
data class WorkstationState(
    val agentInfo: AgentInfo,
    val dispatcherUrl: String,
    val call: CallState,
    val accounts: List<SipAccount>,
    val agent: AgentStatus,
    val callDuration: String?,
    val settingsVisible: Boolean,
    val updateState: UpdateState,
    val updateDialogDismissed: Boolean,
    val diagnosticsVisible: Boolean,
    val isDarkTheme: Boolean,
    val locale: String,
) {
    val activeCallAccountId: String?
        get() = when (val c = call) {
            is CallState.Ringing -> c.accountId
            is CallState.Active -> c.accountId
            is CallState.Ending -> c.accountId
            else -> null
        }

    companion object {
        fun initial(
            agentInfo: AgentInfo,
            dispatcherUrl: String,
            isDarkTheme: Boolean,
            locale: String,
        ): WorkstationState = WorkstationState(
            agentInfo = agentInfo,
            dispatcherUrl = dispatcherUrl,
            call = CallState.Idle,
            accounts = emptyList(),
            agent = AgentStatus.READY,
            callDuration = null,
            settingsVisible = false,
            updateState = UpdateState.Idle,
            updateDialogDismissed = false,
            diagnosticsVisible = false,
            isDarkTheme = isDarkTheme,
            locale = locale,
        )
    }
}

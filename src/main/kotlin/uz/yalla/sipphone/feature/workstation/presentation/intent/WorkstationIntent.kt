package uz.yalla.sipphone.feature.workstation.presentation.intent

import uz.yalla.sipphone.domain.agent.AgentStatus

sealed interface WorkstationIntent {
    data class SubmitCall(val number: String) : WorkstationIntent
    data object AnswerCall : WorkstationIntent
    data object RejectCall : WorkstationIntent
    data object HangupCall : WorkstationIntent
    data object ToggleMute : WorkstationIntent
    data object ToggleHold : WorkstationIntent
    data class OnSipChipClick(val accountId: String) : WorkstationIntent
    data class SetAgentStatus(val status: AgentStatus) : WorkstationIntent
    data object OpenSettings : WorkstationIntent
    data object CloseSettings : WorkstationIntent
    data object ToggleTheme : WorkstationIntent
    data class ChangeLocale(val locale: String) : WorkstationIntent
    data object Logout : WorkstationIntent
    data object ShowUpdateDialog : WorkstationIntent
    data object DismissUpdateDialog : WorkstationIntent
    data object ConfirmUpdateInstall : WorkstationIntent
    data object HideDiagnostics : WorkstationIntent
}

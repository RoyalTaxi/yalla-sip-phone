package uz.yalla.sipphone.feature.workstation.presentation.model

import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationIntent

fun WorkstationComponent.onIntent(intent: WorkstationIntent): kotlinx.coroutines.Job = intent {
    when (intent) {
        is WorkstationIntent.SetPhoneInput -> reduce { state.copy(phoneInput = intent.value) }
        is WorkstationIntent.SubmitCall -> dispatchCall(intent.number)
        WorkstationIntent.AnswerCall -> answer()
        WorkstationIntent.RejectCall -> hangup()
        WorkstationIntent.HangupCall -> hangup()
        WorkstationIntent.ToggleMute -> mute()
        WorkstationIntent.ToggleHold -> hold()
        is WorkstationIntent.OnSipChipClick -> sipChipClick(intent.accountId)
        is WorkstationIntent.SetAgentStatus -> setAgentStatus(intent.status)
        WorkstationIntent.OpenSettings -> reduce { state.copy(settingsVisible = true) }
        WorkstationIntent.CloseSettings -> reduce { state.copy(settingsVisible = false) }
        WorkstationIntent.ToggleTheme -> {
            val next = !state.isDarkTheme
            userPreferences.setDarkTheme(next)
            webPanelBridge.emitThemeChanged(next)
        }
        is WorkstationIntent.ChangeLocale -> {
            userPreferences.setLocale(intent.locale)
            webPanelBridge.emitLocaleChanged(intent.locale)
        }
        WorkstationIntent.Logout -> triggerLogout()
        WorkstationIntent.ShowUpdateDialog -> updateManager.showDialog()
        WorkstationIntent.DismissUpdateDialog -> updateManager.dismiss()
        WorkstationIntent.ConfirmUpdateInstall -> updateManager.confirmInstall()
        WorkstationIntent.HideDiagnostics -> updateManager.hideDiagnostics()
    }
}

package uz.yalla.sipphone.feature.workstation.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.time.Instant
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationIntent
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationState
import uz.yalla.sipphone.feature.workstation.toolbar.widget.SettingsPanel
import uz.yalla.sipphone.feature.workstation.update.UpdateDialog
import uz.yalla.sipphone.feature.workstation.update.UpdateDiagnosticsDialog
import uz.yalla.sipphone.feature.workstation.update.UpdateDiagnosticsSnapshot
import uz.yalla.sipphone.feature.workstation.webview.WebviewPanel
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun WorkstationScreen(
    state: WorkstationState,
    onIntent: (WorkstationIntent) -> Unit,
    jcefManager: JcefManager,
    diagnosticsSnapshot: UpdateDiagnosticsSnapshot,
) {
    Column(modifier = Modifier.fillMaxSize().background(LocalYallaColors.current.backgroundBase)) {
        WorkstationToolbar(
            state = state,
            onIntent = onIntent,
        )
        WebviewPanel(
            jcefManager = jcefManager,
            dispatcherUrl = state.dispatcherUrl,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }

    SettingsPanel(
        visible = state.settingsVisible,
        isDarkTheme = state.isDarkTheme,
        locale = state.locale,
        agentInfo = state.agentInfo,
        onThemeToggle = { onIntent(WorkstationIntent.ToggleTheme) },
        onLocaleChange = { onIntent(WorkstationIntent.ChangeLocale(it)) },
        onLogout = { onIntent(WorkstationIntent.Logout) },
        onDismiss = { onIntent(WorkstationIntent.CloseSettings) },
    )

    UpdateDialog(
        state = state.updateState,
        callState = state.call,
        dismissed = state.updateDialogDismissed,
        onInstall = { onIntent(WorkstationIntent.ConfirmUpdateInstall) },
        onDismiss = { onIntent(WorkstationIntent.DismissUpdateDialog) },
    )

    UpdateDiagnosticsDialog(
        visible = state.diagnosticsVisible,
        snapshot = diagnosticsSnapshot,
        onCopy = { onIntent(WorkstationIntent.HideDiagnostics) },
        onDismiss = { onIntent(WorkstationIntent.HideDiagnostics) },
    )
}

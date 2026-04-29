package uz.yalla.sipphone.feature.workstation.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.Instant
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationEffect
import uz.yalla.sipphone.feature.workstation.presentation.model.WorkstationComponent
import uz.yalla.sipphone.feature.workstation.presentation.model.onIntent
import uz.yalla.sipphone.feature.workstation.update.UpdateDiagnosticsSnapshot

sealed interface FromWorkstation {
    data object ToAuth : FromWorkstation
}

@Composable
fun WorkstationRoute(
    component: WorkstationComponent,
    navigateTo: (FromWorkstation) -> Unit,
) {
    val state by component.container.stateFlow.collectAsState()

    component.collectSideEffect { effect ->
        when (effect) {
            WorkstationEffect.NavigateToAuth -> navigateTo(FromWorkstation.ToAuth)
        }
    }

    val diagnosticsSnapshot = remember(state.updateState, state.diagnosticsVisible) {
        component.diagnosticsSnapshot()
    }

    WorkstationScreen(
        state = state,
        onIntent = component::onIntent,
        jcefManager = component.jcefManager,
        diagnosticsSnapshot = diagnosticsSnapshot,
    )
}

private fun WorkstationComponent.diagnosticsSnapshot(): UpdateDiagnosticsSnapshot {
    val um = updateManager
    return UpdateDiagnosticsSnapshot(
        installId = "—",
        channel = "—",
        currentVersion = BuildVersion.CURRENT,
        stateText = um.state.value.toString(),
        lastCheckText = um.lastCheckMillis().let { ms ->
            if (ms == 0L) "—" else Instant.ofEpochMilli(ms).toString()
        },
        lastErrorText = um.lastErrorMessage() ?: "—",
        logTail = "",
    )
}

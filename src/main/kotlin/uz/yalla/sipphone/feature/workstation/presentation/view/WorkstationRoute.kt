package uz.yalla.sipphone.feature.workstation.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.orbitmvi.orbit.compose.collectSideEffect
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationEffect
import uz.yalla.sipphone.feature.workstation.presentation.model.WorkstationComponent
import uz.yalla.sipphone.feature.workstation.presentation.model.onIntent

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

    WorkstationScreen(
        state = state,
        onIntent = component::onIntent,
        jcefManager = component.jcefManager,
        updateManager = component.updateManager,
        callStateFlow = component.callState,
    )
}

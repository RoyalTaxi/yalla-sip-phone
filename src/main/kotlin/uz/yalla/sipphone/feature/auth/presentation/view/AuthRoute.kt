package uz.yalla.sipphone.feature.auth.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.orbitmvi.orbit.compose.collectSideEffect
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthEffect
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.auth.presentation.model.onIntent
import uz.yalla.sipphone.feature.auth.presentation.view.manual.ManualConnectionSheet

sealed interface FromAuth {
    data class ToWorkstation(val session: Session) : FromAuth
}

@Composable
fun AuthRoute(
    component: AuthComponent,
    navigateTo: (FromAuth) -> Unit,
) {
    val state by component.container.stateFlow.collectAsState()
    val loading by component.loading.collectAsState()

    component.collectSideEffect { effect ->
        when (effect) {
            is AuthEffect.LoggedIn -> navigateTo(FromAuth.ToWorkstation(effect.session))
        }
    }

    AuthScreen(
        state = state,
        loading = loading,
        onIntent = component::onIntent,
    )

    if (state.showManualSheet) {
        ManualConnectionSheet(
            isLoading = loading,
            onConnect = { accounts, dispatcherUrl, backendUrl, pin ->
                component.onIntent(
                    AuthIntent.ManualConnect(
                        accounts = accounts,
                        dispatcherUrl = dispatcherUrl,
                        backendUrl = backendUrl,
                        pin = pin,
                    ),
                )
            },
            onDismiss = { component.onIntent(AuthIntent.DismissManualSheet) },
        )
    }
}

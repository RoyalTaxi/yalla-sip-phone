package uz.yalla.sipphone.feature.auth.presentation.model

import uz.yalla.sipphone.core.result.onFailure
import uz.yalla.sipphone.core.result.onSuccess
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthEffect
import uz.yalla.sipphone.feature.auth.presentation.intent.ManualAccountEntry

internal fun AuthComponent.submit() = intent {
    val pin = state.pin
    withLoading {
        loginUseCase(pin)
            .onSuccess { session -> postSideEffect(AuthEffect.LoggedIn(session)) }
            .onFailure { error -> reduce { state.copy(error = error) } }
    }
}

internal fun AuthComponent.manualConnect(
    accounts: List<ManualAccountEntry>,
    dispatcherUrl: String,
    backendUrl: String,
    pin: String,
) = intent {
    withLoading {
        manualConnectUseCase(
            accounts = accounts.map(ManualAccountEntry::toSipAccountInfo),
            dispatcherUrl = dispatcherUrl,
            backendUrl = backendUrl,
            pin = pin,
        )
            .onSuccess { session ->
                reduce { state.copy(showManualSheet = false) }
                postSideEffect(AuthEffect.LoggedIn(session))
            }
            .onFailure { error -> reduce { state.copy(error = error, showManualSheet = false) } }
    }
}

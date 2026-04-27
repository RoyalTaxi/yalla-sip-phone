package uz.yalla.sipphone.feature.auth.presentation.model

import uz.yalla.sipphone.core.result.onFailure
import uz.yalla.sipphone.core.result.onSuccess
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthEffect

internal fun AuthComponent.submit() = intent {
    val pin = state.pin
    withLoading {
        loginUseCase(pin)
            .onSuccess { session -> postSideEffect(AuthEffect.LoggedIn(session)) }
            .onFailure { error -> reduce { state.copy(error = error) } }
    }
}

internal fun AuthComponent.manualConnect(accounts: List<SipAccountInfo>) = intent {
    withLoading {
        manualConnectUseCase(accounts)
            .onSuccess { session ->
                reduce { state.copy(showManualSheet = false) }
                postSideEffect(AuthEffect.LoggedIn(session))
            }
            .onFailure { error -> reduce { state.copy(error = error, showManualSheet = false) } }
    }
}

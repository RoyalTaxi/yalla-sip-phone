package uz.yalla.sipphone.feature.auth.presentation.model

import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent

fun AuthComponent.onIntent(intent: AuthIntent) = intent {
    when (intent) {
        is AuthIntent.SetPin -> {
            reduce { state.copy(pin = intent.value.take(MAX_PIN_LENGTH), error = null) }
            if (state.pinReady()) submit()
        }
        AuthIntent.Submit -> {
            if (state.pinReady()) submit()
        }
        AuthIntent.OpenManualSheet -> reduce { state.copy(showManualSheet = true) }
        AuthIntent.DismissManualSheet -> reduce { state.copy(showManualSheet = false) }
        is AuthIntent.ManualConnect -> manualConnect(intent.accounts)
    }
}

private const val MAX_PIN_LENGTH = 4

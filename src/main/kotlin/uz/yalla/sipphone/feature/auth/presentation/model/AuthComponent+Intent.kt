package uz.yalla.sipphone.feature.auth.presentation.model

import kotlinx.coroutines.Job
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent

fun AuthComponent.onIntent(intent: AuthIntent): Job = intent {
    when (intent) {
        AuthIntent.ClearError -> reduce { state.copy(error = null) }
        is AuthIntent.Submit -> if (intent.pin.isNotBlank()) submit(intent.pin)
        AuthIntent.OpenManualSheet -> reduce { state.copy(showManualSheet = true) }
        AuthIntent.DismissManualSheet -> reduce { state.copy(showManualSheet = false) }
        is AuthIntent.ManualConnect -> manualConnect(
            accounts = intent.accounts,
            dispatcherUrl = intent.dispatcherUrl,
            backendUrl = intent.backendUrl,
            pin = intent.pin,
        )
    }
}

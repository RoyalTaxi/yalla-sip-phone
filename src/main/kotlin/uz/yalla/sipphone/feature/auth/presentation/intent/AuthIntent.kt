package uz.yalla.sipphone.feature.auth.presentation.intent

import uz.yalla.sipphone.domain.sip.SipAccountInfo

sealed interface AuthIntent {
    data class SetPin(val value: String) : AuthIntent
    data object Submit : AuthIntent
    data object OpenManualSheet : AuthIntent
    data object DismissManualSheet : AuthIntent
    data class ManualConnect(val accounts: List<SipAccountInfo>) : AuthIntent
}

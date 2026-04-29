package uz.yalla.sipphone.feature.auth.presentation.intent

sealed interface AuthIntent {
    data class SetPin(val value: String) : AuthIntent
    data object Submit : AuthIntent
    data object OpenManualSheet : AuthIntent
    data object DismissManualSheet : AuthIntent
    data class ManualConnect(
        val accounts: List<ManualAccountEntry>,
        val dispatcherUrl: String,
        val backendUrl: String,
        val pin: String,
    ) : AuthIntent
}

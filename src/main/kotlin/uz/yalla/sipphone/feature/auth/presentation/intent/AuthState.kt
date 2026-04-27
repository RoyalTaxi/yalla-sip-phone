package uz.yalla.sipphone.feature.auth.presentation.intent

import androidx.compose.runtime.Immutable
import uz.yalla.sipphone.domain.auth.model.AuthError

@Immutable
data class AuthState(
    val pin: String,
    val showManualSheet: Boolean,
    val error: AuthError?,
) {
    fun pinReady(): Boolean = pin.length == PIN_LENGTH

    companion object {
        const val PIN_LENGTH = 4
        val INITIAL = AuthState(pin = "", showManualSheet = false, error = null)
    }
}

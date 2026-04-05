package uz.yalla.sipphone.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable data class Login(val sessionId: Int = 0) : Screen
    @Serializable data object Main : Screen
    @Serializable data object Registration : Screen // keep for back-compat
    @Serializable data object Dialer : Screen // keep for back-compat
}

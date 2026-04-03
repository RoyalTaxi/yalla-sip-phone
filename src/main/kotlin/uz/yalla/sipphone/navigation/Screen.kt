package uz.yalla.sipphone.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    data object Registration : Screen

    @Serializable
    data object Dialer : Screen
}

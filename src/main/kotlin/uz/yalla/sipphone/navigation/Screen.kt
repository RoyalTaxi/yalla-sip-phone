package uz.yalla.sipphone.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable data object Auth : Screen
    @Serializable data object Workstation : Screen
}

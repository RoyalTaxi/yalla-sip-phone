package uz.yalla.sipphone.feature.workstation.presentation.intent

sealed interface WorkstationEffect {
    data object NavigateToAuth : WorkstationEffect
}

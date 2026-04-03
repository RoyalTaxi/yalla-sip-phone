package uz.yalla.sipphone.domain

sealed interface CallState {
    data object Idle : CallState
    // Phase 3: Dialing, Ringing, Active, Held, Ended
}

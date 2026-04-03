package uz.yalla.sipphone.domain

sealed interface SipEvent {
    data class Error(val message: String) : SipEvent
    // Phase 3: IncomingCall, CallEnded, etc.
}

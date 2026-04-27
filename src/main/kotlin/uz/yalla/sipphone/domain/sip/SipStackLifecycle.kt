package uz.yalla.sipphone.domain.sip

interface SipStackLifecycle {
    suspend fun initialize(): Result<Unit>
    suspend fun shutdown()
}

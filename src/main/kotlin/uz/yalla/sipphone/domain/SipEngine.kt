package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SipEngine {
    val registrationState: StateFlow<RegistrationState>
    val events: SharedFlow<SipEvent>

    suspend fun init(): Result<Unit>
    suspend fun register(credentials: SipCredentials): Result<Unit>
    suspend fun unregister()
    suspend fun destroy()
}

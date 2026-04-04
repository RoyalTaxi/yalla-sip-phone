package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface RegistrationEngine {
    val registrationState: StateFlow<RegistrationState>
    suspend fun register(credentials: SipCredentials): Result<Unit>
    suspend fun unregister()
}

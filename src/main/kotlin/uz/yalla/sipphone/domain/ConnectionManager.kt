package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface ConnectionManager {
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(credentials: SipCredentials)
    suspend fun disconnect()
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val server: String) : ConnectionState
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : ConnectionState
    data class Failed(val error: SipError, val willRetry: Boolean) : ConnectionState
}

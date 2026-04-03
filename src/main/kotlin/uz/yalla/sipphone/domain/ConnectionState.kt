package uz.yalla.sipphone.domain

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Registering : ConnectionState()
    data class Registered(
        val server: String,
        val expiresIn: Int
    ) : ConnectionState()
    data class Failed(
        val message: String,
        val isRetryable: Boolean
    ) : ConnectionState()
}

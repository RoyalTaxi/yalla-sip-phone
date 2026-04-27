package uz.yalla.sipphone.domain.auth.model

import uz.yalla.sipphone.core.error.DataError

sealed interface AuthError {
    data class WrongCredentials(val message: String) : AuthError
    data object NoSipAccountsConfigured : AuthError
    data class SipRegistrationTimeout(val timeoutMs: Long) : AuthError
    data class Network(val cause: DataError.Network) : AuthError
}

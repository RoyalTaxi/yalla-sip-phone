package uz.yalla.sipphone.feature.login

import uz.yalla.sipphone.domain.AuthResult

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Error(val message: String) : LoginState
    data class Authenticated(val authResult: AuthResult) : LoginState
}

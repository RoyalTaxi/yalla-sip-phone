package uz.yalla.sipphone.domain

import androidx.compose.runtime.Immutable

@Immutable
data class AuthResult(
    val token: String,
    val accounts: List<SipAccountInfo>,
    val dispatcherUrl: String,
    val backendUrl: String = "",
    val agent: AgentInfo,
)

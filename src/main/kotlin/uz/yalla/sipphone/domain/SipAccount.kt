package uz.yalla.sipphone.domain

import androidx.compose.runtime.Immutable

@Immutable
data class SipAccount(
    val id: String,
    val name: String,
    val credentials: SipCredentials,
    val state: SipAccountState,
)

package uz.yalla.sipphone.domain.sip

import androidx.compose.runtime.Immutable

@Immutable
data class SipAccountInfo(
    val extensionNumber: Int,
    val serverUrl: String,
    val sipName: String?,
    val credentials: SipCredentials,
) {
    val id: String get() = "$extensionNumber@$serverUrl"
    val name: String get() = sipName ?: "SIP $extensionNumber"
}

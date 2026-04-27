package uz.yalla.sipphone.feature.auth.presentation.intent

import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipCredentials

data class ManualAccountEntry(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
) {
    val displayKey: String get() = "$username@$server:$port"

    fun toSipAccountInfo(): SipAccountInfo = SipAccountInfo(
        extensionNumber = username.toIntOrNull() ?: 0,
        serverUrl = server,
        sipName = null,
        credentials = SipCredentials(
            server = server,
            port = port,
            username = username,
            password = password,
        ),
    )

    override fun toString(): String = "ManualAccountEntry($displayKey, password=***)"
}

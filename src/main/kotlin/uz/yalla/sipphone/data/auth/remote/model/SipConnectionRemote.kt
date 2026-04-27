package uz.yalla.sipphone.data.auth.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SipConnectionRemote(
    @SerialName("extension_number") val extensionNumber: Int,
    val password: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("sip_name") val sipName: String? = null,
    @SerialName("server_url") val serverUrl: String,
    @SerialName("server_port") val serverPort: Int,
    val domain: String,
    @SerialName("connection_type") val connectionType: String? = null,
)

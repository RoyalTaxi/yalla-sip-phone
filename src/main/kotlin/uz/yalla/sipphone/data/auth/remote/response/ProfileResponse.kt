package uz.yalla.sipphone.data.auth.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.yalla.sipphone.data.auth.remote.model.SipConnectionRemote

@Serializable
data class ProfileResponse(
    val id: Int,
    @SerialName("tm_user_id") val tmUserId: Int? = null,
    @SerialName("full_name") val fullName: String,
    val roles: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val sips: List<SipConnectionRemote> = emptyList(),
    @SerialName("panel_path") val panelPath: String? = null,
)

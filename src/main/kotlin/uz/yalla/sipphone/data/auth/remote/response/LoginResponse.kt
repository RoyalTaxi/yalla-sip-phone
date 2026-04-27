package uz.yalla.sipphone.data.auth.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("token_type") val tokenType: String? = null,
    val expire: Long? = null,
)

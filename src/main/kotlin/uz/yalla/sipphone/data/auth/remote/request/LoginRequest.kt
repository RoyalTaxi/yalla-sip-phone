package uz.yalla.sipphone.data.auth.remote.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("pin_code") val pinCode: String,
)

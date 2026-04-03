package uz.yalla.sipphone.domain

data class SipCredentials(
    val server: String,
    val port: Int = 5060,
    val username: String,
    val password: String,
)

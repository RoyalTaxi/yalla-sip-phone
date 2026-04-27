package uz.yalla.sipphone.domain.auth.model

data class Session(
    val token: String,
    val profile: Profile,
)

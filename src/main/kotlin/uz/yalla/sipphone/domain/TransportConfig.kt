package uz.yalla.sipphone.domain

data class TransportPreference(
    val protocol: TransportProtocol = TransportProtocol.UDP,
    val srtpPolicy: SrtpPolicy = SrtpPolicy.DISABLED,
)

enum class TransportProtocol { UDP, TCP, TLS }
enum class SrtpPolicy { DISABLED, OPTIONAL, MANDATORY }

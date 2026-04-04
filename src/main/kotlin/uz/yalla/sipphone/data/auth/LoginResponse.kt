package uz.yalla.sipphone.data.auth

import uz.yalla.sipphone.domain.AgentInfo
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.domain.SipCredentials

data class LoginResponse(
    val sipServer: String,
    val sipPort: Int,
    val sipUsername: String,
    val sipPassword: String,
    val sipTransport: String,
    val dispatcherUrl: String,
    val agentId: String,
    val agentName: String,
) {
    fun toAuthResult(): AuthResult = AuthResult(
        sipCredentials = SipCredentials(
            server = sipServer,
            port = sipPort,
            username = sipUsername,
            password = sipPassword,
        ),
        dispatcherUrl = dispatcherUrl,
        agent = AgentInfo(id = agentId, name = agentName),
    )
}

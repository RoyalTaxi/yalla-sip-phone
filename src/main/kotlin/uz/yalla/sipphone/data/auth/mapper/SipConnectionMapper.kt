package uz.yalla.sipphone.data.auth.mapper

import uz.yalla.sipphone.data.auth.remote.model.SipConnectionRemote
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipCredentials

internal object SipConnectionMapper {
    fun map(remote: SipConnectionRemote): SipAccountInfo = SipAccountInfo(
        extensionNumber = remote.extensionNumber,
        serverUrl = remote.serverUrl,
        sipName = remote.sipName,
        credentials = SipCredentials(
            server = remote.domain,
            port = remote.serverPort,
            username = remote.extensionNumber.toString(),
            password = remote.password,
            transport = remote.connectionType?.uppercase() ?: "UDP",
        ),
    )
}

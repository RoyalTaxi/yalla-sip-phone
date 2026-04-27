package uz.yalla.sipphone.domain.auth.model

import uz.yalla.sipphone.domain.sip.SipAccountInfo

data class Profile(
    val id: String,
    val fullName: String,
    val sipAccounts: List<SipAccountInfo>,
    val panelUrl: String?,
)

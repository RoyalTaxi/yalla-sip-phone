package uz.yalla.sipphone.data.pjsip.account

import uz.yalla.sipphone.domain.sip.SipError

sealed interface PjsipRegistrationState {
    data object Idle : PjsipRegistrationState
    data object Registering : PjsipRegistrationState
    data class Registered(val uri: String) : PjsipRegistrationState
    data class Failed(val error: SipError) : PjsipRegistrationState
}

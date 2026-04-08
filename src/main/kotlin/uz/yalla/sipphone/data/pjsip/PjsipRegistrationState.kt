package uz.yalla.sipphone.data.pjsip

import uz.yalla.sipphone.domain.SipError

/**
 * Internal pjsip account registration state.
 *
 * This is the low-level state exposed by pjsip callbacks. Production code
 * maps this to the public [uz.yalla.sipphone.domain.SipAccountState] in
 * [PjsipSipAccountManager].
 */
sealed interface PjsipRegistrationState {
    /** No account registered; initial state after stack initialisation. */
    data object Idle : PjsipRegistrationState

    /** REGISTER request has been sent; waiting for a server response. */
    data object Registering : PjsipRegistrationState

    /**
     * Registration is active and the account is reachable.
     *
     * @param server The SIP server URI returned in the REGISTER 200 OK Contact header.
     */
    data class Registered(val server: String) : PjsipRegistrationState

    /**
     * Registration attempt failed or an existing registration was revoked.
     *
     * @param error Structured error describing the cause; use [SipError.displayMessage] for UI.
     */
    data class Failed(val error: SipError) : PjsipRegistrationState
}

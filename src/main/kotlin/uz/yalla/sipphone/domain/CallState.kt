package uz.yalla.sipphone.domain

/**
 * Represents the lifecycle of a single SIP call.
 *
 * Valid transitions: Idle → Ringing → Active → Ending → Idle.
 * Collect [CallEngine.callState] to observe transitions.
 */
sealed interface CallState {
    /** No call in progress. The engine is ready to make or receive calls. */
    data object Idle : CallState

    /**
     * A call is alerting — either an inbound ring or an outbound dial-tone.
     *
     * @param callId Stable identifier for this call leg; use it for [CallEngine] operations.
     * @param callerNumber Remote party number (inbound) or dialled number (outbound).
     * @param callerName Display name from the SIP From header, if present.
     * @param isOutbound `true` for calls initiated by [CallEngine.makeCall], `false` for inbound.
     * @param accountId SIP account URI that owns this call (e.g. `1001@sip.yalla.uz`). Empty for legacy single-account usage.
     */
    data class Ringing(
        val callId: String,
        val callerNumber: String,
        val callerName: String?,
        val isOutbound: Boolean,
        val accountId: String = "",
    ) : CallState

    /**
     * The call is established and media is flowing.
     *
     * @param callId Same identifier as in the preceding [Ringing] state.
     * @param remoteNumber Remote party's number.
     * @param remoteName Remote party's display name, if available.
     * @param isOutbound `true` for outbound calls.
     * @param isMuted `true` when the local microphone is suppressed.
     * @param isOnHold `true` when the call is on hold (re-INVITE with `sendonly`).
     * @param accountId SIP account URI that owns this call. Empty for legacy single-account usage.
     */
    data class Active(
        val callId: String,
        val remoteNumber: String,
        val remoteName: String?,
        val isOutbound: Boolean,
        val isMuted: Boolean,
        val isOnHold: Boolean,
        val accountId: String = "",
    ) : CallState

    /**
     * Hangup has been requested; waiting for the pjsip disconnect callback.
     * Transitions to [Idle] once the callback fires or a safety timeout elapses.
     *
     * @param callId Identifier of the call being ended. Empty when unknown.
     * @param accountId SIP account URI that owns this call. Empty for legacy single-account usage.
     */
    data class Ending(
        val callId: String = "",
        val accountId: String = "",
    ) : CallState
}

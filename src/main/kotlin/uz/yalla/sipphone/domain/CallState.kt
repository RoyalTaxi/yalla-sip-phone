package uz.yalla.sipphone.domain

sealed interface CallState {
    /** Account ID of the current call, or null if idle. */
    val activeAccountId: String?
        get() = when (this) {
            is Idle -> null
            is Ringing -> accountId
            is Active -> accountId
            is Ending -> accountId
        }

    data object Idle : CallState

    data class Ringing(
        val callId: String,
        val callerNumber: String,
        val callerName: String?,
        val isOutbound: Boolean,
        val accountId: String = "",
        val remoteUri: String = "",
    ) : CallState {
        val direction: String get() = if (isOutbound) "outbound" else "inbound"
    }

    data class Active(
        val callId: String,
        val remoteNumber: String,
        val remoteName: String?,
        val isOutbound: Boolean,
        val isMuted: Boolean,
        val isOnHold: Boolean,
        val accountId: String = "",
        val remoteUri: String = "",
    ) : CallState {
        val direction: String get() = if (isOutbound) "outbound" else "inbound"
    }

    data class Ending(
        val callId: String = "",
        val accountId: String = "",
    ) : CallState
}

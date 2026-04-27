package uz.yalla.sipphone.data.jcef.bridge

import kotlinx.serialization.Serializable

sealed interface BridgeEvent {
    val seq: Int
    val timestamp: Long

    @Serializable
    data class IncomingCall(
        val callId: String,
        val number: String,
        val direction: String = "inbound",
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class OutgoingCall(
        val callId: String,
        val number: String,
        val direction: String = "outbound",
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallConnected(
        val callId: String,
        val number: String,
        val sipFrom: String,
        val direction: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallEnded(
        val callId: String,
        val number: String,
        val direction: String,
        val duration: Int,
        val reason: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallMuteChanged(
        val callId: String,
        val isMuted: Boolean,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallHoldChanged(
        val callId: String,
        val isOnHold: Boolean,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class AgentStatusChanged(
        val status: String,
        val previousStatus: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class ConnectionChanged(
        val state: String,
        val attempt: Int,
        val accountId: String = "",
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class AccountStatusChanged(
        val accountId: String,
        val name: String,
        val status: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallQualityUpdate(
        val callId: String,
        val quality: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class ThemeChanged(
        val theme: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class LocaleChanged(
        val locale: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class BridgeError(
        val code: String,
        val message: String,
        val severity: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class CallRejectedBusy(
        val number: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent

    @Serializable
    data class KeyPressed(
        val key: String,
        override val seq: Int,
        override val timestamp: Long,
    ) : BridgeEvent
}

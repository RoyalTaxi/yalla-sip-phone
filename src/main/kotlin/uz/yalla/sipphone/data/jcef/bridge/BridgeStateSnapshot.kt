package uz.yalla.sipphone.data.jcef.bridge

import kotlinx.serialization.Serializable

@Serializable
data class BridgeInitPayload(
    val version: String,
    val capabilities: List<String>,
    val agent: BridgeAgent,
    val bufferedEvents: List<String>,
)

@Serializable
data class BridgeAgent(
    val id: String,
    val name: String,
)

@Serializable
data class BridgeAccountState(
    val id: String,
    val name: String,
    val extension: String,
    val status: String,
)

@Serializable
data class BridgeState(
    val connection: BridgeConnectionState,
    val agentStatus: String,
    val call: BridgeCallState? = null,
    val token: String? = null,
    val accounts: List<BridgeAccountState> = emptyList(),
)

@Serializable
data class BridgeConnectionState(
    val state: String,
    val attempt: Int,
)

@Serializable
data class BridgeCallState(
    val callId: String,
    val number: String,
    val direction: String,
    val state: String,
    val isMuted: Boolean,
    val isOnHold: Boolean,
    val duration: Int,
)

@Serializable
data class BridgeVersionInfo(
    val version: String,
    val capabilities: List<String>,
)

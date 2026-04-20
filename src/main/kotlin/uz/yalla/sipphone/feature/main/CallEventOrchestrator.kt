package uz.yalla.sipphone.feature.main

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.jcef.BridgeEventEmitter
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.SipAccountManager
import uz.yalla.sipphone.domain.SipAccountState

class CallEventOrchestrator(
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    private val eventEmitter: BridgeEventEmitter,
    private val agentStatusProvider: () -> StateFlow<AgentStatus>,
) {

    fun start(scope: CoroutineScope) {
        launchCallStateCollector(scope)
        launchConnectionStateCollector(scope)
        launchAgentStatusCollector(scope)
        launchBusyRejectionCollector(scope)
    }

    private fun launchBusyRejectionCollector(scope: CoroutineScope) {
        scope.launch {
            callEngine.busyRejections.collect { callerNumber ->
                eventEmitter.emitCallRejectedBusy(callerNumber)
            }
        }
    }

    private data class CallSnapshot(
        val callId: String,
        val number: String,
        val direction: String,
        val isOutbound: Boolean,
        val everActive: Boolean,
        val terminatedLocally: Boolean,
    )

    private fun launchCallStateCollector(scope: CoroutineScope) {
        var previousCallState: CallState = CallState.Idle
        var callStartTimestamp = 0L
        var lastSnapshot: CallSnapshot? = null

        scope.launch {
            callEngine.callState.collect { newState ->
                val prev = previousCallState
                previousCallState = newState

                // Cache call info from Ringing/Active so it survives the Ending bridge.
                // `Ending` carries only callId/accountId, so without this cache `Ending → Idle`
                // (local hangup/reject) would drop the callEnded event.
                when (newState) {
                    is CallState.Ringing -> lastSnapshot = CallSnapshot(
                        callId = newState.callId,
                        number = newState.callerNumber,
                        direction = newState.direction,
                        isOutbound = newState.isOutbound,
                        everActive = lastSnapshot?.everActive ?: false,
                        terminatedLocally = false,
                    )
                    is CallState.Active -> lastSnapshot = CallSnapshot(
                        callId = newState.callId,
                        number = newState.remoteNumber,
                        direction = newState.direction,
                        isOutbound = newState.isOutbound,
                        everActive = true,
                        terminatedLocally = false,
                    )
                    is CallState.Ending -> lastSnapshot = lastSnapshot?.copy(terminatedLocally = true)
                    is CallState.Idle -> Unit
                }

                when {
                    // Idle → Ringing (inbound)
                    prev is CallState.Idle && newState is CallState.Ringing && !newState.isOutbound -> {
                        callStartTimestamp = System.currentTimeMillis()
                        eventEmitter.emitIncomingCall(newState.callId, newState.callerNumber)
                    }
                    // Idle → Ringing (outbound)
                    prev is CallState.Idle && newState is CallState.Ringing && newState.isOutbound -> {
                        callStartTimestamp = System.currentTimeMillis()
                        eventEmitter.emitOutgoingCall(newState.callId, newState.callerNumber)
                    }
                    // Ringing → Active (call connected)
                    prev is CallState.Ringing && newState is CallState.Active -> {
                        callStartTimestamp = System.currentTimeMillis()
                        eventEmitter.emitCallConnected(
                            newState.callId, newState.remoteNumber, newState.remoteUri, newState.direction,
                        )
                    }
                    // Active → Active (mute/hold changed)
                    prev is CallState.Active && newState is CallState.Active -> {
                        if (prev.isMuted != newState.isMuted) {
                            eventEmitter.emitCallMuteChanged(newState.callId, newState.isMuted)
                        }
                        if (prev.isOnHold != newState.isOnHold) {
                            eventEmitter.emitCallHoldChanged(newState.callId, newState.isOnHold)
                        }
                    }
                    // Any → Idle (call ended) — covers Ringing→Idle, Active→Idle, and Ending→Idle.
                    newState is CallState.Idle && prev !is CallState.Idle -> {
                        val snap = lastSnapshot
                        if (snap == null) {
                            callStartTimestamp = 0L
                            return@collect
                        }
                        val duration = if (callStartTimestamp == 0L) 0
                            else ((System.currentTimeMillis() - callStartTimestamp) / 1000).toInt()
                        val reason = when {
                            // Inbound call rejected while ringing (never answered, ended locally).
                            !snap.isOutbound && !snap.everActive && snap.terminatedLocally -> "rejected"
                            // Inbound ringing timed out / caller abandoned (never answered, remote ended).
                            !snap.isOutbound && !snap.everActive -> "missed"
                            // Everything else — outbound dial abort, active hangup (local or remote).
                            else -> "hangup"
                        }
                        eventEmitter.emitCallEnded(snap.callId, snap.number, snap.direction, duration, reason)
                        callStartTimestamp = 0L
                        lastSnapshot = null
                    }
                }
            }
        }
    }

    private fun launchConnectionStateCollector(scope: CoroutineScope) {
        var previousConnState = "disconnected"

        scope.launch {
            sipAccountManager.accounts.collect { accounts ->
                val state = when {
                    accounts.any { it.state is SipAccountState.Connected } -> "connected"
                    accounts.any { it.state is SipAccountState.Reconnecting } -> "reconnecting"
                    else -> "disconnected"
                }
                val connectedCount = accounts.count { it.state is SipAccountState.Connected }

                if (state != previousConnState) {
                    previousConnState = state
                    eventEmitter.emitConnectionChanged(state, connectedCount)
                }
            }
        }
    }

    private fun launchAgentStatusCollector(scope: CoroutineScope) {
        val agentStatusFlow = agentStatusProvider()
        var previousAgentStatus = agentStatusFlow.value

        scope.launch {
            agentStatusFlow.collect { newStatus ->
                val prev = previousAgentStatus
                previousAgentStatus = newStatus
                if (prev != newStatus) {
                    eventEmitter.emitAgentStatusChanged(
                        status = newStatus.name.lowercase(),
                        previousStatus = prev.name.lowercase(),
                    )
                }
            }
        }
    }
}

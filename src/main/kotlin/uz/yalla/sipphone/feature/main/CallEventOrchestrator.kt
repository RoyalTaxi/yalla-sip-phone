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
    }

    private fun launchCallStateCollector(scope: CoroutineScope) {
        var previousCallState: CallState = CallState.Idle
        var callStartTimestamp = 0L

        scope.launch {
            callEngine.callState.collect { newState ->
                val prev = previousCallState
                previousCallState = newState

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
                    // Any → Idle (call ended)
                    newState is CallState.Idle &&
                        (prev is CallState.Ringing || prev is CallState.Active || prev is CallState.Ending) -> {
                        val duration = ((System.currentTimeMillis() - callStartTimestamp) / 1000).toInt()
                        val callId: String
                        val number: String
                        val direction: String
                        val reason: String

                        when (prev) {
                            is CallState.Ringing -> {
                                callId = prev.callId
                                number = prev.callerNumber
                                direction = prev.direction
                                reason = if (prev.isOutbound) "hangup" else "missed"
                            }
                            is CallState.Active -> {
                                callId = prev.callId
                                number = prev.remoteNumber
                                direction = prev.direction
                                reason = "hangup"
                            }
                            else -> return@collect // Ending state doesn't carry call info
                        }

                        eventEmitter.emitCallEnded(callId, number, direction, duration, reason)
                        callStartTimestamp = 0L
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

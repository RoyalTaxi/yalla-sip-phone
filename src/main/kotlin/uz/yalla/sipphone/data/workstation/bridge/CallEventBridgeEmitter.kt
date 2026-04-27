package uz.yalla.sipphone.data.workstation.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState

class CallEventBridgeEmitter(
    private val callEngine: CallEngine,
    private val eventEmitter: BridgeEventEmitter,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            callEngine.busyRejections.collect { number ->
                eventEmitter.emitCallRejectedBusy(number)
            }
        }

        scope.launch {
            var previous: CallState = CallState.Idle
            var startMillis = 0L
            var snapshot: Snapshot? = null

            callEngine.callState.collect { newState ->
                val prev = previous
                previous = newState
                snapshot = updateSnapshot(snapshot, newState)
                val s = snapshot

                when {
                    prev is CallState.Idle && newState is CallState.Ringing && !newState.isOutbound -> {
                        startMillis = System.currentTimeMillis()
                        eventEmitter.emitIncomingCall(newState.callId, newState.callerNumber)
                    }
                    prev is CallState.Idle && newState is CallState.Ringing && newState.isOutbound -> {
                        startMillis = System.currentTimeMillis()
                        eventEmitter.emitOutgoingCall(newState.callId, newState.callerNumber)
                    }
                    prev is CallState.Ringing && newState is CallState.Active -> {
                        startMillis = System.currentTimeMillis()
                        eventEmitter.emitCallConnected(
                            newState.callId, newState.remoteNumber, newState.remoteUri, newState.direction,
                        )
                    }
                    prev is CallState.Active && newState is CallState.Active -> {
                        if (prev.isMuted != newState.isMuted) {
                            eventEmitter.emitCallMuteChanged(newState.callId, newState.isMuted)
                        }
                        if (prev.isOnHold != newState.isOnHold) {
                            eventEmitter.emitCallHoldChanged(newState.callId, newState.isOnHold)
                        }
                    }
                    newState is CallState.Idle && prev !is CallState.Idle && s != null -> {
                        val duration = if (startMillis == 0L) 0
                            else ((System.currentTimeMillis() - startMillis) / 1000).toInt()
                        val reason = when {
                            !s.isOutbound && !s.everActive && s.terminatedLocally -> "rejected"
                            !s.isOutbound && !s.everActive -> "missed"
                            else -> "hangup"
                        }
                        eventEmitter.emitCallEnded(s.callId, s.number, s.direction, duration, reason)
                        snapshot = null
                        startMillis = 0L
                    }
                }
            }
        }
    }

    private fun updateSnapshot(prev: Snapshot?, state: CallState): Snapshot? = when (state) {
        is CallState.Ringing -> Snapshot(
            callId = state.callId,
            number = state.callerNumber,
            direction = state.direction,
            isOutbound = state.isOutbound,
            everActive = prev?.everActive ?: false,
            terminatedLocally = false,
        )
        is CallState.Active -> Snapshot(
            callId = state.callId,
            number = state.remoteNumber,
            direction = state.direction,
            isOutbound = state.isOutbound,
            everActive = true,
            terminatedLocally = false,
        )
        is CallState.Ending -> prev?.copy(terminatedLocally = true)
        is CallState.Idle -> prev
    }

    private data class Snapshot(
        val callId: String,
        val number: String,
        val direction: String,
        val isOutbound: Boolean,
        val everActive: Boolean,
        val terminatedLocally: Boolean,
    )
}

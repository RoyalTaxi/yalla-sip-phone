package uz.yalla.sipphone.data.workstation.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

class SipConnectionBridgeEmitter(
    private val sipAccountManager: SipAccountManager,
    private val eventEmitter: BridgeEventEmitter,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous = STATE_DISCONNECTED
            sipAccountManager.accounts.collect { accounts ->
                val state = when {
                    accounts.any { it.state is SipAccountState.Connected } -> STATE_CONNECTED
                    accounts.any { it.state is SipAccountState.Reconnecting } -> STATE_RECONNECTING
                    else -> STATE_DISCONNECTED
                }
                if (state != previous) {
                    previous = state
                    eventEmitter.emitConnectionChanged(
                        state = state,
                        attempt = accounts.count { it.state is SipAccountState.Connected },
                    )
                }
            }
        }
    }

    private companion object {
        const val STATE_CONNECTED = "connected"
        const val STATE_RECONNECTING = "reconnecting"
        const val STATE_DISCONNECTED = "disconnected"
    }
}

package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjsip_inv_state

private val logger = KotlinLogging.logger {}

// PJSIP Call wrapper. Callbacks run synchronously on the pjsip-event-loop thread.
// Do NOT dispatch work to another coroutine from a callback — SWIG pointers invalidate
// after return.
class PjsipCall : Call {

    private val callManager: PjsipCallManager
    private val deleted = AtomicBoolean(false)

    constructor(callManager: PjsipCallManager, account: Account) : super(account) {
        this.callManager = callManager
    }

    constructor(callManager: PjsipCallManager, account: Account, callId: Int) : super(account, callId) {
        this.callManager = callManager
    }

    override fun onCallState(prm: OnCallStateParam) =
        runSwigCallback("onCallState", callManager::isCallManagerDestroyed) {
            getInfo().use { info ->
                logger.info { "Call state: ${info.stateText} (${info.lastStatusCode})" }
                when (info.state) {
                    pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> callManager.onCallConfirmed(this)
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> callManager.onCallDisconnected(this)
                    else -> {}
                }
            }
        }

    override fun onCallMediaState(prm: OnCallMediaStateParam) =
        runSwigCallback("onCallMediaState", callManager::isCallManagerDestroyed) {
            callManager.connectCallAudio(this)
        }

    fun safeDelete() = deleteOnce(deleted, "call") { delete() }
}

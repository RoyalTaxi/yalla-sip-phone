package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjsip_inv_state

private val logger = KotlinLogging.logger {}

class PjsipCall : Call {

    private val callManager: PjsipCallManager
    private val pjScope: CoroutineScope
    private val deleted = AtomicBoolean(false)

    constructor(callManager: PjsipCallManager, account: Account, pjScope: CoroutineScope) : super(account) {
        this.callManager = callManager
        this.pjScope = pjScope
    }

    constructor(
        callManager: PjsipCallManager,
        account: Account,
        callId: Int,
        pjScope: CoroutineScope,
    ) : super(account, callId) {
        this.callManager = callManager
        this.pjScope = pjScope
    }

    override fun onCallState(prm: OnCallStateParam) {
        if (callManager.isCallManagerDestroyed()) return
        // Capture SWIG pointer values BEFORE dispatching — they become invalid after callback returns
        var info: CallInfo? = null
        try {
            info = getInfo()
            val stateText = info.stateText
            val lastStatusCode = info.lastStatusCode
            val state = info.state
            pjScope.launch {
                try {
                    logger.info { "Call state: $stateText ($lastStatusCode)" }
                    when (state) {
                        pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> callManager.onCallConfirmed(this@PjsipCall)
                        pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> callManager.onCallDisconnected(this@PjsipCall)
                        else -> {}
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error processing onCallState" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error capturing onCallState data" }
        } finally {
            info?.delete()
        }
    }

    override fun onCallMediaState(prm: OnCallMediaStateParam) {
        if (callManager.isCallManagerDestroyed()) return
        // No SWIG values needed from prm — dispatch directly
        pjScope.launch {
            try {
                callManager.connectCallAudio(this@PjsipCall)
            } catch (e: Exception) {
                logger.error(e) { "Error in onCallMediaState callback" }
            }
        }
    }

    // Prevents double-delete SIGSEGV — SWIG pointers crash if delete() is called twice
    fun safeDelete() {
        if (!deleted.compareAndSet(false, true)) return
        try {
            delete()
        } catch (e: Exception) {
            logger.warn(e) { "Error during call delete" }
        }
    }
}

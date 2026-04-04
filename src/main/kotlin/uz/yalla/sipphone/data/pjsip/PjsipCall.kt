package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjsip_inv_state

private val logger = KotlinLogging.logger {}

class PjsipCall : Call {

    private val callManager: PjsipCallManager

    constructor(callManager: PjsipCallManager, account: Account) : super(account) {
        this.callManager = callManager
    }

    constructor(callManager: PjsipCallManager, account: Account, callId: Int) : super(account, callId) {
        this.callManager = callManager
    }

    override fun onCallState(prm: OnCallStateParam) {
        if (callManager.isCallManagerDestroyed()) return
        var info: CallInfo? = null
        try {
            info = getInfo()
            logger.info { "Call state: ${info.stateText} (${info.lastStatusCode})" }
            when (info.state) {
                pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> callManager.onCallConfirmed(this)
                pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> callManager.onCallDisconnected(this)
                else -> {}
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in onCallState callback" }
        } finally {
            info?.delete()
        }
    }

    override fun onCallMediaState(prm: OnCallMediaStateParam) {
        if (callManager.isCallManagerDestroyed()) return
        try {
            callManager.connectCallAudio(this)
        } catch (e: Exception) {
            logger.error(e) { "Error in onCallMediaState callback" }
        }
    }
}

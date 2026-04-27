package uz.yalla.sipphone.data.pjsip.account

import uz.yalla.sipphone.data.pjsip.swig.use
import uz.yalla.sipphone.data.pjsip.swig.deleteOnce
import uz.yalla.sipphone.data.pjsip.swig.runSwigCallback

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import uz.yalla.sipphone.domain.sip.SipConstants
import uz.yalla.sipphone.domain.sip.SipError

private val logger = KotlinLogging.logger {}

class PjsipAccount(
    val accountId: String,
    val server: String,
    private val accountManager: PjsipAccountManager,
) : Account() {

    private val deleted = AtomicBoolean(false)

    override fun onRegState(prm: OnRegStateParam) =
        runSwigCallback("onRegState[$accountId]", accountManager::isAccountDestroyed) {
            val code = prm.code
            val reason = prm.reason
            getInfo().use { info ->
                val state = when {
                    code / 100 == SipConstants.STATUS_CLASS_SUCCESS && info.regIsActive -> {
                        logger.info { "[$accountId] Registered: ${info.uri}, expires: ${info.regExpiresSec}s" }
                        PjsipRegistrationState.Registered(uri = info.uri)
                    }
                    code / 100 == SipConstants.STATUS_CLASS_SUCCESS && !info.regIsActive -> {
                        logger.info { "[$accountId] Unregistered" }
                        PjsipRegistrationState.Idle
                    }
                    else -> {
                        logger.warn { "[$accountId] Registration failed: $code $reason (lastErr=${info.regLastErr})" }
                        PjsipRegistrationState.Failed(error = SipError.fromSipStatus(code, reason))
                    }
                }
                accountManager.updateRegistrationState(accountId, state)
            }
        }

    override fun onIncomingCall(prm: OnIncomingCallParam) =
        runSwigCallback("onIncomingCall[$accountId]", accountManager::isAccountDestroyed) {
            val callId = prm.callId
            logger.debug {
                val rdata = prm.rdata
                "RAW SIP INVITE: src=${rdata.srcAddress} info=${rdata.info}\n${rdata.wholeMsg}"
            }
            accountManager.handleIncomingCall(accountId, callId)
        }

    fun safeDelete() = deleteOnce(deleted, accountId) { delete() }
}

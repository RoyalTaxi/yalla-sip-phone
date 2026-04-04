package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import uz.yalla.sipphone.domain.RegistrationState
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

class PjsipAccount(private val accountManager: PjsipAccountManager) : Account() {

    override fun onRegState(prm: OnRegStateParam) {
        if (accountManager.isAccountDestroyed()) return
        var info: org.pjsip.pjsua2.AccountInfo? = null
        try {
            info = getInfo()
            val code = prm.code
            when {
                code / 100 == SipConstants.STATUS_CLASS_SUCCESS && info.regIsActive -> {
                    accountManager.updateRegistrationState(RegistrationState.Registered(server = info.uri))
                    logger.info { "Registered: ${info.uri}, expires: ${info.regExpiresSec}s" }
                }
                code / 100 == SipConstants.STATUS_CLASS_SUCCESS && !info.regIsActive -> {
                    accountManager.updateRegistrationState(RegistrationState.Idle)
                    logger.info { "Unregistered" }
                }
                else -> {
                    val error = SipError.fromSipStatus(prm.code, prm.reason)
                    accountManager.updateRegistrationState(RegistrationState.Failed(error = error))
                    logger.warn { "Registration failed: ${prm.code} ${prm.reason} (lastErr=${info.regLastErr})" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in onRegState callback" }
            accountManager.updateRegistrationState(RegistrationState.Failed(error = SipError.fromException(e)))
        } finally {
            info?.delete()
        }
    }

    override fun onIncomingCall(prm: OnIncomingCallParam) {
        if (accountManager.isAccountDestroyed()) return
        try {
            accountManager.handleIncomingCall(prm.callId)
        } catch (e: Exception) {
            logger.error(e) { "Error in onIncomingCall callback" }
        }
    }
}

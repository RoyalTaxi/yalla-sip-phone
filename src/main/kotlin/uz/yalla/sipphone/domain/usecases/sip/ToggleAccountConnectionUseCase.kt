package uz.yalla.sipphone.domain.usecases.sip

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

private val logger = KotlinLogging.logger {}

class ToggleAccountConnectionUseCase(
    private val sipAccountManager: SipAccountManager,
    private val callEngine: CallEngine,
) {
    suspend operator fun invoke(accountId: String): Result<Unit> {
        val account = sipAccountManager.accounts.value.find { it.id == accountId }
            ?: return Result.failure(IllegalArgumentException("Unknown account: $accountId"))
        val activeCallAccount = callEngine.callState.value.activeAccountId
        if (activeCallAccount == accountId && account.state is SipAccountState.Connected) {
            logger.debug { "Cannot disconnect SIP account with active call: $accountId" }
            return Result.failure(IllegalStateException("Active call on this account"))
        }
        return when (account.state) {
            is SipAccountState.Connected -> sipAccountManager.disconnect(accountId)
            is SipAccountState.Disconnected -> sipAccountManager.connect(accountId)
            is SipAccountState.Reconnecting -> Result.success(Unit)
        }
    }
}

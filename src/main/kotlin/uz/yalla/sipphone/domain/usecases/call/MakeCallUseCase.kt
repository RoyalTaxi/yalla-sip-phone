package uz.yalla.sipphone.domain.usecases.call

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.sip.PhoneNumberValidator
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

private val logger = KotlinLogging.logger {}

class MakeCallUseCase(
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
) {
    suspend operator fun invoke(number: String): Result<String> {
        val validation = PhoneNumberValidator.validate(number)
        validation.exceptionOrNull()?.let {
            logger.debug { "Invalid phone number input" }
            return Result.failure(it)
        }
        val firstConnected = sipAccountManager.accounts.value
            .firstOrNull { it.state is SipAccountState.Connected }
            ?: run {
                logger.info { "makeCall rejected: no connected SIP account" }
                return Result.failure(IllegalStateException("No connected SIP account"))
            }
        val validated = validation.getOrThrow()
        return callEngine.makeCall(validated, firstConnected.id).map { validated }
    }
}

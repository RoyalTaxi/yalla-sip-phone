package uz.yalla.sipphone.data.pjsip

import kotlinx.coroutines.delay
import uz.yalla.sipphone.domain.SipConstants

class RegisterRateLimiter(
    private val minIntervalMs: Long = SipConstants.RATE_LIMIT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAttempt = mutableMapOf<String, Long>()

    suspend fun awaitSlot(accountId: String) {
        val last = lastAttempt[accountId]
        if (last != null) {
            val wait = minIntervalMs - (clock() - last)
            if (wait > 0) delay(wait)
        }
        lastAttempt[accountId] = clock()
    }

    fun clear() = lastAttempt.clear()
}

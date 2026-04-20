package uz.yalla.sipphone.data.pjsip

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.yalla.sipphone.domain.SipConstants

class RegisterRateLimiter(
    private val minIntervalMs: Long = SipConstants.RATE_LIMIT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAttempt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun awaitSlot(accountId: String) {
        // Serialize per-account so two concurrent awaitSlot calls don't both
        // observe the pre-update timestamp and bypass the rate limit.
        locks.getOrPut(accountId) { Mutex() }.withLock {
            val last = lastAttempt[accountId]
            if (last != null) {
                val wait = minIntervalMs - (clock() - last)
                if (wait > 0) delay(wait)
            }
            lastAttempt[accountId] = clock()
        }
    }

    fun clear() {
        lastAttempt.clear()
        locks.clear()
    }
}

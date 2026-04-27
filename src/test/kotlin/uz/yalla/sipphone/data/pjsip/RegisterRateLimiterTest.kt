package uz.yalla.sipphone.data.pjsip

import uz.yalla.sipphone.data.pjsip.call.HoldController
import uz.yalla.sipphone.data.pjsip.account.ReconnectController
import uz.yalla.sipphone.data.pjsip.account.RegisterRateLimiter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterRateLimiterTest {

    @Test
    fun `first awaitSlot does not wait`() = runTest {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")

        assertEquals(0, currentTime)
    }

    @Test
    fun `second awaitSlot within interval waits remainder`() = runTest {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.awaitSlot("acc1")

        assertEquals(1_000, currentTime)
    }

    @Test
    fun `different accounts do not interfere`() = runTest {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.awaitSlot("acc2")

        assertEquals(0, currentTime)
    }

    @Test
    fun `clear resets all accounts`() = runTest {
        val limiter = RegisterRateLimiter(minIntervalMs = 1_000, clock = { currentTime })

        limiter.awaitSlot("acc1")
        limiter.clear()
        limiter.awaitSlot("acc1")

        assertEquals(0, currentTime)
    }
}

package uz.yalla.sipphone.core.auth

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SessionExpiredSignalTest {

    @Test
    fun `instantiates without throwing`() {
        SessionExpiredSignal()
    }

    @Test
    fun `delivers a signal to a single collector`() = runTest {
        val signal = SessionExpiredSignal()
        signal.events.test {
            signal.signal()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conflates rapid signals (latest wins)`() = runTest {
        val signal = SessionExpiredSignal()
        signal.signal()
        signal.signal()
        signal.signal()
        signal.events.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

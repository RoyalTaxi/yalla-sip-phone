package uz.yalla.sipphone.core.auth

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    /**
     * Pattern test mirroring [RootComponent.init]: gate the logout reaction on
     * `sessionStore.session.value != null` so a buffered signal that arrives after the
     * first logout cycle doesn't re-trigger.
     */
    @Test
    fun `idempotent consumer pattern handles signal after session cleared`() = runTest(UnconfinedTestDispatcher()) {
        val signal = SessionExpiredSignal()
        val sessionFlow = MutableStateFlow<Session?>(
            Session(token = "t", profile = Profile("1", "T", emptyList(), null)),
        )
        var logoutCalls = 0

        val job = launch {
            signal.events.collect {
                if (sessionFlow.value == null) return@collect
                logoutCalls++
                sessionFlow.value = null
            }
        }

        // Two signals fire in quick succession (e.g. two in-flight 401s).
        signal.signal()
        // Yield so the collector can process the first signal and clear the session.
        sessionFlow.first { it == null }
        signal.signal()

        job.cancel()
        assertEquals(
            1,
            logoutCalls,
            "second signal must be a no-op once session is null — otherwise we re-logout",
        )
    }
}

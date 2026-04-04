package uz.yalla.sipphone.feature.dialer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.FakeRegistrationEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DialerMakeCallErrorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `makeCall with failure result still records attempt`() = runTest {
        val regEngine = FakeRegistrationEngine().apply { simulateRegistered() }
        val callEngine = FakeCallEngine(
            makeCallResult = Result.failure(IllegalStateException("Not registered"))
        )
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val component = DialerComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            registrationEngine = regEngine,
            callEngine = callEngine,
            onDisconnected = {},
            ioDispatcher = testDispatcher,
        )

        component.makeCall("102")
        advanceUntilIdle()

        assertNotNull(callEngine.lastCallNumber)
        // Call state remains Idle since FakeCallEngine does not change state on failure
        assertIs<CallState.Idle>(callEngine.callState.value)
    }
}

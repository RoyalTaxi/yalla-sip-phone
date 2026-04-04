package uz.yalla.sipphone.feature.registration

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
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.FakeRegistrationEngine
import uz.yalla.sipphone.domain.RegistrationState
import uz.yalla.sipphone.domain.SipCredentials
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationDoubleConnectTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createComponent(
        engine: FakeRegistrationEngine = FakeRegistrationEngine(),
    ): Pair<RegistrationComponent, FakeRegistrationEngine> {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val component = RegistrationComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            sipEngine = engine,
            appSettings = AppSettings(),
            onRegistered = {},
            ioDispatcher = testDispatcher,
        )
        return component to engine
    }

    @Test
    fun `double connect is blocked when already registering`() = runTest {
        val engine = FakeRegistrationEngine()
        val (component, _) = createComponent(engine)
        val creds = SipCredentials("192.168.0.22", 5060, "102", "pass")

        component.connect(creds)
        advanceUntilIdle()
        assertIs<RegistrationState.Registering>(engine.registrationState.value)

        // Second connect should be silently ignored (guard in RegistrationComponent.connect)
        component.connect(creds)
        advanceUntilIdle()

        // State is still Registering (not a second register call)
        assertIs<RegistrationState.Registering>(engine.registrationState.value)
    }
}

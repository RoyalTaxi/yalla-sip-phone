package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.FakeRegistrationEngine
import uz.yalla.sipphone.domain.RegistrationEngine
import uz.yalla.sipphone.feature.dialer.DialerComponent
import uz.yalla.sipphone.feature.registration.RegistrationComponent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeRegistrationEngine = FakeRegistrationEngine()
    private val fakeCallEngine = FakeCallEngine()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRoot(): RootComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val factory = object : ComponentFactory {
            override fun createRegistration(
                context: com.arkivanov.decompose.ComponentContext,
                onRegistered: () -> Unit,
            ) = RegistrationComponent(
                componentContext = context,
                sipEngine = fakeRegistrationEngine,
                appSettings = AppSettings(),
                onRegistered = onRegistered,
                ioDispatcher = testDispatcher,
            )

            override fun createDialer(
                context: com.arkivanov.decompose.ComponentContext,
                onDisconnected: () -> Unit,
            ) = DialerComponent(
                componentContext = context,
                registrationEngine = fakeRegistrationEngine,
                callEngine = fakeCallEngine,
                onDisconnected = onDisconnected,
                ioDispatcher = testDispatcher,
            )
        }
        return RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            factory = factory,
        )
    }

    @Test
    fun `initial screen is Registration`() {
        val root = createRoot()
        val activeChild = root.childStack.value.active.instance
        assertIs<RootComponent.Child.Registration>(activeChild)
    }

    @Test
    fun `navigates to Dialer on registration`() {
        val root = createRoot()
        fakeRegistrationEngine.simulateRegistered()
        val activeChild = root.childStack.value.active.instance
        assertIs<RootComponent.Child.Dialer>(activeChild)
    }

    @Test
    fun `navigates back to Registration on disconnect`() {
        val root = createRoot()
        fakeRegistrationEngine.simulateRegistered()
        assertIs<RootComponent.Child.Dialer>(root.childStack.value.active.instance)

        fakeRegistrationEngine.simulateFailed("timeout")
        val activeChild = root.childStack.value.active.instance
        assertIs<RootComponent.Child.Registration>(activeChild)
    }
}

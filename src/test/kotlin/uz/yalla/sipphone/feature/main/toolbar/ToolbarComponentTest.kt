package uz.yalla.sipphone.feature.main.toolbar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.FakeRegistrationEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolbarComponentTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeCallEngine = FakeCallEngine()
    private val fakeRegistrationEngine = FakeRegistrationEngine()
    private lateinit var component: ToolbarComponent

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        component = ToolbarComponent(
            callEngine = fakeCallEngine,
            registrationEngine = fakeRegistrationEngine,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial agent status is ready`() {
        assertEquals(AgentStatus.READY, component.agentStatus.value)
    }

    @Test
    fun `setAgentStatus updates status`() {
        component.setAgentStatus(AgentStatus.AWAY)
        assertEquals(AgentStatus.AWAY, component.agentStatus.value)
    }

    @Test
    fun `makeCall rejects invalid number`() {
        assertFalse(component.makeCall("abc"))
    }

    @Test
    fun `makeCall accepts valid number`() {
        assertTrue(component.makeCall("+998901234567"))
    }

    @Test
    fun `call state flows from engine`() {
        assertEquals(CallState.Idle, component.callState.value)
    }

    @Test
    fun `phone input updates`() {
        component.updatePhoneInput("+998")
        assertEquals("+998", component.phoneInput.value)
    }
}

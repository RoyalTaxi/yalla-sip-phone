package uz.yalla.sipphone.feature.main.toolbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.SipAccount
import uz.yalla.sipphone.domain.SipAccountState
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.testing.FakeSipAccountManager
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
    private val fakeSipAccountManager = FakeSipAccountManager()
    private lateinit var component: ToolbarComponent

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        component = ToolbarComponent(
            callEngine = fakeCallEngine,
            sipAccountManager = fakeSipAccountManager,
            scope = CoroutineScope(testDispatcher),
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
        // makeCall now correctly rejects if no SIP account is connected (otherwise the call
        // silently fails downstream). Seed a connected account so the test actually exercises
        // phone-number validation, which is what this test is about.
        fakeSipAccountManager.seedAccounts(
            listOf(
                SipAccount(
                    id = "1001@example",
                    name = "SIP 1001",
                    credentials = SipCredentials(server = "example", username = "1001", password = ""),
                    state = SipAccountState.Connected,
                ),
            ),
        )
        assertTrue(component.makeCall("+998901234567"))
    }

    @Test
    fun `makeCall rejects when no connected account`() {
        // With zero connected accounts, makeCall should refuse — the old behaviour pretended
        // to succeed and produced a silent downstream failure.
        assertFalse(component.makeCall("+998901234567"))
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

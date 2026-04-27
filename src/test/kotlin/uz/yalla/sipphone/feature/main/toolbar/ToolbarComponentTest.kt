package uz.yalla.sipphone.feature.main.toolbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.yalla.sipphone.data.agent.InMemoryAgentStatusRepository
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountState
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.testing.FakeSipAccountManager
import uz.yalla.sipphone.testing.toolbarComponentForTest
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
    private val agentRepo = InMemoryAgentStatusRepository()
    private lateinit var component: ToolbarComponent

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        component = toolbarComponentForTest(
            scope = CoroutineScope(testDispatcher),
            callEngine = fakeCallEngine,
            sipAccountManager = fakeSipAccountManager,
            agentStatusRepository = agentRepo,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial agent status is ready`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(AgentStatus.READY, component.state.value.agent)
    }

    @Test
    fun `setAgentStatus updates status`() = runTest(testDispatcher) {
        component.setAgentStatus(AgentStatus.AWAY)
        advanceUntilIdle()
        assertEquals(AgentStatus.AWAY, component.state.value.agent)
    }

    @Test
    fun `makeCall ignores blank number`() = runTest(testDispatcher) {
        component.makeCall("")
        advanceUntilIdle()
        assertEquals(null, fakeCallEngine.lastCallNumber)
    }

    @Test
    fun `makeCall dispatches valid number to engine`() = runTest(testDispatcher) {
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
        component.makeCall("+998901234567")
        advanceUntilIdle()
        assertEquals("+998901234567", fakeCallEngine.lastCallNumber)
    }

    @Test
    fun `call state flows from engine`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(CallState.Idle, component.state.value.call)
    }

    @Test
    fun `phone input updates`() = runTest(testDispatcher) {
        component.updatePhoneInput("+998")
        advanceUntilIdle()
        assertEquals("+998", component.state.value.phoneInput)
    }
}

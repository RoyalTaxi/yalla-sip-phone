package uz.yalla.sipphone.testing

import kotlinx.coroutines.CoroutineScope
import uz.yalla.sipphone.data.agent.InMemoryAgentStatusRepository
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.feature.main.toolbar.ToolbarComponent
import uz.yalla.sipphone.feature.main.toolbar.sideeffect.CallSideEffects

internal fun toolbarComponentForTest(
    scope: CoroutineScope,
    callEngine: CallEngine = FakeCallEngine(),
    sipAccountManager: SipAccountManager = FakeSipAccountManager(),
    agentStatusRepository: AgentStatusRepository = InMemoryAgentStatusRepository(),
): ToolbarComponent = ToolbarComponent(
    callState = callEngine.callState,
    accounts = sipAccountManager.accounts,
    agentStatusRepository = agentStatusRepository,
    callEngine = callEngine,
    sipAccountManager = sipAccountManager,
    callSideEffects = CallSideEffects(),
    scope = scope,
)

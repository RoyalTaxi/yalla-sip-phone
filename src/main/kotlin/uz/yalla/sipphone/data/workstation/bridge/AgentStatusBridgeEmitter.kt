package uz.yalla.sipphone.data.workstation.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.domain.agent.AgentStatusRepository

class AgentStatusBridgeEmitter(
    private val agentStatusRepository: AgentStatusRepository,
    private val eventEmitter: BridgeEventEmitter,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous = agentStatusRepository.current
            agentStatusRepository.status.collect { newStatus ->
                if (newStatus != previous) {
                    eventEmitter.emitAgentStatusChanged(
                        status = newStatus.name.lowercase(),
                        previousStatus = previous.name.lowercase(),
                    )
                    previous = newStatus
                }
            }
        }
    }
}

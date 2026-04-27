package uz.yalla.sipphone.domain.usecases.agent

import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.agent.AgentStatusRepository

class SetAgentStatusUseCase(private val repository: AgentStatusRepository) {
    operator fun invoke(status: AgentStatus) = repository.set(status)
}

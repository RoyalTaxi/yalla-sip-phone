package uz.yalla.sipphone.feature.workstation.presentation.model

import uz.yalla.sipphone.domain.agent.AgentStatus

internal fun WorkstationComponent.setAgentStatus(status: AgentStatus) {
    agentStatusRepository.set(status)
}

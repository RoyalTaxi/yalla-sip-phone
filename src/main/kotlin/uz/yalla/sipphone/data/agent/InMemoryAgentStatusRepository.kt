package uz.yalla.sipphone.data.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.agent.AgentStatusRepository

class InMemoryAgentStatusRepository : AgentStatusRepository {
    private val _status = MutableStateFlow(AgentStatus.READY)
    override val status: Flow<AgentStatus> = _status.asStateFlow()
    override val current: AgentStatus get() = _status.value
    override fun set(status: AgentStatus) {
        _status.value = status
    }
}

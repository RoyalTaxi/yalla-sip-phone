package uz.yalla.sipphone.domain.agent

import kotlinx.coroutines.flow.Flow

interface AgentStatusRepository {
    val status: Flow<AgentStatus>
    val current: AgentStatus
    fun set(status: AgentStatus)
}

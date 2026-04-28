package uz.yalla.sipphone.data.workstation.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.domain.agent.AgentStatus

class AgentStatusHolder {
    private val _status = MutableStateFlow(AgentStatus.READY)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()
    val current: AgentStatus get() = _status.value

    fun set(status: AgentStatus) {
        _status.value = status
    }
}

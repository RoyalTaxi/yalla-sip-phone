package uz.yalla.sipphone.domain.agent

import androidx.compose.runtime.Immutable

@Immutable
data class AgentInfo(
    val id: String,
    val name: String,
)

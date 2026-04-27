package uz.yalla.sipphone.domain.panel

import uz.yalla.sipphone.domain.agent.AgentInfo

interface WebPanelBridge {

    val isReady: Boolean

    fun activate(session: WebPanelSession)

    fun deactivate()

    fun emitThemeChanged(isDark: Boolean)

    fun emitLocaleChanged(locale: String)
}

data class WebPanelSession(
    val agent: AgentInfo,
    val token: String,
    val onRequestLogout: () -> Unit,
)

package uz.yalla.sipphone.feature.main

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.panel.WebPanelSession
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.feature.main.toolbar.ToolbarComponent

class MainComponent(
    componentContext: ComponentContext,
    val session: Session,
    private val callEngine: CallEngine,
    sipAccountManager: SipAccountManager,
    agentStatusRepository: AgentStatusRepository,
    val jcefManager: JcefManager,
    eventEmitter: BridgeEventEmitter,
    private val webPanelBridge: WebPanelBridge,
    val updateManager: UpdateManager,
    configPreferences: ConfigPreferences,
    toolbarFactory: (CoroutineScope) -> ToolbarComponent,
    private val onLogout: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = coroutineScope()

    val toolbar: ToolbarComponent = toolbarFactory(scope)

    val callState: StateFlow<CallState> get() = callEngine.callState

    val agentInfo: AgentInfo = AgentInfo(id = session.profile.id, name = session.profile.fullName)

    val dispatcherUrl: String = run {
        val base = session.profile.panelUrl?.takeIf { it.isNotBlank() }
            ?: configPreferences.current().dispatcherUrl
        if (session.token.isNotEmpty()) "$base?token=${session.token}" else base
    }

    private val callEventOrchestrator = CallEventOrchestrator(
        callEngine = callEngine,
        sipAccountManager = sipAccountManager,
        eventEmitter = eventEmitter,
        agentStatusRepository = agentStatusRepository,
    )

    init {
        lifecycle.doOnDestroy {
            toolbar.release()
            webPanelBridge.deactivate()
        }

        webPanelBridge.activate(
            WebPanelSession(
                agent = agentInfo,
                token = session.token,
                onRequestLogout = { onLogout() },
            ),
        )

        callEventOrchestrator.start(scope)
    }

    fun onThemeChanged(isDark: Boolean) = webPanelBridge.emitThemeChanged(isDark)
    fun onLocaleChanged(locale: String) = webPanelBridge.emitLocaleChanged(locale)

    fun logout() {
        toolbar.closeSettings()
        scope.launch {
            delay(SETTINGS_CLOSE_ANIMATION_MS)
            toolbar.disconnect()
            onLogout()
        }
    }

    private companion object {
        const val SETTINGS_CLOSE_ANIMATION_MS = 350L
    }
}

package uz.yalla.sipphone.feature.main

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.jcef.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.BridgeRouter
import uz.yalla.sipphone.data.jcef.BridgeSecurity
import uz.yalla.sipphone.data.jcef.BridgeAuditLog
import uz.yalla.sipphone.data.jcef.JcefManager
import uz.yalla.sipphone.data.update.UpdateManager
import uz.yalla.sipphone.domain.AgentInfo
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.SipAccountManager
import uz.yalla.sipphone.feature.main.toolbar.ToolbarComponent

class MainComponent(
    componentContext: ComponentContext,
    val authResult: AuthResult,
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    val jcefManager: JcefManager,
    private val eventEmitter: BridgeEventEmitter,
    private val security: BridgeSecurity,
    private val auditLog: BridgeAuditLog,
    val updateManager: UpdateManager,
    private val onLogout: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = coroutineScope()

    val toolbar = ToolbarComponent(
        callEngine = callEngine,
        sipAccountManager = sipAccountManager,
        scope = scope,
    )

    val dispatcherUrl: String = if (authResult.token.isNotEmpty())
        "${authResult.dispatcherUrl}?token=${authResult.token}"
    else
        authResult.dispatcherUrl
    val agentInfo: AgentInfo = authResult.agent
    private var bridgeRouter: BridgeRouter? = null

    private val callEventOrchestrator = CallEventOrchestrator(
        callEngine = callEngine,
        sipAccountManager = sipAccountManager,
        eventEmitter = eventEmitter,
        agentStatusProvider = { toolbar.agentStatus },
    )

    init {
        lifecycle.doOnDestroy {
            toolbar.releaseAudioResources()
            eventEmitter.detach()
            bridgeRouter?.dispose()
            jcefManager.teardownBridge()
        }

        // Set up JS Bridge (only if JCEF is initialized — skipped in tests)
        if (jcefManager.isInitialized) {
            eventEmitter.agentInfo = authResult.agent

            val bridgeRouter = BridgeRouter(
                callEngine = callEngine,
                sipAccountManager = sipAccountManager,
                security = security,
                auditLog = auditLog,
                agentStatusProvider = { toolbar.agentStatus.value },
                onAgentStatusChange = { toolbar.setAgentStatus(it) },
                onReady = eventEmitter::completeHandshake,
                onRequestLogout = { onLogout() },
                tokenProvider = { authResult.token },
            )
            this.bridgeRouter = bridgeRouter

            jcefManager.setupBridge(
                installMessageRouter = bridgeRouter::install,
                onPageLoadEnd = eventEmitter::injectBridgeScript,
                onPageLoadStart = eventEmitter::resetHandshake,
            )
        }

        callEventOrchestrator.start(scope)
    }

    fun onThemeChanged(isDark: Boolean) {
        eventEmitter.emitThemeChanged(if (isDark) "dark" else "light")
    }

    fun onLocaleChanged(locale: String) {
        eventEmitter.emitLocaleChanged(locale)
    }

    fun logout() {
        toolbar.closeSettings()
        scope.launch {
            kotlinx.coroutines.delay(SETTINGS_CLOSE_ANIMATION_MS)
            toolbar.disconnect()
            onLogout()
        }
    }

    companion object {
        private const val SETTINGS_CLOSE_ANIMATION_MS = 350L
    }
}

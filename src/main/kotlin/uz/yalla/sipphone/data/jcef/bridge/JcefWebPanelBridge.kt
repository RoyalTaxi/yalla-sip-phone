package uz.yalla.sipphone.data.jcef.bridge

import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.keys.KeyShortcutRegistry
import uz.yalla.sipphone.data.workstation.agent.AgentStatusHolder
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.sip.SipAccountManager

data class WebPanelSession(
    val agent: AgentInfo,
    val token: String,
    val onRequestLogout: () -> Unit,
)

class JcefWebPanelBridge(
    private val jcefManager: JcefManager,
    private val eventEmitter: BridgeEventEmitter,
    private val security: BridgeSecurity,
    private val keyRegistry: KeyShortcutRegistry,
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    private val agentStatusHolder: AgentStatusHolder,
) {
    private var router: BridgeRouter? = null

    val isReady: Boolean get() = jcefManager.isInitialized

    fun activate(session: WebPanelSession) {
        if (!jcefManager.isInitialized) return
        deactivate()
        eventEmitter.agentInfo = session.agent
        val r = BridgeRouter(
            callEngine = callEngine,
            sipAccountManager = sipAccountManager,
            security = security,
            keyRegistry = keyRegistry,
            agentStatusHolder = agentStatusHolder,
            onReady = eventEmitter::completeHandshake,
            onRequestLogout = session.onRequestLogout,
            tokenProvider = { session.token },
        )
        router = r
        jcefManager.setupBridge(
            installMessageRouter = r::install,
            onPageLoadEnd = eventEmitter::injectBridgeScript,
            onPageLoadStart = eventEmitter::resetHandshake,
        )
    }

    fun deactivate() {
        router?.dispose()
        router = null
        eventEmitter.detach()
        jcefManager.teardownBridge()
    }

    fun emitThemeChanged(isDark: Boolean) =
        eventEmitter.emitThemeChanged(if (isDark) "dark" else "light")

    fun emitLocaleChanged(locale: String) = eventEmitter.emitLocaleChanged(locale)
}

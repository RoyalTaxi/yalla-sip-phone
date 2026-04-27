package uz.yalla.sipphone.data.jcef.bridge

import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.keys.KeyShortcutRegistry

import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.panel.WebPanelSession

class JcefWebPanelBridge(
    private val jcefManager: JcefManager,
    private val eventEmitter: BridgeEventEmitter,
    private val security: BridgeSecurity,
    private val keyRegistry: KeyShortcutRegistry,
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    private val agentStatusRepository: AgentStatusRepository,
) : WebPanelBridge {

    private var router: BridgeRouter? = null

    override val isReady: Boolean get() = jcefManager.isInitialized

    override fun activate(session: WebPanelSession) {
        if (!jcefManager.isInitialized) return
        deactivate()
        eventEmitter.agentInfo = session.agent
        val r = BridgeRouter(
            callEngine = callEngine,
            sipAccountManager = sipAccountManager,
            security = security,
            keyRegistry = keyRegistry,
            agentStatusRepository = agentStatusRepository,
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

    override fun deactivate() {
        router?.dispose()
        router = null
        eventEmitter.detach()
        jcefManager.teardownBridge()
    }

    override fun emitThemeChanged(isDark: Boolean) =
        eventEmitter.emitThemeChanged(if (isDark) "dark" else "light")

    override fun emitLocaleChanged(locale: String) = eventEmitter.emitLocaleChanged(locale)
}

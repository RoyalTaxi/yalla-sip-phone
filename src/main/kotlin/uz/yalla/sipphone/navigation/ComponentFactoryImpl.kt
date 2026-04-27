package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.usecase.LoginUseCase
import uz.yalla.sipphone.domain.auth.usecase.ManualConnectUseCase
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.main.MainComponent
import uz.yalla.sipphone.feature.main.toolbar.ToolbarComponent

class ComponentFactoryImpl(
    private val sipAccountManager: SipAccountManager,
    private val callEngine: CallEngine,
    private val agentStatusRepository: AgentStatusRepository,
    private val jcefManager: JcefManager,
    private val eventEmitter: BridgeEventEmitter,
    private val webPanelBridge: WebPanelBridge,
    private val updateManager: UpdateManager,
    private val loginUseCase: LoginUseCase,
    private val manualConnectUseCase: ManualConnectUseCase,
    private val configPreferences: ConfigPreferences,
    private val toolbarFactory: (CoroutineScope) -> ToolbarComponent,
) : ComponentFactory {

    override fun createAuth(context: ComponentContext): AuthComponent = AuthComponent(
        componentContext = context,
        loginUseCase = loginUseCase,
        manualConnectUseCase = manualConnectUseCase,
    )

    override fun createMain(
        context: ComponentContext,
        session: Session,
        onLogout: () -> Unit,
    ): MainComponent = MainComponent(
        componentContext = context,
        session = session,
        callEngine = callEngine,
        sipAccountManager = sipAccountManager,
        agentStatusRepository = agentStatusRepository,
        jcefManager = jcefManager,
        eventEmitter = eventEmitter,
        webPanelBridge = webPanelBridge,
        updateManager = updateManager,
        configPreferences = configPreferences,
        toolbarFactory = toolbarFactory,
        onLogout = onLogout,
    )
}

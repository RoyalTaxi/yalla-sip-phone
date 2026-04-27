package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.UserPreferences
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.data.workstation.bridge.AgentStatusBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.CallEventBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.SipConnectionBridgeEmitter
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.usecase.LoginUseCase
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.domain.auth.usecase.ManualConnectUseCase
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent
import uz.yalla.sipphone.feature.workstation.presentation.model.WorkstationComponent
import uz.yalla.sipphone.feature.workstation.sideeffect.CallSideEffects

class ComponentFactoryImpl(
    private val loginUseCase: LoginUseCase,
    private val manualConnectUseCase: ManualConnectUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    private val agentStatusRepository: AgentStatusRepository,
    private val webPanelBridge: WebPanelBridge,
    private val jcefManager: JcefManager,
    private val updateManager: UpdateManager,
    private val userPreferences: UserPreferences,
    private val configPreferences: ConfigPreferences,
    private val callSideEffectsFactory: () -> CallSideEffects,
    private val callEventEmitterFactory: () -> CallEventBridgeEmitter,
    private val sipConnectionEmitterFactory: () -> SipConnectionBridgeEmitter,
    private val agentStatusEmitterFactory: () -> AgentStatusBridgeEmitter,
) : ComponentFactory {

    override fun createAuth(context: ComponentContext): AuthComponent = AuthComponent(
        componentContext = context,
        loginUseCase = loginUseCase,
        manualConnectUseCase = manualConnectUseCase,
    )

    override fun createWorkstation(
        context: ComponentContext,
        session: Session,
    ): WorkstationComponent {
        val agentInfo = AgentInfo(id = session.profile.id, name = session.profile.fullName)
        val dispatcherUrl = run {
            val base = session.profile.panelUrl?.takeIf { it.isNotBlank() }
                ?: configPreferences.current().dispatcherUrl
            if (session.token.isNotEmpty()) "$base?token=${session.token}" else base
        }
        val prefs = userPreferences.current()
        return WorkstationComponent(
            componentContext = context,
            jcefManager = jcefManager,
            updateManager = updateManager,
            agentInfo = agentInfo,
            dispatcherUrl = dispatcherUrl,
            sessionToken = session.token,
            isDarkTheme = prefs.isDarkTheme,
            locale = prefs.locale,
            callEngine = callEngine,
            sipAccountManager = sipAccountManager,
            agentStatusRepository = agentStatusRepository,
            userPreferences = userPreferences,
            logoutUseCase = logoutUseCase,
            webPanelBridge = webPanelBridge,
            callSideEffects = callSideEffectsFactory(),
            callEventEmitter = callEventEmitterFactory(),
            sipConnectionEmitter = sipConnectionEmitterFactory(),
            agentStatusEmitter = agentStatusEmitterFactory(),
        )
    }
}

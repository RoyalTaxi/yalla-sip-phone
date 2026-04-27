package uz.yalla.sipphone.feature.workstation.presentation.model

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.yalla.sipphone.core.infra.BaseComponent
import uz.yalla.sipphone.core.prefs.UserPreferences
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.data.workstation.bridge.AgentStatusBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.CallEventBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.SipConnectionBridgeEmitter
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.call.callDurationFlow
import uz.yalla.sipphone.domain.panel.WebPanelBridge
import uz.yalla.sipphone.domain.panel.WebPanelSession
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationEffect
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationState
import uz.yalla.sipphone.feature.workstation.sideeffect.CallSideEffects

class WorkstationComponent(
    componentContext: ComponentContext,
    val jcefManager: JcefManager,
    val updateManager: UpdateManager,
    agentInfo: AgentInfo,
    dispatcherUrl: String,
    sessionToken: String,
    isDarkTheme: Boolean,
    locale: String,
    internal val callEngine: CallEngine,
    internal val sipAccountManager: SipAccountManager,
    internal val agentStatusRepository: AgentStatusRepository,
    internal val userPreferences: UserPreferences,
    internal val logoutUseCase: LogoutUseCase,
    internal val webPanelBridge: WebPanelBridge,
    private val callSideEffects: CallSideEffects,
    private val callEventEmitter: CallEventBridgeEmitter,
    private val sipConnectionEmitter: SipConnectionBridgeEmitter,
    private val agentStatusEmitter: AgentStatusBridgeEmitter,
) : BaseComponent<WorkstationState, WorkstationEffect>(
    componentContext,
    WorkstationState.initial(agentInfo, dispatcherUrl, isDarkTheme, locale),
) {

    val callState: StateFlow<CallState> get() = callEngine.callState

    init {
        wireMergedState()

        callSideEffects.start(scope, callEngine.callState)
        callEventEmitter.start(scope)
        sipConnectionEmitter.start(scope)
        agentStatusEmitter.start(scope)

        webPanelBridge.activate(
            WebPanelSession(
                agent = agentInfo,
                token = sessionToken,
                onRequestLogout = { triggerLogout() },
            ),
        )

        lifecycle.doOnDestroy {
            callSideEffects.release()
            webPanelBridge.deactivate()
        }
    }

    private fun wireMergedState() {
        val merged: StateFlow<MergedSlices> = combine(
            listOf(
                callEngine.callState,
                sipAccountManager.accounts,
                agentStatusRepository.status,
                callDurationFlow(callEngine.callState),
                userPreferences.values,
                updateManager.state,
                updateManager.dialogDismissed,
                updateManager.diagnosticsVisible,
            )
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            MergedSlices(
                call = values[0] as CallState,
                accounts = values[1] as List<uz.yalla.sipphone.domain.sip.SipAccount>,
                agent = values[2] as uz.yalla.sipphone.domain.agent.AgentStatus,
                callDuration = values[3] as String?,
                userPrefs = values[4] as uz.yalla.sipphone.core.prefs.UserPreferencesValues,
                updateState = values[5] as uz.yalla.sipphone.domain.update.UpdateState,
                updateDismissed = values[6] as Boolean,
                diagnostics = values[7] as Boolean,
            )
        }.stateIn(scope, SharingStarted.Eagerly, MergedSlices.empty())

        scope.launch {
            merged.collect { slice ->
                intent {
                    reduce {
                        state.copy(
                            call = slice.call,
                            accounts = slice.accounts,
                            agent = slice.agent,
                            callDuration = slice.callDuration,
                            isDarkTheme = slice.userPrefs.isDarkTheme,
                            locale = slice.userPrefs.locale,
                            updateState = slice.updateState,
                            updateDialogDismissed = slice.updateDismissed,
                            diagnosticsVisible = slice.diagnostics,
                        )
                    }
                }
            }
        }

        scope.launch {
            callEngine.callState.collect { s ->
                intent {
                    reduce {
                        when {
                            s is CallState.Ringing && !s.isOutbound -> state.copy(phoneInput = s.callerNumber)
                            s is CallState.Idle -> state.copy(phoneInput = "")
                            else -> state
                        }
                    }
                }
            }
        }
    }

    internal fun triggerLogout() {
        intent {
            reduce { state.copy(settingsVisible = false) }
            logoutUseCase()
            postSideEffect(WorkstationEffect.NavigateToAuth)
        }
    }

    private data class MergedSlices(
        val call: CallState,
        val accounts: List<uz.yalla.sipphone.domain.sip.SipAccount>,
        val agent: uz.yalla.sipphone.domain.agent.AgentStatus,
        val callDuration: String?,
        val userPrefs: uz.yalla.sipphone.core.prefs.UserPreferencesValues,
        val updateState: uz.yalla.sipphone.domain.update.UpdateState,
        val updateDismissed: Boolean,
        val diagnostics: Boolean,
    ) {
        companion object {
            fun empty(): MergedSlices = MergedSlices(
                call = CallState.Idle,
                accounts = emptyList(),
                agent = uz.yalla.sipphone.domain.agent.AgentStatus.READY,
                callDuration = null,
                userPrefs = uz.yalla.sipphone.core.prefs.UserPreferencesValues(locale = "uz", isDarkTheme = true),
                updateState = uz.yalla.sipphone.domain.update.UpdateState.Idle,
                updateDismissed = false,
                diagnostics = false,
            )
        }
    }
}

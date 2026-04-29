package uz.yalla.sipphone.feature.workstation.presentation.model

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.yalla.sipphone.core.infra.BaseComponent
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.UserPreferences
import uz.yalla.sipphone.core.prefs.UserPreferencesValues
import uz.yalla.sipphone.data.jcef.bridge.JcefWebPanelBridge
import uz.yalla.sipphone.data.jcef.bridge.WebPanelSession
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.data.workstation.agent.AgentStatusHolder
import uz.yalla.sipphone.data.workstation.bridge.AgentStatusBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.CallEventBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.SipConnectionBridgeEmitter
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.call.callDurationFlow
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.update.UpdateState
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
    internal val agentStatusHolder: AgentStatusHolder,
    internal val userPreferences: UserPreferences,
    internal val configPreferences: ConfigPreferences,
    internal val logoutUseCase: LogoutUseCase,
    internal val webPanelBridge: JcefWebPanelBridge,
    private val callSideEffects: CallSideEffects,
    private val callEventEmitter: CallEventBridgeEmitter,
    private val sipConnectionEmitter: SipConnectionBridgeEmitter,
    private val agentStatusEmitter: AgentStatusBridgeEmitter,
) : BaseComponent<WorkstationState, WorkstationEffect>(
    componentContext,
    WorkstationState.initial(agentInfo, dispatcherUrl, isDarkTheme, locale),
) {

    val callState: StateFlow<CallState> get() = callEngine.callState

    /**
     * Guard against double-taps on the Call button. SubmitCall fires `dispatchCall(number)`
     * which `scope.launch`-es onto pjDispatcher; the JCEF JS bridge guards on `callState !=
     * Idle` but the toolbar button does not, so a fast second tap can race the first call's
     * state transition. This atomic gate sits in front of `callEngine.makeCall`.
     */
    internal val callDispatching: AtomicBoolean = AtomicBoolean(false)

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
        val sipSlice = combine(
            callEngine.callState,
            sipAccountManager.accounts,
            agentStatusHolder.status,
            callDurationFlow(callEngine.callState),
        ) { call, accounts, agent, duration ->
            SipSlice(call, accounts, agent, duration)
        }

        val updateSlice = combine(
            updateManager.state,
            updateManager.dialogDismissed,
            updateManager.diagnosticsVisible,
        ) { update, dismissed, diagnostics ->
            UpdateSlice(update, dismissed, diagnostics)
        }

        val merged: StateFlow<MergedSlices> = combine(
            sipSlice, updateSlice, userPreferences.values,
        ) { sip, update, prefs ->
            MergedSlices(sip, update, prefs)
        }.stateIn(scope, SharingStarted.Eagerly, MergedSlices.empty())

        scope.launch {
            merged.collect { m ->
                intent {
                    reduce {
                        state.copy(
                            call = m.sip.call,
                            accounts = m.sip.accounts,
                            agent = m.sip.agent,
                            callDuration = m.sip.duration,
                            isDarkTheme = m.userPrefs.isDarkTheme,
                            locale = m.userPrefs.locale,
                            updateState = m.update.state,
                            updateDialogDismissed = m.update.dismissed,
                            diagnosticsVisible = m.update.diagnostics,
                        )
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

    private data class SipSlice(
        val call: CallState,
        val accounts: List<SipAccount>,
        val agent: AgentStatus,
        val duration: String?,
    )

    private data class UpdateSlice(
        val state: UpdateState,
        val dismissed: Boolean,
        val diagnostics: Boolean,
    )

    private data class MergedSlices(
        val sip: SipSlice,
        val update: UpdateSlice,
        val userPrefs: UserPreferencesValues,
    ) {
        companion object {
            fun empty(): MergedSlices = MergedSlices(
                sip = SipSlice(
                    call = CallState.Idle,
                    accounts = emptyList(),
                    agent = AgentStatus.READY,
                    duration = null,
                ),
                update = UpdateSlice(
                    state = UpdateState.Idle,
                    dismissed = false,
                    diagnostics = false,
                ),
                userPrefs = UserPreferencesValues(
                    locale = DEFAULT_LOCALE,
                    isDarkTheme = DEFAULT_DARK_THEME,
                ),
            )

            private const val DEFAULT_LOCALE = "uz"
            private const val DEFAULT_DARK_THEME = true
        }
    }
}

package uz.yalla.sipphone.feature.main.toolbar

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.agent.AgentStatusRepository
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.call.callDurationFlow
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState
import uz.yalla.sipphone.feature.main.toolbar.sideeffect.CallSideEffects

private val logger = KotlinLogging.logger {}

class ToolbarComponent(
    callState: StateFlow<CallState>,
    accounts: StateFlow<List<SipAccount>>,
    private val agentStatusRepository: AgentStatusRepository,
    private val callEngine: CallEngine,
    private val sipAccountManager: SipAccountManager,
    private val callSideEffects: CallSideEffects,
    private val scope: CoroutineScope,
) {
    private val _phoneInput = MutableStateFlow("")
    private val _settingsVisible = MutableStateFlow(false)

    val state: StateFlow<ToolbarUiState> = combine(
        listOf(
            callState,
            accounts,
            agentStatusRepository.status,
            _phoneInput,
            callDurationFlow(callState),
            _settingsVisible,
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ToolbarUiState(
            call = values[0] as CallState,
            accounts = values[1] as List<SipAccount>,
            agent = values[2] as AgentStatus,
            phoneInput = values[3] as String,
            callDuration = values[4] as String?,
            settingsVisible = values[5] as Boolean,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ToolbarUiState())

    init {
        callSideEffects.start(scope, callState)
        scope.launch {
            callState.collect { s ->
                when {
                    s is CallState.Ringing && !s.isOutbound -> _phoneInput.value = s.callerNumber
                    s is CallState.Idle -> _phoneInput.value = ""
                    else -> Unit
                }
            }
        }
    }

    fun updatePhoneInput(value: String) { _phoneInput.value = value }
    fun openSettings() { _settingsVisible.value = true }
    fun closeSettings() { _settingsVisible.value = false }

    fun setAgentStatus(status: AgentStatus) = agentStatusRepository.set(status)

    fun makeCall(number: String) {
        if (number.isBlank()) return
        scope.launch {
            callEngine.makeCall(number).onFailure { logger.warn(it) { "makeCall failed" } }
        }
    }

    fun answerCall() = scope.launch { callEngine.answerCall() }
    fun rejectCall() = scope.launch { callEngine.hangupCall() }
    fun hangupCall() = scope.launch { callEngine.hangupCall() }
    fun toggleMute() = scope.launch { callEngine.toggleMute() }
    fun toggleHold() = scope.launch { callEngine.toggleHold() }

    fun onSipChipClick(accountId: String) = scope.launch {
        val account = sipAccountManager.accounts.value.firstOrNull { it.id == accountId } ?: return@launch
        if (callEngine.callState.value !is CallState.Idle) return@launch
        if (account.state is SipAccountState.Connected) {
            sipAccountManager.disconnect(accountId)
        } else {
            sipAccountManager.connect(accountId)
        }
    }

    fun disconnect() = scope.launch { sipAccountManager.unregisterAll() }

    fun release() {
        callSideEffects.release()
    }
}

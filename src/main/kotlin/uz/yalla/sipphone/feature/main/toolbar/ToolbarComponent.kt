package uz.yalla.sipphone.feature.main.toolbar

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.domain.CallEngine
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.PhoneNumberValidator
import uz.yalla.sipphone.domain.RegistrationEngine
import uz.yalla.sipphone.domain.RegistrationState

private val logger = KotlinLogging.logger {}

class ToolbarComponent(
    val callEngine: CallEngine,
    val registrationEngine: RegistrationEngine,
) {
    val callState: StateFlow<CallState> = callEngine.callState
    val registrationState: StateFlow<RegistrationState> = registrationEngine.registrationState

    private val _agentStatus = MutableStateFlow(AgentStatus.READY)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun setAgentStatus(status: AgentStatus) {
        _agentStatus.value = status
    }

    fun updatePhoneInput(value: String) {
        _phoneInput.value = value
    }

    fun makeCall(number: String): Boolean {
        val validation = PhoneNumberValidator.validate(number)
        if (validation.isFailure) {
            logger.warn { "Invalid phone number" }
            return false
        }
        scope.launch { callEngine.makeCall(validation.getOrThrow()) }
        return true
    }

    fun answerCall() {
        scope.launch { callEngine.answerCall() }
    }

    fun rejectCall() {
        scope.launch { callEngine.hangupCall() }
    }

    fun hangupCall() {
        scope.launch { callEngine.hangupCall() }
    }

    fun toggleMute() {
        scope.launch { callEngine.toggleMute() }
    }

    fun toggleHold() {
        scope.launch { callEngine.toggleHold() }
    }

    fun disconnect() {
        scope.launch { registrationEngine.unregister() }
    }
}

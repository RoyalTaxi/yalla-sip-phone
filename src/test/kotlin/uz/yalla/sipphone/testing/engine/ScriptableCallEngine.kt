package uz.yalla.sipphone.testing.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.testing.scenario.ScenarioStep

class ScriptableCallEngine(
    initialState: CallState = CallState.Idle,
    var makeCallResult: Result<Unit> = Result.success(Unit),
    var sendDtmfResult: Result<Unit> = Result.success(Unit),
    var transferCallResult: Result<Unit> = Result.success(Unit),
) : CallEngine {

    private val _callState = MutableStateFlow(initialState)
    override val callState = _callState.asStateFlow()

    private val _busyRejections = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val busyRejections = _busyRejections.asSharedFlow()

    fun emitBusyRejection(callerNumber: String) {
        _busyRejections.tryEmit(callerNumber)
    }

    sealed interface Action {
        data class MakeCall(val number: String, val accountId: String = "") : Action
        data object AnswerCall : Action
        data object HangupCall : Action
        data object ToggleMute : Action
        data object ToggleHold : Action
        data class SetMute(val callId: String, val muted: Boolean) : Action
        data class SetHold(val callId: String, val onHold: Boolean) : Action
        data class SendDtmf(val callId: String, val digits: String) : Action
        data class TransferCall(val callId: String, val destination: String) : Action
    }

    private val _actions = mutableListOf<Action>()
    val actions: List<Action> get() = _actions.toList()

    fun clearActions() {
        _actions.clear()
    }

    override suspend fun makeCall(number: String, accountId: String): Result<Unit> {
        _actions += Action.MakeCall(number, accountId)
        return makeCallResult
    }

    var answerCallResult: Result<Unit> = Result.success(Unit)
    var hangupCallResult: Result<Unit> = Result.success(Unit)
    var toggleMuteResult: Result<Unit> = Result.success(Unit)
    var toggleHoldResult: Result<Unit> = Result.success(Unit)

    override suspend fun answerCall(): Result<Unit> {
        _actions += Action.AnswerCall
        return answerCallResult
    }

    override suspend fun hangupCall(): Result<Unit> {
        _actions += Action.HangupCall
        return hangupCallResult
    }

    override suspend fun toggleMute(): Result<Unit> {
        _actions += Action.ToggleMute
        return toggleMuteResult
    }

    override suspend fun toggleHold(): Result<Unit> {
        _actions += Action.ToggleHold
        return toggleHoldResult
    }

    override suspend fun setMute(callId: String, muted: Boolean) {
        _actions += Action.SetMute(callId, muted)
        val state = _callState.value
        if (state is CallState.Active && state.callId == callId) {
            _callState.value = state.copy(isMuted = muted)
        }
    }

    override suspend fun setHold(callId: String, onHold: Boolean) {
        _actions += Action.SetHold(callId, onHold)
        val state = _callState.value
        if (state is CallState.Active && state.callId == callId) {
            _callState.value = state.copy(isOnHold = onHold)
        }
    }

    override suspend fun sendDtmf(callId: String, digits: String): Result<Unit> {
        _actions += Action.SendDtmf(callId, digits)
        return sendDtmfResult
    }

    override suspend fun transferCall(callId: String, destination: String): Result<Unit> {
        _actions += Action.TransferCall(callId, destination)
        return transferCallResult
    }

    fun emit(state: CallState) {
        _callState.value = state
    }

    suspend fun playScenario(steps: List<ScenarioStep>) {
        for (step in steps) {
            _callState.value = step.state
            if (step.holdFor.isPositive()) {
                delay(step.holdFor)
            }
        }
    }

}

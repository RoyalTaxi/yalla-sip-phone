package uz.yalla.sipphone.testing.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.data.pjsip.PjsipRegistrationState
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.domain.SipError

/**
 * A scriptable registration state emitter for real-world operator simulation tests.
 *
 * Records every action dispatched by production code, and exposes
 * [emit] for driving registration state from the test side.
 *
 * This does NOT implement any production interface — it is purely a test
 * harness for driving [PjsipRegistrationState] transitions in scenario tests.
 */
class ScriptableRegistrationEngine(
    initialState: PjsipRegistrationState = PjsipRegistrationState.Idle,
    var registerResult: Result<Unit> = Result.success(Unit),
) {

    // region State
    private val _registrationState = MutableStateFlow(initialState)
    val registrationState = _registrationState.asStateFlow()
    // endregion

    // region Action recording
    sealed interface Action {
        data class Register(val credentials: SipCredentials) : Action
        data object Unregister : Action
    }

    private val _actions = mutableListOf<Action>()
    val actions: List<Action> get() = _actions.toList()

    fun clearActions() {
        _actions.clear()
    }
    // endregion

    // region Simulation methods
    suspend fun register(credentials: SipCredentials): Result<Unit> {
        _actions += Action.Register(credentials)
        if (registerResult.isSuccess) {
            _registrationState.value = PjsipRegistrationState.Registering
        }
        return registerResult
    }

    suspend fun unregister() {
        _actions += Action.Unregister
        _registrationState.value = PjsipRegistrationState.Idle
    }
    // endregion

    // region Test control
    /**
     * Directly set the registration state.
     */
    fun emit(state: PjsipRegistrationState) {
        _registrationState.value = state
    }

    /** Convenience: transition to [PjsipRegistrationState.Registered]. */
    fun emitRegistered(uri: String = "sip:102@192.168.0.22") {
        emit(PjsipRegistrationState.Registered(uri))
    }

    /** Convenience: transition to [PjsipRegistrationState.Failed]. */
    fun emitFailed(code: Int = 403, reason: String = "Forbidden") {
        emit(PjsipRegistrationState.Failed(SipError.fromSipStatus(code, reason)))
    }

    /** Convenience: transition to [PjsipRegistrationState.Idle] (disconnected). */
    fun emitDisconnected() {
        emit(PjsipRegistrationState.Idle)
    }
    // endregion
}

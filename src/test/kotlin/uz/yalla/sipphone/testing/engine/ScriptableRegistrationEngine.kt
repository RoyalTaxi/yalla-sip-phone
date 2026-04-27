package uz.yalla.sipphone.testing.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.data.pjsip.account.PjsipRegistrationState
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.domain.sip.SipError

class ScriptableRegistrationEngine(
    initialState: PjsipRegistrationState = PjsipRegistrationState.Idle,
    var registerResult: Result<Unit> = Result.success(Unit),
) {

    private val _registrationState = MutableStateFlow(initialState)
    val registrationState = _registrationState.asStateFlow()

    sealed interface Action {
        data class Register(val credentials: SipCredentials) : Action
        data object Unregister : Action
    }

    private val _actions = mutableListOf<Action>()
    val actions: List<Action> get() = _actions.toList()

    fun clearActions() {
        _actions.clear()
    }

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

    fun emit(state: PjsipRegistrationState) {
        _registrationState.value = state
    }

    fun emitRegistered(uri: String = "sip:102@192.168.0.22") {
        emit(PjsipRegistrationState.Registered(uri))
    }

    fun emitFailed(code: Int = 403, reason: String = "Forbidden") {
        emit(PjsipRegistrationState.Failed(SipError.fromSipStatus(code, reason)))
    }

    fun emitDisconnected() {
        emit(PjsipRegistrationState.Idle)
    }

}

package uz.yalla.sipphone.feature.dialer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.RegistrationState
import uz.yalla.sipphone.domain.RegistrationEngine

class DialerComponent(
    componentContext: ComponentContext,
    private val registrationEngine: RegistrationEngine,
    private val onDisconnected: () -> Unit,
) : ComponentContext by componentContext {

    val registrationState: StateFlow<RegistrationState> = registrationEngine.registrationState

    private val scope = coroutineScope()

    init {
        // Navigate back once on disconnect - .first {} fires once
        scope.launch {
            registrationEngine.registrationState
                .drop(1) // skip current value (Registered)
                .first { it is RegistrationState.Idle || it is RegistrationState.Failed }
            onDisconnected()
        }
    }

    fun disconnect() {
        scope.launch { registrationEngine.unregister() }
    }
}

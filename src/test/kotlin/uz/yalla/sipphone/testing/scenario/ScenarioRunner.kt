package uz.yalla.sipphone.testing.scenario

import kotlinx.coroutines.delay
import uz.yalla.sipphone.data.pjsip.account.PjsipRegistrationState
import uz.yalla.sipphone.domain.sip.SipError
import uz.yalla.sipphone.testing.engine.ScriptableCallEngine
import uz.yalla.sipphone.testing.engine.ScriptableRegistrationEngine
import kotlin.time.Duration

class ScenarioRunner(
    val callEngine: ScriptableCallEngine,
    val registrationEngine: ScriptableRegistrationEngine,
) {

    inner class ScenarioContext {

        suspend fun register(server: String = "sip:102@192.168.0.22") {
            registrationEngine.emit(PjsipRegistrationState.Registering)
            delay(50)
            registrationEngine.emitRegistered(uri = server)
        }

        suspend fun registerFailed(code: Int = 403, reason: String = "Forbidden") {
            registrationEngine.emit(PjsipRegistrationState.Registering)
            delay(50)
            registrationEngine.emitFailed(code, reason)
        }

        suspend fun disconnect(reason: String = "Network timeout") {
            registrationEngine.emit(
                PjsipRegistrationState.Failed(
                    SipError.NetworkError(Exception(reason))
                )
            )
            delay(50)
            registrationEngine.emitDisconnected()
        }

        suspend fun pause(duration: Duration) {
            delay(duration)
        }

        suspend fun incomingCall(block: CallScenarioBuilder.() -> Unit) {
            val steps = callScenario(block)
            callEngine.playScenario(steps)
        }

        suspend fun outboundCall(number: String, block: CallScenarioBuilder.() -> Unit) {
            val builder = CallScenarioBuilder()
            builder.block()
            val steps = builder.build().mapIndexed { index, step ->
                if (index == 0 && step.state is uz.yalla.sipphone.domain.call.CallState.Ringing) {
                    val ringing = step.state
                    step.copy(
                        state = ringing.copy(
                            callerNumber = number,
                            isOutbound = true,
                        )
                    )
                } else {
                    step
                }
            }
            callEngine.playScenario(steps)
        }
    }

    suspend fun run(block: suspend ScenarioContext.() -> Unit) {
        ScenarioContext().block()
    }
}

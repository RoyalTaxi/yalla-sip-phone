package uz.yalla.sipphone.feature.workstation.sideeffect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.call.CallState

class CallSideEffects(
    private val ringtone: RingtonePlayer = RingtonePlayer(),
) {
    fun start(scope: CoroutineScope, callState: Flow<CallState>) {
        scope.launch {
            callState.collect { state ->
                when {
                    state is CallState.Ringing && !state.isOutbound -> ringtone.play()
                    else -> ringtone.stop()
                }
            }
        }
    }

    fun release() {
        ringtone.release()
    }
}

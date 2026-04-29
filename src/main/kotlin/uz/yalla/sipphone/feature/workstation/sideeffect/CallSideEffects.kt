package uz.yalla.sipphone.feature.workstation.sideeffect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.call.CallState

class CallSideEffects(
    private val ringtone: RingtonePlayer = RingtonePlayer(),
) {
    fun start(scope: CoroutineScope, callState: Flow<CallState>) {
        scope.launch {
            // Only react when the ring phase changes — not on every Active mute/hold toggle
            // (those re-emit a new CallState.Active instance and would otherwise spam
            // `ringtone.stop()` calls into the audio clip layer).
            callState
                .map { it is CallState.Ringing && !it.isOutbound }
                .distinctUntilChanged()
                .collect { isIncomingRinging ->
                    if (isIncomingRinging) ringtone.play() else ringtone.stop()
                }
        }
    }

    fun release() {
        ringtone.release()
    }
}

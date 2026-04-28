package uz.yalla.sipphone.domain.call

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
fun callDurationFlow(callState: Flow<CallState>): Flow<String?> =
    callState.transformLatest { state ->
        if (state is CallState.Active) {
            var seconds = 0L
            while (coroutineContext.isActive) {
                emit(formatDuration(seconds))
                delay(TICK_MS)
                seconds++
            }
        } else {
            emit(null)
        }
    }

private const val TICK_MS = 1_000L

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}

package uz.yalla.sipphone.domain.call

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import uz.yalla.sipphone.util.formatDuration
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
fun callDurationFlow(callState: Flow<CallState>): Flow<String?> =
    callState.transformLatest { state ->
        if (state is CallState.Active) {
            var seconds = 0L
            while (coroutineContext.isActive) {
                emit(formatDuration(seconds))
                delay(1_000)
                seconds++
            }
        } else {
            emit(null)
        }
    }

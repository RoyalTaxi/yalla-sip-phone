package uz.yalla.sipphone.core.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class SessionExpiredSignal {
    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)

    val events: Flow<Unit> = channel.receiveAsFlow()

    fun signal() {
        channel.trySend(Unit)
    }
}

package uz.yalla.sipphone.core.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fires when the backend reports the access token is no longer valid (HTTP 401 from the
 * Ktor Auth `refreshTokens` lambda). [RootComponent] consumes this and routes back to
 * the auth screen via [LogoutUseCase].
 *
 * Channel(CONFLATED) behavior: only the latest unconsumed signal is buffered. If multiple
 * 401s fire while one logout cycle is in flight, only one extra signal is queued — no
 * stampede of redundant logouts.
 *
 * Even so, consumers MUST guard the reaction (e.g. `if (sessionStore.session.value != null)`)
 * because the second buffered signal arrives after logout has already cleared the session.
 *
 * Single-consumer is intentional: only RootComponent reacts to session expiry. If we ever
 * need multiple subscribers we'll switch to SharedFlow with replay=1, but that complicates
 * the "consume-once" semantics (a fresh subscriber would re-trigger logout on a stale
 * cache).
 */
class SessionExpiredSignal {
    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)

    val events: Flow<Unit> = channel.receiveAsFlow()

    fun signal() {
        channel.trySend(Unit)
    }
}

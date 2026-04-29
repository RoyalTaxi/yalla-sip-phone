package uz.yalla.sipphone.domain.auth.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.core.result.failure
import uz.yalla.sipphone.core.result.flatMapSuccess
import uz.yalla.sipphone.core.result.mapFailure
import uz.yalla.sipphone.core.result.success
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.repository.AuthRepository
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sipAccountManager: SipAccountManager,
    private val sessionStore: SessionStore,
    private val sessionPreferences: SessionPreferences,
) {
    /**
     * Login flow:
     *  1. POST /auth/login → token. AuthRepositoryImpl writes the token into prefs so that
     *     the immediate /me call has a bearer header attached by the Ktor Auth plugin.
     *  2. Fetch profile. The token is now persisted; if anything fails after this point,
     *     we MUST clear prefs ourselves — otherwise a stale token survives into the next
     *     app session and confuses the auth plugin.
     *  3. Register SIP accounts and wait for the first connection.
     *  4. On success, populate SessionStore (single source of truth for in-memory session).
     */
    suspend operator fun invoke(pin: String): Either<AuthError, Session> =
        authRepository.login(pin)
            .mapFailure { it.toAuthError() }
            .flatMapSuccess { session ->
                if (session.profile.sipAccounts.isEmpty()) {
                    sessionPreferences.clear()
                    Either.Failure(AuthError.NoSipAccountsConfigured)
                } else {
                    registerAndAwait(session)
                }
            }

    private suspend fun registerAndAwait(session: Session): Either<AuthError, Session> {
        sipAccountManager.registerAll(session.profile.sipAccounts)
        val connected = withTimeoutOrNull(SIP_CONNECT_TIMEOUT_MS) {
            sipAccountManager.accounts.first { accounts ->
                accounts.any { it.state is SipAccountState.Connected }
            }
        }
        return if (connected != null) {
            sessionStore.set(session)
            Either.Success(session)
        } else {
            sipAccountManager.unregisterAll()
            sessionPreferences.clear()
            Either.Failure(AuthError.SipRegistrationTimeout(SIP_CONNECT_TIMEOUT_MS))
        }
    }

    private fun DataError.Network.toAuthError(): AuthError = when (this) {
        is DataError.Network.Unauthorized -> AuthError.WrongCredentials(message)
        is DataError.Network.Server ->
            if (code in 400..403) AuthError.WrongCredentials(message.ifBlank { "Login rejected" })
            else AuthError.Network(this)
        else -> AuthError.Network(this)
    }

    private companion object {
        const val SIP_CONNECT_TIMEOUT_MS = 15_000L
    }
}

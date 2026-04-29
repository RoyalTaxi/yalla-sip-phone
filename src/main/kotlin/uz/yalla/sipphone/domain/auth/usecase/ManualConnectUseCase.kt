package uz.yalla.sipphone.domain.auth.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.core.result.flatMapSuccess
import uz.yalla.sipphone.core.result.mapFailure
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.repository.AuthRepository
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

class ManualConnectUseCase(
    private val sipAccountManager: SipAccountManager,
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val configPreferences: ConfigPreferences,
    private val sessionPreferences: SessionPreferences,
) {
    suspend operator fun invoke(
        accounts: List<SipAccountInfo>,
        dispatcherUrl: String = "",
        backendUrl: String = "",
        pin: String = "",
    ): Either<AuthError, Session> {
        if (accounts.isEmpty()) return Either.Failure(AuthError.NoSipAccountsConfigured)

        persistOverrides(dispatcherUrl, backendUrl)

        return resolveSession(accounts, pin).flatMapSuccess { session ->
            registerAndAwait(accounts, session)
        }
    }

    private fun persistOverrides(dispatcherUrl: String, backendUrl: String) {
        if (dispatcherUrl.isNotBlank()) configPreferences.setDispatcherUrl(dispatcherUrl)
        if (backendUrl.isNotBlank()) configPreferences.setBackendUrl(backendUrl)
    }

    private suspend fun resolveSession(
        accounts: List<SipAccountInfo>,
        pin: String,
    ): Either<AuthError, Session> = if (pin.isBlank()) {
        Either.Success(syntheticSession(accounts))
    } else {
        authRepository.login(pin).mapFailure { it.toAuthError() }
    }

    private fun syntheticSession(accounts: List<SipAccountInfo>): Session = Session(
        token = "",
        profile = Profile(
            id = MANUAL_PROFILE_ID,
            fullName = accounts.first().credentials.username,
            sipAccounts = accounts,
            panelUrl = null,
        ),
    )

    private suspend fun registerAndAwait(
        accounts: List<SipAccountInfo>,
        session: Session,
    ): Either<AuthError, Session> {
        sipAccountManager.registerAll(accounts)
        val connected = withTimeoutOrNull(SIP_CONNECT_TIMEOUT_MS) {
            sipAccountManager.accounts.first { items ->
                items.any { it.state is SipAccountState.Connected }
            }
        }
        return if (connected != null) {
            sessionStore.set(session)
            Either.Success(session)
        } else {
            sipAccountManager.unregisterAll()
            // Mirror LoginUseCase: clear any token AuthRepositoryImpl persisted during the
            // /login HTTP roundtrip, so a stale token doesn't survive a SIP-connect timeout.
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
        const val MANUAL_PROFILE_ID = "manual"
    }
}

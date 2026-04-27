package uz.yalla.sipphone.domain.auth.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.core.result.failure
import uz.yalla.sipphone.core.result.success
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

class ManualConnectUseCase(
    private val sipAccountManager: SipAccountManager,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(accounts: List<SipAccountInfo>): Either<AuthError, Session> {
        if (accounts.isEmpty()) return Either.Failure(AuthError.NoSipAccountsConfigured)

        val syntheticSession = Session(
            token = "",
            profile = Profile(
                id = MANUAL_PROFILE_ID,
                fullName = accounts.first().credentials.username,
                sipAccounts = accounts,
                panelUrl = null,
            ),
        )

        sipAccountManager.registerAll(accounts)
        val connected = withTimeoutOrNull(SIP_CONNECT_TIMEOUT_MS) {
            sipAccountManager.accounts.first { items ->
                items.any { it.state is SipAccountState.Connected }
            }
        }
        return if (connected != null) {
            sessionStore.set(syntheticSession)
            Either.Success(syntheticSession)
        } else {
            sipAccountManager.unregisterAll()
            Either.Failure(AuthError.SipRegistrationTimeout(SIP_CONNECT_TIMEOUT_MS))
        }
    }

    private companion object {
        const val SIP_CONNECT_TIMEOUT_MS = 15_000L
        const val MANUAL_PROFILE_ID = "manual"
    }
}

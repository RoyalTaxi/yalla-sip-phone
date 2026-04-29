package uz.yalla.sipphone.domain.auth.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.repository.AuthRepository
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.testing.FakeSipAccountManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Seam tests for [LoginUseCase], focused on the contract:
 *  - Prefs MUST be cleared whenever login fails after [AuthRepositoryImpl] persisted the
 *    token. Otherwise a stale token survives into the next app session and the Ktor Auth
 *    plugin attaches it to the next request.
 *  - SessionStore MUST only be populated on full success — a half-completed login (HTTP
 *    succeeded, SIP didn't connect) MUST NOT leave UI-visible state thinking we're logged
 *    in.
 */
class LoginUseCaseTest {

    private val account = SipAccountInfo(
        extensionNumber = 103,
        serverUrl = "sip.example",
        sipName = "S",
        credentials = SipCredentials(server = "sip.example", port = 5060, username = "103", password = "x"),
    )
    private val profile = Profile(id = "1", fullName = "T", sipAccounts = listOf(account), panelUrl = null)
    private val session = Session(token = "jwt-token", profile = profile)

    private val sessionStore = SessionStore()
    private val sessionPrefs = InMemorySessionPreferences().apply {
        // Simulate the state AuthRepositoryImpl.login leaves prefs in: token persisted
        // before the use case got a chance to validate the rest of the flow.
        setAccessToken("jwt-token")
    }
    private val sipManager = FakeSipAccountManager()

    private fun useCase(
        repo: AuthRepository = StaticAuthRepository(Either.Success(session)),
    ) = LoginUseCase(repo, sipManager, sessionStore, sessionPrefs)

    @Test
    fun `success populates SessionStore and leaves prefs token in place`() = runTest {
        val result = useCase().invoke("1234")
        assertIs<Either.Success<Session>>(result)
        assertEquals(session, sessionStore.session.value)
        assertEquals("jwt-token", sessionPrefs.accessToken.value)
    }

    @Test
    fun `NoSipAccountsConfigured clears prefs token`() = runTest {
        val empty = session.copy(profile = profile.copy(sipAccounts = emptyList()))
        val repo = StaticAuthRepository(Either.Success(empty))
        val result = useCase(repo).invoke("1234")

        assertIs<Either.Failure<AuthError>>(result)
        assertEquals(AuthError.NoSipAccountsConfigured, result.error)
        assertNull(sessionStore.session.value)
        assertNull(
            sessionPrefs.accessToken.value,
            "stale token must not survive a NoSipAccountsConfigured failure",
        )
    }

    @Test
    fun `SipRegistrationTimeout clears prefs token and unregisters SIP`() = runTest {
        // SIP registers successfully but never advances past Disconnected — the use case's
        // 15s `accounts.first { Connected }` await fires its timeout.
        sipManager.autoConnectOnRegister = false

        val result = useCase().invoke("1234")

        assertIs<Either.Failure<AuthError>>(result)
        assertIs<AuthError.SipRegistrationTimeout>(result.error)
        assertNull(sessionStore.session.value)
        assertNull(
            sessionPrefs.accessToken.value,
            "stale token must not survive a SipRegistrationTimeout failure",
        )
        assertEquals(1, sipManager.unregisterAllCallCount, "SIP must be unregistered after timeout")
    }

    @Test
    fun `network failure does not clear prefs (token was never persisted)`() = runTest {
        // If AuthRepository fails BEFORE persisting the token, prefs should be untouched.
        // Reset prefs to empty to simulate that pre-login state.
        sessionPrefs.clear()
        val repo = StaticAuthRepository(
            Either.Failure(DataError.Network.Server(500, "boom")),
        )

        val result = useCase(repo).invoke("1234")

        assertIs<Either.Failure<AuthError>>(result)
        assertNull(sessionStore.session.value)
        assertNull(sessionPrefs.accessToken.value)
    }

    @Test
    fun `wrong credentials maps 401 to WrongCredentials`() = runTest {
        sessionPrefs.clear()
        val repo = StaticAuthRepository(
            Either.Failure(DataError.Network.Unauthorized("nope")),
        )
        val result = useCase(repo).invoke("1234")

        assertIs<Either.Failure<AuthError>>(result)
        val error = result.error
        assertIs<AuthError.WrongCredentials>(error)
        assertEquals("nope", error.message)
    }

    private class StaticAuthRepository(
        private val loginResult: Either<DataError.Network, Session>,
    ) : AuthRepository {
        override suspend fun login(pin: String): Either<DataError.Network, Session> = loginResult
        override suspend fun me(): Either<DataError.Network, Profile> = Either.Failure(DataError.Network.Unknown)
        override suspend fun logout(): Either<DataError.Network, Unit> = Either.Success(Unit)
    }

    private class InMemorySessionPreferences : SessionPreferences {
        private val state = MutableStateFlow<String?>(null)
        override val accessToken: StateFlow<String?> = state.asStateFlow()
        override fun setAccessToken(token: String?) { state.value = token }
        override fun clear() { state.value = null }
    }
}

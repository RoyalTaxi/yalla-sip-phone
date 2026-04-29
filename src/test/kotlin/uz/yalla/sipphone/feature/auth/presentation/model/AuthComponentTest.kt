package uz.yalla.sipphone.feature.auth.presentation.model

import app.cash.turbine.test
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.ConfigPreferencesValues
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.repository.AuthRepository
import uz.yalla.sipphone.domain.auth.usecase.LoginUseCase
import uz.yalla.sipphone.domain.auth.usecase.ManualConnectUseCase
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipAccountState
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthEffect
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent
import uz.yalla.sipphone.testing.FakeSipAccountManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthComponentTest {

    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var sessionStore: SessionStore
    private lateinit var sipManager: FakeSipAccountManager

    private val sampleAccount = SipAccountInfo(
        extensionNumber = 103,
        serverUrl = "sip.example",
        sipName = "S",
        credentials = SipCredentials(server = "sip.example", port = 5060, username = "103", password = "x"),
    )
    private val sampleProfile = Profile(
        id = "1",
        fullName = "Tester",
        sipAccounts = listOf(sampleAccount),
        panelUrl = null,
    )
    private val sampleSession = Session(token = "jwt", profile = sampleProfile)

    @BeforeTest
    fun setUp() {
        lifecycle = LifecycleRegistry()
        sessionStore = SessionStore()
        sipManager = FakeSipAccountManager()
    }

    @AfterTest
    fun tearDown() {
        lifecycle.destroy()
    }

    private fun component(
        loginRepo: AuthRepository = StaticAuthRepository(Either.Success(sampleSession)),
    ): AuthComponent = AuthComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        loginUseCase = LoginUseCase(loginRepo, sipManager, sessionStore),
        manualConnectUseCase = ManualConnectUseCase(
            sipAccountManager = sipManager,
            sessionStore = sessionStore,
            authRepository = loginRepo,
            configPreferences = NoopConfigPreferences,
        ),
    ).also { lifecycle.resume() }

    @Test
    fun `initial state is INITIAL`() = runTest {
        val s = component().container.stateFlow.first()
        assertEquals("", s.pin)
        assertNull(s.error)
    }

    @Test
    fun `SetPin updates state without auto-submitting`() = runTest {
        val c = component()
        c.onIntent(AuthIntent.SetPin("1234")).join()
        assertEquals("1234", c.container.stateFlow.first().pin)
        assertNull(c.container.stateFlow.first().error)
    }

    @Test
    fun `Submit with blank pin is a no-op`() = runTest {
        val c = component()
        c.container.sideEffectFlow.test {
            c.onIntent(AuthIntent.Submit).join()
            expectNoEvents()
        }
    }

    @Test
    fun `Submit with non-blank pin posts LoggedIn on success`() = runTest {
        val c = component()
        c.container.sideEffectFlow.test {
            c.onIntent(AuthIntent.SetPin("1234"))
            c.onIntent(AuthIntent.Submit)
            val effect = awaitItem()
            assertIs<AuthEffect.LoggedIn>(effect)
            assertEquals(sampleSession, effect.session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Submit reduces error into state on network failure`() = runTest {
        val failing = StaticAuthRepository(Either.Failure(DataError.Network.Server(401, "wrong")))
        val c = component(loginRepo = failing)
        c.container.stateFlow.test {
            assertEquals("", awaitItem().pin)
            c.onIntent(AuthIntent.SetPin("1234"))
            val withPin = awaitItem()
            assertEquals("1234", withPin.pin)
            c.onIntent(AuthIntent.Submit)
            val errored = awaitItem()
            assertIs<AuthError>(errored.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pin longer than 4 chars is preserved (no silent truncation)`() = runTest {
        val c = component()
        c.onIntent(AuthIntent.SetPin("1234567890")).join()
        assertEquals("1234567890", c.container.stateFlow.first().pin)
    }
}

private object NoopConfigPreferences : ConfigPreferences {
    private val state = MutableStateFlow(
        ConfigPreferencesValues(backendUrl = "", dispatcherUrl = "", updateChannel = "stable", installId = "test"),
    )
    override val values: StateFlow<ConfigPreferencesValues> = state.asStateFlow()
    override fun current(): ConfigPreferencesValues = state.value
    override fun setBackendUrl(url: String) {}
    override fun setDispatcherUrl(url: String) {}
    override fun setUpdateChannel(channel: String) {}
}

private class StaticAuthRepository(
    private val loginResult: Either<DataError.Network, Session>,
) : AuthRepository {
    override suspend fun login(pin: String): Either<DataError.Network, Session> = loginResult
    override suspend fun me(): Either<DataError.Network, Profile> = Either.Failure(DataError.Network.Unknown)
    override suspend fun logout(): Either<DataError.Network, Unit> = Either.Success(Unit)
}

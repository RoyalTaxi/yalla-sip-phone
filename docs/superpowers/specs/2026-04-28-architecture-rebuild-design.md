# yalla-sip-phone Architecture Rebuild — Design

**Date:** 2026-04-28
**Status:** Design — pending implementation
**Reference:** `~/StudioProjects/YallaClient` (Islom's KMP ride-hailing client) is the canonical pattern for this project.

## 1. Problem

The current codebase is "AI-written" rot. Three classes of issues:

1. **Architectural incoherence** — top-level mixes layer-based (`data/`, `domain/`, `feature/`, `ui/`) with feature-based sub-splitting (`feature/main/toolbar/widget/`). Two contradictory rules; no consistency.
2. **Custom reinvention of library features** — `safeRequest` reimplements `safeApiCall`-style wrappers badly, `InMemoryTokenProvider`+`@Volatile`+`AuthEventBus.SessionExpired` reimplements Ktor's `Auth { bearer }` plugin, `LogoutOrchestrator` is two calls dressed as a class, custom `CoroutineExceptionHandlers` exists because every component has the same try/catch boilerplate.
3. **Wrappers-on-wrappers** — `LoginResult` wraps `AuthOutcome` wraps `AuthErrorType` wraps `Result<AuthResult>`. Three sealed translations to mean "login failed". String-matched 401 detection (`"401" in msg.lowercase()`).

A previous "restructure" pass moved files into folders without addressing the rot. This spec rebuilds the architecture against the YallaClient template.

## 2. Goal

Make yalla-sip-phone match YallaClient's quality and conventions. Same idioms, same file shapes, same naming, same library choices — modulo platform differences (Compose Desktop + Decompose vs KMP + Compose Multiplatform).

## 3. Top-level package layout

Single Gradle module (decided), packages mirror YallaClient's three top-level slices:

```
src/main/kotlin/uz/yalla/sipphone/
├── app/                       Main.kt, navigation root, app DI composition
├── core/                      cross-feature infrastructure
│   ├── auth/                  SessionStore (in-memory active session)
│   ├── error/                 DataError sealed hierarchy
│   ├── infra/                 BaseComponent (Decompose + Orbit ContainerHost)
│   ├── network/               Ktor client config, safeApiCall, ApiResponse envelope
│   ├── prefs/                 SessionPreferences, UserPreferences, ConfigPreferences
│   ├── result/                Either<L, R>, onSuccess, onFailure, mapSuccess, etc.
│   ├── system/                DispatchersProvider, BuildVersion
│   └── ui/                    theme, design tokens, i18n, shared composables
├── sip/                       PJSIP infrastructure (peer to data/domain/feature; treated as hardware)
│   ├── account/               PjsipAccount, PjsipAccountManager
│   ├── call/                  PjsipCall, CallStateMachine
│   ├── engine/                PjsipEngine + lifecycle
│   ├── swig/                  SwigResources, SafeCallback, native loader
│   ├── domain/                CallEngine, SipAccountManager, CallState, SipAccount, SipError (interfaces + value types)
│   └── di/                    SipModule
├── data/                      remote services, mappers, repository impls — three vertical slices
│   ├── auth/
│   ├── update/
│   └── workstation/           bridge emitters, agent status holder
├── domain/                    pure: domain models + repository interfaces
│   ├── auth/
│   ├── update/
│   └── workstation/
└── feature/                   UI: presentation + DI per feature
    ├── auth/                  login screen
    ├── workstation/           main screen — toolbar (agent + sip + call) + JCEF panel
    └── update/                update dialogs + diagnostics
```

**Rules:**

- `feature/X` may depend on `core/*`, `domain/*` (any feature). Never on `data/*` directly. Never on another `feature/Y`.
- `data/X` may depend on `core/*`, `domain/X` (its own feature only). May not depend on `feature/*` or another `data/Y`.
- `domain/X` may depend on `core/result`, `core/error`. Nothing else.
- `sip/` may depend on `core/*`. Not on `data/`, `domain/`, `feature/`.
- `app/` is the only place that wires everything; imports across all layers permitted.

## 4. Library additions

Add to `build.gradle.kts`:
- `org.orbit-mvi:orbit-core` — MVI container
- `org.orbit-mvi:orbit-compose` — `collectSideEffect` extension for Compose

No Arrow. The `Either` API is custom and lives in `core/result/`.

## 5. `core/` package

### 5.1 `core/result/`

Custom `Either<L, R>` matching YallaClient's API:

```kotlin
package uz.yalla.sipphone.core.result

sealed interface Either<out L, out R> {
    data class Success<R>(val value: R) : Either<Nothing, R>
    data class Failure<L>(val error: L) : Either<L, Nothing>
}

inline fun <L, R> Either<L, R>.onSuccess(block: (R) -> Unit): Either<L, R> = also {
    if (this is Either.Success) block(value)
}
inline fun <L, R> Either<L, R>.onFailure(block: (L) -> Unit): Either<L, R> = also {
    if (this is Either.Failure) block(error)
}
inline fun <L, R, T> Either<L, R>.mapSuccess(block: (R) -> T): Either<L, T> = when (this) {
    is Either.Success -> Either.Success(block(value))
    is Either.Failure -> this
}
inline fun <L, R, T> Either<L, R>.flatMapSuccess(block: (R) -> Either<L, T>): Either<L, T> = when (this) {
    is Either.Success -> block(value)
    is Either.Failure -> this
}
inline fun <L, R, M> Either<L, R>.mapFailure(block: (L) -> M): Either<M, R> = when (this) {
    is Either.Success -> this
    is Either.Failure -> Either.Failure(block(error))
}
fun <R> success(value: R): Either<Nothing, R> = Either.Success(value)
fun <L> failure(error: L): Either<L, Nothing> = Either.Failure(error)
```

### 5.2 `core/error/DataError.kt`

```kotlin
package uz.yalla.sipphone.core.error

sealed class DataError {
    sealed class Network : DataError() {
        data class Server(val code: Int, val message: String) : Network()
        data class Unauthorized(val message: String) : Network()
        data class Connectivity(val cause: Throwable?) : Network()
        data class Parse(val cause: Throwable?) : Network()
        data object Unknown : Network()
    }
}
```

Each feature may define its own error type composed with `DataError.Network` (e.g., `AuthError` adds `WrongCredentials`, `NoSipAccountsConfigured`).

### 5.3 `core/network/`

```kotlin
core/network/
├── ApiResponse.kt         envelope DTO matching backend shape
├── HttpClientFactory.kt   single Ktor client builder
└── SafeApiCall.kt         safeApiCall { ... } : Either<DataError.Network, T>
```

`HttpClientFactory.createHttpClient(baseUrlProvider, tokenStore)` configures once:
- `DefaultRequest { url(baseUrlProvider()) }`
- `ContentNegotiation { json(...) }`
- `Auth { bearer { loadTokens / refreshTokens } }` — plugin handles token attach + 401
- `HttpRequestRetry { ... }` for transient failures
- `Logging { level = LogLevel.INFO }`

`safeApiCall` shape:

```kotlin
suspend inline fun <T> safeApiCall(crossinline call: suspend () -> T): Either<DataError.Network, T> =
    try { success(call()) }
    catch (e: ClientRequestException) { failure(mapClientError(e)) }
    catch (e: ServerResponseException) { failure(DataError.Network.Server(e.response.status.value, e.message ?: "")) }
    catch (e: SerializationException) { failure(DataError.Network.Parse(e)) }
    catch (e: IOException) { failure(DataError.Network.Connectivity(e)) }
    catch (e: Throwable) { failure(DataError.Network.Unknown) }
```

### 5.4 `core/prefs/`

```kotlin
core/prefs/
├── SessionPreferences.kt      access token + login state (interface + impl on multiplatform-settings)
├── UserPreferences.kt         dark theme, locale (interface + impl)
└── ConfigPreferences.kt       backend URL, dispatcher URL, update channel (interface + impl)
```

Three interfaces, three impls. Replaces the current `AppSettings` + `MultiplatformUserPreferencesRepository` duplicate.

`SessionPreferences` exposes `accessToken: Flow<String?>`, `setAccessToken(String?)`, `clear()`. The Ktor `Auth { bearer { loadTokens } }` block reads from this.

### 5.5 `core/system/`

```kotlin
core/system/
├── DispatchersProvider.kt     interface + StandardDispatchersProvider
└── BuildVersion.kt            const val VERSION
```

### 5.6 `core/ui/`

```kotlin
core/ui/
├── theme/                     YallaColors, AppTokens, Theme, LocalYallaColors, LocalAppTokens
├── strings/                   StringResources interface, UzStrings, RuStrings, LocalStrings
└── component/                 PasswordTextField, YallaIconButton, YallaTooltip, etc.
```

The current `ui/strings/Strings.kt` (English-only `object Strings`) is deleted. Window title and fatal init messages move into `StringResources`.

### 5.7 `core/auth/`

```kotlin
core/auth/
└── SessionStore.kt
```

`SessionStore` is the in-memory holder of the active `Session` (post-login). Components observe it; `LoginUseCase` writes; `LogoutUseCase` clears.

```kotlin
class SessionStore {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun set(session: Session) { _session.value = session }
    fun clear() { _session.value = null }
}
```

Workstation reads `sessionStore.session.value?.profile` for agent name and SIP accounts. The auth token lives separately in `SessionPreferences` (so Ktor `Auth { bearer }` can load it on each request without depending on the in-memory store).

### 5.8 `core/infra/`

```kotlin
core/infra/
└── BaseComponent.kt
```

`BaseComponent` bridges Decompose's `ComponentContext` with Orbit's `ContainerHost`:

```kotlin
abstract class BaseComponent<S : Any, E : Any>(
    componentContext: ComponentContext,
    initialState: S,
) : ComponentContext by componentContext, ContainerHost<S, E> {
    protected val scope: CoroutineScope = coroutineScope()
    override val container: Container<S, E> = scope.container(initialState)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    protected suspend fun <T> withLoading(block: suspend () -> T): T {
        _loading.value = true
        try { return block() } finally { _loading.value = false }
    }
}
```

This is the analogue of YallaClient's `BaseViewModel` for our Decompose context.

## 6. `sip/` package

PJSIP infrastructure stays largely as-is in shape but moves out of `data/`. Threading rules from `rules/pjsip-threading.md` continue to apply. Cleanup pass:

- Plain `Boolean` instead of `AtomicBoolean` for state that lives behind `pjDispatcher` (single-threaded). The only atomics remaining are at boundaries that race with the JVM shutdown hook (e.g., `PjsipEngine.destroyed`).
- `domain/sip/SipConstants.kt` split: protocol constants stay in `sip/` (URI scheme, transport), policy/timeout values move to the feature that owns the policy (mostly `feature/workstation/`).

## 7. `data/<feature>/` template

Mirrors YallaClient exactly:

```
data/<feature>/
├── di/                        <Feature>DataModule.kt — Koin module list
├── mapper/                    internal object FooMapper { fun map(remote: FooResponse?): FooDomain }
├── remote/
│   ├── model/                 embedded data classes (e.g., SipConnectionRemote)
│   ├── request/               FooRequest @Serializable data classes
│   ├── response/              FooResponse @Serializable data classes
│   └── service/               FooService — Ktor calls via safeApiCall
└── repository/                FooRepositoryImpl : domain.FooRepository
```

### 7.1 Service rules

```kotlin
class AuthService(
    private val client: HttpClient,
) {
    suspend fun login(body: LoginRequest): Either<DataError.Network, ApiResponse<LoginResponse>> =
        safeApiCall { client.post(LOGIN) { setBody(body) }.body() }

    suspend fun me(): Either<DataError.Network, ApiResponse<ProfileResponse>> =
        safeApiCall { client.get(ME).body() }

    suspend fun logout(): Either<DataError.Network, Unit> =
        safeApiCall { client.post(LOGOUT) }

    private companion object {
        const val LOGIN = "auth/login"
        const val ME = "auth/me"
        const val LOGOUT = "auth/logout"
    }
}
```

- Endpoints as `private companion object const val`.
- All methods return `Either<DataError.Network, T>`.
- No URL string concatenation, no method-as-data wrapping (`HttpMethod.Post`), no `setBody` outside the natural call.

### 7.2 Mapper rules

```kotlin
internal object ProfileMapper {
    fun map(remote: ProfileResponse?): Profile = Profile(
        id = remote?.id.or0(),
        fullName = remote?.fullName.orEmpty(),
        sipAccounts = remote?.sips.orEmpty().map(SipConnectionMapper::map),
        panelUrl = remote?.panelPath?.takeIf { it.isNotBlank() },
    )
}
```

- Always `internal object`, never extension functions, never classes implementing `ApiMapper<E,D>`.
- `or0()` / `orFalse()` / `orEmpty()` extensions for null-safe primitives.
- One mapper file per remote type.

### 7.3 Repository rules

```kotlin
class AuthRepositoryImpl(
    private val service: AuthService,
    private val sessionPrefs: SessionPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {
    override suspend fun login(pin: String): Either<DataError.Network, Profile> =
        withContext(ioDispatcher) {
            service.login(LoginRequest(pinCode = pin))
                .onSuccess { sessionPrefs.setAccessToken(it.result?.token) }
                .mapSuccess { ProfileMapper.map(it.result?.profile) }
        }
}
```

- `withContext(ioDispatcher)` at the boundary.
- Side effects in `onSuccess` blocks on the Either chain.
- `mapSuccess` for DTO → domain conversion via `Mapper.map(...)`.
- No try/catch — `safeApiCall` already wrapped errors as `Either.Failure`.

### 7.4 DI module rules

```kotlin
object AuthDataModule {
    private val serviceModule = module {
        single { AuthService(client = get()) }
    }
    private val repositoryModule = module {
        single<AuthRepository> { AuthRepositoryImpl(service = get(), sessionPrefs = get(), ioDispatcher = get(named("io"))) }
    }
    val modules = listOf(serviceModule, repositoryModule)
}
```

## 8. `domain/<feature>/` template

```
domain/<feature>/
├── model/                     pure data classes — domain types
└── repository/                interfaces returning Either<DataError.Network, DomainModel>
```

- Models named after the domain noun: `Profile`, `Session`, `SipAccount` — not `AuthResult`, not `MeDto`, not `XxxResult` unless the domain is literally about the result of something.
- Repositories return `Either<DataError.Network, T>`. Always.
- No use cases here. (See §10 on use cases.)

## 9. `feature/<feature>/` template

```
feature/<feature>/
├── di/                        FooModule.kt — Koin module list
└── presentation/
    ├── intent/                FooState.kt, FooIntent.kt, FooEffect.kt
    ├── model/                 FooComponent.kt + FooComponent+Intent.kt + FooComponent+Network.kt + ...
    └── view/                  FooRoute.kt, FooScreen.kt, sub-screens in subdirectories
```

### 9.1 `intent/` rules

```kotlin
// FooState.kt
data class FooState(
    val a: Int,
    val b: String,
) {
    companion object {
        val INITIAL = FooState(a = 0, b = "")
    }
    fun isReady() = a > 0 && b.isNotBlank()
}

// FooIntent.kt
sealed interface FooIntent {
    data object DoX : FooIntent
    data class SetB(val value: String) : FooIntent
}

// FooEffect.kt
sealed interface FooEffect {
    data object NavigateBack : FooEffect
    data class ShowError(val message: String) : FooEffect
}
```

- `State` always has `INITIAL` companion. Helper methods on the state when natural.
- `Intent` covers all user-driven actions.
- `Effect` covers one-shot side effects (navigation, snackbar, haptic).

### 9.2 `model/` rules — Component split into partial extension files

```kotlin
// FooComponent.kt — thin: only constructor + container declaration
class FooComponent(
    componentContext: ComponentContext,
    internal val authRepository: AuthRepository,
    internal val sessionPrefs: SessionPreferences,
) : BaseComponent<FooState, FooEffect>(componentContext, FooState.INITIAL) {
    internal var timerJob: Job? = null
}

// FooComponent+Intent.kt — onIntent dispatcher
fun FooComponent.onIntent(intent: FooIntent) = intent {
    when (intent) {
        FooIntent.DoX -> doX()
        is FooIntent.SetB -> reduce { state.copy(b = intent.value) }
    }
}

// FooComponent+Network.kt — repo calls
internal fun FooComponent.doX() = intent {
    safeScope.launchWithLoading {
        authRepository.somecall().onSuccess { ... }.onFailure { ... }
    }
}
```

- Component is **thin**; no logic in the class body beyond the container.
- All ViewModel-like dependencies are `internal val` so the extension files (in same package) can reach them.
- Each file is one concern: `+Intent`, `+Network`, `+Timer`, `+State`, etc.

### 9.3 `view/` rules — Route + Screen split

```kotlin
// FooRoute.kt — effect collector + navigation
sealed interface FromFoo {
    data object ToBack : FromFoo
    data class ToSomething(val arg: String) : FromFoo
}

@Composable
fun FooRoute(
    component: FooComponent,
    navigateTo: (FromFoo) -> Unit,
) {
    val state by component.container.stateFlow.collectAsState()
    val loading by component.loading.collectAsState()

    component.collectSideEffect { effect ->
        when (effect) {
            FooEffect.NavigateBack -> navigateTo(FromFoo.ToBack)
            is FooEffect.ShowError -> ...
        }
    }

    FooScreen(
        state = state,
        loading = loading,
        onIntent = component::onIntent,
    )
}

// FooScreen.kt — pure stateless
@Composable
fun FooScreen(
    state: FooState,
    loading: Boolean,
    onIntent: (FooIntent) -> Unit,
) {
    // pure render based on state, dispatch via onIntent
}
```

- `Route` knows about Decompose, Orbit, Koin. `Screen` knows nothing — pure UI.
- Screen takes `state`, `loading`, `onIntent` — three things. No more.
- Sub-screens (sheets, dialogs) live in `view/<subname>/` subdirectories.

### 9.4 DI module rules

```kotlin
object AuthModule {
    private val presentationModule = module {
        factory { (componentContext: ComponentContext, onLoginSuccess: (Session) -> Unit) ->
            AuthComponent(
                componentContext = componentContext,
                onLoginSuccess = onLoginSuccess,
                authRepository = get(),
                sessionPrefs = get(),
            )
        }
    }
    val modules = listOf(presentationModule)
}
```

The component is parameterized so the navigation root can pass `ComponentContext` and the `onLoginSuccess` callback at creation time.

## 10. Use cases — when they exist, when they don't

Use cases exist **only when they orchestrate**. Pass-through use cases are deleted.

**Kept:**
- `LoginUseCase` — orchestrates `authRepo.login(pin) → sipManager.registerAll(profile.accounts) → awaitConnected() → sessionStore.set(session)`. Real orchestration.
- `ManualConnectUseCase` — manual SIP entry path: `sipManager.registerAll(manualAccounts) → awaitConnected() → sessionStore.set(syntheticSession)`. Distinct enough from PIN login to deserve its own use case (no backend call, synthesized session, optional PIN-validate on top).
- `LogoutUseCase` — calls `sipManager.unregisterAll() + authRepo.logout() + sessionStore.clear()`. Three side effects coordinated.

**Deleted (pass-throughs):**
- `AnswerCallUseCase`, `HangupCallUseCase`, `MakeCallUseCase`, `ToggleHoldUseCase`, `ToggleMuteUseCase`, `SetAgentStatusUseCase`, `DisconnectAllAccountsUseCase`, `ToggleAccountConnectionUseCase`. All single-line delegates. The component calls the engine/manager directly via the domain interface.
- `AuthenticateUseCase`, `RegisterAndAwaitConnectedUseCase` — folded into `LoginUseCase`.
- Old `ManualLoginUseCase` — replaced by `ManualConnectUseCase` with cleaner shape.

Use cases live in `domain/<feature>/usecase/` (one file each) when kept.

## 11. Auth feature — concrete file layout (the template)

```
data/auth/
├── di/AuthDataModule.kt
├── mapper/
│   ├── ProfileMapper.kt
│   └── SipConnectionMapper.kt
├── remote/
│   ├── request/LoginRequest.kt
│   ├── response/
│   │   ├── LoginResponse.kt
│   │   └── ProfileResponse.kt
│   ├── model/SipConnectionRemote.kt
│   └── service/AuthService.kt
└── repository/AuthRepositoryImpl.kt

domain/auth/
├── model/
│   ├── Profile.kt        (id, fullName, sipAccounts, panelUrl, agent)
│   ├── Session.kt        (token, profile) — replaces "AuthResult"
│   └── AuthError.kt      (sealed: WrongCredentials, NoSipAccountsConfigured, Wrapped(DataError.Network))
├── repository/AuthRepository.kt
└── usecase/
    ├── LoginUseCase.kt
    ├── ManualConnectUseCase.kt
    └── LogoutUseCase.kt

feature/auth/
├── di/AuthModule.kt
└── presentation/
    ├── intent/
    │   ├── AuthState.kt
    │   ├── AuthIntent.kt
    │   ├── AuthEffect.kt
    │   └── ManualAccountEntry.kt    (UI input model — server, port, username, password)
    ├── model/
    │   ├── AuthComponent.kt
    │   ├── AuthComponent+Intent.kt
    │   └── AuthComponent+Network.kt
    └── view/
        ├── AuthRoute.kt        (was: LoginScreen.kt, partly)
        ├── AuthScreen.kt       (was: LoginCard.kt)
        └── manual/ManualConnectionSheet.kt    (was: ManualConnectionDialog.kt — now a sheet)
```

### 11.1 Auth model

```kotlin
// domain/auth/model/Profile.kt
data class Profile(
    val id: String,
    val fullName: String,
    val sipAccounts: List<SipAccountInfo>,
    val panelUrl: String?,
)

// domain/auth/model/Session.kt
data class Session(
    val token: String,
    val profile: Profile,
)

// domain/auth/model/AuthError.kt
sealed interface AuthError {
    data class WrongCredentials(val message: String) : AuthError
    data object NoSipAccountsConfigured : AuthError
    data class Network(val cause: DataError.Network) : AuthError
}
```

### 11.2 Auth repository (interface + impl)

```kotlin
// domain/auth/repository/AuthRepository.kt
interface AuthRepository {
    suspend fun login(pin: String): Either<DataError.Network, Session>
    suspend fun me(): Either<DataError.Network, Profile>
    suspend fun logout(): Either<DataError.Network, Unit>
}
```

```kotlin
// data/auth/repository/AuthRepositoryImpl.kt
class AuthRepositoryImpl(
    private val service: AuthService,
    private val sessionPrefs: SessionPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {
    override suspend fun login(pin: String): Either<DataError.Network, Session> =
        withContext(ioDispatcher) {
            service.login(LoginRequest(pinCode = pin))
                .onSuccess { sessionPrefs.setAccessToken(it.result?.token.orEmpty()) }
                .flatMapSuccess { loginResp ->
                    service.me().mapSuccess { meResp ->
                        Session(
                            token = loginResp.result?.token.orEmpty(),
                            profile = ProfileMapper.map(meResp.result),
                        )
                    }
                }
        }

    override suspend fun me(): Either<DataError.Network, Profile> =
        withContext(ioDispatcher) {
            service.me().mapSuccess { ProfileMapper.map(it.result) }
        }

    override suspend fun logout(): Either<DataError.Network, Unit> =
        withContext(ioDispatcher) {
            service.logout().onSuccess { sessionPrefs.clear() }
        }
}
```

### 11.3 LoginUseCase

```kotlin
// domain/auth/usecase/LoginUseCase.kt
class LoginUseCase(
    private val authRepo: AuthRepository,
    private val sipManager: SipAccountManager,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(pin: String): Either<AuthError, Session> =
        authRepo.login(pin)
            .mapFailure { AuthError.Network(it) }
            .flatMapSuccess { session ->
                if (session.profile.sipAccounts.isEmpty()) {
                    failure(AuthError.NoSipAccountsConfigured)
                } else {
                    registerAndAwait(session)
                }
            }

    private suspend fun registerAndAwait(session: Session): Either<AuthError, Session> {
        sipManager.registerAll(session.profile.sipAccounts)
        val connected = withTimeoutOrNull(SIP_CONNECT_TIMEOUT_MS) {
            sipManager.accounts.first { accounts ->
                accounts.any { it.state is SipAccountState.Connected }
            }
        }
        return if (connected != null) {
            sessionStore.set(session)
            success(session)
        } else {
            sipManager.unregisterAll()
            failure(AuthError.Network(DataError.Network.Connectivity(cause = null)))
        }
    }

    private companion object {
        const val SIP_CONNECT_TIMEOUT_MS = 15_000L
    }
}
```

### 11.4 Auth MVI — state, intent, effect

```kotlin
// presentation/intent/AuthState.kt
data class AuthState(
    val pin: String,
    val showManualSheet: Boolean,
    val error: AuthError?,
) {
    companion object {
        const val PIN_LENGTH = 4
        val INITIAL = AuthState(pin = "", showManualSheet = false, error = null)
    }
    fun pinReady() = pin.length == PIN_LENGTH
}

// presentation/intent/AuthIntent.kt
sealed interface AuthIntent {
    data class SetPin(val value: String) : AuthIntent
    data object Submit : AuthIntent
    data object OpenManual : AuthIntent
    data object DismissManual : AuthIntent
    data class ManualConnect(val accounts: List<ManualAccountEntry>, val pin: String) : AuthIntent
}

// presentation/intent/AuthEffect.kt
sealed interface AuthEffect {
    data class LoggedIn(val session: Session) : AuthEffect
    data class Toast(val message: String) : AuthEffect
}
```

### 11.5 AuthComponent + extensions

The component does **not** take an `onLoggedIn` callback. Navigation flows through `AuthEffect.LoggedIn` → `AuthRoute.collectSideEffect` → `navigateTo(FromAuth.ToWorkstation)`. The Route is the only place navigation lives.

```kotlin
// presentation/model/AuthComponent.kt
class AuthComponent(
    componentContext: ComponentContext,
    internal val loginUseCase: LoginUseCase,
    internal val manualConnectUseCase: ManualConnectUseCase,
) : BaseComponent<AuthState, AuthEffect>(componentContext, AuthState.INITIAL)

// presentation/model/AuthComponent+Intent.kt
fun AuthComponent.onIntent(intent: AuthIntent) = intent {
    when (intent) {
        is AuthIntent.SetPin -> {
            reduce { state.copy(pin = intent.value, error = null) }
            if (state.pinReady()) submit()
        }
        AuthIntent.Submit -> submit()
        AuthIntent.OpenManual -> reduce { state.copy(showManualSheet = true) }
        AuthIntent.DismissManual -> reduce { state.copy(showManualSheet = false) }
        is AuthIntent.ManualConnect -> manualConnect(intent.accounts, intent.pin)
    }
}

// presentation/model/AuthComponent+Network.kt
internal fun AuthComponent.submit() = intent {
    val pin = state.pin
    withLoading {
        loginUseCase(pin)
            .onSuccess { session -> postSideEffect(AuthEffect.LoggedIn(session)) }
            .onFailure { error -> reduce { state.copy(error = error) } }
    }
}

internal fun AuthComponent.manualConnect(accounts: List<ManualAccountEntry>, pin: String) = intent {
    withLoading {
        manualConnectUseCase(accounts.map { it.toSipAccountInfo() }, pin)
            .onSuccess { session -> postSideEffect(AuthEffect.LoggedIn(session)) }
            .onFailure { error -> reduce { state.copy(error = error, showManualSheet = false) } }
    }
}
```

### 11.6 AuthRoute + AuthScreen

```kotlin
// presentation/view/AuthRoute.kt
sealed interface FromAuth {
    data class ToWorkstation(val session: Session) : FromAuth
}

@Composable
fun AuthRoute(component: AuthComponent, navigateTo: (FromAuth) -> Unit) {
    val state by component.container.stateFlow.collectAsState()
    val loading by component.loading.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    component.collectSideEffect { effect ->
        when (effect) {
            is AuthEffect.LoggedIn -> navigateTo(FromAuth.ToWorkstation(effect.session))
            is AuthEffect.Toast -> snackbar.showSnackbar(effect.message)
        }
    }

    AuthScreen(
        state = state,
        loading = loading,
        snackbar = snackbar,
        onIntent = component::onIntent,
    )

    if (state.showManualSheet) {
        ManualConnectionSheet(
            loading = loading,
            onConnect = { accounts, pin -> component.onIntent(AuthIntent.ManualConnect(accounts, pin)) },
            onDismiss = { component.onIntent(AuthIntent.DismissManual) },
        )
    }
}

// presentation/view/AuthScreen.kt — pure stateless
@Composable
fun AuthScreen(
    state: AuthState,
    loading: Boolean,
    snackbar: SnackbarHostState,
    onIntent: (AuthIntent) -> Unit,
) { /* pure render */ }
```

## 12. Workstation feature — outline

The "main" screen is renamed `workstation`. One feature, three concerns rendered side by side: agent status, SIP chip row, call toolbar (top), JCEF dispatcher panel (rest).

```
data/workstation/
├── di/WorkstationDataModule.kt
├── bridge/                    Pjsip/SIP/Agent → JS bridge emitters (was CallEventOrchestrator)
│   ├── CallEventBridgeEmitter.kt
│   ├── SipConnectionBridgeEmitter.kt
│   └── AgentStatusBridgeEmitter.kt
├── agent/AgentStatusHolder.kt    (replaces InMemoryAgentStatusRepository)
└── jcef/                      JcefManager + JcefWebPanelBridge collapsed (single impl, no interface)

domain/workstation/
├── model/
│   ├── AgentStatus.kt
│   └── DispatcherPanelInput.kt
└── usecase/
    └── (none — pass-throughs deleted)

feature/workstation/
├── di/WorkstationModule.kt
└── presentation/
    ├── intent/
    │   ├── WorkstationState.kt
    │   ├── WorkstationIntent.kt
    │   └── WorkstationEffect.kt
    ├── model/
    │   ├── WorkstationComponent.kt
    │   ├── WorkstationComponent+Intent.kt
    │   ├── WorkstationComponent+Call.kt
    │   ├── WorkstationComponent+Sip.kt
    │   └── WorkstationComponent+Agent.kt
    └── view/
        ├── WorkstationRoute.kt
        ├── WorkstationScreen.kt
        ├── toolbar/
        │   ├── ToolbarRow.kt           (composes the widgets)
        │   ├── AgentStatusChip.kt
        │   ├── PhoneField.kt
        │   ├── CallActions.kt
        │   ├── CallTimer.kt
        │   └── SipChipRow.kt
        ├── settings/SettingsSheet.kt
        └── panel/WebviewPanel.kt
```

`CallEventOrchestrator` is split into three single-purpose emitter classes, each with its own private state, each independently testable.

`InMemoryAgentStatusRepository` collapses into a plain `AgentStatusHolder` with `MutableStateFlow<AgentStatus>` — no interface, no `data/agent/` mirror in `domain/`.

## 13. Update feature — outline

```
data/update/
├── di/UpdateDataModule.kt
├── mapper/UpdateManifestMapper.kt
├── remote/
│   ├── response/UpdateManifestResponse.kt
│   └── service/UpdateService.kt
├── repository/UpdateRepositoryImpl.kt
├── download/UpdateDownloader.kt
├── verify/Sha256Verifier.kt
├── install/{MsiBootstrapperInstaller, ProcessLauncher, RealProcessLauncher}.kt
└── storage/UpdatePaths.kt

domain/update/
├── model/{UpdateRelease, UpdateState, UpdateChannel, Semver}.kt
├── repository/UpdateRepository.kt
└── usecase/PerformUpdateCheckUseCase.kt        (orchestrates check → download → verify → install gate)

feature/update/
├── di/UpdateModule.kt
└── presentation/
    ├── intent/{UpdateState, UpdateIntent, UpdateEffect}.kt
    ├── model/UpdateComponent + extensions
    └── view/
        ├── UpdateRoute.kt
        ├── UpdateBadge.kt
        └── UpdateDialogs.kt
```

The current `UpdateManager` (the orchestrator state machine) is renamed `PerformUpdateCheckUseCase` and lives in `domain/update/usecase/`. The `UpdateApiContract`/`UpdateDownloaderContract`/`InstallerContract` testability shims are deleted — the use case takes the concrete `UpdateService`/`UpdateDownloader`/`MsiBootstrapperInstaller` directly. Tests substitute fakes via Koin, not via shim interfaces.

## 14. `app/` package

```
app/
├── Main.kt                    entry point
├── di/AppModule.kt            assembles all data + feature modules
└── navigation/
    ├── RootComponent.kt       Decompose root, owns ChildStack<Screen>
    ├── RootContent.kt         Compose root that renders the active Child
    └── Screen.kt              sealed: Login, Workstation
```

`RootComponent`:
- Owns `SessionStore` observation. When `sessionPrefs.accessToken` becomes null (logout / 401), navigate to `Screen.Login`.
- Translates `FromAuth.ToWorkstation` → `navigation.replaceAll(Screen.Workstation)`.
- Translates `FromWorkstation.Logout` → `LogoutUseCase()` + `navigation.replaceAll(Screen.Login)`.
- No `loginSessionCounter` hack. Decompose 3.x `replaceAll` recreates the component.

## 15. Migration strategy

Order of work, each step keeping the build green:

1. **Add libraries**: Orbit core + compose, write `core/result/Either.kt`, `core/error/DataError.kt`. Compile-only, no consumers yet.
2. **`core/network/` rebuild**: write new `HttpClientFactory` + `safeApiCall` + `ApiResponse`. Old `safeRequest` / `NetworkError` / `HttpClientFactory` stay temporarily.
3. **`core/prefs/` split**: write `SessionPreferences`, `UserPreferences`, `ConfigPreferences` (interfaces + impls). Old `AppSettings` + `MultiplatformUserPreferencesRepository` stay temporarily.
4. **`core/infra/BaseComponent.kt`**: write the Decompose+Orbit base.
5. **Auth feature rewrite (the template)**: write the entire new `data/auth/` + `domain/auth/` + `feature/auth/` per §11. Wire into DI alongside the old.
6. **Cut over auth**: `RootComponent` switches from old `LoginComponent` to new `AuthComponent`. Delete old `data/auth/`, `domain/auth/`, `domain/usecases/auth/`, `feature/login/`. Delete `safeRequest`, `NetworkError`, old `HttpClientFactory`, `AuthEventBus`, `LogoutOrchestrator`, `InMemoryTokenProvider`, `MultiplatformUserPreferencesRepository`.
7. **Update feature rewrite**: same pattern. Cut over and delete old.
8. **Workstation feature rewrite** (largest): same pattern. Cut over and delete old `feature/main/`.
9. **Sip cleanup**: drop AtomicBoolean paranoia, split SipConstants, collapse `WebPanelBridge` interface.
10. **`app/` cleanup**: rewrite `RootComponent` against the new components, delete `ComponentFactory`/`ComponentFactoryImpl` (Koin factories handle the parameterized creation directly).

Each step: build green + relevant tests green before merging. No mass big-bang.

## 16. Testing strategy

**Unchanged from current rules** (`rules/testing.md`): JUnit 4, kotlin.test, Turbine, ktor-client-mock, hand-written fakes (no MockK).

**Test packages mirror source** (`data/auth/repository/AuthRepositoryImplTest`, `feature/auth/presentation/model/AuthComponentTest`, etc.).

**MVI testing pattern** (Orbit-specific):
```kotlin
@Test
fun `Submit dispatches login and posts LoggedIn effect on success`() = runTest {
    val component = createAuthComponent(loginUseCase = fakeUseCaseReturning(success(session)))
    component.test(this) {
        expectInitialState()
        containerHost.onIntent(AuthIntent.SetPin("1234"))
        expectState { copy(pin = "1234") }
        expectSideEffect(AuthEffect.LoggedIn(session))
    }
}
```

Use `org.orbit-mvi:orbit-test` for the `.test { }` DSL.

**Repository tests** continue to use `MockEngine` + `safeApiCall`'s real Either translation — verify Either.Failure variants for each error class.

**Component tests** assert state transitions and effect emissions via Orbit's test harness.

## 17. Commit / PR strategy

Each migration step is one PR. Each PR includes:
- Code changes
- Tests (rewritten or new)
- One short ADR if the step introduces a new convention beyond this spec (e.g., "we settled on X for the third-party dispatch pattern because Y").

ADRs live in `docs/adr/NNNN-title.md` per global instructions.

## 18. Out of scope

- Switching from Decompose to Voyager / Compose Navigation. Decompose stays.
- Switching from Compose Desktop to Compose Multiplatform. Stays JVM Desktop only.
- Persisting tokens to OS keychain. In-memory `SessionPreferences` matches current behavior; persistence is a separate feature.
- Multi-Gradle-module split. Single module decided.
- Code signing. Out of scope per project policy.

## 19. Acceptance

The rebuild is "done" when:

1. No file imports `uz.yalla.sipphone.data.network.NetworkError`, `safeRequest`, `AuthEventBus`, `LogoutOrchestrator`, `InMemoryTokenProvider`, `AppSettings`, or `Result<T>` from kotlin stdlib in domain/repository signatures.
2. All repositories return `Either<DataError.Network, T>`.
3. All HTTP-doing classes are named `*Service` and use `safeApiCall { client.<method>(ENDPOINT) }`.
4. All mappers are `internal object` with `map` methods.
5. All components extend `BaseComponent<S, E>` and implement `ContainerHost<S, E>`.
6. All features expose `Module` lists via `object FooModule { val modules = listOf(...) }`.
7. Top-level packages match the §3 layout exactly.
8. `./gradlew test` passes (≥ current 259 test count).

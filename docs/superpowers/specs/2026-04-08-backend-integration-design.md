# Backend Integration — Design Spec

**Date:** 2026-04-08
**Status:** Approved
**Scope:** Replace MockAuthRepository with real backend API integration (login, me, logout)

---

## Overview

Connect Yalla SIP Phone to the real backend API. Operator enters PIN code, app authenticates via JWT, fetches SIP credentials from `/auth/me`, and registers on the SIP server. Currently using `MockAuthRepository` with hardcoded credentials.

## API Endpoints

Base URL: `http://192.168.0.98:8080/api/v1/` (hardcoded)

### POST /auth/login

Request:
```json
{"pin_code": "778899"}
```

Response (success):
```json
{
    "status": true,
    "code": 200,
    "message": "login successful",
    "result": {
        "token": "eyJhbGci...",
        "token_type": "Bearer ",
        "expire": 4929241537
    },
    "errors": null
}
```

Response (error):
```json
{
    "status": false,
    "code": 401,
    "message": "Error",
    "result": null,
    "errors": "employee not found"
}
```

### GET /auth/me (Bearer token required)

Response:
```json
{
    "status": true,
    "code": 200,
    "message": "success",
    "result": {
        "id": 1,
        "tm_user_id": 1,
        "full_name": "Sadullo Kimsanov",
        "roles": "super_admin",
        "created_at": "2026-04-02 16:24:28",
        "sips": [
            {
                "extension_number": 1003,
                "password": "demo",
                "is_active": true,
                "sip_name": "Andijon server SIp",
                "server_url": "http://test.uz",
                "server_port": 5060,
                "domain": "test.uz",
                "connection_type": "udp"
            }
        ]
    },
    "errors": null
}
```

401 response (invalid/expired token):
```json
{
    "status": false,
    "code": 401,
    "message": "invalid or expired token",
    "result": null,
    "errors": null
}
```

### POST /auth/logout (Bearer token required)

Response:
```json
{
    "status": true,
    "code": 200,
    "message": "logged out successfully",
    "result": null,
    "errors": null
}
```

## API Response Envelope

All endpoints return the same envelope:

```kotlin
@Serializable
data class ApiResponse<T>(
    val status: Boolean,        // true = success, false = error
    val code: Int,              // HTTP status code
    val message: String? = null,
    val result: T? = null,
    val errors: String? = null, // Error description string, NOT a list
)
```

Key: `status` is `Boolean` (not String), `errors` is `String?` (not List).

---

## Architecture

### New Files

```
data/
├── network/                          HTTP infrastructure (reusable)
│   ├── HttpClientFactory.kt         Ktor CIO client factory
│   ├── ApiResponse.kt               Generic response envelope DTO
│   ├── NetworkError.kt              Sealed error hierarchy
│   └── SafeRequest.kt               safeRequest<T> extension function
│
├── auth/
│   ├── TokenProvider.kt             In-memory JWT storage (interface + impl)
│   ├── AuthApi.kt                   Raw HTTP calls (login, me, logout)
│   ├── AuthRepositoryImpl.kt        Orchestrates login flow
│   ├── AuthEventBus.kt              401 → login redirect event bus
│   └── dto/
│       ├── LoginRequestDto.kt       Request body DTO
│       ├── LoginResultDto.kt        Login response result DTO
│       └── MeResultDto.kt           Me response result DTO + SIP DTO

di/
│   └── NetworkModule.kt             Koin module for HttpClient, TokenProvider
```

### Modified Files

```
domain/
├── AuthRepository.kt          Add logout(), rename parameter to pinCode
├── AuthResult.kt              Add token field
├── SipCredentials.kt          Add transport field

data/auth/
├── LoginResponse.kt           DELETE (replaced by DTOs)
├── MockAuthRepository.kt      Update to match new AuthRepository interface

di/
├── AuthModule.kt              Bind AuthRepositoryImpl instead of Mock
├── AppModule.kt               Add networkModule to module list

feature/login/
├── LoginComponent.kt          Update for new AuthResult (token field)

feature/main/
├── MainComponent.kt           Pass token to WebView URL

navigation/
├── RootComponent.kt           Collect AuthEventBus for 401 redirect
```

---

## Domain Changes

### AuthRepository

```kotlin
interface AuthRepository {
    suspend fun login(pinCode: String): Result<AuthResult>
    suspend fun logout(): Result<Unit>
}
```

### AuthResult

```kotlin
data class AuthResult(
    val token: String,
    val sipCredentials: SipCredentials,
    val dispatcherUrl: String,
    val agent: AgentInfo,
)
```

`dispatcherUrl` — static, hardcoded in `AuthRepositoryImpl`. Not from API.

### SipCredentials

```kotlin
data class SipCredentials(
    val server: String,
    val port: Int = SipConstants.DEFAULT_PORT,
    val username: String,
    val password: String,
    val transport: String = "UDP",
) {
    override fun toString(): String = "SipCredentials(server=$server, port=$port, " +
        "username=$username, password=***, transport=$transport)"
}
```

---

## Data Layer Design

### network/HttpClientFactory.kt

Creates a configured Ktor `HttpClient` with:
- **CIO engine** — pure Kotlin, no Android/OkHttp dependency
- **ContentNegotiation** — kotlinx.serialization JSON (`ignoreUnknownKeys = true`)
- **HttpTimeout** — 15s request, 10s connect
- **Logging** — debug level, via kotlin-logging
- **Auth (Bearer)** — automatic token attachment via `TokenProvider`
  - `loadTokens`: reads from `TokenProvider`
  - `refreshTokens`: on 401, clears token and emits `AuthEvent.SessionExpired`
  - `sendWithoutRequest`: skips auth header for `/auth/login`
- **defaultRequest** — base URL + JSON content type
- **expectSuccess = false** — we handle status codes ourselves in `safeRequest`

### network/ApiResponse.kt

Generic envelope DTO matching the backend format. See API Response Envelope section above.

### network/NetworkError.kt

```kotlin
sealed class NetworkError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthorized : NetworkError("Session expired")
    data class ClientError(val code: Int, val serverMessage: String?) : NetworkError(...)
    data class ServerError(val code: Int, val serverMessage: String?) : NetworkError(...)
    data class NoConnection(override val cause: Throwable) : NetworkError(...)
    data class ParseError(override val cause: Throwable) : NetworkError(...)
}
```

### network/SafeRequest.kt

`HttpClient.safeRequest<T>` extension function:
1. Executes the request inside try/catch
2. On 2xx: deserializes `ApiResponse<T>`, returns `Result.success(result)`
3. On 401: returns `Result.failure(NetworkError.Unauthorized)`
4. On 4xx: returns `Result.failure(NetworkError.ClientError(code, message))`
5. On 5xx: returns `Result.failure(NetworkError.ServerError(code, message))`
6. On IOException/timeout: returns `Result.failure(NetworkError.NoConnection(cause))`
7. On SerializationException: returns `Result.failure(NetworkError.ParseError(cause))`

Special case: when `status == false` in 200 response (API returns 200 with `status: false` for auth errors), treat as client error using `code` field from the envelope.

### auth/TokenProvider.kt

```kotlin
interface TokenProvider {
    suspend fun getToken(): String?
    suspend fun setToken(token: String)
    suspend fun clearToken()
}

class InMemoryTokenProvider : TokenProvider { ... }
```

In-memory only. No disk persistence. App restart = re-login.

### auth/AuthApi.kt

Thin class, 3 methods:
- `login(pinCode: String): Result<LoginResultDto>` — POST /auth/login
- `me(): Result<MeResultDto>` — GET /auth/me
- `logout(): Result<Unit>` — POST /auth/logout

Each method calls `client.safeRequest<T>`. No business logic.

### auth/AuthRepositoryImpl.kt

Orchestrates the 2-step login flow:

```
login(pinCode) {
    1. authApi.login(pinCode) → LoginResultDto (JWT token)
    2. tokenProvider.setToken(token)
    3. authApi.me() → MeResultDto (user info + sips[])
       - On failure: tokenProvider.clearToken() (rollback)
    4. Find first sip where is_active == true
       - If none found: return failure
    5. Map to AuthResult(token, sipCredentials, dispatcherUrl, agent)
}

logout() {
    1. authApi.logout()
    2. tokenProvider.clearToken()
}
```

### auth/AuthEventBus.kt

```kotlin
sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}

class AuthEventBus {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()
    fun emit(event: AuthEvent) { _events.tryEmit(event) }
}
```

Wired to `HttpClientFactory.onUnauthorized` → emits `SessionExpired`.
`RootComponent` collects events → navigates to Login screen.

### auth/dto/ — DTO classes

Match actual API responses exactly:

```kotlin
@Serializable
data class LoginRequestDto(
    @SerialName("pin_code") val pinCode: String,
)

@Serializable
data class LoginResultDto(
    val token: String,
    @SerialName("token_type") val tokenType: String,
    val expire: Long,
)

@Serializable
data class MeResultDto(
    val id: Int,
    @SerialName("tm_user_id") val tmUserId: Int,
    @SerialName("full_name") val fullName: String,
    val roles: String,
    @SerialName("created_at") val createdAt: String,
    val sips: List<SipConnectionDto>,
)

@Serializable
data class SipConnectionDto(
    @SerialName("extension_number") val extensionNumber: Int,
    val password: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("sip_name") val sipName: String,
    @SerialName("server_url") val serverUrl: String,
    @SerialName("server_port") val serverPort: Int,
    val domain: String,
    @SerialName("connection_type") val connectionType: String,
)
```

### DTO → Domain Mapping

Extension function in `AuthRepositoryImpl` or a separate mapper file:

```kotlin
fun MeResultDto.toAuthResult(token: String, dispatcherUrl: String): AuthResult {
    val sip = sips.first { it.isActive }
    return AuthResult(
        token = token,
        sipCredentials = SipCredentials(
            server = sip.domain,        // "test.uz" — domain field, not server_url
            port = sip.serverPort,
            username = sip.extensionNumber.toString(),
            password = sip.password,
            transport = sip.connectionType.uppercase(),
        ),
        dispatcherUrl = dispatcherUrl,
        agent = AgentInfo(id = id.toString(), name = fullName),
    )
}
```

SIP registration uses `domain` field (not `server_url` which includes `http://` prefix).
Username is `extension_number` (integer → string).

---

## DI Changes

### New: NetworkModule.kt

```kotlin
val networkModule = module {
    single<TokenProvider> { InMemoryTokenProvider() }
    single { AuthEventBus() }
    single {
        createHttpClient(
            baseUrl = "http://192.168.0.98:8080/api/v1/",
            tokenProvider = get(),
            onUnauthorized = { get<AuthEventBus>().emit(AuthEvent.SessionExpired) },
        )
    }
}
```

### Updated: AuthModule.kt

```kotlin
val authModule = module {
    single { AuthApi(client = get()) }
    single<AuthRepository> { AuthRepositoryImpl(authApi = get(), tokenProvider = get()) }
    // MockAuthRepository stays in test/ for unit tests
}
```

### Updated: AppModule.kt

Add `networkModule` to the list (before `authModule`).

---

## Feature Layer Changes

### LoginComponent

Minimal change — `login(password)` already calls `authRepository.login(password)`. Just rename parameter semantically. The flow stays the same: auth → register SIP → navigate.

### MainComponent / WebviewPanel

WebView URL changes from static dispatcher URL to: `DISPATCHER_URL?token=<jwt_token>`

Token comes from `AuthResult.token` which is passed through navigation.

### RootComponent

Add `AuthEventBus` collection:

```kotlin
init {
    scope.launch {
        authEventBus.events.collect { event ->
            when (event) {
                AuthEvent.SessionExpired -> {
                    // Clean up SIP, navigate to login
                    navigation.replaceAll(Screen.Login)
                }
            }
        }
    }
}
```

---

## Login Screen

No UI changes. Current PIN/password field stays as-is. `ManualConnectionDialog` stays for debugging.

Only semantic change: label could say "PIN code" instead of "Password" (optional, UI rethink task).

---

## Dispatcher URL

Static, hardcoded as a constant. Not from API.

```kotlin
object ApiConfig {
    const val BASE_URL = "http://192.168.0.98:8080/api/v1/"
    const val DISPATCHER_URL = "http://192.168.0.234:5173"  // or current value
}
```

---

## Dependencies (build.gradle.kts)

```kotlin
val ktorVersion = "3.1.2"
implementation("io.ktor:ktor-client-core:$ktorVersion")
implementation("io.ktor:ktor-client-cio:$ktorVersion")
implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
implementation("io.ktor:ktor-client-auth:$ktorVersion")
implementation("io.ktor:ktor-client-logging:$ktorVersion")

testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
```

---

## Error Handling

| Scenario | What happens |
|----------|-------------|
| Wrong PIN | API returns `{status: false, code: 401, errors: "employee not found"}` → LoginScreen shows error |
| Network down | `IOException` → `NetworkError.NoConnection` → LoginScreen shows "No connection" |
| Token expired mid-session | Any API returns 401 → `AuthEventBus.SessionExpired` → redirect to Login |
| No active SIP in /me | `sips.none { it.isActive }` → `Result.failure` → LoginScreen shows "No SIP connection" |
| API server down | Timeout → `NetworkError.NoConnection` → LoginScreen shows error |
| SIP registration fails | Existing flow handles this via `RegistrationState.Failed` |

---

## Testing Strategy

| Component | Test approach |
|-----------|--------------|
| `AuthApi` | `ktor-client-mock` — simulate all response codes |
| `AuthRepositoryImpl` | Inject mock `AuthApi` + `InMemoryTokenProvider` |
| `safeRequest` | Test each status code path with mock engine |
| `TokenProvider` | `InMemoryTokenProvider` is directly testable |
| `LoginComponent` | Existing test structure + `MockAuthRepository` for UI tests |
| `NetworkError` mapping | Unit tests for each HTTP status → NetworkError subtype |

`MockAuthRepository` moves to test source set. Production DI uses `AuthRepositoryImpl`.

---

## Out of Scope

These are known issues found during code review. They should be addressed in separate tasks:

- Multi-SIP connection (separate task)
- UI redesign (separate task)
- PhoneNumberValidator `+` prefix bug
- Simulator JS in production builds
- `AgentStatus.colorHex` Clean Architecture violation
- `CallState` missing failure state
- `SipConstants` splitting
- `BridgeRouter` DI registration

# Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace MockAuthRepository with real backend API (PIN login, JWT, /auth/me, logout) using Ktor client.

**Architecture:** New `data/network/` package for reusable HTTP infrastructure (client factory, error handling, safe request wrapper). New `data/auth/` DTOs and repository implementation orchestrating login→me→SIP register flow. LogoutOrchestrator as single authority for session teardown. AuthEventBus for 401→login redirect.

**Tech Stack:** Ktor 3.1.2 (CIO engine), kotlinx.serialization, Koin DI, Decompose navigation

**Spec:** `docs/superpowers/specs/2026-04-08-backend-integration-design.md`

---

## File Map

### New Files
| File | Responsibility |
|------|---------------|
| `data/network/NetworkError.kt` | Sealed error hierarchy for HTTP/network failures |
| `data/network/ApiResponse.kt` | Generic API envelope DTO + error message helper |
| `data/network/SafeRequest.kt` | `safeRequest<T>` inline reified extension for unified HTTP error handling |
| `data/network/HttpClientFactory.kt` | Ktor CIO client with auth, timeout, JSON, logging |
| `data/auth/TokenProvider.kt` | In-memory JWT storage interface + implementation |
| `data/auth/AuthEventBus.kt` | Session expiry event bus |
| `data/auth/dto/LoginRequestDto.kt` | `{pin_code}` request body |
| `data/auth/dto/LoginResultDto.kt` | Login response result DTO |
| `data/auth/dto/MeResultDto.kt` | Me response result + SipConnectionDto |
| `data/auth/AuthApi.kt` | Raw HTTP calls (login, me, logout) |
| `data/auth/AuthRepositoryImpl.kt` | Login flow orchestration + DTO→Domain mapping |
| `data/auth/LogoutOrchestrator.kt` | Full logout sequence: SIP, connection, API, token |
| `di/NetworkModule.kt` | Koin module for HTTP client, token, event bus |

### Modified Files
| File | Change |
|------|--------|
| `build.gradle.kts` | Add Ktor dependencies |
| `domain/AuthResult.kt` | Add `token: String` field |
| `domain/SipCredentials.kt` | Add `transport: String = "UDP"` |
| `domain/AuthRepository.kt` | Add `logout()`, rename param |
| `data/auth/MockAuthRepository.kt` | Update for new interface, remove LoginResponse dependency |
| `data/auth/LoginResponse.kt` | DELETE |
| `feature/login/LoginComponent.kt` | manualConnect: `token = ""` |
| `feature/main/MainComponent.kt` | Append `?token=` to dispatcher URL |
| `data/jcef/BridgeRouter.kt` | Add `requestLogout` command |
| `navigation/RootComponent.kt` | Add scope, AuthEventBus, LogoutOrchestrator |
| `Main.kt` | Pass new deps to RootComponent |
| `di/AuthModule.kt` | Bind real implementations |
| `di/AppModule.kt` | Add networkModule |

---

### Task 1: Add Ktor Dependencies

**Files:**
- Modify: `build.gradle.kts:16-50`

- [ ] **Step 1: Add Ktor dependencies to build.gradle.kts**

Add after the `// Settings persistence` block (after line 43):

```kotlin
    // HTTP client
    val ktorVersion = "3.1.2"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
```

Add in the test block (after existing `testImplementation` entries):

```kotlin
    testImplementation("io.ktor:ktor-client-mock:3.1.2")
```

- [ ] **Step 2: Verify Gradle sync**

Run: `./gradlew dependencies --configuration runtimeClasspath | grep ktor`
Expected: 6 ktor dependencies resolved

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add Ktor 3.1.2 HTTP client dependencies"
```

---

### Task 2: Domain Model Changes

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/AuthResult.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/SipCredentials.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/domain/AuthRepository.kt`

- [ ] **Step 1: Add token field to AuthResult**

Replace entire `AuthResult.kt`:

```kotlin
package uz.yalla.sipphone.domain

data class AuthResult(
    val token: String,
    val sipCredentials: SipCredentials,
    val dispatcherUrl: String,
    val agent: AgentInfo,
)
```

- [ ] **Step 2: Add transport field to SipCredentials**

Replace entire `SipCredentials.kt`:

```kotlin
package uz.yalla.sipphone.domain

data class SipCredentials(
    val server: String,
    val port: Int = SipConstants.DEFAULT_PORT,
    val username: String,
    val password: String,
    val transport: String = "UDP",
) {
    override fun toString(): String =
        "SipCredentials(server=$server, port=$port, username=$username, password=***, transport=$transport)"
}
```

- [ ] **Step 3: Add logout() to AuthRepository**

Replace entire `AuthRepository.kt`:

```kotlin
package uz.yalla.sipphone.domain

/**
 * Authenticates the agent against the backend and retrieves SIP credentials.
 */
interface AuthRepository {
    /**
     * Authenticates with [pinCode] and returns a populated [AuthResult] on success.
     *
     * @return [Result.failure] on network errors or invalid credentials.
     */
    suspend fun login(pinCode: String): Result<AuthResult>

    /**
     * Logs out the current session. Best-effort — network errors are acceptable.
     */
    suspend fun logout(): Result<Unit>
}
```

- [ ] **Step 4: Verify compilation fails (expected — MockAuthRepository doesn't implement logout yet)**

Run: `./gradlew compileKotlin 2>&1 | head -20`
Expected: Compilation errors in MockAuthRepository, LoginComponent, RootComponentTest

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/domain/AuthResult.kt \
        src/main/kotlin/uz/yalla/sipphone/domain/SipCredentials.kt \
        src/main/kotlin/uz/yalla/sipphone/domain/AuthRepository.kt
git commit -m "feat(domain): add token to AuthResult, transport to SipCredentials, logout to AuthRepository"
```

---

### Task 3: Fix Compilation — MockAuthRepository + Callers

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/auth/MockAuthRepository.kt`
- Delete: `src/main/kotlin/uz/yalla/sipphone/data/auth/LoginResponse.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/login/LoginComponent.kt:80-91`
- Modify: `src/test/kotlin/uz/yalla/sipphone/navigation/RootComponentTest.kt:35-39,60-62`

- [ ] **Step 1: Update MockAuthRepository (remove LoginResponse dependency, add logout, add token)**

Replace entire `MockAuthRepository.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import kotlinx.coroutines.delay
import uz.yalla.sipphone.domain.AgentInfo
import uz.yalla.sipphone.domain.AuthRepository
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.domain.SipCredentials

class MockAuthRepository : AuthRepository {
    override suspend fun login(pinCode: String): Result<AuthResult> {
        delay(1000)
        return if (pinCode == "test123") {
            Result.success(
                AuthResult(
                    token = "mock-jwt-token",
                    sipCredentials = SipCredentials(
                        server = "192.168.30.103",
                        port = 5060,
                        username = "103",
                        password = "callers103",
                        transport = "UDP",
                    ),
                    dispatcherUrl = "http://192.168.60.84:5173",
                    agent = AgentInfo("agent-042", "Islom"),
                )
            )
        } else {
            Result.failure(IllegalArgumentException("Invalid PIN"))
        }
    }

    override suspend fun logout(): Result<Unit> {
        delay(200)
        return Result.success(Unit)
    }
}
```

- [ ] **Step 2: Delete LoginResponse.kt**

```bash
rm src/main/kotlin/uz/yalla/sipphone/data/auth/LoginResponse.kt
```

- [ ] **Step 3: Fix LoginComponent.manualConnect — add token**

In `LoginComponent.kt`, replace lines 80-91 (`manualConnect` function):

```kotlin
    fun manualConnect(server: String, port: Int, username: String, password: String, dispatcherUrl: String = "") {
        val credentials = SipCredentials(server = server, port = port, username = username, password = password)
        lastAuthResult = AuthResult(
            token = "",
            sipCredentials = credentials,
            dispatcherUrl = dispatcherUrl,
            agent = AgentInfo("manual", username),
        )
        _loginState.value = LoginState.Loading
        scope.launch(ioDispatcher) {
            registrationEngine.register(credentials)
        }
    }
```

- [ ] **Step 4: Fix RootComponentTest — add token to AuthResult, add logout to anonymous AuthRepository**

In `RootComponentTest.kt`, replace lines 35-39:

```kotlin
    private val testAuthResult = AuthResult(
        token = "test-token",
        sipCredentials = SipCredentials("192.168.0.22", 5060, "102", "pass"),
        dispatcherUrl = "http://dispatcher.test",
        agent = AgentInfo("1", "Test Agent"),
    )
```

Replace lines 60-62 (anonymous AuthRepository):

```kotlin
                authRepository = object : AuthRepository {
                    override suspend fun login(pinCode: String): Result<AuthResult> =
                        Result.success(testAuthResult)
                    override suspend fun logout(): Result<Unit> = Result.success(Unit)
                },
```

- [ ] **Step 5: Verify compilation passes**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run tests**

Run: `./gradlew test`
Expected: All tests pass (MockAuthRepositoryTest will need minor updates — see step 7)

- [ ] **Step 7: Fix MockAuthRepositoryTest if needed**

The test asserts specific credential values. Update assertions to match the new MockAuthRepository output. Add a test for `logout()`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(auth): update MockAuthRepository for new domain interface, delete LoginResponse"
```

---

### Task 4: Network Layer — NetworkError + ApiResponse

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/network/NetworkError.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/network/ApiResponse.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/network/ApiResponseTest.kt`

- [ ] **Step 1: Write tests for ApiResponse.errorMessage()**

Create `src/test/kotlin/uz/yalla/sipphone/data/network/ApiResponseTest.kt`:

```kotlin
package uz.yalla.sipphone.data.network

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiResponseTest {

    @Test
    fun `errorMessage returns errors string when errors is a string primitive`() {
        val response = ApiResponse<Unit>(
            status = false, code = 401, message = "Error",
            errors = JsonPrimitive("employee not found"),
        )
        assertEquals("employee not found", response.errorMessage())
    }

    @Test
    fun `errorMessage returns joined fields when errors is an object`() {
        val response = ApiResponse<Unit>(
            status = false, code = 422, message = "Validation failed",
            errors = buildJsonObject { put("pin_code", "required") },
        )
        assertEquals("pin_code: required", response.errorMessage())
    }

    @Test
    fun `errorMessage returns message when errors is null`() {
        val response = ApiResponse<Unit>(
            status = false, code = 500, message = "Internal error",
            errors = null,
        )
        assertEquals("Internal error", response.errorMessage())
    }

    @Test
    fun `errorMessage returns fallback when both errors and message are null`() {
        val response = ApiResponse<Unit>(status = false, code = 500)
        assertEquals("Unknown error", response.errorMessage())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*.ApiResponseTest" 2>&1 | tail -5`
Expected: FAIL — classes don't exist yet

- [ ] **Step 3: Create NetworkError.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/network/NetworkError.kt`:

```kotlin
package uz.yalla.sipphone.data.network

sealed class NetworkError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    data object Unauthorized : NetworkError("Session expired")

    data class ClientError(
        val code: Int,
        val serverMessage: String?,
    ) : NetworkError(serverMessage ?: "Client error ($code)")

    data class ServerError(
        val code: Int,
        val serverMessage: String?,
    ) : NetworkError(serverMessage ?: "Server error ($code)")

    data class NoConnection(
        override val cause: Throwable,
    ) : NetworkError("No connection: ${cause.message}", cause)

    data class ParseError(
        override val cause: Throwable,
    ) : NetworkError("Data format error: ${cause.message}", cause)
}
```

- [ ] **Step 4: Create ApiResponse.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/network/ApiResponse.kt`:

```kotlin
package uz.yalla.sipphone.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ApiResponse<T>(
    val status: Boolean,
    val code: Int,
    val message: String? = null,
    val result: T? = null,
    val errors: JsonElement? = null,
)

fun ApiResponse<*>.errorMessage(): String {
    return when (val e = errors) {
        is JsonPrimitive -> e.contentOrNull ?: message ?: "Unknown error"
        is JsonObject -> e.entries.joinToString { "${it.key}: ${it.value.jsonPrimitive.content}" }
        else -> message ?: "Unknown error"
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "*.ApiResponseTest"`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/network/NetworkError.kt \
        src/main/kotlin/uz/yalla/sipphone/data/network/ApiResponse.kt \
        src/test/kotlin/uz/yalla/sipphone/data/network/ApiResponseTest.kt
git commit -m "feat(network): add NetworkError sealed hierarchy and ApiResponse envelope DTO"
```

---

### Task 5: Network Layer — SafeRequest + TokenProvider + AuthEventBus

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/network/SafeRequest.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/TokenProvider.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthEventBus.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/network/SafeRequestTest.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/auth/TokenProviderTest.kt`

- [ ] **Step 1: Write TokenProvider tests**

Create `src/test/kotlin/uz/yalla/sipphone/data/auth/TokenProviderTest.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenProviderTest {

    @Test
    fun `initially returns null`() = runTest {
        val provider = InMemoryTokenProvider()
        assertNull(provider.getToken())
    }

    @Test
    fun `stores and retrieves token`() = runTest {
        val provider = InMemoryTokenProvider()
        provider.setToken("jwt-123")
        assertEquals("jwt-123", provider.getToken())
    }

    @Test
    fun `clearToken removes token`() = runTest {
        val provider = InMemoryTokenProvider()
        provider.setToken("jwt-123")
        provider.clearToken()
        assertNull(provider.getToken())
    }
}
```

- [ ] **Step 2: Create TokenProvider.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/TokenProvider.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TokenProvider {
    suspend fun getToken(): String?
    suspend fun setToken(token: String)
    suspend fun clearToken()
}

class InMemoryTokenProvider : TokenProvider {
    @Volatile
    private var token: String? = null
    private val mutex = Mutex()

    override suspend fun getToken(): String? = token

    override suspend fun setToken(token: String) {
        mutex.withLock { this.token = token }
    }

    override suspend fun clearToken() {
        mutex.withLock { token = null }
    }
}
```

- [ ] **Step 3: Create AuthEventBus.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthEventBus.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}

class AuthEventBus {
    private val _events = MutableSharedFlow<AuthEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emit(event: AuthEvent) {
        _events.tryEmit(event)
    }
}
```

- [ ] **Step 4: Write SafeRequest tests**

Create `src/test/kotlin/uz/yalla/sipphone/data/network/SafeRequestTest.kt`:

```kotlin
package uz.yalla.sipphone.data.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.yalla.sipphone.data.auth.AuthEventBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafeRequestTest {

    private val authEventBus = AuthEventBus()

    private fun createClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun `success response returns result`() = runTest {
        val client = createClient { _ ->
            respond(
                content = """{"status":true,"code":200,"message":"ok","result":{"value":"hello"},"errors":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.safeRequest<TestDto>(authEventBus) {
            url("http://test.com/api")
            method = HttpMethod.Get
        }
        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrThrow().value)
    }

    @Test
    fun `HTTP 401 returns Unauthorized and emits SessionExpired`() = runTest {
        val client = createClient { _ ->
            respond(
                content = """{"status":false,"code":401,"message":"invalid token","result":null,"errors":null}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.safeRequest<TestDto>(authEventBus) {
            url("http://test.com/api")
            method = HttpMethod.Get
        }
        assertTrue(result.isFailure)
        assertIs<NetworkError.Unauthorized>(result.exceptionOrNull())
    }

    @Test
    fun `HTTP 200 with status=false returns ClientError without SessionExpired`() = runTest {
        val client = createClient { _ ->
            respond(
                content = """{"status":false,"code":401,"message":"Error","result":null,"errors":"employee not found"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.safeRequest<TestDto>(authEventBus) {
            url("http://test.com/api")
            method = HttpMethod.Get
        }
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<NetworkError.ClientError>(error)
        assertEquals("employee not found", error.serverMessage)
    }

    @Test
    fun `HTTP 500 returns ServerError`() = runTest {
        val client = createClient { _ ->
            respond(
                content = """{"status":false,"code":500,"message":"boom","result":null,"errors":null}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.safeRequest<TestDto>(authEventBus) {
            url("http://test.com/api")
            method = HttpMethod.Get
        }
        assertTrue(result.isFailure)
        assertIs<NetworkError.ServerError>(result.exceptionOrNull())
    }
}

@kotlinx.serialization.Serializable
private data class TestDto(val value: String)
```

- [ ] **Step 5: Create SafeRequest.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/network/SafeRequest.kt`:

```kotlin
package uz.yalla.sipphone.data.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import uz.yalla.sipphone.data.auth.AuthEvent
import uz.yalla.sipphone.data.auth.AuthEventBus
import java.io.IOException

/**
 * Unified HTTP request wrapper. Deserializes [ApiResponse] envelope and maps
 * errors to typed [NetworkError] subtypes.
 *
 * MUST stay `inline reified` — Ktor's generic deserialization via typeInfo<T>()
 * requires reification. If refactored to non-inline, deserialization silently breaks.
 */
suspend inline fun <reified T> HttpClient.safeRequest(
    authEventBus: AuthEventBus,
    crossinline block: HttpRequestBuilder.() -> Unit,
): Result<T> {
    return try {
        val response: HttpResponse = request { block() }
        handleResponse<T>(response, authEventBus)
    } catch (e: CancellationException) {
        throw e // Never catch — breaks structured concurrency
    } catch (e: HttpRequestTimeoutException) {
        Result.failure(NetworkError.NoConnection(e))
    } catch (e: IOException) {
        Result.failure(NetworkError.NoConnection(e))
    } catch (e: SerializationException) {
        Result.failure(NetworkError.ParseError(e))
    } catch (e: NetworkError) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(NetworkError.NoConnection(e))
    }
}

@PublishedApi
internal suspend inline fun <reified T> handleResponse(
    response: HttpResponse,
    authEventBus: AuthEventBus,
): Result<T> {
    val httpStatus = response.status.value

    return when {
        httpStatus == 401 -> {
            authEventBus.emit(AuthEvent.SessionExpired)
            Result.failure(NetworkError.Unauthorized)
        }

        httpStatus in 200..299 -> {
            try {
                val envelope: ApiResponse<T> = response.body()
                if (envelope.status && envelope.result != null) {
                    Result.success(envelope.result)
                } else if (envelope.status && envelope.result == null) {
                    // Success with no result body (e.g., logout)
                    @Suppress("UNCHECKED_CAST")
                    Result.success(Unit as T)
                } else {
                    // HTTP 200 but status=false — API-level error (e.g., wrong PIN)
                    // Do NOT trigger SessionExpired even if envelope.code == 401
                    Result.failure(
                        NetworkError.ClientError(
                            code = envelope.code,
                            serverMessage = envelope.errorMessage(),
                        )
                    )
                }
            } catch (e: SerializationException) {
                Result.failure(NetworkError.ParseError(e))
            }
        }

        httpStatus in 400..499 -> {
            val msg = runCatching { response.body<ApiResponse<Unit>>().errorMessage() }.getOrNull()
            Result.failure(NetworkError.ClientError(httpStatus, msg))
        }

        httpStatus in 500..599 -> {
            val msg = runCatching { response.body<ApiResponse<Unit>>().errorMessage() }.getOrNull()
            Result.failure(NetworkError.ServerError(httpStatus, msg))
        }

        else -> {
            Result.failure(NetworkError.ClientError(httpStatus, "Unexpected status: $httpStatus"))
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests "*.SafeRequestTest" --tests "*.TokenProviderTest"`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/network/SafeRequest.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/TokenProvider.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/AuthEventBus.kt \
        src/test/kotlin/uz/yalla/sipphone/data/network/SafeRequestTest.kt \
        src/test/kotlin/uz/yalla/sipphone/data/auth/TokenProviderTest.kt
git commit -m "feat(network): add safeRequest, TokenProvider, AuthEventBus"
```

---

### Task 6: HttpClient Factory

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/network/HttpClientFactory.kt`

- [ ] **Step 1: Create HttpClientFactory.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/network/HttpClientFactory.kt`:

```kotlin
package uz.yalla.sipphone.data.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import uz.yalla.sipphone.data.auth.TokenProvider

private val logger = KotlinLogging.logger {}

fun createHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider,
): HttpClient = HttpClient(CIO) {

    expectSuccess = false

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }

    install(Logging) {
        this.logger = object : Logger {
            override fun log(message: String) {
                uz.yalla.sipphone.data.network.logger.debug { message }
            }
        }
        level = LogLevel.HEADERS
    }

    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenProvider.getToken()?.let { BearerTokens(it, "") }
            }
            sendWithoutRequest { request ->
                request.url.encodedPath.contains("/auth/login")
            }
            // NO refreshTokens — 401 handled by safeRequest only
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/network/HttpClientFactory.kt
git commit -m "feat(network): add HttpClient factory with CIO, bearer auth, JSON"
```

---

### Task 7: Auth DTOs

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/LoginRequestDto.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/LoginResultDto.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/MeResultDto.kt`

- [ ] **Step 1: Create all three DTO files**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/LoginRequestDto.kt`:

```kotlin
package uz.yalla.sipphone.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    @SerialName("pin_code") val pinCode: String,
)
```

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/LoginResultDto.kt`:

```kotlin
package uz.yalla.sipphone.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResultDto(
    val token: String,
    @SerialName("token_type") val tokenType: String,
    val expire: Long,
)
```

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/dto/MeResultDto.kt`:

```kotlin
package uz.yalla.sipphone.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.yalla.sipphone.domain.AgentInfo
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.domain.SipCredentials

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

fun MeResultDto.toAuthResult(token: String, dispatcherUrl: String): AuthResult {
    val sip = sips.first { it.isActive }
    return AuthResult(
        token = token,
        sipCredentials = SipCredentials(
            server = sip.domain,
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

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/auth/dto/
git commit -m "feat(auth): add API DTOs — LoginRequest, LoginResult, MeResult, SipConnection"
```

---

### Task 8: AuthApi + AuthRepositoryImpl

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthApi.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImpl.kt`
- Create: `src/test/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImplTest.kt`

- [ ] **Step 1: Create AuthApi.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthApi.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import uz.yalla.sipphone.data.auth.dto.LoginRequestDto
import uz.yalla.sipphone.data.auth.dto.LoginResultDto
import uz.yalla.sipphone.data.auth.dto.MeResultDto
import uz.yalla.sipphone.data.network.safeRequest

class AuthApi(
    private val client: HttpClient,
    private val authEventBus: AuthEventBus,
) {
    suspend fun login(pinCode: String): Result<LoginResultDto> =
        client.safeRequest(authEventBus) {
            url { path("auth", "login") }
            method = HttpMethod.Post
            setBody(LoginRequestDto(pinCode = pinCode))
        }

    suspend fun me(): Result<MeResultDto> =
        client.safeRequest(authEventBus) {
            url { path("auth", "me") }
            method = HttpMethod.Get
        }

    suspend fun logout(): Result<Unit> =
        client.safeRequest(authEventBus) {
            url { path("auth", "logout") }
            method = HttpMethod.Post
        }
}
```

- [ ] **Step 2: Create AuthRepositoryImpl.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImpl.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.data.auth.dto.toAuthResult
import uz.yalla.sipphone.domain.AuthRepository
import uz.yalla.sipphone.domain.AuthResult

private val logger = KotlinLogging.logger {}

object ApiConfig {
    const val BASE_URL = "http://192.168.0.98:8080/api/v1/"
    const val DISPATCHER_URL = "http://192.168.60.84:5173"
}

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) : AuthRepository {

    override suspend fun login(pinCode: String): Result<AuthResult> {
        // Step 1: POST /auth/login → JWT token
        val loginResult = authApi.login(pinCode)
        val loginDto = loginResult.getOrElse { return Result.failure(it) }

        // Step 2: Store token
        tokenProvider.setToken(loginDto.token)
        logger.info { "Token received, fetching user info..." }

        // Step 3: GET /auth/me → user info + SIP connections
        val meResult = authApi.me()
        val meDto = meResult.getOrElse { error ->
            tokenProvider.clearToken()
            return Result.failure(error)
        }

        // Step 4: Find first active SIP connection
        val activeSip = meDto.sips.firstOrNull { it.isActive }
        if (activeSip == null) {
            tokenProvider.clearToken()
            return Result.failure(IllegalStateException("No active SIP connection available"))
        }

        // Step 5: Map to domain
        val authResult = meDto.toAuthResult(
            token = loginDto.token,
            dispatcherUrl = ApiConfig.DISPATCHER_URL,
        )

        logger.info { "Auth complete: agent=${authResult.agent.name}, sip=${authResult.sipCredentials}" }
        return Result.success(authResult)
    }

    override suspend fun logout(): Result<Unit> {
        runCatching { authApi.logout() }
        tokenProvider.clearToken()
        logger.info { "Logged out, token cleared" }
        return Result.success(Unit)
    }
}
```

- [ ] **Step 3: Write AuthRepositoryImpl tests**

Create `src/test/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImplTest.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.yalla.sipphone.data.network.NetworkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryImplTest {

    private val tokenProvider = InMemoryTokenProvider()
    private val authEventBus = AuthEventBus()

    private fun createRepo(handler: MockRequestHandler): AuthRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = AuthApi(client, authEventBus)
        return AuthRepositoryImpl(api, tokenProvider)
    }

    private val loginSuccessJson = """{"status":true,"code":200,"message":"login successful","result":{"token":"jwt-test","token_type":"Bearer ","expire":9999999999},"errors":null}"""
    private val meSuccessJson = """{"status":true,"code":200,"message":"success","result":{"id":1,"tm_user_id":1,"full_name":"Test Agent","roles":"admin","created_at":"2026-01-01","sips":[{"extension_number":103,"password":"demo","is_active":true,"sip_name":"Test SIP","server_url":"http://test.uz","server_port":5060,"domain":"test.uz","connection_type":"udp"}]},"errors":null}"""
    private val loginFailJson = """{"status":false,"code":401,"message":"Error","result":null,"errors":"employee not found"}"""

    @Test
    fun `successful login stores token and returns AuthResult`() = runTest {
        val repo = createRepo { request ->
            when {
                request.url.encodedPath.endsWith("auth/login") -> respond(loginSuccessJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("auth/me") -> respond(meSuccessJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val result = repo.login("778899")
        assertTrue(result.isSuccess)
        val auth = result.getOrThrow()
        assertEquals("jwt-test", auth.token)
        assertEquals("test.uz", auth.sipCredentials.server)
        assertEquals("103", auth.sipCredentials.username)
        assertEquals("Test Agent", auth.agent.name)
        assertEquals("jwt-test", tokenProvider.getToken())
    }

    @Test
    fun `failed login returns error and does not store token`() = runTest {
        val repo = createRepo { _ ->
            respond(loginFailJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = repo.login("wrong")
        assertTrue(result.isFailure)
        assertIs<NetworkError.ClientError>(result.exceptionOrNull())
        assertNull(tokenProvider.getToken())
    }

    @Test
    fun `login clears token if me() fails`() = runTest {
        val repo = createRepo { request ->
            when {
                request.url.encodedPath.endsWith("auth/login") -> respond(loginSuccessJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("auth/me") -> respond("", HttpStatusCode.InternalServerError)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val result = repo.login("778899")
        assertTrue(result.isFailure)
        assertNull(tokenProvider.getToken())
    }

    @Test
    fun `logout clears token`() = runTest {
        tokenProvider.setToken("jwt-test")
        val repo = createRepo { _ ->
            respond("""{"status":true,"code":200,"message":"logged out","result":null,"errors":null}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = repo.logout()
        assertTrue(result.isSuccess)
        assertNull(tokenProvider.getToken())
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "*.AuthRepositoryImplTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/auth/AuthApi.kt \
        src/main/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImpl.kt \
        src/test/kotlin/uz/yalla/sipphone/data/auth/AuthRepositoryImplTest.kt
git commit -m "feat(auth): add AuthApi and AuthRepositoryImpl with login→me flow"
```

---

### Task 9: LogoutOrchestrator + DI Wiring

**Files:**
- Create: `src/main/kotlin/uz/yalla/sipphone/data/auth/LogoutOrchestrator.kt`
- Create: `src/main/kotlin/uz/yalla/sipphone/di/NetworkModule.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/di/AuthModule.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/di/AppModule.kt`

- [ ] **Step 1: Create LogoutOrchestrator.kt**

Create `src/main/kotlin/uz/yalla/sipphone/data/auth/LogoutOrchestrator.kt`:

```kotlin
package uz.yalla.sipphone.data.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.AuthRepository
import uz.yalla.sipphone.domain.ConnectionManager
import uz.yalla.sipphone.domain.RegistrationEngine

private val logger = KotlinLogging.logger {}

class LogoutOrchestrator(
    private val authRepository: AuthRepository,
    private val registrationEngine: RegistrationEngine,
    private val connectionManager: ConnectionManager,
    private val tokenProvider: TokenProvider,
) {
    suspend fun logout() {
        logger.info { "Logout sequence starting..." }
        connectionManager.stopMonitoring()
        runCatching { registrationEngine.unregister() }
            .onFailure { logger.warn { "SIP unregister failed: ${it.message}" } }
        runCatching { authRepository.logout() }
            .onFailure { logger.warn { "API logout failed: ${it.message}" } }
        tokenProvider.clearToken()
        logger.info { "Logout sequence complete" }
    }
}
```

- [ ] **Step 2: Create NetworkModule.kt**

Create `src/main/kotlin/uz/yalla/sipphone/di/NetworkModule.kt`:

```kotlin
package uz.yalla.sipphone.di

import org.koin.dsl.module
import uz.yalla.sipphone.data.auth.AuthEventBus
import uz.yalla.sipphone.data.auth.InMemoryTokenProvider
import uz.yalla.sipphone.data.auth.TokenProvider
import uz.yalla.sipphone.data.auth.AuthRepositoryImpl
import uz.yalla.sipphone.data.network.createHttpClient

val networkModule = module {
    single<TokenProvider> { InMemoryTokenProvider() }
    single { AuthEventBus() }
    single {
        createHttpClient(
            baseUrl = AuthRepositoryImpl.ApiConfig.BASE_URL,
            tokenProvider = get(),
        )
    }
}
```

Wait — `ApiConfig` is inside `AuthRepositoryImpl.kt`. Let me adjust. Move `ApiConfig` to its own reference or inline the constant. Actually, since the spec says `ApiConfig` is a standalone object, let me put it in `AuthRepositoryImpl.kt` as a top-level object (already done in Task 8). The DI module references it.

Actually, update `NetworkModule.kt` to use the constant directly:

```kotlin
package uz.yalla.sipphone.di

import org.koin.dsl.module
import uz.yalla.sipphone.data.auth.ApiConfig
import uz.yalla.sipphone.data.auth.AuthEventBus
import uz.yalla.sipphone.data.auth.InMemoryTokenProvider
import uz.yalla.sipphone.data.auth.TokenProvider
import uz.yalla.sipphone.data.network.createHttpClient

val networkModule = module {
    single<TokenProvider> { InMemoryTokenProvider() }
    single { AuthEventBus() }
    single {
        createHttpClient(
            baseUrl = ApiConfig.BASE_URL,
            tokenProvider = get(),
        )
    }
}
```

- [ ] **Step 3: Update AuthModule.kt**

Replace entire `src/main/kotlin/uz/yalla/sipphone/di/AuthModule.kt`:

```kotlin
package uz.yalla.sipphone.di

import org.koin.dsl.module
import uz.yalla.sipphone.data.auth.AuthApi
import uz.yalla.sipphone.data.auth.AuthRepositoryImpl
import uz.yalla.sipphone.data.auth.LogoutOrchestrator
import uz.yalla.sipphone.domain.AuthRepository

val authModule = module {
    single { AuthApi(client = get(), authEventBus = get()) }
    single<AuthRepository> { AuthRepositoryImpl(authApi = get(), tokenProvider = get()) }
    single { LogoutOrchestrator(get(), get(), get(), get()) }
}
```

- [ ] **Step 4: Update AppModule.kt**

Replace entire `src/main/kotlin/uz/yalla/sipphone/di/AppModule.kt`:

```kotlin
package uz.yalla.sipphone.di

val appModules = listOf(networkModule, sipModule, settingsModule, authModule, featureModule, webviewModule)
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/auth/LogoutOrchestrator.kt \
        src/main/kotlin/uz/yalla/sipphone/di/NetworkModule.kt \
        src/main/kotlin/uz/yalla/sipphone/di/AuthModule.kt \
        src/main/kotlin/uz/yalla/sipphone/di/AppModule.kt
git commit -m "feat(di): add NetworkModule, LogoutOrchestrator, wire real AuthRepository"
```

---

### Task 10: Feature Layer — MainComponent Token, RootComponent 401, Main.kt

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/feature/main/MainComponent.kt:41`
- Modify: `src/main/kotlin/uz/yalla/sipphone/navigation/RootComponent.kt`
- Modify: `src/main/kotlin/uz/yalla/sipphone/Main.kt:76-82`

- [ ] **Step 1: Update MainComponent — token in dispatcher URL**

In `MainComponent.kt`, replace line 41:

```kotlin
    val dispatcherUrl: String = authResult.dispatcherUrl
```

with:

```kotlin
    val dispatcherUrl: String = if (authResult.token.isNotEmpty())
        "${authResult.dispatcherUrl}?token=${authResult.token}"
    else
        authResult.dispatcherUrl
```

- [ ] **Step 2: Update RootComponent — add scope, AuthEventBus, LogoutOrchestrator**

Replace entire `RootComponent.kt`:

```kotlin
package uz.yalla.sipphone.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.auth.AuthEvent
import uz.yalla.sipphone.data.auth.AuthEventBus
import uz.yalla.sipphone.data.auth.LogoutOrchestrator
import uz.yalla.sipphone.domain.AuthResult
import uz.yalla.sipphone.feature.login.LoginComponent
import uz.yalla.sipphone.feature.main.MainComponent

class RootComponent(
    componentContext: ComponentContext,
    private val factory: ComponentFactory,
    private val authEventBus: AuthEventBus,
    private val logoutOrchestrator: LogoutOrchestrator,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Screen>()
    private var currentAuthResult: AuthResult? = null
    private var loginSessionCounter = 0
    private val scope = coroutineScope()

    val childStack: Value<ChildStack<Screen, Child>> = childStack(
        source = navigation,
        serializer = Screen.serializer(),
        initialConfiguration = Screen.Login(),
        handleBackButton = false,
        childFactory = ::createChild,
    )

    init {
        scope.launch {
            authEventBus.events.collect { event ->
                when (event) {
                    AuthEvent.SessionExpired -> {
                        logoutOrchestrator.logout()
                        currentAuthResult = null
                        navigation.navigate { listOf(Screen.Login(sessionId = ++loginSessionCounter)) }
                    }
                }
            }
        }
    }

    private fun createChild(screen: Screen, context: ComponentContext): Child =
        when (screen) {
            is Screen.Login -> Child.Login(
                factory.createLogin(context) { authResult ->
                    currentAuthResult = authResult
                    navigation.pushNew(Screen.Main)
                },
            )
            is Screen.Main -> {
                val auth = currentAuthResult ?: run {
                    navigation.navigate { listOf(Screen.Login(sessionId = ++loginSessionCounter)) }
                    return@createChild Child.Login(
                        factory.createLogin(context) { authResult ->
                            currentAuthResult = authResult
                            navigation.pushNew(Screen.Main)
                        },
                    )
                }
                Child.Main(
                    factory.createMain(context, auth) {
                        currentAuthResult = null
                        navigation.navigate { listOf(Screen.Login(sessionId = ++loginSessionCounter)) }
                    },
                )
            }
        }

    sealed interface Child {
        data class Login(val component: LoginComponent) : Child
        data class Main(val component: MainComponent) : Child
    }
}
```

- [ ] **Step 3: Update Main.kt — pass new deps to RootComponent**

In `Main.kt`, replace lines 76-82:

```kotlin
    val decomposeLifecycle = LifecycleRegistry()
    val factory: ComponentFactory = koin.get()
    val rootComponent = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = decomposeLifecycle),
            factory = factory,
        )
    }
```

with:

```kotlin
    val decomposeLifecycle = LifecycleRegistry()
    val factory: ComponentFactory = koin.get()
    val authEventBus: AuthEventBus = koin.get()
    val logoutOrchestrator: LogoutOrchestrator = koin.get()
    val rootComponent = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = decomposeLifecycle),
            factory = factory,
            authEventBus = authEventBus,
            logoutOrchestrator = logoutOrchestrator,
        )
    }
```

Add import at top of Main.kt:

```kotlin
import uz.yalla.sipphone.data.auth.AuthEventBus
import uz.yalla.sipphone.data.auth.LogoutOrchestrator
```

- [ ] **Step 4: Fix RootComponentTest**

Update `createRoot()` to pass new dependencies:

```kotlin
    private val authEventBus = AuthEventBus()
    private val logoutOrchestrator = LogoutOrchestrator(
        authRepository = object : AuthRepository {
            override suspend fun login(pinCode: String) = Result.success(testAuthResult)
            override suspend fun logout() = Result.success(Unit)
        },
        registrationEngine = fakeRegistrationEngine,
        connectionManager = object : uz.yalla.sipphone.domain.ConnectionManager {
            override val connectionState = kotlinx.coroutines.flow.MutableStateFlow<uz.yalla.sipphone.domain.ConnectionState>(uz.yalla.sipphone.domain.ConnectionState.Disconnected)
            override fun startMonitoring(credentials: uz.yalla.sipphone.domain.SipCredentials) {}
            override fun stopMonitoring() {}
        },
        tokenProvider = InMemoryTokenProvider(),
    )
```

And update `RootComponent` constructor call:

```kotlin
        return RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            factory = factory,
            authEventBus = authEventBus,
            logoutOrchestrator = logoutOrchestrator,
        )
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/feature/main/MainComponent.kt \
        src/main/kotlin/uz/yalla/sipphone/navigation/RootComponent.kt \
        src/main/kotlin/uz/yalla/sipphone/Main.kt \
        src/test/kotlin/uz/yalla/sipphone/navigation/RootComponentTest.kt
git commit -m "feat: wire 401 session expiry — RootComponent, MainComponent token URL, Main.kt"
```

---

### Task 11: JS Bridge — requestLogout Command

**Files:**
- Modify: `src/main/kotlin/uz/yalla/sipphone/data/jcef/BridgeRouter.kt:29-37,96-112`
- Modify: `docs/js-bridge-api.md`

- [ ] **Step 1: Add requestLogout callback to BridgeRouter constructor**

In `BridgeRouter.kt`, add two new constructor parameters after `onReady`:

```kotlin
class BridgeRouter(
    private val callEngine: CallEngine,
    private val registrationEngine: RegistrationEngine,
    private val security: BridgeSecurity,
    private val auditLog: BridgeAuditLog,
    private val agentStatusProvider: () -> AgentStatus,
    private val onAgentStatusChange: (AgentStatus) -> Unit,
    private val onReady: () -> String,
    private val onRequestLogout: () -> Unit = {},  // NEW
) {
```

- [ ] **Step 2: Add requestLogout to dispatch**

In `BridgeRouter.kt`, add the case in `dispatch()` function (line ~110, before `else`):

```kotlin
            "requestLogout" -> {
                logger.info { "Frontend requested logout (token likely invalidated by another session)" }
                scope.launch { onRequestLogout() }
                CommandResult.success(null)
            }
```

- [ ] **Step 3: Wire onRequestLogout in MainComponent**

In `MainComponent.kt`, update the BridgeRouter construction (around line 57-65) to add the callback:

```kotlin
            val bridgeRouter = BridgeRouter(
                callEngine = callEngine,
                registrationEngine = registrationEngine,
                security = security,
                auditLog = auditLog,
                agentStatusProvider = { toolbar.agentStatus.value },
                onAgentStatusChange = { toolbar.setAgentStatus(it) },
                onReady = eventEmitter::completeHandshake,
                onRequestLogout = { onLogout() },
            )
```

- [ ] **Step 4: Update JS Bridge API docs**

Add a new section to `docs/js-bridge-api.md` in the Commands Reference section:

```markdown
### `requestLogout()`

Request the native app to perform a full logout. Used when the frontend detects
that the session token has been invalidated (e.g., another operator logged in
with the same PIN).

```javascript
const result = await YallaSIP.requestLogout();
// Success: { success: true, data: null }
```

The native app will: stop SIP monitoring, unregister from SIP server, call the
backend logout API, clear the token, and navigate to the login screen.
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/uz/yalla/sipphone/data/jcef/BridgeRouter.kt \
        src/main/kotlin/uz/yalla/sipphone/feature/main/MainComponent.kt \
        docs/js-bridge-api.md
git commit -m "feat(bridge): add requestLogout command for frontend-initiated session expiry"
```

---

### Task 12: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual smoke test (if backend accessible)**

Run: `./gradlew run`

1. Enter PIN `778899` → should authenticate via real backend
2. Check logs for "Token received" and "Auth complete"
3. WebView should load dispatcher URL with `?token=...` parameter
4. Close and reopen → should require re-login (no token persistence)

- [ ] **Step 4: Final commit if any adjustments**

```bash
git add -A
git commit -m "fix: post-integration adjustments"
```

- [ ] **Step 5: Push**

```bash
git push
```

---

### Task 13: Update Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/planned/backend-auth.md` → DELETE (implemented)

- [ ] **Step 1: Update README.md**

Move "Real backend auth" from "Not Yet Implemented" to "Implemented" section. Add:
```
- Real backend authentication (PIN login → JWT → auto SIP registration)
- Session expiry handling (HTTP 401 + JS bridge requestLogout)
```

- [ ] **Step 2: Update docs/architecture.md**

Add `data/network/` and `data/auth/` new files to the module map. Update the auth/LoginResponse entry (deleted) and add new files: AuthApi, AuthRepositoryImpl, TokenProvider, AuthEventBus, LogoutOrchestrator, DTOs. Add the network module to the DI section.

- [ ] **Step 3: Delete docs/planned/backend-auth.md**

```bash
rm docs/planned/backend-auth.md
```

This feature is now implemented — the planned doc is obsolete.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/architecture.md docs/planned/backend-auth.md
git commit -m "docs: update README and architecture for backend integration"
```

- [ ] **Step 5: Push**

```bash
git push
```

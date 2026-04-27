package uz.yalla.sipphone.data.auth.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.data.auth.remote.service.AuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryImplTest {

    private val sessionPrefs = TestSessionPreferences()

    private fun createClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = false
                })
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }

    private fun createRepo(handler: MockRequestHandler): AuthRepositoryImpl =
        AuthRepositoryImpl(
            service = AuthService(createClient(handler)),
            sessionPreferences = sessionPrefs,
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `login returns Session on success and stores token`() = runTest {
        val loginBody = """
            {"status":true,"code":200,"message":"ok","result":{"token":"jwt-x","token_type":"Bearer ","expire":9999999999},"errors":null}
        """.trimIndent()
        val meBody = """
            {"status":true,"code":200,"message":"ok","result":{"id":7,"tm_user_id":7,"full_name":"Tester","roles":"admin","created_at":"2026-01-01","panel_path":"http://panel.test","sips":[{"extension_number":103,"password":"x","is_active":true,"sip_name":"S","server_url":"http://s","server_port":5060,"domain":"sip.test","connection_type":"udp"}]},"errors":null}
        """.trimIndent()

        val repo = createRepo { request ->
            val body = if (request.url.encodedPath.endsWith("auth/login")) loginBody else meBody
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = repo.login("1234")
        assertIs<Either.Success<*>>(result)
        val session = result.value as uz.yalla.sipphone.domain.auth.model.Session
        assertEquals("jwt-x", session.token)
        assertEquals("Tester", session.profile.fullName)
        assertEquals(1, session.profile.sipAccounts.size)
        assertEquals(103, session.profile.sipAccounts.first().extensionNumber)
        assertEquals("jwt-x", sessionPrefs.accessToken.value)
    }

    @Test
    fun `login surfaces server failure when login envelope status=false`() = runTest {
        val failBody = """
            {"status":false,"code":401,"message":"Error","result":null,"errors":"employee not found"}
        """.trimIndent()
        val repo = createRepo { _ ->
            respond(failBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = repo.login("wrong")
        assertIs<Either.Failure<*>>(result)
        assertIs<DataError.Network.Server>(result.error)
        assertNull(sessionPrefs.accessToken.value)
    }

    @Test
    fun `logout clears session prefs on success`() = runTest {
        sessionPrefs.setAccessToken("present")
        val ok = """{"status":true,"code":200,"message":"ok","result":null,"errors":null}"""
        val repo = createRepo { _ ->
            respond(ok, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = repo.logout()
        assertIs<Either.Success<*>>(result)
        assertNull(sessionPrefs.accessToken.value)
    }
}

private class TestSessionPreferences : SessionPreferences {
    private val state = MutableStateFlow<String?>(null)
    override val accessToken: StateFlow<String?> = state.asStateFlow()
    override fun setAccessToken(token: String?) { state.value = token?.takeIf { it.isNotBlank() } }
    override fun clear() { state.value = null }
}

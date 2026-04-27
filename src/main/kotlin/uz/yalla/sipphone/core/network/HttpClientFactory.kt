package uz.yalla.sipphone.core.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.SessionPreferences

private val httpLogger = KotlinLogging.logger("ktor.http")

private const val REQUEST_TIMEOUT_MS = 15_000L
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 15_000L

fun createHttpClient(
    sessionPrefs: SessionPreferences,
    configPrefs: ConfigPreferences,
    onSessionExpired: () -> Unit,
): HttpClient = HttpClient(CIO) {

    expectSuccess = true

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }

    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) { httpLogger.info { message } }
        }
        level = LogLevel.INFO
    }

    install(Auth) {
        bearer {
            loadTokens {
                val token = sessionPrefs.accessToken.value ?: return@loadTokens null
                BearerTokens(accessToken = token, refreshToken = "")
            }
            refreshTokens {
                onSessionExpired()
                null
            }
            sendWithoutRequest { request ->
                !request.url.toString().contains("auth/login")
            }
        }
    }

    defaultRequest {
        url(configPrefs.current().backendUrl.ensureTrailingSlash())
        contentType(ContentType.Application.Json)
    }
}

private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"

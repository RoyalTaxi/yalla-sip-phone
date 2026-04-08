package uz.yalla.sipphone.data.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.SipAccountManager

private val logger = KotlinLogging.logger {}

class LogoutOrchestrator(
    private val sipAccountManager: SipAccountManager,
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) {
    private var logoutInProgress = false

    suspend fun logout() {
        if (logoutInProgress) return
        logoutInProgress = true
        try {
            logger.info { "Logout sequence starting..." }
            runCatching { sipAccountManager.unregisterAll() }
                .onFailure { logger.warn { "SIP unregisterAll failed: ${it.message}" } }
            tokenProvider.clearToken()
            runCatching { authApi.logout() }
                .onFailure { logger.warn { "API logout failed (expected if token already invalid): ${it.message}" } }
            logger.info { "Logout sequence complete" }
        } finally {
            logoutInProgress = false
        }
    }
}

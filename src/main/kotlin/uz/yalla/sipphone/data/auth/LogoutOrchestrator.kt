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
    private var logoutInProgress = false

    suspend fun logout() {
        if (logoutInProgress) return // prevent re-entrant loop
        logoutInProgress = true
        try {
            logger.info { "Logout sequence starting..." }
            connectionManager.stopMonitoring()
            runCatching { registrationEngine.unregister() }
                .onFailure { logger.warn { "SIP unregister failed: ${it.message}" } }
            tokenProvider.clearToken() // clear BEFORE API call to prevent 401→SessionExpired loop
            runCatching { authRepository.logout() }
                .onFailure { logger.warn { "API logout failed (expected if token already invalid): ${it.message}" } }
            logger.info { "Logout sequence complete" }
        } finally {
            logoutInProgress = false
        }
    }
}

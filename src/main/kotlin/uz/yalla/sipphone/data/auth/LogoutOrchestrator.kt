package uz.yalla.sipphone.data.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.SipAccountManager
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class LogoutOrchestrator(
    private val sipAccountManager: SipAccountManager,
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) {
    private val logoutInProgress = AtomicBoolean(false)

    suspend fun logout() {
        if (!logoutInProgress.compareAndSet(false, true)) return
        try {
            logger.info { "Logout started" }

            // 1. Unregister SIP first so the server stops routing calls to this extension
            //    while the token is still valid (some PBX auth schemes still need it at UNREGISTER).
            runCatching { sipAccountManager.unregisterAll() }
                .onFailure { logger.warn { "SIP unregisterAll failed: ${it.message}" } }

            // 2. Hit the backend logout while token is still present.
            runCatching { authApi.logout() }
                .onFailure { logger.warn { "Server logout failed: ${it.message}" } }

            // 3. Finally clear the token — after this, no in-flight HTTP call can authenticate.
            tokenProvider.clearToken()

            logger.info { "Logout sequence complete" }
        } finally {
            logoutInProgress.set(false)
        }
    }
}

package uz.yalla.sipphone.domain.auth.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.domain.auth.repository.AuthRepository
import uz.yalla.sipphone.domain.sip.SipAccountManager

private val logger = KotlinLogging.logger {}

class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val sipAccountManager: SipAccountManager,
    private val sessionStore: SessionStore,
    private val sessionPreferences: SessionPreferences,
) {
    suspend operator fun invoke() {
        runCatching { sipAccountManager.unregisterAll() }
            .onFailure { logger.warn(it) { "SIP unregister failed" } }
        runCatching { authRepository.logout() }
            .onFailure { logger.warn(it) { "Server logout failed" } }
        sessionStore.clear()
        sessionPreferences.clear()
    }
}

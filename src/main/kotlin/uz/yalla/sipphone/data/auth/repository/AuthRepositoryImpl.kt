package uz.yalla.sipphone.data.auth.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.network.ApiResponse
import uz.yalla.sipphone.core.network.errorMessage
import uz.yalla.sipphone.core.prefs.SessionPreferences
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.core.result.failure
import uz.yalla.sipphone.core.result.flatMapSuccess
import uz.yalla.sipphone.core.result.mapSuccess
import uz.yalla.sipphone.core.result.onSuccess
import uz.yalla.sipphone.data.auth.mapper.ProfileMapper
import uz.yalla.sipphone.data.auth.remote.request.LoginRequest
import uz.yalla.sipphone.data.auth.remote.response.LoginResponse
import uz.yalla.sipphone.data.auth.remote.response.ProfileResponse
import uz.yalla.sipphone.data.auth.remote.service.AuthService
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session
import uz.yalla.sipphone.domain.auth.repository.AuthRepository

private val logger = KotlinLogging.logger {}

class AuthRepositoryImpl(
    private val service: AuthService,
    private val sessionPreferences: SessionPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override suspend fun login(pin: String): Either<DataError.Network, Session> =
        withContext(ioDispatcher) {
            service.login(LoginRequest(pinCode = pin))
                .toToken()
                .onSuccess { sessionPreferences.setAccessToken(it) }
                .flatMapSuccess { token -> fetchProfileWithRetry().mapSuccess { Session(token, it) } }
        }

    override suspend fun me(): Either<DataError.Network, Profile> =
        withContext(ioDispatcher) { fetchProfileWithRetry() }

    override suspend fun logout(): Either<DataError.Network, Unit> =
        withContext(ioDispatcher) {
            service.logout().onSuccess { sessionPreferences.clear() }
        }

    private suspend fun fetchProfileWithRetry(): Either<DataError.Network, Profile> {
        val first = service.me().mapToProfile()
        if (first is Either.Success) return first
        delay(ME_RETRY_DELAY_MS)
        logger.warn { "First /me failed, retrying" }
        return service.me().mapToProfile()
    }

    private fun Either<DataError.Network, ApiResponse<LoginResponse>>.toToken(): Either<DataError.Network, String> =
        flatMapSuccess { resp ->
            val token = resp.result?.token
            if (resp.status && !token.isNullOrBlank()) {
                Either.Success(token)
            } else {
                failure(DataError.Network.Server(resp.code, resp.errorMessage()))
            }
        }

    private fun Either<DataError.Network, ApiResponse<ProfileResponse>>.mapToProfile(): Either<DataError.Network, Profile> =
        flatMapSuccess { resp ->
            if (resp.status) {
                Either.Success(ProfileMapper.map(resp.result))
            } else {
                failure(DataError.Network.Server(resp.code, resp.errorMessage()))
            }
        }

    private companion object {
        const val ME_RETRY_DELAY_MS = 500L
    }
}

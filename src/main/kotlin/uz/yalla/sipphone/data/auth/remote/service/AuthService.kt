package uz.yalla.sipphone.data.auth.remote.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.network.ApiResponse
import uz.yalla.sipphone.core.network.safeApiCall
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.data.auth.remote.request.LoginRequest
import uz.yalla.sipphone.data.auth.remote.response.LoginResponse
import uz.yalla.sipphone.data.auth.remote.response.ProfileResponse

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

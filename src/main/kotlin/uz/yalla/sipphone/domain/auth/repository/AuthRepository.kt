package uz.yalla.sipphone.domain.auth.repository

import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.domain.auth.model.Profile
import uz.yalla.sipphone.domain.auth.model.Session

interface AuthRepository {
    suspend fun login(pin: String): Either<DataError.Network, Session>
    suspend fun me(): Either<DataError.Network, Profile>
    suspend fun logout(): Either<DataError.Network, Unit>
}

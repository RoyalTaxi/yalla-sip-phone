package uz.yalla.sipphone.core.network

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import uz.yalla.sipphone.core.error.DataError
import uz.yalla.sipphone.core.result.Either
import uz.yalla.sipphone.core.result.failure
import uz.yalla.sipphone.core.result.success
import java.io.IOException

suspend inline fun <T> safeApiCall(crossinline call: suspend () -> T): Either<DataError.Network, T> {
    return try {
        success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClientRequestException) {
        val body = runCatching { e.response.bodyAsText() }.getOrDefault("")
        when (e.response.status.value) {
            401 -> failure(DataError.Network.Unauthorized(body.ifBlank { e.message ?: "Unauthorized" }))
            in 400..499 -> failure(DataError.Network.Server(e.response.status.value, body.ifBlank { e.message ?: "" }))
            else -> failure(DataError.Network.Server(e.response.status.value, body.ifBlank { e.message ?: "" }))
        }
    } catch (e: ServerResponseException) {
        val body = runCatching { e.response.bodyAsText() }.getOrDefault("")
        failure(DataError.Network.Server(e.response.status.value, body.ifBlank { e.message ?: "" }))
    } catch (e: HttpRequestTimeoutException) {
        failure(DataError.Network.Connectivity(e))
    } catch (e: ConnectTimeoutException) {
        failure(DataError.Network.Connectivity(e))
    } catch (e: SocketTimeoutException) {
        failure(DataError.Network.Connectivity(e))
    } catch (e: IOException) {
        failure(DataError.Network.Connectivity(e))
    } catch (e: NoTransformationFoundException) {
        failure(DataError.Network.Parse(e))
    } catch (e: JsonConvertException) {
        failure(DataError.Network.Parse(e))
    } catch (e: SerializationException) {
        failure(DataError.Network.Parse(e))
    } catch (e: Throwable) {
        failure(DataError.Network.Unknown)
    }
}

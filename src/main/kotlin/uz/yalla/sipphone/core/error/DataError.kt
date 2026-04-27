package uz.yalla.sipphone.core.error

sealed class DataError {
    sealed class Network : DataError() {
        data class Server(val code: Int, val message: String) : Network()
        data class Unauthorized(val message: String) : Network()
        data class Connectivity(val cause: Throwable?) : Network()
        data class Parse(val cause: Throwable?) : Network()
        data object Unknown : Network()
    }
}

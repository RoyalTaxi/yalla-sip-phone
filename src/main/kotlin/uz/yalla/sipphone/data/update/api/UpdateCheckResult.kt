package uz.yalla.sipphone.data.update.api

import uz.yalla.sipphone.domain.update.UpdateRelease

sealed interface UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data class Malformed(val reason: String) : UpdateCheckResult
    data class Error(val cause: Throwable?) : UpdateCheckResult
}

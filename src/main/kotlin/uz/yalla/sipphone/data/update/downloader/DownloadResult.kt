package uz.yalla.sipphone.data.update.downloader

import java.nio.file.Path

sealed interface DownloadResult {
    data class Success(val msiFile: Path) : DownloadResult
    data object VerifyFailed : DownloadResult
    data class Failed(val cause: Throwable?) : DownloadResult
}

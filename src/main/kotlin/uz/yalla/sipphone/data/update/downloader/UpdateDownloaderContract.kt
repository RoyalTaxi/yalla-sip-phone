package uz.yalla.sipphone.data.update.downloader

import uz.yalla.sipphone.domain.update.UpdateRelease

interface UpdateDownloaderContract {
    suspend fun download(release: UpdateRelease): DownloadResult
}

fun UpdateDownloader.asContract(): UpdateDownloaderContract = object : UpdateDownloaderContract {
    override suspend fun download(release: UpdateRelease): DownloadResult =
        this@asContract.download(release)
}

package uz.yalla.sipphone.data.update.api

import uz.yalla.sipphone.domain.update.UpdateChannel

interface UpdateApiContract {
    suspend fun check(
        channel: UpdateChannel,
        currentVersion: String,
        installId: String,
        platform: String = "windows",
    ): UpdateCheckResult
}

fun UpdateApi.asContract(): UpdateApiContract = object : UpdateApiContract {
    override suspend fun check(
        channel: UpdateChannel,
        currentVersion: String,
        installId: String,
        platform: String,
    ): UpdateCheckResult = this@asContract.check(channel, currentVersion, installId, platform)
}

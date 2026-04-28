package uz.yalla.sipphone.feature.workstation.sideeffect

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class NotificationService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun showIncomingCall(scope: CoroutineScope) {
        scope.launch(ioDispatcher) {
            runCatching {
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> ProcessBuilder(
                        "osascript", "-e",
                        "display notification \"Incoming Call\" with title \"Yalla SIP Phone\" sound name \"default\"",
                    ).start().waitFor(NOTIFY_TIMEOUT_S, TimeUnit.SECONDS)
                    else -> logger.debug { "Notifications not implemented for $os" }
                }
            }.onFailure { logger.warn(it) { "Failed to show notification" } }
        }
    }

    private companion object {
        const val NOTIFY_TIMEOUT_S = 5L
    }
}

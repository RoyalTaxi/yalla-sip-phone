package uz.yalla.sipphone.feature.main.toolbar

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class NotificationService {

    fun showIncomingCall(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> {
                        val process = ProcessBuilder(
                            "osascript", "-e",
                            "display notification \"Incoming Call\" with title \"Yalla SIP Phone\" sound name \"default\""
                        ).start()
                        process.waitFor(5, TimeUnit.SECONDS)
                    }
                    else -> logger.debug { "Notifications not implemented for $os" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to show notification" }
            }
        }
    }
}

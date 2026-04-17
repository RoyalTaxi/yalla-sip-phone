package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

class HoldController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    @Volatile
    private var inProgress = false
    private var timeoutJob: Job? = null

    fun request(op: () -> Unit): Boolean {
        if (inProgress) return false
        inProgress = true
        try {
            op()
        } catch (t: Throwable) {
            inProgress = false
            throw t
        }
        armTimeout()
        return true
    }

    fun onMediaStateChanged() = clear()

    fun cancel() = clear()

    private fun clear() {
        inProgress = false
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun armTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (inProgress) {
                logger.warn { "Hold timeout — clearing in-progress flag" }
                inProgress = false
            }
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

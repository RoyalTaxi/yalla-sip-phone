package uz.yalla.sipphone.data.pjsip.call

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

class HoldController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val onTimeout: () -> Unit = {},
) {
    private val inProgress = AtomicBoolean(false)
    @Volatile
    private var timeoutJob: Job? = null

    fun request(op: () -> Unit): Boolean {
        if (!inProgress.compareAndSet(false, true)) return false
        try {
            op()
        } catch (t: Throwable) {
            inProgress.set(false)
            throw t
        }
        armTimeout()
        return true
    }

    fun onMediaStateChanged() = clear()

    fun cancel() = clear()

    private fun clear() {
        inProgress.set(false)
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun armTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (inProgress.compareAndSet(true, false)) {
                logger.warn { "Hold timeout — clearing in-progress flag and notifying observers" }
                runCatching { onTimeout() }
                    .onFailure { logger.error(it) { "Hold timeout callback threw" } }
            }
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging

@PublishedApi
internal val callbackLogger = KotlinLogging.logger("pjsip.callback")

// C cannot unwind across JNI, so a callback must never throw. Respects the engine's destroyed gate
// so callbacks that race with shutdown become no-ops instead of touching freed native memory.
inline fun runSwigCallback(tag: String, isDestroyed: () -> Boolean, block: () -> Unit) {
    if (isDestroyed()) return
    runCatching(block).onFailure { e ->
        callbackLogger.error(e) { "[$tag] callback failed" }
    }
}

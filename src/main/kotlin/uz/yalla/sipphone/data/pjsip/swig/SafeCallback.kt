package uz.yalla.sipphone.data.pjsip.swig

import io.github.oshai.kotlinlogging.KotlinLogging

@PublishedApi
internal val callbackLogger = KotlinLogging.logger("pjsip.callback")

inline fun runSwigCallback(tag: String, isDestroyed: () -> Boolean, block: () -> Unit) {
    if (isDestroyed()) return
    runCatching(block).onFailure { e ->
        callbackLogger.error(e) { "[$tag] callback failed" }
    }
}

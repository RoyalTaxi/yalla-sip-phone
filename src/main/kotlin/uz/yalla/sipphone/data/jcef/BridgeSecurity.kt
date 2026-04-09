package uz.yalla.sipphone.data.jcef

import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque

class BridgeSecurity {
    private val commandTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()

    private val limits = mapOf(
        "makeCall" to RateLimit(max = 5, windowMs = 60_000),
        "hangup" to RateLimit(max = 10, windowMs = 60_000),
        "answer" to RateLimit(max = 10, windowMs = 60_000),
        "reject" to RateLimit(max = 10, windowMs = 60_000),
        "setMute" to RateLimit(max = 30, windowMs = 60_000),
        "setHold" to RateLimit(max = 20, windowMs = 60_000),
        "setAgentStatus" to RateLimit(max = 10, windowMs = 60_000),
        "getState" to RateLimit(max = 60, windowMs = 60_000),
        "getVersion" to RateLimit(max = 60, windowMs = 60_000),
    )

    fun checkRateLimit(command: String): Boolean {
        val limit = limits[command] ?: RateLimit(max = 30, windowMs = 60_000)
        val now = System.currentTimeMillis()
        val timestamps = commandTimestamps.computeIfAbsent(command) { ArrayDeque() }

        synchronized(timestamps) {
            while (timestamps.isNotEmpty() && timestamps.first() < now - limit.windowMs) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= limit.max) return false
            timestamps.addLast(now)
            return true
        }
    }

    private data class RateLimit(val max: Int, val windowMs: Long)
}

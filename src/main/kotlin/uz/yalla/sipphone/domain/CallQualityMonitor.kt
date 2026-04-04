package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface CallQualityMonitor {
    val qualityStats: StateFlow<CallQualityStats?>
}

data class CallQualityStats(
    val codec: String,
    val rxJitterMs: Float,
    val txJitterMs: Float,
    val rxPacketLoss: Float,
    val txPacketLoss: Float,
    val rttMs: Float,
    val mosScore: Float,
    val durationSeconds: Long,
)

package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface CallEngine {
    val callState: StateFlow<CallState>

    suspend fun makeCall(number: String, accountId: String = ""): Result<Unit>
    suspend fun answerCall(): Result<Unit>
    suspend fun hangupCall(): Result<Unit>
    suspend fun toggleMute(): Result<Unit>
    suspend fun toggleHold(): Result<Unit>
    suspend fun setMute(callId: String, muted: Boolean)
    suspend fun setHold(callId: String, onHold: Boolean)
    suspend fun sendDtmf(callId: String, digits: String): Result<Unit>
    suspend fun transferCall(callId: String, destination: String): Result<Unit>
}

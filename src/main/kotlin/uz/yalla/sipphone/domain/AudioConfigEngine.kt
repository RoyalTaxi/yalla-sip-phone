package uz.yalla.sipphone.domain

import kotlinx.coroutines.flow.StateFlow

interface AudioConfigEngine {
    val audioDevices: StateFlow<AudioDeviceList>
    val currentConfig: StateFlow<AudioSettings>
    suspend fun selectInputDevice(deviceId: Int)
    suspend fun selectOutputDevice(deviceId: Int)
    suspend fun setEchoCancellation(enabled: Boolean, tailLengthMs: Int = 200)
    suspend fun setNoiseSuppression(enabled: Boolean)
    suspend fun setCodecPriority(codec: String, priority: Int)
}

data class AudioDeviceList(
    val inputDevices: List<AudioDevice> = emptyList(),
    val outputDevices: List<AudioDevice> = emptyList(),
    val selectedInput: Int = -1,
    val selectedOutput: Int = -1,
)

data class AudioDevice(
    val id: Int,
    val name: String,
    val inputCount: Int,
    val outputCount: Int,
)

data class AudioSettings(
    val echoCancellation: Boolean = true,
    val echoCancellationTailMs: Int = 200,
    val noiseSuppression: Boolean = true,
    val codecPriorities: Map<String, Int> = emptyMap(),
)

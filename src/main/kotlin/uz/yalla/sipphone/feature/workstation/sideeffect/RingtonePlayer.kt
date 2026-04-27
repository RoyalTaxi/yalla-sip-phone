package uz.yalla.sipphone.feature.workstation.sideeffect

import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

private val logger = KotlinLogging.logger {}

class RingtonePlayer {

    private var clip: Clip? = null

    private var audioStream: AudioInputStream? = null

    fun play() {
        try {
            stop()
            val resourceStream = javaClass.getResourceAsStream("/ringtone.wav") ?: return

            val bufferedSource = resourceStream.buffered()
            val stream = AudioSystem.getAudioInputStream(bufferedSource)
            audioStream = stream
            clip = AudioSystem.getClip().apply {
                open(stream)
                loop(Clip.LOOP_CONTINUOUSLY)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to play ringtone" }
            closeStream()
        }
    }

    fun stop() {
        clip?.let { c ->
            runCatching {
                if (c.isRunning) c.stop()
                c.close()
            }.onFailure { logger.warn(it) { "Failed to close ringtone clip" } }
        }
        clip = null
        closeStream()
    }

    fun release() {
        stop()
    }

    private fun closeStream() {
        audioStream?.let { s ->
            runCatching { s.close() }
                .onFailure { logger.warn(it) { "Failed to close ringtone AudioInputStream" } }
        }
        audioStream = null
    }
}

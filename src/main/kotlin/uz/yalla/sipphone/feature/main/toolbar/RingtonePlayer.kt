package uz.yalla.sipphone.feature.main.toolbar

import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

private val logger = KotlinLogging.logger {}

class RingtonePlayer {

    private var clip: Clip? = null
    // AudioInputStream wraps the resource stream; Clip.close() doesn't propagate close to it,
    // so we hold a reference and close it ourselves. Without this, every ringtone play leaks
    // a file-descriptor-backed stream.
    private var audioStream: AudioInputStream? = null

    fun play() {
        try {
            stop()
            val resourceStream = javaClass.getResourceAsStream("/ringtone.wav") ?: return
            // Do NOT close the resource stream with `.use` here — AudioSystem.getAudioInputStream
            // returns a stream that wraps (or reads lazily from) the source, and closing the
            // source mid-playback aborts the clip. Buffer instead so the source can be released
            // but the audio data stays available.
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

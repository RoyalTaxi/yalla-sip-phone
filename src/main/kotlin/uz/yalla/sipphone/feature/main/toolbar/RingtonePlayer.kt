package uz.yalla.sipphone.feature.main.toolbar

import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

private val logger = KotlinLogging.logger {}

class RingtonePlayer {

    private var clip: Clip? = null

    fun play() {
        try {
            stop()
            val resourceStream = javaClass.getResourceAsStream("/ringtone.wav") ?: return
            resourceStream.use { input ->
                val audioStream = AudioSystem.getAudioInputStream(input)
                clip = AudioSystem.getClip().apply {
                    open(audioStream)
                    loop(Clip.LOOP_CONTINUOUSLY)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to play ringtone" }
        }
    }

    fun stop() {
        clip?.let { c ->
            if (c.isRunning) c.stop()
            c.close()
        }
        clip = null
    }

    fun release() {
        stop()
    }
}

package com.crosspaste.sound

import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import kotlin.time.Duration.Companion.milliseconds

object DesktopSoundService : SoundService {

    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(cpuDispatcher)

    private const val ERROR_SOUND_PATH = "sound/Basso.wav"

    private const val SYNC_FILE_COMPLETE_SOUND_PATH = "sound/Blow.wav"

    private const val ENABLE_PASTEBOARD_LISTENING_SOUND_PATH = "sound/Glass.wav"

    private const val DISABLE_PASTEBOARD_LISTENING_SOUND_PATH = "sound/Pop.wav"

    fun playSound(filePath: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    val byteArray = DesktopResourceUtils.readResourceBytes(filePath)

                    ByteArrayInputStream(byteArray).use { byteInputStream ->
                        AudioSystem.getAudioInputStream(byteInputStream).use { audioInputStream ->
                            val format = audioInputStream.format

                            val info = DataLine.Info(Clip::class.java, format)
                            if (!AudioSystem.isLineSupported(info)) {
                                logger.error { "Line not supported" }
                                return@withContext
                            }

                            AudioSystem.getClip().use { clip ->
                                clip.open(audioInputStream)
                                clip.start()

                                // Wait for the clip to finish playing
                                withTimeoutOrNull(clip.microsecondLength.milliseconds) {
                                    while (clip.isActive) {
                                        delay(10)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error playing sound $filePath" }
            }
        }
    }

    override fun errorSound() {
        playSound(ERROR_SOUND_PATH)
    }

    override fun syncFileCompleteSound() {
        playSound(SYNC_FILE_COMPLETE_SOUND_PATH)
    }

    override fun enablePasteboardListening() {
        playSound(ENABLE_PASTEBOARD_LISTENING_SOUND_PATH)
    }

    override fun disablePasteboardListening() {
        playSound(DISABLE_PASTEBOARD_LISTENING_SOUND_PATH)
    }
}

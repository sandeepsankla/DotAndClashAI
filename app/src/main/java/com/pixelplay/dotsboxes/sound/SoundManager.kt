package com.pixelplay.dotsboxes.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class SoundManager(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sampleRate = 44100
    private var isMuted = false

    fun setMuted(muted: Boolean) { isMuted = muted }
    fun toggleMute(): Boolean { isMuted = !isMuted; return isMuted }

    fun playLineDraw()   = play(generateTone(600.0,  60,  fadeOut = true))
    fun playBoxComplete()= play(generateTone(880.0, 150,  fadeOut = false))
    fun playWin()        = playSequence(listOf(523, 659, 784, 1047), 100)
    fun playLose()       = playSequence(listOf(400, 350, 300), 120)
    fun playTie()        = playSequence(listOf(523, 523, 659), 100)

    private fun play(pcm: ShortArray) {
        if (isMuted) return
        scope.launch {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                kotlinx.coroutines.delay((pcm.size * 1000L / sampleRate) + 50)
                track.release()
            }
        }
    }

    private fun playSequence(frequencies: List<Int>, noteDurationMs: Int) {
        if (isMuted) return
        scope.launch {
            for (freq in frequencies) {
                play(generateTone(freq.toDouble(), noteDurationMs, fadeOut = true))
                kotlinx.coroutines.delay(noteDurationMs.toLong() + 10)
            }
        }
    }

    private fun generateTone(frequency: Double, durationMs: Int, fadeOut: Boolean): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        return ShortArray(numSamples) { i ->
            val raw = sin(2.0 * PI * frequency * i / sampleRate)
            val envelope = if (fadeOut && i > numSamples / 2)
                1.0 - (i - numSamples / 2).toDouble() / (numSamples / 2)
            else 1.0
            (Short.MAX_VALUE * 0.6 * envelope * raw).toInt().toShort()
        }
    }
}

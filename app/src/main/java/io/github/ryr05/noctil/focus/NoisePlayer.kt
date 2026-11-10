package io.github.ryr05.noctil.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/**
 * Background sound for focusing, generated on the phone (no sound files, nothing to download).
 * White noise is evenly bright; soft rain is pink noise (calmer, like steady rain);
 * brown noise is deeper, like distant wind or a waterfall.
 */
enum class Noise(val label: String) { Off("Off"), Rain("Soft rain"), White("White noise"), Brown("Brown noise") }

class NoisePlayer {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun play(noise: Noise, volume: Float = 0.5f) {
        stop()
        if (noise == Noise.Off) return
        running = true
        thread = Thread({ loop(noise, volume) }, "noctil-noise").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop(noise: Noise, volume: Float) {
        val rate = 44_100
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, rate / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val buffer = ShortArray(2048)
        val random = Random.Default
        // Filter state for pink (Paul Kellet's method) and brown noise.
        var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
        var brown = 0f
        var fade = 0f
        try {
            track.play()
            while (running) {
                for (i in buffer.indices) {
                    val white = random.nextFloat() * 2f - 1f
                    val sample = when (noise) {
                        Noise.White -> white * 0.35f
                        Noise.Rain -> {
                            b0 = 0.99886f * b0 + white * 0.0555179f
                            b1 = 0.99332f * b1 + white * 0.0750759f
                            b2 = 0.96900f * b2 + white * 0.1538520f
                            b3 = 0.86650f * b3 + white * 0.3104856f
                            b4 = 0.55000f * b4 + white * 0.5329522f
                            b5 = -0.7616f * b5 - white * 0.0168980f
                            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                            b6 = white * 0.115926f
                            pink * 0.11f
                        }
                        Noise.Brown -> {
                            brown = (brown + 0.02f * white) / 1.02f
                            brown * 3.5f
                        }
                        Noise.Off -> 0f
                    }
                    if (fade < 1f) fade = minOf(1f, fade + 1f / rate) // one-second fade in
                    buffer[i] = (sample * volume * fade).coerceIn(-1f, 1f).times(Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (e: Exception) {
            // Audio device went away; just stop.
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}

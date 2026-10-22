package com.fitflow.timer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.fitflow.timer.data.SoundOutput
import kotlin.math.PI
import kotlin.math.sin

/**
 * Genera los pitidos del temporizador con AudioTrack y PCM sintetizado en memoria
 * (sin ficheros de audio, por lo que la latencia es minima).
 *
 * IMPORTANTE: esta clase NUNCA pide foco de audio. Por eso la musica de
 * Spotify, YouTube o cualquier otra app NO se pausa, silencia ni atenua
 * (duck) cuando suena el pitido: ambos se mezclan en la salida de sonido.
 */
class BeepPlayer(output: SoundOutput) {

    var volume: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private val usage: Int = if (output == SoundOutput.MEDIA) {
        AudioAttributes.USAGE_MEDIA
    } else {
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
    }

    private val handler = Handler(Looper.getMainLooper())

    fun play(freqHz: Double, durationMs: Int) {
        if (volume <= 0f) return
        val samples = buildTone(freqHz, durationMs)
        val track = buildTrack(samples.size * 2) ?: return
        try {
            track.write(samples, 0, samples.size)
            track.setVolume(volume)
            track.play()
        } catch (error: IllegalStateException) {
            track.release()
            return
        }
        handler.postDelayed({
            try {
                track.stop()
            } catch (error: IllegalStateException) {
                // la pista ya estaba detenida
            }
            track.release()
        }, durationMs.toLong() + 200L)
    }

    fun playSequence(tones: List<Pair<Double, Int>>, gapMs: Long = 140L) {
        var delay = 0L
        for ((freq, duration) in tones) {
            handler.postDelayed({ play(freq, duration) }, delay)
            delay += duration + gapMs
        }
    }

    private fun buildTone(freqHz: Double, durationMs: Int): ShortArray {
        val count = (SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val output = ShortArray(count)
        val fade = (count / 8).coerceAtLeast(1)
        for (i in 0 until count) {
            val time = i.toDouble() / SAMPLE_RATE
            var amplitude = 1.0
            if (i < fade) {
                amplitude = i.toDouble() / fade
            } else if (i > count - fade) {
                amplitude = (count - i).toDouble() / fade
            }
            val sample = sin(2.0 * PI * freqHz * time) * amplitude
            output[i] = (sample * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return output
    }

    private fun buildTrack(bufferBytes: Int): AudioTrack? = try {
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    } catch (error: Exception) {
        null
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
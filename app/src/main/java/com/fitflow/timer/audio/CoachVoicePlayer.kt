package com.fitflow.timer.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.fitflow.timer.data.CoachStyle
import com.fitflow.timer.core.VoiceEvent

class CoachVoicePlayer(
    private val context: Context,
    private val style: CoachStyle,
) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(event: VoiceEvent) {
        val fileName = when (event) {
            is VoiceEvent.SessionStarting -> "session_starting.ogg"
            is VoiceEvent.WorkStarted -> "work_started.ogg"
            is VoiceEvent.RestStarted -> "rest_started.ogg"
            is VoiceEvent.SessionFinished -> "session_finished.ogg"
            else -> return
        }
        playAsset("coach/${style.folder}/$fileName")
    }

    private fun playAsset(assetPath: String) {
        stop()
        val afd: AssetFileDescriptor = try {
            context.assets.openFd(assetPath)
        } catch (e: Exception) {
            return
        }
        try {
            val player = MediaPlayer().apply {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                setAudioAttributes(attrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                setOnCompletionListener { mp ->
                    try {
                        afd.close()
                    } catch (_: Exception) {
                    }
                    mp.release()
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    try {
                        afd.close()
                    } catch (_: Exception) {
                    }
                    mp.release()
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                    true
                }
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            try {
                afd.close()
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            }
            try {
                mp.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
    }
}

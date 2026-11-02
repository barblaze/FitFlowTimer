package com.fitflow.timer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.data.CoachStyle

class CoachVoicePlayer(
    private val context: Context,
    private val style: CoachStyle,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var afd: android.content.res.AssetFileDescriptor? = null

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
        val descriptor = try {
            context.assets.openFd(assetPath)
        } catch (e: Exception) {
            return
        }
        afd = descriptor
        try {
            val player = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            player.setAudioAttributes(attrs)
            player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            player.prepare()
            player.setOnCompletionListener { mp ->
                closeAfd()
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                closeAfd()
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
                true
            }
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            closeAfd()
        }
    }

    private fun closeAfd() {
        try {
            afd?.close()
        } catch (_: Exception) {
        }
        afd = null
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
        closeAfd()
    }

    fun release() {
        stop()
    }
}

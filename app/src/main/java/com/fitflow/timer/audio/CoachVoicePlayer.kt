package com.fitflow.timer.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.data.CoachStyle

class CoachVoicePlayer(
    private val context: Context,
    private val style: CoachStyle,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentAfd: AssetFileDescriptor? = null

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
        val afd = try {
            context.assets.openFd(assetPath)
        } catch (e: Exception) {
            return
        }
        currentAfd = afd
        try {
            val player = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            player.setAudioAttributes(attrs)
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            player.prepare()
            player.setOnCompletionListener { mp ->
                releaseCurrent()
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                releaseCurrent()
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
                true
            }
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            releaseCurrent()
        }
    }

    private fun releaseCurrent() {
        currentAfd?.let { afd ->
            try {
                afd.close()
            } catch (_: Exception) {
            }
        }
        currentAfd = null
    }

    fun stop() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (_: Exception) {
            }
            try {
                mp.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        releaseCurrent()
    }

    fun release() {
        stop()
    }
}

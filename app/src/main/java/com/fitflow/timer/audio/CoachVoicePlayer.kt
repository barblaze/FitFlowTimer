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
        val name = when (event) {
            is VoiceEvent.SessionStarting -> "session_starting.ogg"
            is VoiceEvent.WorkStarted -> "work_started.ogg"
            is VoiceEvent.RestStarted -> "rest_started.ogg"
            is VoiceEvent.SessionFinished -> "session_finished.ogg"
            else -> return
        }
        playPath("coach/" + style.folder + "/" + name)
    }

    private fun playPath(path: String) {
        release()
        val afd = try {
            context.assets.openFd(path)
        } catch (e: Exception) {
            return
        }
        currentAfd = afd
        try {
            val mp = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mp.prepare()
            mp.setOnCompletionListener {
                closeAfd()
                try { it.release() } catch (_: Exception) {}
                if (mediaPlayer === it) mediaPlayer = null
            }
            mp.setOnErrorListener { it, _, _ ->
                closeAfd()
                try { it.release() } catch (_: Exception) {}
                if (mediaPlayer === it) mediaPlayer = null
                true
            }
            mediaPlayer = mp
            mp.start()
        } catch (e: Exception) {
            closeAfd()
        }
    }

    private fun closeAfd() {
        try { currentAfd?.close() } catch (_: Exception) {}
        currentAfd = null
    }

    fun stop() {
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        closeAfd()
    }

    fun release() {
        stop()
    }
}

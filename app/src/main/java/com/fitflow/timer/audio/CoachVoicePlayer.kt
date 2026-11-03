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
    private var player: MediaPlayer? = null
    private var afd: AssetFileDescriptor? = null

    fun play(event: VoiceEvent) {
        val name = when (event) {
            is VoiceEvent.SessionStarting -> "session_starting.ogg"
            is VoiceEvent.WorkStarted -> "work_started.ogg"
            is VoiceEvent.RestStarted -> "rest_started.ogg"
            is VoiceEvent.SessionFinished -> "session_finished.ogg"
            else -> return
        }
        val path = "coach/${style.folder}/$name"
        start(path)
    }

    private fun start(path: String) {
        stop()
        val d = try { context.assets.openFd(path) } catch (_: Exception) { return }
        afd = d
        try {
            val mp = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(d.fileDescriptor, d.startOffset, d.length)
            mp.prepare()
            mp.setOnCompletionListener {
                close()
                try { it.release() } catch (_: Exception) {}
                if (player === it) player = null
            }
            mp.setOnErrorListener { it, _, _ ->
                close()
                try { it.release() } catch (_: Exception) {}
                if (player === it) player = null
                true
            }
            player = mp
            mp.start()
        } catch (_: Exception) {
            close()
        }
    }

    private fun close() {
        try { afd?.close() } catch (_: Exception) {}
        afd = null
    }

    fun stop() {
        val mp = player
        player = null
        if (mp != null) {
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        close()
    }

    fun release() {
        stop()
    }
}

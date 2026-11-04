package com.fitflow.timer.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.data.CoachStyle

class CoachVoicePlayer(private val ctx: Context, private val style: CoachStyle) {
    private var mp: MediaPlayer? = null
    private var afd: AssetFileDescriptor? = null
    var volume: Float = 0.8f

    fun play(event: VoiceEvent) {
        val name = when (event) {
            is VoiceEvent.SessionStarting -> "session_starting.ogg"
            is VoiceEvent.WorkStarted -> "work_started.ogg"
            is VoiceEvent.HalfTime -> "halftime.ogg"
            is VoiceEvent.RestStarted -> "rest_started.ogg"
            is VoiceEvent.CycleFinished -> "cycle_finished.ogg"
            is VoiceEvent.SessionFinished -> "session_finished.ogg"
            else -> return
        }
        playPath(name)
    }

    fun playAssetName(assetName: String) {
        if (!assetName.endsWith(".ogg")) return
        playPath(assetName)
    }

    private fun playPath(fileName: String) {
        release()
        var d: AssetFileDescriptor? = null
        // try current style first
        try {
            d = ctx.assets.openFd("coach/${style.folder}/$fileName")
        } catch (_: Exception) {
        }
        // fallback to other styles
        if (d == null) {
            for (s in CoachStyle.values()) {
                if (s == style) continue
                try {
                    d = ctx.assets.openFd("coach/${s.folder}/$fileName")
                    break
                } catch (_: Exception) {
                }
            }
        }
        // fallback to any .ogg in coach
        if (d == null) {
            for (s in CoachStyle.values()) {
                try {
                    val list = ctx.assets.list("coach/${s.folder}")
                    val any = list?.firstOrNull { it.endsWith(".ogg") } ?: continue
                    d = ctx.assets.openFd("coach/${s.folder}/$any")
                    break
                } catch (_: Exception) {
                }
            }
        }
        if (d == null) {
            try {
                val alt = fileName.replace(".ogg", ".mp3")
                try {
                    d = ctx.assets.openFd("coach/${style.folder}/$alt")
                } catch (_: Exception) {
                    for (s in CoachStyle.values()) {
                        try {
                            d = ctx.assets.openFd("coach/${s.folder}/$alt")
                            break
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (d == null) return
        afd = d
        try {
            val m = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            m.setAudioAttributes(attrs)
            m.setDataSource(d.fileDescriptor, d.startOffset, d.length)
            m.prepare()
            val v = volume.coerceIn(0f, 1f)
            try { m.setVolume(v, v) } catch (_: Exception) {}
            m.setOnCompletionListener {
                close()
                try { it.release() } catch (_: Exception) {}
                if (mp === it) mp = null
            }
            m.setOnErrorListener { it, _, _ ->
                close()
                try { it.release() } catch (_: Exception) {}
                if (mp === it) mp = null
                true
            }
            mp = m
            m.start()
        } catch (_: Exception) {
            close()
        }
    }

    private fun close() {
        try { afd?.close() } catch (_: Exception) {}
        afd = null
    }

    fun stop() {
        val m = mp
        mp = null
        if (m != null) {
            try { if (m.isPlaying) m.stop() } catch (_: Exception) {}
            try { m.release() } catch (_: Exception) {}
        }
        close()
    }

    fun release() {
        stop()
    }
}

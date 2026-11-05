package com.fitflow.timer.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.data.CoachStyle
import java.util.ArrayDeque

/**
 * Reproduce los avisos de voz del entrenador.
 *
 * Cada evento busca su archivo exacto (por ejemplo session_starting.ogg). Si el
 * archivo no existe, el evento se ignora: nunca se reutiliza otro audio como
 * relleno. Los avisos que coinciden en el tiempo se encolan para no cortarse.
 */
class CoachVoicePlayer(private val ctx: Context, private val style: CoachStyle) {

    private data class Clip(val folder: String, val fileName: String)

    private val queue = ArrayDeque<Clip>()
    private var mp: MediaPlayer? = null
    private var afd: AssetFileDescriptor? = null
    private var currentClip: Clip? = null
    private var enhancer: LoudnessEnhancer? = null
    private val lastMeme = HashMap<String, String>()

    var volume: Float = 1.0f

    @Synchronized
    fun play(event: VoiceEvent) {
        val candidates = when (event) {
            is VoiceEvent.SessionStarting -> listOf("session_starting.ogg")
            is VoiceEvent.WorkStarted -> listOf("work_started.ogg")
            is VoiceEvent.HalfTime -> listOf("halftime.ogg", "half_time.ogg")
            is VoiceEvent.WorkFinished -> listOf("work_finished.ogg", "round_finished.ogg")
            is VoiceEvent.RestStarted -> listOf("rest_started.ogg")
            is VoiceEvent.CycleFinished -> listOf("cycle_finished.ogg")
            is VoiceEvent.SessionFinished -> listOf("session_finished.ogg", "session_finish.ogg")
        }
        enqueue(candidates)
    }

    @Synchronized
    fun playAssetName(assetName: String) {
        if (!assetName.endsWith(".ogg")) return
        enqueue(listOf(assetName))
    }

    @Synchronized
    private fun enqueue(candidates: List<String>) {
        val clip = resolve(candidates) ?: return
        if (clip == currentClip || queue.peekLast() == clip) return
        if (mp == null && queue.isEmpty()) {
            playNow(clip)
        } else {
            queue.addLast(clip)
        }
    }

    /**
     * Modo Memes: solo usa su propia carpeta (coach/memes) y admite variantes por evento.
     * Para "work_started" sirven work_started.ogg, work_started_1.ogg, work_started_gol.mp3...
     * y se elige una al azar, evitando repetir la ultima. Sin archivos, el evento queda en silencio.
     */
    private fun resolveMemes(candidates: List<String>): Clip? {
        val files = try {
            ctx.assets.list("coach/${style.folder}")?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        for (candidate in candidates) {
            val base = candidate.removeSuffix(".ogg").removeSuffix(".mp3")
            val pattern = Regex("^${Regex.escape(base)}([_-].+)?\\.(ogg|mp3)$", RegexOption.IGNORE_CASE)
            val matches = files.filter { pattern.matches(it) }
            if (matches.isEmpty()) continue
            val pool = if (matches.size > 1) matches.filter { it != lastMeme[base] } else matches
            val pick = pool.random()
            lastMeme[base] = pick
            return Clip(style.folder, pick)
        }
        return null
    }

    private fun resolve(candidates: List<String>): Clip? {
        if (style == CoachStyle.MEMES) return resolveMemes(candidates)
        for (candidate in candidates) {
            val names = if (candidate.endsWith(".ogg")) {
                listOf(candidate, candidate.removeSuffix(".ogg") + ".mp3")
            } else {
                listOf(candidate)
            }
            for (fileName in names) {
                val folder = styleOrder().firstOrNull { assetExists(it.folder, fileName) } ?: continue
                return Clip(folder.folder, fileName)
            }
        }
        return null
    }

    private fun styleOrder(): List<CoachStyle> =
        listOf(style) + CoachStyle.values().filter { it != style }

    private fun assetExists(folder: String, fileName: String): Boolean = try {
        ctx.assets.openFd("coach/${folder}/$fileName").use { true }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun playNow(clip: Clip) {
        releaseCurrent()
        val d = try {
            ctx.assets.openFd("coach/${clip.folder}/${clip.fileName}")
        } catch (_: Exception) {
            playNext()
            return
        }
        afd = d
        currentClip = clip
        try {
            val m = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            m.setAudioAttributes(attrs)
            m.setDataSource(d.fileDescriptor, d.startOffset, d.length)
            m.prepare()
            val v = volume.coerceIn(0f, 1f)
            try { m.setVolume(v, v) } catch (_: Exception) {}
            // Ganancia fija para que la voz no dependa del nivel de grabacion del .ogg
            // Los memes suelen venir ya masterizados fuerte: sin ganancia extra para no saturar
            if (style != CoachStyle.MEMES) {
                try {
                    enhancer = LoudnessEnhancer(m.audioSessionId).apply {
                        setTargetGain(GAIN_MB)
                        enabled = true
                    }
                } catch (_: Exception) {}
            }
            m.setOnCompletionListener { player ->
                if (mp === player) {
                    releaseCurrent()
                    playNext()
                }
            }
            m.setOnErrorListener { player, _, _ ->
                if (mp === player) {
                    releaseCurrent()
                    playNext()
                }
                true
            }
            mp = m
            m.start()
        } catch (_: Exception) {
            releaseCurrent()
            playNext()
        }
    }

    @Synchronized
    private fun playNext() {
        releaseCurrent()
        val next = queue.pollFirst() ?: return
        playNow(next)
    }

    @Synchronized
    private fun releaseCurrent() {
        val m = mp
        mp = null
        currentClip = null
        try { enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        if (m != null) {
            try { if (m.isPlaying) m.stop() } catch (_: Exception) {}
            try { m.release() } catch (_: Exception) {}
        }
        close()
    }

    private fun close() {
        try { afd?.close() } catch (_: Exception) {}
        afd = null
    }

    @Synchronized
    fun stop() {
        queue.clear()
        releaseCurrent()
    }

    @Synchronized
    fun release() {
        stop()
    }

    private companion object {
        /** +6 dB (milibelios) sobre el archivo original. */
        const val GAIN_MB = 600
    }
}

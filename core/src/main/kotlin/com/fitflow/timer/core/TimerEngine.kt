package com.fitflow.timer.core

/** Reloj inyectable para poder testear el motor sin esperar en tiempo real. */
fun interface Clock {
    fun nowMs(): Long
}

sealed interface EngineEvent {
    data class SegmentChanged(val index: Int, val segment: PhaseSegment) : EngineEvent
    data class Countdown(val secondsLeft: Int) : EngineEvent
    data class HalfTime(val segment: PhaseSegment) : EngineEvent
    data object Finished : EngineEvent
}

/**
 * Maquina de estados del temporizador.
 *
 * El tiempo restante se calcula siempre respecto a una marca de tiempo
 * monotonica (SystemClock.elapsedRealtime en produccion), por lo que el
 * temporizador no acumula deriva aunque el sistema retrase los callbacks.
 */
class TimerEngine(
    val config: WorkoutConfig,
    private val clock: Clock,
    private val announcer: Announcer = Announcer { },
) {

    val segments: List<PhaseSegment> = PhasePlanner.build(config)
    val totalMs: Long = segments.sumOf { it.durationSeconds.toLong() * 1000L }

    var index: Int = 0
        private set

    var isRunning: Boolean = false
        private set

    var isFinished: Boolean = false
        private set

    private var segmentEndMs: Long = 0L
    private var pausedRemainingMs: Long = 0L
    private var lastWholeSecond: Int = -1
    private var halfwayFired: Boolean = false

    val currentSegment: PhaseSegment? get() = segments.getOrNull(index)

    fun start() {
        if (segments.isEmpty()) {
            isFinished = true
            isRunning = false
            return
        }
        index = 0
        isFinished = false
        isRunning = true
        lastWholeSecond = -1
        halfwayFired = false
        segmentEndMs = clock.nowMs() + segments[0].durationSeconds * 1000L
        announcer.announce(VoiceEvent.SessionStarting(totalMs / 1000))
        announceSegmentStart(segments[0])
    }

    fun pause() {
        if (!isRunning || isFinished) return
        pausedRemainingMs = (segmentEndMs - clock.nowMs()).coerceAtLeast(0L)
        isRunning = false
    }

    fun resume() {
        if (isRunning || isFinished) return
        segmentEndMs = clock.nowMs() + pausedRemainingMs
        isRunning = true
    }

    fun stop() {
        isRunning = false
        isFinished = true
    }

    fun skip() {
        if (isFinished) return
        if (index + 1 >= segments.size) {
            finish()
            return
        }
        moveTo(index + 1, fromNow = true)
    }

    fun remainingMs(): Long {
        if (isFinished) return 0L
        if (!isRunning) return pausedRemainingMs.coerceAtLeast(0L)
        return (segmentEndMs - clock.nowMs()).coerceAtLeast(0L)
    }

    /** Progreso de la fase actual, de 0f a 1f. */
    fun progress(): Float {
        val segment = currentSegment ?: return 1f
        val total = segment.durationSeconds * 1000L
        if (total <= 0L) return 1f
        val done = 1.0 - (remainingMs().toDouble() / total.toDouble())
        return done.toFloat().coerceIn(0f, 1f)
    }

    /** Tiempo total transcurrido de la sesion en milisegundos. */
    fun elapsedMs(): Long {
        val done = segments.take(index).sumOf { it.durationSeconds.toLong() * 1000L }
        val segment = currentSegment ?: return totalMs
        return done + (segment.durationSeconds * 1000L - remainingMs())
    }

    /**
     * Actualiza el motor. Debe llamarse de forma periodica (cada ~100 ms).
     * Devuelve los eventos-produciidos en esta llamada.
     */
    fun tick(): List<EngineEvent> {
        val events = ArrayList<EngineEvent>()
        if (!isRunning || isFinished) return events

        var guard = 0
        while (guard++ < 500) {
            if (segmentEndMs - clock.nowMs() > 0) break
            val finished = segments.getOrNull(index) ?: break
            if (finished.type == PhaseType.WORK) {
                announcer.announce(VoiceEvent.WorkFinished(finished.round))
            }
            if (index + 1 >= segments.size) {
                finish()
                events += EngineEvent.Finished
                return events
            }
            moveTo(index + 1, fromNow = false)
            events += EngineEvent.SegmentChanged(index, segments[index])
        }

        val segment = currentSegment ?: return events
        val remaining = segmentEndMs - clock.nowMs()

        val wholeSecond = Math.ceil(remaining / 1000.0).toInt()
        if (wholeSecond != lastWholeSecond) {
            if (segment.type != PhaseType.WORK && wholeSecond in 1..5) {
                events += EngineEvent.Countdown(wholeSecond)
            }
            lastWholeSecond = wholeSecond
        }

        if (!halfwayFired && segment.type == PhaseType.WORK && config.announceHalfway) {
            val halfMs = segment.durationSeconds * 1000L / 2
            if (remaining <= halfMs) {
                halfwayFired = true
                events += EngineEvent.HalfTime(segment)
                announcer.announce(VoiceEvent.HalfTime(segment.round, segment.cycle))
            }
        }

        return events
    }

    private fun moveTo(newIndex: Int, fromNow: Boolean) {
        index = newIndex
        val segment = segments[index]
        segmentEndMs = if (fromNow) {
            clock.nowMs() + segment.durationSeconds * 1000L
        } else {
            segmentEndMs + segment.durationSeconds * 1000L
        }
        lastWholeSecond = -1
        halfwayFired = false

        val previous = segments.getOrNull(index - 1)
        if (previous != null && segment.type == PhaseType.WORK && segment.cycle > previous.cycle) {
            announcer.announce(VoiceEvent.CycleFinished(previous.cycle))
        }
        announceSegmentStart(segment)
    }

    private fun finish() {
        isRunning = false
        isFinished = true
        announcer.announce(VoiceEvent.SessionFinished(totalMs / 1000))
    }

    private fun announceSegmentStart(segment: PhaseSegment) {
        when (segment.type) {
            PhaseType.WORK -> announcer.announce(VoiceEvent.WorkStarted(segment.round, segment.cycle))
            PhaseType.REST, PhaseType.REST_BETWEEN_CYCLES ->
                announcer.announce(VoiceEvent.RestStarted(segment.durationSeconds))
            PhaseType.PREPARATION -> Unit
        }
    }
}
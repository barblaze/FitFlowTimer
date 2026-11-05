package com.fitflow.timer.core

import java.io.Serializable

enum class Mode { TABATA, EMOM, AMRAP, CUSTOM }

enum class PhaseType { PREPARATION, WORK, REST, REST_BETWEEN_CYCLES }

/**
 * Segmento individual que forma parte de la secuencia generada por [PhasePlanner].
 */
data class PhaseSegment(
    val type: PhaseType,
    val durationSeconds: Int,
    val cycle: Int,
    val totalCycles: Int,
    val round: Int,
    val totalRounds: Int,
) : Serializable

/**
 * Configuracion completa de un entrenamiento de intervalos.
 * Los tiempos van en segundos, las rondas y ciclos en unidades.
 */
data class WorkoutConfig(
    val mode: Mode = Mode.TABATA,
    val prepSeconds: Int = 10,
    val workSeconds: Int = 20,
    val restSeconds: Int = 10,
    val rounds: Int = 8,
    val cycles: Int = 1,
    val restBetweenCyclesSeconds: Int = 0,
    val announceHalfway: Boolean = false,
) : Serializable {

    /** Duracion total de la sesion en segundos (incluye la preparacion). */
    fun totalSeconds(): Int = PhasePlanner.build(this).sumOf { it.durationSeconds }

    /** Duracion total del bloque de trabajo de una ronda en segundos. */
    fun roundBlockSeconds(): Int = workSeconds + restSeconds

    companion object {
        /** Configuracion inicial de cada perfil (Perfil 1 a 4) mientras no se personalice. */
        fun preset(mode: Mode): WorkoutConfig = when (mode) {
            Mode.TABATA -> WorkoutConfig(
                mode = Mode.TABATA, prepSeconds = 10, workSeconds = 20, restSeconds = 10,
                rounds = 8, cycles = 1, restBetweenCyclesSeconds = 0, announceHalfway = true,
            )
            Mode.EMOM -> WorkoutConfig(
                mode = Mode.EMOM, prepSeconds = 10, workSeconds = 60, restSeconds = 0,
                rounds = 10, cycles = 1, restBetweenCyclesSeconds = 0, announceHalfway = true,
            )
            Mode.AMRAP -> WorkoutConfig(
                mode = Mode.AMRAP, prepSeconds = 10, workSeconds = 300, restSeconds = 0,
                rounds = 1, cycles = 1, restBetweenCyclesSeconds = 0, announceHalfway = true,
            )
            Mode.CUSTOM -> WorkoutConfig(mode = Mode.CUSTOM, announceHalfway = true)
        }
    }
}
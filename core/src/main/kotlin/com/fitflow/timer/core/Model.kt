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
}
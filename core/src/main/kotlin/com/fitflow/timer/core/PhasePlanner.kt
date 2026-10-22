package com.fitflow.timer.core

/**
 * Convierte una [WorkoutConfig] en la lista de fases que se ejecutara el entrenamiento.
 *
 * PREPARACION
 *   por cada ciclo
 *     por cada ronda
 *       TRABAJO
 *       DESCANSO (solo entre rondas del mismo ciclo)
 *     DESCANSO ENTRE CICLOS (solo si quedan ciclos)
 */
object PhasePlanner {

    fun build(config: WorkoutConfig): List<PhaseSegment> {
        val segments = ArrayList<PhaseSegment>()
        val cycles = config.cycles.coerceAtLeast(1)
        val rounds = config.rounds.coerceAtLeast(1)

        if (config.prepSeconds > 0) {
            segments += PhaseSegment(
                type = PhaseType.PREPARATION,
                durationSeconds = config.prepSeconds,
                cycle = 0,
                totalCycles = cycles,
                round = 0,
                totalRounds = rounds,
            )
        }

        for (cycle in 1..cycles) {
            for (round in 1..rounds) {
                segments += PhaseSegment(
                    type = PhaseType.WORK,
                    durationSeconds = config.workSeconds,
                    cycle = cycle,
                    totalCycles = cycles,
                    round = round,
                    totalRounds = rounds,
                )
                if (round < rounds && config.restSeconds > 0) {
                    segments += PhaseSegment(
                        type = PhaseType.REST,
                        durationSeconds = config.restSeconds,
                        cycle = cycle,
                        totalCycles = cycles,
                        round = round,
                        totalRounds = rounds,
                    )
                }
            }
            if (cycle < cycles && config.restBetweenCyclesSeconds > 0) {
                segments += PhaseSegment(
                    type = PhaseType.REST_BETWEEN_CYCLES,
                    durationSeconds = config.restBetweenCyclesSeconds,
                    cycle = cycle,
                    totalCycles = cycles,
                    round = rounds,
                    totalRounds = rounds,
                )
            }
        }
        return segments
    }
}
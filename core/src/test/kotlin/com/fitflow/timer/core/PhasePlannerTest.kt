package com.fitflow.timer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhasePlannerTest {

    @Test
    fun `tabata builds preparation, eight works and seven rests`() {
        val config = WorkoutConfig(
            mode = Mode.TABATA,
            prepSeconds = 10,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 8,
            cycles = 1,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(16, segments.size)
        assertEquals(PhaseType.PREPARATION, segments[0].type)
        assertEquals(PhaseType.WORK, segments[1].type)
        assertEquals(PhaseType.REST, segments[2].type)
        assertEquals(PhaseType.WORK, segments.last().type)
        assertEquals(240, segments.sumOf { it.durationSeconds })
    }

    @Test
    fun `emom round lasts sixty seconds and the session lasts nine fifty`() {
        val config = WorkoutConfig(
            mode = Mode.EMOM,
            prepSeconds = 10,
            workSeconds = 40,
            restSeconds = 20,
            rounds = 10,
            cycles = 1,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(1 + 10 + 9, segments.size)
        assertEquals(60, config.roundBlockSeconds())
        assertEquals(590, config.totalSeconds())
    }

    @Test
    fun `amrap is a single continuous work phase`() {
        val config = WorkoutConfig(
            mode = Mode.AMRAP,
            prepSeconds = 10,
            workSeconds = 600,
            restSeconds = 0,
            rounds = 1,
            cycles = 1,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(2, segments.size)
        assertEquals(PhaseType.PREPARATION, segments[0].type)
        assertEquals(PhaseType.WORK, segments[1].type)
        assertEquals(610, config.totalSeconds())
    }

    @Test
    fun `rest between cycles is added only between cycles`() {
        val config = WorkoutConfig(
            prepSeconds = 0,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 2,
            cycles = 3,
            restBetweenCyclesSeconds = 60,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(3 * 3 + 2, segments.size)
        assertEquals(6, segments.count { it.type == PhaseType.WORK })
        assertEquals(2, segments.count { it.type == PhaseType.REST_BETWEEN_CYCLES })
    }

    @Test
    fun `there is no preparation phase when preparation is zero`() {
        val config = WorkoutConfig(
            prepSeconds = 0,
            workSeconds = 30,
            restSeconds = 0,
            rounds = 1,
            cycles = 1,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(1, segments.size)
        assertEquals(PhaseType.WORK, segments[0].type)
    }

    @Test
    fun `rounds and cycles are always at least one`() {
        val config = WorkoutConfig(
            prepSeconds = 5,
            workSeconds = 10,
            restSeconds = 5,
            rounds = 0,
            cycles = 0,
        )
        val segments = PhasePlanner.build(config)

        assertEquals(2, segments.size)
        assertEquals(PhaseType.PREPARATION, segments[0].type)
        assertEquals(PhaseType.WORK, segments[1].type)
    }
}
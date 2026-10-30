package com.fitflow.timer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMs(): Long = now
    fun advance(ms: Long) {
        now += ms
    }
}

class TimerEngineTest {

    @Test
    fun `countdown beeps in the last three seconds of preparation`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 1, cycles = 1),
            clock,
        )
        engine.start()

        assertTrue(engine.tick().none { it is EngineEvent.Countdown })

        clock.advance(6_000)
        assertTrue(engine.tick().none { it is EngineEvent.Countdown })

        clock.advance(1_000)
        assertEquals(listOf(3), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(2), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(1), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })
    }

    @Test
    fun `countdown beeps in the last three seconds of work`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 1, cycles = 1),
            clock,
        )
        engine.start()

        clock.advance(16_000)
        assertTrue(engine.tick().none { it is EngineEvent.Countdown })

        clock.advance(1_000)
        assertEquals(listOf(3), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(2), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(1), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })
    }

    @Test
    fun `countdown beeps in the last three seconds of rest before the next round`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 0, workSeconds = 10, restSeconds = 10, rounds = 2, cycles = 1),
            clock,
        )
        engine.start()

        clock.advance(10_000)
        assertTrue(engine.tick().none { it is EngineEvent.Countdown })

        clock.advance(6_000)
        assertTrue(engine.tick().none { it is EngineEvent.Countdown })

        clock.advance(1_000)
        assertEquals(listOf(3), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(2), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })

        clock.advance(1_000)
        assertEquals(listOf(1), engine.tick().filterIsInstance<EngineEvent.Countdown>().map { it.secondsLeft })
    }

    @Test
    fun `work phase is followed by rest and then the session ends`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 1, cycles = 1),
            clock,
        )
        engine.start()

        clock.advance(20_000)
        val first = engine.tick()
        assertTrue(first.any { it is EngineEvent.Finished })

        assertTrue(engine.isFinished)
    }

    @Test
    fun `pause freezes the countdown and resume continues it`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 1, cycles = 1),
            clock,
        )
        engine.start()
        clock.advance(3_000)
        engine.tick()

        engine.pause()
        assertFalse(engine.isRunning)
        clock.advance(10_000)
        assertEquals(7_000L, engine.remainingMs())

        engine.resume()
        assertTrue(engine.isRunning)
        clock.advance(1_000)
        assertEquals(6_000L, engine.remainingMs())
    }

    @Test
    fun `halfway event fires once per work round regardless of the legacy flag`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(
                prepSeconds = 0,
                workSeconds = 40,
                restSeconds = 20,
                rounds = 2,
                cycles = 1,
                announceHalfway = false,
            ),
            clock,
        )
        engine.start()

        clock.advance(19_000)
        assertTrue(engine.tick().none { it is EngineEvent.HalfTime })

        clock.advance(1_000)
        assertTrue(engine.tick().any { it is EngineEvent.HalfTime })
        assertTrue(engine.tick().none { it is EngineEvent.HalfTime })

        clock.advance(20_000)
        engine.tick()

        clock.advance(20_000)
        engine.tick()

        clock.advance(20_000)
        assertTrue(engine.tick().any { it is EngineEvent.HalfTime })
    }

    @Test
    fun `voice events are emitted for work rest and finish`() {
        val clock = FakeClock()
        val events = mutableListOf<VoiceEvent>()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 2, cycles = 1),
            clock,
            Announcer { events += it },
        )
        engine.start()
        assertTrue(events.any { it is VoiceEvent.WorkStarted })

        clock.advance(20_000)
        engine.tick()
        assertTrue(events.any { it is VoiceEvent.WorkFinished })
        assertTrue(events.any { it is VoiceEvent.RestStarted })

        clock.advance(10_000)
        engine.tick()
        clock.advance(20_000)
        engine.tick()
        assertTrue(events.any { it is VoiceEvent.SessionFinished })
    }

    @Test
    fun `skip jumps to the next phase`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 2, cycles = 1),
            clock,
        )
        engine.start()
        assertEquals(PhaseType.PREPARATION, engine.currentSegment?.type)

        engine.skip()
        assertEquals(PhaseType.WORK, engine.currentSegment?.type)

        engine.skip()
        assertEquals(PhaseType.REST, engine.currentSegment?.type)
    }

    @Test
    fun `elapsed time follows the clock`() {
        val clock = FakeClock()
        val engine = TimerEngine(
            WorkoutConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 2, cycles = 1),
            clock,
        )
        engine.start()
        clock.advance(5_000)
        engine.tick()

        assertEquals(5_000L, engine.elapsedMs())
        assertEquals(50_000L, engine.totalMs)
    }
}
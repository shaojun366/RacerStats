package com.example.racerstats.timing

import org.junit.Assert.*
import org.junit.Test

class AutomaticLapTimerTest {
    private fun engine(finish: Boolean = false) = AutomaticLapTimer(
        TimingCourse(timingLine(), if (finish) timingLine(60.0) else null, 100f))
    private fun start(engine: AutomaticLapTimer) {
        engine.update(timingFix(1000, 0.0, -10.0))
        val event = engine.update(timingFix(2000, 0.0, 10.0)).single() as AutomaticTimingEvent.Started
        assertEquals(1500L, event.timestamp)
    }
    private fun completeLoop(engine: AutomaticLapTimer, offset: Long = 0): AutomaticTimingEvent.Completed {
        engine.update(timingFix(3000 + offset, 30.0, 10.0))
        engine.update(timingFix(4000 + offset, 30.0, -10.0))
        engine.update(timingFix(5000 + offset, 0.0, -10.0))
        return engine.update(timingFix(6000 + offset, 0.0, 10.0)).single() as AutomaticTimingEvent.Completed
    }

    @Test fun firstPassArmsTheLapWithoutCreatingAResult() {
        val engine = engine()
        assertFalse(engine.isTiming)
        start(engine)
        assertTrue(engine.isTiming)
    }

    @Test fun closedTrackRecordsConsecutiveFullLaps() {
        val engine = engine()
        start(engine)
        val first = completeLoop(engine)
        val second = completeLoop(engine, 4000)
        assertEquals(4000L, first.duration)
        assertEquals(4000L, second.duration)
        assertTrue(first.continues)
        assertTrue(engine.isTiming)
        assertTrue(engine.currentLapDistance > 0f) // Distance after the interpolated line stays in the new lap.
    }

    @Test fun reverseCrossingDoesNotFinishTheLap() {
        val engine = engine()
        start(engine)
        engine.update(timingFix(3000, 30.0, 10.0))
        engine.update(timingFix(4000, 0.0, 10.0))
        assertTrue(engine.update(timingFix(5000, 0.0, -10.0)).isEmpty())
        assertTrue(engine.isTiming)
    }

    @Test fun movingBackAndForthNearTheGateCannotAccumulateAFakeLap() {
        val engine = engine()
        engine.update(timingFix(1000, 0.0, -5.0))
        engine.update(timingFix(2000, 0.0, 5.0))
        for (time in 3..30) {
            assertTrue(engine.update(timingFix(time * 1000L, 0.0, if (time % 2 == 0) 5.0 else -5.0)).isEmpty())
        }
    }

    @Test fun separateFinishCompletesOneRunThenWaitsForAnotherStart() {
        val engine = engine(finish = true)
        start(engine)
        engine.update(timingFix(3000, 0.0, 30.0))
        engine.update(timingFix(4000, 0.0, 50.0))
        val event = engine.update(timingFix(5000, 0.0, 70.0)).single() as AutomaticTimingEvent.Completed
        assertEquals(3000L, event.duration)
        assertFalse(event.continues)
        assertFalse(engine.isTiming)
        assertTrue(engine.update(timingFix(6000, 0.0, 80.0)).isEmpty())
    }

    @Test fun stationaryDriftDoesNotBuildTheDistanceForAFakeLap() {
        val engine = engine()
        start(engine)
        engine.update(timingFix(3000, 30.0, 10.0, speed = 0f))
        engine.update(timingFix(4000, 60.0, 10.0, speed = 0f))
        engine.update(timingFix(5000, 30.0, 10.0, speed = 0f))
        engine.update(timingFix(6000, 0.0, 10.0, speed = 0f))
        engine.update(timingFix(7000, 0.0, -10.0, speed = 0f))
        assertTrue(engine.update(timingFix(8000, 0.0, 10.0)).isEmpty())
        assertTrue(engine.currentLapDistance < 50f)
    }

    @Test fun aPositionJumpOrLongGapInvalidatesTheUnfinishedLap() {
        val engine = engine()
        start(engine)
        assertEquals(listOf(AutomaticTimingEvent.Invalidated), engine.update(timingFix(9000, 0.0, -10.0)))
        assertFalse(engine.isTiming)
        startFreshAtLaterTime(engine)
        assertEquals(listOf(AutomaticTimingEvent.Invalidated), engine.update(timingFix(12100, 0.0, 500.0)))
        assertFalse(engine.isTiming)
    }

    private fun startFreshAtLaterTime(engine: AutomaticLapTimer) {
        engine.update(timingFix(10000, 0.0, -10.0))
        engine.update(timingFix(11000, 0.0, -10.0))
        assertTrue(engine.update(timingFix(12000, 0.0, 10.0)).single() is AutomaticTimingEvent.Started)
    }

    @Test fun poorAccuracyDiscardsTheLapRatherThanBridgingAcrossTheGap() {
        val engine = engine()
        start(engine)
        assertEquals(listOf(AutomaticTimingEvent.Invalidated), engine.update(timingFix(3000, 0.0, 20.0, accuracy = 30f)))
        assertFalse(engine.isTiming)
        assertTrue(engine.update(timingFix(4000, 0.0, -10.0)).isEmpty())
    }

    @Test fun pauseResetDiscardsThePartialLapAndPreservesDrivingDirection() {
        val engine = engine()
        start(engine)
        engine.reset()
        engine.update(timingFix(3000, 0.0, 10.0))
        assertTrue(engine.update(timingFix(4000, 0.0, -10.0)).isEmpty())
        assertFalse(engine.isTiming)
    }

    @Test fun reversedEndpointsOfTheSameFinishLineStillMeanAClosedCircuit() {
        val line = timingLine()
        assertTrue(TimingCourse(line, line.second to line.first, 100f).isClosed)
    }
}

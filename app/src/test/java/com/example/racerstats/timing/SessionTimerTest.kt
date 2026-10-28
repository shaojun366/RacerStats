package com.example.racerstats.timing

import org.junit.Assert.*
import org.junit.Test

class SessionTimerTest {
    private var clock = 100L
    private val timer = SessionTimer { clock }

    @Test fun pausesAreExcludedFromBothSessionAndLapTime() {
        timer.start()
        clock += 1_000
        timer.pause()
        clock += 60_000
        assertEquals(1_000L, timer.lapElapsed)
        timer.resume()
        clock += 2_000
        assertEquals(3_000L, timer.recordLap())
        assertEquals(0L, timer.lapElapsed)
        assertEquals(3_000L, timer.elapsed)
        clock += 500
        assertEquals(500L, timer.recordLap())
    }

    @Test fun stoppedClockFreezesAndNewSessionResetsOffsets() {
        timer.start()
        clock += 1_000
        timer.recordLap()
        clock += 500
        timer.stop()
        clock += 60_000
        assertEquals(500L, timer.lapElapsed)
        assertEquals(0L, timer.recordLap())
        timer.start()
        assertEquals(0L, timer.elapsed)
        assertEquals(0L, timer.lapElapsed)
        assertFalse(timer.isPaused)
    }

    @Test fun repeatedPauseAndResumeDoNotChangeElapsedTime() {
        timer.start()
        clock += 1_000
        timer.pause()
        clock += 100
        timer.pause()
        timer.resume()
        clock += 500
        timer.resume()
        assertEquals(1_500L, timer.elapsed)
    }
}

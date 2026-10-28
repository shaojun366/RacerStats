package com.example.racerstats.timing

import org.junit.Assert.*
import org.junit.Test

class LapManagerTest {
    private val manager = LapManager()
    private fun point(time: Long, distance: Float, speed: Float = 10f) =
        manager.updateCurrentLap(time, distance, speed, 0.0, 0.0)

    private fun referenceLap() {
        manager.startNewLap(0)
        point(1_000, 10f)
        point(2_000, 20f)
        point(10_000, 100f)
        manager.completeLap(10_000)
    }

    @Test fun firstLapDoesNotInventDeltaOrPrediction() {
        manager.startNewLap(0)
        assertNull(point(1_000, 10f))
    }

    @Test fun deltaInterpolatesBetweenCheckpoints() {
        referenceLap()
        manager.startNewLap(10_000)
        val result = point(11_600, 15f)!!
        assertEquals(0.1, result.first, 0.001)
        assertEquals(10_100L, result.second)
    }

    @Test fun eachNewLapRecordsItsOwnEarlyCheckpoints() {
        referenceLap()
        manager.startNewLap(10_000)
        point(10_500, 10f)
        point(15_000, 100f)
        manager.completeLap(15_000)
        manager.startNewLap(15_000)
        assertEquals(0.0, point(15_500, 10f)!!.first, 0.001)
        assertEquals(5_000L, manager.getBestLapTime())
    }

    @Test fun predictionCannotHaveNegativeRemainingTime() {
        referenceLap()
        manager.startNewLap(10_000)
        assertEquals(12_000L, point(22_000, 110f)!!.second)
    }

    @Test fun stoppedSpeedHasNoPredictionAndInvalidSamplesAreIgnored() {
        referenceLap()
        manager.startNewLap(10_000)
        assertEquals(0L, point(11_000, 10f, 0f)!!.second)
        assertNull(point(12_000, Float.NaN))
        assertNull(point(10_500, 15f))
        assertNull(point(12_000, 5f))
    }

    @Test fun newSessionClearsPreviousReference() {
        referenceLap()
        manager.reset()
        manager.startNewLap(0)
        assertEquals(0L, manager.getBestLapTime())
        assertNull(point(1_000, 10f))
    }
}

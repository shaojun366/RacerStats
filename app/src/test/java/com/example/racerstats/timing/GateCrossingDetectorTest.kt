package com.example.racerstats.timing

import com.example.racerstats.track.model.GpsPoint
import org.junit.Assert.*
import org.junit.Test

private const val METRES_PER_DEGREE = 111194.92664455873
internal fun metrePoint(x: Double, y: Double) = GpsPoint(y / METRES_PER_DEGREE, x / METRES_PER_DEGREE)
internal fun timingFix(time: Long, x: Double, y: Double, speed: Float = 10f, accuracy: Float = 1f) =
    TimingFix(time, y / METRES_PER_DEGREE, x / METRES_PER_DEGREE, speed, accuracy, time / 100.0)
internal fun timingLine(y: Double = 0.0) = metrePoint(-10.0, y) to metrePoint(10.0, y)

class GateCrossingDetectorTest {
    private val detector = GateCrossingDetector(timingLine())

    @Test fun interpolatesTheCrossingTimeAndDistance() {
        assertNull(detector.update(timingFix(1000, 0.0, -10.0)))
        val crossing = detector.update(timingFix(2000, 0.0, 10.0))!!
        assertEquals(1500L, crossing.timestamp)
        assertEquals(15.0, crossing.distance, 0.01)
        assertEquals(1, crossing.direction)
    }

    @Test fun crossingTheLineExtensionDoesNotCount() {
        detector.update(timingFix(1000, 30.0, -10.0))
        assertNull(detector.update(timingFix(2000, 30.0, 10.0)))
    }

    @Test fun parallelMovementDoesNotCount() {
        detector.update(timingFix(1000, -5.0, 5.0))
        assertNull(detector.update(timingFix(2000, 5.0, 5.0)))
    }

    @Test fun deadBandSuppressesStationaryJitter() {
        detector.update(timingFix(1000, 0.0, -0.5))
        assertNull(detector.update(timingFix(2000, 0.0, 0.5)))
    }

    @Test fun aSampleOnTheLineDoesNotLoseTheCrossing() {
        detector.update(timingFix(1000, 0.0, -10.0))
        assertNull(detector.update(timingFix(1500, 0.0, 0.0)))
        assertEquals(1500L, detector.update(timingFix(2000, 0.0, 10.0))!!.timestamp)
    }

    @Test fun stoppedVehicleCannotTriggerFromPositionDrift() {
        detector.update(timingFix(1000, 0.0, -10.0, speed = 0f))
        assertNull(detector.update(timingFix(2000, 0.0, 10.0, speed = 0f)))
    }

    @Test fun badAccuracyBreaksTheMovementSegment() {
        detector.update(timingFix(1000, 0.0, -10.0))
        assertNull(detector.update(timingFix(1500, 0.0, 0.0, accuracy = 50f)))
        assertNull(detector.update(timingFix(2000, 0.0, 10.0)))
    }

    @Test fun staleAndImplausiblyFastSegmentsAreIgnored() {
        detector.update(timingFix(1000, 0.0, -10.0))
        assertNull(detector.update(timingFix(5000, 0.0, 10.0)))
        detector.reset()
        detector.update(timingFix(1000, 0.0, -100.0))
        assertNull(detector.update(timingFix(1100, 0.0, 100.0)))
    }

    @Test fun anOutOfOrderPacketDoesNotReplaceTheAnchor() {
        detector.update(timingFix(1000, 0.0, -10.0))
        assertNull(detector.update(timingFix(900, 0.0, 10.0)))
        assertEquals(1500L, detector.update(timingFix(2000, 0.0, 10.0))!!.timestamp)
    }

    @Test fun gatesAcrossTheDateLineUseTheShortLongitudeArc() {
        val gate = GateCrossingDetector(GpsPoint(0.0, 179.9999) to GpsPoint(0.0, -179.9999))
        gate.update(TimingFix(1000, -0.0001, 180.0, 10f, 1f))
        assertEquals(1500L, gate.update(TimingFix(2000, 0.0001, 180.0, 10f, 1f))!!.timestamp)
    }

    @Test(expected = IllegalArgumentException::class) fun aZeroLengthGateIsRejected() {
        GateCrossingDetector(GpsPoint(0.0, 0.0) to GpsPoint(0.0, 0.0))
    }
}

package com.example.racerstats.timing

import com.example.racerstats.track.model.GpsPoint
import kotlin.math.*

data class TimingFix(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float,
    val distance: Double = 0.0
) {
    val point: GpsPoint get() = GpsPoint(latitude, longitude)
    val isUsable: Boolean get() = timestamp >= 0 && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        speed.isFinite() && speed >= 0f && accuracy.isFinite() && accuracy > 0f && accuracy <= 20f
}

data class GateCrossing(val timestamp: Long, val distance: Double, val direction: Int, val point: GpsPoint)

/** Detects a crossing of the finite gate, with a dead band around the line. */
class GateCrossingDetector(private val line: Pair<GpsPoint, GpsPoint>) {
    private val origin = line.first
    private val end = project(line.second)
    private val length = hypot(end.first, end.second)
    private var anchor: TimingFix? = null
    private var lastTimestamp: Long? = null
    private var lastCrossing: Long? = null

    init {
        require(listOf(line.first, line.second).all {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        } && length in 2.0..500.0) { "起终点线长度需要在 2 到 500 米之间" }
    }

    fun reset() {
        anchor = null
        lastTimestamp = null
        lastCrossing = null
    }

    fun distanceFromGate(point: GpsPoint): Double {
        val position = project(point)
        val fraction = ((position.first * end.first + position.second * end.second) / (length * length)).coerceIn(0.0, 1.0)
        return hypot(position.first - fraction * end.first, position.second - fraction * end.second)
    }

    fun update(fix: TimingFix): GateCrossing? {
        if (!fix.isUsable) { anchor = null; return null }
        if (lastTimestamp?.let { fix.timestamp <= it } == true) return null
        lastTimestamp = fix.timestamp
        val current = project(fix.point)
        val side = signedDistance(current)
        val deadBand = max(1.5, min(fix.accuracy / 2.0, 5.0))
        if (abs(side) < deadBand) return null
        val previous = anchor
        anchor = fix
        if (previous == null) return null
        val interval = fix.timestamp - previous.timestamp
        if (interval !in 1L..3_000L) return null
        val from = project(previous.point)
        val previousSide = signedDistance(from)
        if (previousSide * side >= 0) return null
        val travelled = hypot(current.first - from.first, current.second - from.second)
        if ((fix.speed + previous.speed) / 2 < 2f || travelled / (interval / 1000.0) > 120.0) return null
        val fraction = previousSide / (previousSide - side)
        val x = from.first + fraction * (current.first - from.first)
        val y = from.second + fraction * (current.second - from.second)
        val alongGate = (x * end.first + y * end.second) / (length * length)
        if (alongGate !in 0.0..1.0) return null
        val timestamp = previous.timestamp + (interval * fraction).roundToLong()
        if (lastCrossing?.let { timestamp - it < 2_000L } == true) return null
        lastCrossing = timestamp
        return GateCrossing(timestamp,
            previous.distance + (fix.distance - previous.distance) * fraction,
            if (side > 0) 1 else -1,
            GpsPoint(previous.latitude + (fix.latitude - previous.latitude) * fraction,
                (previous.longitude + ((fix.longitude - previous.longitude + 540.0) % 360.0 - 180.0) * fraction + 540.0) % 360.0 - 180.0))
    }

    private fun signedDistance(point: Pair<Double, Double>) =
        (end.first * point.second - end.second * point.first) / length

    private fun project(point: GpsPoint): Pair<Double, Double> {
        val longitudeDelta = (point.longitude - origin.longitude + 540.0) % 360.0 - 180.0
        return Math.toRadians(longitudeDelta) * 6_371_000 * cos(Math.toRadians(origin.latitude)) to
            Math.toRadians(point.latitude - origin.latitude) * 6_371_000
    }
}

internal fun gpsDistance(a: GpsPoint, b: GpsPoint): Double {
    val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
    val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
    val value = sin(latitudeDelta / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(longitudeDelta / 2).pow(2)
    return 12_742_000 * asin(sqrt(value.coerceIn(0.0, 1.0)))
}

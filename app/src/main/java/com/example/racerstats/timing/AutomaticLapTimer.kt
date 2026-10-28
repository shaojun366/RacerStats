package com.example.racerstats.timing

import com.example.racerstats.track.model.GpsPoint
import kotlin.math.max
import kotlin.math.min

data class TimingCourse(
    val startLine: Pair<GpsPoint, GpsPoint>,
    val finishLine: Pair<GpsPoint, GpsPoint>?,
    val length: Float
) {
    val isClosed: Boolean get() = finishLine == null ||
        (gpsDistance(startLine.first, finishLine.first) < 1 && gpsDistance(startLine.second, finishLine.second) < 1) ||
        (gpsDistance(startLine.first, finishLine.second) < 1 && gpsDistance(startLine.second, finishLine.first) < 1)
}

sealed class AutomaticTimingEvent {
    data class Started(val timestamp: Long) : AutomaticTimingEvent()
    data class Completed(val timestamp: Long, val duration: Long, val continues: Boolean,
        val distance: Float, val point: GpsPoint) : AutomaticTimingEvent()
    object Invalidated : AutomaticTimingEvent()
}

/** GPS timestamps are monotonic; incomplete laps are discarded across pauses or bad fixes. */
class AutomaticLapTimer(private val course: TimingCourse) {
    private val startGate = GateCrossingDetector(course.startLine)
    private val finishGate = if (course.isClosed) null else GateCrossingDetector(course.finishLine!!)
    private var previous: TimingFix? = null
    private var totalDistance = 0.0
    private var startDistance = 0.0
    private var startedAt: Long? = null
    private var startDirection: Int? = null
    private var finishDirection: Int? = null
    private var departedStart = false
    private val minimumDeparture = min(40.0, max(20.0, course.length * 0.05))
    private val minimumLapTime = max(3_000L, (course.length / 120f * 1000).toLong())

    val isTiming: Boolean get() = startedAt != null
    val currentLapDistance: Float get() = if (isTiming) (totalDistance - startDistance).coerceAtLeast(0.0).toFloat() else 0f

    init { require(course.length.isFinite() && course.length >= 100f) { "自动计时需要至少 100 米的赛道" } }

    fun reset() {
        previous = null
        totalDistance = 0.0
        startDistance = 0.0
        startedAt = null
        departedStart = false
        startGate.reset()
        finishGate?.reset()
        // Preserve the learned driving direction within this session.
    }

    fun update(raw: TimingFix): List<AutomaticTimingEvent> {
        val last = previous
        if (last != null && raw.timestamp <= last.timestamp) return emptyList()
        if (!raw.isUsable) return invalidate()
        val interval = last?.let { raw.timestamp - it.timestamp }
        val movement = last?.let { gpsDistance(it.point, raw.point) } ?: 0.0
        if (interval != null && (interval > 3_000L || movement / (interval / 1000.0) > 120.0)) {
            val events = invalidate()
            acceptBaseline(raw)
            return events
        }
        // A parked receiver can wander; movement without a moving speed must not build a lap.
        if (raw.speed >= 2f) totalDistance += movement
        val fix = raw.copy(distance = totalDistance)
        previous = fix
        val start = startGate.update(fix)
        val finish = finishGate?.update(fix)
        val lapStart = startedAt
        if (lapStart == null) {
            if (start != null && (startDirection == null || start.direction == startDirection)) {
                startDirection = start.direction
                startedAt = start.timestamp
                startDistance = start.distance
                return listOf(AutomaticTimingEvent.Started(start.timestamp))
            }
            return emptyList()
        }
        if (fix.speed >= 2f && startGate.distanceFromGate(fix.point) >= minimumDeparture) departedStart = true
        val crossing = if (course.isClosed) start else finish
        val direction = if (course.isClosed) startDirection else finishDirection
        if (crossing == null || (direction != null && crossing.direction != direction)) return emptyList()
        if (course.isClosed && !departedStart) return emptyList()
        val duration = crossing.timestamp - lapStart
        if (duration < minimumLapTime || crossing.distance - startDistance < max(20.0, course.length * 0.5)) return emptyList()
        if (finishGate != null) finishDirection = crossing.direction
        val lapDistance = (crossing.distance - startDistance).toFloat()
        startedAt = if (course.isClosed) crossing.timestamp else null
        startDistance = crossing.distance
        departedStart = false
        return listOf(AutomaticTimingEvent.Completed(crossing.timestamp, duration, course.isClosed, lapDistance, crossing.point))
    }

    private fun acceptBaseline(fix: TimingFix) {
        previous = fix.copy(distance = totalDistance)
        startGate.update(previous!!)
        finishGate?.update(previous!!)
    }

    private fun invalidate(): List<AutomaticTimingEvent> {
        val hadLap = isTiming
        reset()
        return if (hadLap) listOf(AutomaticTimingEvent.Invalidated) else emptyList()
    }
}

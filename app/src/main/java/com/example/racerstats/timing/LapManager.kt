package com.example.racerstats.timing

data class LapData(
    val startTime: Long,
    var endTime: Long = 0,
    var duration: Long = 0,
    val checkpoints: MutableList<CheckpointData> = mutableListOf()
)

data class CheckpointData(
    val timestamp: Long,
    val distance: Float,
    val speed: Float,
    val latitude: Double,
    val longitude: Double
)

/** All timestamps use the same active-session clock, excluding pauses. */
class LapManager {
    private var bestLap: LapData? = null
    private var currentLap: LapData? = null
    private var lastPoint: CheckpointData? = null
    private var totalLapDistance = 0f

    fun startNewLap(startTime: Long) {
        currentLap = LapData(startTime)
        lastPoint = null
    }

    fun reset() {
        bestLap = null
        currentLap = null
        lastPoint = null
        totalLapDistance = 0f
    }

    fun updateCurrentLap(
        timestamp: Long, distance: Float, speed: Float, latitude: Double, longitude: Double
    ): Pair<Double, Long>? {
        val lap = currentLap ?: return null
        if (!distance.isFinite() || distance < 0 || !speed.isFinite() || speed < 0 || timestamp < lap.startTime) return null
        val previous = lastPoint
        if (previous != null && (timestamp < previous.timestamp || distance < previous.distance)) return null
        val point = CheckpointData(timestamp, distance, speed, latitude, longitude)
        lastPoint = point
        val lastDistance = lap.checkpoints.lastOrNull()?.distance ?: 0f
        if (distance - lastDistance >= 10f) lap.checkpoints.add(point)

        val best = bestLap ?: return null
        if (best.checkpoints.isEmpty() || totalLapDistance <= 0) return null
        val boundedDistance = distance.coerceAtMost(totalLapDistance)
        val upper = best.checkpoints.firstOrNull { it.distance >= boundedDistance }
            ?: best.checkpoints.last()
        val lower = best.checkpoints.lastOrNull { it.distance < boundedDistance }
        val lowerDistance = lower?.distance ?: 0f
        val lowerTime = lower?.timestamp ?: best.startTime
        val fraction = if (upper.distance > lowerDistance) {
            (boundedDistance - lowerDistance) / (upper.distance - lowerDistance)
        } else 0f
        val referenceElapsed = lowerTime - best.startTime +
            ((upper.timestamp - lowerTime) * fraction).toLong()
        val elapsed = timestamp - lap.startTime
        val delta = (elapsed - referenceElapsed) / 1000.0
        val predicted = if (speed > 0f) {
            elapsed + ((totalLapDistance - distance).coerceAtLeast(0f) / speed * 1000).toLong()
        } else 0L
        return delta to predicted
    }

    fun completeLap(endTime: Long) {
        val lap = currentLap ?: return
        lap.duration = (endTime - lap.startTime).coerceAtLeast(0L)
        lap.endTime = endTime
        lastPoint?.let { point ->
            if (point.distance > (lap.checkpoints.lastOrNull()?.distance ?: 0f)) {
                lap.checkpoints.add(point.copy(timestamp = endTime))
            }
        }
        if (lap.duration > 0 && lap.checkpoints.isNotEmpty() &&
            (bestLap == null || lap.duration < bestLap!!.duration)) {
            bestLap = lap
            totalLapDistance = lap.checkpoints.last().distance
        }
        currentLap = null
        lastPoint = null
    }

    fun getBestLapTime(): Long = bestLap?.duration ?: 0L
}

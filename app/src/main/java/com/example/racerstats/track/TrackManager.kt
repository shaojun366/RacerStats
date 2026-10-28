package com.example.racerstats.track

import com.example.racerstats.track.dao.TrackDao
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackDetails
import com.example.racerstats.track.model.TrackLine
import com.example.racerstats.track.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackManager @Inject constructor(
    private val trackDao: TrackDao,
    private val trackMatcher: TrackMatcher
) {
    fun getAllTracks(): Flow<List<Track>> = trackDao.getAllTracks()

    suspend fun saveTrack(
        name: String,
        points: List<GpsPoint>,
        startLine: Pair<GpsPoint, GpsPoint>,
        finishLine: Pair<GpsPoint, GpsPoint>? = null,
        tolerance: Double = TrackMatcher.THRESHOLD_STREET_CIRCUIT
    ): String? = withContext(Dispatchers.Default) {
        require(name.isNotBlank()) { "请输入赛道名称" }
        require(points.size >= 2) { "请先录制赛道轨迹" }
        val simplified = trackMatcher.simplifyTrack(points)
        val length = calculateTrackLength(simplified)
        require(length >= 100f) { "赛道至少需要 100 米，当前为 ${length.toInt()} 米" }
        val existing = findMatchingTrack(simplified, tolerance)
        val track = existing?.copy(name = name.trim(), length = length)
            ?: Track(name = name.trim(), length = length)
        trackDao.saveTrackWithDetails(track, buildTrackPoints(track.id, simplified),
            buildLine(track.id, startLine, finishLine))
        existing?.id
    }

    private suspend fun findMatchingTrack(points: List<GpsPoint>, tolerance: Double): Track? {
        // Room's observable Flow never completes. Take one snapshot before comparing.
        for (track in trackDao.getAllTracks().first()) {
            val reference = trackDao.getTrackPoints(track.id).map { GpsPoint(it.latitude, it.longitude, it.altitude) }
            if (trackMatcher.calculateSimilarity(points, reference, tolerance) >= track.similarity) return track
        }
        return null
    }

    private fun buildTrackPoints(trackId: String, points: List<GpsPoint>): List<TrackPoint> {
        var cumulativeDistance = 0f
        return points.mapIndexed { index, point ->
            if (index > 0) cumulativeDistance += trackMatcher.calculateDistance(points[index - 1], point).toFloat()
            TrackPoint(trackId = trackId, latitude = point.latitude, longitude = point.longitude,
                altitude = point.altitude, distance = cumulativeDistance, sequence = index)
        }
    }

    private fun buildLine(id: String, start: Pair<GpsPoint, GpsPoint>, finish: Pair<GpsPoint, GpsPoint>?) = TrackLine(
        trackId = id, startPoint1Lat = start.first.latitude, startPoint1Lon = start.first.longitude,
        startPoint2Lat = start.second.latitude, startPoint2Lon = start.second.longitude,
        finishPoint1Lat = finish?.first?.latitude, finishPoint1Lon = finish?.first?.longitude,
        finishPoint2Lat = finish?.second?.latitude, finishPoint2Lon = finish?.second?.longitude
    )

    private fun calculateTrackLength(points: List<GpsPoint>): Float = points.zipWithNext().sumOf { (a, b) ->
        trackMatcher.calculateDistance(a, b)
    }.toFloat()

    suspend fun deleteTrack(trackId: String) {
        trackDao.getTrackById(trackId)?.let { trackDao.deleteTrackWithDetails(it) }
    }

    suspend fun getTrackDetails(trackId: String): TrackDetails? = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext null
        val points = trackDao.getTrackPoints(trackId).map { GpsPoint(it.latitude, it.longitude, it.altitude) }
        val line = trackDao.getTrackLine(trackId)
        val start = line?.let { GpsPoint(it.startPoint1Lat, it.startPoint1Lon) to GpsPoint(it.startPoint2Lat, it.startPoint2Lon) }
        val finish = if (line?.finishPoint1Lat != null && line.finishPoint1Lon != null &&
            line.finishPoint2Lat != null && line.finishPoint2Lon != null) {
            GpsPoint(line.finishPoint1Lat, line.finishPoint1Lon) to GpsPoint(line.finishPoint2Lat, line.finishPoint2Lon)
        } else null
        TrackDetails(track, points, start, finish, bestLapTimeMillis = null)
    }

    suspend fun updateTrack(
        trackId: String, name: String, startLine: Pair<GpsPoint, GpsPoint>, finishLine: Pair<GpsPoint, GpsPoint>?
    ) {
        require(name.isNotBlank()) { "请输入赛道名称" }
        trackDao.updateTrackWithLine(trackId, name.trim(), buildLine(trackId, startLine, finishLine))
    }
}

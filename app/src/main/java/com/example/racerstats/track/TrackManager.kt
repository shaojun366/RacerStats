package com.example.racerstats.track

import android.util.Log
import com.example.racerstats.track.dao.TrackDao
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackDetails
import com.example.racerstats.track.model.TrackLine
import com.example.racerstats.track.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackManager @Inject constructor(
    private val trackDao: TrackDao,
    private val trackMatcher: TrackMatcher
) {
    companion object {
        private const val TAG = "TrackManager"
        private const val MIN_TRACK_LENGTH = 100f  // 最小赛道长度（米）
        private const val DEFAULT_SIMILARITY_THRESHOLD = 0.95f
    }
    
    /**
     * 获取所有赛道
     */
    fun getAllTracks(): Flow<List<Track>> = trackDao.getAllTracks()
    
    /**
     * 保存新赛道
     * @return 返回匹配到的已存在赛道的ID，如果是新赛道则返回null
     */
    suspend fun saveTrack(
        name: String,
        points: List<GpsPoint>,
        startLine: Pair<GpsPoint, GpsPoint>,
        finishLine: Pair<GpsPoint, GpsPoint>? = null
    ): String? = withContext(Dispatchers.Default) {
        try {
            // 1. 简化轨迹点
            val simplifiedPoints = trackMatcher.simplifyTrack(points)
            
            // 2. 计算赛道长度
            val length = calculateTrackLength(simplifiedPoints)
            if (length < MIN_TRACK_LENGTH) {
                throw IllegalArgumentException("Track is too short: $length meters")
            }
            
            // 3. 检查是否与现有赛道匹配
            val existingTrack = findMatchingTrack(simplifiedPoints)
            
            if (existingTrack != null) {
                // 更新现有赛道
                updateTrackDetails(existingTrack.id, simplifiedPoints, startLine, finishLine)
                return@withContext existingTrack.id
            }
            
            // 4. 创建新赛道
            val track = Track(
                name = name,
                length = length,
                similarity = DEFAULT_SIMILARITY_THRESHOLD
            )
            
            // 5. 保存赛道数据
            saveTrackDetails(track, simplifiedPoints, startLine, finishLine)
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving track: ${e.message}", e)
            throw e
        }
    }
    
    private suspend fun findMatchingTrack(points: List<GpsPoint>): Track? {
        val tracks = trackDao.getAllTracks().let { flow ->
            var result: List<Track>? = null
            flow.collect { result = it }
            result ?: return null
        }
        
        for (track in tracks) {
            val trackPoints = trackDao.getTrackPoints(track.id)
            val trackGpsPoints = trackPoints.map { 
                GpsPoint(it.latitude, it.longitude, it.altitude)
            }
            
            val similarity = trackMatcher.calculateSimilarity(points, trackGpsPoints)
            if (similarity >= track.similarity) {
                return track
            }
        }
        
        return null
    }
    
    private suspend fun saveTrackDetails(
        track: Track,
        points: List<GpsPoint>,
        startLine: Pair<GpsPoint, GpsPoint>,
        finishLine: Pair<GpsPoint, GpsPoint>?
    ) {
        // 1. 保存赛道基本信息
        trackDao.insertTrack(track)
        
        // 2. 保存轨迹点
        val trackPoints = points.mapIndexed { index, point ->
            TrackPoint(
                trackId = track.id,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                distance = calculateDistanceFromStart(points.subList(0, index + 1)),
                sequence = index
            )
        }
        trackDao.insertPoints(trackPoints)
        
        // 3. 保存起终点线
        val trackLine = TrackLine(
            trackId = track.id,
            startPoint1Lat = startLine.first.latitude,
            startPoint1Lon = startLine.first.longitude,
            startPoint2Lat = startLine.second.latitude,
            startPoint2Lon = startLine.second.longitude,
            finishPoint1Lat = finishLine?.first?.latitude,
            finishPoint1Lon = finishLine?.first?.longitude,
            finishPoint2Lat = finishLine?.second?.latitude,
            finishPoint2Lon = finishLine?.second?.longitude
        )
        trackDao.insertTrackLine(trackLine)
    }
    
    private suspend fun updateTrackDetails(
        trackId: String,
        points: List<GpsPoint>,
        startLine: Pair<GpsPoint, GpsPoint>,
        finishLine: Pair<GpsPoint, GpsPoint>?
    ) {
        // 1. 删除旧的轨迹点和起终点线
        trackDao.deleteTrackPoints(trackId)
        trackDao.deleteTrackLine(trackId)
        
        // 2. 保存新的轨迹点
        val trackPoints = points.mapIndexed { index, point ->
            TrackPoint(
                trackId = trackId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                distance = calculateDistanceFromStart(points.subList(0, index + 1)),
                sequence = index
            )
        }
        trackDao.insertPoints(trackPoints)
        
        // 3. 保存新的起终点线
        val trackLine = TrackLine(
            trackId = trackId,
            startPoint1Lat = startLine.first.latitude,
            startPoint1Lon = startLine.first.longitude,
            startPoint2Lat = startLine.second.latitude,
            startPoint2Lon = startLine.second.longitude,
            finishPoint1Lat = finishLine?.first?.latitude,
            finishPoint1Lon = finishLine?.first?.longitude,
            finishPoint2Lat = finishLine?.second?.latitude,
            finishPoint2Lon = finishLine?.second?.longitude
        )
        trackDao.insertTrackLine(trackLine)
    }
    
    private fun calculateTrackLength(points: List<GpsPoint>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += trackMatcher.calculateDistance(points[i-1], points[i]).toFloat()
        }
        return length
    }
    
    private fun calculateDistanceFromStart(points: List<GpsPoint>): Float {
        var distance = 0f
        for (i in 1 until points.size) {
            distance += trackMatcher.calculateDistance(points[i-1], points[i]).toFloat()
        }
        return distance
    }
    
    suspend fun deleteTrack(trackId: String) {
        val track = trackDao.getTrackById(trackId) ?: return
        trackDao.deleteTrackWithDetails(track)
    }

    suspend fun getTrackDetails(trackId: String): TrackDetails? = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext null
        val points = trackDao.getTrackPoints(trackId).map {
            GpsPoint(it.latitude, it.longitude, it.altitude)
        }
        val line = trackDao.getTrackLine(trackId)

        val startLine = line?.let {
            Pair(
                GpsPoint(it.startPoint1Lat, it.startPoint1Lon),
                GpsPoint(it.startPoint2Lat, it.startPoint2Lon)
            )
        }
        val finishLine = if (
            line?.finishPoint1Lat != null &&
            line.finishPoint1Lon != null &&
            line.finishPoint2Lat != null &&
            line.finishPoint2Lon != null
        ) {
            val finishPoint1 = GpsPoint(line.finishPoint1Lat, line.finishPoint1Lon!!)
            val finishPoint2 = GpsPoint(line.finishPoint2Lat, line.finishPoint2Lon!!)
            Pair(finishPoint1, finishPoint2)
        } else {
            null
        }

        TrackDetails(
            track = track,
            points = points,
            startLine = startLine,
            finishLine = finishLine,
            bestLapTimeMillis = null
        )
    }

    suspend fun updateTrack(
        trackId: String,
        name: String,
        startLine: Pair<GpsPoint, GpsPoint>,
        finishLine: Pair<GpsPoint, GpsPoint>?
    ) = withContext(Dispatchers.IO) {
        val current = trackDao.getTrackById(trackId) ?: return@withContext
        val updated = current.copy(name = name)
        trackDao.insertTrack(updated)

        val line = TrackLine(
            trackId = trackId,
            startPoint1Lat = startLine.first.latitude,
            startPoint1Lon = startLine.first.longitude,
            startPoint2Lat = startLine.second.latitude,
            startPoint2Lon = startLine.second.longitude,
            finishPoint1Lat = finishLine?.first?.latitude,
            finishPoint1Lon = finishLine?.first?.longitude,
            finishPoint2Lat = finishLine?.second?.latitude,
            finishPoint2Lon = finishLine?.second?.longitude
        )
        trackDao.insertTrackLine(line)
    }
}
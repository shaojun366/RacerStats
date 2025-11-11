package com.example.racerstats.track

import android.util.Log
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackPoint
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackMatcher @Inject constructor() {
    companion object {
        private const val TAG = "TrackMatcher"
        
        // 不同场景的距离阈值配置
        const val THRESHOLD_PRECISION_TRACK = 25.0   // 精确赛道：25米
        const val THRESHOLD_STREET_CIRCUIT = 40.0    // 街道赛道：40米  
        const val THRESHOLD_GENERAL_ROAD = 50.0      // 一般道路：50米
        const val THRESHOLD_HIGHWAY = 80.0           // 高速公路：80米
        
        private const val DEFAULT_DISTANCE_THRESHOLD = THRESHOLD_STREET_CIRCUIT
        private const val MIN_POINTS_REQUIRED = 10
    }
    
    /**
     * 计算两条轨迹的相似度 (0-1)
     * @param distanceThreshold 距离阈值，默认使用街道赛道标准
     */
    fun calculateSimilarity(
        track1Points: List<GpsPoint>, 
        track2Points: List<GpsPoint>,
        distanceThreshold: Double = DEFAULT_DISTANCE_THRESHOLD
    ): Double {
        if (track1Points.size < MIN_POINTS_REQUIRED || track2Points.size < MIN_POINTS_REQUIRED) {
            return 0.0
        }
        
        try {
            // 计算Hausdorff距离
            val hausdorffDistance = calculateHausdorffDistance(track1Points, track2Points)
            
            // 将距离转换为相似度得分 (0-1)
            val similarity = 1.0 / (1.0 + hausdorffDistance / distanceThreshold)
            return similarity.coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating similarity: ${e.message}", e)
            return 0.0
        }
    }
    
    private fun calculateHausdorffDistance(track1Points: List<GpsPoint>, track2Points: List<GpsPoint>): Double {
        val forward = track1Points.maxOf { p1 -> 
            track2Points.minOf { p2 -> calculateDistance(p1, p2) }
        }
        val backward = track2Points.maxOf { p2 -> 
            track1Points.minOf { p1 -> calculateDistance(p1, p2) }
        }
        return max(forward, backward)
    }
    
    fun calculateDistance(point1: GpsPoint, point2: GpsPoint): Double {
        val lat1 = point1.latitude * PI / 180.0
        val lon1 = point1.longitude * PI / 180.0
        val lat2 = point2.latitude * PI / 180.0
        val lon2 = point2.longitude * PI / 180.0
        
        val R = 6371e3 // 地球半径（米）
        val φ1 = lat1
        val φ2 = lat2
        val Δφ = (lat2 - lat1)
        val Δλ = (lon2 - lon1)
        
        val a = sin(Δφ/2) * sin(Δφ/2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ/2) * sin(Δλ/2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        
        return R * c
    }
    
    /**
     * 简化轨迹点（Douglas-Peucker算法）
     */
    fun simplifyTrack(points: List<GpsPoint>, epsilon: Double = 10.0): List<GpsPoint> {
        if (points.size < 3) return points
        
        val mask = BooleanArray(points.size) { true }
        douglasPeucker(points, 0, points.lastIndex, epsilon, mask)
        
        return points.filterIndexed { index, _ -> mask[index] }
    }
    
    private fun douglasPeucker(
        points: List<GpsPoint>,
        start: Int,
        end: Int,
        epsilon: Double,
        mask: BooleanArray
    ) {
        if (end <= start + 1) return
        
        var maxDistance = 0.0
        var maxIndex = start + 1
        
        val startPoint = points[start]
        val endPoint = points[end]
        
        for (i in start + 1 until end) {
            val distance = perpendicularDistance(points[i], startPoint, endPoint)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }
        
        if (maxDistance > epsilon) {
            douglasPeucker(points, start, maxIndex, epsilon, mask)
            douglasPeucker(points, maxIndex, end, epsilon, mask)
        } else {
            for (i in start + 1 until end) {
                mask[i] = false
            }
        }
    }
    
    private fun perpendicularDistance(point: GpsPoint, lineStart: GpsPoint, lineEnd: GpsPoint): Double {
        val lat = point.latitude * PI / 180.0
        val lon = point.longitude * PI / 180.0
        val lat1 = lineStart.latitude * PI / 180.0
        val lon1 = lineStart.longitude * PI / 180.0
        val lat2 = lineEnd.latitude * PI / 180.0
        val lon2 = lineEnd.longitude * PI / 180.0
        
        // 使用球面几何计算垂直距离
        val R = 6371e3
        
        val bearingA = bearing(lat1, lon1, lat, lon)
        val bearingB = bearing(lat1, lon1, lat2, lon2)
        val distance = calculateDistance(lineStart, point)
        
        val angle = abs(bearingA - bearingB)
        return abs(distance * sin(angle))
    }
    
    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(lon2 - lon1) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon2 - lon1)
        return atan2(y, x)
    }
}
package com.example.racerstats

import android.util.Log
import java.util.*
import kotlin.math.*

/**
 * Manages lap timing, delta calculations, and predictions
 */
class LapManager {
    private data class LapPoint(
        var timestamp: Long,
        var distance: Float,
        var speed: Float,
        var latitude: Double,
        var longitude: Double
    )

    private data class LapData(
        var startTime: Long,
        var endTime: Long? = null,
        var points: MutableList<LapPoint> = mutableListOf(),
        var totalDistance: Float = 0f,
        var bestSplits: MutableMap<Float, Long> = mutableMapOf()
    )

    private var currentLap: LapData? = null
    private var bestLap: LapData? = null
    private var previousLap: LapData? = null
    private var lapCount = 0
    
    private var startFinishLine = StartFinishLine()
    private var isFirstCrossing = true
    
    // 配置参数
    private val minLapTime = 10_000L  // 最短圈速时间（毫秒）
    private val maxLapTime = 600_000L // 最大圈速时间（毫秒）
    private val splitInterval = 100f   // 分段距离（米）
    
    /**
     * 更新当前圈信息，返回 delta 和预测完成时间
     * @return Pair<Delta (seconds), Predicted total time (milliseconds)> 如果没有足够数据则返回null
     */
    fun updateCurrentLap(
        timestamp: Long,
        distance: Float,
        speed: Float,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Long>? {
        // 检查是否穿越起终点线
        if (startFinishLine.checkCrossing(latitude, longitude)) {
            if (isFirstCrossing) {
                // 第一次穿越，开始计时
                startNewLap(timestamp, distance, speed, latitude, longitude)
                isFirstCrossing = false
            } else {
                // 完成一圈
                finishCurrentLap(timestamp, distance)
                startNewLap(timestamp, distance, speed, latitude, longitude)
            }
        }
        
        // 如果没有当前圈数据，返回null
        val current = currentLap ?: return null
        
        // 添加新的数据点
        current.points.add(LapPoint(timestamp, distance, speed, latitude, longitude))
        
        // 更新最佳分段时间
        val currentSplit = (distance / splitInterval).toInt() * splitInterval
        val splitTime = timestamp - current.startTime
        current.bestSplits[currentSplit] = splitTime
        
        // 计算 delta
        val delta = calculateDelta(current, distance)
        
        // 预测完成时间
        val predicted = predictLapTime(current, distance, speed)
        
        return Pair(delta, predicted)
    }
    
    private fun startNewLap(
        timestamp: Long,
        distance: Float,
        speed: Float,
        latitude: Double,
        longitude: Double
    ) {
        currentLap = LapData(timestamp).apply {
            points.add(LapPoint(timestamp, distance, speed, latitude, longitude))
        }
        lapCount++
    }
    
    private fun finishCurrentLap(timestamp: Long, distance: Float) {
        currentLap?.let { lap ->
            lap.endTime = timestamp
            lap.totalDistance = distance
            
            // 检查是否是有效的圈速
            val lapTime = timestamp - lap.startTime
            if (lapTime in minLapTime..maxLapTime) {
                // 更新最佳圈速
                if (bestLap == null || lapTime < (bestLap?.endTime ?: Long.MAX_VALUE) - (bestLap?.startTime ?: 0)) {
                    previousLap = bestLap
                    bestLap = lap
                } else {
                    previousLap = lap
                }
            }
        }
    }
    
    private fun calculateDelta(current: LapData, distance: Float): Double {
        val best = bestLap ?: return 0.0
        
        // 在最佳圈中找到相近距离的点
        val bestIndex = best.points.binarySearch { 
            (it.distance - distance).toInt()
        }.let { if (it < 0) -it - 1 else it }
        
        if (bestIndex >= best.points.size) return 0.0
        
        // 计算当前用时与最佳圈在相同距离时的用时差
        val currentTime = current.points.last().timestamp - current.startTime
        val bestTime = best.points[bestIndex].timestamp - best.startTime
        
        return (currentTime - bestTime) / 1000.0  // 转换为秒
    }
    
    private fun predictLapTime(current: LapData, distance: Float, speed: Float): Long {
        val best = bestLap ?: return 0L
        
        if (speed <= 0f) return 0L
        
        // 使用当前速度和剩余距离进行简单预测
        val remainingDistance = best.totalDistance - distance
        val predictedRemainingTime = (remainingDistance / speed * 1000).toLong()
        
        return (current.points.last().timestamp - current.startTime) + predictedRemainingTime
    }
    
    /**
     * 简单的起终点线检测
     * TODO: 实现更精确的起终点线检测逻辑
     */
    private class StartFinishLine {
        private var lastLat: Double = 0.0
        private var lastLon: Double = 0.0
        private var startFinishLat: Double = 0.0
        private var startFinishLon: Double = 0.0
        private var isSet = false
        
        fun checkCrossing(lat: Double, lon: Double): Boolean {
            if (!isSet) {
                // 第一个有效点设为起终点线
                if (lat != 0.0 && lon != 0.0) {
                    startFinishLat = lat
                    startFinishLon = lon
                    isSet = true
                }
                lastLat = lat
                lastLon = lon
                return false
            }
            
            // 简单距离检测（实际应该使用更复杂的线段交叉检测）
            val distance = calculateDistance(lat, lon, startFinishLat, startFinishLon)
            val lastDistance = calculateDistance(lastLat, lastLon, startFinishLat, startFinishLon)
            
            lastLat = lat
            lastLon = lon
            
            // 如果从远处接近起终点线，则认为穿越
            return lastDistance > 20 && distance < 10
        }
        
        private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371e3 // 地球半径（米）
            val φ1 = lat1 * PI / 180
            val φ2 = lat2 * PI / 180
            val Δφ = (lat2 - lat1) * PI / 180
            val Δλ = (lon2 - lon1) * PI / 180

            val a = sin(Δφ / 2) * sin(Δφ / 2) +
                    cos(φ1) * cos(φ2) *
                    sin(Δλ / 2) * sin(Δλ / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return r * c
        }
    }
    
    fun reset() {
        currentLap = null
        bestLap = null
        previousLap = null
        lapCount = 0
        isFirstCrossing = true
    }
    
    fun getCurrentLapTime(): Long {
        val current = currentLap ?: return 0L
        return System.currentTimeMillis() - current.startTime
    }
    
    fun getBestLapTime(): Long {
        val best = bestLap ?: return 0L
        return (best.endTime ?: 0L) - best.startTime
    }
    
    fun getLapCount(): Int = lapCount
}

fun formatTime(timeMs: Long): String {
    if (timeMs == 0L) return "--:--.---"
    val minutes = timeMs / 60000
    val seconds = (timeMs % 60000) / 1000
    val milliseconds = timeMs % 1000
    return "%d:%02d.%03d".format(minutes, seconds, milliseconds)
}
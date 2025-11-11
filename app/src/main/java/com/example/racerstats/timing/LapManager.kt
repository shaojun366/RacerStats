package com.example.racerstats.timing

data class LapData(
    val startTime: Long,
    var endTime: Long,
    var duration: Long,
    val checkpoints: MutableList<CheckpointData> = mutableListOf()
)

data class CheckpointData(
    var timestamp: Long,
    var distance: Float,  // 从起点的距离
    var speed: Float,     // m/s
    var latitude: Double,
    var longitude: Double
)

class LapManager {
    private var bestLap: LapData? = null
    private var currentLap: LapData? = null
    private val checkpointInterval = 10f  // 每10米记录一个检查点
    private var lastCheckpointDistance = 0f
    private var totalLapDistance = 0f  // 将在第一圈完成后设置
    
    fun startNewLap() {
        val startTime = System.currentTimeMillis()
        currentLap = LapData(startTime, 0, 0)
    }
    
    fun updateCurrentLap(
        timestamp: Long,
        distance: Float,
        speed: Float,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Long>? {
        val currentLap = currentLap ?: return null
        
        // 如果距离上次检查点已经超过间隔，记录新的检查点
        if (distance - lastCheckpointDistance >= checkpointInterval) {
            currentLap.checkpoints.add(CheckpointData(
                timestamp,
                distance,
                speed,
                latitude,
                longitude
            ))
            lastCheckpointDistance = distance
        }
        
        // 计算与最佳圈速的差值（delta）
        val delta = calculateDelta(distance, timestamp)
        
        // 预测完成时间
        val predictedTotal = predictTotalTime(distance, speed)
        
        return Pair(delta, predictedTotal)
    }
    
    private fun calculateDelta(currentDistance: Float, currentTime: Long): Double {
        bestLap?.let { best ->
            if (best.checkpoints.isEmpty() || currentLap?.checkpoints.isNullOrEmpty()) return 0.0
            
            // 找到最近的检查点进行比较
            val currentElapsed = currentTime - (currentLap?.startTime ?: return 0.0)
            val bestCheckpoint = best.checkpoints.firstOrNull { 
                it.distance >= currentDistance 
            } ?: return 0.0
            
            val bestElapsed = bestCheckpoint.timestamp - best.startTime
            return (currentElapsed - bestElapsed) / 1000.0  // 转换为秒
        }
        return 0.0
    }
    
    private fun predictTotalTime(currentDistance: Float, currentSpeed: Float): Long {
        if (totalLapDistance == 0f || currentSpeed <= 0) return 0L
        
        val currentLap = currentLap ?: return 0L
        val elapsed = System.currentTimeMillis() - currentLap.startTime
        val remainingDistance = totalLapDistance - currentDistance
        
        // 基于当前速度预测剩余时间
        val remainingTime = (remainingDistance / currentSpeed * 1000).toLong()
        return elapsed + remainingTime
    }
    
    fun completeLap(endTime: Long) {
        currentLap?.let { lap ->
            lap.endTime = endTime
            lap.duration = endTime - lap.startTime
            
            // 更新总圈距
            if (totalLapDistance == 0f && lap.checkpoints.isNotEmpty()) {
                totalLapDistance = lap.checkpoints.last().distance
            }
            
            // 更新最佳圈速
            if (bestLap == null || lap.duration < bestLap!!.duration) {
                bestLap = lap
            }
        }
        currentLap = null
        lastCheckpointDistance = 0f
    }
    
    fun getBestLapTime(): Long = bestLap?.duration ?: 0L
}
package com.example.racerstats.track.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val length: Float,
    val similarity: Float = 0.95f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_points")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val distance: Float,    // 从起点累计距离
    val sequence: Int       // 点位顺序
)

@Entity(tableName = "track_lines")
data class TrackLine(
    @PrimaryKey
    val trackId: String,
    // 起点线
    val startPoint1Lat: Double,
    val startPoint1Lon: Double,
    val startPoint2Lat: Double,
    val startPoint2Lon: Double,
    // 终点线（如果与起点线相同则为null）
    val finishPoint1Lat: Double? = null,
    val finishPoint1Lon: Double? = null,
    val finishPoint2Lat: Double? = null,
    val finishPoint2Lon: Double? = null
)

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)
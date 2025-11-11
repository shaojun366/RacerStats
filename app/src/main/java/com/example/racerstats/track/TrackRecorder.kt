package com.example.racerstats.track

import android.util.Log
import com.example.racerstats.track.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRecorder @Inject constructor(
    private val matcher: TrackMatcher
) {
    private var points = mutableListOf<GpsPoint>()
    private var distance = 0.0
    private var isRecording = false
    private var lastPoint: GpsPoint? = null
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState = _recordingState.asStateFlow()
    
    fun startRecording() {
        points.clear()
        distance = 0.0
        lastPoint = null
        isRecording = true
        _recordingState.value = RecordingState.Recording(0.0, 0)
    }
    
    fun stopRecording(): List<GpsPoint> {
        isRecording = false
        _recordingState.value = RecordingState.Idle
        return points.toList()
    }
    
    fun pauseRecording() {
        isRecording = false
        _recordingState.value = RecordingState.Paused(distance, points.size)
    }
    
    fun resumeRecording() {
        isRecording = true
        _recordingState.value = RecordingState.Recording(distance, points.size)
    }
    
    fun getPoints(): List<GpsPoint> = points.toList()

    fun addPoint(latitude: Double, longitude: Double, altitude: Double) {
        if (!isRecording) return
        
        val point = GpsPoint(latitude, longitude, altitude)
        lastPoint?.let { last ->
            distance += matcher.calculateDistance(last, point)
        }
        points.add(point)
        lastPoint = point
        
        _recordingState.value = RecordingState.Recording(distance, points.size)
    }
    
    sealed class RecordingState {
        object Idle : RecordingState()
        data class Recording(val distance: Double, val pointCount: Int) : RecordingState()
        data class Paused(val distance: Double, val pointCount: Int) : RecordingState()
    }
}
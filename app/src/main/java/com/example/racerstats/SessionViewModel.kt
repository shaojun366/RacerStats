package com.example.racerstats

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.example.racerstats.location.LocationManager
import com.example.racerstats.timing.LapManager
import com.example.racerstats.timing.SessionTimer
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// An activity-scoped model keeps timing and GPS state through view recreation.
class SessionViewModel(application: Application) : AndroidViewModel(application) {
    val location = LocationManager(application)
    val timer = SessionTimer { SystemClock.elapsedRealtime() }
    private val lapManager = LapManager()
    private val preferences = application.getSharedPreferences("lap_history", 0)
    val history = MutableLiveData<List<RecordedLap>>(readHistory())
    val sessionLaps = MutableLiveData<List<RecordedLap>>(emptyList())
    private var sessionId = UUID.randomUUID().toString()
    private var lastTimestamp: Long? = null
    private var distance = 0f
    private var lastFixAt = 0L
    private var gpsEnabled = false
    var speedKmh = 0f
        private set
    var maxSpeedKmh = 0f
        private set
    var comparison: Pair<Double, Long>? = null
        private set
    val hasFreshFix: Boolean get() = lastFixAt > 0 && SystemClock.elapsedRealtime() - lastFixAt < 5_000

    private val locationObserver = Observer<LocationManager.LocationData> { data ->
        speedKmh = data.speed * 3.6f
        lastFixAt = SystemClock.elapsedRealtime()
        if (!timer.isRunning) return@Observer
        maxSpeedKmh = maxOf(maxSpeedKmh, speedKmh)
        val previous = lastTimestamp
        if (previous != null && data.timestamp > previous) {
            val interval = data.timestamp - previous
            if (interval <= 5_000) distance += data.speed * interval / 1000f
        }
        lastTimestamp = data.timestamp
        comparison = lapManager.updateCurrentLap(
            timer.elapsed, distance, data.speed, data.latitude, data.longitude
        )
    }

    init { location.locationData.observeForever(locationObserver) }

    fun enableGps() {
        location.startUpdates()
        gpsEnabled = true
    }

    fun start() {
        timer.start()
        lapManager.reset()
        lapManager.startNewLap(0)
        sessionId = UUID.randomUUID().toString()
        sessionLaps.value = emptyList()
        distance = 0f
        maxSpeedKmh = 0f
        lastTimestamp = null
        comparison = null
    }

    fun pause() {
        timer.pause()
        lastTimestamp = null
    }

    fun resume() {
        timer.resume()
        lastTimestamp = null
    }

    fun recordLap() {
        if (!timer.isRunning || timer.lapElapsed <= 0) return
        val duration = timer.recordLap()
        val boundary = timer.lapStartedAt
        lapManager.completeLap(boundary)
        val lap = RecordedLap(UUID.randomUUID().toString(), sessionId,
            (sessionLaps.value?.size ?: 0) + 1, duration, System.currentTimeMillis())
        sessionLaps.value = sessionLaps.value.orEmpty() + lap
        history.value = (history.value.orEmpty() + lap).takeLast(500)
        persistHistory()
        lapManager.startNewLap(boundary)
        distance = 0f
        lastTimestamp = null
        comparison = null
    }

    fun stop() {
        timer.stop()
        lastTimestamp = null
        comparison = null
    }

    fun clearHistory() {
        history.value = emptyList()
        persistHistory()
    }

    fun resetMaxSpeed() { maxSpeedKmh = 0f }

    fun onBackground() {
        if (timer.isRunning) pause()
        location.stopLocationUpdates()
        lastFixAt = 0
        speedKmh = 0f
    }

    fun onForeground() {
        if (gpsEnabled) runCatching { location.startUpdates() }
    }

    private fun readHistory(): List<RecordedLap> = runCatching {
        val array = JSONArray(preferences.getString("laps", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RecordedLap(item.getString("id"), item.getString("session"), item.getInt("number"),
                item.getLong("duration"), item.getLong("created"))
        }
    }.getOrDefault(emptyList())

    private fun persistHistory() {
        val array = JSONArray()
        history.value.orEmpty().forEach { lap ->
            array.put(JSONObject().put("id", lap.id).put("session", lap.sessionId)
                .put("number", lap.number).put("duration", lap.duration).put("created", lap.createdAt))
        }
        preferences.edit().putString("laps", array.toString()).apply()
    }

    override fun onCleared() {
        location.locationData.removeObserver(locationObserver)
        location.release()
    }
}

data class RecordedLap(
    val id: String, val sessionId: String, val number: Int, val duration: Long, val createdAt: Long
)

fun formatLapTime(millis: Long): String = java.lang.String.format(java.util.Locale.US,
    "%02d:%02d.%03d", millis.coerceAtLeast(0) / 60000,
    millis.coerceAtLeast(0) % 60000 / 1000, millis.coerceAtLeast(0) % 1000)

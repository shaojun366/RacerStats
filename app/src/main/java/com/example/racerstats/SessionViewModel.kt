package com.example.racerstats

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.racerstats.location.LocationManager
import com.example.racerstats.timing.AutomaticLapTimer
import com.example.racerstats.timing.AutomaticTimingEvent
import com.example.racerstats.timing.CheckpointData
import com.example.racerstats.timing.LapManager
import com.example.racerstats.timing.SessionTimer
import com.example.racerstats.timing.TimingCourse
import com.example.racerstats.timing.TimingFix
import com.example.racerstats.track.TrackManager
import com.example.racerstats.track.model.TrackDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    application: Application,
    private val trackManager: TrackManager
) : AndroidViewModel(application) {
    val location = LocationManager(application)
    val timer = SessionTimer { SystemClock.elapsedRealtime() }
    private val lapManager = LapManager()
    private val preferences = application.getSharedPreferences("lap_history", 0)
    private val _history = MutableLiveData<List<RecordedLap>>(readHistory())
    val history: LiveData<List<RecordedLap>> = _history
    private val _sessionLaps = MutableLiveData<List<RecordedLap>>(emptyList())
    val sessionLaps: LiveData<List<RecordedLap>> = _sessionLaps
    val availableTracks = trackManager.getAllTracks().asLiveData()
    private val _selectedTrack = MutableLiveData<TrackDetails?>(null)
    val selectedTrack: LiveData<TrackDetails?> = _selectedTrack
    private val _selectingTrack = MutableLiveData(false)
    val selectingTrack: LiveData<Boolean> = _selectingTrack
    private val _notice = MutableLiveData<String?>(null)
    val notice: LiveData<String?> = _notice
    private var selectionJob: Job? = null
    private var selectionSequence = 0
    private var automaticTimer: AutomaticLapTimer? = null
    private var automaticArmed = false
    private var automaticPaused = false
    private var armedAt = 0L
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
    var gpsAccuracy: Float? = null
        private set
    val hasFreshFix: Boolean get() = lastFixAt > 0 && SystemClock.elapsedRealtime() - lastFixAt < 5_000
    val isAutomatic: Boolean get() = _selectedTrack.value != null
    val isSessionActive: Boolean get() = automaticArmed || automaticPaused || timer.isRunning || timer.isPaused
    val isPaused: Boolean get() = if (isAutomatic) automaticPaused else timer.isPaused
    val isWaitingForStart: Boolean get() = isAutomatic && automaticArmed && automaticTimer?.isTiming != true
    val canPause: Boolean get() = if (isAutomatic) automaticArmed else timer.isRunning

    private val locationObserver = Observer<LocationManager.LocationData>(::onLocationData)
    init { location.locationData.observeForever(locationObserver) }

    fun selectTrack(trackId: String?): Boolean {
        if (isSessionActive) {
            _notice.value = "请先结束当前计时，再切换赛道"
            return false
        }
        val request = ++selectionSequence
        selectionJob?.cancel()
        if (trackId == null) {
            _selectedTrack.value = null
            _selectingTrack.value = false
            return true
        }
        _selectingTrack.value = true
        selectionJob = viewModelScope.launch {
            try {
                val details = trackManager.getTrackDetails(trackId) ?: error("赛道已不存在")
                createAutomaticTimer(details) // Validate legacy or incomplete line configurations before arming.
                if (request == selectionSequence) _selectedTrack.value = details
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (request == selectionSequence) _notice.value = error.message ?: "无法加载赛道"
            } finally {
                if (request == selectionSequence) _selectingTrack.value = false
            }
        }
        return true
    }

    private fun createAutomaticTimer(details: TrackDetails): AutomaticLapTimer {
        val start = details.startLine ?: error("请先在赛道编辑页设置起点线")
        return AutomaticLapTimer(TimingCourse(start, details.finishLine, details.track.length))
    }

    fun consumeNotice() { _notice.value = null }

    fun enableGps() {
        location.startUpdates()
        gpsEnabled = true
    }

    fun start() {
        if (_selectingTrack.value == true || isSessionActive) return
        val selected = _selectedTrack.value
        if (selected == null) {
            beginSession(null)
            return
        }
        val request = ++selectionSequence
        _selectingTrack.value = true
        selectionJob = viewModelScope.launch {
            try {
                val latest = trackManager.getTrackDetails(selected.track.id) ?: error("赛道已删除，请重新选择")
                val engine = createAutomaticTimer(latest)
                if (request == selectionSequence) {
                    _selectedTrack.value = latest
                    beginSession(engine)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (request == selectionSequence) {
                    _selectedTrack.value = null
                    _notice.value = error.message ?: "无法开启赛道计时"
                }
            } finally {
                if (request == selectionSequence) _selectingTrack.value = false
            }
        }
    }

    private fun beginSession(engine: AutomaticLapTimer?) {
        lapManager.reset()
        sessionId = UUID.randomUUID().toString()
        _sessionLaps.value = emptyList()
        distance = 0f
        maxSpeedKmh = 0f
        lastTimestamp = null
        comparison = null
        if (engine == null) {
            timer.start()
            lapManager.startNewLap(0)
        } else {
            automaticTimer = engine
            automaticArmed = true
            automaticPaused = false
            armedAt = SystemClock.elapsedRealtime()
            timer.reset()
        }
    }

    fun pause() {
        if (isAutomatic) {
            if (!automaticArmed) return
            automaticArmed = false
            automaticPaused = true
            automaticTimer?.reset()
            timer.reset()
            comparison = null
        } else timer.pause()
        lastTimestamp = null
    }

    fun resume() {
        if (isAutomatic) {
            if (!automaticPaused) return
            automaticPaused = false
            automaticArmed = true
            armedAt = SystemClock.elapsedRealtime()
            automaticTimer?.reset()
            timer.reset()
        } else timer.resume()
        lastTimestamp = null
    }

    fun recordLap() {
        if (isAutomatic || !timer.isRunning || timer.lapElapsed <= 0) return
        val duration = timer.recordLap()
        val boundary = timer.lapStartedAt
        lapManager.completeLap(boundary)
        saveLap(duration, automatic = false)
        lapManager.startNewLap(boundary)
        distance = 0f
        lastTimestamp = null
        comparison = null
    }

    internal fun onLocationData(data: LocationManager.LocationData) {
        val receivedAt = SystemClock.elapsedRealtime()
        if (data.timestamp > receivedAt + 100 || receivedAt - data.timestamp > 5_000 ||
            data.timestamp <= (lastTimestamp ?: Long.MIN_VALUE)) return
        speedKmh = data.speed.takeIf { it.isFinite() && it >= 0 }?.times(3.6f) ?: 0f
        lastFixAt = data.timestamp
        gpsAccuracy = data.accuracy.takeIf { it.isFinite() && it > 0 }
        if (isAutomatic) {
            if (!automaticArmed || data.timestamp < armedAt) return
            processAutomaticFix(data)
            return
        }
        if (!timer.isRunning) return
        maxSpeedKmh = maxOf(maxSpeedKmh, speedKmh)
        val previous = lastTimestamp
        if (previous != null) {
            val interval = data.timestamp - previous
            if (interval in 1L..5_000L) distance += data.speed * interval / 1000f
        }
        lastTimestamp = data.timestamp
        comparison = lapManager.updateCurrentLap(timer.elapsed, distance, data.speed, data.latitude, data.longitude)
    }

    private fun processAutomaticFix(data: LocationManager.LocationData) {
        val engine = automaticTimer ?: return
        lastTimestamp = data.timestamp
        val fix = TimingFix(data.timestamp, data.latitude, data.longitude, data.speed, data.accuracy)
        for (event in engine.update(fix)) {
            when (event) {
                is AutomaticTimingEvent.Started -> {
                    timer.start(event.timestamp)
                    lapManager.startNewLap(event.timestamp)
                    comparison = null
                }
                is AutomaticTimingEvent.Completed -> {
                    lapManager.completeLap(event.timestamp, CheckpointData(event.timestamp, event.distance,
                        data.speed, event.point.latitude, event.point.longitude))
                    saveLap(event.duration, automatic = true)
                    if (event.continues) {
                        timer.start(event.timestamp)
                        lapManager.startNewLap(event.timestamp)
                    } else timer.reset()
                    comparison = null
                }
                AutomaticTimingEvent.Invalidated -> {
                    timer.reset()
                    comparison = null
                    _notice.value = "GPS 质量不足或定位中断，本圈未记成绩；请重新通过起点"
                }
            }
        }
        if (fix.isUsable && engine.isTiming) {
            maxSpeedKmh = maxOf(maxSpeedKmh, speedKmh)
            comparison = lapManager.updateCurrentLap(data.timestamp, engine.currentLapDistance,
                data.speed, data.latitude, data.longitude)
        }
    }

    private fun saveLap(duration: Long, automatic: Boolean) {
        if (duration <= 0) return
        val track = _selectedTrack.value?.track
        val lap = RecordedLap(UUID.randomUUID().toString(), sessionId,
            _sessionLaps.value.orEmpty().size + 1, duration, System.currentTimeMillis(),
            track?.id, track?.name, automatic)
        _sessionLaps.value = _sessionLaps.value.orEmpty() + lap
        _history.value = (_history.value.orEmpty() + lap).takeLast(500)
        persistHistory()
    }

    fun bestLapForTrack(trackId: String): Long? = _history.value.orEmpty()
        .filter { it.trackId == trackId && it.isAutomatic }.minOfOrNull { it.duration }

    fun stop() {
        cancelSelection()
        timer.stop()
        automaticTimer = null
        automaticArmed = false
        automaticPaused = false
        lastTimestamp = null
        comparison = null
    }

    fun clearHistory(trackId: String? = null, onlyManual: Boolean = false) {
        _history.value = _history.value.orEmpty().filterNot {
            if (onlyManual) it.trackId == null else trackId == null || it.trackId == trackId
        }
        persistHistory()
    }

    fun resetMaxSpeed() { maxSpeedKmh = 0f }

    fun onBackground() {
        cancelSelection()
        if (canPause) pause()
        location.stopLocationUpdates()
        lastFixAt = 0
        speedKmh = 0f
    }

    fun onForeground() {
        if (gpsEnabled) runCatching { location.startUpdates() }
    }

    private fun cancelSelection() {
        ++selectionSequence
        selectionJob?.cancel()
        selectionJob = null
        _selectingTrack.value = false
    }

    private fun readHistory(): List<RecordedLap> = runCatching {
        val array = JSONArray(preferences.getString("laps", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            RecordedLap(item.getString("id"), item.getString("session"), item.getInt("number"),
                item.getLong("duration"), item.getLong("created"), item.nullableString("trackId"),
                item.nullableString("trackName"), item.optBoolean("automatic", false))
                .takeIf { it.duration > 0 && it.number > 0 }
        }.getOrNull() }.takeLast(500)
    }.getOrDefault(emptyList())

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun persistHistory() {
        val array = JSONArray()
        _history.value.orEmpty().forEach { lap ->
            array.put(JSONObject().put("id", lap.id).put("session", lap.sessionId)
                .put("number", lap.number).put("duration", lap.duration).put("created", lap.createdAt)
                .put("trackId", lap.trackId ?: JSONObject.NULL).put("trackName", lap.trackName ?: JSONObject.NULL)
                .put("automatic", lap.isAutomatic))
        }
        preferences.edit().putString("laps", array.toString()).apply()
    }

    override fun onCleared() {
        location.locationData.removeObserver(locationObserver)
        location.release()
    }
}

data class RecordedLap(
    val id: String, val sessionId: String, val number: Int, val duration: Long, val createdAt: Long,
    val trackId: String? = null, val trackName: String? = null, val isAutomatic: Boolean = false
)

fun formatLapTime(millis: Long): String = java.lang.String.format(java.util.Locale.US,
    "%02d:%02d.%03d", millis.coerceAtLeast(0) / 60000,
    millis.coerceAtLeast(0) % 60000 / 1000, millis.coerceAtLeast(0) % 1000)

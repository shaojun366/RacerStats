package com.example.racerstats.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.racerstats.R
import com.example.racerstats.location.LocationManager as RacerLocationManager
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackBounds
import com.example.racerstats.track.model.TrackDetails
import com.example.racerstats.track.model.TrackEditEvent
import com.example.racerstats.track.model.TrackEditState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val trackManager: TrackManager,
    private val trackRecorder: TrackRecorder,
    private val locationManager: RacerLocationManager
) : ViewModel() {
    
    val tracks = trackManager.getAllTracks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    private val _currentTrackPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val currentTrackPoints = _currentTrackPoints.asStateFlow()
    
    private val _lineSettingState = MutableStateFlow<LineSettingState>(LineSettingState.NotSetting)
    val lineSettingState = _lineSettingState.asStateFlow()

    private val _linePreviewState = MutableStateFlow(LinePreviewState())
    val linePreviewState = _linePreviewState.asStateFlow()

    private val _trackDetails = MutableStateFlow<TrackDetails?>(null)
    val trackDetails = _trackDetails.asStateFlow()

    private val _trackEdit = MutableStateFlow(TrackEditState())
    val trackEdit = _trackEdit.asStateFlow()

    private val _events = MutableSharedFlow<TrackEditEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    
    // 临时存储起终点线的点位
    private var startLinePoint1: GpsPoint? = null
    private var startLinePoint2: GpsPoint? = null
    private var finishLinePoint1: GpsPoint? = null
    private var finishLinePoint2: GpsPoint? = null

    private var editingLine: EditingLine? = null
    private var editingFirstPoint: GpsPoint? = null
    
    val recordingState = trackRecorder.recordingState
    
    // 宽容度管理
    private val _currentTolerance = MutableStateFlow(TrackMatcher.THRESHOLD_STREET_CIRCUIT)
    val currentTolerance = _currentTolerance.asStateFlow()
    
    init {
        // 观察定位更新
        viewModelScope.launch {
            locationManager.locationUpdates.collectLatest { data ->
                if (recordingState.value is TrackRecorder.RecordingState.Recording) {
                    trackRecorder.addPoint(data.latitude, data.longitude, data.altitude)
                    updateCurrentTrackPoints()
                }
            }
        }
    }
    
    fun startRecording() {
        viewModelScope.launch {
            try {
                trackRecorder.startRecording()
                locationManager.startUpdates()
            } catch (e: Exception) {
                _events.emit(
                    TrackEditEvent.ShowError(
                        R.string.error_saving_track,
                        arrayOf("Failed to start GPS: ${e.message}")
                    )
                )
            }
        }
    }
    
    fun pauseRecording() {
        trackRecorder.pauseRecording()
    }
    
    fun resumeRecording() {
        trackRecorder.resumeRecording()
    }
    
    fun toggleLineSettingMode() {
        val currentState = _lineSettingState.value
        _lineSettingState.value = when (currentState) {
            is LineSettingState.NotSetting -> LineSettingState.SettingStartLine
            is LineSettingState.SettingStartLine -> {
                if (startLinePoint1 != null && startLinePoint2 != null) {
                    LineSettingState.SettingFinishLine
                } else {
                    currentState
                }
            }
            is LineSettingState.SettingFinishLine -> LineSettingState.NotSetting
        }
    }
    
    fun handleMapClick(point: GpsPoint) {
        when (val state = _lineSettingState.value) {
            is LineSettingState.SettingStartLine -> {
                if (startLinePoint1 == null) {
                    startLinePoint1 = point
                    _linePreviewState.update {
                        it.copy(startPointPreview = point)
                    }
                } else if (startLinePoint2 == null) {
                    startLinePoint2 = point
                    _linePreviewState.update {
                        it.copy(
                            startLine = Pair(startLinePoint1!!, startLinePoint2!!),
                            startPointPreview = null
                        )
                    }
                    _lineSettingState.value = LineSettingState.SettingFinishLine
                } else {
                    // 重新选择起点线
                    startLinePoint1 = point
                    startLinePoint2 = null
                    _linePreviewState.update {
                        it.copy(startLine = null, startPointPreview = point)
                    }
                }
            }
            is LineSettingState.SettingFinishLine -> {
                if (finishLinePoint1 == null) {
                    finishLinePoint1 = point
                    _linePreviewState.update {
                        it.copy(finishPointPreview = point)
                    }
                } else if (finishLinePoint2 == null) {
                    finishLinePoint2 = point
                    _linePreviewState.update {
                        it.copy(
                            finishLine = Pair(finishLinePoint1!!, finishLinePoint2!!),
                            finishPointPreview = null
                        )
                    }
                    _lineSettingState.value = LineSettingState.NotSetting
                } else {
                    // 重新选择终点线
                    finishLinePoint1 = point
                    finishLinePoint2 = null
                    _linePreviewState.update {
                        it.copy(finishLine = null, finishPointPreview = point)
                    }
                }
            }
            else -> {}
        }
    }
    
    fun saveTrack(name: String) {
        viewModelScope.launch {
            try {
                val points = trackRecorder.stopRecording()
                locationManager.stopLocationUpdates()
                
                if (startLinePoint1 == null || startLinePoint2 == null) {
                    _events.emit(TrackEditEvent.ShowError(R.string.error_start_line_not_set))
                    return@launch
                }
                
                val startLine = Pair(startLinePoint1!!, startLinePoint2!!)
                val finishLine = if (finishLinePoint1 != null && finishLinePoint2 != null) {
                    Pair(finishLinePoint1!!, finishLinePoint2!!)
                } else {
                    null
                }
                
                trackManager.saveTrack(name, points, startLine, finishLine)
                
                // 重置状态
                resetState()
                _events.emit(TrackEditEvent.ShowMessage(R.string.track_saved))
            } catch (e: Exception) {
                _events.emit(
                    TrackEditEvent.ShowError(
                        R.string.error_saving_track,
                        arrayOf(e.message ?: "Unknown error")
                    )
                )
            }
        }
    }
    
    private fun resetState() {
        _currentTrackPoints.value = emptyList()
        _lineSettingState.value = LineSettingState.NotSetting
        _linePreviewState.value = LinePreviewState()
        startLinePoint1 = null
        startLinePoint2 = null
        finishLinePoint1 = null
        finishLinePoint2 = null
        editingLine = null
        editingFirstPoint = null
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            try {
                trackManager.deleteTrack(trackId)
            } catch (e: Exception) {
                // TODO: 处理删除错误
            }
        }
    }
    
    fun loadTrackDetails(trackId: String) {
        viewModelScope.launch {
            val details = trackManager.getTrackDetails(trackId)
            _trackDetails.value = details
        }
    }

    fun loadTrackForEdit(trackId: String) {
        viewModelScope.launch {
            _trackEdit.update { it.copy(isLoading = true) }
            val details = trackManager.getTrackDetails(trackId)
            if (details == null) {
                _events.emit(TrackEditEvent.ShowError(R.string.error_saving_track, arrayOf("Track not found")))
                _trackEdit.value = TrackEditState(isLoading = false)
                return@launch
            }

            val hasSeparateFinish = details.finishLine != null && details.finishLine != details.startLine
            _trackEdit.value = TrackEditState(
                trackId = details.track.id,
                name = details.track.name,
                startLine = details.startLine,
                finishLine = if (hasSeparateFinish) details.finishLine else null,
                hasSeparateFinishLine = hasSeparateFinish,
                trackBounds = calculateBounds(details.points),
                isLoading = false
            )
        }
    }

    fun updateTrackName(name: String) {
        _trackEdit.update { it.copy(name = name) }
    }

    fun setSeparateFinishLine(isSeparate: Boolean) {
        _trackEdit.update { state ->
            if (!isSeparate) {
                state.copy(hasSeparateFinishLine = false, finishLine = null)
            } else {
                state.copy(hasSeparateFinishLine = true)
            }
        }
    }

    fun startSettingStartLine() {
        editingLine = EditingLine.START
        editingFirstPoint = null
        _trackEdit.update { it.copy(isSettingLine = true) }
    }

    fun startSettingFinishLine() {
        _trackEdit.update { state ->
            if (!state.hasSeparateFinishLine) state else {
                editingLine = EditingLine.FINISH
                editingFirstPoint = null
                state.copy(isSettingLine = true)
            }
        }
    }

    fun handleStartLineClick(point: GpsPoint) {
        if (editingLine != EditingLine.START) return
        handleEditingPoint(point) { first, second ->
            _trackEdit.update { it.copy(startLine = Pair(first, second), isSettingLine = false) }
        }
    }

    fun handleFinishLineClick(point: GpsPoint) {
        if (editingLine != EditingLine.FINISH) return
        handleEditingPoint(point) { first, second ->
            _trackEdit.update { it.copy(finishLine = Pair(first, second), isSettingLine = false) }
        }
    }

    fun saveTrack() {
        val state = _trackEdit.value
        val trackId = state.trackId
        if (trackId.isNullOrBlank()) {
            viewModelScope.launch {
                _events.emit(TrackEditEvent.ShowError(R.string.error_saving_track, arrayOf("Missing track id")))
            }
            return
        }

        if (state.name.isBlank()) {
            viewModelScope.launch {
                _events.emit(TrackEditEvent.ShowError(R.string.error_track_name_empty))
            }
            return
        }

        val startLine = state.startLine
        if (startLine == null) {
            viewModelScope.launch {
                _events.emit(TrackEditEvent.ShowError(R.string.error_start_line_not_set))
            }
            return
        }

        val finishLine = if (state.hasSeparateFinishLine) {
            state.finishLine ?: run {
                viewModelScope.launch {
                    _events.emit(TrackEditEvent.ShowError(R.string.error_finish_line_not_set))
                }
                return
            }
        } else {
            null
        }

        viewModelScope.launch {
            try {
                trackManager.updateTrack(trackId, state.name, startLine, finishLine)
                _events.emit(TrackEditEvent.ShowMessage(R.string.track_saved))
                _events.emit(TrackEditEvent.NavigateBack)
            } catch (e: Exception) {
                _events.emit(
                    TrackEditEvent.ShowError(
                        R.string.error_saving_track,
                        arrayOf(e.message ?: "Unknown error")
                    )
                )
            }
        }
    }


    
    private fun updateCurrentTrackPoints() {
        _currentTrackPoints.value = trackRecorder.getPoints()
    }

    private fun calculateBounds(points: List<GpsPoint>): TrackBounds? {
        if (points.isEmpty()) return null
        var minLat = points.first().latitude
        var maxLat = points.first().latitude
        var minLon = points.first().longitude
        var maxLon = points.first().longitude

        for (point in points) {
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }

        return TrackBounds(
            southwest = GpsPoint(minLat, minLon),
            northeast = GpsPoint(maxLat, maxLon)
        )
    }

    private fun handleEditingPoint(point: GpsPoint, onComplete: (GpsPoint, GpsPoint) -> Unit) {
        val firstPoint = editingFirstPoint
        if (firstPoint == null) {
            editingFirstPoint = point
        } else {
            onComplete(firstPoint, point)
            editingLine = null
            editingFirstPoint = null
        }
    }
    
    sealed class LineSettingState {
        object NotSetting : LineSettingState()
        object SettingStartLine : LineSettingState()
        object SettingFinishLine : LineSettingState()
    }

    private enum class EditingLine { START, FINISH }
    
    fun getCurrentTolerance(): Double {
        return _currentTolerance.value
    }
    
    fun setTolerance(tolerance: Double) {
        _currentTolerance.value = tolerance
    }
}

data class LinePreviewState(
    val startLine: Pair<GpsPoint, GpsPoint>? = null,
    val startPointPreview: GpsPoint? = null,
    val finishLine: Pair<GpsPoint, GpsPoint>? = null,
    val finishPointPreview: GpsPoint? = null
)

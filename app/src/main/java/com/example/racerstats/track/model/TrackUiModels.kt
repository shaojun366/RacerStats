package com.example.racerstats.track.model

/**
 * Aggregated data objects used by track detail and edit screens.
 */
import androidx.annotation.StringRes

data class TrackDetails(
    val track: Track,
    val points: List<GpsPoint>,
    val startLine: Pair<GpsPoint, GpsPoint>?,
    val finishLine: Pair<GpsPoint, GpsPoint>?,
    val bestLapTimeMillis: Long? = null
)

data class TrackBounds(
    val southwest: GpsPoint,
    val northeast: GpsPoint
)

data class TrackEditState(
    val trackId: String? = null,
    val name: String = "",
    val startLine: Pair<GpsPoint, GpsPoint>? = null,
    val finishLine: Pair<GpsPoint, GpsPoint>? = null,
    val hasSeparateFinishLine: Boolean = false,
    val isSettingLine: Boolean = false,
    val trackBounds: TrackBounds? = null,
    val isLoading: Boolean = false
)

sealed class TrackEditEvent {
    object NavigateBack : TrackEditEvent()
    data class ShowError(@StringRes val messageRes: Int, val formatArgs: Array<Any?> = emptyArray()) : TrackEditEvent()
    data class ShowMessage(@StringRes val messageRes: Int, val formatArgs: Array<Any?> = emptyArray()) : TrackEditEvent()
}

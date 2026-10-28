package com.example.racerstats.history

import com.example.racerstats.RecordedLap

data class LapHistorySummary(
    val laps: List<RecordedLap>, val bestTime: Long?, val comparable: Boolean, val manualOnly: Boolean
)

fun summarizeLaps(laps: List<RecordedLap>, trackId: String? = null, manualOnly: Boolean = false): LapHistorySummary {
    val filtered = laps.filter { if (manualOnly) it.trackId == null else trackId == null || it.trackId == trackId }
    val groups = filtered.map { it.trackId }.distinct()
    val comparable = trackId != null || manualOnly || groups.size == 1
    return LapHistorySummary(filtered, if (comparable) filtered.minOfOrNull { it.duration } else null,
        comparable, manualOnly || (groups.size == 1 && groups.single() == null))
}

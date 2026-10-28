package com.example.racerstats.history

import com.example.racerstats.RecordedLap
import org.junit.Assert.*
import org.junit.Test

class LapHistorySummaryTest {
    private val manual = RecordedLap("manual", "session-0", 1, 1000, 0)
    private val first = RecordedLap("first", "session-1", 1, 2000, 1, "track-a", "A", true)
    private val second = RecordedLap("second", "session-2", 1, 3000, 2, "track-b", "B", true)

    @Test fun differentTracksNeverProduceAMisleadingOverallBest() {
        val summary = summarizeLaps(listOf(manual, first, second))
        assertEquals(3, summary.laps.size)
        assertFalse(summary.comparable)
        assertNull(summary.bestTime)
    }

    @Test fun trackFilterUsesIdentityRatherThanTheDisplayedName() {
        val renamed = first.copy(id = "renamed", trackName = "New name", duration = 1500)
        val summary = summarizeLaps(listOf(manual, first, renamed, second), "track-a")
        assertEquals(2, summary.laps.size)
        assertEquals(1500L, summary.bestTime)
        assertTrue(summary.comparable)
    }

    @Test fun legacyRecordsRemainInTheManualFilter() {
        val summary = summarizeLaps(listOf(manual, first), manualOnly = true)
        assertEquals(listOf(manual), summary.laps)
        assertTrue(summary.manualOnly)
        assertEquals(1000L, summary.bestTime)
    }
}

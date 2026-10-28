package com.example.racerstats.track

import com.example.racerstats.track.dao.TrackDao
import com.example.racerstats.track.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TrackManagerTest {
    private val dao = MemoryDao()
    private val manager = TrackManager(dao, TrackMatcher())
    private val points = (0..14).map { GpsPoint(if (it % 2 == 0) 0.0 else 0.0003, it * 0.0003) }
    private val line = GpsPoint(0.0, 0.0) to GpsPoint(0.0001, 0.0)

    @Test fun savesUsingOneSnapshotOfAnObservableFlow() = runBlocking {
        withTimeout(2_000) { manager.saveTrack("First", points, line) }
        val saved = dao.tracks.value.single()
        val savedPoints = dao.getTrackPoints(saved.id)
        assertEquals("First", saved.name)
        assertEquals(0f, savedPoints.first().distance)
        assertEquals(saved.length, savedPoints.last().distance, 0.01f)
        assertNotNull(dao.getTrackLine(saved.id))
    }

    @Test fun matchingTrackUpdatesMetadataAndReplacesPoints() = runBlocking {
        manager.saveTrack("First", points, line)
        val id = dao.tracks.value.single().id
        val match = withTimeout(2_000) { manager.saveTrack("Renamed", points, line) }
        assertEquals(id, match)
        assertEquals("Renamed", dao.tracks.value.single().name)
        assertEquals(15, dao.getTrackPoints(id).size)
    }

    @Test fun failedValidationDoesNotWritePartialTrack() = runBlocking {
        try {
            manager.saveTrack("Short", listOf(GpsPoint(0.0, 0.0), GpsPoint(0.0, 0.00001)), line)
            fail("Expected minimum-length validation")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.tracks.value.isEmpty())
    }

    private class MemoryDao : TrackDao {
        val tracks = MutableStateFlow<List<Track>>(emptyList())
        private val points = mutableMapOf<String, List<TrackPoint>>()
        private val lines = mutableMapOf<String, TrackLine>()
        override fun getAllTracks() = tracks
        override suspend fun getTrackById(trackId: String) = tracks.value.find { it.id == trackId }
        override suspend fun getTrackPoints(trackId: String) = points[trackId].orEmpty()
        override suspend fun getTrackLine(trackId: String) = lines[trackId]
        override suspend fun insertTrack(track: Track) { tracks.value = tracks.value.filterNot { it.id == track.id } + track }
        override suspend fun insertPoints(points: List<TrackPoint>) { points.groupBy { it.trackId }.forEach { (id, list) -> this.points[id] = list } }
        override suspend fun insertTrackLine(trackLine: TrackLine) { lines[trackLine.trackId] = trackLine }
        override suspend fun deleteTrack(track: Track) { tracks.value = tracks.value.filterNot { it.id == track.id } }
        override suspend fun deleteTrackPoints(trackId: String) { points.remove(trackId) }
        override suspend fun deleteTrackLine(trackId: String) { lines.remove(trackId) }
    }
}

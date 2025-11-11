package com.example.racerstats.track.dao

import androidx.room.*
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackLine
import com.example.racerstats.track.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun getAllTracks(): Flow<List<Track>>
    
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): Track?
    
    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY sequence")
    suspend fun getTrackPoints(trackId: String): List<TrackPoint>
    
    @Query("SELECT * FROM track_lines WHERE trackId = :trackId")
    suspend fun getTrackLine(trackId: String): TrackLine?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPoint>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackLine(trackLine: TrackLine)
    
    @Delete
    suspend fun deleteTrack(track: Track)
    
    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deleteTrackPoints(trackId: String)
    
    @Query("DELETE FROM track_lines WHERE trackId = :trackId")
    suspend fun deleteTrackLine(trackId: String)
    
    @Transaction
    suspend fun deleteTrackWithDetails(track: Track) {
        deleteTrackPoints(track.id)
        deleteTrackLine(track.id)
        deleteTrack(track)
    }
}
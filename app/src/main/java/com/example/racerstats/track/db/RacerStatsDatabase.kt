package com.example.racerstats.track.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.racerstats.track.dao.TrackDao
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackLine
import com.example.racerstats.track.model.TrackPoint

@Database(
    entities = [Track::class, TrackPoint::class, TrackLine::class],
    version = 1,
    exportSchema = false
)
abstract class RacerStatsDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}

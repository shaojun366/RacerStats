package com.example.racerstats.di

import android.content.Context
import androidx.room.Room
import com.example.racerstats.track.dao.TrackDao
import com.example.racerstats.track.db.RacerStatsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RacerStatsDatabase {
        return Room.databaseBuilder(
            context,
            RacerStatsDatabase::class.java,
            "racerstats.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTrackDao(
        database: RacerStatsDatabase
    ): TrackDao = database.trackDao()
}

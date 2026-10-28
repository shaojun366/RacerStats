package com.example.racerstats

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.racerstats.track.TrackManager
import com.example.racerstats.track.TrackMatcher
import com.example.racerstats.track.db.RacerStatsDatabase
import com.example.racerstats.track.model.GpsPoint
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RacerStatsRegressionTest {
    @Test fun timingSurvivesRecreationAndReviewShowsRecordedLaps() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertVisible(scenario, R.id.tvLapTime)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                model.clearHistory()
                model.start()
            }
            SystemClock.sleep(100)
            scenario.onActivity { activity -> ViewModelProvider(activity)[SessionViewModel::class.java].recordLap() }
            scenario.recreate()
            assertVisible(scenario, R.id.tvLapTime)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                assertTrue(model.timer.isRunning)
                assertEquals(1, model.sessionLaps.value!!.size)
                assertEquals(1, model.history.value!!.size)
                activity.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_review
            }
            assertVisible(scenario, R.id.tvTotalLaps)
            scenario.onActivity { activity ->
                assertEquals("1", activity.findViewById<android.widget.TextView>(R.id.tvTotalLaps).text.toString())
            }
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_track
            }
            assertVisible(scenario, R.id.fabAddTrack)
            scenario.onActivity { it.findViewById<android.view.View>(R.id.fabAddTrack).performClick() }
            assertVisible(scenario, R.id.btnStartFinish)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertVisible(scenario, R.id.fabAddTrack)
            scenario.onActivity { activity -> ViewModelProvider(activity)[SessionViewModel::class.java].stop() }
        }
    }

    private fun assertVisible(scenario: ActivityScenario<MainActivity>, id: Int) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val view = activity.findViewById<android.view.View>(id)
            assertNotNull("Missing view $id", view)
            assertTrue("View $id is hidden", view.isShown)
        }
    }

    @Test fun roomTrackSaveCompletesAndDeleteRemovesAllDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RacerStatsDatabase::class.java).build()
        try {
            val dao = db.trackDao()
            val manager = TrackManager(dao, TrackMatcher())
            val points = (0..14).map { GpsPoint(if (it % 2 == 0) 0.0 else 0.0003, it * 0.0003) }
            val line = GpsPoint(0.0, 0.0) to GpsPoint(0.0001, 0.0)
            withTimeout(5_000) { manager.saveTrack("Regression track", points, line) }
            val saved = dao.getAllTracks().first().single()
            assertEquals(15, dao.getTrackPoints(saved.id).size)
            assertNotNull(dao.getTrackLine(saved.id))
            manager.deleteTrack(saved.id)
            assertTrue(dao.getAllTracks().first().isEmpty())
            assertTrue(dao.getTrackPoints(saved.id).isEmpty())
            assertNull(dao.getTrackLine(saved.id))
        } finally { db.close() }
    }
}

package com.example.racerstats

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.racerstats.location.LocationManager
import com.example.racerstats.track.db.RacerStatsDatabase
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackLine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import android.graphics.Bitmap
import java.io.File
import com.google.android.material.bottomnavigation.BottomNavigationView

@RunWith(AndroidJUnit4::class)
class AutomaticTimingIntegrationTest {
    @Test fun legacyHistorySurvivesUpgradeAndSkipsOnlyMalformedEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("lap_history", 0)
        preferences.edit().putString("laps", JSONArray()
            .put(JSONObject().put("id", "legacy").put("session", "legacy-session").put("number", 1)
                .put("duration", 12345).put("created", 1000))
            .put(JSONObject().put("id", "invalid-entry")).toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                val lap = model.history.value!!.single()
                assertEquals(12345L, lap.duration)
                assertNull(lap.trackId)
                assertFalse(lap.isAutomatic)
                model.start()
            }
            SystemClock.sleep(20)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                model.recordLap()
                model.stop()
                assertEquals(2, model.history.value!!.size)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                assertEquals(2, model.history.value!!.size)
                assertEquals(12345L, model.history.value!!.first().duration)
                model.clearHistory()
            }
        }
    }

    @Test fun crossingARealSavedTrackRecordsIdentityAndPauseDiscardsThePartialLap(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, RacerStatsDatabase::class.java, "racerstats.db").build()
        val track = Track(id = UUID.randomUUID().toString(), name = "自动计时测试赛道", length = 100f)
        val metres = 111194.92664455873
        val line = TrackLine(track.id, 0.0, -10 / metres, 0.0, 10 / metres)
        db.trackDao().saveTrackWithDetails(track, emptyList(), line)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                    model.clearHistory()
                    assertTrue(model.selectTrack(track.id))
                }
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(20)
                    scenario.onActivity { activity ->
                        val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                        ready = model.selectingTrack.value == false && model.selectedTrack.value?.track?.id == track.id
                    }
                }
                assertTrue("Saved track did not load", ready)
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                    model.start()
                }
                awaitModel(scenario) { it.isWaitingForStart }
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                    assertTrue(model.isWaitingForStart)
                    assertFalse(model.selectTrack(null)) // Cannot change identity during an active session.
                    val now = SystemClock.elapsedRealtime()
                    model.onLocationData(LocationManager.LocationData(now - 2000, -5 / metres, 0.0,
                        20f, 1f, 0.0, 0f, LocationManager.Source.PHONE_GPS))
                    model.onLocationData(LocationManager.LocationData(now - 1000, 5 / metres, 0.0,
                        20f, 1f, 0.0, 0f, LocationManager.Source.PHONE_GPS))
                    assertTrue("Cached fixes must not start timing before arming", model.isWaitingForStart)
                }
                val path = listOf(0.0 to -5.0, 0.0 to 5.0, 35.0 to 5.0, 35.0 to -5.0, 0.0 to -5.0, 0.0 to 5.0)
                path.forEachIndexed { index, (x, y) ->
                    if (index > 0) SystemClock.sleep(800)
                    scenario.onActivity { activity ->
                        ViewModelProvider(activity)[SessionViewModel::class.java].onLocationData(
                            LocationManager.LocationData(SystemClock.elapsedRealtime(), y / metres, x / metres,
                                20f, 1f, 0.0, 0f, LocationManager.Source.PHONE_GPS))
                    }
                }
                SystemClock.sleep(100)
                capturePreview("automatic-live.png")
                scenario.onActivity { activity ->
                    activity.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_review
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                SystemClock.sleep(100)
                capturePreview("automatic-review.png")
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[SessionViewModel::class.java]
                    val lap = model.history.value!!.single()
                    assertEquals(track.id, lap.trackId)
                    assertEquals(track.name, lap.trackName)
                    assertTrue(lap.isAutomatic)
                    assertTrue(lap.duration >= 3_000)
                    assertEquals(lap.duration, model.bestLapForTrack(track.id))
                    model.pause()
                    assertTrue(model.isPaused)
                    model.resume()
                    assertTrue(model.isWaitingForStart)
                    model.recordLap() // Manual button cannot inject an automatic result.
                    assertEquals(1, model.history.value!!.size)
                    model.clearHistory(onlyManual = true)
                    assertEquals(1, model.history.value!!.size)
                    model.clearHistory(track.id)
                    assertTrue(model.history.value!!.isEmpty())
                    model.stop()
                }
                db.trackDao().insertTrack(track.copy(name = "改名后的测试赛道"))
                scenario.onActivity { activity -> ViewModelProvider(activity)[SessionViewModel::class.java].start() }
                awaitModel(scenario) { it.isWaitingForStart && it.selectedTrack.value?.track?.name == "改名后的测试赛道" }
                scenario.onActivity { activity -> ViewModelProvider(activity)[SessionViewModel::class.java].stop() }
                db.trackDao().deleteTrackWithDetails(track)
                scenario.onActivity { activity -> ViewModelProvider(activity)[SessionViewModel::class.java].start() }
                awaitModel(scenario) { it.selectingTrack.value == false && it.selectedTrack.value == null }
                scenario.onActivity { activity ->
                    assertFalse(ViewModelProvider(activity)[SessionViewModel::class.java].isSessionActive)
                }
            }
        } finally {
            db.trackDao().deleteTrackWithDetails(track)
            db.close()
        }
    }

    private fun capturePreview(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Cannot capture rendered application", bitmap)
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun awaitModel(scenario: ActivityScenario<MainActivity>, predicate: (SessionViewModel) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20)
            scenario.onActivity { ready = predicate(ViewModelProvider(it)[SessionViewModel::class.java]) }
        }
        assertTrue("Session model did not reach expected state", ready)
    }
}

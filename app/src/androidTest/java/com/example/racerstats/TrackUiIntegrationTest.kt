package com.example.racerstats

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.racerstats.maps.MapSupport
import com.example.racerstats.timing.gpsDistance
import com.example.racerstats.track.db.RacerStatsDatabase
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.Track
import com.example.racerstats.track.model.TrackLine
import com.example.racerstats.track.model.TrackPoint
import com.example.racerstats.track.ui.TrackListAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercises the real navigation, persisted Room data and optional Maps configuration. */
@RunWith(AndroidJUnit4::class)
class TrackUiIntegrationTest {
    @Test fun editingSurvivesRotationRefreshesDetailsAndDeletionCompletesBeforeReturning(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.databaseBuilder(context, RacerStatsDatabase::class.java, "racerstats.db").build()
        val id = UUID.randomUUID().toString()
        val metresPerDegree = 111194.92664455873
        // Synthetic course near (0, 0); never use a person's actual location in repository previews.
        val offsets = listOf(
            -20.0 to 0.0, 0.0 to 0.0, 170.0 to 0.0, 290.0 to 20.0,
            350.0 to 70.0, 380.0 to 150.0, 355.0 to 215.0, 290.0 to 250.0,
            220.0 to 250.0, 180.0 to 195.0, 170.0 to 145.0, 115.0 to 125.0,
            60.0 to 165.0, 5.0 to 140.0, -25.0 to 65.0, -20.0 to 0.0
        )
        val points = offsets.map { (x, y) -> GpsPoint(y / metresPerDegree, x / metresPerDegree) }
        var distance = 0f
        val storedPoints = points.mapIndexed { index, point ->
            if (index > 0) distance += gpsDistance(points[index - 1], point).toFloat()
            TrackPoint(trackId = id, latitude = point.latitude, longitude = point.longitude,
                altitude = 0.0, distance = distance, sequence = index)
        }
        val track = Track(id = id, name = "示例环线 · 模拟数据", length = distance)
        val line = TrackLine(id, -12 / metresPerDegree, 0.0, 12 / metresPerDegree, 0.0)
        val renamed = "示例环线 · 模拟数据 V2"
        db.trackDao().saveTrackWithDetails(track, storedPoints, line)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_track
                }
                awaitUi(scenario, "Saved course did not appear in library") { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.rvTracks)
                    val adapter = list?.adapter as? TrackListAdapter
                    val index = adapter?.currentList?.indexOfFirst { it.id == id } ?: -1
                    index >= 0 && list.findViewHolderForAdapterPosition(index)?.itemView?.isShown == true
                }
                capturePreview("track-library.png")
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.rvTracks)
                    val adapter = list.adapter as TrackListAdapter
                    val index = adapter.currentList.indexOfFirst { it.id == id }
                    list.findViewHolderForAdapterPosition(index)!!.itemView.performClick()
                }
                awaitUi(scenario, "Course detail did not load") {
                    it.findViewById<TextView>(R.id.tvTrackName)?.text?.toString() == track.name &&
                        it.findViewById<View>(R.id.btnStartSession)?.isEnabled == true
                }
                scenario.onActivity { activity ->
                    assertActionOnScreen(activity, R.id.btnStartSession)
                    assertEquals(context.getString(R.string.track_created_short,
                        java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(track.createdAt))),
                        activity.findViewById<TextView>(R.id.tvLastUsed).text.toString())
                    if (!MapSupport.isConfigured(context)) {
                        assertTrue(activity.findViewById<View>(R.id.routePreview).isShown)
                        assertTrue(activity.findViewById<View>(R.id.mapStatus).isShown)
                        assertEquals(View.GONE, activity.findViewById<View>(R.id.mapView).visibility)
                    }
                }
                capturePreview("track-detail.png")
                scenario.onActivity {
                    assertTrue(it.findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.action_edit, 0))
                }
                awaitUi(scenario, "Editor did not load the saved name") {
                    it.findViewById<EditText>(R.id.etName)?.let { field ->
                        field.isEnabled && field.text.toString() == track.name
                    } == true
                }
                scenario.onActivity { activity ->
                    if (!MapSupport.isConfigured(context)) {
                        assertTrue(activity.findViewById<View>(R.id.routePreviewStart).isShown)
                        assertFalse(activity.findViewById<View>(R.id.btnSetStartLine).isEnabled)
                        assertFalse(activity.findViewById<View>(R.id.btnSetFinishLine).isEnabled)
                        assertFalse(activity.findViewById<View>(R.id.cbSeparateFinishLine).isEnabled)
                    }
                    activity.findViewById<EditText>(R.id.etName).setText(renamed)
                }
                scenario.recreate()
                awaitUi(scenario, "Unsaved name was lost after activity recreation") {
                    it.findViewById<EditText>(R.id.etName)?.text?.toString() == renamed
                }
                scenario.onActivity { activity ->
                    val field = activity.findViewById<EditText>(R.id.etName)
                    field.clearFocus()
                    activity.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(field.windowToken, 0)
                    assertTrue(activity.findViewById<View>(R.id.fabSave).isEnabled)
                    assertActionOnScreen(activity, R.id.fabSave)
                }
                capturePreview("track-edit.png")
                scenario.onActivity { it.findViewById<View>(R.id.fabSave).performClick() }
                awaitUi(scenario, "Saved edit was not shown on returning to detail") {
                    it.findViewById<TextView>(R.id.tvTrackName)?.text?.toString() == renamed &&
                        it.findViewById<View>(R.id.btnStartSession)?.isShown == true
                }
                assertEquals(renamed, db.trackDao().getTrackById(id)?.name)
                assertEquals(storedPoints.size, db.trackDao().getTrackPoints(id).size)
                assertEquals(line, db.trackDao().getTrackLine(id))

                scenario.onActivity {
                    assertTrue(it.findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.action_delete, 0))
                }
                clickDialogPositiveButton(scenario)
                awaitUi(scenario, "Successful deletion did not return to library") {
                    it.findViewById<View>(R.id.fabAddTrack)?.isShown == true
                }
                // Navigation must happen after the transaction, so these reads need no retry.
                assertNull(db.trackDao().getTrackById(id))
                assertTrue(db.trackDao().getTrackPoints(id).isEmpty())
                assertNull(db.trackDao().getTrackLine(id))

                scenario.onActivity { it.findViewById<View>(R.id.fabAddTrack).performClick() }
                awaitUi(scenario, "Recording screen did not appear") {
                    it.findViewById<View>(R.id.btnStartFinish)?.isShown == true
                }
                scenario.onActivity { activity ->
                    assertActionOnScreen(activity, R.id.btnStartFinish)
                    if (!MapSupport.isConfigured(context)) {
                        assertTrue(activity.findViewById<View>(R.id.routePreview).isShown)
                        assertTrue(activity.findViewById<View>(R.id.mapStatus).isShown)
                        assertFalse(activity.findViewById<View>(R.id.btnSetLine).isEnabled)
                    }
                }
                SystemClock.sleep(2_000) // Allow the earlier save confirmation to disappear.
                capturePreview("track-recording.png")
            }
        } finally {
            db.trackDao().deleteTrackWithDetails(track)
            db.close()
        }
    }

    private fun awaitUi(scenario: ActivityScenario<MainActivity>, message: String, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 7_000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(30)
            scenario.onActivity { ready = predicate(it) }
        }
        assertTrue(message, ready)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertActionOnScreen(activity: MainActivity, id: Int) {
        val view = activity.findViewById<View>(id)
        val visible = android.graphics.Rect()
        assertTrue("Primary action is hidden", view.getGlobalVisibleRect(visible))
        assertEquals("Primary action is clipped vertically", view.height, visible.height())
        assertEquals("Primary action is clipped horizontally", view.width, visible.width())
    }

    private fun clickDialogPositiveButton(scenario: ActivityScenario<MainActivity>) {
        // Locate the app's actual dialog window without depending on accessibility snapshot timing.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            var clicked = false
            awaitUi(scenario, "Delete confirmation button was not available") {
                val button = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { window -> window.findViewById<android.widget.Button>(android.R.id.button1) }
                    .firstOrNull { it.isShown && it.isEnabled }
                if (!clicked && button != null) clicked = button.performClick()
                clicked
            }
            return
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var clicked = false
        while (!clicked && SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            val buttons = root?.findAccessibilityNodeInfosByViewId("android:id/button1").orEmpty()
            val positive = buttons.firstOrNull { it.isEnabled && it.isClickable }
            if (positive != null) clicked = positive.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            buttons.forEach { it.recycle() }
            root?.recycle()
            if (!clicked) SystemClock.sleep(30)
        }
        assertTrue("Delete confirmation button was not available", clicked)
    }

    private fun capturePreview(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250) // Let navigation and Material state animations finish before capture.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Cannot capture rendered application", bitmap)
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

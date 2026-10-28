package com.example.racerstats.track

import org.junit.Assert.*
import org.junit.Test

class TrackRecorderTest {
    private val recorder = TrackRecorder(TrackMatcher())

    @Test fun pausedMovementDoesNotAddDistanceWhenRecordingResumes() {
        recorder.startRecording()
        recorder.addPoint(0.0, 0.0, 0.0)
        recorder.addPoint(0.0, 0.001, 0.0)
        val before = (recorder.recordingState.value as TrackRecorder.RecordingState.Recording).distance
        recorder.pauseRecording()
        recorder.addPoint(1.0, 1.0, 0.0)
        recorder.resumeRecording()
        recorder.addPoint(0.0, 0.01, 0.0)
        val after = (recorder.recordingState.value as TrackRecorder.RecordingState.Recording).distance
        assertEquals(before, after, 0.01)
        assertEquals(3, recorder.getPoints().size)
    }

    @Test fun invalidCoordinatesAreDiscardedAndNewRecordingClearsOldPoints() {
        recorder.startRecording()
        recorder.addPoint(Double.NaN, 0.0, 0.0)
        recorder.addPoint(91.0, 0.0, 0.0)
        assertTrue(recorder.getPoints().isEmpty())
        recorder.addPoint(0.0, 0.0, 0.0)
        recorder.stopRecording()
        recorder.resumeRecording()
        recorder.addPoint(1.0, 1.0, 0.0)
        assertEquals(1, recorder.getPoints().size)
        recorder.startRecording()
        assertTrue(recorder.getPoints().isEmpty())
    }
}

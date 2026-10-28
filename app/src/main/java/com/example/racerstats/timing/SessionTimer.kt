package com.example.racerstats.timing

/** Monotonic clock supplied by the caller so wall-clock changes never affect laps. */
class SessionTimer(private val now: () -> Long) {
    var isRunning = false
        private set
    var isPaused = false
        private set
    private var startedAt = 0L
    private var accumulated = 0L
    private var lapOffset = 0L
    val lapStartedAt: Long get() = lapOffset

    val elapsed: Long get() = accumulated + if (isRunning) (now() - startedAt).coerceAtLeast(0) else 0
    val lapElapsed: Long get() = (elapsed - lapOffset).coerceAtLeast(0)

    fun start(timestamp: Long = now()) {
        accumulated = 0
        lapOffset = 0
        startedAt = timestamp.coerceIn(0L, now())
        isRunning = true
        isPaused = false
    }

    fun pause() {
        if (!isRunning) return
        accumulated = elapsed
        isRunning = false
        isPaused = true
    }

    fun resume() {
        if (!isPaused) return
        startedAt = now()
        isRunning = true
        isPaused = false
    }

    fun recordLap(): Long {
        if (!isRunning) return 0
        val current = elapsed
        val duration = current - lapOffset
        lapOffset = current
        return duration
    }

    fun stop() {
        if (isRunning) accumulated = elapsed
        isRunning = false
        isPaused = false
    }

    fun reset() {
        stop()
        accumulated = 0
        lapOffset = 0
    }
}

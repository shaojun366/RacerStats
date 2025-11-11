package com.example.racerstats

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.example.racerstats.location.LocationManager
import com.example.racerstats.location.PermissionManager
import kotlin.math.roundToInt

class LiveFragment : Fragment() {
    private lateinit var tvLapTime: TextView
    private lateinit var tvDelta: TextView
    private lateinit var tvPredicted: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvVmax: TextView
    private lateinit var deltaIndicator: View
    private lateinit var speedBar: ProgressBar
    private var btnStart: MaterialButton? = null
    private var btnLap: MaterialButton? = null
    private var btnStop: MaterialButton? = null
    private lateinit var btnClearLapHistory: MaterialButton
    private lateinit var chipHz: Chip
    private lateinit var chipSats: Chip
    private lateinit var chipAcc: Chip
    private lateinit var lapHistoryGroup: View
    private lateinit var lapListScroll: NestedScrollView
    private lateinit var lapListContainer: LinearLayout
    
    private val lapManager = com.example.racerstats.timing.LapManager()
    private var currentDistance = 0f  // 当前圈的累计距离
    private var locationUpdatesActive = false
    private var inferredUpdateIntervalMs = 200L
    private val speedTimeoutRunnable = object : Runnable {
        override fun run() {
            try {
                startSpeedDecay()
            } catch (e: Exception) {
                Log.e("LiveFragment", "Error resetting speed after timeout: ${e.message}", e)
            }
        }
    }
    private val speedDecayRunnable = object : Runnable {
        override fun run() {
            if (currentSpeed <= SPEED_DECAY_STOP_THRESHOLD) {
                currentSpeed = 0f
                updateSpeedDisplay(0f)
                stopSpeedDecay()
                lastLocationTimestamp = null
                return
            }
            currentSpeed = (currentSpeed * SPEED_DECAY_FACTOR).coerceAtLeast(0f)
            updateSpeedDisplay(currentSpeed * 3.6f)
            handler.postDelayed(this, SPEED_DECAY_INTERVAL_MS)
        }
    }
    private var isDecaying = false
    
    fun setButtons(startButton: MaterialButton, lapButton: MaterialButton, stopButton: MaterialButton) {
        btnStart = startButton
        btnLap = lapButton
        btnStop = stopButton
        setupButtonListeners()
    }
    
    private lateinit var locationManager: LocationManager
    private lateinit var permissionManager: PermissionManager
    
    private var isRunning = false
    private var isPaused = false
    private var lapStartTime = 0L
    private var elapsedOffset = 0L
    private var currentSpeed = 0f
    private var vmaxKmh = 0f
    private var lastLocationTimestamp: Long? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 16) // ~60fps
        }
    }
    private var lapCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Initialize views from main activity
            val activity = requireActivity()
            tvLapTime = activity.findViewById(R.id.tvLapTime)
            tvDelta = activity.findViewById(R.id.tvDelta)
            tvPredicted = activity.findViewById(R.id.tvPredicted)
            tvSpeed = activity.findViewById(R.id.tvSpeed)
            tvVmax = activity.findViewById(R.id.tvVmax)
            deltaIndicator = activity.findViewById(R.id.deltaIndicator)
            speedBar = activity.findViewById(R.id.speedBar)
            chipHz = activity.findViewById(R.id.chipHz)
            chipSats = activity.findViewById(R.id.chipSats)
            chipAcc = activity.findViewById(R.id.chipAcc)
            lapHistoryGroup = activity.findViewById(R.id.lapHistoryGroup)
            lapListScroll = activity.findViewById(R.id.lapListScroll)
            lapListContainer = activity.findViewById(R.id.lapListContainer)
            btnClearLapHistory = activity.findViewById(R.id.btnClearLapHistory)
            adjustLapListHeight()
            hideLapHistoryGroup()

            activity.findViewById<View>(R.id.vmaxContainer)?.setOnClickListener {
                resetVmax(userInitiated = true)
            }
            resetVmax()

            btnClearLapHistory.setOnClickListener {
                lapCount = 0
                clearLapList()
                showToast("圈速记录已清空")
            }

            // Initialize managers
            locationManager = LocationManager(requireContext())
            permissionManager = PermissionManager(this)

            // Set up location data observer
            locationManager.locationData.observe(viewLifecycleOwner) { data ->
                try {
                    updateLocationData(data)
                } catch (e: Exception) {
                    Log.e("LiveFragment", "Error updating location data: ${e.message}", e)
                }
            }

            // Set up connection status observer
            locationManager.connectionStatus.observe(viewLifecycleOwner) { status ->
                try {
                    updateConnectionStatus(status)
                } catch (e: Exception) {
                    Log.e("LiveFragment", "Error updating connection status: ${e.message}", e)
                }
            }

            setupButtonListeners()

            // Start status simulation
            startStatusSimulation()

            // Ensure GPS tracking is active when the UI is ready
            ensureLocationTracking()
            
        } catch (e: Exception) {
            Log.e("LiveFragment", "Error in onViewCreated: ${e.message}", e)
            showErrorDialog("初始化错误: ${e.message}")
        }
    }

    private fun startTiming() {
        beginTiming(resume = false)
    }

    private fun resumeTiming() {
        beginTiming(resume = true)
    }

    private fun beginTiming(resume: Boolean) {
        ensureLocationTracking {
            if (resume) {
                resumeTimingInternal()
            } else {
                startTimingInternal()
            }
        }
    }

    private fun startTimingInternal() {
        isRunning = true
        isPaused = false
        elapsedOffset = 0L
        lapStartTime = System.currentTimeMillis()
        currentDistance = 0f
        clearLapList()
        lapCount = 0
        lastLocationTimestamp = null
        btnStart?.text = "暂停"
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        lapManager.startNewLap()
        resetVmax()
    }

    private fun resumeTimingInternal() {
        isRunning = true
        isPaused = false
        btnStart?.text = "暂停"
        lapStartTime = System.currentTimeMillis() - elapsedOffset
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        lastLocationTimestamp = null
    }

    private fun pauseTiming() {
        if (!isRunning) return
        isRunning = false
        isPaused = true
        handler.removeCallbacks(updateRunnable)
        elapsedOffset = System.currentTimeMillis() - lapStartTime
        btnStart?.text = "继续"
        lastLocationTimestamp = null
        handler.removeCallbacks(speedTimeoutRunnable)
        stopSpeedDecay()
    }

    private fun stopTiming() {
        isRunning = false
        isPaused = false
        btnStart?.text = "开始"
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(speedTimeoutRunnable)
        stopSpeedDecay()
        lapStartTime = 0L
        elapsedOffset = 0L
        currentDistance = 0f
        lapCount = 0
        resetHudForNewLap()
        showToast("计时结束")
        lastLocationTimestamp = null
    }
    
    private fun setupButtonListeners() {
        btnStart?.setOnClickListener {
            try {
                when {
                    !isRunning && !isPaused -> startTiming()
                    isRunning -> pauseTiming()
                    isPaused -> resumeTiming()
                }
            } catch (e: Exception) {
                Log.e("LiveFragment", "Error in start button click: ${e.message}", e)
                showErrorDialog("启动计时失败: ${e.message}")
            }
        }

        btnLap?.setOnClickListener {
            try {
                if (isRunning) {
                    recordLap()
                }
            } catch (e: Exception) {
                Log.e("LiveFragment", "Error in lap button click: ${e.message}", e)
                showToast("记录圈速失败: ${e.message}")
            }
        }

        btnStop?.setOnClickListener {
            try {
                if (isRunning || isPaused) {
                    stopTiming()
                }
            } catch (e: Exception) {
                Log.e("LiveFragment", "Error in stop button click: ${e.message}", e)
                showErrorDialog("结束计时失败: ${e.message}")
            }
        }
    }

    private fun updateLocationData(data: LocationManager.LocationData) {
        try {
            stopSpeedDecay()
            // Update speed display with speed smoothing
            currentSpeed = if (currentSpeed == 0f) data.speed else {
                // Apply low-pass filter for smoother speed updates
                currentSpeed * 0.7f + data.speed * 0.3f
            }

            Log.d(
                "LiveFragment",
                "loc source=${data.source} speed=${"%.3f".format(data.speed)} filtered=${"%.3f".format(currentSpeed)} \n" +
                    "lat=${data.latitude} lon=${data.longitude} ts=${data.timestamp}"
            )
            
            // Convert to km/h and update display
            val speedKmh = currentSpeed * 3.6f
            updateSpeedDisplay(speedKmh)
            if (speedKmh > vmaxKmh) {
                vmaxKmh = speedKmh
                updateVmaxDisplay()
            }
            
            val timestamp = data.timestamp
            val previousTimestamp = lastLocationTimestamp
            val updateIntervalMs = if (previousTimestamp != null && timestamp > previousTimestamp) {
                timestamp - previousTimestamp
            } else {
                null
            }
            scheduleSpeedTimeout(updateIntervalMs)

            if (!isRunning) {
                lastLocationTimestamp = timestamp
                return
            }
            
            // 更新圈速信息
            if (previousTimestamp != null && timestamp > previousTimestamp) {
                val deltaSeconds = (timestamp - previousTimestamp) / 1000f
                if (deltaSeconds > 0f) {
                    currentDistance += data.speed * deltaSeconds
                }
            }
            lastLocationTimestamp = timestamp

            val (delta, predictedTotal) = lapManager.updateCurrentLap(
                timestamp,
                currentDistance,
                data.speed,
                data.latitude,
                data.longitude
            ) ?: Pair(0.0, 0L)
            
            tvDelta.text = "Δ %+.3f s".format(delta)
            tvPredicted.text = formatTime(predictedTotal)
            
            // 更新 delta 指示器
            val deltaProgress = ((delta + 2.0) / 4.0 * 100).toInt()
            deltaIndicator.translationX = (deltaProgress - 50) * resources.displayMetrics.density
            deltaIndicator.setBackgroundResource(if (delta > 0) R.color.bad else R.color.good)
            
        } catch (e: Exception) {
            Log.e("LiveFragment", "Error updating location data display: ${e.message}", e)
        }
    }

    private fun updateConnectionStatus(status: LocationManager.ConnectionStatus) {
        when (status) {
            LocationManager.ConnectionStatus.CONNECTED_DRAGY -> {
                // Dragy Pro 已连接
                showToast("已连接到 Dragy Pro")
            }
            LocationManager.ConnectionStatus.CONNECTED_GPS -> {
                // 使用手机 GPS
                showToast("使用手机 GPS")
            }
            LocationManager.ConnectionStatus.ERROR -> {
                showToast("定位设备异常，已尝试切换手机 GPS")
                locationManager.startUpdates()
            }
            else -> {}
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun recordLap() {
        if (!isRunning) {
            showToast("请先开始计时")
            return
        }

        val now = System.currentTimeMillis()
        val lapDuration = now - lapStartTime

        lapManager.completeLap(now)
        lapCount += 1

        val lapLabel = lapLabelFor(lapCount)
        showToast("$lapLabel: ${formatTime(lapDuration)}")

        addLapEntry(lapCount, lapDuration)

        lapManager.startNewLap()

        lapStartTime = now
        elapsedOffset = 0L
        currentDistance = 0f
        currentSpeed = 0f
        lastLocationTimestamp = null

        resetHudForNewLap()
    }

    private fun resetHudForNewLap() {
        stopSpeedDecay()
        tvLapTime.text = formatTime(0)
        tvDelta.text = "Δ +0.000 s"
        tvPredicted.text = "--:--.---"
        deltaIndicator.translationX = 0f
        deltaIndicator.setBackgroundResource(R.color.good)
        updateSpeedDisplay(0f)
    }

    private fun addLapEntry(lapNumber: Int, duration: Long) {
        if (!::lapListContainer.isInitialized) return
        val context = context ?: return
        val lapLabel = lapLabelFor(lapNumber)
        val entryView = TextView(context).apply {
            text = "$lapLabel  ${formatTime(duration)}"
            setTextColor(ContextCompat.getColor(context, R.color.text))
            textSize = 16f
            setPadding(0, 8.dp(), 0, 8.dp())
        }
        showLapHistoryGroup()
        lapListContainer.addView(entryView)
        adjustLapListHeight()
        if (::lapListScroll.isInitialized) {
            lapListScroll.post {
                lapListScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun clearLapList() {
        if (::lapListContainer.isInitialized) {
            lapListContainer.removeAllViews()
        }
        if (::lapListScroll.isInitialized) {
            lapListScroll.scrollTo(0, 0)
        }
        adjustLapListHeight()
        hideLapHistoryGroup()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun lapLabelFor(number: Int): String {
        return if (number > 0) "第${toChineseNumeral(number)}圈" else "第${number}圈"
    }

    private fun toChineseNumeral(number: Int): String {
        if (number <= 0) return number.toString()
        if (number >= 100) return number.toString()

        val digits = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

        return when {
            number < 10 -> digits[number]
            number == 10 -> "十"
            number < 20 -> "十" + digits[number - 10]
            number % 10 == 0 -> digits[number / 10] + "十"
            else -> digits[number / 10] + "十" + digits[number % 10]
        }
    }

    private fun adjustLapListHeight() {
        if (!::lapListScroll.isInitialized) return
        val hasLaps = ::lapListContainer.isInitialized && lapListContainer.childCount > 0
        val shouldClamp = hasLaps && lapListContainer.childCount > MAX_VISIBLE_LAPS
        val desiredHeight = when {
            !hasLaps -> 0
            shouldClamp -> (MAX_LAP_LIST_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }

        val params = lapListScroll.layoutParams
        if (params.height != desiredHeight) {
            params.height = desiredHeight
            lapListScroll.layoutParams = params
        }
    }

    private fun showLapHistoryGroup() {
        if (::lapHistoryGroup.isInitialized) {
            if (lapHistoryGroup.visibility != View.VISIBLE) {
                lapHistoryGroup.visibility = View.VISIBLE
            }
        }
        if (::btnClearLapHistory.isInitialized && btnClearLapHistory.visibility != View.VISIBLE) {
            btnClearLapHistory.visibility = View.VISIBLE
        }
    }

    private fun hideLapHistoryGroup() {
        if (::lapHistoryGroup.isInitialized) {
            lapHistoryGroup.visibility = View.GONE
        }
        if (::btnClearLapHistory.isInitialized) {
            btnClearLapHistory.visibility = View.GONE
        }
    }

    private fun updateUI() {
        if (!isRunning) return

        try {
            val currentTime = System.currentTimeMillis()
            val lapTime = currentTime - lapStartTime

            // Update lap time
            tvLapTime.text = formatTime(lapTime)

            // Speed is now updated directly from GPS data in updateLocationData()

            // Simulate Delta (TODO: implement real delta calculation)
            val delta = 0.0 // Will be implemented later
            tvDelta.text = "Δ %+.3f s".format(delta)
            
            // Update Delta indicator position
            val deltaProgress = ((delta + 2.0) / 4.0 * 100).toInt()
            deltaIndicator.translationX = (deltaProgress - 50) * resources.displayMetrics.density
            deltaIndicator.setBackgroundResource(if (delta > 0) R.color.bad else R.color.good)

            // Predicted time (TODO: implement real prediction)
            val predictedTotal = lapTime // Will be implemented later
            tvPredicted.text = formatTime(predictedTotal)
        } catch (e: Exception) {
            Log.e("LiveFragment", "Error updating UI: ${e.message}", e)
            handler.removeCallbacks(updateRunnable)
            showErrorDialog("UI更新错误，计时已停止")
            stopTiming()
        }
    }

    private fun startStatusSimulation() {
        // 移除模拟数据，改为观察真实的 GNSS 数据
        locationManager.gnssData.observe(viewLifecycleOwner) { gnssData ->
            try {
                if (isAdded && ::chipHz.isInitialized && ::chipSats.isInitialized && ::chipAcc.isInitialized) {
                    chipHz.text = "Hz: %.1f".format(gnssData.updateRate)
                    chipSats.text = "Sats: ${gnssData.satsUsed}/${gnssData.satsInView}"
                    chipAcc.text = "Acc: %.1f m".format(gnssData.accuracy)
                }
            } catch (e: Exception) {
                Log.e("LiveFragment", "Error updating GNSS status: ${e.message}", e)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 60000).toInt()
        val seconds = ((millis % 60000) / 1000).toInt()
        val milliseconds = (millis % 1000).toInt()
        return "%02d:%02d.%03d".format(minutes, seconds, milliseconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        locationManager.release()
        locationUpdatesActive = false
        handler.removeCallbacks(speedTimeoutRunnable)
        handler.removeCallbacks(speedDecayRunnable)
    }

    private fun ensureLocationTracking(onReady: (() -> Unit)? = null) {
        permissionManager.checkAndRequestPermissions { granted ->
            if (granted) {
                if (!locationUpdatesActive) {
                    locationManager.startUpdates()
                    locationManager.connectToDragy()
                    locationUpdatesActive = true
                }
                onReady?.invoke()
            } else {
                if (onReady != null) {
                    showErrorDialog("需要位置和蓝牙权限才能使用此功能")
                }
            }
        }
    }

    private fun scheduleSpeedTimeout(updateIntervalMs: Long?) {
        val interval = updateIntervalMs?.takeIf { it > 0 }
            ?.coerceAtMost(MAX_INFERRED_INTERVAL_MS)
            ?: inferredUpdateIntervalMs
        inferredUpdateIntervalMs = interval.coerceIn(MIN_INFERRED_INTERVAL_MS, MAX_INFERRED_INTERVAL_MS)
        val timeout = (inferredUpdateIntervalMs * MISSED_PACKET_THRESHOLD).coerceAtLeast(MIN_TIMEOUT_MS)
        handler.removeCallbacks(speedTimeoutRunnable)
        handler.postDelayed(speedTimeoutRunnable, timeout)
    }

    private fun updateSpeedDisplay(speedKmh: Float) {
        if (::tvSpeed.isInitialized) {
            tvSpeed.text = "%.1f km/h".format(speedKmh)
        }
        if (::speedBar.isInitialized) {
            val progressPercent = (speedKmh / 350f * 100f).toInt().coerceIn(0, 100)
            speedBar.progress = progressPercent
        }
    }

    private fun startSpeedDecay() {
        if (isDecaying) return
        if (currentSpeed <= SPEED_DECAY_STOP_THRESHOLD) {
            currentSpeed = 0f
            updateSpeedDisplay(0f)
            lastLocationTimestamp = null
            return
        }
        isDecaying = true
        handler.post(speedDecayRunnable)
    }

    private fun stopSpeedDecay() {
        handler.removeCallbacks(speedDecayRunnable)
        isDecaying = false
    }

    private fun updateVmaxDisplay() {
        if (::tvVmax.isInitialized) {
            tvVmax.text = "%.1f km/h".format(vmaxKmh)
        }
    }

    private fun resetVmax(userInitiated: Boolean = false) {
        vmaxKmh = 0f
        updateVmaxDisplay()
        if (userInitiated) {
            showToast("Vmax 已清零")
        }
    }

    companion object {
        private const val MAX_VISIBLE_LAPS = 5
        private const val MAX_LAP_LIST_HEIGHT_DP = 200
        private const val MISSED_PACKET_THRESHOLD = 5
        private const val MIN_TIMEOUT_MS = 300L
        private const val MIN_INFERRED_INTERVAL_MS = 50L
        private const val MAX_INFERRED_INTERVAL_MS = 2_000L
        private const val SPEED_DECAY_FACTOR = 0.8f
        private const val SPEED_DECAY_INTERVAL_MS = 200L
        private const val SPEED_DECAY_STOP_THRESHOLD = 0.3f
    }
}
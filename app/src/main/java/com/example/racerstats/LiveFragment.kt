package com.example.racerstats

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.racerstats.databinding.FragmentLiveBinding
import com.example.racerstats.location.LocationManager
import com.example.racerstats.location.PermissionManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class LiveFragment : Fragment(R.layout.fragment_live) {
    private val model: SessionViewModel by activityViewModels()
    private val permissions = PermissionManager(this)
    private var _binding: FragmentLiveBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLiveBinding.bind(view)
        binding.btnSelectTrack.setOnClickListener { showTrackSelection() }
        binding.btnStart.setOnClickListener {
            when {
                model.canPause -> model.pause()
                else -> permissions.checkAndRequestPermissions { granted ->
                    if (_binding == null) return@checkAndRequestPermissions
                    if (granted) {
                        runCatching {
                            model.enableGps()
                            if (model.isPaused) model.resume() else model.start()
                        }.onFailure { showMessage("无法启动 GPS，请检查系统定位设置") }
                    } else showMessage("请授予精确位置权限并开启 GPS")
                    render()
                }
            }
            render()
        }
        binding.btnLap.setOnClickListener { model.recordLap(); render() }
        binding.btnStop.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("结束本次计时？")
                .setMessage("已完成的圈速会保留，当前未完成圈不会计入成绩。")
                .setPositiveButton("结束计时") { _, _ -> model.stop(); render() }
                .setNegativeButton("继续计时", null).show()
        }
        binding.vmaxContainer.setOnClickListener { model.resetMaxSpeed(); render() }
        model.selectedTrack.observe(viewLifecycleOwner) { render() }
        model.selectingTrack.observe(viewLifecycleOwner) { render() }
        model.availableTracks.observe(viewLifecycleOwner) { }
        model.notice.observe(viewLifecycleOwner) { message ->
            if (message != null) { showMessage(message); model.consumeNotice() }
        }
        model.location.gnssData.observe(viewLifecycleOwner) { data ->
            binding.chipHz.showText(String.format(Locale.US, "%.1f Hz", data.updateRate))
            binding.chipSats.showText("卫星 ${data.satsUsed}/${data.satsInView}")
            binding.chipAcc.showText(if (data.accuracy > 0) String.format(Locale.US, "±%.1f m", data.accuracy) else "精度 --")
        }
        model.location.connectionStatus.observe(viewLifecycleOwner) { status ->
            if (status == LocationManager.ConnectionStatus.ERROR) showMessage("定位不可用，请检查 GPS 和位置权限")
        }
        model.sessionLaps.observe(viewLifecycleOwner) { laps ->
            val best = laps.minByOrNull { it.duration }
            binding.tvBestLap.text = best?.let { formatLapTime(it.duration) } ?: "--:--.---"
            binding.tvLastLap.text = laps.lastOrNull()?.let { formatLapTime(it.duration) } ?: "--:--.---"
            binding.tvLapCount.text = "已完成 ${laps.size} 圈"
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    render()
                    delay(if (model.canPause) 50 else 500)
                }
            }
        }
        render()
    }

    private fun render() {
        val ui = _binding ?: return
        val timer = model.timer
        ui.tvLapTime.showText(formatLapTime(timer.lapElapsed))
        ui.tvSessionState.showText(when {
            model.isWaitingForStart -> getString(R.string.waiting_start_line)
            model.isAutomatic && model.isPaused -> getString(R.string.auto_paused)
            timer.isRunning -> "正在计时 · 第 ${model.sessionLaps.value.orEmpty().size + 1} 圈"
            timer.isPaused -> "计时已暂停"
            timer.elapsed > 0 || model.sessionLaps.value.orEmpty().isNotEmpty() -> "本次计时已结束"
            else -> "准备就绪"
        })
        ui.tvGpsStatus.showText(when {
            !model.hasFreshFix -> "等待 GPS 信号"
            model.isAutomatic && (model.gpsAccuracy == null || model.gpsAccuracy!! > 20) -> getString(R.string.gps_accuracy_insufficient)
            else -> "GPS 信号正常"
        })
        ui.btnStart.showText(when {
            model.canPause -> "暂停"
            model.isPaused -> "继续计时"
            model.isAutomatic -> getString(R.string.arm_auto_timing)
            else -> "开始计时"
        })
        val selecting = model.selectingTrack.value == true
        ui.btnStart.isEnabled = !selecting
        ui.btnSelectTrack.isEnabled = !model.isSessionActive && !selecting
        ui.btnSelectTrack.showText(when {
            selecting -> getString(R.string.loading_track)
            model.isAutomatic -> getString(R.string.selected_auto_track, model.selectedTrack.value!!.track.name)
            else -> getString(R.string.manual_track_selection)
        })
        ui.tvTimingHelp.showText(getString(if (model.isAutomatic) R.string.auto_timing_help else R.string.timing_help))
        ui.btnLap.showText(getString(if (model.isAutomatic) R.string.automatic_crossing else R.string.record_lap))
        ui.btnLap.isEnabled = !model.isAutomatic && timer.isRunning
        ui.btnStop.isEnabled = model.isSessionActive
        val speed = if (model.hasFreshFix) model.speedKmh else 0f
        ui.tvSpeed.showText(String.format(Locale.US, "%.1f", speed))
        ui.tvVmax.showText(String.format(Locale.US, "%.1f", model.maxSpeedKmh))
        ui.speedBar.progress = (speed / 350 * 100).toInt().coerceIn(0, 100)
        val comparison = model.comparison.takeIf { model.hasFreshFix }
        ui.tvDelta.showText(comparison?.let { String.format(Locale.US, "%+.3f s", it.first) } ?: "--.--- s")
        ui.tvPredicted.showText(comparison?.second?.takeIf { it > 0 }?.let(::formatLapTime) ?: "--:--.---")
        val color = when {
            comparison == null -> R.color.muted
            comparison.first > 0 -> R.color.bad
            else -> R.color.good
        }
        val resolvedColor = ContextCompat.getColor(requireContext(), color)
        if (ui.tvDelta.currentTextColor != resolvedColor) ui.tvDelta.setTextColor(resolvedColor)
        if ((ui.deltaIndicator.background as? ColorDrawable)?.color != resolvedColor)
            ui.deltaIndicator.setBackgroundColor(resolvedColor)
        val track = ui.deltaIndicator.parent as View
        ui.deltaIndicator.translationX = ((comparison?.first ?: 0.0) / 2.0).coerceIn(-1.0, 1.0).toFloat() *
            ((track.width - ui.deltaIndicator.width) / 2f).coerceAtLeast(0f)
        val window = requireActivity().window
        val keepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val screenIsKeptOn = window.attributes.flags and keepScreenOn != 0
        if (model.canPause != screenIsKeptOn) {
            if (model.canPause) window.addFlags(keepScreenOn) else window.clearFlags(keepScreenOn)
        }
    }

    private fun showTrackSelection() {
        val tracks = model.availableTracks.value.orEmpty()
        val labels = listOf(getString(R.string.manual_timing_option)) + tracks.map { it.name } +
            getString(R.string.manage_tracks)
        val selected = tracks.indexOfFirst { it.id == model.selectedTrack.value?.track?.id } + 1
        AlertDialog.Builder(requireContext()).setTitle(R.string.choose_timing_track)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                when (which) {
                    0 -> model.selectTrack(null)
                    labels.lastIndex -> requireActivity()
                        .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                        .selectedItemId = R.id.nav_track
                    else -> model.selectTrack(tracks[which - 1].id)
                }
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun TextView.showText(value: String) {
        if (text.toString() != value) text = value
    }

    private fun showMessage(message: String) {
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

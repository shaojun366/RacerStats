package com.example.racerstats.track

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.racerstats.location.PermissionManager
import androidx.lifecycle.lifecycleScope
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackBinding
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackEditEvent
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.example.racerstats.maps.MapSupport
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class TrackFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentTrackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackViewModel by viewModels()
    private val permissions = PermissionManager(this)
    private var map: GoogleMap? = null
    private var mapsConfigured = false
    private var currentTrackPolyline: Polyline? = null
    private var startLineMarkerPrimary: Marker? = null
    private var startLineMarkerSecondary: Marker? = null
    private var startLinePreviewMarker: Marker? = null
    private var startLinePolyline: Polyline? = null
    private var finishLineMarkerPrimary: Marker? = null
    private var finishLineMarkerSecondary: Marker? = null
    private var finishLinePreviewMarker: Marker? = null
    private var finishLinePolyline: Polyline? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapsConfigured = MapSupport.bind(this, R.id.map, binding.mapStatus, binding.routePreview, this)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnStartFinish.setOnClickListener {
            when (viewModel.recordingState.value) {
                is TrackRecorder.RecordingState.Idle -> {
                    permissions.checkAndRequestPermissions { granted ->
                        if (granted && _binding != null) {
                            setupMapLocation()
                            viewModel.startRecording()
                        } else if (_binding != null) showSnackbar(getString(R.string.recording_gps_required))
                    }
                }
                is TrackRecorder.RecordingState.Recording -> showSaveTrackDialog()
                is TrackRecorder.RecordingState.Paused -> showSaveTrackDialog()
            }
        }

        binding.btnPause.setOnClickListener {
            when (viewModel.recordingState.value) {
                is TrackRecorder.RecordingState.Recording -> viewModel.pauseRecording()
                is TrackRecorder.RecordingState.Paused -> permissions.checkAndRequestPermissions { granted ->
                    if (granted && _binding != null) viewModel.resumeRecording()
                }
                else -> {}
            }
        }

        binding.btnSetLine.setOnClickListener {
            viewModel.toggleLineSettingMode()
        }

        // 宽容度设置按钮
        binding.btnTolerance.setOnClickListener {
            showToleranceSelectionDialog()
        }

        // 初始化宽容度显示
        val currentTolerance = viewModel.getCurrentTolerance()
        updateToleranceButton("", currentTolerance)
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSaving.collectLatest {
                updateUI(viewModel.recordingState.value)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recordingState.collectLatest { state ->
                updateUI(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentTrackPoints.collectLatest { points ->
                updateTrackDisplay(points)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lineSettingState.collectLatest { state ->
                updateLineSettingUI(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.linePreviewState.collectLatest { state ->
                renderLinePreview(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is TrackEditEvent.NavigateBack -> findNavController().popBackStack()
                    is TrackEditEvent.ShowError -> showSnackbar(getString(event.messageRes, *event.formatArgs))
                    is TrackEditEvent.ShowMessage -> showSnackbar(getString(event.messageRes, *event.formatArgs))
                }
            }
        }
    }

    private fun updateUI(state: TrackRecorder.RecordingState) {
        binding.btnStartFinish.isEnabled = !viewModel.isSaving.value
        binding.btnPause.isEnabled = !viewModel.isSaving.value
        binding.btnSetLine.isEnabled = map != null && state !is TrackRecorder.RecordingState.Idle && !viewModel.isSaving.value
        binding.btnTolerance.isEnabled = !viewModel.isSaving.value
        // 操作区固定在底部
        binding.actionsContainer.visibility = View.VISIBLE
        binding.btnStartFinish.visibility = View.VISIBLE

        when (state) {
            is TrackRecorder.RecordingState.Idle -> {
                binding.btnStartFinish.setText(R.string.start_recording)
                binding.btnPause.visibility = View.GONE
                binding.btnSetLine.visibility = View.VISIBLE
                binding.recordingState.setText(R.string.recording_ready)
                updateTrackStats(0.0, 0)
            }
            is TrackRecorder.RecordingState.Recording -> {
                binding.btnStartFinish.setText(R.string.recording_save)
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.setText(R.string.pause)
                binding.recordingState.setText(R.string.recording_active)
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
            }
            is TrackRecorder.RecordingState.Paused -> {
                binding.btnStartFinish.setText(R.string.recording_save)
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.setText(R.string.resume)
                binding.recordingState.setText(R.string.recording_paused)
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
            }
        }
        if (viewModel.isSaving.value) binding.btnStartFinish.setText(R.string.recording_saving)
    }

    private fun updateTrackStats(distance: Double, pointCount: Int) {
        binding.tvDistance.text = getString(R.string.recording_distance_value, distance / 1000)
        binding.tvPointCount.text = getString(R.string.recording_points, pointCount)
    }

    private fun updateTrackDisplay(points: List<GpsPoint>) {
        val lines = viewModel.linePreviewState.value
        binding.routePreview.setRoute(points, lines.startLine, lines.finishLine)
        if (points.isEmpty()) {
            currentTrackPolyline?.remove()
            currentTrackPolyline = null
            return
        }

        val polylineOptions = PolylineOptions()
            .addAll(points.map { LatLng(it.latitude, it.longitude) })
            .color(ContextCompat.getColor(requireContext(), R.color.track_line))
            .width(8f)

        currentTrackPolyline?.remove()
        currentTrackPolyline = map?.addPolyline(polylineOptions)

        // 移动摄像头到最新点
        val lastPoint = points.last()
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(lastPoint.latitude, lastPoint.longitude),
            17f
        ))
    }

    private fun renderLinePreview(state: LinePreviewState) {
        binding.routePreview.setRoute(viewModel.currentTrackPoints.value, state.startLine, state.finishLine)
        updateStartLineDisplay(state)
        updateFinishLineDisplay(state)
    }

    private fun updateStartLineDisplay(state: LinePreviewState) {
        clearStartLineGraphics()
        val map = map ?: return

        state.startLine?.let { (point1, point2) ->
            startLineMarkerPrimary = addMarker(map, point1, BitmapDescriptorFactory.HUE_AZURE)
            startLineMarkerSecondary = addMarker(map, point2, BitmapDescriptorFactory.HUE_AZURE)
            startLinePolyline = addLine(map, point1, point2, Color.BLUE)
            return
        }

        state.startPointPreview?.let { preview ->
            startLinePreviewMarker = addMarker(map, preview, BitmapDescriptorFactory.HUE_AZURE)
        }
    }

    private fun updateFinishLineDisplay(state: LinePreviewState) {
        clearFinishLineGraphics()
        val map = map ?: return

        state.finishLine?.let { (point1, point2) ->
            finishLineMarkerPrimary = addMarker(map, point1, BitmapDescriptorFactory.HUE_ROSE)
            finishLineMarkerSecondary = addMarker(map, point2, BitmapDescriptorFactory.HUE_ROSE)
            finishLinePolyline = addLine(map, point1, point2, Color.RED)
            return
        }

        state.finishPointPreview?.let { preview ->
            finishLinePreviewMarker = addMarker(map, preview, BitmapDescriptorFactory.HUE_ROSE)
        }
    }

    private fun addMarker(map: GoogleMap, point: GpsPoint, hue: Float): Marker? {
        return map.addMarker(
            MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
        )
    }

    private fun addLine(map: GoogleMap, start: GpsPoint, end: GpsPoint, color: Int): Polyline? {
        return map.addPolyline(
            PolylineOptions()
                .add(LatLng(start.latitude, start.longitude))
                .add(LatLng(end.latitude, end.longitude))
                .color(color)
                .width(6f)
        )
    }

    private fun clearStartLineGraphics() {
        startLineMarkerPrimary?.remove()
        startLineMarkerPrimary = null
        startLineMarkerSecondary?.remove()
        startLineMarkerSecondary = null
        startLinePreviewMarker?.remove()
        startLinePreviewMarker = null
        startLinePolyline?.remove()
        startLinePolyline = null
    }

    private fun clearFinishLineGraphics() {
        finishLineMarkerPrimary?.remove()
        finishLineMarkerPrimary = null
        finishLineMarkerSecondary?.remove()
        finishLineMarkerSecondary = null
        finishLinePreviewMarker?.remove()
        finishLinePreviewMarker = null
        finishLinePolyline?.remove()
        finishLinePolyline = null
    }

    private fun updateLineSettingUI(state: TrackViewModel.LineSettingState) {
        when (state) {
            is TrackViewModel.LineSettingState.SettingStartLine -> {
                binding.btnSetLine.setText(R.string.setting_start_line)
                showLineSettingInstructions(R.string.tap_to_set_start_line)
            }
            is TrackViewModel.LineSettingState.SettingFinishLine -> {
                binding.btnSetLine.setText(R.string.setting_finish_line)
                showLineSettingInstructions(R.string.tap_to_set_finish_line)
            }
            is TrackViewModel.LineSettingState.NotSetting -> {
                binding.btnSetLine.setText(R.string.set_line)
                hideLineSettingInstructions()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (_binding == null) return
        map = googleMap
        setupMapLocation()

        map?.uiSettings?.apply {
            isMyLocationButtonEnabled = true
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        // 设置地图类型为混合模式（卫星+道路）
        map?.mapType = GoogleMap.MAP_TYPE_HYBRID

        map?.setOnMapClickListener { latLng ->
            viewModel.handleMapClick(GpsPoint(latLng.latitude, latLng.longitude))
        }

        // 设置默认位置
        setDefaultMapLocation()

        // 更新按钮文本表示地图已就绪
        updateUI(viewModel.recordingState.value)
        updateTrackDisplay(viewModel.currentTrackPoints.value)
        renderLinePreview(viewModel.linePreviewState.value)
    }

    private fun setupMapLocation() {
        if (checkLocationPermissions()) {
            try {
                map?.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                showSnackbar(getString(R.string.recording_map_permission))
            }
        }
    }

    private fun setDefaultMapLocation() {
        // 设置默认位置到一个合理的位置，比如北京
        val defaultLocation = LatLng(39.9042, 116.4074)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
    }

    private fun showSaveTrackDialog() {
        SaveTrackDialog().show(childFragmentManager, "save_track_dialog")
    }

    fun onSaveTrack(name: String) {
        viewModel.saveTrack(name)
    }

    private fun showLineSettingInstructions(messageResId: Int) {
        binding.lineInstructions.setText(messageResId)
        binding.lineInstructions.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
    }

    private fun hideLineSettingInstructions() {
        binding.lineInstructions.setText(if (mapsConfigured) R.string.recording_help else R.string.recording_without_maps)
        binding.lineInstructions.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showToleranceSelectionDialog() {
        val toleranceOptions = arrayOf(
            "精密赛道 · 25 米",
            "街道赛道 · 40 米",
            "普通道路 · 50 米",
            "高速道路 · 80 米"
        )

        val toleranceValues = arrayOf(
            TrackMatcher.THRESHOLD_PRECISION_TRACK,
            TrackMatcher.THRESHOLD_STREET_CIRCUIT,
            TrackMatcher.THRESHOLD_GENERAL_ROAD,
            TrackMatcher.THRESHOLD_HIGHWAY
        )

        val currentTolerance = viewModel.getCurrentTolerance()
        val currentIndex = toleranceValues.indexOfFirst {
            kotlin.math.abs(it - currentTolerance) < 1.0
        }.takeIf { it >= 0 } ?: 1 // 默认选择 Street Circuit

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("重录赛道匹配容差")
            .setSingleChoiceItems(toleranceOptions, currentIndex) { dialog, which ->
                val selectedTolerance = toleranceValues[which]
                val selectedName = toleranceOptions[which]

                viewModel.setTolerance(selectedTolerance)
                updateToleranceButton(selectedName, selectedTolerance)

                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateToleranceButton(@Suppress("UNUSED_PARAMETER") name: String, tolerance: Double) {
        val shortName = when {
            tolerance <= 25.0 -> "精密 · ${tolerance.toInt()} 米"
            tolerance <= 40.0 -> "街道 · ${tolerance.toInt()} 米"
            tolerance <= 50.0 -> "道路 · ${tolerance.toInt()} 米"
            else -> "高速 · ${tolerance.toInt()} 米"
        }
        binding.btnTolerance.text = shortName
    }

    override fun onDestroyView() {
        map?.setOnMapClickListener(null)
        clearStartLineGraphics()
        clearFinishLineGraphics()
        currentTrackPolyline = null
        map = null
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        if (!requireActivity().isChangingConfigurations) viewModel.pauseForBackground()
        super.onStop()
    }
}

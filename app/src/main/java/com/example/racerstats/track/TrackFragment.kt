package com.example.racerstats.track

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackBinding
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackEditEvent
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
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
    private var map: GoogleMap? = null
    private var currentTrackPolyline: Polyline? = null
    private var startLineMarkerPrimary: Marker? = null
    private var startLineMarkerSecondary: Marker? = null
    private var startLinePreviewMarker: Marker? = null
    private var startLinePolyline: Polyline? = null
    private var finishLineMarkerPrimary: Marker? = null
    private var finishLineMarkerSecondary: Marker? = null
    private var finishLinePreviewMarker: Marker? = null
    private var finishLinePolyline: Polyline? = null
    
    // 权限请求
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted && coarseLocationGranted) {
            setupMapLocation()
        } else {
            showSnackbar("Location permission is required for track recording")
        }
    }
    
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
        
        // 强制显示所有UI元素
        binding.actionsContainer.visibility = View.VISIBLE
        binding.statsContainer.visibility = View.VISIBLE
        binding.btnStartFinish.visibility = View.VISIBLE
        binding.btnPause.visibility = View.VISIBLE
        binding.btnSetLine.visibility = View.VISIBLE
        
        // 设置按钮文本以确保它们有内容
        binding.btnStartFinish.text = "START RECORDING"
        binding.btnPause.text = "PAUSE"
        binding.btnSetLine.text = "SET LINE"
        
        // 调试：添加日志
        android.util.Log.d("TrackFragment", "onViewCreated: All UI elements forced visible")
        
        // 初始化地图
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        if (mapFragment == null) {
            android.util.Log.e("TrackFragment", "MapFragment is null!")
            // 如果地图加载失败，至少显示按钮
            binding.btnStartFinish.text = "Start Recording (Map Error)"
        } else {
            android.util.Log.d("TrackFragment", "Initializing Google Maps")
            mapFragment.getMapAsync(this)
        }
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        // 调试：确保按钮点击事件正常工作
        android.util.Log.d("TrackFragment", "setupUI: Configuring button listeners")
        
        binding.btnStartFinish.setOnClickListener {
            android.util.Log.d("TrackFragment", "Start/Finish button clicked")
            when (viewModel.recordingState.value) {
                is TrackRecorder.RecordingState.Idle -> {
                    if (checkLocationPermissions()) {
                        android.util.Log.d("TrackFragment", "Starting recording")
                        viewModel.startRecording()
                    } else {
                        android.util.Log.d("TrackFragment", "Requesting location permissions")
                        requestLocationPermissions()
                    }
                }
                is TrackRecorder.RecordingState.Recording -> showSaveTrackDialog()
                is TrackRecorder.RecordingState.Paused -> showSaveTrackDialog()
            }
        }
        
        binding.btnPause.setOnClickListener {
            when (viewModel.recordingState.value) {
                is TrackRecorder.RecordingState.Recording -> viewModel.pauseRecording()
                is TrackRecorder.RecordingState.Paused -> viewModel.resumeRecording()
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
    
    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun observeViewModel() {
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
                    is TrackEditEvent.NavigateBack -> {}
                    is TrackEditEvent.ShowError -> showSnackbar(getString(event.messageRes, *event.formatArgs))
                    is TrackEditEvent.ShowMessage -> showSnackbar(getString(event.messageRes, *event.formatArgs))
                }
            }
        }
    }
    
    private fun updateUI(state: TrackRecorder.RecordingState) {
        // 强制确保按钮容器可见
        binding.actionsContainer.visibility = View.VISIBLE
        binding.btnStartFinish.visibility = View.VISIBLE
        
        when (state) {
            is TrackRecorder.RecordingState.Idle -> {
                binding.btnStartFinish.text = "Start Recording"
                binding.btnPause.visibility = View.VISIBLE // 暂时都显示，方便调试
                binding.btnSetLine.visibility = View.VISIBLE
                binding.tvDistance.text = "0.0 km"
                binding.tvPointCount.text = "0 points"
                android.util.Log.d("TrackFragment", "UI State: Idle")
            }
            is TrackRecorder.RecordingState.Recording -> {
                binding.btnStartFinish.text = "Finish"
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.text = "Pause"
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
                android.util.Log.d("TrackFragment", "UI State: Recording")
            }
            is TrackRecorder.RecordingState.Paused -> {
                binding.btnStartFinish.text = "Finish"
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.text = "Resume"
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
                android.util.Log.d("TrackFragment", "UI State: Paused")
            }
        }
    }
    
    private fun updateTrackStats(distance: Double, pointCount: Int) {
        binding.tvDistance.text = "%.2f km".format(distance / 1000)
        binding.tvPointCount.text = "$pointCount points"
    }
    
    private fun updateTrackDisplay(points: List<GpsPoint>) {
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
        android.util.Log.d("TrackFragment", "Google Maps ready!")
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
            android.util.Log.d("TrackFragment", "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")
            viewModel.handleMapClick(GpsPoint(latLng.latitude, latLng.longitude))
        }
        
        // 设置默认位置
        setDefaultMapLocation()
        
        // 更新按钮文本表示地图已就绪
        binding.btnStartFinish.text = getString(R.string.start_recording)
    }
    
    private fun setupMapLocation() {
        if (checkLocationPermissions()) {
            try {
                map?.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                showSnackbar("Unable to enable location on map")
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
        Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_INDEFINITE).show()
    }
    
    private fun hideLineSettingInstructions() {
        // 移除任何正在显示的Snackbar
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showToleranceSelectionDialog() {
        val toleranceOptions = arrayOf(
            "Precision Track (25m)",
            "Street Circuit (40m)",
            "General Road (50m)", 
            "Highway (80m)"
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
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Track Tolerance")
            .setSingleChoiceItems(toleranceOptions, currentIndex) { dialog, which ->
                val selectedTolerance = toleranceValues[which]
                val selectedName = toleranceOptions[which]
                
                viewModel.setTolerance(selectedTolerance)
                updateToleranceButton(selectedName, selectedTolerance)
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateToleranceButton(@Suppress("UNUSED_PARAMETER") name: String, tolerance: Double) {
        val shortName = when {
            tolerance <= 25.0 -> "Precision (${tolerance.toInt()}m)"
            tolerance <= 40.0 -> "Street (${tolerance.toInt()}m)"
            tolerance <= 50.0 -> "Road (${tolerance.toInt()}m)"
            else -> "Highway (${tolerance.toInt()}m)"
        }
        binding.btnTolerance.text = shortName
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
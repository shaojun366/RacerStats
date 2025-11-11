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
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.btnStartFinish.setOnClickListener {
            when (viewModel.recordingState.value) {
                is TrackRecorder.RecordingState.Idle -> {
                    if (checkLocationPermissions()) {
                        viewModel.startRecording()
                    } else {
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
        when (state) {
            is TrackRecorder.RecordingState.Idle -> {
                binding.btnStartFinish.setText(R.string.start_recording)
                binding.btnPause.visibility = View.GONE
                binding.btnSetLine.visibility = View.GONE
                binding.tvDistance.text = "0.0 km"
                binding.tvPointCount.text = "0 points"
            }
            is TrackRecorder.RecordingState.Recording -> {
                binding.btnStartFinish.setText(R.string.finish)
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.setText(R.string.pause)
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
            }
            is TrackRecorder.RecordingState.Paused -> {
                binding.btnStartFinish.setText(R.string.finish)
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.setText(R.string.resume)
                binding.btnSetLine.visibility = View.VISIBLE
                updateTrackStats(state.distance, state.pointCount)
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
        map = googleMap
        setupMapLocation()
        
        map?.uiSettings?.apply {
            isMyLocationButtonEnabled = true
            isZoomControlsEnabled = true
            isCompassEnabled = true
        }
        
        map?.setOnMapClickListener { latLng ->
            viewModel.handleMapClick(GpsPoint(latLng.latitude, latLng.longitude))
        }
        
        // 设置默认位置（如果有最后已知位置的话）
        setDefaultMapLocation()
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
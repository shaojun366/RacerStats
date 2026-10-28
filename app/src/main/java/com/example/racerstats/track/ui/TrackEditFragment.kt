package com.example.racerstats.track.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackEditBinding
import com.example.racerstats.maps.MapSupport
import com.example.racerstats.timing.gpsDistance
import com.example.racerstats.track.TrackViewModel
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackEditEvent
import com.example.racerstats.track.model.TrackEditState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackEditFragment : Fragment() {
    private var _binding: FragmentTrackEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrackViewModel by viewModels()
    private val args: TrackEditFragmentArgs by navArgs()

    private var startLineMap: GoogleMap? = null
    private var finishLineMap: GoogleMap? = null
    private val startMarkers = mutableListOf<Marker>()
    private val finishMarkers = mutableListOf<Marker>()
    private var startPolyline: Polyline? = null
    private var finishPolyline: Polyline? = null
    private var previewMarker: Marker? = null
    private var startCameraPositioned = false
    private var finishCameraPositioned = false
    private var renderedStart: Pair<GpsPoint, GpsPoint>? = null
    private var renderedFinish: Pair<GpsPoint, GpsPoint>? = null
    private var editingStart = true
    private var previewPoint: GpsPoint? = null
    private var updatingUi = false
    private var mapsConfigured = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrackEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editingStart = savedInstanceState?.getBoolean("editing_start", true) ?: true
        if (savedInstanceState?.containsKey("preview_lat") == true) {
            previewPoint = GpsPoint(savedInstanceState.getDouble("preview_lat"), savedInstanceState.getDouble("preview_lon"))
        }
        setupUI()
        setupMapViews()
        observeViewModel()
        // The fragment's ViewModel survives rotation; reloading here would discard unsaved edits.
        if (viewModel.trackEdit.value.trackId == null && !viewModel.trackEdit.value.isLoading) {
            viewModel.loadTrackForEdit(args.trackId)
        }
    }

    private fun setupMapViews() {
        val currentBinding = binding
        mapsConfigured = MapSupport.bind(this, R.id.mapStartLine, binding.mapStatus, binding.routePreviewStart, OnMapReadyCallback { map ->
            if (_binding !== currentBinding) return@OnMapReadyCallback
            startLineMap = map
            setupMap(map, true)
            updateUI(viewModel.trackEdit.value)
        })
        MapSupport.bind(this, R.id.mapFinishLine, binding.mapStatus, binding.routePreviewFinish, OnMapReadyCallback { map ->
            if (_binding !== currentBinding) return@OnMapReadyCallback
            finishLineMap = map
            setupMap(map, false)
            updateUI(viewModel.trackEdit.value)
        })
    }

    private fun setupMap(map: GoogleMap, isStart: Boolean) {
        map.mapType = GoogleMap.MAP_TYPE_HYBRID
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }
        map.setOnMapClickListener { point ->
            if (!viewModel.trackEdit.value.isSettingLine || editingStart != isStart || viewModel.isSaving.value) {
                return@setOnMapClickListener
            }
            val gpsPoint = GpsPoint(point.latitude, point.longitude)
            if (previewPoint == null) {
                previewPoint = gpsPoint
            } else {
                previewPoint = null
            }
            if (isStart) viewModel.handleStartLineClick(gpsPoint) else viewModel.handleFinishLineClick(gpsPoint)
            updateUI(viewModel.trackEdit.value)
        }
    }

    private fun setupUI() = with(binding) {
        toolbar.setNavigationContentDescription(R.string.editor_back)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        etName.doAfterTextChanged { text ->
            if (!updatingUi) {
                tilName.error = null
                viewModel.updateTrackName(text?.toString().orEmpty())
            }
        }
        cbSeparateFinishLine.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setSeparateFinishLine(checked)
        }
        btnSetStartLine.setOnClickListener { beginLineSelection(true) }
        btnSetFinishLine.setOnClickListener { beginLineSelection(false) }
        fabSave.setOnClickListener { saveChanges() }
    }

    private fun beginLineSelection(isStart: Boolean) {
        editingStart = isStart
        previewPoint = null
        clearPreview()
        if (isStart) viewModel.startSettingStartLine() else viewModel.startSettingFinishLine()
        updateUI(viewModel.trackEdit.value)
    }

    private fun saveChanges() {
        val state = viewModel.trackEdit.value
        if (viewModel.isSaving.value || state.isLoading || state.isSettingLine) return
        if (state.name.isBlank()) {
            binding.tilName.error = getString(R.string.error_track_name_empty)
            binding.etName.requestFocus()
            return
        }
        val lines = listOfNotNull(state.startLine, state.finishLine.takeIf { state.hasSeparateFinishLine })
        if (lines.any { gpsDistance(it.first, it.second) !in 2.0..500.0 }) {
            showMessage(getString(R.string.editor_line_invalid))
            return
        }
        viewModel.saveTrack()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trackEdit.collectLatest { updateUI(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSaving.collectLatest { updateUI(viewModel.trackEdit.value) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is TrackEditEvent.NavigateBack -> findNavController().navigateUp()
                    is TrackEditEvent.ShowError -> Snackbar.make(binding.root,
                        getString(event.messageRes, *event.formatArgs), Snackbar.LENGTH_LONG).show()
                    is TrackEditEvent.ShowMessage -> showMessage(getString(event.messageRes, *event.formatArgs))
                }
            }
        }
    }

    private fun updateUI(state: TrackEditState) = with(binding) {
        updatingUi = true
        if (etName.text?.toString() != state.name) etName.setText(state.name)
        cbSeparateFinishLine.isChecked = state.hasSeparateFinishLine
        updatingUi = false
        finishSection.visibility = if (state.hasSeparateFinishLine) View.VISIBLE else View.GONE

        val saving = viewModel.isSaving.value
        val ready = !saving && !state.isLoading && state.trackId != null
        etName.isEnabled = ready
        cbSeparateFinishLine.isEnabled = ready && !state.isSettingLine && mapsConfigured
        btnSetStartLine.isEnabled = ready && startLineMap != null
        btnSetFinishLine.isEnabled = ready && finishLineMap != null && state.hasSeparateFinishLine
        fabSave.isEnabled = ready && !state.isSettingLine
        fabSave.setText(when {
            saving -> R.string.editor_saving
            state.isLoading -> R.string.editor_loading
            else -> R.string.editor_save
        })

        if (!state.isSettingLine) {
            previewPoint = null
            clearPreview()
        }
        updateHint(startLineHint, state.startLine, state.isSettingLine && editingStart)
        updateHint(finishLineHint, state.finishLine, state.isSettingLine && !editingStart)
        renderLine(state.startLine, true)
        renderLine(state.finishLine.takeIf { state.hasSeparateFinishLine }, false)
        routePreviewStart.setRoute(state.points, state.startLine, state.finishLine)
        routePreviewFinish.setRoute(state.points, state.startLine, state.finishLine)
        renderPreview()
        positionMaps(state)
    }

    private fun updateHint(view: android.widget.TextView, line: Pair<GpsPoint, GpsPoint>?, selecting: Boolean) {
        view.text = when {
            selecting -> getString(if (previewPoint == null) R.string.editor_first_point else R.string.editor_second_point)
            line != null -> getString(R.string.editor_saved_line, gpsDistance(line.first, line.second)) + "\n" + getString(R.string.editor_line_help)
            else -> getString(R.string.editor_line_help)
        }
        view.setTextColor(ContextCompat.getColor(requireContext(), if (selecting) R.color.accent else R.color.muted))
    }

    private fun renderLine(line: Pair<GpsPoint, GpsPoint>?, isStart: Boolean) {
        val map = (if (isStart) startLineMap else finishLineMap) ?: return
        if (line == (if (isStart) renderedStart else renderedFinish)) return
        val markers = if (isStart) startMarkers else finishMarkers
        markers.forEach { it.remove() }
        markers.clear()
        (if (isStart) startPolyline else finishPolyline)?.remove()
        var polyline: Polyline? = null
        if (line != null) {
            val positions = listOf(line.first, line.second).map { LatLng(it.latitude, it.longitude) }
            positions.forEach { point ->
                map.addMarker(MarkerOptions().position(point).icon(BitmapDescriptorFactory.defaultMarker(
                    if (isStart) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_ROSE
                )))?.let(markers::add)
            }
            polyline = map.addPolyline(PolylineOptions().addAll(positions).width(7f)
                .color(ContextCompat.getColor(requireContext(), if (isStart) R.color.accent else R.color.good)))
        }
        if (isStart) {
            startPolyline = polyline
            renderedStart = line
        } else {
            finishPolyline = polyline
            renderedFinish = line
        }
    }

    private fun renderPreview() {
        clearPreview()
        val point = previewPoint ?: return
        val map = (if (editingStart) startLineMap else finishLineMap) ?: return
        previewMarker = map.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude))
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)))
    }

    private fun positionMaps(state: TrackEditState) {
        val bounds = state.trackBounds ?: return
        val mapBounds = LatLngBounds(LatLng(bounds.southwest.latitude, bounds.southwest.longitude),
            LatLng(bounds.northeast.latitude, bounds.northeast.longitude))
        val currentBinding = binding
        if (!startCameraPositioned && startLineMap != null) currentBinding.mapStartLine.doOnLayout { view ->
            if (_binding === currentBinding && !startCameraPositioned && view.width > 0 && view.height > 0) {
                startLineMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, view.width, view.height, 48))
                startCameraPositioned = true
            }
        }
        if (!finishCameraPositioned && state.hasSeparateFinishLine && finishLineMap != null) {
            currentBinding.mapFinishLine.doOnLayout { view ->
                if (_binding === currentBinding && !finishCameraPositioned && view.width > 0 && view.height > 0) {
                    finishLineMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, view.width, view.height, 48))
                    finishCameraPositioned = true
                }
            }
        }
    }

    private fun clearPreview() {
        previewMarker?.remove()
        previewMarker = null
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("editing_start", editingStart)
        previewPoint?.let { point ->
            outState.putDouble("preview_lat", point.latitude)
            outState.putDouble("preview_lon", point.longitude)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        startLineMap?.setOnMapClickListener(null)
        finishLineMap?.setOnMapClickListener(null)
        clearPreview()
        startLineMap = null
        finishLineMap = null
        startMarkers.clear()
        finishMarkers.clear()
        startPolyline = null
        finishPolyline = null
        renderedStart = null
        renderedFinish = null
        startCameraPositioned = false
        finishCameraPositioned = false
        super.onDestroyView()
        _binding = null
    }
}

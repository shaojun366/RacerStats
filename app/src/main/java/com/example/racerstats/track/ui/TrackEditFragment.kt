package com.example.racerstats.track.ui

import android.os.Bundle
import android.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackEditBinding
import com.example.racerstats.track.TrackViewModel
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackEditEvent
import com.example.racerstats.track.model.TrackEditState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
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
    private var startLineMarkers: Pair<Marker, Marker>? = null
    private var finishLineMarkers: Pair<Marker, Marker>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupMapViews()
        setupUI()
        observeViewModel()
        
        if (args.trackId.isNotEmpty()) {
            viewModel.loadTrackForEdit(args.trackId)
        }
    }
    
    private fun setupMapViews() {
        val startLineMapFragment = childFragmentManager
            .findFragmentById(R.id.mapStartLine) as SupportMapFragment
        startLineMapFragment.getMapAsync { googleMap ->
            startLineMap = googleMap
            setupMap(googleMap, isStartLine = true)
        }
        
        val finishLineMapFragment = childFragmentManager
            .findFragmentById(R.id.mapFinishLine) as SupportMapFragment
        finishLineMapFragment.getMapAsync { googleMap ->
            finishLineMap = googleMap
            setupMap(googleMap, isStartLine = false)
        }
    }
    
    private fun setupMap(map: GoogleMap, isStartLine: Boolean) {
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }
        
        map.setOnMapClickListener { latLng ->
            if (isStartLine) {
                viewModel.handleStartLineClick(
                    GpsPoint(latLng.latitude, latLng.longitude)
                )
            } else {
                viewModel.handleFinishLineClick(
                    GpsPoint(latLng.latitude, latLng.longitude)
                )
            }
        }
    }
    
    private fun setupUI() {
        binding.apply {
            etName.doAfterTextChanged { text ->
                viewModel.updateTrackName(text?.toString() ?: "")
            }
            
            cbSeparateFinishLine.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setSeparateFinishLine(isChecked)
                updateFinishLineVisibility(isChecked)
            }
            
            btnSetStartLine.setOnClickListener {
                viewModel.startSettingStartLine()
            }
            
            btnSetFinishLine.setOnClickListener {
                viewModel.startSettingFinishLine()
            }
            
            fabSave.setOnClickListener {
                viewModel.saveTrack()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trackEdit.collectLatest { state ->
                updateUI(state)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                handleEvent(event)
            }
        }
    }
    
    private fun updateUI(state: TrackEditState) {
        binding.apply {
            if (etName.text?.toString() != state.name) {
                etName.setText(state.name)
            }
            cbSeparateFinishLine.isChecked = state.hasSeparateFinishLine
            updateFinishLineVisibility(state.hasSeparateFinishLine)
            
            // 更新起点线标记
            updateLineMarkers(
                state.startLine,
                startLineMap,
                startLineMarkers,
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            ) { markers -> startLineMarkers = markers }
            
            // 更新终点线标记
            if (state.hasSeparateFinishLine) {
                updateLineMarkers(
                    state.finishLine,
                    finishLineMap,
                    finishLineMarkers,
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                ) { markers -> finishLineMarkers = markers }
            } else {
                updateLineMarkers(
                    null,
                    finishLineMap,
                    finishLineMarkers,
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                ) { markers -> finishLineMarkers = markers }
            }
            
            // 更新按钮状态
            btnSetStartLine.isEnabled = !state.isSettingLine
            btnSetFinishLine.isEnabled = !state.isSettingLine && state.hasSeparateFinishLine
            
            // 更新地图边界
            state.trackBounds?.let { bounds ->
                val mapBounds = LatLngBounds(
                    LatLng(bounds.southwest.latitude, bounds.southwest.longitude),
                    LatLng(bounds.northeast.latitude, bounds.northeast.longitude)
                )
                startLineMap?.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(mapBounds, 100)
                )
                if (state.hasSeparateFinishLine) {
                    finishLineMap?.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(mapBounds, 100)
                    )
                }
            }
        }
    }
    
    private fun updateLineMarkers(
        line: Pair<GpsPoint, GpsPoint>?,
        map: GoogleMap?,
        currentMarkers: Pair<Marker, Marker>?,
        icon: BitmapDescriptor,
        updateMarkers: (Pair<Marker, Marker>?) -> Unit
    ) {
        // 清除现有标记
        currentMarkers?.let { (m1, m2) ->
            m1.remove()
            m2.remove()
        }
        
        // 如果有新的线，添加新标记
        if (line != null && map != null) {
            val marker1 = map.addMarker(
                MarkerOptions()
                    .position(LatLng(line.first.latitude, line.first.longitude))
                    .icon(icon)
            )
            
            val marker2 = map.addMarker(
                MarkerOptions()
                    .position(LatLng(line.second.latitude, line.second.longitude))
                    .icon(icon)
            )
            
            if (marker1 != null && marker2 != null) {
                updateMarkers(Pair(marker1, marker2))
            }
        } else {
            updateMarkers(null)
        }
    }
    
    private fun updateFinishLineVisibility(isVisible: Boolean) {
        binding.apply {
            tvFinishLine.visibility = if (isVisible) View.VISIBLE else View.GONE
            mapFinishLine.visibility = if (isVisible) View.VISIBLE else View.GONE
            btnSetFinishLine.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
    }
    
    private fun handleEvent(event: TrackEditEvent) {
        when (event) {
            is TrackEditEvent.NavigateBack -> findNavController().navigateUp()
            is TrackEditEvent.ShowError -> showError(getString(event.messageRes, *event.formatArgs))
            is TrackEditEvent.ShowMessage -> showMessage(getString(event.messageRes, *event.formatArgs))
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("OK") {}
            .show()
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

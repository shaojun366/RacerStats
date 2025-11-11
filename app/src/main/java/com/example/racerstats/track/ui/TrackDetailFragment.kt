package com.example.racerstats.track.ui

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackDetailBinding
import com.example.racerstats.track.TrackViewModel
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackDetails
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TrackDetailFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentTrackDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TrackViewModel by viewModels()
    private val args: TrackDetailFragmentArgs by navArgs()
    
    private var map: GoogleMap? = null
    private var trackPolyline: Polyline? = null
    private var startLineMarkers: Pair<Marker, Marker>? = null
    private var finishLineMarkers: Pair<Marker, Marker>? = null
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        setupClickListeners()
        observeTrackData()
    }
    
    private fun setupClickListeners() {
        binding.btnStartSession.setOnClickListener {
            Snackbar.make(binding.root, R.string.start_session, Snackbar.LENGTH_SHORT).show()
        }
        
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    findNavController().navigate(
                        TrackDetailFragmentDirections.actionTrackDetailToTrackEdit(args.trackId)
                    )
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun observeTrackData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadTrackDetails(args.trackId)
            viewModel.trackDetails.collectLatest { trackDetails ->
                trackDetails?.let { updateUI(it) }
            }
        }
    }
    
    private fun updateUI(trackDetails: TrackDetails) {
        binding.apply {
            tvTrackName.text = trackDetails.track.name
            tvLength.text = getString(R.string.track_length_format, trackDetails.track.length / 1000f)
            tvBestLap.text = getString(
                R.string.best_lap_format,
                formatTime(trackDetails.bestLapTimeMillis)
            )
            tvLastUsed.text = getString(
                R.string.last_used_format, 
                dateFormat.format(Date(trackDetails.track.createdAt))
            )
        }
        
        updateMapDisplay(trackDetails)
    }
    
    private fun updateMapDisplay(trackDetails: TrackDetails) {
        // 清除旧的标记
        trackPolyline?.remove()
        startLineMarkers?.let { (m1, m2) ->
            m1.remove()
            m2.remove()
        }
        finishLineMarkers?.let { (m1, m2) ->
            m1.remove()
            m2.remove()
        }
        
        // 绘制轨迹
        val points = trackDetails.points.map { LatLng(it.latitude, it.longitude) }
        if (points.isNotEmpty()) {
            val polylineOptions = PolylineOptions()
                .addAll(points)
                .color(resources.getColor(R.color.track_line, null))
                .width(8f)

            trackPolyline = map?.addPolyline(polylineOptions)
        }
        
        // 绘制起点线
        trackDetails.startLine?.let { line ->
            startLineMarkers = addLineMarkers(
                line.first,
                line.second,
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            )
        }
        
        // 绘制终点线
        trackDetails.finishLine?.let { line ->
            finishLineMarkers = addLineMarkers(
                line.first,
                line.second,
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }
        
        // 移动相机到轨迹中心
        if (points.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply {
                points.forEach { include(it) }
            }.build()
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }
    
    private fun addLineMarkers(
        point1: GpsPoint,
        point2: GpsPoint,
        icon: BitmapDescriptor
    ): Pair<Marker, Marker>? {
        val map = map ?: return null
        
        val marker1 = map.addMarker(
            MarkerOptions()
                .position(LatLng(point1.latitude, point1.longitude))
                .icon(icon)
        )
        
        val marker2 = map.addMarker(
            MarkerOptions()
                .position(LatLng(point2.latitude, point2.longitude))
                .icon(icon)
        )
        
        return if (marker1 != null && marker2 != null) {
            Pair(marker1, marker2)
        } else {
            null
        }
    }
    
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.delete_track_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTrack(args.trackId)
                findNavController().navigateUp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.uiSettings?.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun formatTime(timeMillis: Long?): String {
    if (timeMillis == null) return "--:--"
    val totalSeconds = timeMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = (timeMillis % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, milliseconds)
}
package com.example.racerstats.track.ui

import android.os.Bundle
import android.view.*
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import com.example.racerstats.SessionViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.racerstats.R
import com.example.racerstats.databinding.FragmentTrackDetailBinding
import com.example.racerstats.maps.MapSupport
import com.example.racerstats.timing.TimingCourse
import com.example.racerstats.track.TrackViewModel
import com.example.racerstats.track.model.GpsPoint
import com.example.racerstats.track.model.TrackDetails
import com.example.racerstats.track.model.TrackEditEvent
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
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
    private val session: SessionViewModel by activityViewModels()
    private val args: TrackDetailFragmentArgs by navArgs()
    
    private var map: GoogleMap? = null
    private var trackPolyline: Polyline? = null
    private var startLineMarkers: Pair<Marker, Marker>? = null
    private var finishLineMarkers: Pair<Marker, Marker>? = null
    private val gatePolylines = mutableListOf<Polyline>()
    
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
        
        MapSupport.bind(this, R.id.mapView, binding.mapStatus, binding.routePreview, this)
        
        setupClickListeners()
        observeTrackData()
        session.history.observe(viewLifecycleOwner) { updateBestLap() }
    }
    
    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnStartSession.setOnClickListener {
            if (session.selectTrack(args.trackId)) {
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                    .selectedItemId = R.id.nav_live
            } else {
                Snackbar.make(binding.root, R.string.track_selection_locked, Snackbar.LENGTH_LONG).show()
            }
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deletedTrackId.collectLatest { deletedId ->
                    if (deletedId == args.trackId) findNavController().navigateUp()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                if (event is TrackEditEvent.ShowError) {
                    Snackbar.make(binding.root, getString(event.messageRes, *event.formatArgs), Snackbar.LENGTH_LONG).show()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trackDetails.collectLatest { trackDetails ->
                    trackDetails?.let { updateUI(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Editing uses another ViewModel; reload when returning so names and gates stay current.
        viewModel.loadTrackDetails(args.trackId)
    }
    
    private fun updateUI(trackDetails: TrackDetails) {
        binding.apply {
            tvTrackName.text = trackDetails.track.name
            tvLength.text = getString(R.string.track_distance_value, trackDetails.track.length / 1000f)
            tvBestLap.text = formatTime(session.bestLapForTrack(args.trackId))
            tvPointCount.text = getString(R.string.track_detail_points, trackDetails.points.size)
            tvCourseType.setText(when {
                trackDetails.startLine == null -> R.string.track_missing_gate
                isClosed(trackDetails) -> R.string.track_closed_course
                else -> R.string.track_open_course
            })
            tvLastUsed.text = getString(
                R.string.track_created_short,
                dateFormat.format(Date(trackDetails.track.createdAt))
            )
            btnStartSession.isEnabled = trackDetails.startLine != null && trackDetails.track.length >= 100f
            routePreview.setRoute(trackDetails.points, trackDetails.startLine, trackDetails.finishLine)
        }
        
        updateMapDisplay(trackDetails)
    }

    private fun updateBestLap() {
        _binding?.tvBestLap?.text = formatTime(session.bestLapForTrack(args.trackId))
    }

    private fun isClosed(details: TrackDetails): Boolean = details.startLine?.let {
        TimingCourse(it, details.finishLine, details.track.length).isClosed
    } ?: false
    
    private fun updateMapDisplay(trackDetails: TrackDetails) {
        if (map == null) return
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
        gatePolylines.forEach { it.remove() }
        gatePolylines.clear()
        
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
            drawGate(line, R.color.good)
        }
        
        // 绘制终点线
        trackDetails.finishLine?.takeUnless { isClosed(trackDetails) }?.let { line ->
            finishLineMarkers = addLineMarkers(
                line.first,
                line.second,
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            drawGate(line, R.color.bad)
        }
        
        // 移动相机到轨迹中心
        if (points.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply {
                points.forEach { include(it) }
            }.build()
            val currentMap = map
            binding.mapView.doOnLayout { mapView ->
                if (_binding != null && map === currentMap && mapView.width > 0 && mapView.height > 0) {
                    currentMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(
                        bounds, mapView.width, mapView.height,
                        (40 * resources.displayMetrics.density).toInt().coerceAtMost(mapView.width / 4)
                    ))
                }
            }
        }
    }

    private fun drawGate(line: Pair<GpsPoint, GpsPoint>, color: Int) {
        map?.addPolyline(PolylineOptions()
            .add(LatLng(line.first.latitude, line.first.longitude), LatLng(line.second.latitude, line.second.longitude))
            .color(resources.getColor(color, null)).width(8f))?.let(gatePolylines::add)
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
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.track_delete_named, viewModel.trackDetails.value?.track?.name.orEmpty()))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTrack(args.trackId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (_binding == null) return
        map = googleMap
        map?.uiSettings?.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }
        viewModel.trackDetails.value?.let { updateUI(it) }
    }

    override fun onDestroyView() {
        map = null
        trackPolyline = null
        startLineMarkers = null
        finishLineMarkers = null
        gatePolylines.clear()
        super.onDestroyView()
        _binding = null
    }
}

private fun formatTime(timeMillis: Long?): String {
    return timeMillis?.let { com.example.racerstats.formatLapTime(it) } ?: "--:--.---"
}

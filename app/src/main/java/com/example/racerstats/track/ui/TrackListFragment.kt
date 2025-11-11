package com.example.racerstats.track.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.racerstats.databinding.FragmentTrackListBinding
import com.example.racerstats.track.TrackViewModel
import com.example.racerstats.track.model.Track
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackListFragment : Fragment() {
    
    private var _binding: FragmentTrackListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TrackViewModel by viewModels()
    private val trackAdapter = TrackListAdapter(
        onTrackClick = { track -> navigateToTrackDetail(track) },
        onTrackEdit = { track -> navigateToTrackEdit(track) },
        onTrackDelete = { track -> showDeleteConfirmation(track) }
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        binding.rvTracks.adapter = trackAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddTrack.setOnClickListener {
            findNavController().navigate(
                TrackListFragmentDirections.actionTrackListToTrackRecording()
            )
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.tracks.collectLatest { tracks ->
                trackAdapter.submitList(tracks)
                updateEmptyState(tracks.isEmpty())
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
    
    private fun navigateToTrackDetail(track: Track) {
        findNavController().navigate(
            TrackListFragmentDirections.actionTrackListToTrackDetail(track.id)
        )
    }
    
    private fun navigateToTrackEdit(track: Track) {
        findNavController().navigate(
            TrackListFragmentDirections.actionTrackListToTrackEdit(track.id)
        )
    }
    
    private fun showDeleteConfirmation(track: Track) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("Are you sure you want to delete this track?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTrack(track.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
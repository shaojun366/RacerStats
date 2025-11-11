package com.example.racerstats.track.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.racerstats.R
import com.example.racerstats.databinding.ItemTrackBinding
import com.example.racerstats.track.model.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackListAdapter(
    private val onTrackClick: (Track) -> Unit,
    private val onTrackEdit: (Track) -> Unit,
    private val onTrackDelete: (Track) -> Unit
) : ListAdapter<Track, TrackListAdapter.ViewHolder>(TrackDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        fun bind(track: Track) {
            binding.apply {
                root.setOnClickListener { onTrackClick(track) }

                tvTrackName.text = track.name
                tvLength.text = itemView.context.getString(
                    R.string.track_length_format,
                    track.length / 1000f
                )
                tvLastUsed.text = itemView.context.getString(
                    R.string.last_used_format,
                    dateFormat.format(Date(track.createdAt))
                )

                btnMore.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.track_item_menu)
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit -> {
                                    onTrackEdit(track)
                                    true
                                }
                                R.id.action_delete -> {
                                    onTrackDelete(track)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
}

private class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean {
        return oldItem == newItem
    }
}

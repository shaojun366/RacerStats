package com.example.racerstats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.racerstats.databinding.FragmentReviewBinding
import com.example.racerstats.history.summarizeLaps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewFragment : Fragment(R.layout.fragment_review) {
    private val model: SessionViewModel by activityViewModels()
    private var _binding: FragmentReviewBinding? = null
    private val adapter = LapAdapter()
    private var trackFilter: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentReviewBinding.bind(view)
        trackFilter = savedInstanceState?.getString("trackFilter") ?: trackFilter
        val ui = _binding!!
        ui.recyclerLaps.layoutManager = LinearLayoutManager(requireContext())
        ui.recyclerLaps.adapter = adapter
        ui.btnFilterTrack.setOnClickListener { showTrackFilter() }
        ui.btnClearHistory.setOnClickListener {
            val current = trackFilter
            AlertDialog.Builder(requireContext()).setTitle("清空圈速记录？")
                .setMessage(if (current == null) "将删除全部已保存的圈速记录。此操作无法撤销。"
                    else "仅删除当前筛选条件下的圈速记录。此操作无法撤销。")
                .setPositiveButton("清空记录") { _, _ ->
                    model.clearHistory(current?.takeUnless { it == MANUAL }, current == MANUAL)
                }.setNegativeButton("取消", null).show()
        }
        model.history.observe(viewLifecycleOwner) { renderHistory(it) }
    }

    private fun renderHistory(laps: List<RecordedLap>) {
        val ui = _binding ?: return
        val summary = summarizeLaps(laps, trackFilter?.takeUnless { it == MANUAL }, trackFilter == MANUAL)
        val filtered = summary.laps
        val names = trackNames(laps)
        ui.btnFilterTrack.text = when (val filter = trackFilter) {
            null -> getString(R.string.filter_all_tracks)
            MANUAL -> getString(R.string.filter_manual)
            else -> names[filter] ?: filter
        }
        ui.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        ui.tvEmpty.setText(if (trackFilter == null) R.string.history_empty else R.string.track_history_empty)
        ui.btnClearHistory.isEnabled = filtered.isNotEmpty()
        ui.tvTotalLaps.text = filtered.size.toString()
        ui.tvHistoryBest.text = summary.bestTime?.let(::formatLapTime) ?: "--:--.---"
        ui.tvHistoryBestLabel.setText(when {
            !summary.comparable -> R.string.best_select_track
            summary.manualOnly -> R.string.best_manual_label
            else -> R.string.best_track_label
        })
        val sessionBest = filtered.groupBy { it.sessionId }.mapValues { (_, list) -> list.minOf { it.duration } }
        adapter.submitList(filtered.asReversed().map { LapRow(it, sessionBest.getValue(it.sessionId)) })
    }

    private fun trackNames(laps: List<RecordedLap>): Map<String, String> = laps.asReversed()
        .filter { it.trackId != null }.distinctBy { it.trackId }
        .associate { it.trackId!! to (it.trackName ?: "未命名赛道") }

    private fun showTrackFilter() {
        val tracks = trackNames(model.history.value.orEmpty()).toList()
        val ids = listOf(null, MANUAL) + tracks.map { it.first }
        val labels = listOf(getString(R.string.filter_all_tracks), getString(R.string.filter_manual)) + tracks.map { it.second }
        AlertDialog.Builder(requireContext()).setTitle(R.string.filter_track_title)
            .setSingleChoiceItems(labels.toTypedArray(), ids.indexOf(trackFilter)) { dialog, which ->
                trackFilter = ids[which]
                renderHistory(model.history.value.orEmpty())
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("trackFilter", trackFilter)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding?.recyclerLaps?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private data class LapRow(val lap: RecordedLap, val best: Long)
    private class LapAdapter : ListAdapter<LapRow, LapAdapter.Holder>(object : DiffUtil.ItemCallback<LapRow>() {
        override fun areItemsTheSame(old: LapRow, new: LapRow) = old.lap.id == new.lap.id
        override fun areContentsTheSame(old: LapRow, new: LapRow) = old == new
    }) {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.tvLapNumber)
            val time: TextView = view.findViewById(R.id.tvTime)
            val delta: TextView = view.findViewById(R.id.tvDelta)
            val date: TextView = view.findViewById(R.id.tvLapDate)
            val track: TextView = view.findViewById(R.id.tvLapTrack)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_lap, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (lap, best) = getItem(position)
            val context = holder.itemView.context
            holder.number.text = "第 ${lap.number} 圈"
            holder.time.text = formatLapTime(lap.duration)
            holder.delta.text = if (lap.duration == best) "本次最佳" else String.format(Locale.US, "+%.3f s", (lap.duration - best) / 1000.0)
            holder.date.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lap.createdAt))
            holder.track.text = if (lap.trackId != null) context.getString(R.string.automatic_lap_label, lap.trackName ?: "未命名赛道")
                else context.getString(R.string.manual_lap_label)
            holder.time.setTextColor(ContextCompat.getColor(context, if (lap.duration == best) R.color.good else R.color.text))
            holder.delta.setTextColor(ContextCompat.getColor(context, if (lap.duration == best) R.color.good else R.color.muted))
        }
    }

    companion object { private const val MANUAL = "__manual__" }
}

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewFragment : Fragment(R.layout.fragment_review) {
    private val model: SessionViewModel by activityViewModels()
    private var _binding: FragmentReviewBinding? = null
    private val adapter = LapAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentReviewBinding.bind(view)
        val ui = _binding!!
        ui.recyclerLaps.layoutManager = LinearLayoutManager(requireContext())
        ui.recyclerLaps.adapter = adapter
        ui.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("清空圈速记录？")
                .setMessage("将删除已保存的圈速记录。此操作无法撤销。")
                .setPositiveButton("清空记录") { _, _ -> model.clearHistory() }
                .setNegativeButton("取消", null).show()
        }
        model.history.observe(viewLifecycleOwner) { laps ->
            ui.tvEmpty.visibility = if (laps.isEmpty()) View.VISIBLE else View.GONE
            ui.btnClearHistory.isEnabled = laps.isNotEmpty()
            ui.tvTotalLaps.text = laps.size.toString()
            ui.tvHistoryBest.text = laps.minOfOrNull { it.duration }?.let(::formatLapTime) ?: "--:--.---"
            val sessionBest = laps.groupBy { it.sessionId }.mapValues { (_, list) -> list.minOf { it.duration } }
            adapter.submitList(laps.asReversed().map { LapRow(it, sessionBest.getValue(it.sessionId)) })
        }
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
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_lap, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (lap, best) = getItem(position)
            holder.number.text = "第 ${lap.number} 圈"
            holder.time.text = formatLapTime(lap.duration)
            holder.delta.text = if (lap.duration == best) "本次最佳" else String.format(Locale.US, "+%.3f s", (lap.duration - best) / 1000.0)
            holder.date.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lap.createdAt))
            val color = if (lap.duration == best) R.color.good else R.color.text
            holder.time.setTextColor(ContextCompat.getColor(holder.itemView.context, color))
            holder.delta.setTextColor(ContextCompat.getColor(holder.itemView.context, if (lap.duration == best) R.color.good else R.color.muted))
        }
    }
}

package com.example.racerstats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ReviewFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private val lapAdapter = LapAdapter()
    private val laps = mutableListOf<LapData>()

    data class LapData(
        val lapNumber: Int,
        val time: String,
        val delta: String,
        val isBest: Boolean = false
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_review, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.recyclerLaps)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = lapAdapter
    }

    fun addLap(time: Long, bestTime: Long) {
        val lapNumber = laps.size + 1
        val delta = time - bestTime
        val isBest = time < bestTime
        
        val lapData = LapData(
            lapNumber = lapNumber,
            time = formatTime(time),
            delta = "%+.3fs".format(delta / 1000.0),
            isBest = isBest
        )
        
        laps.add(0, lapData)
        lapAdapter.notifyItemInserted(0)
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 60000).toInt()
        val seconds = ((millis % 60000) / 1000).toInt()
        val milliseconds = (millis % 1000).toInt()
        return "%02d:%02d.%03d".format(minutes, seconds, milliseconds)
    }

    inner class LapAdapter : RecyclerView.Adapter<LapAdapter.LapViewHolder>() {
        inner class LapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvLapNumber: TextView = view.findViewById(R.id.tvLapNumber)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvDelta: TextView = view.findViewById(R.id.tvDelta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LapViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lap, parent, false)
            return LapViewHolder(view)
        }

        override fun onBindViewHolder(holder: LapViewHolder, position: Int) {
            val lap = laps[position]
            holder.tvLapNumber.text = "Lap ${lap.lapNumber}"
            holder.tvTime.text = lap.time
            holder.tvDelta.text = lap.delta
            
            if (lap.isBest) {
                holder.tvTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.good))
            } else {
                holder.tvTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
            }
        }

        override fun getItemCount() = laps.size
    }
}
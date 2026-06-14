package com.shadowtalk

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingAdapter(
    private var recordings: List<MainActivity.RecordingEntry>,
    private val onItemClick: (MainActivity.RecordingEntry) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US)

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvRecordingDate)
        val tvName: TextView = view.findViewById(R.id.tvRecordingName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.tvDate.text = dateFormat.format(recording.date)
        holder.tvName.text = recording.name
        holder.itemView.setOnClickListener { onItemClick(recording) }
    }

    override fun getItemCount(): Int = recordings.size

    fun updateData(newRecordings: List<MainActivity.RecordingEntry>) {
        recordings = newRecordings
        notifyDataSetChanged()
    }
}

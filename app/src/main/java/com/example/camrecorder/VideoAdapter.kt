package com.example.camrecorder

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.camrecorder.VideoListActivity.VideoItem

class VideoAdapter(
    private val onClick: (Uri) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DiffCallback) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VideoViewHolder(view, onClick)
    }
    
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class VideoViewHolder(
        itemView: android.view.View,
        private val onClick: (Uri) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val titleView = itemView.findViewById<TextView>(android.R.id.text1)
        private val subtitleView = itemView.findViewById<TextView>(android.R.id.text2)
        
        fun bind(video: VideoItem) {
            titleView.text = video.name
            val durationMinutes = video.duration / 1000 / 60
            val durationSeconds = (video.duration / 1000) % 60
            subtitleView.text = String.format("%02d:%02d", durationMinutes, durationSeconds)
            
            itemView.setOnClickListener {
                onClick(video.uri)
            }
        }
    }
    
    companion object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}

package com.simplevlc.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simplevlc.databinding.ItemQuickVideoBinding
import com.simplevlc.model.Video
import com.simplevlc.utils.TimeUtils

class QuickListAdapter(
    private val onVideoClick: (Int) -> Unit
) : ListAdapter<Video, QuickListAdapter.QuickViewHolder>(VideoDiffCallback()) {

    private var fullList: List<Video> = emptyList()
    private var currentVideoUri: String? = null

    fun submitFullList(list: List<Video>) {
        fullList = list
        submitList(list)
    }

    fun filter(query: String) {
        val filtered = if (query.isBlank()) {
            fullList
        } else {
            fullList.filter { video ->
                video.displayName.contains(query, ignoreCase = true)
            }
        }
        submitList(filtered)
    }

    fun setCurrentVideo(uri: String?) {
        val oldUri = currentVideoUri
        currentVideoUri = uri
        
        // Find and notify changed items
        currentList.forEachIndexed { index, video ->
            val videoUri = video.uri.toString()
            if (videoUri == oldUri || videoUri == uri) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickViewHolder {
        val binding = ItemQuickVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickViewHolder, position: Int) {
        val video = getItem(position)
        val isCurrentVideo = video.uri.toString() == currentVideoUri
        holder.bind(video, isCurrentVideo)
        holder.setOnClickListener {
            onVideoClick(position)
        }
    }

    class QuickViewHolder(
        private val binding: ItemQuickVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        companion object {
            private val HIGHLIGHT_COLOR = Color.parseColor("#33FFFFFF")
            private val NORMAL_COLOR = Color.TRANSPARENT
        }

        fun bind(video: Video, isCurrentVideo: Boolean) {
            binding.textViewName.text = video.displayName
            binding.textViewDuration.text = TimeUtils.formatDurationShort(video.duration)
            
            // Highlight current playing video
            binding.root.setBackgroundColor(
                if (isCurrentVideo) HIGHLIGHT_COLOR else NORMAL_COLOR
            )
        }

        fun setOnClickListener(listener: (() -> Unit)?) {
            binding.root.setOnClickListener { listener?.invoke() }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem == newItem
        }
    }
}

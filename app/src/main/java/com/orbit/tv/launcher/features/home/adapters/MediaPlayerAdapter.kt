package com.orbit.tv.launcher.features.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemMediaPlayerAppBinding
import com.orbit.tv.launcher.features.home.model.MediaPlayerApp

class MediaPlayerAdapter(
    private val onAppClick: (MediaPlayerApp) -> Unit
) : ListAdapter<MediaPlayerApp, MediaPlayerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaPlayerAppBinding.inflate(
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
        private val binding: ItemMediaPlayerAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.root, scaleRatio = 1.04f, elevationDp = 6f)
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(pos))
                }
            }
        }

        fun bind(item: MediaPlayerApp) {
            binding.tvPlayerName.text = item.name
            binding.tvPlayerCategory.text = item.categoryLabel

            if (item.icon != null) {
                binding.ivPlayerIcon.setImageDrawable(item.icon)
            } else {
                binding.ivPlayerIcon.setImageResource(R.drawable.ic_search)
            }

            if (item.isSystemSearch) {
                binding.tvPlayerBadge.text = itemView.context.getString(R.string.watch_badge_search)
                binding.tvPlayerBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.white_80))
            } else {
                binding.tvPlayerBadge.text = itemView.context.getString(R.string.watch_badge_open)
                binding.tvPlayerBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.leanback_cyan))
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MediaPlayerApp>() {
        override fun areItemsTheSame(oldItem: MediaPlayerApp, newItem: MediaPlayerApp): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: MediaPlayerApp, newItem: MediaPlayerApp): Boolean {
            return oldItem == newItem
        }
    }
}

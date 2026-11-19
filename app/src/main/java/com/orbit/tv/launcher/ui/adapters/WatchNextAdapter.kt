package com.orbit.tv.launcher.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Precision
import coil.size.ViewSizeResolver
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemWatchNextCardBinding
import com.orbit.tv.launcher.platform.tvprovider.WatchNextItem

class WatchNextAdapter(
    private val onItemClick: (WatchNextItem) -> Unit
) : ListAdapter<WatchNextItem, WatchNextAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemWatchNextCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchNextCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvWatchNextTitle.text = item.title
            tvWatchNextApp.text = item.appName

            val subtitleText = item.episodeTitle ?: if (item.progressPercent > 0) {
                "${item.progressPercent}% просмотрено"
            } else ""
            tvWatchNextSubtitle.text = subtitleText
            tvWatchNextSubtitle.visibility = if (subtitleText.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE

            pbWatchNextProgress.progress = item.progressPercent
            pbWatchNextProgress.visibility = if (item.progressPercent > 0) android.view.View.VISIBLE else android.view.View.GONE

            ivWatchNextThumb.load(item.posterUri) {
                crossfade(true)
                size(ViewSizeResolver(ivWatchNextThumb))
                precision(Precision.INEXACT)
                placeholder(R.drawable.bg_media_placeholder)
                error(R.drawable.bg_media_placeholder)
            }

            TvFocusHelper.bindFocusScale(root, scaleRatio = 1.05f, elevationDp = 6f)

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<WatchNextItem>() {
        override fun areItemsTheSame(oldItem: WatchNextItem, newItem: WatchNextItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WatchNextItem, newItem: WatchNextItem): Boolean {
            return oldItem == newItem
        }
    }
}

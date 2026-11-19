package com.orbit.tv.launcher.features.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemMediaCardBinding
import com.orbit.tv.launcher.features.home.model.MediaModel

class MediaAdapter(
    private val onMediaClick: (MediaModel) -> Unit
) : ListAdapter<MediaModel, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding, onMediaClick)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.resetTransform()
    }

    class MediaViewHolder(
        private val binding: ItemMediaCardBinding,
        private val onMediaClick: (MediaModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.cardContainer, scaleRatio = 1.05f, elevationDp = 0f)
        }

        fun resetTransform() {
            binding.cardContainer.animate().cancel()
            val hasFocus = binding.cardContainer.isFocused
            val targetScale = if (hasFocus) 1.05f else 1.0f
            binding.cardContainer.scaleX = targetScale
            binding.cardContainer.scaleY = targetScale
            binding.cardContainer.translationZ = 0f
            binding.cardContainer.elevation = 0f
            binding.cardContainer.translationX = 0f
            binding.cardContainer.translationY = 0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                binding.cardContainer.outlineAmbientShadowColor = android.graphics.Color.TRANSPARENT
                binding.cardContainer.outlineSpotShadowColor = android.graphics.Color.TRANSPARENT
            }
        }

        fun bind(media: MediaModel) {
            resetTransform()
            binding.tvTitle.text = media.title
            binding.tvTitle.paintFlags = binding.tvTitle.paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
            binding.tvBadge.text = media.badge
            binding.tvBadge.paintFlags = binding.tvBadge.paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG

            when {
                media.id.startsWith("app_") -> {
                    val pkg = media.id.removePrefix("app_")
                    try {
                        val icon = binding.root.context.packageManager.getApplicationIcon(pkg)
                        binding.ivPoster.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        binding.ivPoster.setImageDrawable(icon)
                    } catch (_: Exception) {
                        binding.ivPoster.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        binding.ivPoster.setImageResource(R.drawable.ic_nav_apps)
                    }
                }
                media.id.startsWith("iptv_") -> {
                    if (media.posterUrl.isNotBlank()) {
                        binding.ivPoster.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        binding.ivPoster.load(media.posterUrl) {
                            crossfade(true)
                            placeholder(R.drawable.bg_iptv_card_fallback)
                            error(R.drawable.bg_iptv_card_fallback)
                        }
                    } else {
                        binding.ivPoster.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        binding.ivPoster.setImageResource(R.drawable.bg_iptv_card_fallback)
                    }
                }
                else -> {
                    binding.ivPoster.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    binding.ivPoster.load(media.posterUrl) {
                        crossfade(true)
                        placeholder(R.drawable.bg_media_placeholder)
                        error(R.drawable.bg_media_placeholder)
                    }
                }
            }

            binding.cardContainer.setOnClickListener {
                onMediaClick(media)
            }
        }
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaModel>() {
        override fun areItemsTheSame(oldItem: MediaModel, newItem: MediaModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaModel, newItem: MediaModel): Boolean {
            return oldItem == newItem
        }
    }
}

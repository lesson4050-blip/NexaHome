package com.orbit.tv.launcher.features.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemRecommendedAppCardBinding
import com.orbit.tv.launcher.features.home.model.RecommendedAppModel

class RecommendedAppsAdapter(
    private val onAppAction: (RecommendedAppModel) -> Unit
) : ListAdapter<RecommendedAppModel, RecommendedAppsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecommendedAppCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onAppAction)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.resetTransform()
    }

    class ViewHolder(
        private val binding: ItemRecommendedAppCardBinding,
        private val onAppAction: (RecommendedAppModel) -> Unit
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

        fun bind(item: RecommendedAppModel) {
            resetTransform()
            val ctx = binding.root.context
            binding.tvAppName.text = item.name
            binding.tvAppName.paintFlags = binding.tvAppName.paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
            binding.tvCategoryTag.text = item.category
            binding.tvAppDescription.text = item.description
            binding.tvAppDescription.paintFlags = binding.tvAppDescription.paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG

            if (item.installedIcon != null) {
                binding.ivAppIcon.setImageDrawable(item.installedIcon)
            } else {
                binding.ivAppIcon.setImageResource(item.iconRes)
            }

            if (item.isInstalled) {
                binding.tvActionBadge.text = ctx.getString(R.string.recommended_open)
                binding.tvActionBadge.setTextColor(ContextCompat.getColor(ctx, R.color.vpn_emerald))
            } else {
                binding.tvActionBadge.text = ctx.getString(R.string.recommended_install)
                binding.tvActionBadge.setTextColor(ContextCompat.getColor(ctx, R.color.leanback_cyan))
            }

            binding.cardContainer.setOnClickListener {
                onAppAction(item)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<RecommendedAppModel>() {
        override fun areItemsTheSame(oldItem: RecommendedAppModel, newItem: RecommendedAppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: RecommendedAppModel, newItem: RecommendedAppModel): Boolean {
            return oldItem == newItem
        }
    }
}

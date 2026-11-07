package com.orbit.tv.launcher.features.home.adapters

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemAppCardBinding
import com.orbit.tv.launcher.features.home.model.AppModel

class AppsAdapter(
    private val showPinBadge: Boolean = true,
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: ((AppModel) -> Unit)? = null
) : ListAdapter<AppModel, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    constructor(onAppClick: (AppModel) -> Unit) : this(true, onAppClick, null)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding, showPinBadge, onAppClick, onAppLongClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppCardBinding,
        private val showPinBadge: Boolean,
        private val onAppClick: (AppModel) -> Unit,
        private val onAppLongClick: ((AppModel) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.cardContainer, scaleRatio = 1.06f, elevationDp = 8f)
        }

        fun bind(app: AppModel) {
            binding.tvAppLabel.text = app.label
            if (app.iconDrawable != null) {
                binding.ivAppIcon.setImageDrawable(app.iconDrawable)
            } else {
                binding.ivAppIcon.setImageResource(R.drawable.ic_nav_apps)
            }

            // Pin badge indicator (shown in AppsHub, hidden on Home shelf)
            binding.ivPinIndicator.visibility = if (showPinBadge && app.isPinned) View.VISIBLE else View.GONE

            // Hidden badge & alpha
            binding.ivHiddenIndicator.visibility = if (app.isHidden) View.VISIBLE else View.GONE
            binding.cardContainer.alpha = if (app.isHidden) 0.55f else 1.0f

            binding.cardContainer.setOnClickListener {
                onAppClick(app)
            }

            binding.cardContainer.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }

            // TV Remote [Menu] button trigger
            binding.cardContainer.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) &&
                    event.action == KeyEvent.ACTION_DOWN) {
                    onAppLongClick?.invoke(app)
                    true
                } else {
                    false
                }
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}

package com.orbit.tv.launcher.features.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemAppCardBinding
import com.orbit.tv.launcher.features.home.model.AppModel

class AppsAdapter(
    private val onAppClick: (AppModel) -> Unit
) : ListAdapter<AppModel, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppCardBinding,
        private val onAppClick: (AppModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.cardContainer, scaleRatio = 1.06f, elevationDp = 8f)
        }

        fun bind(app: AppModel) {
            binding.tvAppLabel.text = app.label
            if (app.iconDrawable != null) {
                binding.ivAppIcon.setImageDrawable(app.iconDrawable)
            } else {
                binding.ivAppIcon.setImageResource(com.orbit.tv.launcher.R.drawable.ic_nav_apps)
            }

            binding.cardContainer.setOnClickListener {
                onAppClick(app)
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

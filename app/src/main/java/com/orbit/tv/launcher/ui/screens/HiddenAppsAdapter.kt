package com.orbit.tv.launcher.ui.screens

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemHiddenAppCardBinding
import com.orbit.tv.launcher.features.home.model.AppModel

class HiddenAppsAdapter(
    private val onUnhideClick: (AppModel) -> Unit
) : ListAdapter<AppModel, HiddenAppsAdapter.HiddenAppViewHolder>(HiddenAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiddenAppViewHolder {
        val binding = ItemHiddenAppCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HiddenAppViewHolder(binding, onUnhideClick)
    }

    override fun onBindViewHolder(holder: HiddenAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HiddenAppViewHolder(
        private val binding: ItemHiddenAppCardBinding,
        private val onUnhideClick: (AppModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.cardContainer, scaleRatio = 1.08f, elevationDp = 8f)
        }

        fun bind(app: AppModel) {
            binding.tvAppName.text = app.label
            val icon = app.iconDrawable ?: try {
                binding.root.context.packageManager.getApplicationIcon(app.packageName)
            } catch (_: Exception) {
                null
            }
            if (icon != null) {
                binding.ivAppIcon.setImageDrawable(icon)
            }
            binding.cardContainer.setOnClickListener {
                onUnhideClick(app)
            }
        }
    }

    private class HiddenAppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName && oldItem.label == newItem.label
        }
    }
}

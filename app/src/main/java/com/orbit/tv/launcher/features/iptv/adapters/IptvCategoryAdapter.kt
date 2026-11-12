package com.orbit.tv.launcher.features.iptv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemIptvCategoryBinding
import com.orbit.tv.launcher.features.iptv.model.IptvCategory

class IptvCategoryAdapter(
    private val onCategorySelected: (IptvCategory) -> Unit
) : ListAdapter<IptvCategory, IptvCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    var selectedCategoryId: String = "All"
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemIptvCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding, onCategorySelected)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id.equals(selectedCategoryId, ignoreCase = true))
    }

    class CategoryViewHolder(
        private val binding: ItemIptvCategoryBinding,
        private val onCategorySelected: (IptvCategory) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.categoryCard, scaleRatio = 1.05f, elevationDp = 6f)
        }

        fun bind(category: IptvCategory, isSelected: Boolean) {
            binding.tvCategoryName.text = category.name
            binding.tvCategoryCount.text = category.count.toString()
            binding.categoryCard.isSelected = isSelected

            binding.categoryCard.setOnClickListener {
                onCategorySelected(category)
            }
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<IptvCategory>() {
        override fun areItemsTheSame(oldItem: IptvCategory, newItem: IptvCategory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: IptvCategory, newItem: IptvCategory): Boolean {
            return oldItem == newItem
        }
    }
}

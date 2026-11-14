package com.orbit.tv.launcher.ui.screens

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemGenreChipBinding

data class SettingsChip(
    val id: String,
    val title: String
)

class SettingsChipAdapter(
    private val onChipClick: (SettingsChip) -> Unit
) : RecyclerView.Adapter<SettingsChipAdapter.ChipViewHolder>() {

    private var items: List<SettingsChip> = emptyList()
    var selectedId: String = ""
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun submitList(list: List<SettingsChip>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemGenreChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChipViewHolder(binding, onChipClick)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == selectedId)
    }

    class ChipViewHolder(
        private val binding: ItemGenreChipBinding,
        private val onChipClick: (SettingsChip) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.chipContainer, scaleRatio = 1.08f, elevationDp = 6f)
        }

        fun bind(chip: SettingsChip, isSelected: Boolean) {
            binding.tvGenreName.text = chip.title
            binding.chipContainer.isSelected = isSelected
            binding.chipContainer.setOnClickListener {
                onChipClick(chip)
            }
        }
    }
}

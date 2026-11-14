package com.orbit.tv.launcher.features.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemGenreChipBinding
import com.orbit.tv.launcher.features.home.model.GenreFilter

class GenreAdapter(
    private val onGenreClick: (GenreFilter) -> Unit
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {

    private var items: List<GenreFilter> = emptyList()
    var selectedGenreId: String = "all"
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun submitList(list: List<GenreFilter>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val binding = ItemGenreChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GenreViewHolder(binding, onGenreClick)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == selectedGenreId)
    }

    class GenreViewHolder(
        private val binding: ItemGenreChipBinding,
        private val onGenreClick: (GenreFilter) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.chipContainer, scaleRatio = 1.08f, elevationDp = 6f)
        }

        fun bind(genre: GenreFilter, isSelected: Boolean) {
            binding.tvGenreName.setText(genre.nameRes)
            binding.chipContainer.isSelected = isSelected
            binding.chipContainer.setOnClickListener {
                onGenreClick(genre)
            }
        }
    }
}

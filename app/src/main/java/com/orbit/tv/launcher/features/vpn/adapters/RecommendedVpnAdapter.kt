package com.orbit.tv.launcher.features.vpn.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemRecommendedVpnBinding
import com.orbit.tv.launcher.features.vpn.model.RecommendedVpnApp

class RecommendedVpnAdapter(
    private val onItemClick: (RecommendedVpnApp) -> Unit
) : RecyclerView.Adapter<RecommendedVpnAdapter.ViewHolder>() {

    private val items = mutableListOf<RecommendedVpnApp>()

    fun submitList(newItems: List<RecommendedVpnApp>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecommendedVpnBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemRecommendedVpnBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(itemView, scaleRatio = 1.03f, elevationDp = 6f)
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(items[pos])
                }
            }
        }

        fun bind(item: RecommendedVpnApp) {
            binding.tvVpnAppName.text = item.name
            binding.tvVpnAppDesc.text = item.description

            if (item.isInstalled) {
                binding.tvVpnAppAction.text = "ОТКРЫТЬ"
                binding.tvVpnAppAction.setTextColor(ContextCompat.getColor(itemView.context, R.color.vpn_emerald))
                binding.ivVpnAppIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.vpn_emerald))
            } else {
                binding.tvVpnAppAction.text = "УСТАНОВИТЬ"
                binding.tvVpnAppAction.setTextColor(ContextCompat.getColor(itemView.context, R.color.leanback_cyan))
                binding.ivVpnAppIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.leanback_cyan))
            }
        }
    }
}

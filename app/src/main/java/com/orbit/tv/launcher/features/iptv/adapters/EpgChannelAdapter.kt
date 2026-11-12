package com.orbit.tv.launcher.features.iptv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemEpgChannelBinding
import com.orbit.tv.launcher.features.iptv.model.IptvChannel

class EpgChannelAdapter(
    private val onChannelClicked: (IptvChannel) -> Unit
) : ListAdapter<IptvChannel, EpgChannelAdapter.EpgViewHolder>(ChannelDiffCallback()) {

    var activeChannelId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpgViewHolder {
        val binding = ItemEpgChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EpgViewHolder(binding, onChannelClicked)
    }

    override fun onBindViewHolder(holder: EpgViewHolder, position: Int) {
        val channel = getItem(position)
        holder.bind(channel, channel.id == activeChannelId)
    }

    class EpgViewHolder(
        private val binding: ItemEpgChannelBinding,
        private val onChannelClicked: (IptvChannel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.root, scaleRatio = 1.03f, elevationDp = 8f)
        }

        fun bind(channel: IptvChannel, isActive: Boolean) {
            val context = binding.root.context
            binding.tvChannelNumber.text = String.format("%03d", channel.number)
            binding.tvChannelName.text = channel.name
            binding.tvChannelCategory.text = when (channel.group.lowercase()) {
                "sports" -> context.getString(R.string.cat_sports)
                "cinema" -> context.getString(R.string.cat_cinema)
                "news" -> context.getString(R.string.cat_news)
                "all" -> context.getString(R.string.cat_all)
                else -> channel.group
            }
            binding.tvCurrentProgram.text = channel.currentProgram
            binding.tvNextProgram.text = context.getString(R.string.iptv_next_program_format, channel.nextProgram)
            binding.pbEpgProgress.progress = channel.progressPercent

            binding.dotActiveIndicator.visibility = if (isActive) View.VISIBLE else View.GONE

            if (!channel.logoUrl.isNullOrEmpty()) {
                binding.ivChannelLogo.load(channel.logoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_nav_iptv)
                    error(R.drawable.ic_nav_iptv)
                }
            } else {
                binding.ivChannelLogo.setImageResource(R.drawable.ic_nav_iptv)
            }

            binding.root.setOnClickListener {
                onChannelClicked(channel)
            }
        }
    }

    private class ChannelDiffCallback : DiffUtil.ItemCallback<IptvChannel>() {
        override fun areItemsTheSame(oldItem: IptvChannel, newItem: IptvChannel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: IptvChannel, newItem: IptvChannel): Boolean {
            return oldItem == newItem
        }
    }
}

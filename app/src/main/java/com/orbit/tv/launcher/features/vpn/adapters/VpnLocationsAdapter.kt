package com.orbit.tv.launcher.features.vpn.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ItemVpnLocationBinding
import com.orbit.tv.launcher.features.vpn.model.VpnServerLocation

class VpnLocationsAdapter(
    private val onLocationClick: (VpnServerLocation) -> Unit
) : RecyclerView.Adapter<VpnLocationsAdapter.LocationViewHolder>() {

    private var locations: List<VpnServerLocation> = emptyList()
    private var selectedCode: String = "WARP"

    fun submitList(newList: List<VpnServerLocation>, currentSelected: String) {
        this.locations = newList
        this.selectedCode = currentSelected
        notifyDataSetChanged()
    }

    fun setSelectedCode(code: String) {
        this.selectedCode = code
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemVpnLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(locations[position])
    }

    override fun getItemCount(): Int = locations.size

    inner class LocationViewHolder(
        private val binding: ItemVpnLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            TvFocusHelper.bindFocusScale(binding.root, scaleRatio = 1.03f, elevationDp = 4f)
            binding.root.setOnClickListener {
                val loc = locations.getOrNull(adapterPosition) ?: return@setOnClickListener
                selectedCode = loc.code
                notifyDataSetChanged()
                onLocationClick(loc)
            }
        }

        fun bind(loc: VpnServerLocation) {
            binding.tvLocFlag.text = loc.flag
            binding.tvLocCountry.text = loc.country
            binding.tvLocCity.text = loc.city
            binding.tvLocPing.text = "${loc.pingMs} ms"

            val isSelected = (loc.code == selectedCode)
            binding.dotLocSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.isSelected = isSelected
        }
    }
}

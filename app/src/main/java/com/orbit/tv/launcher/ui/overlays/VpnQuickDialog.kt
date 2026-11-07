package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogVpnQuickBinding
import com.orbit.tv.launcher.databinding.ItemVpnAppDialogBinding
import com.orbit.tv.launcher.features.vpn.VpnManager
import kotlinx.coroutines.launch

class VpnQuickDialog : DialogFragment() {

    private var _binding: DialogVpnQuickBinding? = null
    private val binding get() = _binding!!

    private lateinit var vpnManager: VpnManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVpnQuickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { win ->
            val dm = resources.displayMetrics
            val width = (460 * dm.density).toInt()
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vpnManager = VpnManager(requireContext())

        setupFocus()
        setupListeners()
        updateVpnStatus()
        loadInstalledVpnApps()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnCloseDialog, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnOpenSystemVpn, scaleRatio = 1.04f, elevationDp = 6f)
        binding.btnOpenSystemVpn.requestFocus()
    }

    private fun setupListeners() {
        binding.btnCloseDialog.setOnClickListener {
            dismiss()
        }

        binding.btnOpenSystemVpn.setOnClickListener {
            vpnManager.openSystemVpnSettings()
            dismiss()
        }
    }

    private fun updateVpnStatus() {
        val isActive = vpnManager.isVpnActive.value

        if (isActive) {
            val emerald = ContextCompat.getColor(requireContext(), R.color.vpn_emerald)
            binding.ivDialogShield.setColorFilter(emerald)
            binding.indicatorVpnDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.vpn_emerald)
            val code = vpnManager.vpnCountryCode.value
            binding.tvVpnStateTitle.text = if (code != "VPN") "VPN ACTIVE • $code" else "VPN ACTIVE & PROTECTED"
            binding.tvVpnStateTitle.setTextColor(emerald)
            binding.tvVpnTunnelInfo.text = vpnManager.vpnDetails.value
        } else {
            val grey = ContextCompat.getColor(requireContext(), R.color.white_40)
            binding.ivDialogShield.setColorFilter(grey)
            binding.indicatorVpnDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white_40)
            binding.tvVpnStateTitle.text = "VPN DISCONNECTED"
            binding.tvVpnStateTitle.setTextColor(grey)
            binding.tvVpnTunnelInfo.text = "All TV traffic unencrypted • Direct connection"
        }
    }

    private fun loadInstalledVpnApps() {
        lifecycleScope.launch {
            val apps = vpnManager.getInstalledVpnApps()
            if (apps.isEmpty()) {
                binding.tvNoVpnApps.visibility = View.VISIBLE
            } else {
                binding.tvNoVpnApps.visibility = View.GONE
                for (app in apps) {
                    val itemBinding = ItemVpnAppDialogBinding.inflate(
                        layoutInflater,
                        binding.containerVpnApps,
                        false
                    )
                    itemBinding.tvVpnAppName.text = app.name
                    itemBinding.tvVpnAppPkg.text = "${app.packageName} • v${app.version ?: "1.0"}"
                    if (app.icon != null) {
                        itemBinding.ivVpnAppIcon.setImageDrawable(app.icon)
                    }

                    TvFocusHelper.bindFocusScale(itemBinding.root, scaleRatio = 1.03f, elevationDp = 6f)
                    itemBinding.root.setOnClickListener {
                        vpnManager.launchVpnApp(app.packageName)
                        dismiss()
                    }
                    binding.containerVpnApps.addView(itemBinding.root)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vpnManager.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG = "VpnQuickDialog"
        fun newInstance() = VpnQuickDialog()
    }
}

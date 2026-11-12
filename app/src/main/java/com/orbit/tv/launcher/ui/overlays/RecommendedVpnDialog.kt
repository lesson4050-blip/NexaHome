package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogRecommendedVpnBinding
import com.orbit.tv.launcher.features.vpn.VpnManager
import com.orbit.tv.launcher.features.vpn.adapters.RecommendedVpnAdapter
import com.orbit.tv.launcher.features.vpn.model.RecommendedVpnApp

class RecommendedVpnDialog : DialogFragment() {

    private var _binding: DialogRecommendedVpnBinding? = null
    private val binding get() = _binding!!

    private lateinit var vpnManager: VpnManager
    private lateinit var adapter: RecommendedVpnAdapter

    private val curatedVpnApps = listOf(
        RecommendedVpnApp(
            packageName = "com.urbanvpn.android",
            name = "Urban VPN",
            description = "Бесплатный VPN с серверами в 80+ странах мира"
        ),
        RecommendedVpnApp(
            packageName = "com.cloudflare.onedotonedotonedotone",
            name = "Cloudflare 1.1.1.1 + WARP",
            description = "Официальный Cloudflare WARP • Защищенный быстрый Anycast DNS"
        ),
        RecommendedVpnApp(
            packageName = "com.wireguard.android",
            name = "WireGuard",
            description = "Сверхбыстрый минималистичный протокол туннелирования"
        ),
        RecommendedVpnApp(
            packageName = "org.amnezia.vpn",
            name = "Amnezia VPN",
            description = "Обход любых блокировок • Поддержка VLESS, WireGuard, XRay"
        ),
        RecommendedVpnApp(
            packageName = "ch.protonvpn.android",
            name = "Proton VPN",
            description = "Швейцарская безопасность • Бесплатные серверы без ограничений"
        ),
        RecommendedVpnApp(
            packageName = "com.adguard.vpn",
            name = "AdGuard VPN",
            description = "Высокая скорость и встроенная фильтрация рекламы"
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRecommendedVpnBinding.inflate(inflater, container, false)
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
            val width = (540 * dm.density).toInt()
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vpnManager = VpnManager(requireContext())

        TvFocusHelper.bindFocusScale(binding.btnCloseRecommended, scaleRatio = 1.15f, elevationDp = 8f)
        binding.btnCloseRecommended.setOnClickListener {
            dismiss()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = RecommendedVpnAdapter { app ->
            vpnManager.launchOrInstallVpnApp(app.packageName)
            dismiss()
        }

        binding.rvRecommendedVpns.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecommendedVpnDialog.adapter
        }

        val enriched = curatedVpnApps.map { app ->
            app.copy(isInstalled = vpnManager.isAppInstalled(app.packageName))
        }
        adapter.submitList(enriched)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vpnManager.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG = "RecommendedVpnDialog"
        fun newInstance() = RecommendedVpnDialog()
    }
}

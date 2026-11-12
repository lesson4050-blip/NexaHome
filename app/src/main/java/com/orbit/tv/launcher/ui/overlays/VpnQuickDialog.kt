package com.orbit.tv.launcher.ui.overlays

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogVpnQuickBinding
import com.orbit.tv.launcher.features.vpn.VpnManager
import com.orbit.tv.launcher.features.vpn.adapters.VpnLocationsAdapter
import com.orbit.tv.launcher.features.vpn.model.VpnServerLocation
import com.orbit.tv.launcher.features.vpn.service.OrbitTvVpnService
import kotlinx.coroutines.launch

class VpnQuickDialog : DialogFragment() {

    private var _binding: DialogVpnQuickBinding? = null
    private val binding get() = _binding!!

    private lateinit var vpnManager: VpnManager
    private lateinit var locationsAdapter: VpnLocationsAdapter
    private var selectedLocation: String = "WARP"

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            OrbitTvVpnService.startVpn(requireContext(), selectedLocation)
        }
    }

    private val serverLocations = listOf(
        // Quick & Fast / Default
        VpnServerLocation("WARP", "⚡", "Cloudflare WARP", "Глобальный авто-маршрут Anycast", 12),
        // Europe (East / Central / West / Nordic / South)
        VpnServerLocation("UA", "🇺🇦", "Украина", "Киев • UA-IX", 15),
        VpnServerLocation("PL", "🇵🇱", "Польша", "Варшава • Equinix", 22),
        VpnServerLocation("DE", "🇩🇪", "Германия", "Франкфурт • DE-CIX", 26),
        VpnServerLocation("NL", "🇳🇱", "Нидерланды", "Амстердам • AMS-IX", 28),
        VpnServerLocation("GB", "🇬🇧", "Великобритания", "Лондон • LINX", 32),
        VpnServerLocation("FR", "🇫🇷", "Франция", "Париж • France-IX", 35),
        VpnServerLocation("CH", "🇨🇭", "Швейцария", "Цюрих • SwissIX", 30),
        VpnServerLocation("AT", "🇦🇹", "Австрия", "Вена • VIX", 28),
        VpnServerLocation("CZ", "🇨🇿", "Чехия", "Прага • NIX.CZ", 25),
        VpnServerLocation("SK", "🇸🇰", "Словакия", "Братислава • SIX", 27),
        VpnServerLocation("HU", "🇭🇺", "Венгрия", "Будапешт • BIX", 29),
        VpnServerLocation("RO", "🇷🇴", "Румыния", "Бухарест • Interlan", 31),
        VpnServerLocation("BG", "🇧🇬", "Болгария", "София • BIX.BG", 33),
        VpnServerLocation("MD", "🇲🇩", "Молдова", "Кишинёв • MD-IX", 24),
        VpnServerLocation("SE", "🇸🇪", "Швеция", "Стокгольм • Netnod", 38),
        VpnServerLocation("NO", "🇳🇴", "Норвегия", "Осло • NIX", 42),
        VpnServerLocation("FI", "🇫🇮", "Финляндия", "Хельсинки • FICIX", 40),
        VpnServerLocation("DK", "🇩🇰", "Дания", "Копенгаген • DIX", 36),
        VpnServerLocation("BE", "🇧🇪", "Бельгия", "Брюссель • BNIX", 33),
        VpnServerLocation("IE", "🇮🇪", "Ирландия", "Дублин • INEX", 45),
        VpnServerLocation("IT", "🇮🇹", "Италия", "Милан • MIX-IT", 37),
        VpnServerLocation("ES", "🇪🇸", "Испания", "Мадрид • ESpanix", 48),
        VpnServerLocation("PT", "🇵🇹", "Португалия", "Лиссабон • GigaPIX", 52),
        VpnServerLocation("GR", "🇬🇷", "Греция", "Афины • GR-IX", 44),
        VpnServerLocation("HR", "🇭🇷", "Хорватия", "Загреб • CIX", 35),
        VpnServerLocation("RS", "🇷🇸", "Сербия", "Белград • SOX", 36),
        VpnServerLocation("SI", "🇸🇮", "Словения", "Любляна • SIX", 34),
        VpnServerLocation("EE", "🇪🇪", "Эстония", "Таллин • RTIX", 38),
        VpnServerLocation("LV", "🇱🇻", "Латвия", "Рига • LIX", 35),
        VpnServerLocation("LT", "🇱🇹", "Литва", "Вильнюс • LITIX", 33),
        VpnServerLocation("IS", "🇮🇸", "Исландия", "Рейкьявик • RIX", 65),
        VpnServerLocation("LU", "🇱🇺", "Люксембург", "Люксембург • LU-CIX", 31),
        VpnServerLocation("CY", "🇨🇾", "Кипр", "Никосия • CyIX", 55),
        // North & South America
        VpnServerLocation("US", "🇺🇸", "США (Восток)", "Нью-Йорк • NYIIX", 85),
        VpnServerLocation("US_CENTRAL", "🇺🇸", "США (Центр)", "Чикаго • Equinix CH", 98),
        VpnServerLocation("US_WEST", "🇺🇸", "США (Запад)", "Лос-Анджелес • CoreSite", 115),
        VpnServerLocation("US_SOUTH", "🇺🇸", "США (Юг)", "Майами • NOTA", 95),
        VpnServerLocation("CA", "🇨🇦", "Канада (Восток)", "Торонто • TorIX", 88),
        VpnServerLocation("CA_WEST", "🇨🇦", "Канада (Запад)", "Ванкувер • VANIX", 120),
        VpnServerLocation("MX", "🇲🇽", "Мексика", "Мехико • MEX-IX", 130),
        VpnServerLocation("BR", "🇧🇷", "Бразилия", "Сан-Паулу • IX.br", 160),
        VpnServerLocation("AR", "🇦🇷", "Аргентина", "Буэнос-Айрес • CABASE", 185),
        VpnServerLocation("CL", "🇨🇱", "Чили", "Сантьяго • PIT", 195),
        VpnServerLocation("CO", "🇨🇴", "Колумбия", "Богота • NAP Colombia", 150),
        // Caucasus & Central Asia
        VpnServerLocation("GE", "🇬🇪", "Грузия", "Тбилиси • GeoIX", 48),
        VpnServerLocation("AM", "🇦🇲", "Армения", "Ереван • ARMIX", 52),
        VpnServerLocation("AZ", "🇦🇿", "Азербайджан", "Баку • AZ-IX", 56),
        VpnServerLocation("KZ", "🇰🇿", "Казахстан", "Алматы • KazIX", 75),
        VpnServerLocation("UZ", "UZ", "Узбекистан", "Ташкент • TAS-IX", 82),
        // Middle East & Africa
        VpnServerLocation("TR", "🇹🇷", "Турция", "Стамбул • TurkIX", 38),
        VpnServerLocation("IL", "🇮🇱", "Израиль", "Тель-Авив • IIX", 49),
        VpnServerLocation("AE", "🇦🇪", "ОАЭ", "Дубай • UAE-IX", 95),
        VpnServerLocation("SA", "🇸🇦", "Саудовская Аравия", "Эр-Рияд • SA-IX", 105),
        VpnServerLocation("ZA", "🇿🇦", "ЮАР", "Йоханнесбург • JINX", 170),
        VpnServerLocation("EG", "🇪🇬", "Египет", "Каир • CAIX", 85),
        VpnServerLocation("NG", "🇳🇬", "Нигерия", "Лагос • IXPN", 165),
        VpnServerLocation("KE", "🇰🇪", "Кения", "Найроби • KIXP", 140),
        // Asia-Pacific & Oceania
        VpnServerLocation("JP", "🇯🇵", "Япония", "Токио • JPNAP", 140),
        VpnServerLocation("KR", "🇰🇷", "Южная Корея", "Сеул • KINX", 145),
        VpnServerLocation("SG", "🇸🇬", "Сингапур", "Сингапур • SGIX", 150),
        VpnServerLocation("HK", "🇭🇰", "Гонконг", "Гонконг • HKIX", 135),
        VpnServerLocation("TW", "🇹🇼", "Тайвань", "Тайбэй • TWIX", 145),
        VpnServerLocation("IN", "🇮🇳", "Индия", "Мумбаи • NIXI", 110),
        VpnServerLocation("TH", "🇹🇭", "Таиланд", "Бангкок • BKNIX", 160),
        VpnServerLocation("MY", "🇲🇾", "Малайзия", "Куала-Лумпур • MyIX", 155),
        VpnServerLocation("VN", "🇻🇳", "Вьетнам", "Ханой • VNIX", 165),
        VpnServerLocation("ID", "🇮🇩", "Индонезия", "Джакарта • IIX", 175),
        VpnServerLocation("PH", "🇵🇭", "Филиппины", "Манила • PHOpenIX", 170),
        VpnServerLocation("AU", "🇦🇺", "Австралия", "Сидней • Equinix SY", 210),
        VpnServerLocation("NZ", "🇳🇿", "Новая Зеландия", "Окленд • AKL-IX", 230)
    )

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
            val width = (520 * dm.density).toInt()
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vpnManager = VpnManager(requireContext())
        selectedLocation = OrbitTvVpnService.currentLocation.value

        setupLocationsList()
        setupFocus()
        setupListeners()
        observeVpnState()
    }

    private fun setupLocationsList() {
        locationsAdapter = VpnLocationsAdapter { location ->
            selectedLocation = location.code
            if (OrbitTvVpnService.isServiceRunning.value) {
                OrbitTvVpnService.startVpn(requireContext(), selectedLocation)
            }
        }

        binding.rvVpnLocations.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = locationsAdapter
        }
        locationsAdapter.submitList(serverLocations, selectedLocation)
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnCloseDialog, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnToggleVpn, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnRecommendedVpn, scaleRatio = 1.04f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnOpenSystemVpn, scaleRatio = 1.04f, elevationDp = 4f)
        binding.btnToggleVpn.requestFocus()
    }

    private fun setupListeners() {
        binding.btnCloseDialog.setOnClickListener {
            dismiss()
        }

        binding.btnToggleVpn.setOnClickListener {
            toggleVpn()
        }

        binding.btnRecommendedVpn.setOnClickListener {
            RecommendedVpnDialog.newInstance().show(parentFragmentManager, RecommendedVpnDialog.TAG)
        }

        binding.btnOpenSystemVpn.setOnClickListener {
            vpnManager.openSystemVpnSettings()
            dismiss()
        }
    }

    private fun toggleVpn() {
        if (OrbitTvVpnService.isServiceRunning.value || vpnManager.isVpnActive.value) {
            OrbitTvVpnService.stopVpn(requireContext())
        } else {
            val intent = VpnService.prepare(requireContext())
            if (intent != null) {
                vpnConsentLauncher.launch(intent)
            } else {
                OrbitTvVpnService.startVpn(requireContext(), selectedLocation)
            }
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                vpnManager.isVpnActive,
                OrbitTvVpnService.isServiceRunning,
                OrbitTvVpnService.currentLocation
            ) { active, running, location ->
                Triple(active || running, running, location)
            }.collect { (isActive, _, location) ->
                updateVpnUI(isActive, location)
            }
        }
    }

    private fun updateVpnUI(isActive: Boolean, location: String) {
        if (isActive) {
            val emerald = ContextCompat.getColor(requireContext(), R.color.vpn_emerald)
            binding.ivDialogShield.setColorFilter(emerald)
            binding.indicatorVpnDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.vpn_emerald)

            val matchedName = serverLocations.firstOrNull { it.code == location }?.country ?: location
            binding.tvVpnStateTitle.text = getString(R.string.vpn_active_country, matchedName)
            binding.tvVpnStateTitle.setTextColor(emerald)
            binding.tvVpnTunnelInfo.text = "DNS 1.1.1.1 зашифрован • Защищённый туннель"

            binding.btnToggleVpn.text = getString(R.string.vpn_btn_disconnect)
            binding.btnToggleVpn.setTextColor(Color.parseColor("#EA4335"))
        } else {
            val grey = ContextCompat.getColor(requireContext(), R.color.white_40)
            binding.ivDialogShield.setColorFilter(grey)
            binding.indicatorVpnDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white_40)

            binding.tvVpnStateTitle.text = getString(R.string.vpn_disconnected)
            binding.tvVpnStateTitle.setTextColor(grey)
            binding.tvVpnTunnelInfo.text = getString(R.string.vpn_traffic_unencrypted)

            binding.btnToggleVpn.text = getString(R.string.vpn_btn_connect)
            binding.btnToggleVpn.setTextColor(ContextCompat.getColor(requireContext(), R.color.vpn_emerald))
        }
        locationsAdapter.setSelectedCode(location)
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

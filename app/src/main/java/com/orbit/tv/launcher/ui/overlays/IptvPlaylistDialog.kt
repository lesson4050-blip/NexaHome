package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogIptvPlaylistBinding
import com.orbit.tv.launcher.features.iptv.IptvViewModel

class IptvPlaylistDialog : DialogFragment() {

    private var _binding: DialogIptvPlaylistBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IptvViewModel by activityViewModels()

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIptvPlaylistBinding.inflate(inflater, container, false)
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
            val width = (740 * dm.density).toInt().coerceAtMost((dm.widthPixels * 0.90).toInt())
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocus()
        setupCurrentState()
        setupListeners()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnPresetOrbit, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnPresetUkraine, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnPresetNews, scaleRatio = 1.05f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.btnResetDefault, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnClose, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnLoadUrl, scaleRatio = 1.06f, elevationDp = 8f)

        binding.btnPresetOrbit.requestFocus()
    }

    private fun setupCurrentState() {
        val activeUrl = viewModel.getActivePlaylistUrl()
        if (!activeUrl.isNullOrBlank()) {
            binding.etPlaylistUrl.setText(activeUrl)
            showStatus("Активный кастомный плейлист: $activeUrl", isError = false)
        } else {
            showStatus("Используется стандартный плейлист Orbit Live HD", isError = false)
        }
    }

    private fun setupListeners() {
        binding.btnPresetOrbit.setOnClickListener {
            loadPresetOrbit()
        }

        binding.btnPresetUkraine.setOnClickListener {
            binding.etPlaylistUrl.setText(URL_PRESET_UKRAINE)
            loadUrl(URL_PRESET_UKRAINE)
        }

        binding.btnPresetNews.setOnClickListener {
            binding.etPlaylistUrl.setText(URL_PRESET_NEWS)
            loadUrl(URL_PRESET_NEWS)
        }

        binding.btnLoadUrl.setOnClickListener {
            val inputUrl = binding.etPlaylistUrl.text?.toString()?.trim().orEmpty()
            if (inputUrl.isBlank()) {
                showStatus("Введите URL плейлиста в формате http:// или https://", isError = true)
                return@setOnClickListener
            }
            loadUrl(inputUrl)
        }

        binding.btnResetDefault.setOnClickListener {
            loadPresetOrbit()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun loadPresetOrbit() {
        setLoading(true)
        showStatus("Восстановление стандартного набора каналов...", isError = false)
        viewModel.resetDefaultPlaylist { success, message ->
            setLoading(false)
            showStatus(message, isError = !success)
            if (success) {
                binding.etPlaylistUrl.setText("")
                mainHandler.postDelayed({
                    if (isAdded) dismiss()
                }, 1200)
            }
        }
    }

    private fun loadUrl(url: String) {
        setLoading(true)
        showStatus("Загрузка и парсинг M3U плейлиста...", isError = false)
        viewModel.importPlaylist(url) { success, message ->
            setLoading(false)
            showStatus(message, isError = !success)
            if (success) {
                mainHandler.postDelayed({
                    if (isAdded) dismiss()
                }, 1500)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLoadUrl.isEnabled = !isLoading
        binding.btnResetDefault.isEnabled = !isLoading
        binding.btnPresetOrbit.isEnabled = !isLoading
        binding.btnPresetUkraine.isEnabled = !isLoading
        binding.btnPresetNews.isEnabled = !isLoading
    }

    private fun showStatus(text: String, isError: Boolean) {
        binding.tvStatusMessage.text = text
        binding.tvStatusMessage.visibility = View.VISIBLE
        val color = if (isError) {
            ContextCompat.getColor(requireContext(), R.color.tv_red_accent)
        } else {
            ContextCompat.getColor(requireContext(), R.color.leanback_cyan)
        }
        binding.tvStatusMessage.setTextColor(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        _binding = null
    }

    companion object {
        const val TAG = "IptvPlaylistDialog"
        const val URL_PRESET_UKRAINE = "https://iptv-org.github.io/iptv/countries/ua.m3u"
        const val URL_PRESET_NEWS = "https://iptv-org.github.io/iptv/categories/news.m3u"

        fun show(fragmentManager: FragmentManager) {
            val existing = fragmentManager.findFragmentByTag(TAG)
            if (existing == null) {
                IptvPlaylistDialog().show(fragmentManager, TAG)
            }
        }
    }
}

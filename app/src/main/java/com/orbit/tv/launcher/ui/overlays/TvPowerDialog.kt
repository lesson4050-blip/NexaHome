package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogPowerBinding
import com.orbit.tv.launcher.platform.home.TvHomeWatcherService

class TvPowerDialog : DialogFragment() {

    private var _binding: DialogPowerBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "TvPowerDialog"

        fun show(fragmentManager: FragmentManager) {
            val existing = fragmentManager.findFragmentByTag(TAG)
            if (existing == null) {
                TvPowerDialog().show(fragmentManager, TAG)
            }
        }

        private var activeSleepTimerHandler: Handler? = null
        private var activeSleepTimerRunnable: Runnable? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPowerBinding.inflate(inflater, container, false)
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

        setupFocus()
        setupListeners()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnActionSleep, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionReboot, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionShutdown, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionTimer, scaleRatio = 1.04f, elevationDp = 6f)

        binding.btnActionSleep.requestFocus()
    }

    private fun setupListeners() {
        // 1. Sleep / Lock screen
        binding.btnActionSleep.setOnClickListener {
            dismiss()
            val performed = TvHomeWatcherService.lockScreenOrSleep()
            if (!performed) {
                promptAccessibilityFallback(getString(R.string.power_accessibility_sleep_prompt))
            }
        }

        // 2. Reboot
        binding.btnActionReboot.setOnClickListener {
            dismiss()
            val performed = TvHomeWatcherService.showGlobalPowerDialog()
            if (!performed) {
                promptAccessibilityFallback(getString(R.string.power_accessibility_reboot_prompt))
            }
        }

        // 3. Shutdown
        binding.btnActionShutdown.setOnClickListener {
            dismiss()
            val performed = TvHomeWatcherService.showGlobalPowerDialog()
            if (!performed) {
                try {
                    val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN").apply {
                        putExtra("android.intent.extra.KEY_CONFIRM", false)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    promptAccessibilityFallback(getString(R.string.power_accessibility_shutdown_prompt))
                }
            }
        }

        // 4. Sleep Timer (30 minutes)
        binding.btnActionTimer.setOnClickListener {
            dismiss()
            setSleepTimer(30)
        }
    }

    private fun setSleepTimer(minutes: Int) {
        activeSleepTimerRunnable?.let { activeSleepTimerHandler?.removeCallbacks(it) }

        val handler = Handler(Looper.getMainLooper())
        activeSleepTimerHandler = handler

        val runnable = Runnable {
            TvHomeWatcherService.lockScreenOrSleep()
        }
        activeSleepTimerRunnable = runnable

        handler.postDelayed(runnable, minutes * 60 * 1000L)
        Toast.makeText(
            requireContext(),
            getString(R.string.power_timer_toast, minutes),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun promptAccessibilityFallback(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

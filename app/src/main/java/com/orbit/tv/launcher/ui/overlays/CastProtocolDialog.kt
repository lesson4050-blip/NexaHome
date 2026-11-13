package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogCastProtocolBinding

class CastProtocolDialog : DialogFragment() {

    private var _binding: DialogCastProtocolBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCastProtocolBinding.inflate(inflater, container, false)
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

        val title = arguments?.getString(ARG_TITLE) ?: "Wireless Cast"
        val subtitle = arguments?.getString(ARG_SUBTITLE) ?: ""
        val instructions = arguments?.getString(ARG_INSTRUCTIONS) ?: ""
        val iconRes = arguments?.getInt(ARG_ICON, R.drawable.ic_nav_cast) ?: R.drawable.ic_nav_cast
        val primaryText = arguments?.getString(ARG_PRIMARY_TEXT) ?: getString(R.string.cast_btn_install_airscreen)
        val targetPackage = arguments?.getString(ARG_TARGET_PACKAGE) ?: AIRSCREEN_PKG
        val localIp = arguments?.getString(ARG_LOCAL_IP)

        binding.tvDialogTitle.text = title
        binding.tvDialogSubtitle.text = subtitle
        binding.tvProtocolInstructions.text = instructions
        binding.ivProtocolIcon.setImageResource(iconRes)
        binding.btnPrimaryAction.text = primaryText

        val model = android.os.Build.MODEL ?: "TV"
        val deviceName = if (model.contains("TV", ignoreCase = true)) model else "Smart TV ($model)"
        if (localIp != null) {
            binding.tvDialogIpStatus.text = "IP: $localIp • $deviceName"
        } else {
            binding.tvDialogIpStatus.text = getString(R.string.cast_network_offline)
        }

        TvFocusHelper.bindFocusScale(binding.btnCloseDialog, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSecondaryAction, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnPrimaryAction, scaleRatio = 1.08f, elevationDp = 6f)

        binding.btnCloseDialog.setOnClickListener { dismiss() }
        binding.btnSecondaryAction.setOnClickListener { dismiss() }

        val isUnderstoodOnly = primaryText.equals(getString(R.string.cast_btn_understood), ignoreCase = true)
        if (isUnderstoodOnly) {
            binding.btnSecondaryAction.visibility = View.GONE
            binding.btnPrimaryAction.setOnClickListener { dismiss() }
        } else {
            binding.btnSecondaryAction.visibility = View.VISIBLE
            binding.btnPrimaryAction.setOnClickListener {
                val pm = requireContext().packageManager
                val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    openGooglePlay(targetPackage)
                }
                dismiss()
            }
        }

        binding.btnPrimaryAction.requestFocus()
    }

    private fun openGooglePlay(packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e2: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CastProtocolDialog"
        const val AIRSCREEN_PKG = "com.ionitech.airscreen"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_SUBTITLE = "arg_subtitle"
        private const val ARG_INSTRUCTIONS = "arg_instructions"
        private const val ARG_ICON = "arg_icon"
        private const val ARG_PRIMARY_TEXT = "arg_primary_text"
        private const val ARG_TARGET_PACKAGE = "arg_target_pkg"
        private const val ARG_LOCAL_IP = "arg_local_ip"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            subtitle: String,
            instructions: String,
            iconRes: Int = R.drawable.ic_nav_cast,
            primaryText: String? = null,
            targetPackage: String = AIRSCREEN_PKG,
            localIp: String? = null
        ) {
            val dialog = CastProtocolDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                    putString(ARG_INSTRUCTIONS, instructions)
                    putInt(ARG_ICON, iconRes)
                    putString(ARG_PRIMARY_TEXT, primaryText)
                    putString(ARG_TARGET_PACKAGE, targetPackage)
                    putString(ARG_LOCAL_IP, localIp)
                }
            }
            dialog.show(fragmentManager, TAG)
        }
    }
}

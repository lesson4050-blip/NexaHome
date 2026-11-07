package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogAppOptionsBinding
import com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository
import com.orbit.tv.launcher.features.home.model.AppModel

class AppOptionsDialog : DialogFragment() {

    private var _binding: DialogAppOptionsBinding? = null
    private val binding get() = _binding!!

    private var appModel: AppModel? = null
    private var onPreferenceChangedListener: (() -> Unit)? = null
    private lateinit var appPrefsRepo: AppPreferencesRepository

    fun setTargetApp(app: AppModel, onPreferenceChanged: () -> Unit) {
        this.appModel = app
        this.onPreferenceChangedListener = onPreferenceChanged
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAppOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appPrefsRepo = AppPreferencesRepository(requireContext())

        setupFocus()
        bindAppData()
        setupListeners()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnDialogClose, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnActionPin, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionHide, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionInfo, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionUninstall, scaleRatio = 1.04f, elevationDp = 6f)

        // Default focus to Pin action
        binding.btnActionPin.requestFocus()
    }

    private fun bindAppData() {
        val app = appModel ?: return

        binding.tvDialogAppTitle.text = app.label
        binding.tvDialogAppPackage.text = app.packageName
        if (app.iconDrawable != null) {
            binding.ivDialogAppIcon.setImageDrawable(app.iconDrawable)
        } else {
            binding.ivDialogAppIcon.setImageResource(R.drawable.ic_nav_apps)
        }

        val isPinned = appPrefsRepo.isPinned(app.packageName)
        val isHidden = appPrefsRepo.isHidden(app.packageName)

        // Dynamic Pin status
        if (isPinned) {
            binding.tvActionPinTitle.text = "Unpin from Favorites Shelf"
            binding.tvActionPinDesc.text = "Remove from Home screen quick access shelf"
        } else {
            binding.tvActionPinTitle.text = "Pin to Favorites Shelf"
            binding.tvActionPinDesc.text = "Show on Home screen quick access shelf"
        }

        // Dynamic Hide status
        if (isHidden) {
            binding.tvActionHideTitle.text = "Unhide App (Restore)"
            binding.tvActionHideDesc.text = "Restore back to Home and main application grid"
            binding.ivActionHideIcon.setImageResource(R.drawable.ic_eye)
            binding.ivActionHideIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.vpn_emerald))
        } else {
            binding.tvActionHideTitle.text = "Hide App (Debloat)"
            binding.tvActionHideDesc.text = "Hide bloatware from Home & Apps shelf"
            binding.ivActionHideIcon.setImageResource(R.drawable.ic_eye_off)
            binding.ivActionHideIcon.setColorFilter(Color.parseColor("#F87171"))
        }
    }

    private fun setupListeners() {
        val app = appModel ?: return

        binding.btnDialogClose.setOnClickListener {
            dismiss()
        }

        // 1. Toggle Pin
        binding.btnActionPin.setOnClickListener {
            val newlyPinned = appPrefsRepo.togglePin(app.packageName)
            val msg = if (newlyPinned) "Pinned ${app.label} to Favorites" else "Unpinned ${app.label}"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            onPreferenceChangedListener?.invoke()
            dismiss()
        }

        // 2. Toggle Hide
        binding.btnActionHide.setOnClickListener {
            val newlyHidden = appPrefsRepo.toggleHide(app.packageName)
            val msg = if (newlyHidden) "Hidden ${app.label} from Launcher" else "Restored ${app.label}"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            onPreferenceChangedListener?.invoke()
            dismiss()
        }

        // 3. System App Details
        binding.btnActionInfo.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot open settings for ${app.label}", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }

        // 4. Uninstall App
        binding.btnActionUninstall.setOnClickListener {
            try {
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                }
                startActivity(uninstallIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot uninstall ${app.label}", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AppOptionsDialog"

        fun newInstance(app: AppModel, onPreferenceChanged: () -> Unit): AppOptionsDialog {
            val dialog = AppOptionsDialog()
            dialog.setTargetApp(app, onPreferenceChanged)
            return dialog
        }
    }
}

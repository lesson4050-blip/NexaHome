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
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogWatchInAppsBinding
import com.orbit.tv.launcher.features.home.TvDeepLinkHelper
import com.orbit.tv.launcher.features.home.adapters.MediaPlayerAdapter

class WatchInAppsDialog : DialogFragment() {

    private var _binding: DialogWatchInAppsBinding? = null
    private val binding get() = _binding!!

    private var movieTitle: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWatchInAppsBinding.inflate(inflater, container, false)
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

        movieTitle = arguments?.getString(ARG_MOVIE_TITLE) ?: ""

        binding.tvDialogSubtitle.text = movieTitle
        TvFocusHelper.bindFocusScale(binding.btnCloseDialog, scaleRatio = 1.15f, elevationDp = 8f)
        binding.btnCloseDialog.setOnClickListener { dismiss() }

        val players = TvDeepLinkHelper.getInstalledMediaPlayers(requireContext())
        val adapter = MediaPlayerAdapter { player ->
            TvDeepLinkHelper.launchSearchInApp(requireContext(), player, movieTitle)
            dismiss()
        }

        binding.rvMediaPlayers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMediaPlayers.adapter = adapter
        adapter.submitList(players) {
            if (players.isNotEmpty()) {
                binding.rvMediaPlayers.post {
                    binding.rvMediaPlayers.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WatchInAppsDialog"
        private const val ARG_MOVIE_TITLE = "arg_movie_title"

        fun newInstance(movieTitle: String): WatchInAppsDialog {
            val dialog = WatchInAppsDialog()
            val args = Bundle().apply {
                putString(ARG_MOVIE_TITLE, movieTitle)
            }
            dialog.arguments = args
            return dialog
        }
    }
}

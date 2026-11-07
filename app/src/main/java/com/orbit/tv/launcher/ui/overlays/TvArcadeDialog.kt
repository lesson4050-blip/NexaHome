package com.orbit.tv.launcher.ui.overlays

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogArcadeGameBinding

class TvArcadeDialog : DialogFragment() {

    private var _binding: DialogArcadeGameBinding? = null
    private val binding get() = _binding!!

    private var gameType: String = "snake"

    companion object {
        const val TAG = "TvArcadeDialog"
        private const val ARG_GAME = "arg_game"

        fun newInstance(game: String): TvArcadeDialog {
            val dialog = TvArcadeDialog()
            val args = Bundle().apply {
                putString(ARG_GAME, game)
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameType = arguments?.getString(ARG_GAME) ?: "snake"
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        _binding = DialogArcadeGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TvFocusHelper.bindFocusScale(binding.btnCloseArcade, scaleRatio = 1.15f, elevationDp = 8f)
        binding.btnCloseArcade.setOnClickListener {
            dismiss()
        }

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webViewArcade.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            loadUrl("file:///android_asset/arcade_games.html?game=$gameType")
            requestFocus()
        }
    }

    override fun onDestroyView() {
        binding.webViewArcade.destroy()
        _binding = null
        super.onDestroyView()
    }
}

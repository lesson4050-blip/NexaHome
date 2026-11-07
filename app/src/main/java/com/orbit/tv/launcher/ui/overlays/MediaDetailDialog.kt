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
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import coil.load
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogMediaDetailBinding
import com.orbit.tv.launcher.features.home.model.MediaModel
import java.net.URLEncoder

class MediaDetailDialog : DialogFragment() {

    private var _binding: DialogMediaDetailBinding? = null
    private val binding get() = _binding!!

    private var targetMedia: MediaModel? = null

    fun setMedia(media: MediaModel) {
        this.targetMedia = media
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMediaDetailBinding.inflate(inflater, container, false)
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
        dialog?.window?.let { window ->
            val dm = resources.displayMetrics
            val widthPx = (680 * dm.density).toInt()
            window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFocus()
        bindData()
        setupListeners()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnDetailClose, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnActionTrailer, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionSearchApps, scaleRatio = 1.06f, elevationDp = 6f)

        binding.btnActionTrailer.requestFocus()
    }

    private fun bindData() {
        val media = targetMedia ?: return

        binding.tvDetailTitle.text = media.title
        binding.tvDetailGenre.text = if (media.genre.isNotBlank()) media.genre else "Feature Film"
        binding.tvDetailDesc.text = if (media.description.isNotBlank()) media.description else "Описание недоступно."
        binding.tvDetailRating.text = if (media.rating.isNotBlank()) "★ ${media.rating}" else media.badge
        binding.tvDetailYear.text = if (media.releaseYear.isNotBlank()) media.releaseYear else "2024"

        binding.ivDetailPoster.load(media.posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_focus_media_card)
            error(R.drawable.bg_focus_media_card)
        }
    }

    private fun setupListeners() {
        val media = targetMedia ?: return

        binding.btnDetailClose.setOnClickListener {
            dismiss()
        }

        binding.btnActionTrailer.setOnClickListener {
            try {
                val query = URLEncoder.encode("${media.title} official trailer", "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть YouTube", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }

        binding.btnActionSearchApps.setOnClickListener {
            try {
                val query = URLEncoder.encode(media.title, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=watch+online+$query"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Поиск ${media.title}...", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MediaDetailDialog"

        fun newInstance(media: MediaModel): MediaDetailDialog {
            val dialog = MediaDetailDialog()
            dialog.setMedia(media)
            return dialog
        }
    }
}

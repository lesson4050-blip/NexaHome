package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogSearchBinding
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.features.home.adapters.MediaAdapter
import com.orbit.tv.launcher.features.home.model.AppModel
import com.orbit.tv.launcher.features.home.model.MediaModel
import com.orbit.tv.launcher.features.home.repository.MediaRepository
import com.orbit.tv.launcher.platform.package_manager.PackageManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchDialogFragment : DialogFragment() {

    private var _binding: DialogSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var packageManagerRepository: PackageManagerRepository
    private lateinit var mediaRepository: MediaRepository

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var mediaAdapter: MediaAdapter

    private var allApps: List<AppModel> = emptyList()
    private var allMedia: List<MediaModel> = emptyList()

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            binding.etSearchQuery.setText(spoken)
            binding.etSearchQuery.setSelection(spoken.length)
            performSearch(spoken)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        packageManagerRepository = PackageManagerRepository(requireContext())
        mediaRepository = MediaRepository(requireContext())

        setupAdapters()
        setupFocus()
        setupListeners()
        loadInitialData()

        // Focus search edit text and summon on-screen keyboard
        binding.etSearchQuery.postDelayed({
            if (_binding != null) {
                binding.etSearchQuery.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 300)
    }

    private fun setupAdapters() {
        appsAdapter = AppsAdapter(
            onAppClick = { app ->
                val intent = app.launchIntent ?: requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    startActivity(intent)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "Не удалось запустить ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvSearchApps.adapter = appsAdapter

        mediaAdapter = MediaAdapter(
            onMediaClick = { media ->
                MediaDetailDialog.newInstance(media)
                    .show(parentFragmentManager, MediaDetailDialog.TAG)
            }
        )
        binding.rvSearchMedia.adapter = mediaAdapter
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnDialogVoice, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnDialogClose, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnClearQuery, scaleRatio = 1.12f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionYoutube, scaleRatio = 1.05f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnActionWeb, scaleRatio = 1.05f, elevationDp = 6f)
    }

    private fun setupListeners() {
        binding.btnDialogClose.setOnClickListener { dismiss() }

        binding.btnClearQuery.setOnClickListener {
            binding.etSearchQuery.setText("")
            binding.etSearchQuery.requestFocus()
        }

        binding.btnDialogVoice.setOnClickListener {
            triggerVoiceRecognition()
        }

        binding.btnActionYoutube.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            launchYouTubeSearch(query)
        }

        binding.btnActionWeb.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            launchWebSearch(query)
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearchQuery.text.toString().trim()
                if (query.isNotBlank()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearQuery.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { packageManagerRepository.getInstalledTvApps() }
            val movies = withContext(Dispatchers.IO) { mediaRepository.getTrendingMovies() }
            val series = withContext(Dispatchers.IO) { mediaRepository.getTrendingSeries() }

            allApps = apps
            allMedia = movies + series

            // Show default list
            appsAdapter.submitList(allApps)
            mediaAdapter.submitList(allMedia)
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            appsAdapter.submitList(allApps)
            mediaAdapter.submitList(allMedia)
            binding.sectionSearchApps.visibility = if (allApps.isNotEmpty()) View.VISIBLE else View.GONE
            binding.sectionSearchMedia.visibility = if (allMedia.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvSearchEmpty.visibility = View.GONE
            return
        }

        val filteredApps = allApps.filter { it.label.contains(query, ignoreCase = true) }
        val filteredMedia = allMedia.filter {
            it.title.contains(query, ignoreCase = true) || it.genre.contains(query, ignoreCase = true)
        }

        appsAdapter.submitList(filteredApps)
        mediaAdapter.submitList(filteredMedia)

        binding.sectionSearchApps.visibility = if (filteredApps.isNotEmpty()) View.VISIBLE else View.GONE
        binding.sectionSearchMedia.visibility = if (filteredMedia.isNotEmpty()) View.VISIBLE else View.GONE

        val hasResults = filteredApps.isNotEmpty() || filteredMedia.isNotEmpty()
        binding.tvSearchEmpty.visibility = if (hasResults) View.GONE else View.VISIBLE
    }

    private fun triggerVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Произнесите название фильма или приложения")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Голосовой ввод недоступен на этом устройстве. Используйте клавиатуру.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchYouTubeSearch(query: String) {
        val q = query.ifBlank { "Трейлеры кино" }
        try {
            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=${Uri.encode(q)}"))
            startActivity(ytIntent)
        } catch (e: Exception) {
            try {
                val webYt = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))
                startActivity(webYt)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть YouTube", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchWebSearch(query: String) {
        val q = query.ifBlank { "Новости кино" }
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, q)
            }
            startActivity(searchIntent)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}"))
                startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SearchDialogFragment"

        fun newInstance(prefillQuery: String? = null): SearchDialogFragment {
            return SearchDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("prefill_query", prefillQuery)
                }
            }
        }
    }
}

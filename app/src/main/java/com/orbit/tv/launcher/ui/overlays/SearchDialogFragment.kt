package com.orbit.tv.launcher.ui.overlays

import android.Manifest
import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.R
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecordingVoice: Boolean = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInPlaceVoiceRecognition()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == android.view.KeyEvent.KEYCODE_SEARCH ||
                keyCode == android.view.KeyEvent.KEYCODE_ASSIST) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0 && !isRecordingVoice) {
                        startInPlaceVoiceRecognition()
                    }
                    return@setOnKeyListener true
                } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                    stopInPlaceVoiceRecognition()
                    return@setOnKeyListener true
                }
            }
            false
        }
        return dialog
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

        val prefill = arguments?.getString("prefill_query")
        if (!prefill.isNullOrBlank()) {
            binding.etSearchQuery.setText(prefill)
            binding.etSearchQuery.setSelection(prefill.length)
            performSearch(prefill)
        } else {
            // Focus search edit text and summon on-screen keyboard
            binding.etSearchQuery.postDelayed({
                if (_binding != null) {
                    binding.etSearchQuery.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 300)
        }
    }

    private fun setupAdapters() {
        appsAdapter = AppsAdapter(
            onAppClick = { app ->
                val intent = app.launchIntent ?: requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    startActivity(intent)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.app_cant_launch, app.label), Toast.LENGTH_SHORT).show()
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

            val currentQuery = _binding?.etSearchQuery?.text?.toString()?.trim().orEmpty()
            if (currentQuery.isNotEmpty()) {
                performSearch(currentQuery)
            } else {
                appsAdapter.submitList(allApps)
                mediaAdapter.submitList(allMedia)
            }
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

    private fun animateVoiceMicActive(active: Boolean) {
        if (active) {
            binding.btnDialogVoice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA4335"))
            binding.btnDialogVoice.scaleX = 1.20f
            binding.btnDialogVoice.scaleY = 1.20f
        } else {
            binding.btnDialogVoice.backgroundTintList = null
            val targetScale = if (binding.btnDialogVoice.isFocused) 1.12f else 1.0f
            binding.btnDialogVoice.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(200)
                .start()
        }
    }

    private fun startInPlaceVoiceRecognition() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isRecordingVoice = true
                        animateVoiceMicActive(true)
                        binding.etSearchQuery.hint = getString(R.string.search_voice_listening)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = (rmsdB.coerceIn(0f, 10f) / 10f)
                        val s = 1.18f + (norm * 0.16f)
                        binding.btnDialogVoice.scaleX = s
                        binding.btnDialogVoice.scaleY = s
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        binding.etSearchQuery.hint = getString(R.string.search_voice_processing)
                    }

                    override fun onError(error: Int) {
                        isRecordingVoice = false
                        animateVoiceMicActive(false)
                        binding.etSearchQuery.hint = getString(R.string.search_hint)
                    }

                    override fun onResults(results: Bundle?) {
                        isRecordingVoice = false
                        animateVoiceMicActive(false)
                        binding.etSearchQuery.hint = getString(R.string.search_hint)

                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            binding.etSearchQuery.setText(spoken)
                            binding.etSearchQuery.setSelection(spoken.length)
                            performSearch(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            binding.etSearchQuery.setText(partial)
                            binding.etSearchQuery.setSelection(partial.length)
                            performSearch(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            isRecordingVoice = false
            animateVoiceMicActive(false)
        }
    }

    private fun stopInPlaceVoiceRecognition() {
        if (isRecordingVoice) {
            speechRecognizer?.stopListening()
        }
    }

    private fun triggerVoiceRecognition() {
        if (isRecordingVoice) {
            stopInPlaceVoiceRecognition()
        } else {
            startInPlaceVoiceRecognition()
        }
    }

    private fun launchYouTubeSearch(query: String) {
        val q = query.ifBlank { getString(R.string.search_default_yt_query) }
        val pm = requireContext().packageManager
        
        // Check for SmartTube first (frequently used on TV without GMS)
        val smartTubePackages = listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta")
        for (pkg in smartTubePackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(pkg)
                    putExtra("query", q)
                    putExtra(SearchManager.QUERY, q)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                dismissAllowingStateLoss()
                return
            } catch (_: Exception) {}
        }

        // Try standard YouTube deep link
        try {
            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=${Uri.encode(q)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(ytIntent)
            dismissAllowingStateLoss()
            return
        } catch (_: Exception) {}

        // Fallback to web YouTube
        try {
            val webYt = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webYt)
            dismissAllowingStateLoss()
        } catch (e2: Exception) {
            Toast.makeText(requireContext(), getString(R.string.search_cant_open_yt), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchWebSearch(query: String) {
        val q = query.ifBlank { getString(R.string.search_default_web_query) }
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(searchIntent)
            dismissAllowingStateLoss()
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
                dismissAllowingStateLoss()
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), getString(R.string.search_cant_open_browser), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        speechRecognizer?.destroy()
        speechRecognizer = null
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

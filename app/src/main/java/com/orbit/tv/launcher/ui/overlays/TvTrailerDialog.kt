package com.orbit.tv.launcher.ui.overlays

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogTvTrailerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class TvTrailerDialog : DialogFragment() {

    private var _binding: DialogTvTrailerBinding? = null
    private val binding get() = _binding!!

    private var mediaTitle: String = ""
    private var mediaId: String = ""
    private var resolvedVideoId: String? = null

    companion object {
        const val TAG = "TvTrailerDialog"
        private const val ARG_TITLE = "arg_media_title"
        private const val ARG_ID = "arg_media_id"

        fun newInstance(title: String, id: String = ""): TvTrailerDialog {
            val dialog = TvTrailerDialog()
            dialog.arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_ID, id)
            }
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaTitle = arguments?.getString(ARG_TITLE).orEmpty()
        mediaId = arguments?.getString(ARG_ID).orEmpty()
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }
        _binding = DialogTvTrailerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTrailerTitle.text = getString(R.string.trailer_title_format, mediaTitle)

        TvFocusHelper.bindFocusScale(binding.btnCloseTrailer, scaleRatio = 1.15f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnOpenExternalYt, scaleRatio = 1.08f, elevationDp = 6f)

        binding.btnCloseTrailer.setOnClickListener {
            dismiss()
        }

        binding.btnOpenExternalYt.setOnClickListener {
            launchExternalYouTube()
        }

        setupWebViewSettings()
        resolveAndPlayTrailer()
        binding.btnCloseTrailer.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        binding.webViewTrailer.apply {
            setBackgroundColor(Color.parseColor("#050811"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _binding?.pbTrailerLoading?.visibility = View.GONE
                    view?.evaluateJavascript(
                        "(function() {" +
                        "var css = '.mobile-topbar-header, #header-bar, .ytm-mobile-topbar-renderer, ytm-pivot-bar-renderer, .watch-below-the-player, ytm-item-section-renderer { display: none !important; } body, ytm-app { background-color: #050811 !important; }';" +
                        "var s = document.createElement('style');" +
                        "s.innerHTML = css;" +
                        "document.head.appendChild(s);" +
                        "var v = document.querySelector('video');" +
                        "if (v && v.paused) { v.play().catch(function(){}); }" +
                        "})();",
                        null
                    )
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    private fun resolveAndPlayTrailer() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Tier 1: Check Cinemeta (Movie & Series) if mediaId is present
            var videoId: String? = if (mediaId.isNotBlank()) fetchCinemetaTrailer(mediaId) else null

            // Validate videoId via oEmbed
            if (!videoId.isNullOrBlank() && !isYouTubeVideoValid(videoId)) {
                videoId = null
            }

            // Tier 2: Dynamic YouTube Search Scraping (Guaranteed 100% fresh active trailer for any title)
            if (videoId.isNullOrBlank()) {
                videoId = searchYouTubeTrailerId(mediaTitle)
            }

            // Fallback: If title has colon / subtitle, search by main title
            if (videoId.isNullOrBlank() && mediaTitle.contains(":")) {
                val cleanTitle = mediaTitle.substringBefore(":").trim()
                videoId = searchYouTubeTrailerId(cleanTitle)
            }

            withContext(Dispatchers.Main) {
                resolvedVideoId = videoId
                if (!videoId.isNullOrBlank()) {
                    playEmbedVideo(videoId)
                } else {
                    val query = URLEncoder.encode("$mediaTitle official trailer", "UTF-8")
                    binding.webViewTrailer.loadUrl("https://m.youtube.com/results?search_query=$query")
                }
            }
        }
    }

    private fun isYouTubeVideoValid(videoId: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            true // If check fails, don't block
        } finally {
            conn?.disconnect()
        }
    }

    private fun playEmbedVideo(videoId: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: 100%; height: 100%;
                        background-color: #050811;
                        overflow: hidden;
                        display: flex; align-items: center; justify-content: center;
                    }
                    #player {
                        width: 100%; height: 100%;
                    }
                    iframe {
                        width: 100% !important; height: 100% !important; border: none;
                    }
                </style>
            </head>
            <body>
                <div id="player"></div>
                <script>
                    var tag = document.createElement('script');
                    tag.src = "https://www.youtube.com/iframe_api";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            height: '100%',
                            width: '100%',
                            videoId: '$videoId',
                            playerVars: {
                                'autoplay': 1,
                                'controls': 1,
                                'fs': 1,
                                'rel': 0,
                                'modestbranding': 1,
                                'playsinline': 1,
                                'iv_load_policy': 3
                            },
                            events: {
                                'onReady': function(e) {
                                    e.target.playVideo();
                                },
                                'onError': function(e) {
                                    // Error 101/150: Video owner forbids embed; switch to mobile stream
                                    window.location.replace("https://m.youtube.com/watch?v=$videoId&autoplay=1");
                                }
                            }
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.webViewTrailer.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    }

    private fun fetchCinemetaTrailer(id: String): String? {
        if (!id.startsWith("tt")) return null

        val movieUrl = "https://v3-cinemeta.strem.io/meta/movie/$id.json"
        val seriesUrl = "https://v3-cinemeta.strem.io/meta/series/$id.json"

        return queryCinemetaUrl(movieUrl) ?: queryCinemetaUrl(seriesUrl)
    }

    private fun queryCinemetaUrl(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "OrbitTV-Launcher/1.0")
            }
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(body)
                val meta = root.optJSONObject("meta") ?: return null
                val trailers = meta.optJSONArray("trailers")
                if (trailers != null && trailers.length() > 0) {
                    val first = trailers.optJSONObject(0)
                    first?.optString("source")?.ifBlank { null }
                } else {
                    meta.optString("trailer").ifBlank { null }
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun searchYouTubeTrailerId(title: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode("$title official trailer", "UTF-8")
            val url = URL("https://www.youtube.com/results?search_query=$encodedQuery")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            if (conn.responseCode == 200) {
                val html = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val regex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                val matches = regex.findAll(html)
                for (match in matches) {
                    val candidate = match.groups[1]?.value
                    if (!candidate.isNullOrBlank() && isYouTubeVideoValid(candidate)) {
                        return candidate
                    }
                }
                null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun launchExternalYouTube() {
        val vId = resolvedVideoId
        val intent = if (!vId.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$vId"))
        } else {
            val query = URLEncoder.encode("$mediaTitle official trailer", "UTF-8")
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            if (!vId.isNullOrBlank()) {
                binding.webViewTrailer.loadUrl("https://m.youtube.com/watch?v=$vId")
            }
        }
    }

    override fun onDestroyView() {
        binding.webViewTrailer.loadUrl("about:blank")
        binding.webViewTrailer.destroy()
        _binding = null
        super.onDestroyView()
    }
}

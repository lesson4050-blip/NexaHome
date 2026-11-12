package com.orbit.tv.launcher.features.cast

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.orbit.tv.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Embedded HTTP Cast receiver running locally on the TV box (port 8080).
 * Serves a mobile-friendly Web App to any smartphone scanning the QR code,
 * enabling direct video/photo streaming, YouTube deep-linking, and TV remote control.
 */
class CastWebServer(
    private val context: Context,
    private val port: Int = 8080,
    private val onPinRequested: () -> String
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(scope: CoroutineScope) {
        stop()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                while (isActive) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)

            // Read request line and headers
            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val pathWithQuery = parts[1]
            val path = pathWithQuery.substringBefore("?")
            val query = if (pathWithQuery.contains("?")) pathWithQuery.substringAfter("?") else ""

            var contentLength = 0
            var contentType = ""
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrBlank()) {
                if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                if (headerLine.startsWith("Content-Type:", ignoreCase = true)) {
                    contentType = headerLine.substringAfter(":").trim()
                }
                headerLine = reader.readLine()
            }

            if (method.equals("GET", ignoreCase = true)) {
                handleGet(path, query, output)
            } else if (method.equals("POST", ignoreCase = true)) {
                handlePost(path, query, contentType, contentLength, reader, input, output)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun handleGet(path: String, query: String, output: PrintWriter) {
        val currentPin = onPinRequested()
        val html = buildMobileWebHtml(currentPin)
        val bytes = html.toByteArray(Charsets.UTF_8)

        output.print("HTTP/1.1 200 OK\r\n")
        output.print("Content-Type: text/html; charset=utf-8\r\n")
        output.print("Content-Length: ${bytes.size}\r\n")
        output.print("Access-Control-Allow-Origin: *\r\n")
        output.print("Connection: close\r\n\r\n")
        output.flush()

        output.print(html)
        output.flush()
    }

    private fun handlePost(
        path: String,
        query: String,
        contentType: String,
        contentLength: Int,
        reader: BufferedReader,
        rawInput: InputStream,
        output: PrintWriter
    ) {
        when {
            path.startsWith("/api/play") -> {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val bodyStr = String(body)
                val params = parseQueryParams(bodyStr)
                val url = params["url"] ?: ""
                if (url.isNotBlank()) {
                    playMediaOnTv(url)
                }
                sendJsonResponse(output, """{"status":"ok","action":"play"}""")
            }
            path.startsWith("/api/remote") -> {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val bodyStr = String(body)
                val params = parseQueryParams(bodyStr)
                val key = params["key"] ?: ""
                handleRemoteKey(key)
                sendJsonResponse(output, """{"status":"ok","key":"$key"}""")
            }
            path.startsWith("/api/upload") -> {
                // Read binary payload into cache file
                try {
                    val ext = if (contentType.contains("image")) "jpg" else "mp4"
                    val file = File(context.cacheDir, "cast_phone_${System.currentTimeMillis()}.$ext")
                    val fileOut = FileOutputStream(file)
                    val buffer = ByteArray(8192)
                    var bytesRemaining = contentLength
                    while (bytesRemaining > 0) {
                        val toRead = minOf(buffer.size, bytesRemaining)
                        val read = rawInput.read(buffer, 0, toRead)
                        if (read == -1) break
                        fileOut.write(buffer, 0, read)
                        bytesRemaining -= read
                    }
                    fileOut.close()

                    openFileOnTv(file, if (ext == "jpg") "image/*" else "video/*")
                    sendJsonResponse(output, """{"status":"ok","message":"File opened on TV"}""")
                } catch (e: Exception) {
                    sendJsonResponse(output, """{"status":"error","message":"${e.message}"}""")
                }
            }
            else -> {
                sendJsonResponse(output, """{"status":"not_found"}""")
            }
        }
    }

    private fun sendJsonResponse(output: PrintWriter, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        output.print("HTTP/1.1 200 OK\r\n")
        output.print("Content-Type: application/json; charset=utf-8\r\n")
        output.print("Content-Length: ${bytes.size}\r\n")
        output.print("Access-Control-Allow-Origin: *\r\n")
        output.print("Connection: close\r\n\r\n")
        output.print(json)
        output.flush()
    }

    private fun parseQueryParams(data: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in data.split("&")) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                try {
                    val k = URLDecoder.decode(parts[0], "UTF-8")
                    val v = URLDecoder.decode(parts[1], "UTF-8")
                    map[k] = v
                } catch (e: Exception) {}
            }
        }
        return map
    }

    private fun playMediaOnTv(url: String) {
        mainHandler.post {
            try {
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val smartTube = "com.teamsmart.videomanager.tv"
                    val ytTv = "com.google.android.youtube.tv"
                    if (context.packageManager.getLaunchIntentForPackage(smartTube) != null) {
                        intent.setPackage(smartTube)
                    } else if (context.packageManager.getLaunchIntentForPackage(ytTv) != null) {
                        intent.setPackage(ytTv)
                    }
                    context.startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e2: Exception) {
                    // Silent failover or log - no intrusive toasts on TV
                }
            }
        }
    }

    private fun openFileOnTv(file: File, mimeType: String) {
        mainHandler.post {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Silent failover - no intrusive toasts on TV
            }
        }
    }

    private fun handleRemoteKey(key: String) {
        mainHandler.post {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (key) {
                "vol_up" -> audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "vol_down" -> audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> audio.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun buildMobileWebHtml(pin: String): String {
        val lang = context.resources.configuration.locales[0].language
        val statusText = context.getString(R.string.cast_web_connected, pin)
        val tabYt = context.getString(R.string.cast_web_tab_youtube)
        val tabFiles = context.getString(R.string.cast_web_tab_files)
        val tabRemote = context.getString(R.string.cast_web_tab_remote)
        val labelUrl = context.getString(R.string.cast_web_label_url)
        val phUrl = context.getString(R.string.cast_web_placeholder_url)
        val btnPlay = context.getString(R.string.cast_web_btn_play)
        val labelFiles = context.getString(R.string.cast_web_label_files)
        val btnUpload = context.getString(R.string.cast_web_btn_upload)
        val volUp = context.getString(R.string.cast_web_vol_up)
        val volDown = context.getString(R.string.cast_web_vol_down)
        val mute = context.getString(R.string.cast_web_mute)
        val playPause = context.getString(R.string.cast_web_play_pause)
        val sentToast = context.getString(R.string.cast_web_sent_toast)
        val alertEnterUrl = context.getString(R.string.cast_web_alert_enter_url)
        val alertConnError = context.getString(R.string.cast_web_alert_conn_error)
        val alertSelectFile = context.getString(R.string.cast_web_alert_select_file)
        val alertUploading = context.getString(R.string.cast_web_alert_uploading)
        val alertFileOpened = context.getString(R.string.cast_web_alert_file_opened)
        val alertUploadError = context.getString(R.string.cast_web_alert_upload_error)
        val cmdDone = context.getString(R.string.cast_web_cmd_done)

        return """
<!DOCTYPE html>
<html lang="$lang">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Orbit TV Cast</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0B0F19; color: #FFFFFF; min-height: 100vh; padding: 20px; display: flex; flex-direction: column; align-items: center; }
        .card { width: 100%; max-width: 440px; background: rgba(26, 32, 48, 0.85); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.1); border-radius: 24px; padding: 24px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
        .header { display: flex; align-items: center; margin-bottom: 20px; gap: 14px; }
        .logo { width: 44px; height: 44px; border-radius: 12px; background: linear-gradient(135deg, #06B6D4, #3B82F6); display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 20px; }
        .title { font-size: 20px; font-weight: 700; color: #FFFFFF; }
        .status-badge { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #10B981; font-weight: 600; margin-top: 4px; }
        .dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; animation: pulse 2s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
        .tabs { display: flex; gap: 8px; margin-bottom: 20px; background: rgba(0,0,0,0.3); padding: 4px; border-radius: 14px; }
        .tab-btn { flex: 1; padding: 10px; font-size: 13px; font-weight: 600; border: none; background: transparent; color: #94A3B8; border-radius: 10px; cursor: pointer; transition: 0.2s; }
        .tab-btn.active { background: #06B6D4; color: #000000; }
        .section { display: none; }
        .section.active { display: block; }
        .input-group { margin-bottom: 16px; }
        label { display: block; font-size: 13px; color: #94A3B8; margin-bottom: 8px; font-weight: 500; }
        input[type="text"], input[type="url"] { width: 100%; padding: 14px 16px; border-radius: 14px; background: rgba(0,0,0,0.4); border: 1px solid rgba(255,255,255,0.15); color: #FFFFFF; font-size: 14px; outline: none; }
        input:focus { border-color: #06B6D4; }
        .btn { width: 100%; padding: 14px; border-radius: 14px; font-size: 15px; font-weight: 700; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; background: linear-gradient(135deg, #06B6D4, #0EA5E9); color: #000000; box-shadow: 0 4px 14px rgba(6, 182, 212, 0.4); transition: transform 0.1s; }
        .btn:active { transform: scale(0.98); }
        .remote-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
        .remote-btn { padding: 16px; border-radius: 14px; font-size: 14px; font-weight: 600; border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.06); color: #FFFFFF; cursor: pointer; }
        .remote-btn:active { background: rgba(6, 182, 212, 0.2); border-color: #06B6D4; }
        .toast { margin-top: 14px; font-size: 13px; text-align: center; color: #10B981; display: none; font-weight: 600; }
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <div class="logo">📡</div>
            <div>
                <div class="title">Orbit TV Cast</div>
                <div class="status-badge"><span class="dot"></span> $statusText</div>
            </div>
        </div>

        <div class="tabs">
            <button class="tab-btn active" onclick="switchTab('youtube')">$tabYt</button>
            <button class="tab-btn" onclick="switchTab('files')">$tabFiles</button>
            <button class="tab-btn" onclick="switchTab('remote')">$tabRemote</button>
        </div>

        <!-- Tab 1: Video URL & YouTube -->
        <div id="tab-youtube" class="section active">
            <div class="input-group">
                <label>$labelUrl</label>
                <input type="url" id="mediaUrl" placeholder="$phUrl">
            </div>
            <button class="btn" onclick="sendMediaUrl()">$btnPlay</button>
        </div>

        <!-- Tab 2: Photos & Videos Upload -->
        <div id="tab-files" class="section">
            <div class="input-group">
                <label>$labelFiles</label>
                <input type="file" id="fileSelector" accept="image/*,video/*" style="display:block; width:100%; font-size:13px; color:#94A3B8;">
            </div>
            <button class="btn" onclick="uploadSelectedFile()">$btnUpload</button>
        </div>

        <!-- Tab 3: Remote Control -->
        <div id="tab-remote" class="section">
            <div class="remote-grid">
                <button class="remote-btn" onclick="sendRemote('vol_up')">$volUp</button>
                <button class="remote-btn" onclick="sendRemote('vol_down')">$volDown</button>
                <button class="remote-btn" onclick="sendRemote('mute')">$mute</button>
                <button class="remote-btn" onclick="sendRemote('mute')">$playPause</button>
            </div>
        </div>

        <div id="toastMsg" class="toast">$sentToast</div>
    </div>

    <script>
        function switchTab(tabId) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            if (tabId === 'youtube') {
                document.querySelectorAll('.tab-btn')[0].classList.add('active');
                document.getElementById('tab-youtube').classList.add('active');
            } else if (tabId === 'files') {
                document.querySelectorAll('.tab-btn')[1].classList.add('active');
                document.getElementById('tab-files').classList.add('active');
            } else {
                document.querySelectorAll('.tab-btn')[2].classList.add('active');
                document.getElementById('tab-remote').classList.add('active');
            }
        }

        function showNotification(text) {
            const t = document.getElementById('toastMsg');
            t.innerText = text;
            t.style.display = 'block';
            setTimeout(() => { t.style.display = 'none'; }, 3000);
        }

        function sendMediaUrl() {
            const url = document.getElementById('mediaUrl').value.trim();
            if (!url) return alert('$alertEnterUrl');
            fetch('/api/play', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'url=' + encodeURIComponent(url)
            }).then(() => {
                showNotification('$sentToast');
                document.getElementById('mediaUrl').value = '';
            }).catch(e => alert('$alertConnError'));
        }

        function uploadSelectedFile() {
            const f = document.getElementById('fileSelector').files[0];
            if (!f) return alert('$alertSelectFile');
            showNotification('$alertUploading');
            fetch('/api/upload', {
                method: 'POST',
                headers: { 'Content-Type': f.type || 'application/octet-stream' },
                body: f
            }).then(() => {
                showNotification('$alertFileOpened');
            }).catch(e => alert('$alertUploadError'));
        }

        function sendRemote(key) {
            fetch('/api/remote', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'key=' + encodeURIComponent(key)
            }).then(() => {
                showNotification('$cmdDone');
            });
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}

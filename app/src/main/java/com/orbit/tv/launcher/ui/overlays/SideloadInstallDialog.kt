package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogSideloadInstallBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SideloadInstallDialog : DialogFragment() {

    private var _binding: DialogSideloadInstallBinding? = null
    private val binding get() = _binding!!

    private var appName: String = ""
    private var packageName: String = ""
    private var apkUrl: String = ""
    private var iconRes: Int = 0

    private var downloadJob: Job? = null
    private var downloadedFile: File? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        const val TAG = "SideloadInstallDialog"

        private const val ARG_NAME = "arg_name"
        private const val ARG_PKG = "arg_pkg"
        private const val ARG_URL = "arg_url"
        private const val ARG_ICON = "arg_icon"

        fun newInstance(
            appName: String,
            packageName: String,
            apkUrl: String,
            iconRes: Int
        ): SideloadInstallDialog {
            return SideloadInstallDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, appName)
                    putString(ARG_PKG, packageName)
                    putString(ARG_URL, apkUrl)
                    putInt(ARG_ICON, iconRes)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appName = it.getString(ARG_NAME).orEmpty()
            packageName = it.getString(ARG_PKG).orEmpty()
            apkUrl = it.getString(ARG_URL).orEmpty()
            iconRes = it.getInt(ARG_ICON)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSideloadInstallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAppName.text = appName
        if (iconRes != 0) {
            binding.ivAppIcon.setImageResource(iconRes)
        }

        TvFocusHelper.bindFocusScale(binding.btnActionPrimary, 1.05f)
        TvFocusHelper.bindFocusScale(binding.btnActionSecondary, 1.05f)
        TvFocusHelper.bindFocusScale(binding.btnCancel, 1.05f)

        binding.btnCancel.setOnClickListener {
            cancelDownload()
            dismissAllowingStateLoss()
        }

        binding.btnActionSecondary.setOnClickListener {
            val siteUrl = when (packageName) {
                "com.teamsmart.videomanager.tv" -> "https://smarttubeapp.github.io"
                "tv.lampa" -> "https://lampa.mx"
                else -> apkUrl
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
            dismissAllowingStateLoss()
        }

        startDownload()
    }

    private fun startDownload() {
        binding.pbDownload.progress = 0
        binding.pbDownload.isIndeterminate = false
        binding.tvDownloadStatus.text = "Подключение к серверу..."
        binding.tvDownloadMetrics.text = "0% • 0.0 MB"
        binding.tvInstallNotice.visibility = View.GONE
        binding.btnActionPrimary.visibility = View.GONE
        binding.btnActionSecondary.visibility = View.GONE

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val installerDir = File(requireContext().cacheDir, "installer")
                if (!installerDir.exists()) {
                    installerDir.mkdirs()
                }

                val apkFile = File(installerDir, "${packageName}.apk")
                downloadedFile = apkFile

                val request = Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}")
                }

                val body = response.body ?: throw RuntimeException("Empty response body")
                val totalLength = body.contentLength()
                val isLengthKnown = totalLength > 0

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastUiUpdateTime = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                apkFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateTime > 150L) {
                                lastUiUpdateTime = now
                                val progress = if (isLengthKnown) {
                                    ((totalBytesRead * 100) / totalLength).toInt().coerceIn(0, 100)
                                } else 0
                                val readMb = totalBytesRead.toFloat() / (1024 * 1024)
                                val totalMb = if (isLengthKnown) totalLength.toFloat() / (1024 * 1024) else 0f

                                withContext(Dispatchers.Main) {
                                    if (_binding != null) {
                                        if (isLengthKnown) {
                                            binding.pbDownload.isIndeterminate = false
                                            binding.pbDownload.progress = progress
                                            binding.tvDownloadMetrics.text = String.format("%.0f%% • %.1f / %.1f MB", progress.toFloat(), readMb, totalMb)
                                        } else {
                                            binding.pbDownload.isIndeterminate = true
                                            binding.tvDownloadMetrics.text = String.format("%.1f MB", readMb)
                                        }
                                        binding.tvDownloadStatus.text = "Загрузка официального APK..."
                                    }
                                }
                            }
                        }
                    }
                }

                if (apkFile.length() < 100_000) {
                    throw RuntimeException("APK file incomplete")
                }

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.pbDownload.isIndeterminate = false
                        binding.pbDownload.progress = 100
                        binding.tvDownloadStatus.text = "Загрузка завершена! Запуск установки..."
                        binding.tvDownloadMetrics.text = "100%"
                        triggerInstallation(apkFile)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.pbDownload.isIndeterminate = false
                        binding.tvDownloadStatus.text = "Ошибка загрузки: ${e.message ?: "Сеть недоступна"}"
                        binding.tvDownloadMetrics.text = ""
                        binding.btnActionPrimary.visibility = View.VISIBLE
                        binding.btnActionPrimary.text = "Повторить"
                        binding.btnActionPrimary.setOnClickListener {
                            startDownload()
                        }
                        binding.btnActionSecondary.visibility = View.VISIBLE
                        binding.btnActionPrimary.requestFocus()
                    }
                }
            }
        }
    }

    private fun triggerInstallation(apkFile: File) {
        val context = context ?: return

        // Check if unknown sources permission is granted on Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                binding.tvDownloadStatus.text = "Требуется разрешение на установку"
                binding.tvInstallNotice.visibility = View.VISIBLE
                binding.tvInstallNotice.text = "Для завершения установки разрешите лаунчеру установку неизвестных приложений в настройках Android TV."
                binding.btnActionPrimary.visibility = View.VISIBLE
                binding.btnActionPrimary.text = "Разрешить"
                binding.btnActionPrimary.setOnClickListener {
                    try {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(settingsIntent)
                    } catch (_: Exception) {}
                }
                binding.btnActionPrimary.requestFocus()
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            dismissAllowingStateLoss()
        } catch (e: Exception) {
            binding.tvDownloadStatus.text = "Не удалось запустить установщик: ${e.message}"
            binding.tvInstallNotice.visibility = View.VISIBLE
            binding.tvInstallNotice.text = "Файл сохранен в кэш. Вы можете установить его через файловый менеджер или повторить попытку."
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        try {
            downloadedFile?.delete()
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        cancelDownload()
        _binding = null
        super.onDestroyView()
    }
}

package com.orbit.tv.launcher.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.tv.launcher.features.home.model.AppModel
import com.orbit.tv.launcher.features.home.model.MediaModel
import com.orbit.tv.launcher.platform.device_capabilities.VpnCapabilityManager
import com.orbit.tv.launcher.platform.package_manager.PackageManagerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.res.Resources
import android.os.Build
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.orbit.tv.launcher.features.home.data.WatchHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManagerRepository = PackageManagerRepository(application)
    val appPrefsRepository = com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(application)
    val watchHistoryRepository = WatchHistoryRepository(application)
    val vpnManager = com.orbit.tv.launcher.features.vpn.VpnManager(application)
    val networkStateManager = com.orbit.tv.launcher.platform.device_capabilities.NetworkStateManager(application)
    val networkStatus = networkStateManager.networkStatus
    val isWifiActive = networkStateManager.isWifiActive
    val wifiUiState = networkStateManager.wifiUiState

    val recentMedia: StateFlow<List<MediaModel>> = watchHistoryRepository.recentMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rawInstalledApps = MutableStateFlow<List<AppModel>>(emptyList())
    val rawInstalledApps: StateFlow<List<AppModel>> = _rawInstalledApps.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppModel>>(emptyList())
    val installedApps: StateFlow<List<AppModel>> = _installedApps.asStateFlow()

    private val _movies = MutableStateFlow<List<MediaModel>>(emptyList())
    val movies: StateFlow<List<MediaModel>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<MediaModel>>(emptyList())
    val series: StateFlow<List<MediaModel>> = _series.asStateFlow()

    private val _currentTime = MutableStateFlow("12:00")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _currentDay = MutableStateFlow("")
    val currentDay: StateFlow<String> = _currentDay.asStateFlow()

    val mediaRepository = com.orbit.tv.launcher.features.home.repository.MediaRepository(application)
    private var lastLoadedLanguage: String = ""

    init {
        loadInstalledApps()
        loadMediaShelves()
        startClockTicker()
    }

    fun loadMediaShelves(forceRefresh: Boolean = false) {
        lastLoadedLanguage = mediaRepository.getTmdbLanguage()
        viewModelScope.launch {
            val m = mediaRepository.getTrendingMovies(forceRefresh)
            _movies.value = m
        }
        viewModelScope.launch {
            val s = mediaRepository.getTrendingSeries(forceRefresh)
            _series.value = s
        }
    }

    fun checkAndReloadMediaIfNeeded() {
        val currentLang = mediaRepository.getTmdbLanguage()
        if (currentLang != lastLoadedLanguage || _movies.value.isEmpty() || _series.value.isEmpty()) {
            loadMediaShelves(forceRefresh = true)
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val systemApps = packageManagerRepository.getInstalledTvApps()
            val pinnedList = appPrefsRepository.getPinnedPackages()
            val hiddenSet = appPrefsRepository.getHiddenPackages()

            val enrichedApps = systemApps.map { app ->
                app.copy(
                    isPinned = pinnedList.contains(app.packageName),
                    isHidden = hiddenSet.contains(app.packageName)
                )
            }
            _rawInstalledApps.value = enrichedApps

            val visibleApps = enrichedApps.filter { !it.isHidden }
            val pinnedApps = visibleApps
                .filter { it.isPinned }
                .sortedBy { pinnedList.indexOf(it.packageName) }
            val unpinnedApps = visibleApps.filter { !it.isPinned }

            _installedApps.value = pinnedApps + unpinnedApps
        }
    }

    fun launchApp(app: AppModel): Boolean {
        return packageManagerRepository.launchApp(app)
    }

    fun recordWatched(media: MediaModel, targetApp: String? = null) {
        viewModelScope.launch {
            watchHistoryRepository.recordWatched(media, targetApp)
        }
    }

    fun getActiveLocale(): Locale {
        return try {
            val sysConfig = Resources.getSystem().configuration
            val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!sysConfig.locales.isEmpty) sysConfig.locales[0] else null
            } else {
                @Suppress("DEPRECATION")
                sysConfig.locale
            }
            locale ?: Locale.getDefault()
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            var cachedLocale: Locale? = null
            var cached24Hour: Boolean? = null
            var timeFormat: SimpleDateFormat? = null
            var dateFormat: SimpleDateFormat? = null
            var dayFormat: SimpleDateFormat? = null

            while (true) {
                val currentLocale = getActiveLocale()
                val is24Hour = DateFormat.is24HourFormat(app)

                if (currentLocale != cachedLocale || is24Hour != cached24Hour) {
                    cachedLocale = currentLocale
                    cached24Hour = is24Hour
                    Locale.setDefault(currentLocale)
                    val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
                    timeFormat = SimpleDateFormat(timePattern, currentLocale)
                    dateFormat = SimpleDateFormat("d MMMM yyyy", currentLocale)
                    dayFormat = SimpleDateFormat("EEEE", currentLocale)
                }

                val now = Date()
                _currentTime.value = timeFormat?.format(now).orEmpty()
                _currentDate.value = dateFormat?.format(now).orEmpty()
                _currentDay.value = dayFormat?.format(now)?.uppercase(currentLocale).orEmpty()
                delay(1000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vpnManager.onDestroy()
    }
}

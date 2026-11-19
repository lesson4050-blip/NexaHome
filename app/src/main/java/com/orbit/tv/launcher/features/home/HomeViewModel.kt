package com.orbit.tv.launcher.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.tv.launcher.R
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

    val movieGenres = listOf(
        com.orbit.tv.launcher.features.home.model.GenreFilter("all", R.string.genre_all, null, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("action", R.string.genre_action, 28, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("comedy", R.string.genre_comedy, 35, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("scifi", R.string.genre_scifi, 878, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("horror", R.string.genre_horror, 27, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("drama", R.string.genre_drama, 18, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("animation", R.string.genre_animation, 16, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("thriller", R.string.genre_thriller, 53, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("adventure", R.string.genre_adventure, 12, isSeries = false),
        com.orbit.tv.launcher.features.home.model.GenreFilter("family", R.string.genre_family, 10751, isSeries = false)
    )

    val seriesGenres = listOf(
        com.orbit.tv.launcher.features.home.model.GenreFilter("all", R.string.genre_all, null, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("drama", R.string.genre_drama, 18, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("comedy", R.string.genre_comedy, 35, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("scifi", R.string.genre_scifi, 10765, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("action", R.string.genre_action, 10759, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("crime", R.string.genre_crime, 80, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("animation", R.string.genre_animation, 16, isSeries = true),
        com.orbit.tv.launcher.features.home.model.GenreFilter("doc", R.string.genre_documentary, 99, isSeries = true)
    )

    private val _selectedMovieGenre = MutableStateFlow(movieGenres[0])
    val selectedMovieGenre: StateFlow<com.orbit.tv.launcher.features.home.model.GenreFilter> = _selectedMovieGenre.asStateFlow()

    private val _selectedSeriesGenre = MutableStateFlow(seriesGenres[0])
    val selectedSeriesGenre: StateFlow<com.orbit.tv.launcher.features.home.model.GenreFilter> = _selectedSeriesGenre.asStateFlow()

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
        viewModelScope.launch {
            delay(150) // Let initial apps shelf & UI frame layout first
            loadMediaShelves()
        }
        startClockTicker()
    }

    fun selectMovieGenre(genre: com.orbit.tv.launcher.features.home.model.GenreFilter) {
        if (_selectedMovieGenre.value == genre) return
        _selectedMovieGenre.value = genre
        viewModelScope.launch {
            val m = mediaRepository.getMoviesByGenre(genre.tmdbGenreId)
            _movies.value = m
        }
    }

    fun selectSeriesGenre(genre: com.orbit.tv.launcher.features.home.model.GenreFilter) {
        if (_selectedSeriesGenre.value == genre) return
        _selectedSeriesGenre.value = genre
        viewModelScope.launch {
            val s = mediaRepository.getSeriesByGenre(genre.tmdbGenreId)
            _series.value = s
        }
    }

    fun loadMediaShelves(forceRefresh: Boolean = false) {
        lastLoadedLanguage = mediaRepository.getTmdbLanguage()
        viewModelScope.launch {
            val m = mediaRepository.getMoviesByGenre(_selectedMovieGenre.value.tmdbGenreId, forceRefresh)
            _movies.value = m
        }
        viewModelScope.launch {
            val s = mediaRepository.getSeriesByGenre(_selectedSeriesGenre.value.tmdbGenreId, forceRefresh)
            _series.value = s
        }
    }

    fun checkAndReloadMediaIfNeeded() {
        val currentLang = mediaRepository.getTmdbLanguage()
        if (currentLang != lastLoadedLanguage || _movies.value.isEmpty() || _series.value.isEmpty()) {
            loadMediaShelves(forceRefresh = true)
        }
    }

    fun loadInstalledApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val systemApps = packageManagerRepository.getInstalledTvApps(forceRefresh)
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

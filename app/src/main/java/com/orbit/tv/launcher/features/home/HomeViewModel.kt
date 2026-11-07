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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManagerRepository = PackageManagerRepository(application)
    val appPrefsRepository = com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(application)
    val vpnManager = com.orbit.tv.launcher.features.vpn.VpnManager(application)
    val networkStateManager = com.orbit.tv.launcher.platform.device_capabilities.NetworkStateManager(application)
    val networkStatus = networkStateManager.networkStatus

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

    init {
        loadInstalledApps()
        loadMediaShelves()
        startClockTicker()
    }

    fun loadMediaShelves() {
        viewModelScope.launch {
            val m = mediaRepository.getTrendingMovies()
            _movies.value = m
        }
        viewModelScope.launch {
            val s = mediaRepository.getTrendingSeries()
            _series.value = s
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

    private fun startClockTicker() {
        viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

            while (true) {
                val now = Date()
                _currentTime.value = timeFormat.format(now)
                _currentDate.value = dateFormat.format(now)
                _currentDay.value = dayFormat.format(now).uppercase(Locale.getDefault())
                delay(1000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vpnManager.onDestroy()
    }
}

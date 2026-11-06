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
    val vpnCapabilityManager = VpnCapabilityManager(application)

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

    init {
        loadInstalledApps()
        loadCuratedMediaShelves()
        startClockTicker()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = packageManagerRepository.getInstalledTvApps()
            _installedApps.value = apps
        }
    }

    fun launchApp(app: AppModel): Boolean {
        return packageManagerRepository.launchApp(app)
    }

    private fun loadCuratedMediaShelves() {
        // High quality curated offline-ready items matching reference design exactly
        _movies.value = listOf(
            MediaModel(
                id = "m1",
                title = "HYPNOTIC",
                posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&q=80",
                badge = "NETFLIX ORIGINAL",
                genre = "Sci-Fi Thriller"
            ),
            MediaModel(
                id = "m2",
                title = "SECURITY",
                posterUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=400&q=80",
                badge = "Action & Adventure",
                genre = "Action"
            ),
            MediaModel(
                id = "m3",
                title = "BLOOD RED SKY",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80",
                badge = "4K Ultra HD",
                genre = "Horror Thriller"
            ),
            MediaModel(
                id = "m4",
                title = "THE 8TH NIGHT",
                posterUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=400&q=80",
                badge = "Supernatural",
                genre = "Mystery"
            ),
            MediaModel(
                id = "m5",
                title = "ARMY OF THE DEAD",
                posterUrl = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=400&q=80",
                badge = "Dolby Atmos",
                genre = "Heist Zombie"
            ),
            MediaModel(
                id = "m6",
                title = "OXYGEN",
                posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&q=80",
                badge = "Sci-Fi Survival",
                genre = "Sci-Fi"
            )
        )

        _series.value = listOf(
            MediaModel(
                id = "s1",
                title = "SQUID GAME",
                posterUrl = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=400&q=80",
                badge = "#1 in TV Shows",
                isSeries = true
            ),
            MediaModel(
                id = "s2",
                title = "MONEY HEIST",
                posterUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=400&q=80",
                badge = "Season Finale",
                isSeries = true
            ),
            MediaModel(
                id = "s3",
                title = "STRANGER THINGS",
                posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&q=80",
                badge = "Sci-Fi Classic",
                isSeries = true
            ),
            MediaModel(
                id = "s4",
                title = "DARK",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80",
                badge = "Mind-bending",
                isSeries = true
            ),
            MediaModel(
                id = "s5",
                title = "THE WITCHER",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=400&q=80",
                badge = "Fantasy Saga",
                isSeries = true
            ),
            MediaModel(
                id = "s6",
                title = "ARCANE",
                posterUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80",
                badge = "Critically Acclaimed",
                isSeries = true
            )
        )
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
        vpnCapabilityManager.unregister()
    }
}

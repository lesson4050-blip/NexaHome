package com.orbit.tv.launcher.features.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.features.iptv.model.IptvCategory
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import com.orbit.tv.launcher.features.iptv.repository.IptvRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IptvRepository(application)

    private val _allChannels = MutableStateFlow<List<IptvChannel>>(emptyList())

    private val _filteredChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val filteredChannels: StateFlow<List<IptvChannel>> = _filteredChannels.asStateFlow()

    private val _activeChannel = MutableStateFlow<IptvChannel?>(null)
    val activeChannel: StateFlow<IptvChannel?> = _activeChannel.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<IptvCategory>>(emptyList())
    val categories: StateFlow<List<IptvCategory>> = _categories.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _currentTime = MutableStateFlow("12:00")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadChannels()
        startClockTicker()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                _currentTime.value = timeFormat.format(Date())
                delay(1000L)
            }
        }
    }

    fun loadChannels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            val channels = repository.loadDefaultChannels(forceRefresh)
            _allChannels.value = channels
            updateCategories(channels)
            applyFilter(_selectedCategory.value)
            if (channels.isNotEmpty() && _activeChannel.value == null) {
                _activeChannel.value = channels.first()
            }
            _isLoading.value = false
        }
    }

    private fun updateCategories(channels: List<IptvChannel>) {
        val app = getApplication<Application>()
        val list = mutableListOf<IptvCategory>()
        list.add(IptvCategory(id = "All", name = app.getString(R.string.cat_all), count = channels.size))
        val favCount = channels.count { it.isFavorite }
        list.add(IptvCategory(id = "Favorites", name = app.getString(R.string.cat_favorites), count = favCount))

        val groups = channels.map { it.group.trim() }
            .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) && !it.equals("Favorites", ignoreCase = true) }
            .distinct()
        for (g in groups) {
            val count = channels.count { it.group.equals(g, ignoreCase = true) }
            list.add(IptvCategory(id = g, name = g, count = count))
        }
        _categories.value = list
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        applyFilter(category)
    }

    fun selectChannel(channel: IptvChannel) {
        if (_activeChannel.value?.id == channel.id) {
            toggleFullscreen()
        } else {
            _activeChannel.value = channel
        }
    }

    fun nextChannel() {
        val list = _filteredChannels.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _activeChannel.value?.id }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        _activeChannel.value = list[nextIndex]
    }

    fun previousChannel() {
        val list = _filteredChannels.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _activeChannel.value?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        _activeChannel.value = list[prevIndex]
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
    }

    fun toggleFavorite(channel: IptvChannel) {
        viewModelScope.launch {
            val newFav = !channel.isFavorite
            repository.toggleFavorite(channel.id, newFav)
            val updated = _allChannels.value.map {
                if (it.id == channel.id) it.copy(isFavorite = newFav) else it
            }
            _allChannels.value = updated
            updateCategories(updated)
            applyFilter(_selectedCategory.value)
        }
    }

    fun importPlaylist(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.loadPlaylistFromUrl(url)
            if (result.isSuccess) {
                val count = result.getOrThrow()
                loadChannels(forceRefresh = false)
                onResult(true, "Успешно загружено каналов: $count")
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки"
                onResult(false, errorMsg)
            }
            _isLoading.value = false
        }
    }

    fun resetDefaultPlaylist(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.resetToDefaultPlaylist()
            if (result.isSuccess) {
                val count = result.getOrThrow()
                loadChannels(forceRefresh = false)
                onResult(true, "Восстановлен стандартный плейлист ($count каналов)")
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Ошибка сброса"
                onResult(false, errorMsg)
            }
            _isLoading.value = false
        }
    }

    fun getActivePlaylistUrl(): String? {
        return repository.getCustomPlaylistUrl()
    }

    private fun applyFilter(category: String) {
        val all = _allChannels.value
        _filteredChannels.value = when {
            category.equals("All", ignoreCase = true) -> all
            category.equals("Favorites", ignoreCase = true) -> all.filter { it.isFavorite }
            else -> all.filter { it.group.equals(category, ignoreCase = true) }
        }
    }
}

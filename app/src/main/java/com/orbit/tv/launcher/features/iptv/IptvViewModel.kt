package com.orbit.tv.launcher.features.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import com.orbit.tv.launcher.features.iptv.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IptvRepository(application)

    private val _allChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    
    private val _filteredChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val filteredChannels: StateFlow<List<IptvChannel>> = _filteredChannels.asStateFlow()

    private val _activeChannel = MutableStateFlow<IptvChannel?>(null)
    val activeChannel: StateFlow<IptvChannel?> = _activeChannel.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            val channels = repository.loadDefaultChannels()
            _allChannels.value = channels
            applyFilter(_selectedCategory.value)
            if (channels.isNotEmpty() && _activeChannel.value == null) {
                _activeChannel.value = channels.first()
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        applyFilter(category)
    }

    fun selectChannel(channel: IptvChannel) {
        if (_activeChannel.value?.id == channel.id) {
            // Clicking again on the currently playing channel toggles Fullscreen mode!
            toggleFullscreen()
        } else {
            _activeChannel.value = channel
        }
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
    }

    private fun applyFilter(category: String) {
        val all = _allChannels.value
        _filteredChannels.value = if (category.equals("All", ignoreCase = true)) {
            all
        } else {
            all.filter { it.group.equals(category, ignoreCase = true) }
        }
    }
}

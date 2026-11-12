package com.orbit.tv.launcher.features.iptv.repository

import android.content.Context
import android.content.SharedPreferences
import com.orbit.tv.launcher.features.iptv.data.ChannelEntity
import com.orbit.tv.launcher.features.iptv.data.OrbitDatabase
import com.orbit.tv.launcher.features.iptv.epg.XmltvParser
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class IptvRepository(private val context: Context) {

    private val db = OrbitDatabase.getInstance(context)
    private val iptvDao = db.iptvDao()
    private val logoPattern = Pattern.compile("tvg-logo=\"([^\"]+)\"")
    private val groupPattern = Pattern.compile("group-title=\"([^\"]+)\"")
    private val idPattern = Pattern.compile("tvg-id=\"([^\"]+)\"")
    private val xmltvParser = XmltvParser()

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun loadDefaultChannels(forceRefresh: Boolean = false): List<IptvChannel> = withContext(Dispatchers.IO) {
        try {
            // 1. Check offline Room DB cache
            val cachedEntities = iptvDao.getAllChannels()
            if (!forceRefresh && cachedEntities.isNotEmpty()) {
                return@withContext mapEntitiesToDomain(cachedEntities)
            }

            // 2. If custom playlist URL exists, try to re-fetch from that URL
            val customUrl = getCustomPlaylistUrl()
            if (!customUrl.isNullOrBlank()) {
                val remoteResult = fetchChannelsFromUrl(customUrl)
                if (remoteResult.isSuccess && remoteResult.getOrNull()?.isNotEmpty() == true) {
                    val rawChannels = remoteResult.getOrThrow()
                    val entities = rawToEntities(rawChannels)
                    iptvDao.clearAllChannels()
                    iptvDao.insertChannels(entities)
                    return@withContext mapEntitiesToDomain(entities)
                }
            }

            // 3. Fallback to bundled assets default channels
            val rawChannels = parseM3uFromAssets()
            if (rawChannels.isNotEmpty()) {
                val entities = rawToEntities(rawChannels)
                iptvDao.clearAllChannels()
                iptvDao.insertChannels(entities)
                return@withContext mapEntitiesToDomain(entities)
            }

            mapEntitiesToDomain(cachedEntities)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun loadPlaylistFromUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = url.trim()
            if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Некорректный URL (должен начинаться с http:// или https://)"))
            }

            val fetchResult = fetchChannelsFromUrl(trimmedUrl)
            if (fetchResult.isFailure) {
                return@withContext Result.failure(fetchResult.exceptionOrNull() ?: Exception("Ошибка загрузки плейлиста"))
            }

            val rawChannels = fetchResult.getOrThrow()
            if (rawChannels.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Плейлист пуст или не содержит доступных каналов"))
            }

            val entities = rawToEntities(rawChannels)
            iptvDao.clearAllChannels()
            iptvDao.insertChannels(entities)

            prefs.edit().putString(KEY_CUSTOM_PLAYLIST_URL, trimmedUrl).apply()

            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetToDefaultPlaylist(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            prefs.edit().remove(KEY_CUSTOM_PLAYLIST_URL).apply()
            iptvDao.clearAllChannels()
            val rawChannels = parseM3uFromAssets()
            val entities = rawToEntities(rawChannels)
            iptvDao.insertChannels(entities)
            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCustomPlaylistUrl(): String? {
        return prefs.getString(KEY_CUSTOM_PLAYLIST_URL, null)
    }

    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        iptvDao.updateFavorite(channelId, isFavorite)
    }

    suspend fun updateLastWatched(channelId: String) = withContext(Dispatchers.IO) {
        iptvDao.updateLastWatched(channelId, System.currentTimeMillis())
    }

    private fun rawToEntities(rawChannels: List<RawChannelData>): List<ChannelEntity> {
        return rawChannels.map { raw ->
            ChannelEntity(
                id = raw.id,
                number = raw.number,
                name = raw.name,
                logoUrl = raw.logoUrl,
                streamUrl = raw.streamUrl,
                groupTitle = raw.group,
                isFavorite = false,
                lastWatchedTimestamp = 0L
            )
        }
    }

    private suspend fun mapEntitiesToDomain(entities: List<ChannelEntity>): List<IptvChannel> = withContext(Dispatchers.IO) {
        val channelIds = entities.map { it.id }.toSet()
        val epgSchedules = try {
            context.assets.open("default_epg.xml").use { epgStream ->
                xmltvParser.parseStream(epgStream, channelIds)
            }
        } catch (e: Exception) {
            emptyMap()
        }

        entities.map { entity ->
            val schedule = epgSchedules[entity.id]
            IptvChannel(
                id = entity.id,
                number = entity.number,
                name = entity.name,
                logoUrl = entity.logoUrl,
                streamUrl = entity.streamUrl,
                group = entity.groupTitle,
                currentProgram = schedule?.currentProgram ?: "Прямой эфир • ${entity.name}",
                nextProgram = schedule?.nextProgram ?: "Программа уточняется",
                timeRange = schedule?.timeRange ?: "LIVE NOW",
                progressPercent = schedule?.progressPercent ?: 45,
                isFavorite = entity.isFavorite
            )
        }
    }

    private fun fetchChannelsFromUrl(playlistUrl: String): Result<List<RawChannelData>> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(playlistUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "*/*")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return Result.failure(Exception("HTTP $responseCode: Сервер отклонил запрос плейлиста"))
            }

            connection.inputStream.use { stream ->
                val channels = parseM3uStream(stream, maxChannels = 500)
                Result.success(channels)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseM3uFromAssets(): List<RawChannelData> {
        return try {
            context.assets.open("default_channels.m3u").use { inputStream ->
                parseM3uStream(inputStream, maxChannels = 200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseM3uStream(inputStream: InputStream, maxChannels: Int = 500): List<RawChannelData> {
        val rawChannels = mutableListOf<RawChannelData>()
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var currentLine: String? = reader.readLine()
            var pendingExtinf: String? = null
            var channelIndex = 1

            while (currentLine != null && rawChannels.size < maxChannels) {
                val line = currentLine.trim()
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    pendingExtinf = line
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val streamUrl = line
                    val extinf = pendingExtinf ?: ""

                    val id = extractAttribute(idPattern, extinf) ?: "chan_$channelIndex"
                    val logo = extractAttribute(logoPattern, extinf)
                    val group = extractAttribute(groupPattern, extinf) ?: "Общие"

                    val commaIndex = extinf.lastIndexOf(',')
                    val rawTitle = if (commaIndex != -1 && commaIndex < extinf.length - 1) {
                        extinf.substring(commaIndex + 1).trim()
                    } else {
                        "Канал $channelIndex"
                    }

                    rawChannels.add(
                        RawChannelData(
                            id = id,
                            number = channelIndex,
                            name = rawTitle,
                            logoUrl = logo,
                            streamUrl = streamUrl,
                            group = group
                        )
                    )
                    channelIndex++
                    pendingExtinf = null
                }
                currentLine = reader.readLine()
            }
        }
        return rawChannels
    }

    private fun extractAttribute(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private data class RawChannelData(
        val id: String,
        val number: Int,
        val name: String,
        val logoUrl: String?,
        val streamUrl: String,
        val group: String
    )

    companion object {
        private const val PREFS_NAME = "orbit_iptv_prefs"
        private const val KEY_CUSTOM_PLAYLIST_URL = "key_custom_playlist_url"
    }
}

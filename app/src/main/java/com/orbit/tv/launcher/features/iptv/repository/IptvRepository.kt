package com.orbit.tv.launcher.features.iptv.repository

import android.content.Context
import com.orbit.tv.launcher.features.iptv.data.ChannelEntity
import com.orbit.tv.launcher.features.iptv.data.OrbitDatabase
import com.orbit.tv.launcher.features.iptv.epg.XmltvParser
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class IptvRepository(private val context: Context) {

    private val db = OrbitDatabase.getInstance(context)
    private val iptvDao = db.iptvDao()
    private val logoPattern = Pattern.compile("tvg-logo=\"([^\"]+)\"")
    private val groupPattern = Pattern.compile("group-title=\"([^\"]+)\"")
    private val idPattern = Pattern.compile("tvg-id=\"([^\"]+)\"")
    private val xmltvParser = XmltvParser()

    suspend fun loadDefaultChannels(forceRefresh: Boolean = false): List<IptvChannel> = withContext(Dispatchers.IO) {
        try {
            // 1. Check offline Room DB cache
            val cachedEntities = iptvDao.getAllChannels()
            if (!forceRefresh && cachedEntities.isNotEmpty()) {
                return@withContext mapEntitiesToDomain(cachedEntities)
            }

            // 2. If DB cache is empty or forceRefresh, parse default channels from M3U
            val rawChannels = parseM3uFromAssets()
            if (rawChannels.isNotEmpty()) {
                val entities = rawChannels.map { raw ->
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
                iptvDao.insertChannels(entities)
                return@withContext mapEntitiesToDomain(entities)
            }

            mapEntitiesToDomain(cachedEntities)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        iptvDao.updateFavorite(channelId, isFavorite)
    }

    suspend fun updateLastWatched(channelId: String) = withContext(Dispatchers.IO) {
        iptvDao.updateLastWatched(channelId, System.currentTimeMillis())
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
                currentProgram = schedule?.currentProgram ?: "Live Broadcast • ${entity.name}",
                nextProgram = schedule?.nextProgram ?: "Upcoming Schedule",
                timeRange = schedule?.timeRange ?: "LIVE NOW",
                progressPercent = schedule?.progressPercent ?: 45,
                isFavorite = entity.isFavorite
            )
        }
    }

    private fun parseM3uFromAssets(): List<RawChannelData> {
        val rawChannels = mutableListOf<RawChannelData>()
        try {
            context.assets.open("default_channels.m3u").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var currentLine: String? = reader.readLine()
                    var pendingExtinf: String? = null
                    var channelIndex = 1

                    while (currentLine != null) {
                        val line = currentLine.trim()
                        if (line.startsWith("#EXTINF:")) {
                            pendingExtinf = line
                        } else if (line.isNotEmpty() && !line.startsWith("#")) {
                            val streamUrl = line
                            val extinf = pendingExtinf ?: ""

                            val id = extractAttribute(idPattern, extinf) ?: "chan_$channelIndex"
                            val logo = extractAttribute(logoPattern, extinf)
                            val group = extractAttribute(groupPattern, extinf) ?: "General"

                            val commaIndex = extinf.lastIndexOf(',')
                            val rawTitle = if (commaIndex != -1 && commaIndex < extinf.length - 1) {
                                extinf.substring(commaIndex + 1).trim()
                            } else {
                                "Channel $channelIndex"
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
}

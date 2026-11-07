package com.orbit.tv.launcher.features.iptv.repository

import android.content.Context
import com.orbit.tv.launcher.features.iptv.epg.XmltvParser
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

class IptvRepository(private val context: Context) {

    private val logoPattern = Pattern.compile("tvg-logo=\"([^\"]+)\"")
    private val groupPattern = Pattern.compile("group-title=\"([^\"]+)\"")
    private val idPattern = Pattern.compile("tvg-id=\"([^\"]+)\"")
    private val xmltvParser = XmltvParser()

    suspend fun loadDefaultChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        val rawChannels = mutableListOf<RawChannelData>()
        val channelIds = mutableSetOf<String>()

        try {
            // 1. Read M3U playlist from assets
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

                            channelIds.add(id)
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

            // 2. Parse real XMLTV EPG feed
            val epgSchedules = try {
                context.assets.open("default_epg.xml").use { epgStream ->
                    xmltvParser.parseStream(epgStream, channelIds)
                }
            } catch (e: Exception) {
                emptyMap()
            }

            // 3. Combine Channel Data with Real EPG Schedule
            rawChannels.map { raw ->
                val schedule = epgSchedules[raw.id]
                IptvChannel(
                    id = raw.id,
                    number = raw.number,
                    name = raw.name,
                    logoUrl = raw.logoUrl,
                    streamUrl = raw.streamUrl,
                    group = raw.group,
                    currentProgram = schedule?.currentProgram ?: "Live Broadcast • ${raw.name}",
                    nextProgram = schedule?.nextProgram ?: "Upcoming Schedule",
                    timeRange = schedule?.timeRange ?: "LIVE NOW",
                    progressPercent = schedule?.progressPercent ?: 45
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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

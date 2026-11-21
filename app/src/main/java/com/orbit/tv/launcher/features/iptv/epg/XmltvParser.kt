package com.orbit.tv.launcher.features.iptv.epg

import android.util.Xml
import com.orbit.tv.launcher.features.iptv.data.ProgramEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EpgChannelSchedule(
    val currentProgram: String,
    val nextProgram: String,
    val timeRange: String,
    val progressPercent: Int
)

data class EpgParseResult(
    val schedules: Map<String, EpgChannelSchedule>,
    val programEntities: List<ProgramEntity>
)

/**
 * High-performance, streaming XMLTV (EPG) parser.
 * Uses XmlPullParser with subtree skipping for minimal CPU/memory footprint suitable for 1 GB RAM Android TV.
 */
class XmltvParser {

    private val timeDisplayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun parseStream(inputStream: InputStream, targetChannelIds: Set<String> = emptySet()): EpgParseResult {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        val nowMs = System.currentTimeMillis()
        val channelPrograms = mutableMapOf<String, MutableList<RawEpgItem>>()

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        // Skip entire <channel> subtree (display-name, icon, etc.)
                        skip(parser)
                    }
                    "programme" -> {
                        val channel = parser.getAttributeValue(null, "channel")
                        if (channel == null || (targetChannelIds.isNotEmpty() && !targetChannelIds.contains(channel))) {
                            // Immediately skip non-targeted channels without parsing children
                            skip(parser)
                        } else {
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            val startMs = parseXmltvTime(startStr)
                            val stopMs = parseXmltvTime(stopStr)
                            var title = ""
                            var description: String? = null

                            while (parser.next() != XmlPullParser.END_TAG) {
                                if (parser.eventType != XmlPullParser.START_TAG) {
                                    continue
                                }
                                when (parser.name) {
                                    "title" -> title = readText(parser)
                                    "desc" -> description = readText(parser)
                                    else -> skip(parser)
                                }
                            }

                            if (title.isNotBlank()) {
                                val list = channelPrograms.getOrPut(channel) { mutableListOf() }
                                list.add(RawEpgItem(title.trim(), description?.trim(), startMs, stopMs))
                            }
                        }
                    }
                    else -> {
                        // Root-level tags like <tv>, let parser advance
                    }
                }
            }
            eventType = parser.next()
        }

        val resultMap = mutableMapOf<String, EpgChannelSchedule>()
        val programEntities = mutableListOf<ProgramEntity>()

        for ((chanId, rawProgs) in channelPrograms) {
            if (rawProgs.isEmpty()) continue

            val normalizedProgs = normalizeToToday(rawProgs)
            normalizedProgs.sortBy { it.startMs }

            // Find current active program
            val current = normalizedProgs.firstOrNull { nowMs in it.startMs until it.stopMs }
                ?: normalizedProgs.firstOrNull { it.startMs >= nowMs }
                ?: normalizedProgs.last()

            val next = normalizedProgs.firstOrNull { it.startMs >= current.stopMs }
            val startFormatted = timeDisplayFormat.format(Date(current.startMs))
            val stopFormatted = timeDisplayFormat.format(Date(current.stopMs))
            val range = "$startFormatted - $stopFormatted"

            val totalDuration = (current.stopMs - current.startMs).coerceAtLeast(1)
            val elapsed = (nowMs - current.startMs).coerceIn(0, totalDuration)
            val percent = ((elapsed * 100) / totalDuration).toInt().coerceIn(5, 95)

            resultMap[chanId] = EpgChannelSchedule(
                currentProgram = current.title,
                nextProgram = next?.title ?: "Upcoming Broadcast",
                timeRange = range,
                progressPercent = percent
            )

            for (prog in normalizedProgs) {
                val pStart = timeDisplayFormat.format(Date(prog.startMs))
                val pStop = timeDisplayFormat.format(Date(prog.stopMs))
                programEntities.add(
                    ProgramEntity(
                        programId = "${chanId}_${prog.startMs}",
                        channelId = chanId,
                        title = prog.title,
                        description = prog.description,
                        startTime = prog.startMs,
                        endTime = prog.stopMs,
                        timeRange = "$pStart - $pStop"
                    )
                )
            }
        }

        return EpgParseResult(resultMap, programEntities)
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            return
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
        return sb.toString()
    }

    private fun normalizeToToday(progs: List<RawEpgItem>): MutableList<RawEpgItem> {
        val now = System.currentTimeMillis()
        val hasFuture = progs.any { it.stopMs > now }
        if (hasFuture && progs.any { it.startMs <= now && it.stopMs > now }) {
            return progs.toMutableList()
        }

        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)

        val itemCal = Calendar.getInstance()
        return progs.map { item ->
            itemCal.timeInMillis = item.startMs
            itemCal.set(Calendar.YEAR, todayYear)
            itemCal.set(Calendar.DAY_OF_YEAR, todayDayOfYear)
            val newStart = itemCal.timeInMillis

            itemCal.timeInMillis = item.stopMs
            itemCal.set(Calendar.YEAR, todayYear)
            itemCal.set(Calendar.DAY_OF_YEAR, todayDayOfYear)
            val newStop = if (itemCal.timeInMillis <= newStart) newStart + (3600 * 1000L) else itemCal.timeInMillis

            RawEpgItem(item.title, item.description, newStart, newStop)
        }.toMutableList()
    }

    private fun parseXmltvTime(timeStr: String?): Long {
        if (timeStr == null || timeStr.length < 14) return 0L
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val year = timeStr.substring(0, 4).toInt()
            val month = timeStr.substring(4, 6).toInt() - 1
            val day = timeStr.substring(6, 8).toInt()
            val hour = timeStr.substring(8, 10).toInt()
            val min = timeStr.substring(10, 12).toInt()
            val sec = timeStr.substring(12, 14).toInt()
            cal.set(year, month, day, hour, min, sec)
            cal.set(Calendar.MILLISECOND, 0)

            var timeMs = cal.timeInMillis
            val trimmed = timeStr.trim()
            if (trimmed.length >= 19) {
                val spaceOrSignIdx = 14
                val signChar = trimmed[if (trimmed[spaceOrSignIdx] == ' ') spaceOrSignIdx + 1 else spaceOrSignIdx]
                if (signChar == '+' || signChar == '-') {
                    val sign = if (signChar == '-') -1 else 1
                    val offsetIdx = if (trimmed[spaceOrSignIdx] == ' ') spaceOrSignIdx + 2 else spaceOrSignIdx + 1
                    val offsetHours = trimmed.substring(offsetIdx, offsetIdx + 2).toInt()
                    val offsetMinutes = trimmed.substring(offsetIdx + 2, offsetIdx + 4).toInt()
                    val offsetMs = (offsetHours * 60 + offsetMinutes) * 60 * 1000L * sign
                    timeMs -= offsetMs
                }
            }
            timeMs
        } catch (e: Exception) {
            0L
        }
    }

    private data class RawEpgItem(
        val title: String,
        val description: String?,
        val startMs: Long,
        val stopMs: Long
    )
}

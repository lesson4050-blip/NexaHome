package com.orbit.tv.launcher.features.iptv.epg

import android.util.Xml
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

/**
 * High-performance, streaming XMLTV (EPG) parser.
 * Uses XmlPullParser for O(1) memory footprint suitable for 1 GB RAM Android TV hardware.
 */
class XmltvParser {

    private val xmltvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val timeDisplayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun parseStream(inputStream: InputStream, targetChannelIds: Set<String>): Map<String, EpgChannelSchedule> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        val nowMs = System.currentTimeMillis()
        val channelPrograms = mutableMapOf<String, MutableList<EpgProgramItem>>()

        var eventType = parser.eventType
        var currentChannel: String? = null
        var startMs = 0L
        var stopMs = 0L
        var title = ""
        var inTitle = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            currentChannel = parser.getAttributeValue(null, "channel")
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            startMs = parseXmltvTime(startStr)
                            stopMs = parseXmltvTime(stopStr)
                            title = ""
                        }
                        "title" -> {
                            inTitle = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) {
                        title += parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> {
                            inTitle = false
                        }
                        "programme" -> {
                            if (currentChannel != null && targetChannelIds.contains(currentChannel) && title.isNotBlank()) {
                                val list = channelPrograms.getOrPut(currentChannel) { mutableListOf() }
                                list.add(EpgProgramItem(title.trim(), startMs, stopMs))
                            }
                            currentChannel = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // Align schedules to today's date if timestamps are static
        val resultMap = mutableMapOf<String, EpgChannelSchedule>()

        for ((chanId, rawProgs) in channelPrograms) {
            if (rawProgs.isEmpty()) continue

            // Normalize programs to current day hours
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
        }

        return resultMap
    }

    private fun normalizeToToday(progs: List<EpgProgramItem>): MutableList<EpgProgramItem> {
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

            EpgProgramItem(item.title, newStart, newStop)
        }.toMutableList()
    }

    private fun parseXmltvTime(timeStr: String?): Long {
        if (timeStr == null || timeStr.length < 14) return 0L
        return try {
            xmltvDateFormat.parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private data class EpgProgramItem(
        val title: String,
        val startMs: Long,
        val stopMs: Long
    )
}

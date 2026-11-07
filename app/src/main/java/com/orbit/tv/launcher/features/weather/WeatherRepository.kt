package com.orbit.tv.launcher.features.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class WeatherInfo(
    val temperature: String,
    val weatherCode: Int,
    val city: String
)

class WeatherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    suspend fun fetchWeather(): WeatherInfo = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve location by IP (fallback: Kyiv 50.45, 30.52)
            var lat = prefs.getString("cached_lat", "50.45") ?: "50.45"
            var lon = prefs.getString("cached_lon", "30.52") ?: "30.52"
            var city = prefs.getString("cached_city", "Kyiv") ?: "Kyiv"

            try {
                val ipUrl = URL("http://ip-api.com/json/?fields=lat,lon,city,status")
                val conn = ipUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val json = JSONObject(resp)
                    if (json.optString("status") == "success") {
                        lat = json.optDouble("lat").toString()
                        lon = json.optDouble("lon").toString()
                        city = json.optString("city", city)
                        prefs.edit().putString("cached_lat", lat).putString("cached_lon", lon).putString("cached_city", city).apply()
                    }
                }
            } catch (e: Exception) {}

            // 2. Fetch current weather from Open-Meteo (100% free, no API key required)
            val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
            val conn = weatherUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(resp)
                val current = json.getJSONObject("current")
                val tempVal = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")

                val sign = if (tempVal > 0) "+" else ""
                val tempStr = "$sign${Math.round(tempVal)}°C"

                prefs.edit().putString("cached_temp", tempStr).putInt("cached_code", code).apply()
                return@withContext WeatherInfo(tempStr, code, city)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Offline / Fallback cache
        val cachedTemp = prefs.getString("cached_temp", "+18°C") ?: "+18°C"
        val cachedCode = prefs.getInt("cached_code", 1)
        val cachedCity = prefs.getString("cached_city", "Local") ?: "Local"
        WeatherInfo(cachedTemp, cachedCode, cachedCity)
    }
}

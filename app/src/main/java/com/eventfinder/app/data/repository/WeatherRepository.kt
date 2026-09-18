package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.OpenMeteoApi
import com.eventfinder.app.data.remote.dto.OmForecastResponse
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils
import java.io.IOException
import retrofit2.HttpException

/**
 * A forecast summarised for the event-detail weather card.
 *
 * The prototype reads the first hourly sample for the event day. Only
 * temperature and weather code are carried across; the UI resolves the
 * description from string resources using [WeatherCodeMapper].
 */
data class WeatherSummary(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val unit: String,
    val hourIso: String
)

/**
 * Describes WMO weather codes for UI translation. Codes follow the World
 * Meteorological Organisation standard used by Open-Meteo.
 *  - https://open-meteo.com/en/docs#weathervariables
 */
fun describeWeatherCode(code: Int): String = when (code) {
    0 -> "Clear"
    in 1..3 -> "Partly cloudy"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    95 -> "Thunderstorm"
    in 96..99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

/** Weather boundary backed by the keyless Open-Meteo REST API. */
interface WeatherRepository {
    suspend fun forecastFor(eventId: String, latitude: Double, longitude: Double, startDate: Long): Result<WeatherSummary>
}

class WeatherRepositoryImpl(
    private val openMeteoApi: OpenMeteoApi
) : WeatherRepository {

    private val tag = "WeatherRepository"

    override suspend fun forecastFor(
        eventId: String,
        latitude: Double,
        longitude: Double,
        startDate: Long
    ): Result<WeatherSummary> = try {
        val iso = DateTimeUtils.isoDate(startDate)
        val response: OmForecastResponse = openMeteoApi.getForecast(
            latitude = latitude,
            longitude = longitude,
            startDate = iso,
            endDate = iso
        )
        val time = response.hourly?.time.orEmpty()
        val temps = response.hourly?.temperature2m.orEmpty()
        val codes = response.hourly?.weatherCode.orEmpty()
        val index = time.indexOfFirst { it.startsWith(iso) }
        if (index >= 0 && index < temps.size) {
            val summary = WeatherSummary(
                temperatureCelsius = temps[index],
                weatherCode = codes.getOrElse(index) { 0 },
                unit = response.hourlyUnits?.temperatureUnit ?: "°C",
                hourIso = time[index]
            )
            AppLogger.d(tag, "Forecast for event $eventId: ${summary.temperatureCelsius}${summary.unit}")
            Result.success(summary)
        } else {
            AppLogger.w(tag, "No hourly sample on event day for $eventId")
            Result.failure(IllegalStateException("no_hourly_data"))
        }
    } catch (t: HttpException) {
        if (t.code() == 400) {
            AppLogger.w(tag, "No forecast for $eventId (outside Open-Meteo's forecast window)")
        } else {
            AppLogger.e(tag, "Weather lookup failed", t)
        }
        Result.failure(t)
    } catch (t: IOException) {
        AppLogger.e(tag, "Weather lookup failed (offline)", t)
        Result.failure(t)
    } catch (t: Exception) {
        AppLogger.e(tag, "Weather lookup failed", t)
        Result.failure(t)
    }
}
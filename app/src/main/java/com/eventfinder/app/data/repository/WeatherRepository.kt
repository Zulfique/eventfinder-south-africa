package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.OpenMeteoApi
import com.eventfinder.app.data.remote.dto.OmForecastResponse
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import retrofit2.HttpException

data class WeatherSummary(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val unit: String,
    val hourIso: String
)

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

interface WeatherRepository {
    suspend fun forecastFor(
        eventId: String,
        latitude: Double,
        longitude: Double,
        startDate: Long
    ): Result<WeatherSummary>
}

class WeatherRepositoryImpl(
    private val openMeteoApi: OpenMeteoApi
) : WeatherRepository {

    private val tag = "WeatherRepository"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    override suspend fun forecastFor(
        eventId: String,
        latitude: Double,
        longitude: Double,
        startDate: Long
    ): Result<WeatherSummary> {
        return try {
            val isoDate = DateTimeUtils.isoDate(startDate)

            val response: OmForecastResponse = openMeteoApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                startDate = isoDate,
                endDate = isoDate
            )

            val hourly = response.hourly
                ?: return Result.failure(IllegalStateException("no_hourly_data"))

            val times = hourly.time.orEmpty()
            val temperatures = hourly.temperature2m.orEmpty()
            val codes = hourly.weatherCode.orEmpty()

            if (times.isEmpty() || temperatures.isEmpty()) {
                AppLogger.w(tag, "No hourly weather data for event $eventId")
                return Result.failure(IllegalStateException("no_hourly_data"))
            }

            val timezone = runCatching {
                ZoneId.of(response.timezone ?: ZoneId.systemDefault().id)
            }.getOrElse { ZoneId.systemDefault() }

            val targetInstant = Instant.ofEpochMilli(startDate)

            var bestIndex = -1
            var bestDistance = Long.MAX_VALUE

            for (index in times.indices) {
                if (index >= temperatures.size) break

                val forecastInstant = runCatching {
                    LocalDateTime.parse(times[index], formatter).atZone(timezone).toInstant()
                }.getOrNull() ?: continue

                val distance = kotlin.math.abs(forecastInstant.toEpochMilli() - targetInstant.toEpochMilli())

                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }

            if (bestIndex < 0) {
                return Result.failure(IllegalStateException("no_matching_hour"))
            }

            val maxAllowedDistance = 12L * 60L * 60L * 1000L
            if (bestDistance > maxAllowedDistance) {
                AppLogger.w(
                    tag,
                    "Nearest forecast is too far from event time: ${bestDistance / 3_600_000}h"
                )
                return Result.failure(IllegalStateException("forecast_too_far"))
            }

            val summary = WeatherSummary(
                temperatureCelsius = temperatures[bestIndex],
                weatherCode = codes.getOrElse(bestIndex) { 0 },
                unit = response.hourlyUnits?.temperatureUnit ?: "°C",
                hourIso = times[bestIndex]
            )

            AppLogger.d(
                tag,
                "Forecast for event $eventId: ${summary.temperatureCelsius}${summary.unit} " +
                    "at ${summary.hourIso} ($timezone)"
            )

            Result.success(summary)
        } catch (t: HttpException) {
            AppLogger.e(tag, "Open-Meteo HTTP ${t.code()} for $eventId", t)
            Result.failure(t)
        } catch (t: IOException) {
            AppLogger.e(tag, "Weather lookup failed - offline", t)
            Result.failure(t)
        } catch (t: Exception) {
            AppLogger.e(tag, "Weather lookup failed", t)
            Result.failure(t)
        }
    }
}

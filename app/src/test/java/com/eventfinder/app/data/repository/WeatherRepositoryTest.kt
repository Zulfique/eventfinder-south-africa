package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.OpenMeteoApi
import com.eventfinder.app.data.remote.dto.OmForecastResponse
import com.eventfinder.app.data.remote.dto.OmHourly
import com.eventfinder.app.data.remote.dto.OmHourlyUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests the keyless Open-Meteo weather integration (external REST API #2):
 * both the WMO code descriptions and the repository's handling of usable and
 * unusable payloads.
 */
class WeatherRepositoryTest {

    // ------------------------------------------------------- describeWeatherCode

    @Test
    fun `describes representative WMO weather codes`() {
        assertEquals("Clear", describeWeatherCode(0))
        assertEquals("Partly cloudy", describeWeatherCode(2))
        assertEquals("Foggy", describeWeatherCode(45))
        assertEquals("Drizzle", describeWeatherCode(53))
        assertEquals("Rain", describeWeatherCode(65))
        assertEquals("Snow", describeWeatherCode(73))
        assertEquals("Showers", describeWeatherCode(81))
        assertEquals("Thunderstorm", describeWeatherCode(95))
        assertEquals("Thunderstorm with hail", describeWeatherCode(99))
    }

    @Test
    fun `unknown weather codes degrade gracefully`() {
        assertEquals("Unknown", describeWeatherCode(-1))
        assertEquals("Unknown", describeWeatherCode(1234))
    }

    // -------------------------------------------------- WeatherRepositoryImpl

    private val isoDate = "2026-10-03"
    private val zone = ZoneId.systemDefault()
    private val startDate: Long = LocalDate.of(2026, 10, 3)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    private class FakeOpenMeteoApi(private val response: OmForecastResponse) : OpenMeteoApi {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            daily: String,
            forecastDays: Int,
            temperatureUnit: String,
            windSpeedUnit: String,
            timezone: String,
            startDate: String?,
            endDate: String?
        ): OmForecastResponse = response
    }

    @Test
    fun `returns the hourly sample closest to the event start hour`() = runTest {
        val api = FakeOpenMeteoApi(
            OmForecastResponse(
                timezone = zone.id,
                hourly = OmHourly(
                    time = listOf("2026-10-03T00:00", "2026-10-03T09:00", "2026-10-03T12:00"),
                    temperature2m = listOf(12.0, 18.5, 22.0),
                    weatherCode = listOf(0, 1, 2)
                ),
                hourlyUnits = OmHourlyUnits(temperatureUnit = "°C")
            )
        )

        val result = WeatherRepositoryImpl(api).forecastFor("event-1", -26.2, 28.0, startDate)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(12.0, summary.temperatureCelsius, 0.0001)
        assertEquals(0, summary.weatherCode)
        assertEquals("°C", summary.unit)
        assertEquals("2026-10-03T00:00", summary.hourIso)
    }

    @Test
    fun `selects closest hour when event starts in the afternoon`() = runTest {
        val afternoonStart = LocalDate.of(2026, 10, 3)
            .atTime(14, 0).atZone(zone).toInstant().toEpochMilli()

        val api = FakeOpenMeteoApi(
            OmForecastResponse(
                timezone = zone.id,
                hourly = OmHourly(
                    time = listOf("2026-10-03T06:00", "2026-10-03T12:00", "2026-10-03T18:00"),
                    temperature2m = listOf(12.0, 22.0, 25.0),
                    weatherCode = listOf(0, 1, 3)
                ),
                hourlyUnits = OmHourlyUnits(temperatureUnit = "°C")
            )
        )

        val result = WeatherRepositoryImpl(api).forecastFor("event-1", -26.2, 28.0, afternoonStart)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(22.0, summary.temperatureCelsius, 0.0001)
        assertEquals("2026-10-03T12:00", summary.hourIso)
    }

    @Test
    fun `defaults the unit when the API omits it`() = runTest {
        val api = FakeOpenMeteoApi(
            OmForecastResponse(
                timezone = zone.id,
                hourly = OmHourly(
                    time = listOf("2026-10-03T10:00"),
                    temperature2m = listOf(20.0),
                    weatherCode = listOf(3)
                )
            )
        )
        val summary = WeatherRepositoryImpl(api).forecastFor("event-1", -33.9, 18.4, startDate).getOrThrow()
        assertEquals("°C", summary.unit)
    }

    @Test
    fun `rejects forecast when nearest hour is too far from event time`() = runTest {
        val api = FakeOpenMeteoApi(
            OmForecastResponse(
                timezone = zone.id,
                hourly = OmHourly(
                    time = listOf("2026-10-04T10:00"),
                    temperature2m = listOf(20.0),
                    weatherCode = listOf(3)
                )
            )
        )
        val result = WeatherRepositoryImpl(api).forecastFor("event-1", -33.9, 18.4, startDate)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when the payload has no hourly data at all`() = runTest {
        val api = FakeOpenMeteoApi(
            OmForecastResponse(
                timezone = zone.id,
                hourly = OmHourly(
                    time = emptyList(),
                    temperature2m = emptyList(),
                    weatherCode = emptyList()
                )
            )
        )
        val result = WeatherRepositoryImpl(api).forecastFor("event-1", -33.9, 18.4, startDate)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when the payload has no hourly block`() = runTest {
        val api = FakeOpenMeteoApi(OmForecastResponse(hourly = null))
        val result = WeatherRepositoryImpl(api).forecastFor("event-1", -33.9, 18.4, startDate)
        assertTrue(result.isFailure)
    }
}

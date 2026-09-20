package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.OmForecastResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo weather API.
 *
 * Free non-commercial usage does not require an API key or account.
 *
 * https://open-meteo.com/en/docs
 */
interface OpenMeteoApi {

    /**
     * Retrieves hourly weather for the requested event date.
     *
     * timezone=auto is important because Open-Meteo returns the
     * hourly timestamps in the timezone of the supplied coordinates.
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("hourly")
        hourly: String = "temperature_2m,weather_code",

        @Query("start_date")
        startDate: String,

        @Query("end_date")
        endDate: String,

        @Query("temperature_unit")
        temperatureUnit: String = "celsius",

        @Query("timezone")
        timezone: String = "auto"
    ): OmForecastResponse
}

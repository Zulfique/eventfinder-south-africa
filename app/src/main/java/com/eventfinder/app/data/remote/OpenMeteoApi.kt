package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.OmAirQualityResponse
import com.eventfinder.app.data.remote.dto.OmElevationResponse
import com.eventfinder.app.data.remote.dto.OmForecastResponse
import com.eventfinder.app.data.remote.dto.OmGeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo APIs.
 *
 * No API key or token is sent.
 *
 * Free non-commercial usage is supported by Open-Meteo.
 */
interface OpenMeteoApi {

    /**
     * Weather forecast.
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("hourly")
        hourly: String =
            "temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation_probability,precipitation,weather_code," +
                "wind_speed_10m,wind_direction_10m,uv_index",

        @Query("daily")
        daily: String =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_sum,precipitation_probability_max," +
                "sunrise,sunset,uv_index_max",

        @Query("forecast_days")
        forecastDays: Int = 7,

        @Query("temperature_unit")
        temperatureUnit: String = "celsius",

        @Query("wind_speed_unit")
        windSpeedUnit: String = "kmh",

        @Query("timezone")
        timezone: String = "auto",

        @Query("start_date")
        startDate: String? = null,

        @Query("end_date")
        endDate: String? = null
    ): OmForecastResponse

    /**
     * Open-Meteo geocoding.
     */
    @GET("v1/search")
    suspend fun geocode(
        @Query("name")
        name: String,

        @Query("count")
        count: Int = 10,

        @Query("language")
        language: String = "en",

        @Query("format")
        format: String = "json"
    ): OmGeocodingResponse

    /**
     * Elevation for one or more coordinate pairs.
     */
    @GET("v1/elevation")
    suspend fun elevation(
        @Query("latitude")
        latitude: String,

        @Query("longitude")
        longitude: String
    ): OmElevationResponse
}


/**
 * Open-Meteo Air Quality API.
 *
 * This uses a separate Retrofit base URL.
 */
interface OpenMeteoAirQualityApi {

    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("current")
        current: String =
            "european_aqi,pm10,pm2_5,carbon_monoxide," +
                "nitrogen_dioxide,sulphur_dioxide,ozone",

        @Query("hourly")
        hourly: String =
            "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide," +
                "sulphur_dioxide,ozone,european_aqi",

        @Query("forecast_days")
        forecastDays: Int = 3,

        @Query("timezone")
        timezone: String = "auto"
    ): OmAirQualityResponse
}

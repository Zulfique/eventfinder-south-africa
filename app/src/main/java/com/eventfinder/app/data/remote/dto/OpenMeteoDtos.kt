package com.eventfinder.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Gson DTOs for the Open-Meteo free weather API (keyless, CC-BY 4.0).
 *
 * Docs: https://open-meteo.com/en/docs
 */
data class OmForecastResponse(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("hourly") val hourly: OmHourly? = null,
    @SerializedName("hourly_units") val hourlyUnits: OmHourlyUnits? = null
)

data class OmHourly(
    @SerializedName("time") val time: List<String>? = null,
    @SerializedName("temperature_2m") val temperature2m: List<Double>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null
)

data class OmHourlyUnits(
    @SerializedName("temperature_2m") val temperatureUnit: String? = null
)
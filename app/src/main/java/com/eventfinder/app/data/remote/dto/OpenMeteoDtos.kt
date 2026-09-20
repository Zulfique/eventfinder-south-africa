package com.eventfinder.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OmForecastResponse(
    @SerializedName("latitude")
    val latitude: Double? = null,

    @SerializedName("longitude")
    val longitude: Double? = null,

    @SerializedName("timezone")
    val timezone: String? = null,

    @SerializedName("timezone_abbreviation")
    val timezoneAbbreviation: String? = null,

    @SerializedName("elevation")
    val elevation: Double? = null,

    @SerializedName("hourly")
    val hourly: OmHourly? = null,

    @SerializedName("hourly_units")
    val hourlyUnits: OmHourlyUnits? = null,

    @SerializedName("daily")
    val daily: OmDaily? = null,

    @SerializedName("daily_units")
    val dailyUnits: OmDailyUnits? = null
)

data class OmHourly(
    @SerializedName("time")
    val time: List<String>? = null,

    @SerializedName("temperature_2m")
    val temperature2m: List<Double>? = null,

    @SerializedName("relative_humidity_2m")
    val relativeHumidity2m: List<Double>? = null,

    @SerializedName("apparent_temperature")
    val apparentTemperature: List<Double>? = null,

    @SerializedName("precipitation_probability")
    val precipitationProbability: List<Int>? = null,

    @SerializedName("precipitation")
    val precipitation: List<Double>? = null,

    @SerializedName("weather_code")
    val weatherCode: List<Int>? = null,

    @SerializedName("wind_speed_10m")
    val windSpeed10m: List<Double>? = null,

    @SerializedName("wind_direction_10m")
    val windDirection10m: List<Double>? = null,

    @SerializedName("uv_index")
    val uvIndex: List<Double>? = null
)

data class OmHourlyUnits(
    @SerializedName("temperature_2m")
    val temperatureUnit: String? = null,

    @SerializedName("wind_speed_10m")
    val windSpeedUnit: String? = null,

    @SerializedName("precipitation")
    val precipitationUnit: String? = null
)

data class OmDaily(
    @SerializedName("time")
    val time: List<String>? = null,

    @SerializedName("weather_code")
    val weatherCode: List<Int>? = null,

    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>? = null,

    @SerializedName("temperature_2m_min")
    val temperatureMin: List<Double>? = null,

    @SerializedName("precipitation_sum")
    val precipitationSum: List<Double>? = null,

    @SerializedName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>? = null,

    @SerializedName("sunrise")
    val sunrise: List<String>? = null,

    @SerializedName("sunset")
    val sunset: List<String>? = null,

    @SerializedName("uv_index_max")
    val uvIndexMax: List<Double>? = null
)

data class OmDailyUnits(
    @SerializedName("temperature_2m_max")
    val temperatureMaxUnit: String? = null,

    @SerializedName("temperature_2m_min")
    val temperatureMinUnit: String? = null,

    @SerializedName("precipitation_sum")
    val precipitationUnit: String? = null
)


/**
 * Open-Meteo geocoding response.
 */
data class OmGeocodingResponse(
    @SerializedName("results")
    val results: List<OmGeocodingResult>? = null
)

data class OmGeocodingResult(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("latitude")
    val latitude: Double? = null,

    @SerializedName("longitude")
    val longitude: Double? = null,

    @SerializedName("elevation")
    val elevation: Double? = null,

    @SerializedName("timezone")
    val timezone: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("country_code")
    val countryCode: String? = null,

    @SerializedName("admin1")
    val admin1: String? = null,

    @SerializedName("admin2")
    val admin2: String? = null,

    @SerializedName("population")
    val population: Long? = null
)


/**
 * Open-Meteo elevation response.
 */
data class OmElevationResponse(
    @SerializedName("elevation")
    val elevation: List<Double>? = null
)


/**
 * Open-Meteo Air Quality.
 */
data class OmAirQualityResponse(
    @SerializedName("latitude")
    val latitude: Double? = null,

    @SerializedName("longitude")
    val longitude: Double? = null,

    @SerializedName("timezone")
    val timezone: String? = null,

    @SerializedName("current")
    val current: OmCurrentAirQuality? = null,

    @SerializedName("current_units")
    val currentUnits: Map<String, String>? = null,

    @SerializedName("hourly")
    val hourly: OmAirQualityHourly? = null,

    @SerializedName("hourly_units")
    val hourlyUnits: Map<String, String>? = null
)

data class OmCurrentAirQuality(
    @SerializedName("time")
    val time: String? = null,

    @SerializedName("european_aqi")
    val europeanAqi: Double? = null,

    @SerializedName("pm10")
    val pm10: Double? = null,

    @SerializedName("pm2_5")
    val pm25: Double? = null,

    @SerializedName("carbon_monoxide")
    val carbonMonoxide: Double? = null,

    @SerializedName("nitrogen_dioxide")
    val nitrogenDioxide: Double? = null,

    @SerializedName("sulphur_dioxide")
    val sulphurDioxide: Double? = null,

    @SerializedName("ozone")
    val ozone: Double? = null
)

data class OmAirQualityHourly(
    @SerializedName("time")
    val time: List<String>? = null,

    @SerializedName("european_aqi")
    val europeanAqi: List<Double>? = null,

    @SerializedName("pm10")
    val pm10: List<Double>? = null,

    @SerializedName("pm2_5")
    val pm25: List<Double>? = null,

    @SerializedName("carbon_monoxide")
    val carbonMonoxide: List<Double>? = null,

    @SerializedName("nitrogen_dioxide")
    val nitrogenDioxide: List<Double>? = null,

    @SerializedName("sulphur_dioxide")
    val sulphurDioxide: List<Double>? = null,

    @SerializedName("ozone")
    val ozone: List<Double>? = null
)

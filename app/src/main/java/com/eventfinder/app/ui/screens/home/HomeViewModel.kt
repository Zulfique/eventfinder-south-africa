package com.eventfinder.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.EventFilterer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.domain.model.EventAlertDetector.Alerts
import com.eventfinder.app.domain.model.EventAlertDetector.detect
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DistanceCalculator
import com.eventfinder.app.utils.LocationUtils
import com.eventfinder.app.utils.SouthAfricanPlaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@Suppress("unused")
@OptIn(ExperimentalCoroutinesApi::class)
data class HomeUiState(
    val events: List<EventView> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val lastRefreshAt: Long = 0L,
    val selectedCategory: EventCategory? = null,
    val query: String = "",
    val sort: EventSort = EventSort.DATE,
    val radiusKm: Int = 50,
    val radiusFilterEnabled: Boolean = false,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val isMapView: Boolean = false,
    val showFilterSheet: Boolean = false,
    /** Header location pill: "<City>, ZA" — defaults to the national centre (Cape Town). */
    val cityLabel: String = "${SouthAfricanPlaces.nationalDefault.name}, ZA",
    /** Smart Discovery "Trending This Weekend" card is active. */
    val weekendOnly: Boolean = false
)

/**
 * Home screen logic (Screens 4 & 5): live event catalogue, category + keyword +
 * radius filtering (FR-02), proximity sorting and the map/list toggle.
 */
class HomeViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val eventRepository = container.eventRepository
    private val eventDiscoveryRepository = container.eventDiscoveryRepository
    private val preferences = container.preferences
    private val networkMonitor = container.networkMonitor

    private val _uiState = MutableStateFlow(HomeUiState())
    private val _messages = MutableSharedFlow<UiMessage>()

    val uiState: StateFlow<HomeUiState> = _uiState
    val messages: kotlinx.coroutines.flow.SharedFlow<UiMessage> = _messages

    private val _previousEvents = MutableStateFlow<List<EventView>>(emptyList())
    val previousEvents: StateFlow<List<EventView>> = _previousEvents.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val categoryFlow = MutableStateFlow<EventCategory?>(null)
    private val sortFlow = MutableStateFlow(EventSort.DATE)
    private val radiusFlow = MutableStateFlow(50)
    private val radiusEnabledFlow = MutableStateFlow(false)
    private val locationFlow = MutableStateFlow<Pair<Double, Double>?>(null)
    private val weekendFlow = MutableStateFlow(false)

    /** True while an explicit city from the header selector overrides the GPS fix. */
    private var manualCityActive = false

    private data class Combined(
        val events: List<Event>,
        val rsvps: Map<String, RsvpStatus>,
        val favoriteIds: Set<String>
    )

    private data class Controls(
        val query: String,
        val category: EventCategory?,
        val sort: EventSort,
        val radiusKm: Int,
        val radiusEnabled: Boolean,
        val location: Pair<Double, Double>?,
        val weekendOnly: Boolean
    )

    init {
        viewModelScope.launch {
            preferences.defaultRadiusKm.first().let { radiusFlow.value = it }
        }

        // Restore the header location selector's saved city.
        viewModelScope.launch {
            preferences.selectedCity.first()?.let { city ->
                manualCityActive = true
                _uiState.update { it.copy(cityLabel = "$city, ZA") }
            }
        }

        // Seed demo events, then attempt to discover public JSON feeds when online.
        viewModelScope.launch {
            val online = networkMonitor.isCurrentlyOnline()
            _uiState.update { it.copy(isOffline = !online) }

            eventRepository.ensureSeeded()

            if (online) {
                runCatching { eventDiscoveryRepository.refresh() }.onFailure {
                    AppLogger.e("HomeViewModel", "Event discovery failed", it)
                }
            }
        }

        val combinedFlow = combine(
            eventRepository.observeAllEvents(),
            eventRepository.observeRsvpStatuses(),
            eventRepository.observeFavoriteIds()
        ) { events, rsvps, favoriteIds ->
            Combined(events, rsvps, favoriteIds)
        }

        val textControlsFlow = combine(queryFlow, categoryFlow, sortFlow) { query, category, sort ->
            Triple(query, category, sort)
        }
        val rangeControlsFlow = combine(radiusFlow, radiusEnabledFlow, locationFlow) { radius, enabled, location ->
            Triple(radius, enabled, location)
        }
        val controlsFlow = combine(textControlsFlow, rangeControlsFlow, weekendFlow) { text, range, weekend ->
            Controls(text.first, text.second, text.third, range.first, range.second, range.third, weekend)
        }

        viewModelScope.launch {
            combinedFlow.combine(controlsFlow) { combined, controls -> applyControls(combined, controls) }
                .collect { views ->
                    _uiState.value = _uiState.value.copy(events = views, isLoading = false, lastRefreshAt = System.currentTimeMillis())
                }
        }

        // Wire up new event / favourite updated alerts (FR-04)
        viewModelScope.launch {
            combine(
                eventRepository.observeAllEvents(),
                eventRepository.observeFavoriteIds()
            ) { events, favoriteIds ->
                events to favoriteIds
            }.collect { (events, favoriteIds) ->

                val previous = _previousEvents.value
                    .map { it.event }
                    .associateBy { it.id }


                val alerts = detect(
                    previous = previous,
                    current = events,
                    favoriteIds = favoriteIds,
                    now = System.currentTimeMillis()
                )


                if (!alerts.isEmpty) {
                    NotificationHelper.postEventAlerts(
                        context = appContext,
                        newEvents = alerts.newEvents,
                        updatedFavorites = alerts.updatedFavorites
                    )
                }


                _previousEvents.value = events.map { EventView(it) }
            }
        }
    }

    private fun applyControls(combined: Combined, c: Controls): List<EventView> {
        val userLat = c.location?.first
        val userLng = c.location?.second

        val now = System.currentTimeMillis()

        val upcomingEvents =
            combined.events.filter { event ->
                event.endDate > now
            }

        // Smart Discovery: "Trending This Weekend" narrows to the upcoming
        // Saturday-Sunday window (inclusive of today when it is a weekend day).
        val inScope = if (c.weekendOnly) {
            val weekend = upcomingWeekendWindow(now)
            upcomingEvents.filter { it.startDate in weekend }
        } else upcomingEvents

        val filtered = EventFilterer.filter(
            events = inScope,
            query = c.query.ifBlank { null },
            category = c.category,
            userLat = userLat,
            userLng = userLng,
            radiusKm = if (c.radiusEnabled) c.radiusKm else 0
        )
        val sorted = EventFilterer.sort(filtered, c.sort, userLat, userLng)
        return EventFilterer.attachDistances(sorted, userLat, userLng, combined.favoriteIds)
            .map { it.copy(rsvpStatus = combined.rsvps[it.event.id]) }
    }

    fun onQueryChange(value: String) {
        queryFlow.value = value
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun onCategorySelect(category: EventCategory?) {
        categoryFlow.value = category
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun onSortSelect(sort: EventSort) {
        sortFlow.value = sort
        _uiState.value = _uiState.value.copy(sort = sort)
    }

    fun onRadiusSliderChange(km: Int) {
        val value = km.coerceIn(5, 200)
        radiusFlow.value = value
        _uiState.value = _uiState.value.copy(radiusKm = value)
    }

    fun onToggleRadiusFilter(enabled: Boolean) {
        radiusEnabledFlow.value = enabled
        _uiState.value = _uiState.value.copy(radiusFilterEnabled = enabled)
        if (enabled) {
            // Remember the radius the user settled on so the default is
            // restored next session (previously the value silently reset).
            viewModelScope.launch { preferences.setDefaultRadiusKm(radiusFlow.value) }
        }
    }

    fun onToggleMapView() {
        _uiState.value = _uiState.value.copy(isMapView = !_uiState.value.isMapView)
        AppLogger.d("HomeViewModel", "Map view toggled -> ${_uiState.value.isMapView}")
    }

    /**
     * Smart Discovery "Near Me Now": nearest events first with the radius
     * filter on, using the best available fix (saved city or device GPS).
     */
    fun onNearMeNow() {
        manualLocation()
        onSortSelect(EventSort.DISTANCE)
        onToggleRadiusFilter(true)
        AppLogger.i("HomeViewModel", "Smart Discovery -> Near Me Now")
    }

    /** Smart Discovery "Trending This Weekend": toggle the weekend window. */
    fun onTrendingWeekend() {
        val enabled = !weekendFlow.value
        weekendFlow.value = enabled
        _uiState.update { it.copy(weekendOnly = enabled) }
        if (enabled) onSortSelect(EventSort.TRENDING)
        AppLogger.i("HomeViewModel", "Smart Discovery -> Trending This Weekend: $enabled")
    }

    /** The upcoming Saturday-Sunday window (this weekend when today is Sat/Sun). */
    internal fun upcomingWeekendWindow(now: Long): LongRange {
        val tz = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(tz).toLocalDate()
        val saturdayOffset: Long = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> 0L
            DayOfWeek.SUNDAY -> -1L // still this weekend
            else -> (DayOfWeek.SATURDAY.value - today.dayOfWeek.value).toLong()
        }
        val saturday = today.plusDays(saturdayOffset)
        val sunday = saturday.plusDays(1)
        val start = saturday.atStartOfDay(tz).toInstant().toEpochMilli()
        val end = sunday.atTime(23, 59, 59).atZone(tz).toInstant().toEpochMilli()
        return start..end
    }

    /** Nearest known SA city for a fix — keeps the header pill in "<City>, ZA" form. */
    private fun nearestCityLabel(lat: Double, lng: Double): String {
        val nearest = SouthAfricanPlaces.all.minByOrNull {
            DistanceCalculator.between(lat, lng, it.latitude, it.longitude)
        } ?: return "South Africa"
        return "${nearest.name}, ZA"
    }

    fun onShowFilterSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFilterSheet = show)
    }

    fun setUserLocation(lat: Double, lng: Double) {
        if (manualCityActive) return
        locationFlow.value = lat to lng
        _uiState.value = _uiState.value.copy(
            userLat = lat,
            userLng = lng,
            cityLabel = nearestCityLabel(lat, lng)
        )
        AppLogger.i("HomeViewModel", "User location set ($lat, $lng)")
    }

    fun manualLocation() {
        LocationUtils.lastKnown(appContext)?.let { (lat, lng) -> setUserLocation(lat, lng) }
    }

    /** Header location selector: show events around [cityName] instead of the device fix. */
    fun selectCity(cityName: String) {
        viewModelScope.launch {
            preferences.setSelectedCity(cityName)
            manualCityActive = true
            _uiState.update { it.copy(cityLabel = "$cityName, ZA") }

            val place = runCatching { container.placeResolver.resolveCity(cityName) }.getOrNull()
            if (place != null) {
                locationFlow.value = place.latitude to place.longitude
                _uiState.update {
                    it.copy(userLat = place.latitude, userLng = place.longitude)
                }
                AppLogger.i("HomeViewModel", "Location selector -> $cityName (${place.latitude}, ${place.longitude})")
            } else {
                _messages.emit(UiMessage.Literal("Could not locate $cityName — check your connection"))
            }
        }
    }

    /** Header location selector: hand control back to the device's GPS fix. */
    fun useCurrentLocation() {
        viewModelScope.launch {
            preferences.setSelectedCity(null)
            manualCityActive = false
            val fix = LocationUtils.lastKnown(appContext)
            _uiState.update {
                it.copy(
                    cityLabel = fix?.let { (lat, lng) -> nearestCityLabel(lat, lng) }
                        ?: "${SouthAfricanPlaces.nationalDefault.name}, ZA"
                )
            }
            fix?.let { (lat, lng) ->
                locationFlow.value = lat to lng
                _uiState.update { state -> state.copy(userLat = lat, userLng = lng) }
            }
        }
    }

    fun toggleFavorite(eventId: String) {
        viewModelScope.launch {
            val added = eventRepository.toggleFavorite(eventId)
            _messages.emit(
                if (added) UiMessage.Resource(R.string.added_to_favorites)
                else UiMessage.Resource(R.string.removed_from_favorites)
            )
        }
    }

    fun setRsvp(eventId: String, status: RsvpStatus) {
        viewModelScope.launch {
            eventRepository.setRsvp(eventId, status)
            when (status) {
                RsvpStatus.ATTENDING -> {
                    val event = eventRepository.getEvent(eventId)
                    val remindersEnabled = preferences.remindersEnabled.first()
                    val userId = preferences.sessionUserId.first()
                    if (event != null && remindersEnabled && !userId.isNullOrBlank()) {
                        val scheduled = NotificationHelper.scheduleEventReminders(appContext, event, userId)
                        _messages.emit(
                            if (scheduled > 0) UiMessage.Resource(R.string.reminder_scheduled)
                            else UiMessage.Resource(R.string.rsvp_updated)
                        )
                    } else {
                        _messages.emit(UiMessage.Resource(R.string.rsvp_updated))
                    }
                }
                RsvpStatus.MAYBE, RsvpStatus.DECLINED -> {
                    NotificationHelper.cancelEventReminders(appContext, eventId)
                    _messages.emit(UiMessage.Resource(R.string.reminders_cancelled))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                _uiState.update { it.copy(isLoading = false, isOffline = true) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, isOffline = false) }

            runCatching { eventDiscoveryRepository.refresh() }.onFailure {
                AppLogger.e("HomeViewModel", "Event discovery failed on refresh", it)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshEvents() {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                AppLogger.i("HomeViewModel", "Skipping event refresh: device is offline")
                return@launch
            }

            runCatching { eventDiscoveryRepository.refresh() }
                .onSuccess {
                    AppLogger.i(
                        "HomeViewModel",
                        "Event discovery: fetched=${it.fetched}, inserted=${it.inserted}, failedSources=${it.failedSources}"
                    )
                }
                .onFailure {
                    AppLogger.e("HomeViewModel", "Event discovery failed", it)
                }
        }
    }

    companion object {
        fun factory(container: AppContainer, appContext: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(container, appContext) }
            }
    }
}

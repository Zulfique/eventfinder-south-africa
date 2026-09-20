package com.eventfinder.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.SyncResult
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.EventFilterer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.LocationUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("unused")
@OptIn(ExperimentalCoroutinesApi::class)
data class HomeUiState(
    val events: List<EventView> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val selectedCategory: EventCategory? = null,
    val query: String = "",
    val sort: EventSort = EventSort.DATE,
    val radiusKm: Int = 50,
    val radiusFilterEnabled: Boolean = false,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val isMapView: Boolean = false,
    val showFilterSheet: Boolean = false
)

/**
 * Home screen logic (Screens 4 & 5): live event catalogue, category + keyword +
 * radius filtering (FR-02), proximity sorting and the map/list toggle. All list
 * manipulation funnels through [EventFilterer] so the rules are unit-tested.
 */
class HomeViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val eventRepository = container.eventRepository
    private val preferences = container.preferences

    private val _uiState = MutableStateFlow(HomeUiState())
    private val _messages = MutableSharedFlow<UiMessage>()

    val uiState: StateFlow<HomeUiState> = _uiState
    val messages: kotlinx.coroutines.flow.SharedFlow<UiMessage> = _messages

    // Reactive filter inputs
    private val queryFlow = MutableStateFlow("")
    private val categoryFlow = MutableStateFlow<EventCategory?>(null)
    private val sortFlow = MutableStateFlow(EventSort.DATE)
    private val radiusFlow = MutableStateFlow(50)
    private val radiusEnabledFlow = MutableStateFlow(false)
    private val locationFlow = MutableStateFlow<Pair<Double, Double>?>(null)

    private data class Combined(val events: List<Event>, val rsvps: Map<String, RsvpStatus>)

    private data class Controls(
        val query: String,
        val category: EventCategory?,
        val sort: EventSort,
        val radiusKm: Int,
        val radiusEnabled: Boolean,
        val location: Pair<Double, Double>?
    )

    init {
        // Preferences (radius default), connectivity and content pipeline.
        viewModelScope.launch {
            preferences.defaultRadiusKm.first().let { radiusFlow.value = it }
        }
        viewModelScope.launch {
            container.networkMonitor.isOnline
                .collect { online ->
                    val wasOffline = _uiState.value.isOffline
                    _uiState.value = _uiState.value.copy(isOffline = !online)
                    if (online && wasOffline) {
                        AppLogger.i("HomeViewModel", "Internet available - refreshing local journal")
                        flushPendingActions()
                    }
                }
        }

        // Seed the offline cache on startup.
        viewModelScope.launch {
            eventRepository.ensureSeeded()
            flushPendingActions()
        }

        // Combine catalog + favourites + RSVPs, then apply current controls.
        val combinedFlow = combine(
            eventRepository.observeAllEvents(),
            eventRepository.observeRsvpStatuses()
        ) { events, rsvps ->
            Combined(events, rsvps)
        }

        // Kotlin's `combine` only has typed overloads up to five flows, so the
        // six control inputs are grouped into two triples that are combined again.
        val textControlsFlow = combine(queryFlow, categoryFlow, sortFlow) { query, category, sort ->
            Triple(query, category, sort)
        }
        val rangeControlsFlow = combine(radiusFlow, radiusEnabledFlow, locationFlow) { radius, enabled, location ->
            Triple(radius, enabled, location)
        }
        val controlsFlow = combine(textControlsFlow, rangeControlsFlow) { text, range ->
            Controls(text.first, text.second, text.third, range.first, range.second, range.third)
        }

        viewModelScope.launch {
            combinedFlow.combine(controlsFlow) { combined, controls -> applyControls(combined, controls) }
                .collect { views ->
                    _uiState.value = _uiState.value.copy(events = views, isLoading = false)
                }
        }
    }

    private fun applyControls(combined: Combined, c: Controls): List<EventView> {
        val userLat = c.location?.first
        val userLng = c.location?.second

        val filtered = EventFilterer.filter(
            events = combined.events,
            query = c.query.ifBlank { null },
            category = c.category,
            userLat = userLat,
            userLng = userLng,
            radiusKm = if (c.radiusEnabled) c.radiusKm else 0
        )
        val sorted = EventFilterer.sort(filtered, c.sort, userLat, userLng)
        return EventFilterer.attachDistances(sorted, userLat, userLng)
            .map { it.copy(rsvpStatus = combined.rsvps[it.event.id]) }
    }

    // ---- User actions ----

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
        radiusFlow.value = km
    }

    fun onToggleRadiusFilter(enabled: Boolean) {
        radiusEnabledFlow.value = enabled
    }

    fun onToggleMapView() {
        _uiState.value = _uiState.value.copy(isMapView = !_uiState.value.isMapView)
        AppLogger.d("HomeViewModel", "Map view toggled -> ${_uiState.value.isMapView}")
    }

    fun onShowFilterSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFilterSheet = show)
    }

    fun setUserLocation(lat: Double, lng: Double) {
        locationFlow.value = lat to lng
        _uiState.value = _uiState.value.copy(
            userLat = lat,
            userLng = lng,
            radiusFilterEnabled = true
        )
        radiusEnabledFlow.value = true
        AppLogger.i("HomeViewModel", "User location set ($lat, $lng)")
    }

    fun manualLocation() {
        LocationUtils.lastKnown(appContext)?.let { (lat, lng) -> setUserLocation(lat, lng) }
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
                RsvpStatus.DECLINED -> {
                    NotificationHelper.cancelEventReminders(appContext, eventId)
                    _messages.emit(UiMessage.Resource(R.string.reminders_cancelled))
                }
                else -> _messages.emit(UiMessage.Resource(R.string.rsvp_updated))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            flushPendingActions()
        }
    }

    private suspend fun flushPendingActions() {
        when (eventRepository.flushPendingActions()) {
            SyncResult.Synced ->
                AppLogger.i(
                    "HomeViewModel",
                    "Local pending journal reconciled"
                )
            SyncResult.NoSession ->
                AppLogger.d(
                    "HomeViewModel",
                    "No active session - local journal not processed"
                )
            SyncResult.Failed ->
                AppLogger.w(
                    "HomeViewModel",
                    "Some local journal entries remain"
                )
        }
    }

    companion object {
        fun factory(container: AppContainer, appContext: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(container, appContext) }
            }
    }
}

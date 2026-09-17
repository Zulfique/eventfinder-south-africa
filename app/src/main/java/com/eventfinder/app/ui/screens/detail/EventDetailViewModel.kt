package com.eventfinder.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.WeatherRepository
import com.eventfinder.app.data.repository.WeatherSummary
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the event detail screen (Screen 6). */
data class EventDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val eventView: EventView? = null,
    val weather: WeatherSummary? = null,
    val weatherUnavailable: Boolean = false
)

/**
 * Event detail (Screen 6): loads the event, streams live favourite + RSVP
 * state, and fetches the venue weather from the free Open-Meteo API.
 */
class EventDetailViewModel(
    private val container: AppContainer,
    private val appContext: android.content.Context,
    private val eventId: String
) : ViewModel() {

    private val eventRepository = container.eventRepository

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val event = eventRepository.getEvent(eventId)
            if (event == null) {
                _uiState.value = EventDetailUiState(loading = false, notFound = true)
                return@launch
            }

            // Stream favourite + RSVP updates reactively.
            kotlinx.coroutines.flow.combine(
                eventRepository.observeFavoriteIds(),
                eventRepository.observeRsvpStatuses()
            ) { favoriteIds, rsvps ->
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        eventView = current.eventView?.let {
                            it.copy(
                                event = it.event.copy(isFavorite = favoriteIds.contains(it.event.id)),
                                rsvpStatus = rsvps[it.event.id]
                            )
                        } ?: com.eventfinder.app.domain.model.EventView(
                            event = event.copy(isFavorite = favoriteIds.contains(event.id)),
                            rsvpStatus = rsvps[event.id]
                        )
                    )
                }
            }.collect {}

            loadWeather(event.latitude, event.longitude, event.startDate)
        }
    }

    private suspend fun loadWeather(lat: Double, lng: Double, startDate: Long) {
        val weatherRepository: WeatherRepository = container.weatherRepository
        if (!DateTimeUtils.isInFuture(startDate) ||
            DateTimeUtils.daysUntil(startDate) > 16L
        ) {
            _uiState.update { it.copy(weatherUnavailable = true) }
            return
        }
        weatherRepository.forecastFor(eventId, lat, lng, startDate)
            .onSuccess { weather ->
                _uiState.update { it.copy(weather = weather) }
                AppLogger.d("EventDetailViewModel", "Weather loaded for $eventId")
            }
            .onFailure {
                _uiState.update { it.copy(weatherUnavailable = true) }
            }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val added = eventRepository.toggleFavorite(eventId)
            _messages.emit(
                if (added) UiMessage.Resource(R.string.added_to_favorites)
                else UiMessage.Resource(R.string.removed_from_favorites)
            )
        }
    }

    fun setRsvp(status: RsvpStatus) {
        viewModelScope.launch {
            eventRepository.setRsvp(eventId, status)
            when (status) {
                RsvpStatus.ATTENDING -> {
                    val event = eventRepository.getEvent(eventId)
                    val remindersEnabled = container.preferences.remindersEnabled.first()
                    if (event != null && remindersEnabled) {
                        val scheduled = NotificationHelper.scheduleEventReminders(appContext, event)
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

    /** Deletes the event when the signed-in user is its organiser (FR-06). */
    fun deleteEvent(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = eventRepository.deleteEvent(eventId)
            if (result.isSuccess) {
                NotificationHelper.cancelEventReminders(appContext, eventId)
                _messages.emit(UiMessage.Resource(R.string.event_deleted))
                onDeleted()
            } else {
                _messages.emit(UiMessage.Resource(R.string.delete_failed))
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            appContext: android.content.Context,
            eventId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { EventDetailViewModel(container, appContext, eventId) }
        }
    }
}
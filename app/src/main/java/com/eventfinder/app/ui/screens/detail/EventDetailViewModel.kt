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
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventReview
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
    val weatherUnavailable: Boolean = false,
    val reviews: List<EventReview> = emptyList(),
    val averageRating: Double? = null,
    val reviewCount: Int = 0,
    val canReview: Boolean = false,
    val reviewDraftRating: Int = 0,
    val reviewDraftComment: String = "",
    val isSubmittingReview: Boolean = false
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
    private data class DetailsBundle(
        val event: Event?,
        val favoriteIds: Set<String>,
        val rsvps: Map<String, com.eventfinder.app.domain.model.RsvpStatus>,
        val reviews: List<EventReview>,
        val rating: com.eventfinder.app.data.repository.RatingSummary
    )

    private val eventRepository = container.eventRepository
    private val reviewRepository = container.eventReviewRepository

    private val eventFlow = MutableStateFlow<Event?>(null)
    private var currentUserId: String? = null
    private var weatherKey: Triple<Double, Double, Long>? = null

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            currentUserId = container.preferences.sessionUserId.first()
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                eventFlow,
                eventRepository.observeFavoriteIds(),
                eventRepository.observeRsvpStatuses(),
                reviewRepository.observeReviews(eventId),
                reviewRepository.observeRatingSummary(eventId)
            ) { event, favoriteIds, rsvps, reviews, rating ->
                DetailsBundle(event, favoriteIds, rsvps, reviews, rating)
            }.collect { bundle ->
                val event = bundle.event ?: return@collect
                val canReview = !currentUserId.isNullOrBlank() && (
                    (event.isCreatedByUser && event.organizerId == currentUserId) ||
                        bundle.rsvps[event.id] == com.eventfinder.app.domain.model.RsvpStatus.ATTENDING
                )
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        notFound = false,
                        eventView = EventView(
                            event = event,
                            rsvpStatus = bundle.rsvps[event.id],
                            isFavorite = bundle.favoriteIds.contains(event.id)
                        ),
                        reviews = bundle.reviews,
                        averageRating = bundle.rating.average,
                        reviewCount = bundle.rating.count,
                        canReview = canReview
                    )
                }
            }
        }
        refresh()
    }

    /**
     * Reloads the event from the cache. Called again whenever the screen resumes
     * so edits made on the create/edit screen are reflected immediately.
     */
    fun refresh() {
        viewModelScope.launch {
            val event = eventRepository.getEvent(eventId)
            if (event == null) {
                _uiState.update { it.copy(loading = false, notFound = true, eventView = null) }
                return@launch
            }
            eventFlow.value = event
            val latitude = event.latitude
            val longitude = event.longitude
            if (latitude != null && longitude != null) {
                loadWeather(latitude, longitude, event.startDate)
            } else {
                weatherKey = null
                _uiState.update {
                    it.copy(weather = null, weatherUnavailable = true)
                }
            }
        }
    }

    private suspend fun loadWeather(lat: Double, lng: Double, startDate: Long) {
        val key = Triple(lat, lng, startDate)
        if (key == weatherKey && _uiState.value.weather != null) return
        // Never keep showing a forecast belonging to an old event/date.
        _uiState.update { it.copy(weather = null, weatherUnavailable = false) }

        val weatherRepository: WeatherRepository = container.weatherRepository
        if (!DateTimeUtils.isInFuture(startDate) ||
            DateTimeUtils.daysUntil(startDate) > 16L
        ) {
            weatherKey = null
            _uiState.update { it.copy(weather = null, weatherUnavailable = true) }
            return
        }

        weatherRepository.forecastFor(eventId, lat, lng, startDate)
            .onSuccess { weather ->
                weatherKey = key
                _uiState.update { it.copy(weather = weather, weatherUnavailable = false) }
                AppLogger.d("EventDetailViewModel", "Weather loaded for $eventId")
            }
            .onFailure { error ->
                weatherKey = null
                _uiState.update { it.copy(weather = null, weatherUnavailable = true) }
                AppLogger.w(
                    "EventDetailViewModel",
                    "Weather unavailable for $eventId: ${error.message}"
                )
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
                    val userId = container.preferences.sessionUserId.first()
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

    fun setReviewRating(rating: Int) {
        _uiState.update { it.copy(reviewDraftRating = rating.coerceIn(0, 5)) }
    }

    fun setReviewComment(text: String) {
        _uiState.update { it.copy(reviewDraftComment = text.take(500)) }
    }

    fun submitReview() {
        viewModelScope.launch {
            val rating = _uiState.value.reviewDraftRating
            val comment = _uiState.value.reviewDraftComment
            _uiState.update { it.copy(isSubmittingReview = true) }
            val result = reviewRepository.submitReview(eventId, rating, comment)
            _uiState.update {
                it.copy(
                    isSubmittingReview = false,
                    reviewDraftRating = if (result.isSuccess) 0 else it.reviewDraftRating,
                    reviewDraftComment = if (result.isSuccess) "" else it.reviewDraftComment
                )
            }
            _messages.emit(
                if (result.isSuccess) UiMessage.Resource(R.string.review_submitted)
                else UiMessage.Literal(result.exceptionOrNull()?.message ?: "review_failed")
            )
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
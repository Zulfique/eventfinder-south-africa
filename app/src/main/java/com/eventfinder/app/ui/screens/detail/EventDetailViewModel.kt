package com.eventfinder.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.local.CachedReviewEntity
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.ReviewsRepository
import com.eventfinder.app.data.repository.WeatherRepository
import com.eventfinder.app.data.repository.WeatherSummary
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
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

/** One reviewer row rendered by the detail screen. */
data class ReviewRowUi(
    val id: String,
    val userName: String,
    val rating: Int,
    val comment: String?,
    val isVerifiedAttendee: Boolean,
    val createdAtMillis: Long,
    val isMine: Boolean
)

/** UI state for the event detail screen (Screen 6). */
data class EventDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val eventView: EventView? = null,
    val weather: WeatherSummary? = null,
    val weatherUnavailable: Boolean = false,
    val reviews: List<ReviewRowUi> = emptyList(),
    val reviewAverage: Double = 0.0,
    val reviewCount: Int = 0,
    val reviewDialogOpen: Boolean = false,
    val reviewSubmitting: Boolean = false,
    val signedIn: Boolean = false
)

/**
 * Event detail (Screen 6): loads the event, streams live favourite + RSVP
 * state, and fetches the venue weather from the free Open-Meteo API.
 */
class EventDetailViewModel(
    private val container: AppContainer,
    private val appContext: android.content.Context,
    private val eventId: String,
    // Optional seams so JVM tests can drive the weather state machine with
    // fakes instead of the Room-backed singletons in [AppContainer].
    injectedEventRepository: EventRepository? = null,
    injectedWeatherRepository: WeatherRepository? = null
) : ViewModel() {

    private val eventRepository = injectedEventRepository ?: container.eventRepository
    private val weatherRepository = injectedWeatherRepository ?: container.weatherRepository

    /**
     * Reviews are optional: JVM unit tests build the container over a mock
     * Context where Room/DataStore cannot initialize, so a failure here just
     * disables the reviews feature instead of crashing the screen.
     */
    private val reviewsRepository: ReviewsRepository? =
        runCatching { container.reviewsRepository }.getOrNull()

    private val eventFlow = MutableStateFlow<Event?>(null)
    private var weatherKey: Triple<Double, Double, Long>? = null

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        val reviews = reviewsRepository
        if (reviews != null) {
            viewModelScope.launch {
                reviews.observeReviews(eventId).collect { rows ->
                    val myId = runCatching { container.preferences.sessionUserId.first() }.getOrNull()
                    _uiState.update { s -> s.copy(reviews = rows.map { it.toRowUi(myId) }) }
                }
            }
            viewModelScope.launch {
                reviews.observeSummary(eventId).collect { summary ->
                    _uiState.update { s ->
                        s.copy(
                            reviewAverage = summary?.average ?: 0.0,
                            reviewCount = summary?.count ?: 0
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                container.preferences.sessionUserId.collect { id ->
                    _uiState.update { it.copy(signedIn = id != null) }
                }
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                eventFlow,
                eventRepository.observeFavoriteIds(),
                eventRepository.observeRsvpStatuses()
            ) { event, favoriteIds, rsvps -> Triple(event, favoriteIds, rsvps) }
                .collect { (event, favoriteIds, rsvps) ->
                    if (event == null) return@collect
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            notFound = false,
                            eventView = EventView(
                                event = event,
                                rsvpStatus = rsvps[event.id],
                                isFavorite = favoriteIds.contains(event.id)
                            )
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
            loadReviews()
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

        // Never keep showing a forecast belonging to an old event/date: the
        // WeatherCard reads state.weather before state.weatherUnavailable, so a
        // stale value would mask the unavailable state entirely.
        _uiState.update { it.copy(weather = null, weatherUnavailable = false) }

        if (!DateTimeUtils.isInFuture(startDate) ||
            DateTimeUtils.daysUntil(startDate) > 16L
        ) {
            weatherKey = null
            _uiState.update {
                it.copy(weather = null, weatherUnavailable = true)
            }
            return
        }

        weatherRepository.forecastFor(eventId, lat, lng, startDate)
            .onSuccess { weather ->
                weatherKey = key
                _uiState.update {
                    it.copy(weather = weather, weatherUnavailable = false)
                }
                AppLogger.d("EventDetailViewModel", "Weather loaded for $eventId")
            }
            .onFailure { error ->
                weatherKey = null
                _uiState.update {
                    it.copy(weather = null, weatherUnavailable = true)
                }
                AppLogger.w(
                    "EventDetailViewModel",
                    "Weather unavailable for $eventId: ${error.message}"
                )
            }
    }

    /** Re-fetches reviews from the server; the Room cache drives the UI. */
    private fun loadReviews() {
        val reviews = reviewsRepository ?: return
        viewModelScope.launch {
            reviews.refresh(eventId).onFailure { error ->
                AppLogger.w("EventDetailViewModel", "Reviews refresh failed: ${error.message}")
            }
        }
    }

    fun openReviewDialog() {
        _uiState.update { it.copy(reviewDialogOpen = true) }
    }

    fun closeReviewDialog() {
        _uiState.update { it.copy(reviewDialogOpen = false, reviewSubmitting = false) }
    }

    /** Creates or replaces the signed-in user's review for this event. */
    fun submitReview(rating: Int, comment: String?) {
        if (rating !in 1..5) return
        val reviews = reviewsRepository
        viewModelScope.launch {
            if (reviews == null) {
                _messages.emit(UiMessage.Resource(R.string.review_failed))
                return@launch
            }
            _uiState.update { it.copy(reviewSubmitting = true) }
            val trimmed = comment?.trim()?.takeIf { it.isNotEmpty() }
            reviews.submit(eventId, rating, trimmed)
                .onSuccess {
                    _uiState.update { s -> s.copy(reviewDialogOpen = false, reviewSubmitting = false) }
                    _messages.emit(UiMessage.Resource(R.string.review_saved))
                }
                .onFailure { error ->
                    _uiState.update { s -> s.copy(reviewSubmitting = false) }
                    AppLogger.w("EventDetailViewModel", "Review submit failed: ${error.message}")
                    _messages.emit(UiMessage.Resource(R.string.review_failed))
                }
        }
    }

    /** Deletes the signed-in user's review for this event. */
    fun deleteMyReview() {
        val reviews = reviewsRepository
        viewModelScope.launch {
            if (reviews == null) {
                _messages.emit(UiMessage.Resource(R.string.review_failed))
                return@launch
            }
            reviews.delete(eventId)
                .onSuccess { _messages.emit(UiMessage.Resource(R.string.review_deleted)) }
                .onFailure { error ->
                    AppLogger.w("EventDetailViewModel", "Review delete failed: ${error.message}")
                    _messages.emit(UiMessage.Resource(R.string.review_failed))
                }
        }
    }

    private fun CachedReviewEntity.toRowUi(currentUserId: String?) = ReviewRowUi(
        id = reviewId,
        userName = userName,
        rating = rating,
        comment = comment,
        isVerifiedAttendee = isVerifiedAttendee,
        createdAtMillis = createdAt,
        isMine = currentUserId != null && userId == currentUserId
    )

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
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
import com.eventfinder.app.domain.model.SimilarEvents
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

/** Review list filter chips (see the reviews section of EventDetailScreen). */
enum class ReviewFilter(val label: String) {
    ALL("All"),
    RATING_5("5★"),
    RATING_4("4★"),
    RATING_3("3★"),
    RATING_2("2★"),
    VERIFIED("Verified ✓"),
    RECENT("Last 30 days"),
    PHOTOS("With photos");

    fun matches(review: com.eventfinder.app.domain.model.EventReview): Boolean = when (this) {
        ALL -> true
        RATING_5 -> review.rating == 5
        RATING_4 -> review.rating == 4
        RATING_3 -> review.rating == 3
        RATING_2 -> review.rating <= 2
        VERIFIED -> review.verifiedAttendance
        RECENT -> review.createdAt >= System.currentTimeMillis() - THIRTY_DAYS_MS
        PHOTOS -> review.photoUrls.isNotEmpty()
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** UI state for the event detail screen (Screen 6). */
data class EventDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val eventView: EventView? = null,
    val weather: WeatherSummary? = null,
    val weatherUnavailable: Boolean = false,
    val reviews: List<EventReview> = emptyList(),
    val reviewFilter: ReviewFilter = ReviewFilter.ALL,
    val averageRating: Double? = null,
    val reviewCount: Int = 0,
    val canReview: Boolean = false,
    val reviewDraftRating: Int = 0,
    val reviewDraftComment: String = "",
    /** Local URIs staged for the review being written (max 4). */
    val reviewDraftPhotos: List<String> = emptyList(),
    val isSubmittingReview: Boolean = false,
    /** The signed-in attendee's own ticket for this event (QR shows this). */
    val myTicket: com.eventfinder.app.domain.model.EventTicket? = null,
    /** True while an RSVP write (and its reminder/ticket side-effects) runs. */
    val rsvpSaving: Boolean = false,
    /** "Skip reminders" is on for this event only. */
    val reminderSkipped: Boolean = false,
    /** Authors the signed-in user has blocked; their reviews stay hidden. */
    val blockedUserIds: Set<String> = emptySet(),
    /** Nearby events in the same category, loaded on demand. */
    val similarEvents: List<EventView> = emptyList()
) { 
    val visibleReviews: List<EventReview>
        get() = reviews.filter { it.userId !in blockedUserIds && reviewFilter.matches(it) }
}

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
    private val ticketRepository = container.ticketRepository

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
            loadMyTicket()
            loadBlockedUsers()
        }
        viewModelScope.launch {
            container.preferences.skippedReminderEventIds.collect { skipped ->
                _uiState.update { it.copy(reminderSkipped = eventId in skipped) }
            }
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
            loadMyTicket()
            loadSimilarEvents(event)
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

    /** Users this account has blocked — their reviews never render. */
    private suspend fun loadBlockedUsers() {
        val userId = currentUserId ?: return
        val blocked = container.reportRepository.blockedIds(userId)
        _uiState.update { it.copy(blockedUserIds = blocked) }
    }

    /** Blocks the author of a review and hides their content immediately. */
    fun blockReviewer(authorId: String) {
        val userId = currentUserId ?: return
        if (authorId.isBlank() || authorId == userId) return
        viewModelScope.launch {
            container.reportRepository.block(userId, authorId)
            loadBlockedUsers()
            _messages.emit(UiMessage.Literal("Reviewer blocked"))
        }
    }

    /**
     * Builds the "Similar events" rail from the local catalogue: same category,
     * same organiser and nearby venues, soonest first. Works offline.
     */
    private suspend fun loadSimilarEvents(event: Event) {
        val candidates = eventRepository.observeAllEvents().first()
        val ranked = SimilarEvents.rank(event, candidates)
        if (ranked.isEmpty()) {
            _uiState.update { it.copy(similarEvents = emptyList()) }
            return
        }
        val favorites = eventRepository.observeFavoriteIds().first()
        val rsvps = eventRepository.observeRsvpStatuses().first()
        _uiState.update { state ->
            state.copy(
                similarEvents = ranked.map { similar ->
                    EventView(
                        event = similar,
                        rsvpStatus = rsvps[similar.id],
                        isFavorite = favorites.contains(similar.id)
                    )
                }
            )
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

    /** Loads (never creates) the attendee's own ticket for this event. */
    private suspend fun loadMyTicket() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            _uiState.update { it.copy(myTicket = null) }
            return
        }
        _uiState.update { it.copy(myTicket = ticketRepository.getTicketForUser(eventId, userId)) }
    }

    /** Issues (or reloads) the attendee's own ticket on demand. */
    fun issueMyTicket() {
        viewModelScope.launch {
            val userId = currentUserId
            if (userId.isNullOrBlank()) return@launch
            val ticket = ticketRepository.getTicketForUser(eventId, userId)
                ?: ticketRepository.issueTicket(eventId, userId)
            _uiState.update { it.copy(myTicket = ticket) }
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
            _uiState.update { it.copy(rsvpSaving = true) }
            try {
            eventRepository.setRsvp(eventId, status)
            when (status) {
                RsvpStatus.ATTENDING -> {
                    val event = eventRepository.getEvent(eventId)
                    val userId = container.preferences.sessionUserId.first()
                    // Check in tools need a ticket: issuing one on RSVP keeps the
                    // attendee QR flow working even for events created offline.
                    if (!userId.isNullOrBlank()) {
                        val existing = ticketRepository.getTicketForUser(eventId, userId)
                        val ticket = existing ?: ticketRepository.issueTicket(eventId, userId)
                        _uiState.update { it.copy(myTicket = ticket) }
                    }
                    val remindersEnabled = container.preferences.remindersEnabled.first()
                    val reminderSkipped = eventId in
                        container.preferences.skippedReminderEventIds.first()
                    if (event != null && remindersEnabled && !reminderSkipped && !userId.isNullOrBlank()) {
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
            } finally {
                _uiState.update { it.copy(rsvpSaving = false) }
            }
        }
    }

    /**
     * Mutes or restores this event's reminder without touching the global
     * reminders setting, matching the showcase's "Skip reminders: this event
     * only" control.
     */
    fun setReminderSkipped(skip: Boolean) {
        viewModelScope.launch {
            container.preferences.setReminderSkipped(eventId, skip)
            val event = eventRepository.getEvent(eventId)
            val userId = container.preferences.sessionUserId.first()
            if (skip) {
                NotificationHelper.cancelEventReminders(appContext, eventId)
                _messages.emit(UiMessage.Resource(R.string.reminders_cancelled))
            } else if (
                event != null &&
                !userId.isNullOrBlank() &&
                container.preferences.remindersEnabled.first()
            ) {
                NotificationHelper.scheduleEventReminders(appContext, event, userId)
                _messages.emit(UiMessage.Resource(R.string.reminder_scheduled))
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

    fun setReviewFilter(filter: ReviewFilter) {
        _uiState.update { it.copy(reviewFilter = filter) }
    }

    fun addReviewPhoto(uri: String) {
        _uiState.update { state ->
            if (uri.isBlank() || state.reviewDraftPhotos.contains(uri)) state
            else state.copy(reviewDraftPhotos = (state.reviewDraftPhotos + uri).take(4))
        }
    }

    fun removeReviewPhoto(uri: String) {
        _uiState.update { it.copy(reviewDraftPhotos = it.reviewDraftPhotos - uri) }
    }

    fun submitReview() {
        viewModelScope.launch {
            val rating = _uiState.value.reviewDraftRating
            val comment = _uiState.value.reviewDraftComment
            val photos = _uiState.value.reviewDraftPhotos
            _uiState.update { it.copy(isSubmittingReview = true) }
            // Best effort: only URLs that upload successfully are attached; a
            // failed upload never blocks the review itself (offline-safe).
            val uploaded = photos.mapNotNull { uri ->
                runCatching { container.imageUploader.uploadEventImage(uri, eventId) }
                    .getOrNull()
            }
            val result = reviewRepository.submitReview(eventId, rating, comment, uploaded)
            _uiState.update {
                it.copy(
                    isSubmittingReview = false,
                    reviewDraftRating = if (result.isSuccess) 0 else it.reviewDraftRating,
                    reviewDraftComment = if (result.isSuccess) "" else it.reviewDraftComment,
                    reviewDraftPhotos = if (result.isSuccess) emptyList() else it.reviewDraftPhotos
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
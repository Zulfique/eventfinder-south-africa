package com.eventfinder.app.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.NewEventDraft
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Number of creation steps in the wizard. */
const val CREATE_STEPS = 3

/** UI state for the create-event wizard. */
data class CreateEventUiState(
    val step: Int = 1,
    val isSubmitting: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: EventCategory = EventCategory.COMMUNITY,
    val dateMillis: Long = 0L,
    val venueName: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val isPublic: Boolean = true
)

/**
 * Create event wizard (Screen 8). Multi-step form with validation on each
 * step before the event is written to the local cache (REST publishing is
 * queued when offline).
 */
class CreateEventViewModel(
    private val eventRepository: EventRepository,
    private val eventId: String? = null
) : ViewModel() {

    /** True when the wizard is editing an existing user-created event. */
    val isEditMode: Boolean = eventId != null

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    /** Emitted after a successful edit so the host screen can navigate back. */
    private val _completed = MutableSharedFlow<Unit>()
    val completed = _completed.asSharedFlow()

    init {
        if (eventId != null) {
            viewModelScope.launch { loadEvent(eventId) }
        }
    }

    private suspend fun loadEvent(id: String) {
        val event = eventRepository.getEvent(id)
        if (event == null) {
            AppLogger.w("CreateEventViewModel", "Edit requested for missing event: $id")
            _messages.emit(UiMessage.Resource(R.string.update_failed))
            return
        }
        _uiState.update {
            it.copy(
                title = event.title,
                description = event.description,
                category = event.category,
                dateMillis = event.startDate,
                venueName = event.venueName,
                address = event.address,
                latitude = event.latitude.toString(),
                longitude = event.longitude.toString(),
                isPublic = event.isPublic
            )
        }
    }

    fun onTitleChange(v: String) = _uiState.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onCategoryChange(c: EventCategory) = _uiState.update { it.copy(category = c) }
    fun onDateChange(millis: Long) = _uiState.update { it.copy(dateMillis = millis) }
    fun onVenueChange(v: String) = _uiState.update { it.copy(venueName = v) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v) }
    fun onLatitudeChange(v: String) = _uiState.update { it.copy(latitude = v) }
    fun onLongitudeChange(v: String) = _uiState.update { it.copy(longitude = v) }
    fun onPublicChange(v: Boolean) = _uiState.update { it.copy(isPublic = v) }

    /** Advances to the next step if the current one validates. */
    fun nextStep() {
        val state = _uiState.value
        val errorRes = validateStep(state)
        if (errorRes != null) {
            _messages.tryEmit(UiMessage.Resource(errorRes))
            return
        }
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(CREATE_STEPS)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    private fun validateStep(state: CreateEventUiState): Int? = when (state.step) {
        1 -> when {
            state.title.isBlank() -> R.string.title_required
            state.description.length < 20 -> R.string.description_required
            else -> null
        }
        2 -> when {
            state.dateMillis == 0L || !DateTimeUtils.isInFuture(state.dateMillis) ->
                R.string.date_in_past
            state.venueName.isBlank() -> R.string.venue_required
            !isValidCoordinate(state.latitude, -90.0, 90.0) -> R.string.invalid_coordinates
            !isValidCoordinate(state.longitude, -180.0, 180.0) -> R.string.invalid_coordinates
            else -> null
        }
        else -> null
    }

    private fun isValidCoordinate(raw: String, min: Double, max: Double): Boolean {
        if (raw.isBlank()) return true
        val value = raw.toDoubleOrNull() ?: return false
        return value in min..max
    }

    /** Publishes the event to the local cache + offline queue. */
    fun publish() {
        val state = _uiState.value
        if (state.isSubmitting) return
        // Default coordinates fall back to Johannesburg (South Africa).
        val defaultLat = -26.2041
        val defaultLng = 28.0473
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val lat = state.latitude.toDoubleOrNull() ?: defaultLat
            val lng = state.longitude.toDoubleOrNull() ?: defaultLng
            val draft = NewEventDraft(
                title = state.title.trim(),
                description = state.description.trim(),
                category = state.category,
                startDate = state.dateMillis,
                endDate = state.dateMillis + TimeUnit.HOURS.toMillis(2),
                venueName = state.venueName.trim(),
                address = state.address.trim().ifBlank { state.venueName.trim() },
                latitude = lat,
                longitude = lng,
                isPublic = state.isPublic
            )
            val result: Result<*> = if (eventId != null) {
                eventRepository.updateEvent(eventId, draft)
            } else {
                eventRepository.createEvent(draft)
            }
            _uiState.update { it.copy(isSubmitting = false) }
            if (result.isSuccess) {
                if (eventId != null) {
                    AppLogger.i("CreateEventViewModel", "Updated event: $eventId")
                    _messages.emit(UiMessage.Resource(R.string.event_updated))
                    _completed.emit(Unit)
                } else {
                    AppLogger.i("CreateEventViewModel", "Published event: ${result.getOrNull()}")
                    _messages.emit(UiMessage.Resource(R.string.event_published))
                    reset()
                }
            } else {
                _messages.emit(
                    UiMessage.Resource(
                        if (eventId != null) R.string.update_failed else R.string.publish_failed
                    )
                )
            }
        }
    }

    fun reset() {
        _uiState.value = CreateEventUiState()
    }

    companion object {
        fun factory(
            container: AppContainer,
            eventId: String? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateEventViewModel(container.eventRepository, eventId) }
        }
    }
}
package com.eventfinder.app.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import android.net.Uri
import com.eventfinder.app.R
import com.eventfinder.app.data.cloud.AzureImageUploader
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.IMAGE_REMOVED
import com.eventfinder.app.data.repository.NewEventDraft
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils
import com.eventfinder.app.utils.ImageStorage
import com.eventfinder.app.utils.PlaceResolver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val isPublic: Boolean = true,
    val imageUrl: String? = null,
    /** null = one-off, otherwise "weekly" / "fortnightly" / "monthly". */
    val recurrenceRule: String? = null,
    /** True once the user has tried to advance an invalid step, enabling inline errors. */
    val showErrors: Boolean = false
)

/** A field-level validation failure for the create-event wizard. */
enum class ValidationError {
    TITLE_REQUIRED,
    DESCRIPTION_REQUIRED,
    DESCRIPTION_TOO_SHORT,
    DATE_REQUIRED,
    DATE_IN_PAST,
    VENUE_REQUIRED,
    INVALID_COORDINATES
}

/**
 * Pure, framework-free validation for a wizard step. Kept out of the class so it
 * can be covered directly by JVM unit tests (see [CreateEventValidationTest]).
 */
internal fun validateCreateStep(state: CreateEventUiState): ValidationError? = when (state.step) {
    1 -> when {
        state.title.isBlank() -> ValidationError.TITLE_REQUIRED
        state.description.isBlank() -> ValidationError.DESCRIPTION_REQUIRED
        state.description.length < MIN_DESCRIPTION_LENGTH -> ValidationError.DESCRIPTION_TOO_SHORT
        else -> null
    }
    2 -> when {
        state.dateMillis == 0L -> ValidationError.DATE_REQUIRED
        !DateTimeUtils.isInFuture(state.dateMillis) -> ValidationError.DATE_IN_PAST
        state.venueName.isBlank() -> ValidationError.VENUE_REQUIRED
        !isValidLatitude(state.latitude) || !isValidLongitude(state.longitude) ->
            ValidationError.INVALID_COORDINATES
        else -> null
    }
    else -> null
}

internal fun isValidLatitude(raw: String): Boolean = isValidCoordinate(raw, -90.0, 90.0)

internal fun isValidLongitude(raw: String): Boolean = isValidCoordinate(raw, -180.0, 180.0)

internal fun isValidCoordinate(raw: String, min: Double, max: Double): Boolean {
    // Blank coordinates are allowed: the event is stored with null latitude/
    // longitude (see the "leave the coordinates blank" hint on step 2).
    if (raw.isBlank()) return true
    val value = raw.toDoubleOrNull() ?: return false
    return value in min..max
}

/** Minimum description length enforced by step 1. */
internal const val MIN_DESCRIPTION_LENGTH = 20

/**
 * Create event wizard (Screen 8). Multi-step form with validation on each
 * step before the event is written to the local Room catalogue and, when
 * available, mirrored to Cloud Firestore with the photo stored in Firebase
 * Cloud Storage (blob storage).
 */
class CreateEventViewModel(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository,
    private val eventId: String? = null,
    private val imageUploader: AzureImageUploader? = null,
    private val placeResolver: PlaceResolver? = null,
    private val draftDao: com.eventfinder.app.data.local.DraftDao? = null,
    private val userIdProvider: suspend () -> String? = { null }
) : ViewModel() {

    /** True when the wizard is editing an existing user-created event. */
    val isEditMode: Boolean = eventId != null

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    /** Emitted after a successful edit so the host screen can navigate back. */
    private val _completed = MutableSharedFlow<Unit>()
    val completed = _completed.asSharedFlow()

    init {
        if (eventId != null) {
            viewModelScope.launch { loadEvent(eventId) }
        } else {
            // Create mode: crash/lifecycle recovery — restore the last autosaved
            // draft, then keep it refreshed while the user fills the wizard in.
            viewModelScope.launch { restoreDraft() }
            viewModelScope.launch { autosaveDrafts() }
        }
    }

    /** Stable id of the autosaved draft row (kept across restores). */
    private var draftId: String = java.util.UUID.randomUUID().toString()

    /**
     * Draft restoration (FR-10): pre-fills the wizard from the latest draft
     * saved for the signed-in/guest user so an interrupted session resumes.
     */
    private suspend fun restoreDraft() {
        val dao = draftDao ?: return
        val userId = userIdProvider() ?: return
        val latest = runCatching { dao.forUser(userId).firstOrNull() }.getOrNull() ?: return
        if (latest.title.isBlank() && latest.description.isBlank()) return
        draftId = latest.id
        _uiState.update {
            it.copy(
                title = latest.title,
                description = latest.description,
                category = runCatching { EventCategory.valueOf(latest.category) }
                    .getOrDefault(EventCategory.COMMUNITY),
                dateMillis = latest.startDate,
                venueName = latest.venueName,
                address = latest.address,
                latitude = latest.latitude?.toString() ?: "",
                longitude = latest.longitude?.toString() ?: "",
                imageUrl = latest.imageUrl
            )
        }
        _messages.tryEmit(UiMessage.Resource(R.string.draft_restored))
    }

    /**
     * Debounced autosave: a MutableStateFlow conflates bursts, so the short
     * delay below turns rapid typing into a single Room upsert.
     */
    private suspend fun autosaveDrafts() {
        var lastSaved = ""
        uiState.collect { state ->
            kotlinx.coroutines.delay(750)
            val snapshot = listOf(
                state.title, state.description, state.venueName,
                state.address, state.latitude, state.longitude,
                state.dateMillis.toString(), state.category.name,
                state.imageUrl ?: ""
            ).joinToString("\u0001")
            if (snapshot == lastSaved) return@collect
            val dao = draftDao ?: return@collect
            val userId = userIdProvider() ?: return@collect
            if (state.title.isBlank() && state.description.isBlank()) return@collect
            runCatching {
                dao.upsert(
                    com.eventfinder.app.data.local.DraftEntity(
                        id = draftId,
                        userId = userId,
                        title = state.title,
                        description = state.description,
                        category = state.category.name,
                        venueName = state.venueName,
                        address = state.address,
                        latitude = state.latitude.toDoubleOrNull(),
                        longitude = state.longitude.toDoubleOrNull(),
                        imageUrl = state.imageUrl,
                        startDate = state.dateMillis,
                        endDate = state.dateMillis + TimeUnit.HOURS.toMillis(2),
                        savedAt = System.currentTimeMillis()
                    )
                )
                lastSaved = snapshot
            }
        }
    }

    /** Discards the autosaved draft and starts the form fresh. */
    fun discardDraft() {
        viewModelScope.launch {
            val dao = draftDao
            val userId = userIdProvider()
            if (dao != null && userId != null) {
                runCatching { dao.delete(draftId) }
            }
            reset()
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
                latitude = event.latitude?.toString() ?: "",
                longitude = event.longitude?.toString() ?: "",
                isPublic = event.isPublic,
                imageUrl = event.imageUrl,
                recurrenceRule = event.recurrenceRule
            )
        }
    }

    /** Imports a picked gallery image into internal storage (FR-06). */
    fun importImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val previous = _uiState.value.imageUrl
            val imported = ImageStorage.importImage(context, uri)
            if (imported != null) {
                ImageStorage.deleteIfLocal(previous)
                _uiState.update { it.copy(imageUrl = imported) }
            } else {
                _messages.emit(UiMessage.Resource(R.string.image_pick_failed))
            }
        }
    }

    /** Removes the chosen image (and its local copy, if any). */
    fun removeImage() {
        ImageStorage.deleteIfLocal(_uiState.value.imageUrl)
        _uiState.update { it.copy(imageUrl = IMAGE_REMOVED) }
    }

    fun onTitleChange(v: String) = _uiState.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onCategoryChange(c: EventCategory) = _uiState.update { it.copy(category = c) }
    fun onDateChange(millis: Long) = _uiState.update { it.copy(dateMillis = millis) }

    /** null clears the repeat rule (one-off event). */
    fun onRecurrenceChange(rule: String?) =
        _uiState.update { it.copy(recurrenceRule = rule) }
    fun onVenueChange(v: String) = _uiState.update { it.copy(venueName = v) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v) }
    fun onLatitudeChange(v: String) = _uiState.update { it.copy(latitude = v) }
    fun onLongitudeChange(v: String) = _uiState.update { it.copy(longitude = v) }
    fun onPublicChange(v: Boolean) = _uiState.update { it.copy(isPublic = v) }

    /** Advances to the next step if the current one validates. */
    fun nextStep() {
        val state = _uiState.value
        val error = validateCreateStep(state)
        if (error != null) {
            _uiState.update { it.copy(showErrors = true) }
            _messages.tryEmit(UiMessage.Resource(error.messageRes()))
            return
        }
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(CREATE_STEPS), showErrors = false) }
    }

    fun previousStep() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(1), showErrors = false) }
    }

    private fun ValidationError.messageRes(): Int = when (this) {
        ValidationError.TITLE_REQUIRED -> R.string.title_required
        ValidationError.DESCRIPTION_REQUIRED -> R.string.description_required
        ValidationError.DESCRIPTION_TOO_SHORT -> R.string.description_too_short
        ValidationError.DATE_REQUIRED -> R.string.date_required
        ValidationError.DATE_IN_PAST -> R.string.date_in_past
        ValidationError.VENUE_REQUIRED -> R.string.venue_required
        ValidationError.INVALID_COORDINATES -> R.string.invalid_coordinates
    }

    /** Publishes the event to the local Room catalogue. */
    fun publish() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val step1Error = when {
            state.title.isBlank() -> ValidationError.TITLE_REQUIRED
            state.description.isBlank() -> ValidationError.DESCRIPTION_REQUIRED
            state.description.length < MIN_DESCRIPTION_LENGTH -> ValidationError.DESCRIPTION_TOO_SHORT
            else -> null
        }
        if (step1Error != null) {
            _messages.tryEmit(UiMessage.Resource(step1Error.messageRes()))
            return
        }

        val step2Error = when {
            state.dateMillis == 0L -> ValidationError.DATE_REQUIRED
            !DateTimeUtils.isInFuture(state.dateMillis) -> ValidationError.DATE_IN_PAST
            state.venueName.isBlank() -> ValidationError.VENUE_REQUIRED
            !isValidLatitude(state.latitude) || !isValidLongitude(state.longitude) ->
                ValidationError.INVALID_COORDINATES
            else -> null
        }
        if (step2Error != null) {
            _messages.tryEmit(UiMessage.Resource(step2Error.messageRes()))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            // Blank coordinates mean "place it where the user is". The place
            // resolver returns the device position, the city configured in
            // Settings or the nation-wide fallback — never a hard-coded city.
            var lat = state.latitude.trim().toDoubleOrNull()
            var lng = state.longitude.trim().toDoubleOrNull()
            if (lat == null || lng == null) {
                val place = placeResolver?.resolve()
                if (place != null) {
                    lat = lat ?: place.latitude
                    lng = lng ?: place.longitude
                    AppLogger.i(
                        "CreateEventViewModel",
                        "Placed event at ${place.label} (${place.latitude}, ${place.longitude})"
                    )
                }
            }

            // Blob storage: if a local photo was picked, upload it to
            // Azure Blob Storage (through the REST API, which owns the storage
            // credentials) and use the permanent public URL. Falls back to
            // the local file URI when Azure is unavailable or the upload
            // fails, so the flow never crashes and works fully offline.
            var imageUrl = state.imageUrl
            if (imageUrl != null &&
                imageUrl.startsWith("file:") &&
                imageUploader != null
            ) {
                val uploadId = eventId ?: imageUploader.newImageId()
                val remoteUrl = imageUploader.uploadEventImage(imageUrl, uploadId)
                if (remoteUrl != null) {
                    ImageStorage.deleteIfLocal(imageUrl)
                    imageUrl = remoteUrl
                }
            }

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
                isPublic = state.isPublic,
                imageUrl = imageUrl,
                recurrenceRule = state.recurrenceRule
            )
            val result: Result<*> = if (eventId != null) {
                eventRepository.updateEvent(eventId, draft)
            } else {
                // The app runs in no-account mode (splash skips auth), so a guest
                // session must exist for the event to be stored with an organizer.
                authRepository.continueAsGuest()
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
                    // The wizard succeeded — its autosaved draft is now stale.
                    runCatching { draftDao?.delete(draftId) }
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
            initializer {
                CreateEventViewModel(
                    container.eventRepository,
                    container.authRepository,
                    eventId,
                    container.imageUploader,
                    container.placeResolver,
                    draftDao = container.database.draftDao(),
                    userIdProvider = { container.preferences.sessionUserId.first() }
                )
            }
        }
    }
}
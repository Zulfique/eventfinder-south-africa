package com.eventfinder.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the profile screen. */
data class ProfileUiState(
    val fullName: String = "",
    val email: String = "",
    val eventsCreated: Int = 0,
    val attendingCount: Int = 0,
    val favoriteCount: Int = 0,
    val avatarUrl: String? = null,
    val avatarStyle: Int = 0,
    val myEvents: List<EventView> = emptyList(),
    val attending: List<EventView> = emptyList()
)

/** Profile avatar snapshot (photo URL + generated style). */
data class AvatarInfo(val url: String?, val style: Int)

/**
 * Profile (Screen 4): account summary, stats and the user's own/attending
 * events. Logout navigates back to the login flow.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences,
    eventRepository: EventRepository
) : ViewModel() {

    private val rsvps = eventRepository.observeRsvpStatuses()
    private val favorites = eventRepository.observeFavoriteIds()
    private val events = eventRepository.observeAllEvents()
    private val avatar = MutableStateFlow(AvatarInfo(null, 0))

    init {
        reloadAvatar()
    }

    /** Re-reads the stored avatar; called again whenever the screen re-enters. */
    fun reloadAvatar() {
        viewModelScope.launch {
            val userId = preferences.sessionUserId.first() ?: return@launch
            avatar.value = AvatarInfo(
                url = preferences.avatarUrl(userId),
                style = preferences.avatarStyle(userId)
            )
        }
    }

    val uiState = combine(authRepository.currentUser, events, rsvps, favorites, avatar) { user, all, rsvpMap, favIds, avatarInfo ->
        // Only events this account organised: isCreatedByUser alone also matches
        // other accounts' events that share the device database.
        val myEvents = all.filter { it.isCreatedByUser && it.organizerId == user?.id }
            .sortedByDescending { it.startDate }
            .map { EventView(event = it, rsvpStatus = rsvpMap[it.id]) }
        val attending = all.filter { rsvpMap[it.id] == RsvpStatus.ATTENDING || rsvpMap[it.id] == RsvpStatus.MAYBE }
            .sortedBy { it.startDate }
            .map { EventView(event = it, rsvpStatus = rsvpMap[it.id]) }
        ProfileUiState(
            fullName = user?.fullName ?: "",
            email = user?.email ?: "",
            eventsCreated = myEvents.size,
            // "Maybe" replies are shown in the list below but are not counted as
            // attending — the stat should reflect a committed RSVP only.
            attendingCount = all.count { rsvpMap[it.id] == RsvpStatus.ATTENDING },
            favoriteCount = favIds.size,
            avatarUrl = avatarInfo.url,
            avatarStyle = avatarInfo.style,
            myEvents = myEvents,
            attending = attending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(container.authRepository, container.preferences, container.eventRepository)
            }
        }
    }
}
package com.eventfinder.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the profile screen. */
data class ProfileUiState(
    val fullName: String = "",
    val email: String = "",
    val eventsCreated: Int = 0,
    val attendingCount: Int = 0,
    val favoriteCount: Int = 0,
    val myEvents: List<EventView> = emptyList(),
    val attending: List<EventView> = emptyList()
)

/**
 * Profile (Screen 4): account summary, stats and the user's own/attending
 * events. Logout navigates back to the login flow.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    eventRepository: EventRepository
) : ViewModel() {

    private val rsvps = eventRepository.observeRsvpStatuses()
    private val favorites = eventRepository.observeFavoriteIds()
    private val events = eventRepository.observeAllEvents()

    val uiState = combine(authRepository.currentUser, events, rsvps, favorites) { user, all, rsvpMap, favIds ->
        val myEvents = all.filter { it.isCreatedByUser }.sortedByDescending { it.startDate }
            .map { EventView(event = it, rsvpStatus = rsvpMap[it.id]) }
        val attending = all.filter { rsvpMap[it.id] == RsvpStatus.ATTENDING || rsvpMap[it.id] == RsvpStatus.MAYBE }
            .sortedBy { it.startDate }
            .map { EventView(event = it, rsvpStatus = rsvpMap[it.id]) }
        ProfileUiState(
            fullName = user?.fullName ?: "",
            email = user?.email ?: "",
            eventsCreated = myEvents.size,
            attendingCount = attending.size,
            favoriteCount = favIds.size,
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
                ProfileViewModel(container.authRepository, container.eventRepository)
            }
        }
    }
}
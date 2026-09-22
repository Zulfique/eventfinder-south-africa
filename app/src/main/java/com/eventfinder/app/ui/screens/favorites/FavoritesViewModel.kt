package com.eventfinder.app.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventFilterer
import com.eventfinder.app.domain.model.EventView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favourites (Offline Favourites, FR-03). */
class FavoritesViewModel(
    private val eventRepository: EventRepository
) : ViewModel() {

    val uiState = combine(
        eventRepository.observeFavoriteEvents().onStart { emit(emptyList()) },
        eventRepository.observeRsvpStatuses(),
        eventRepository.observeFavoriteIds()
    ) { favorites, rsvps, favoriteIds ->
        val sorted = favorites.sortedBy { it.startDate }
        EventFilterer.attachDistances(sorted, null, null, favoriteIds).map { view ->
            view.copy(rsvpStatus = rsvps[view.event.id])
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun onRemoveFavorite(eventId: String) {
        viewModelScope.launch { eventRepository.toggleFavorite(eventId) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FavoritesViewModel(container.eventRepository) }
        }
    }
}
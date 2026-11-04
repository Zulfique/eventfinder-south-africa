package com.eventfinder.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventFilterer
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.data.repository.EventRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the search screen (Screen 7). */
data class SearchUiState(
    val query: String = "",
    val results: List<EventView> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val savedSearches: List<String> = emptyList()
) {
    /** True when the current query is already pinned, so the chip can show "Saved". */
    val queryIsSaved: Boolean
        get() = query.isNotBlank() && savedSearches.any { it.equals(query.trim(), ignoreCase = true) }
}

/**
 * Search (Screen 7): debounced live search across the local catalogue plus
 * recent-search history from DataStore. Empty queries show "popular now".
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val eventRepository: EventRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    private val searchMemory = combine(
        preferences.recentSearches,
        preferences.savedSearches
    ) { recents, saved -> recents to saved }

    val uiState = combine(
        eventRepository.observeAllEvents(),
        queryFlow,
        queryFlow.debounce(250),
        searchMemory,
        eventRepository.observeFavoriteIds()
    ) { events, immediateQuery, searchQuery, memory, favoriteIds ->
        val trimmed = searchQuery.trim()
        val now = System.currentTimeMillis()
        val futureEvents = events.filter { it.endDate > now }
        val filtered = EventFilterer.filter(
            futureEvents,
            query = trimmed.ifBlank { null }
        )
        val sorted = if (trimmed.isBlank()) {
            futureEvents.sortedByDescending { it.attendeeCount }
        } else {
            filtered.sortedBy { it.startDate }
        }
        SearchUiState(
            // Display the exact text the user typed; the debounced copy drives results.
            query = immediateQuery,
            results = EventFilterer.attachDistances(sorted, null, null, favoriteIds),
            recentSearches = memory.first,
            savedSearches = memory.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    fun toggleFavorite(eventId: String) {
        viewModelScope.launch { eventRepository.toggleFavorite(eventId) }
    }

    /** Called when the user commits a search (keyboard action). */
    fun commitSearch() {
        val term = queryFlow.value
        if (term.isNotBlank()) {
            viewModelScope.launch { preferences.addRecentSearch(term) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { preferences.clearRecentSearches() }
    }

    /** Pins the current query so it survives history clears and app restarts. */
    fun saveCurrentSearch() {
        val term = queryFlow.value
        if (term.isNotBlank()) {
            viewModelScope.launch { preferences.saveSearch(term) }
        }
    }

    fun removeSavedSearch(term: String) {
        viewModelScope.launch { preferences.removeSavedSearch(term) }
    }

    /** Tapping a saved search re-runs it and re-timestamps it in recent history. */
    fun applySavedSearch(term: String) {
        queryFlow.value = term
        viewModelScope.launch { preferences.addRecentSearch(term) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(container.eventRepository, container.preferences)
            }
        }
    }
}
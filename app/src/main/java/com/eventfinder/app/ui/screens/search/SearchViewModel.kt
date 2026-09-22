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
    val recentSearches: List<String> = emptyList()
)

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

    val uiState = combine(
        eventRepository.observeAllEvents(),
        queryFlow,
        queryFlow.debounce(250),
        preferences.recentSearches
    ) { events, immediateQuery, searchQuery, recents ->
        val trimmed = searchQuery.trim()
        val filtered = EventFilterer.filter(events, query = trimmed.ifBlank { null })
        val sorted = if (trimmed.isBlank()) {
            filtered.sortedByDescending { it.attendeeCount }
        } else {
            filtered.sortedBy { it.startDate }
        }
        SearchUiState(
            // Display the exact text the user typed; the debounced copy drives results.
            query = immediateQuery,
            results = EventFilterer.attachDistances(sorted, null, null),
            recentSearches = recents
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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(container.eventRepository, container.preferences)
            }
        }
    }
}
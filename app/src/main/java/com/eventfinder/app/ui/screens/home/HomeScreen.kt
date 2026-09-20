package com.eventfinder.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.ui.components.CategoryChips
import com.eventfinder.app.ui.components.EmptyState
import com.eventfinder.app.ui.components.EventCard
import com.eventfinder.app.ui.components.LoadingView
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.LocationUtils

/**
 * Screen 4 & 5 (Home): interactive OpenStreetMap view + scrollable event list
 * with category chips, keyword search and a sort/filter sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    container: AppContainer,
    onEventClick: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container, context.applicationContext)
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Location permission + one-time capture of the last-known position.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LocationUtils.requestCurrentLocation(
                context = context,
                onLocation = { lat, lng -> viewModel.setUserLocation(lat, lng) },
                onUnavailable = {
                    LocationUtils.lastKnown(context)?.let { (lat, lng) ->
                        viewModel.setUserLocation(lat, lng)
                    }
                }
            )
        } else {
            AppLogger.w("HomeScreen", "Location permission denied - using default radius")
        }
    }

    LaunchedEffect(Unit) {
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_events), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconToggleButton(
                        checked = state.isMapView,
                        onCheckedChange = { viewModel.onToggleMapView() }
                    ) {
                        Icon(
                            if (state.isMapView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.Map,
                            contentDescription = stringResource(
                                if (state.isMapView) R.string.list_view else R.string.map_view
                            )
                        )
                    }
                    IconButton(onClick = { viewModel.onShowFilterSheet(true) }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filter))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isOffline) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(R.string.offline_banner),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(4.dp))

            CategoryChips(
                selected = state.selectedCategory,
                onSelect = viewModel::onCategorySelect
            )

            Spacer(Modifier.height(8.dp))

            if (state.isLoading && state.events.isEmpty()) {
                LoadingView(stringResource(R.string.loading_events))
            } else if (state.isMapView) {
                EventMap(
                    events = state.events.map { it.event },
                    center = (state.userLat ?: -26.2041) to (state.userLng ?: 28.0473),
                    onEventClick = onEventClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EventList(
                    state = state,
                    onEventClick = onEventClick,
                    onFavoriteToggle = viewModel::toggleFavorite
                )
            }
        }
    }

    if (state.showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { viewModel.onShowFilterSheet(false) },
            onSortSelect = viewModel::onSortSelect,
            onRadiusChange = viewModel::onRadiusSliderChange,
            onToggleRadius = viewModel::onToggleRadiusFilter
        )
    }
}

/** Scrollable event cards (list view). */
@Composable
private fun EventList(
    state: HomeUiState,
    onEventClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit
) {
    if (state.events.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.EventBusy,
            title = stringResource(R.string.no_events)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.events, key = { it.event.id }) { eventView ->
                EventCard(
                    view = eventView,
                    onClick = { onEventClick(eventView.event.id) },
                    onFavoriteToggle = { onFavoriteToggle(eventView.event.id) }
                )
            }
        }
    }
}

/** Sort / distance filter sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onSortSelect: (EventSort) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onToggleRadius: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sort == EventSort.DATE,
                    onClick = { onSortSelect(EventSort.DATE) },
                    label = { Text(stringResource(R.string.sort_date)) }
                )
                FilterChip(
                    selected = state.sort == EventSort.DISTANCE,
                    onClick = { onSortSelect(EventSort.DISTANCE) },
                    label = { Text(stringResource(R.string.sort_distance)) }
                )
                FilterChip(
                    selected = state.sort == EventSort.NAME,
                    onClick = { onSortSelect(EventSort.NAME) },
                    label = { Text(stringResource(R.string.sort_name)) }
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.events_nearby), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = state.radiusFilterEnabled,
                    onCheckedChange = onToggleRadius
                )
            }
            Text(
                "${state.radiusKm} km",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.End)
            )
            Slider(
                value = state.radiusKm.toFloat(),
                onValueChange = { onRadiusChange(it.toInt()) },
                valueRange = 5f..200f,
                enabled = state.radiusFilterEnabled
            )
        }
    }
}
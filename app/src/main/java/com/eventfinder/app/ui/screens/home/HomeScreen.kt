package com.eventfinder.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.ui.components.CategoryChips
import com.eventfinder.app.ui.components.EmptyState
import com.eventfinder.app.ui.components.EventCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.ui.components.categoryLabel
import com.eventfinder.app.utils.DateTimeUtils
import com.eventfinder.app.ui.components.LoadingView
import com.eventfinder.app.ui.components.ShimmerBox
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.LocationUtils
import com.eventfinder.app.utils.SouthAfricanPlaces
import org.osmdroid.views.MapView

/**
 * Nation-wide default map centre used until the user's place is known.
 *
 * This is deliberately *not* derived from the device language: an English
 * speaker in Durban and an Afrikaans speaker in Cape Town must both get the
 * same sensible starting point. The real centre comes from the device
 * position, or from the "Default city" preference in Settings â€” see
 * [PlaceResolver].
 */
internal fun defaultSouthAfricaCenter(): Pair<Double, Double> {
    val fallback = SouthAfricanPlaces.nationalDefault
    return fallback.latitude to fallback.longitude
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

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

    var userLocation by rememberSaveable { mutableStateOf<Location?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val scope = rememberCoroutineScope()

    // Location permission + one-time capture of the last-known position.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        locationPermissionGranted = granted

        if (granted) {
            LocationUtils.requestCurrentLocation(
                context = context,
                onLocation = { lat, lng ->
                    viewModel.setUserLocation(lat, lng)
                    userLocation = Location("device").apply {
                        latitude = lat
                        longitude = lng
                    }
                },
                onUnavailable = {
                    LocationUtils.lastKnown(context)?.let { (lat, lng) ->
                        viewModel.setUserLocation(lat, lng)
                        userLocation = Location("last_known").apply {
                            latitude = lat
                            longitude = lng
                        }
                    }
                }
            )
        } else {
            AppLogger.w("HomeScreen", "Location permission denied")
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            LocationUtils.requestCurrentLocation(
                context = context,
                onLocation = { lat, lng ->
                    viewModel.setUserLocation(lat, lng)
                    userLocation = Location("device").apply {
                        latitude = lat
                        longitude = lng
                    }
                },
                onUnavailable = {
                    LocationUtils.lastKnown(context)?.let { (lat, lng) ->
                        viewModel.setUserLocation(lat, lng)
                        userLocation = Location("last_known").apply {
                            latitude = lat
                            longitude = lng
                        }
                    }
                }
            )
        }

        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    // Start/stop continuous location tracking based on permission state.
    // Re-runs whenever locationPermissionGranted changes (e.g. after grant).
    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            return@DisposableEffect onDispose { }
        }

        val handle = LocationUtils.requestLocationUpdates(context) { lat, lng ->
            viewModel.setUserLocation(lat, lng)
            userLocation = Location("continuous").apply {
                latitude = lat
                longitude = lng
            }
        }

        onDispose {
            handle?.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Event",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Finder",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "SOUTH AFRICA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                    }                },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                if (state.isOffline) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.offline_banner),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                if (state.lastRefreshAt > 0L) {
                    Text(
                        "Last updated ${((System.currentTimeMillis() - state.lastRefreshAt) / 60000)} min ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text("What are you in the mood for?")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search events"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                Spacer(Modifier.height(4.dp))

                CategoryChips(
                    selected = state.selectedCategory,
                    onSelect = viewModel::onCategorySelect
                )

                Spacer(Modifier.height(8.dp))

                if (state.isLoading && state.events.isEmpty()) {
                    Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) {
                            ShimmerBox(Modifier.fillMaxWidth().height(96.dp))
                        }
                    }
                } else if (state.isMapView) {
                    // weight(1f) keeps the map inside the space left by the search
                    // field and the category chips. fillMaxSize() made the map
                    // overlap them, which hid the chips and clipped the search box.
                    Box(Modifier.weight(1f).clipToBounds()) {
                        val defaultCenter = defaultSouthAfricaCenter()
                        EventMap(
                            events = state.events.map { it.event },
                            center = (state.userLat ?: defaultCenter.first) to
                                (state.userLng ?: defaultCenter.second),
                            userLocation = userLocation,
                            onEventClick = onEventClick,
                            onMapViewCreated = { mapViewRef = it },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Locate Me button
                        FloatingActionButton(
                            onClick = {
                                if (!locationPermissionGranted) {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                    return@FloatingActionButton
                                }

                                val known = userLocation
                                if (known != null) {
                                    mapViewRef?.let { recenterMapOnUser(it, known) }
                                    return@FloatingActionButton
                                }

                                LocationUtils.requestCurrentLocation(
                                    context = context,
                                    onLocation = { lat, lng ->
                                        viewModel.setUserLocation(lat, lng)
                                        val fresh = Location("device").apply {
                                            latitude = lat
                                            longitude = lng
                                        }
                                        userLocation = fresh
                                        mapViewRef?.let { recenterMapOnUser(it, fresh) }
                                    },
                                    onUnavailable = {
                                        AppLogger.w("HomeScreen", "Locate me: location unavailable")
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.location_unavailable)
                                            )
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MyLocation,
                                contentDescription = stringResource(R.string.locate_me)
                            )
                        }
                    }
                } else {
                    EventList(
                        state = state,
                        onEventClick = onEventClick,
                        onFavoriteToggle = viewModel::toggleFavorite,
                        onRetry = viewModel::refresh
                    )
                }
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
private fun ColumnScope.EventList(
    state: HomeUiState,
    onEventClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onRetry: () -> Unit = {}
) {
    if (state.events.isEmpty()) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.no_events),
                subtitle = "Try changing your filters or check again later."
            )

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onRetry) {
                Text("Refresh events")
            }
        }

        return
    }

    val columns =
        if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 2
        else 1

    if (columns == 1) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Featured event",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "A highlight from around South Africa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                FeaturedEventCard(
                    view = state.events.first(),
                    onClick = {
                        onEventClick(state.events.first().event.id)
                    },
                    onFavoriteToggle = {
                        onFavoriteToggle(state.events.first().event.id)
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming events",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "${state.events.size} events",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = state.events.drop(1),
                key = { it.event.id }
            ) { eventView ->
                EventCard(
                    view = eventView,
                    onClick = {
                        onEventClick(eventView.event.id)
                    },
                    onFavoriteToggle = {
                        onFavoriteToggle(eventView.event.id)
                    }
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = state.events,
                key = { it.event.id }
            ) { eventView ->
                EventCard(
                    view = eventView,
                    onClick = {
                        onEventClick(eventView.event.id)
                    },
                    onFavoriteToggle = {
                        onFavoriteToggle(eventView.event.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun FeaturedEventCard(
    view: EventView,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val event = view.event

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = event.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x33000000),
                                Color(0xE6000000)
                            )
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(categoryLabel(event.category)),
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 7.dp
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.38f),
                        RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    imageVector = if (view.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (view.isFavorite) {
                        "Remove from favourites"
                    } else {
                        "Add to favourites"
                    },
                    tint = if (view.isFavorite) {
                        Color(0xFFFF6B6B)
                    } else {
                        Color.White
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(Modifier.width(5.dp))

                    Text(
                        text = event.venueName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = DateTimeUtils.formatShortDate(event.startDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f)
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
                stringResource(R.string.radius_value, state.radiusKm),
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







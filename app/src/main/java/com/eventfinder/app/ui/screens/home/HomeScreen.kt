package com.eventfinder.app.ui.screens.home

import com.eventfinder.app.ui.theme.LocalAccentText
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.RouteOption
import com.eventfinder.app.data.repository.TravelMode
import com.eventfinder.app.data.repository.formatRouteDistance
import com.eventfinder.app.data.repository.formatRouteDuration
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.domain.model.TimeWindow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.eventfinder.app.ui.components.CategoryChips
import com.eventfinder.app.ui.components.EmptyState
import com.eventfinder.app.ui.components.EventCard
import com.eventfinder.app.ui.components.FavoritePngIcon
import com.eventfinder.app.ui.components.InteractivePngIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
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
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class
)
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    container: AppContainer,
    onEventClick: (String) -> Unit,
    onSeeAll: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container, context.applicationContext)
    )
    val state by viewModel.uiState.collectAsState()
    val themeMode by container.preferences.themeMode.collectAsState(initial = "system")
    val snackbarHostState = remember { SnackbarHostState() }

    var userLocation by rememberSaveable { mutableStateOf<Location?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    // Google Maps-style routing: blue route lines + info panel over the map.
    val routeState by viewModel.route.collectAsState()
    // Showcase map: pin taps select an event; the floating card opens it.
    var selectedEventId by remember { mutableStateOf<String?>(null) }
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
                                color = LocalAccentText.current,
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
                    // Showcase map screen: quick Map/List switch and the
                    // Filter Events modal, both as full-colour header PNGs.
                    IconButton(onClick = viewModel::onToggleMapView) {
                        Image(
                            painter = painterResource(
                                if (state.isMapView) R.drawable.hdr_list else R.drawable.hdr_map
                            ),
                            contentDescription = if (state.isMapView) "List view" else "Map view",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.onShowFilterSheet(true) }) {
                        Image(
                            painter = painterResource(R.drawable.hdr_filters),
                            contentDescription = "Filter events",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    ThemeModeToggle(
                        themeMode = themeMode,
                        onSelect = { mode ->
                            scope.launch { container.preferences.setThemeMode(mode) }
                        }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = state.isRefreshing,
            onRefresh = viewModel::refresh
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
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

                LocationSelectorPill(
                    label = state.cityLabel,
                    onCitySelected = viewModel::selectCity,
                    onUseGps = viewModel::useCurrentLocation,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                if (state.isRefreshing) {
                    // Sync status is visible instead of silent: the user can
                    // tell a pull-to-refresh or background sync is in flight.
                    Text(
                        "Syncing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAccentText.current,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                } else if (state.lastRefreshAt > 0L) {
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
                        Image(
                            painter = painterResource(R.drawable.evt_search),
                            contentDescription = "Search events",
                            modifier = Modifier.size(22.dp)
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
                            onEventSelect = { id -> selectedEventId = id },
                            onMapViewCreated = { mapViewRef = it },
                            routeLines = routeState?.routes?.map { it.points }.orEmpty(),
                            selectedRouteIndex = routeState?.selectedRouteIndex ?: 0,
                            // Tapping the blue line re-fits the camera to it.
                            onRouteTap = {
                                mapViewRef?.let { map ->
                                    routeState?.routes
                                        ?.getOrNull(routeState?.selectedRouteIndex ?: 0)
                                        ?.let { fitMapToRoute(map, it) }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Frame the whole route whenever a new route (or a
                        // different alternative/mode) becomes available.
                        LaunchedEffect(
                            routeState?.routes,
                            routeState?.selectedRouteIndex
                        ) {
                            val map = mapViewRef ?: return@LaunchedEffect
                            val active = routeState?.routes
                                ?.getOrNull(routeState?.selectedRouteIndex ?: 0)
                                ?: return@LaunchedEffect
                            map.post { fitMapToRoute(map, active) }
                        }

                        val selectedView = state.events
                            .firstOrNull { it.event.id == selectedEventId }
                            ?: state.events.firstOrNull()

                        // Showcase zoom controls on the right edge.
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MapCircleButton(
                                icon = Icons.Outlined.Add,
                                contentDescription = "Zoom in",
                                onClick = { mapViewRef?.controller?.zoomIn() }
                            )
                            MapCircleButton(
                                icon = Icons.Outlined.Remove,
                                contentDescription = "Zoom out",
                                onClick = { mapViewRef?.controller?.zoomOut() }
                            )
                            // Recenter route: visible only while a route is up.
                            if (routeState != null) {
                                val active = routeState?.routes
                                    ?.getOrNull(routeState?.selectedRouteIndex ?: 0)
                                MapCircleButton(
                                    icon = Icons.Outlined.Navigation,
                                    contentDescription = stringResource(R.string.route_recenter),
                                    enabled = active != null,
                                    onClick = {
                                        active?.let { route -> mapViewRef?.let { fitMapToRoute(it, route) } }
                                    }
                                )
                            }
                        }

                        // Route panel replaces the event card while
                        // directions are open (spec: destination, distance,
                        // ETA, mode, start navigation, close).
                        if (routeState != null) {
                            RoutePanel(
                                state = routeState!!,
                                onModeChange = viewModel::changeRouteMode,
                                onRetry = viewModel::retryRoute,
                                onClose = {
                                    viewModel.clearRoute()
                                    selectedEventId = null
                                },
                                onStartNavigation = { route ->
                                    openNavigation(
                                        context = context,
                                        title = routeState!!.destinationTitle,
                                        lat = routeState!!.destinationLat,
                                        lng = routeState!!.destinationLng,
                                        mode = routeState!!.mode
                                    )
                                },
                                onSelectAlternative = viewModel::selectRouteOption,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        } else {
                            // Floating showcase card + View Details, kept clear of
                            // the locate-me FAB in the bottom-right corner.
                            selectedView?.let { selected ->
                                MapEventCard(
                                    view = selected,
                                    onOpen = {
                                        onEventClick(selected.event.id)
                                    },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(selected.event.id)
                                    },
                                    onRouteClick = {
                                        viewModel.requestRouteTo(selected.event)
                                    },
                                    onDirections = {
                                        openDirections(context, selected.event)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 84.dp, bottom = 16.dp)
                                )
                            }
                        }

                        // Locate Me button (hidden while a route panel owns
                        // the bottom-right corner so nothing overlaps).
                        if (routeState == null) FloatingActionButton(
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
                        onRetry = viewModel::refresh,
                        onSeeAll = onSeeAll,
                        onNearMe = viewModel::onNearMeNow,
                        onTrendingWeekend = viewModel::onTrendingWeekend,
                        onCollectionSelect = viewModel::onCollectionSelect
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (state.showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { viewModel.onShowFilterSheet(false) },
            onSortSelect = viewModel::onSortSelect,
            onApply = viewModel::applyFilters
        )
    }
}

/** Scrollable event cards (list view). */
@Composable
private fun ColumnScope.EventList(
    state: HomeUiState,
    onEventClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onRetry: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onNearMe: () -> Unit = {},
    onTrendingWeekend: () -> Unit = {},
    onCollectionSelect: (String) -> Unit = {}
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
                        tint = LocalAccentText.current
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

            // Smart Discovery — showcase section between featured and upcoming.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Discovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "See All  \u2192",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAccentText.current,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onSeeAll)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }

            item {
                val imageForNearMe = state.events
                    .mapNotNull { view -> view.event.imageUrl?.let { it to view } }
                    .minByOrNull { it.second.distanceKm ?: Double.MAX_VALUE }
                    ?.first
                // Prefer a *different* event for the trending card so the two
                // thumbnails do not repeat when every attendee count ties.
                val trendingEvent = state.events
                    .filter { it.event.imageUrl != null && it.event.imageUrl != imageForNearMe }
                    .maxByOrNull { it.event.attendeeCount }
                    ?: state.events.firstOrNull { it.event.imageUrl != null }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        DiscoveryCard(
                            title = "Near Me Now",
                            subtitle = "Events nearby",
                            imageUrl = imageForNearMe
                                ?: state.events.firstOrNull()?.event?.imageUrl,
                            imageDescription = "Nearest upcoming event",
                            icon = Icons.Outlined.LocationOn,
                            active = state.sort == EventSort.DISTANCE && state.radiusFilterEnabled,
                            onClick = onNearMe
                        )
                    }
                    item {
                        DiscoveryCard(
                            title = "Trending This Weekend",
                            subtitle = "Top picks",
                            imageUrl = trendingEvent?.event?.imageUrl,
                            imageDescription = trendingEvent?.event?.let { "Trending event: ${it.title}" }
                                ?: "Trending event",
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                            active = state.weekendOnly,
                            onClick = onTrendingWeekend
                        )
                    }
                }
            }

            // Curated collections — one-tap filter presets over the catalogue.
            if (state.collections.isNotEmpty()) {
                item {
                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            items = state.collections,
                            key = { it.collection.id }
                        ) { chip ->
                            val selected = state.activeCollectionId == chip.collection.id
                            FilterChip(
                                selected = selected,
                                onClick = { onCollectionSelect(chip.collection.id) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedLabelColor = LocalAccentText.current
                                ),
                                label = {
                                    Text(
                                        text = "${chip.collection.title}  ${chip.count}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
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

/**
 * Showcase floating map card: event photo, title, date • distance, rating and
 * the favourite heart, with the green *View Details* button beneath it.
 */
@Composable
private fun MapEventCard(
    view: EventView,
    onOpen: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRouteClick: () -> Unit = {},
    onDirections: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val event = view.event
    Column(modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = event.imageUrl,
                        contentDescription = "${stringResource(R.string.event_image)}: ${event.title}",
                        placeholder = painterResource(R.drawable.ic_image_placeholder),
                        error = painterResource(R.drawable.ic_image_placeholder),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(DateTimeUtils.formatShortDate(event.startDate))
                            view.distanceKm?.let { d ->
                                append(" • ")
                                append("%.1f km".format(d))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    view.averageRating?.let { avg ->
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.evt_rating),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (view.reviewCount > 0) {
                                    "${"%.1f".format(avg)} (${formatReviewCount(view.reviewCount)})"
                                } else {
                                    "%.1f".format(avg)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                FavoritePngIcon(
                    isFavorite = view.isFavorite,
                    onToggle = onFavoriteToggle,
                    size = 26.dp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("View Details  \u2192", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        // Directions row: draws the real blue route on the map, or hands the
        // venue to an installed maps app.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRouteClick,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.route_show),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = onDirections,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.directions),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Google Maps-inspired route panel: destination, distance, ETA, travel-mode
 * chips, alternative-route selector, Start navigation, recenter and close.
 * Renders the spec's loading, available, cached, alternative and unavailable
 * states over the `#0B1929` surface with `#4285F4` route accents.
 */
@Composable
private fun RoutePanel(
    state: RouteUiState,
    onModeChange: (TravelMode) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onStartNavigation: (RouteOption) -> Unit,
    onSelectAlternative: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state.routes.getOrNull(state.selectedRouteIndex)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Directions,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.destinationTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            state.isLoading -> stringResource(R.string.route_calculating)
                            state.hasError -> stringResource(R.string.route_unavailable)
                            active != null && state.fromCache -> stringResource(R.string.route_cached)
                            active != null ->
                                "${formatRouteDistance(active.distanceMeters)} · " +
                                    formatRouteDuration(active.durationSeconds)
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.route_close),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Travel-mode chips: driving / walking / cycling.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteModeChip(
                    mode = TravelMode.DRIVING,
                    selected = state.mode == TravelMode.DRIVING,
                    icon = Icons.Outlined.DirectionsCar,
                    label = stringResource(R.string.route_mode_drive),
                    onSelect = onModeChange
                )
                RouteModeChip(
                    mode = TravelMode.WALKING,
                    selected = state.mode == TravelMode.WALKING,
                    icon = Icons.Outlined.DirectionsWalk,
                    label = stringResource(R.string.route_mode_walk),
                    onSelect = onModeChange
                )
                RouteModeChip(
                    mode = TravelMode.CYCLING,
                    selected = state.mode == TravelMode.CYCLING,
                    icon = Icons.Outlined.DirectionsBike,
                    label = stringResource(R.string.route_mode_cycle),
                    onSelect = onModeChange
                )
            }

            when {
                state.isLoading -> {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF4285F4)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.route_calculating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.hasError -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.route_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                active != null -> {
                    // Alternative-route selector: primary blue, others muted.
                    if (state.routes.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            state.routes.forEachIndexed { index, option ->
                                val selected = index == state.selectedRouteIndex
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFF4285F4).copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.clickable { onSelectAlternative(index) }
                                ) {
                                    Text(
                                        text = "${index + 1} · ${formatRouteDuration(option.durationSeconds)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color(0xFF4285F4)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onStartNavigation(active) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.route_start_navigation),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Travel-mode chip for the route panel (48dp touch target). */
@Composable
private fun RouteModeChip(
    mode: TravelMode,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onSelect: (TravelMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF4285F4).copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .height(48.dp)
            .clickable { onSelect(mode) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Fits the camera around the whole route with breathing room for the panel. */
private fun fitMapToRoute(map: MapView, route: RouteOption) {
    if (route.points.size < 2) return
    val lats = route.points.map { it.lat }
    val lngs = route.points.map { it.lng }
    val box = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())
    runCatching {
        map.zoomToBoundingBox(box, false, 96)
    }.onFailure { AppLogger.w("EventMap", "Route camera fit failed: ${it.message}") }
}

/**
 * Hands the destination to an installed maps app in navigation mode
 * (Google Maps style: driving/walking/bicycling deep link).
 */
private fun openNavigation(
    context: Context,
    title: String,
    lat: Double,
    lng: Double,
    mode: TravelMode
) {
    val label = android.net.Uri.encode(title)
    val candidates = listOf(
        android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("google.navigation:q=$lat,$lng&mode=${mode.googleMapsMode.first()}")
        ),
        android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=${mode.googleMapsMode}"
            )
        ),
        android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
        )
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // try the next candidate
        }
    }
}

/** Opens an installed maps app for directions to [event] (no route drawn). */
private fun openDirections(
    context: Context,
    event: com.eventfinder.app.domain.model.Event
) {
    val latitude = event.latitude ?: return
    val longitude = event.longitude ?: return
    openNavigation(
        context = context,
        title = event.venueName,
        lat = latitude,
        lng = longitude,
        mode = TravelMode.DRIVING
    )
}

/** "4.8 (2.5k)"-style review counts for the floating card. */
private fun formatReviewCount(count: Int): String =
    if (count >= 1000) "%.1fk".format(count / 1000.0) else count.toString()

/** Circular zoom button for the map's right edge (showcase +/− controls). */
@Composable
private fun MapCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Smart Discovery thumbnail (showcase design): event photo or icon, title and
 * subtitle. Tapping applies the card's discovery mode — Near Me sorts by
 * distance with the radius filter on, Trending toggles the weekend window —
 * and [active] paints the card in its enabled state.
 */
@Composable
private fun DiscoveryCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    imageDescription: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = imageDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = LocalAccentText.current,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Header theme switch: full-colour sun and moon PNGs in a pill.
 * Tapping a half switches light/dark mode; the active half scales up and brightens.
 */
@Composable
private fun ThemeModeToggle(
    themeMode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemDark = isSystemInDarkTheme()
    val darkActive = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InteractivePngIcon(
            iconRes = R.drawable.hdr_sun,
            contentDescription = "Light mode",
            active = !darkActive,
            dimWhenInactive = true,
            size = 22.dp,
            onToggle = { onSelect("light") }
        )
        Spacer(Modifier.width(4.dp))
        InteractivePngIcon(
            iconRes = R.drawable.hdr_moon,
            contentDescription = "Dark mode",
            active = darkActive,
            dimWhenInactive = true,
            size = 22.dp,
            onToggle = { onSelect("dark") }
        )
    }
}

/** Header location selector: full-colour pin + chevron opening the SA city list. */
@Composable
private fun LocationSelectorPill(
    label: String,
    onCitySelected: (String) -> Unit,
    onUseGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.hdr_map_pin),
                contentDescription = "Location: $label",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(5.dp))
            Image(
                painter = painterResource(R.drawable.hdr_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.location_use_gps)) },
                onClick = {
                    expanded = false
                    onUseGps()
                }
            )
            HorizontalDivider()
            SouthAfricanPlaces.all
                .filter { it.name != "Cape Town CBD" && it.name != "Nelspruit" }
                .forEach { place ->
                    DropdownMenuItem(
                        text = { Text("${place.name}, ZA") },
                        onClick = {
                            expanded = false
                            onCitySelected(place.name)
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

            // Red FEATURED pill, matching the reference showcase design.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error
            ) {
                Text(
                    text = "FEATURED",
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
                FavoritePngIcon(
                    isFavorite = view.isFavorite,
                    onToggle = onFavoriteToggle,
                    size = 26.dp,
                    outlineRes = R.drawable.heart_outline_white
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

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeUtils.formatShortDate(event.startDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Text(
                        text = "View Details  \u2192",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Showcase "Filter Events" modal: When? windows, minimum rating, sort chips,
 * Verified Organiser / Accessible Events / Near Me Now toggles and the
 * Apply Filters + Clear All actions. Sort chips apply live; the rest are
 * staged until Apply so "Apply Filters" means what it says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onSortSelect: (EventSort) -> Unit,
    onApply: (TimeWindow, Int, Boolean, Boolean, Boolean, Int) -> Unit
) {
    var window by remember { mutableStateOf(state.timeWindow) }
    var minRating by remember { mutableStateOf(state.minRating) }
    var verifiedOnly by remember { mutableStateOf(state.verifiedOnly) }
    var accessibleOnly by remember { mutableStateOf(state.accessibleOnly) }
    var nearMe by remember { mutableStateOf(state.radiusFilterEnabled) }
    var radius by remember { mutableStateOf(state.radiusKm) }
    var ratingMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Filter Events",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.usr_close),
                        contentDescription = "Close filters",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "When?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = window == TimeWindow.ANYTIME,
                    onClick = { window = TimeWindow.ANYTIME },
                    label = { Text("Anytime") }
                )
                FilterChip(
                    selected = window == TimeWindow.TODAY,
                    onClick = { window = TimeWindow.TODAY },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = window == TimeWindow.WEEKEND,
                    onClick = { window = TimeWindow.WEEKEND },
                    label = { Text("This Weekend") }
                )
                FilterChip(
                    selected = window == TimeWindow.NEXT_WEEK,
                    onClick = { window = TimeWindow.NEXT_WEEK },
                    label = { Text("Next Week") }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Rating",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { index ->
                    Image(
                        painter = painterResource(R.drawable.evt_rating),
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(if (index < minRating) 1f else 0.25f)
                    )
                    if (index < 4) Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { ratingMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (minRating == 0) "Any rating" else "$minRating+ stars",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalAccentText.current
                        )
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(R.drawable.hdr_chevron_down),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = ratingMenuExpanded,
                        onDismissRequest = { ratingMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any rating") },
                            onClick = {
                                minRating = 0
                                ratingMenuExpanded = false
                            }
                        )
                        for (rating in 2..5) {
                            DropdownMenuItem(
                                text = { Text("$rating stars or more") },
                                onClick = {
                                    minRating = rating
                                    ratingMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Verified Organiser",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = verifiedOnly, onCheckedChange = { verifiedOnly = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Accessible Events",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = accessibleOnly, onCheckedChange = { accessibleOnly = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Near Me Now",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = nearMe, onCheckedChange = { nearMe = it })
            }
            if (nearMe) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.radius_value, radius),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
                Slider(
                    value = radius.toFloat(),
                    onValueChange = { radius = it.toInt().coerceIn(5, 200) },
                    valueRange = 5f..200f,
                    enabled = nearMe
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onApply(window, minRating, verifiedOnly, accessibleOnly, nearMe, radius)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    onApply(TimeWindow.ANYTIME, 0, false, false, false, state.radiusKm)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All")
            }
        }
    }
}







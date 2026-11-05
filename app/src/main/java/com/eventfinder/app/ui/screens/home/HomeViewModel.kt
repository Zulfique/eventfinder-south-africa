package com.eventfinder.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.EventCollection
import com.eventfinder.app.domain.model.EventFilterer
import com.eventfinder.app.domain.model.EventSort
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.domain.model.TimeWindow
import com.eventfinder.app.data.repository.RatingSummary
import com.eventfinder.app.data.repository.RouteOption
import com.eventfinder.app.data.repository.RouteRepository
import com.eventfinder.app.data.repository.TravelMode
import com.eventfinder.app.domain.model.EventAlertDetector.Alerts
import com.eventfinder.app.domain.model.EventAlertDetector.detect
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DistanceCalculator
import com.eventfinder.app.utils.LocationUtils
import com.eventfinder.app.utils.SouthAfricanPlaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@Suppress("unused")
@OptIn(ExperimentalCoroutinesApi::class)
data class HomeUiState(
    val events: List<EventView> = emptyList(),
    val isLoading: Boolean = true,
    /** True only while a user-initiated pull-to-refresh is running. */
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val lastRefreshAt: Long = 0L,
    val selectedCategory: EventCategory? = null,
    val query: String = "",
    val sort: EventSort = EventSort.DATE,
    val radiusKm: Int = 50,
    val radiusFilterEnabled: Boolean = false,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val isMapView: Boolean = false,
    val showFilterSheet: Boolean = false,
    /** Header location pill: "<City>, ZA" — defaults to the national centre (Cape Town). */
    val cityLabel: String = "${SouthAfricanPlaces.nationalDefault.name}, ZA",
    /** Smart Discovery "Trending This Weekend" card is active. */
    val weekendOnly: Boolean = false,
    /** Filter Events modal state (showcase). */
    val timeWindow: TimeWindow = TimeWindow.ANYTIME,
    val minRating: Int = 0,
    val verifiedOnly: Boolean = false,
    val accessibleOnly: Boolean = false,
    /** Curated collections shown as a rail on Home, with current match counts. */
    val collections: List<CollectionChip> = emptyList(),
    /** Id of the active collection ([EventCollection.id]), null when none. */
    val activeCollectionId: String? = null
)

/** A curated collection plus how many upcoming events it currently holds. */
data class CollectionChip(
    val collection: EventCollection,
    val count: Int
)

/**
 * Map route-panel state: real road routes between the user's location and
 * the selected event, covering the routing spec's loading, available,
 * alternative, offline-cache and unavailable states.
 */
data class RouteUiState(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val fromCache: Boolean = false,
    val destinationEventId: String = "",
    val destinationTitle: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val mode: TravelMode = TravelMode.DRIVING,
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteIndex: Int = 0
)

/**
 * Home screen logic (Screens 4 & 5): live event catalogue, category + keyword +
 * radius filtering (FR-02), proximity sorting and the map/list toggle.
 */
class HomeViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val eventRepository = container.eventRepository
    private val eventDiscoveryRepository = container.eventDiscoveryRepository
    private val preferences = container.preferences
    private val networkMonitor = container.networkMonitor

    private val _uiState = MutableStateFlow(HomeUiState())
    private val _messages = MutableSharedFlow<UiMessage>()

    val uiState: StateFlow<HomeUiState> = _uiState
    val messages: kotlinx.coroutines.flow.SharedFlow<UiMessage> = _messages

    private val collectionFlow = MutableStateFlow<String?>(null)

    private val _previousEvents = MutableStateFlow<List<EventView>>(emptyList())
    val previousEvents: StateFlow<List<EventView>> = _previousEvents.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val categoryFlow = MutableStateFlow<EventCategory?>(null)
    private val sortFlow = MutableStateFlow(EventSort.DATE)
    private val radiusFlow = MutableStateFlow(50)
    private val radiusEnabledFlow = MutableStateFlow(false)
    private val locationFlow = MutableStateFlow<Pair<Double, Double>?>(null)
    private val timeWindowFlow = MutableStateFlow(TimeWindow.ANYTIME)
    private val minRatingFlow = MutableStateFlow(0)
    private val verifiedOnlyFlow = MutableStateFlow(false)
    private val accessibleOnlyFlow = MutableStateFlow(false)

    private val reviewRepository = container.eventReviewRepository

    /**
     * Settings that previously only existed as screens: interests rank the
     * feed, commute time caps how far the radius filter reaches, and the
     * accessibility preference picks the accessible-first ordering.
     */
    private data class Personalisation(
        val interests: Set<String> = emptySet(),
        val commuteMinutes: Int = 30,
        val commuteMode: String = "car",
        val accessibilityFirst: Boolean = false
    )

    private val personalisationFlow = combine(
        preferences.interests,
        preferences.commuteMinutes,
        preferences.commuteMode,
        preferences.accessibilityFirstSorting
    ) { interests, minutes, mode, accessibilityFirst ->
        Personalisation(interests, minutes, mode, accessibilityFirst)
    }

    /** True while an explicit city from the header selector overrides the GPS fix. */
    private var manualCityActive = false

    private data class Combined(
        val events: List<Event>,
        val rsvps: Map<String, RsvpStatus>,
        val favoriteIds: Set<String>,
        val ratings: Map<String, RatingSummary>
    )

    private data class Controls(
        val query: String,
        val category: EventCategory?,
        val sort: EventSort,
        val radiusKm: Int,
        val radiusEnabled: Boolean,
        val location: Pair<Double, Double>?,
        val timeWindow: TimeWindow,
        val minRating: Int,
        val verifiedOnly: Boolean,
        val accessibleOnly: Boolean,
        val collectionId: String? = null,
        val interests: Set<String> = emptySet(),
        val commuteMinutes: Int = 30,
        val commuteMode: String = "car",
        val accessibilityFirst: Boolean = false
    )

    init {
        viewModelScope.launch {
            preferences.defaultRadiusKm.first().let { radiusFlow.value = it }
        }

        // Restore the header location selector's saved city.
        viewModelScope.launch {
            preferences.selectedCity.first()?.let { city ->
                manualCityActive = true
                _uiState.update { it.copy(cityLabel = "$city, ZA") }
            }
        }

        // Seed demo events, then attempt to discover public JSON feeds when online.
        viewModelScope.launch {
            val online = networkMonitor.isCurrentlyOnline()
            _uiState.update { it.copy(isOffline = !online) }

            eventRepository.ensureSeeded()

            // Surface expiry instead of silently shrinking the offline
            // catalogue: the user learns why older events disappeared.
            val expired = runCatching { eventRepository.expireStaleEvents() }.getOrDefault(0)
            if (expired > 0) {
                _messages.emit(
                    UiMessage.Literal(
                        "$expired expired event${if (expired == 1) "" else "s"} removed from the local cache"
                    )
                )
            }

            if (online) {
                runCatching { eventDiscoveryRepository.refresh() }.onFailure {
                    AppLogger.e("HomeViewModel", "Event discovery failed", it)
                }
            }
        }

        val combinedFlow = combine(
            eventRepository.observeAllEvents(),
            eventRepository.observeRsvpStatuses(),
            eventRepository.observeFavoriteIds(),
            reviewRepository.observeAllRatingSummaries()
        ) { events, rsvps, favoriteIds, ratings ->
            Combined(events, rsvps, favoriteIds, ratings)
        }

        val textControlsFlow = combine(queryFlow, categoryFlow, sortFlow) { query, category, sort ->
            Triple(query, category, sort)
        }
        val rangeControlsFlow = combine(radiusFlow, radiusEnabledFlow, locationFlow) { radius, enabled, location ->
            Triple(radius, enabled, location)
        }
        val filtersFlow = combine(
            timeWindowFlow, minRatingFlow, verifiedOnlyFlow, accessibleOnlyFlow
        ) { window, rating, verified, accessible ->
            FilterDraft(window, rating, verified, accessible)
        }
        val baseControlsFlow =
            combine(textControlsFlow, rangeControlsFlow, filtersFlow) { text, range, filters ->
                Controls(
                    text.first, text.second, text.third,
                    range.first, range.second, range.third,
                    filters.window, filters.minRating, filters.verified, filters.accessible
                )
            }
        val controlsFlow = combine(
            baseControlsFlow,
            collectionFlow,
            personalisationFlow
        ) { controls, collectionId, personalisation ->
            controls.copy(
                collectionId = collectionId,
                interests = personalisation.interests,
                commuteMinutes = personalisation.commuteMinutes,
                commuteMode = personalisation.commuteMode,
                accessibilityFirst = personalisation.accessibilityFirst
            )
        }

        viewModelScope.launch {
            combinedFlow.combine(controlsFlow) { combined, controls ->
                applyControls(combined, controls) to collectionChips(combined.events)
            }.collect { (views, chips) ->
                _uiState.value = _uiState.value.copy(
                    events = views,
                    collections = chips,
                    isLoading = false,
                    lastRefreshAt = System.currentTimeMillis()
                )
            }
        }

        // Wire up new event / favourite updated alerts (FR-04)
        viewModelScope.launch {
            combine(
                eventRepository.observeAllEvents(),
                eventRepository.observeFavoriteIds()
            ) { events, favoriteIds ->
                events to favoriteIds
            }.collect { (events, favoriteIds) ->

                val previous = _previousEvents.value
                    .map { it.event }
                    .associateBy { it.id }


                val alerts = detect(
                    previous = previous,
                    current = events,
                    favoriteIds = favoriteIds,
                    now = System.currentTimeMillis()
                )


                if (!alerts.isEmpty) {
                    // The Smart notifications switches gate this: "Nearby
                    // events" covers new events in the feed, "Personalised
                    // picks" covers updates to favourited events.
                    val nearbyEnabled = preferences.notifyNearbyEvents.first()
                    val picksEnabled = preferences.notifyPersonalisedPicks.first()
                    if (nearbyEnabled || picksEnabled) {
                        NotificationHelper.postEventAlerts(
                            context = appContext,
                            newEvents = if (nearbyEnabled) alerts.newEvents else emptyList(),
                            updatedFavorites = if (picksEnabled) alerts.updatedFavorites else emptyList()
                        )
                    }
                }


                _previousEvents.value = events.map { EventView(it) }
            }
        }
    }

    private data class FilterDraft(
        val window: TimeWindow,
        val minRating: Int,
        val verified: Boolean,
        val accessible: Boolean
    )

    private fun applyControls(combined: Combined, c: Controls): List<EventView> {
        val userLat = c.location?.first
        val userLng = c.location?.second

        val now = System.currentTimeMillis()

        val upcomingEvents =
            combined.events.filter { event ->
                event.endDate > now
            }

        // Showcase "When?" window (Smart Discovery's Trending card reuses WEEKEND).
        // Curated collection narrows the feed before the user filters run.
        val collection = EventCollection.byId(c.collectionId)
        val collectionScoped = if (collection == null) {
            upcomingEvents
        } else {
            upcomingEvents.filter { collection.matches(it, now) }
        }

        val inScope = when (c.timeWindow) {
            TimeWindow.ANYTIME -> collectionScoped
            TimeWindow.TODAY -> collectionScoped.filter { it.startDate <= endOfTodayMillis(now) }
            TimeWindow.WEEKEND -> {
                val weekend = upcomingWeekendWindow(now)
                collectionScoped.filter { it.startDate in weekend }
            }
            TimeWindow.NEXT_WEEK -> collectionScoped.filter { it.startDate <= now + 7 * 86_400_000L }
        }

        val scoped = inScope.filter { event ->
            (!c.verifiedOnly || event.organizerIsVerified) &&
                (!c.accessibleOnly || event.wheelchairAccessible || event.sensoryFriendly)
        }

        val filtered = EventFilterer.filter(
            events = scoped,
            query = c.query.ifBlank { null },
            category = c.category,
            userLat = userLat,
            userLng = userLng,
            radiusKm = if (c.radiusEnabled) c.radiusKm else 0
        )
        // The accessibility-first preference picks the accessible-first order
        // unless the user explicitly chose another sort on this screen.
        val effectiveSort = if (c.accessibilityFirst && c.sort == EventSort.DATE) {
            EventSort.ACCESSIBILITY
        } else {
            c.sort
        }
        val sorted = EventFilterer.sort(filtered, effectiveSort, userLat, userLng)
        val withDistance = EventFilterer.attachDistances(sorted, userLat, userLng, combined.favoriteIds)

        // "Max 30 min commute" caps the radius filter rather than being a dead
        // setting. It only applies while the radius filter itself is on, so a
        // national browse is never silently cut down to the neighbourhood.
        val reachable = if (c.radiusEnabled && userLat != null && userLng != null) {
            val maxKm = commuteReachKm(c.commuteMinutes, c.commuteMode)
            withDistance.filter { (it.distanceKm ?: 0.0) <= maxKm }
        } else {
            withDistance
        }

        val views = reachable.map { view ->
            val rating = combined.ratings[view.event.id]
            view.copy(
                rsvpStatus = combined.rsvps[view.event.id],
                averageRating = rating?.average,
                reviewCount = rating?.count ?: 0
            )
        }

        // Interests are a stable boost: the chosen sort still decides the order
        // inside "matches your interests" and "everything else".
        val ranked = if (c.interests.isEmpty()) {
            views
        } else {
            views.sortedByDescending { it.event.category.labelKey in c.interests }
        }

        // Showcase "Rating" filter runs on the enriched views.
        return if (c.minRating > 0) {
            ranked.filter { (it.averageRating ?: 0.0) >= c.minRating }
        } else ranked
    }

    /** Urban travel speeds used to turn "max commute minutes" into kilometres. */
    private fun commuteReachKm(minutes: Int, mode: String): Double {
        val kmPerHour = when (mode.lowercase()) {
            "walking" -> 5.0
            "cycling" -> 15.0
            "transit" -> 20.0
            else -> 40.0
        }
        return (minutes.coerceIn(5, 180) / 60.0) * kmPerHour
    }

    /**
     * Upcoming-event counts for each curated collection. Empty collections are
     * dropped so the rail never shows a dead end.
     */
    private fun collectionChips(events: List<Event>): List<CollectionChip> {
        val now = System.currentTimeMillis()
        val upcoming = events.filter { it.endDate > now }
        return EventCollection.curated.mapNotNull { collection ->
            val count = upcoming.count { collection.matches(it, now) }
            if (count == 0) null else CollectionChip(collection, count)
        }
    }

    /** Activate a curated collection, or clear it when the same chip is tapped. */
    fun onCollectionSelect(id: String?) {
        val next = if (collectionFlow.value == id) null else id
        collectionFlow.value = next
        _uiState.value = _uiState.value.copy(activeCollectionId = next)
    }

    /** Last millisecond of the calendar day containing [now]. */
    private fun endOfTodayMillis(now: Long): Long =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - 1

    fun onQueryChange(value: String) {
        queryFlow.value = value
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun onCategorySelect(category: EventCategory?) {
        categoryFlow.value = category
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun onSortSelect(sort: EventSort) {
        sortFlow.value = sort
        _uiState.value = _uiState.value.copy(sort = sort)
    }

    fun onRadiusSliderChange(km: Int) {
        val value = km.coerceIn(5, 200)
        radiusFlow.value = value
        _uiState.value = _uiState.value.copy(radiusKm = value)
    }

    fun onToggleRadiusFilter(enabled: Boolean) {
        radiusEnabledFlow.value = enabled
        _uiState.value = _uiState.value.copy(radiusFilterEnabled = enabled)
        if (enabled) {
            // Remember the radius the user settled on so the default is
            // restored next session (previously the value silently reset).
            viewModelScope.launch { preferences.setDefaultRadiusKm(radiusFlow.value) }
        }
    }

    fun onToggleMapView() {
        _uiState.value = _uiState.value.copy(isMapView = !_uiState.value.isMapView)
        AppLogger.d("HomeViewModel", "Map view toggled -> ${_uiState.value.isMapView}")
    }

    // ------------------------------------------------------------------ route

    private val routeRepository: RouteRepository = container.routeRepository
    private val _route = MutableStateFlow<RouteUiState?>(null)

    /** Current map route (null = normal event discovery map). */
    val route: StateFlow<RouteUiState?> = _route.asStateFlow()

    private var routeJob: kotlinx.coroutines.Job? = null

    /**
     * Request a real road route from the user's location to [event]'s venue.
     * The panel enters its loading state immediately; a missing location or a
     * provider/cache failure surfaces the spec's message + retry states.
     */
    fun requestRouteTo(event: Event) {
        val destLat = event.latitude
        val destLng = event.longitude
        val originLat = _uiState.value.userLat
        val originLng = _uiState.value.userLng
        if (destLat == null || destLng == null || originLat == null || originLng == null) {
            viewModelScope.launch { _messages.emit(UiMessage.Resource(R.string.route_location_needed)) }
            return
        }
        _route.value = RouteUiState(
            isLoading = true,
            destinationEventId = event.id,
            destinationTitle = event.title,
            destinationLat = destLat,
            destinationLng = destLng,
            mode = TravelMode.DRIVING
        )
        fetchRoute()
    }

    /** Travel mode changed: refetch the route for the same destination. */
    fun changeRouteMode(mode: TravelMode) {
        val current = _route.value ?: return
        if (current.mode == mode && !current.hasError) return
        _route.update {
            it?.copy(
                mode = mode,
                isLoading = true,
                hasError = false,
                fromCache = false,
                routes = emptyList(),
                selectedRouteIndex = 0
            )
        }
        fetchRoute()
    }

    /** Pick an alternative route (index into [RouteUiState.routes]). */
    fun selectRouteOption(index: Int) {
        _route.update { state ->
            if (state == null || index !in state.routes.indices) state
            else state.copy(selectedRouteIndex = index)
        }
    }

    /** Retry after a failed request (panel error state). */
    fun retryRoute() {
        val current = _route.value ?: return
        _route.update {
            it?.copy(isLoading = true, hasError = false, fromCache = false, routes = emptyList())
        }
        if (current.destinationLat != 0.0 || current.destinationLng != 0.0) fetchRoute()
    }

    /** Close directions: clears the route and its lines from the map. */
    fun clearRoute() {
        routeJob?.cancel()
        _route.value = null
    }

    private fun fetchRoute() {
        val current = _route.value ?: return
        val originLat = _uiState.value.userLat
        val originLng = _uiState.value.userLng
        if (originLat == null || originLng == null) {
            _route.update { it?.copy(isLoading = false, hasError = true) }
            return
        }
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            routeRepository.route(
                fromLat = originLat,
                fromLng = originLng,
                toLat = current.destinationLat,
                toLng = current.destinationLng,
                mode = current.mode
            ).fold(
                onSuccess = { result ->
                    _route.update { state ->
                        if (state == null || state.destinationEventId != current.destinationEventId) {
                            state
                        } else {
                            state.copy(
                                isLoading = false,
                                hasError = false,
                                fromCache = result.fromCache,
                                routes = result.routes,
                                selectedRouteIndex = 0
                            )
                        }
                    }
                },
                onFailure = {
                    _route.update { state ->
                        if (state == null || state.destinationEventId != current.destinationEventId) {
                            state
                        } else {
                            state.copy(
                                isLoading = false,
                                hasError = true,
                                fromCache = false,
                                routes = emptyList()
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Smart Discovery "Near Me Now": nearest events first with the radius
     * filter on, using the best available fix (saved city or device GPS).
     */
    fun onNearMeNow() {
        manualLocation()
        onSortSelect(EventSort.DISTANCE)
        onToggleRadiusFilter(true)
        AppLogger.i("HomeViewModel", "Smart Discovery -> Near Me Now")
    }

    /** Smart Discovery "Trending This Weekend": toggle the weekend window. */
    fun onTrendingWeekend() {
        val enabled = timeWindowFlow.value != TimeWindow.WEEKEND
        timeWindowFlow.value = if (enabled) TimeWindow.WEEKEND else TimeWindow.ANYTIME
        _uiState.update {
            it.copy(timeWindow = timeWindowFlow.value, weekendOnly = enabled)
        }
        if (enabled) onSortSelect(EventSort.TRENDING)
        AppLogger.i("HomeViewModel", "Smart Discovery -> Trending This Weekend: $enabled")
    }

    /**
     * Filter Events modal: stage-free apply of the showcase filters —
     * When?, minimum rating, verified organiser, accessible events and the
     * Near Me radius toggle.
     */
    fun applyFilters(
        window: TimeWindow,
        minRating: Int,
        verifiedOrganizerOnly: Boolean,
        accessibleOnly: Boolean,
        nearMeEnabled: Boolean,
        radiusKm: Int
    ) {
        val radius = radiusKm.coerceIn(5, 200)
        timeWindowFlow.value = window
        minRatingFlow.value = minRating.coerceIn(0, 5)
        verifiedOnlyFlow.value = verifiedOrganizerOnly
        accessibleOnlyFlow.value = accessibleOnly
        radiusEnabledFlow.value = nearMeEnabled
        radiusFlow.value = radius
        _uiState.update {
            it.copy(
                timeWindow = window,
                weekendOnly = window == TimeWindow.WEEKEND,
                minRating = minRating.coerceIn(0, 5),
                verifiedOnly = verifiedOrganizerOnly,
                accessibleOnly = accessibleOnly,
                radiusFilterEnabled = nearMeEnabled,
                radiusKm = radius
            )
        }
        if (nearMeEnabled) {
            viewModelScope.launch { preferences.setDefaultRadiusKm(radius) }
        }
        AppLogger.i(
            "HomeViewModel",
            "Filters applied: window=$window rating=$minRating verified=$verifiedOrganizerOnly " +
                "accessible=$accessibleOnly nearMe=$nearMeEnabled radius=${radius}km"
        )
    }

    /** The upcoming Saturday-Sunday window (this weekend when today is Sat/Sun). */
    internal fun upcomingWeekendWindow(now: Long): LongRange {
        val tz = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(tz).toLocalDate()
        val saturdayOffset: Long = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> 0L
            DayOfWeek.SUNDAY -> -1L // still this weekend
            else -> (DayOfWeek.SATURDAY.value - today.dayOfWeek.value).toLong()
        }
        val saturday = today.plusDays(saturdayOffset)
        val sunday = saturday.plusDays(1)
        val start = saturday.atStartOfDay(tz).toInstant().toEpochMilli()
        val end = sunday.atTime(23, 59, 59).atZone(tz).toInstant().toEpochMilli()
        return start..end
    }

    /** Nearest known SA city for a fix — keeps the header pill in "<City>, ZA" form. */
    private fun nearestCityLabel(lat: Double, lng: Double): String {
        val nearest = SouthAfricanPlaces.all.minByOrNull {
            DistanceCalculator.between(lat, lng, it.latitude, it.longitude)
        } ?: return "South Africa"
        return "${nearest.name}, ZA"
    }

    fun onShowFilterSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFilterSheet = show)
    }

    fun setUserLocation(lat: Double, lng: Double) {
        if (manualCityActive) return
        locationFlow.value = lat to lng
        _uiState.value = _uiState.value.copy(
            userLat = lat,
            userLng = lng,
            cityLabel = nearestCityLabel(lat, lng)
        )
        AppLogger.i("HomeViewModel", "User location set ($lat, $lng)")
    }

    fun manualLocation() {
        LocationUtils.lastKnown(appContext)?.let { (lat, lng) -> setUserLocation(lat, lng) }
    }

    /** Header location selector: show events around [cityName] instead of the device fix. */
    fun selectCity(cityName: String) {
        viewModelScope.launch {
            preferences.setSelectedCity(cityName)
            manualCityActive = true
            _uiState.update { it.copy(cityLabel = "$cityName, ZA") }

            val place = runCatching { container.placeResolver.resolveCity(cityName) }.getOrNull()
            if (place != null) {
                locationFlow.value = place.latitude to place.longitude
                _uiState.update {
                    it.copy(userLat = place.latitude, userLng = place.longitude)
                }
                AppLogger.i("HomeViewModel", "Location selector -> $cityName (${place.latitude}, ${place.longitude})")
            } else {
                _messages.emit(UiMessage.Literal("Could not locate $cityName — check your connection"))
            }
        }
    }

    /** Header location selector: hand control back to the device's GPS fix. */
    fun useCurrentLocation() {
        viewModelScope.launch {
            preferences.setSelectedCity(null)
            manualCityActive = false
            val fix = LocationUtils.lastKnown(appContext)
            _uiState.update {
                it.copy(
                    cityLabel = fix?.let { (lat, lng) -> nearestCityLabel(lat, lng) }
                        ?: "${SouthAfricanPlaces.nationalDefault.name}, ZA"
                )
            }
            fix?.let { (lat, lng) ->
                locationFlow.value = lat to lng
                _uiState.update { state -> state.copy(userLat = lat, userLng = lng) }
            }
        }
    }

    fun toggleFavorite(eventId: String) {
        viewModelScope.launch {
            val added = eventRepository.toggleFavorite(eventId)
            _messages.emit(
                if (added) UiMessage.Resource(R.string.added_to_favorites)
                else UiMessage.Resource(R.string.removed_from_favorites)
            )
        }
    }

    fun setRsvp(eventId: String, status: RsvpStatus) {
        viewModelScope.launch {
            eventRepository.setRsvp(eventId, status)
            when (status) {
                RsvpStatus.ATTENDING -> {
                    val event = eventRepository.getEvent(eventId)
                    val remindersEnabled = preferences.remindersEnabled.first()
                    val userId = preferences.sessionUserId.first()
                    if (event != null && remindersEnabled && !userId.isNullOrBlank()) {
                        val scheduled = NotificationHelper.scheduleEventReminders(appContext, event, userId)
                        _messages.emit(
                            if (scheduled > 0) UiMessage.Resource(R.string.reminder_scheduled)
                            else UiMessage.Resource(R.string.rsvp_updated)
                        )
                    } else {
                        _messages.emit(UiMessage.Resource(R.string.rsvp_updated))
                    }
                }
                RsvpStatus.MAYBE, RsvpStatus.DECLINED -> {
                    NotificationHelper.cancelEventReminders(appContext, eventId)
                    _messages.emit(UiMessage.Resource(R.string.reminders_cancelled))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, isOffline = true) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, isRefreshing = true, isOffline = false) }

            runCatching { eventDiscoveryRepository.refresh() }.onFailure {
                AppLogger.e("HomeViewModel", "Event discovery failed on refresh", it)
            }

            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    fun refreshEvents() {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                AppLogger.i("HomeViewModel", "Skipping event refresh: device is offline")
                return@launch
            }

            runCatching { eventDiscoveryRepository.refresh() }
                .onSuccess {
                    AppLogger.i(
                        "HomeViewModel",
                        "Event discovery: fetched=${it.fetched}, inserted=${it.inserted}, failedSources=${it.failedSources}"
                    )
                }
                .onFailure {
                    AppLogger.e("HomeViewModel", "Event discovery failed", it)
                }
        }
    }

    companion object {
        fun factory(container: AppContainer, appContext: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(container, appContext) }
            }
    }
}

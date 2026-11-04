package com.eventfinder.app.ui.screens.organizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.RatingSummary
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.ui.components.EventCard
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri

/** Organizers holding these account names get the verified badge (mirrors detail screen). */
private val TRUSTED_ORGANIZERS = setOf(
    "Ticketmaster SA", "Computicket", "Webtickets", "Quicket", "Howler SA"
)

/**
 * Public organizer profile (showcase3 screen 6): logo badge + name + verified
 * badge, a stats row (events / attendees / rating), About, website contact,
 * Report Organizer and the organizer's upcoming events.
 *
 * Every number on this screen is derived from the local database — the
 * organizer's own events, their summed attendee counts and the average of the
 * verified reviews on those events. Nothing is hardcoded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerProfileScreen(
    container: AppContainer,
    organizerId: String,
    onBack: () -> Unit,
    onEventClick: (String) -> Unit
) {
    val vm: OrganizerProfileViewModel = viewModel(key = "organizer:$organizerId") {
        OrganizerProfileViewModel(container, organizerId)
    }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.organizer_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // Logo badge + name + verified badge
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = organizerInitials(state.organizerName),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = state.organizerName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        if (state.verified) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.verified_organizer),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.organizer),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Stats row: Events | Attendees | Rating
            item(key = "stats") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCell(
                            value = state.eventCount.toString(),
                            label = stringResource(R.string.organizer_events_label),
                            modifier = Modifier.weight(1f)
                        )
                        VerticalStatDivider()
                        StatCell(
                            value = state.attendeeTotal.toString(),
                            label = stringResource(R.string.attendees),
                            modifier = Modifier.weight(1f)
                        )
                        VerticalStatDivider()
                        StatCell(
                            value = state.averageRating
                                ?.let { String.format("%.1f", it) }
                                ?: "–",
                            label = stringResource(R.string.organizer_rating_label),
                            modifier = Modifier.weight(1f),
                            star = true
                        )
                    }
                }
            }

            // About — real derived sentence
            item(key = "about") {
                Column {
                    Text(
                        text = stringResource(R.string.about),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.host_line, state.eventCount),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Contact — the organizer's real website (first http source URL)
            state.websiteUrl?.let { url ->
                item(key = "contact") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.contact),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = websiteLabel(url),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Report organizer
            item(key = "report") {
                TextButton(
                    onClick = { vm.reportOrganizer() },
                    enabled = !state.reported,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (state.reported) R.string.report_submitted
                            else R.string.report_organizer
                        ),
                        color = if (state.reported) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            // Block / unblock the organiser (safety control).
            item(key = "block") {
                TextButton(
                    onClick = { vm.toggleBlockOrganizer() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_ms_block),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.blocked) "Unblock organizer" else "Block organizer",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Upcoming events hosted by this organizer
            if (state.upcomingEvents.isNotEmpty()) {
                item(key = "upcoming_title") {
                    Text(
                        text = stringResource(R.string.upcoming_events),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(state.upcomingEvents, key = { it.id }) { event ->
                    EventCard(
                        view = EventView(event),
                        onClick = { onEventClick(event.id) },
                        onFavoriteToggle = { vm.toggleFavorite(event.id) }
                    )
                }
            }
        }
    }
}

/** Big number over a small label — one cell of the showcase stats row. */
@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    star: Boolean = false
) {
    Column(
        modifier = modifier.padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (star) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalStatDivider() {
    HorizontalDivider(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .height(48.dp)
            .width(1.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private fun organizerInitials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }

private fun websiteLabel(url: String): String = runCatching {
    Uri.parse(url).host?.removePrefix("www.") ?: url
}.getOrDefault(url)

private data class OrganizerUiState(
    val organizerName: String = "",
    val verified: Boolean = false,
    val eventCount: Int = 0,
    val attendeeTotal: Int = 0,
    val averageRating: Float? = null,
    val websiteUrl: String? = null,
    val upcomingEvents: List<Event> = emptyList(),
    val reported: Boolean = false,
    /** Organiser is blocked by the signed-in user (their events stay hidden). */
    val blocked: Boolean = false,
    val loading: Boolean = true
)

/**
 * Loads everything shown on the organizer profile from the local database:
 * the organizer's events, summed attendee counts, average review rating and
 * the first real website link among their events.
 */
private class OrganizerProfileViewModel(
    private val container: AppContainer,
    private val organizerId: String
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizerUiState())
    val state: StateFlow<OrganizerUiState> = _state

    init {
        viewModelScope.launch {
            val userId = container.preferences.sessionUserId.first()
            if (!userId.isNullOrBlank()) {
                val blocked = container.reportRepository.isBlocked(userId, organizerId)
                _state.value = _state.value.copy(blocked = blocked)
            }
        }
        viewModelScope.launch {
            combine(
                container.eventRepository.observeAllEvents(),
                container.eventReviewRepository.observeAllRatingSummaries()
            ) { events, ratings -> events to ratings }
                .distinctUntilChanged()
                .collect { (events, ratings) ->
                    val hosted = events.filter { it.organizerId == organizerId }
                    val now = System.currentTimeMillis()
                    val upcoming = hosted
                        .filter { it.endDate > now }
                        .sortedBy { it.startDate }
                    val hostedIds = hosted.map { it.id }.toSet()
                    val hostRatings = ratings
                        .filterKeys { it in hostedIds }
                        .values
                        .filter { it.count > 0 && it.average != null }
                    val average = hostRatings
                        .takeIf { it.isNotEmpty() }
                        ?.let { list ->
                            list.sumOf { (it.average!! * it.count).toDouble() } /
                                list.sumOf { it.count }
                        }
                        ?.toFloat()
                    val name = hosted.firstOrNull()?.organizerName ?: organizerId
                    _state.value = OrganizerUiState(
                        organizerName = name,
                        // Data first: the feed's verified flag, with the
                        // ticketing-platform allowlist as a fallback.
                        verified = hosted.any { it.organizerIsVerified } ||
                            name in TRUSTED_ORGANIZERS,
                        eventCount = hosted.size,
                        attendeeTotal = hosted.sumOf { it.attendeeCount },
                        averageRating = average,
                        websiteUrl = hosted.firstNotNullOfOrNull { event ->
                            event.sourceUrl?.takeIf { it.startsWith("http") }
                        },
                        upcomingEvents = upcoming,
                        reported = _state.value.reported,
                        blocked = _state.value.blocked,
                        loading = false
                    )
                }
        }
    }

    fun reportOrganizer() {
        if (_state.value.reported) return
        viewModelScope.launch {
            container.reportRepository.report(
                targetType = "organizer",
                targetId = organizerId,
                reason = "user_report",
                reporterId = ""
            )
            _state.value = _state.value.copy(reported = true)
        }
    }

    fun toggleFavorite(eventId: String) {
        viewModelScope.launch {
            container.eventRepository.toggleFavorite(eventId)
        }
    }

    /** Blocks or unblocks this organiser; the state drives the button label. */
    fun toggleBlockOrganizer() {
        viewModelScope.launch {
            val userId = container.preferences.sessionUserId.first() ?: return@launch
            val repository = container.reportRepository
            val nowBlocked = !_state.value.blocked
            if (nowBlocked) {
                repository.block(userId, organizerId)
            } else {
                repository.unblock(userId, organizerId)
            }
            _state.value = _state.value.copy(blocked = nowBlocked)
        }
    }
}

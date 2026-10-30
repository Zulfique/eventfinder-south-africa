package com.eventfinder.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline

import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.describeWeatherCode
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.ui.components.categoryLabel
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.ui.components.LoadingView
import com.eventfinder.app.utils.DateTimeUtils

/**
 * Screen 6 (Event Detail): hero image, metadata, RSVP, favourite, share,
 * directions and the Open-Meteo weather card.
 */
@Composable
fun EventDetailScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit,
    onEditEvent: (String) -> Unit = {},
    onPlanWithFriends: (String) -> Unit = {},
    onOpenScanner: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: EventDetailViewModel = viewModel(
        factory = EventDetailViewModel.factory(container, context.applicationContext, eventId)
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showOwnerMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    // Re-read the event when returning from the create/edit screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = {},
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingView()
                state.notFound || state.eventView == null -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.no_events))
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.retry)) }
                    }
                }
                else -> {
                    val view = state.eventView!!
                    val event = view.event

                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        // Hero image with back + favourite controls
                        Box(Modifier.fillMaxWidth().height(230.dp)) {
                            AsyncImage(
                                model = event.imageUrl,
                                contentDescription = stringResource(R.string.event_image),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                ) {
                                    Icon(
                                        if (view.isFavorite) Icons.Outlined.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(
                                            if (view.isFavorite) R.string.favorite_remove
                                            else R.string.favorite_add
                                        ),
                                        tint = if (view.isFavorite) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (event.isCreatedByUser) {
                                    Box {
                                        IconButton(
                                            onClick = { showOwnerMenu = true },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                        ) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                contentDescription = stringResource(R.string.event_options)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showOwnerMenu,
                                            onDismissRequest = { showOwnerMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.edit_event)) },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    onEditEvent(event.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete_event)) },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text(stringResource(R.string.delete_event)) },
                                text = { Text(stringResource(R.string.delete_event_confirm)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                        viewModel.deleteEvent(onDeleted = onBack)
                                    }) { Text(stringResource(R.string.delete)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }

                        Column(Modifier.padding(16.dp)) {
                            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Source: ${event.provenance}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(categoryLabel(event.category)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Date", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    DateTimeUtils.formatFullDateTime(event.startDate),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (view.distanceKm != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        "ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â· ${com.eventfinder.app.utils.DistanceCalculator.displayDistanceKm(view.distanceKm)} km " +
                                            stringResource(R.string.distance_away),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            if (event.endDate > event.startDate) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.size(24.dp))
                                    Text(
                                        "${stringResource(R.string.ends)} ${DateTimeUtils.formatFullDateTime(event.endDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LocationOn, contentDescription = "Location", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(event.venueName, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                event.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = "Organizer", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.organizer), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.size(4.dp))
                                Text(event.organizerName, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Group, contentDescription = "Attendees", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "${event.attendeeCount} ${stringResource(R.string.attendees)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Weather card (Open-Meteo)
                            WeatherCard(state)
                            Spacer(Modifier.height(12.dp))

                            Text(stringResource(R.string.reviews), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val average = state.averageRating
                                if (average != null && state.reviewCount > 0) {
                                    Row {
                                        repeat(5) { index ->
                                            Icon(
                                                imageVector = if (index < average.toInt()) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (index < average.toInt()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("%.1f (%d)".format(average, state.reviewCount), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text(stringResource(R.string.no_reviews_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            if (state.canReview) {
                                Text(
                                    if (state.reviewDraftRating == 0) stringResource(R.string.tap_to_rate)
                                    else "${state.reviewDraftRating} ${stringResource(R.string.your_rating)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row {
                                    repeat(5) { index ->
                                        val star = index + 1
                                        IconButton(onClick = { viewModel.setReviewRating(star) }) {
                                            Icon(
                                                imageVector = if (state.reviewDraftRating >= star) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                                contentDescription = null,
                                                tint = if (state.reviewDraftRating >= star) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = state.reviewDraftComment,
                                    onValueChange = { viewModel.setReviewComment(it) },
                                    label = { Text(stringResource(R.string.review_comment_optional)) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    maxLines = 5
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.submitReview() },
                                    enabled = state.reviewDraftRating > 0 && !state.isSubmittingReview,
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Text(stringResource(R.string.submit_review))
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            if (state.reviews.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.reviews.forEach { review ->
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Row {
                                                        repeat(5) { index ->
                                                            Icon(
                                                                imageVector = if (index < review.rating) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (index < review.rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(review.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            Button(
                                onClick = { viewModel.setRsvp(RsvpStatus.ATTENDING) },
                                enabled = view.rsvpStatus != RsvpStatus.ATTENDING,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(
                                    if (view.rsvpStatus == RsvpStatus.ATTENDING) stringResource(R.string.attending)
                                    else stringResource(R.string.attend)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setRsvp(
                                            if (view.rsvpStatus == RsvpStatus.MAYBE) RsvpStatus.DECLINED
                                            else RsvpStatus.MAYBE
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(stringResource(R.string.attend_maybe))
                                }
                                OutlinedButton(
                                    onClick = {
                                        val latitude = event.latitude
                                        val longitude = event.longitude
                                        if (latitude == null || longitude == null) {
                                            // No coordinates: fall back to a text-based maps search.
                                            val query = Uri.encode(
                                                listOf(event.venueName, event.address)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(", ")
                                            )


                                            val searchUri = Uri.parse(
                                                "https://www.google.com/maps/search/?api=1&query=$query"
                                            )


                                            val searchIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                searchUri
                                            )


                                            if (searchIntent.resolveActivity(context.packageManager) != null) {
                                                context.startActivity(searchIntent)
                                                return@OutlinedButton
                                            }
                                        }


                                        val geoUri = Uri.parse(
                                            "geo:$latitude,$longitude" +
                                                "?q=$latitude,$longitude(${Uri.encode(event.venueName)})"
                                        )


                                        val intent = Intent(Intent.ACTION_VIEW, geoUri)


                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        } else {
                                            // geo: intent had no handler; fall back to web search.
                                            val query = Uri.encode(
                                                listOf(event.venueName, event.address)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(", ")
                                            )


                                            val fallback = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    "https://www.google.com/maps/search/?api=1&query=$query"
                                                )
                                            )


                                            if (fallback.resolveActivity(context.packageManager) != null) {
                                                context.startActivity(fallback)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Outlined.Navigation, contentDescription = "Directions")
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.directions))
                                }
                            }
                             FilledTonalButton(
                                 onClick = { onPlanWithFriends(event.id) },
                                 modifier = Modifier.fillMaxWidth().height(44.dp)
                             ) {
                                 Icon(Icons.Outlined.Group, contentDescription = "Plan with friends")
                                 Spacer(Modifier.size(6.dp))
                                 Text("Plan with Friends")
                             }
                             Spacer(Modifier.height(8.dp))
                             if (event.isCreatedByUser) {
                                 Button(
                                     onClick = { onOpenScanner(event.id) },
                                     modifier = Modifier.fillMaxWidth().height(44.dp)
                                 ) {
                                     Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Check-in scanner")
                                     Spacer(Modifier.size(6.dp))
                                     Text("Check-in Attendees")
                                 }
                                 Spacer(Modifier.height(8.dp))
                             }
                             FilledTonalButton(
                                 onClick = {
                                     val send = Intent(Intent.ACTION_SEND).apply {
                                         type = "text/plain"
                                         putExtra(
                                             Intent.EXTRA_TEXT,
                                             "${event.title}\n${DateTimeUtils.formatFullDateTime(event.startDate)}\n" +
                                                 "${event.venueName}, ${event.address}\n\nDiscover via EventFinder"
                                         )
                                     }
                                     context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                                 },
                                 modifier = Modifier.fillMaxWidth().height(44.dp)
                             ) {
                                 Icon(Icons.Outlined.Share, contentDescription = "Share")
                                 Spacer(Modifier.size(6.dp))
                                 Text(stringResource(R.string.share))
                             }

                            val reportScope = rememberCoroutineScope()
                            TextButton(
                                onClick = {
                                    reportScope.launch {
                                        container.reportRepository.report(
                                            targetType = "event",
                                            targetId = event.id,
                                            reason = "user_report",
                                            reporterId = ""
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Report this event")
                            }

                            Spacer(Modifier.height(24.dp))

                            if (event.ageRestricted) {
                                Text(
                                    "Age-restricted event (18+)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(stringResource(R.string.about_event), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Weather card populated from the free Open-Meteo API. */
@Composable
private fun WeatherCard(state: EventDetailUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.WbSunny, contentDescription = "Weather", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    stringResource(R.string.weather_at_venue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                when {
                    state.weather != null -> Text(
                        "${describeWeatherCode(state.weather.weatherCode)} - " +
                            "${state.weather.temperatureCelsius}${state.weather.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    state.weatherUnavailable -> Text(
                        stringResource(R.string.weather_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    else -> Text(
                        stringResource(R.string.weather_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}


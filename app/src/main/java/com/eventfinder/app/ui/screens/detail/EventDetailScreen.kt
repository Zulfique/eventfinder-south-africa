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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
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
    onEditEvent: (String) -> Unit = {}
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
                                        if (event.isFavorite) Icons.Outlined.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(
                                            if (event.isFavorite) R.string.favorite_remove
                                            else R.string.favorite_add
                                        ),
                                        tint = if (event.isFavorite) MaterialTheme.colorScheme.error
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
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    DateTimeUtils.formatFullDateTime(event.startDate),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (view.distanceKm != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        "· ${com.eventfinder.app.utils.DistanceCalculator.displayDistanceKm(view.distanceKm)} km " +
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
                                Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.organizer), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.size(4.dp))
                                Text(event.organizerName, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                        val uri = Uri.parse("geo:${event.latitude},${event.longitude}" +
                                            "?q=${event.latitude},${event.longitude}(${Uri.encode(event.venueName)})")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Outlined.Navigation, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.directions))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.share))
                            }

                            Spacer(Modifier.height(24.dp))

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
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    stringResource(R.string.weather_at_venue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                when {
                    state.weather != null -> Text(
                        "${describeWeatherCode(state.weather.weatherCode)} · " +
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
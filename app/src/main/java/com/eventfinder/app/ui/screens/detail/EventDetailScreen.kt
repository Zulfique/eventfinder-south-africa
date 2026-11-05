package com.eventfinder.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.eventfinder.app.ui.components.EventCard
import com.eventfinder.app.ui.components.UiMessage
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms

import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.components.FavoritePngIcon
import com.eventfinder.app.ui.components.InteractivePngIcon
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.ui.components.LoadingView
import com.eventfinder.app.utils.DateTimeUtils
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Verified
import com.eventfinder.app.ui.components.QrImage
import com.eventfinder.app.ui.components.AboutEventButton
import com.eventfinder.app.ui.components.RsvpActionButton
import com.eventfinder.app.ui.components.RsvpOptionPill
import com.eventfinder.app.ui.components.RsvpTallyChips
import com.eventfinder.app.ui.components.ReportEventButton
import com.eventfinder.app.ui.components.RsvpStatusNotice
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface

/**
 * Screen 6 (Event Detail): hero image, metadata, RSVP, favourite, share,
 * directions and the Open-Meteo weather card.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit,
    onEditEvent: (String) -> Unit = {},
    onPlanWithFriends: (String) -> Unit = {},
    onOpenScanner: (String) -> Unit = {},
    onOrganizerClick: (String) -> Unit = {},
    onReportEvent: (String) -> Unit = {},
    onOpenEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: EventDetailViewModel = viewModel(
        factory = EventDetailViewModel.factory(container, context.applicationContext, eventId)
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showOwnerMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTicketDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exportTickets by remember { mutableStateOf<List<com.eventfinder.app.domain.model.EventTicket>>(emptyList()) }
    // RSVP system (2026 spec): sheet with pending choice + notify toggle,
    // submitted state with a success/error result.
    var showRsvpSheet by remember { mutableStateOf(false) }
    var rsvpNotifyMe by remember { mutableStateOf(true) }
    // True between tapping Confirm and the repository reporting the new status.
    var rsvpAwaitingConfirm by remember { mutableStateOf(false) }
    var rsvpError by remember { mutableStateOf(false) }
    var rsvpJustConfirmed by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

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
                        // Hero image with back + favourite controls.
                        // widthIn + centring keeps phone layouts untouched while
                        // capping stretched content on tablets/foldables.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp)
                                .align(Alignment.CenterHorizontally)
                                .height(300.dp)
                        ) {
                            AsyncImage(
                                model = event.imageUrl,
                                contentDescription = stringResource(R.string.event_image),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(R.drawable.ic_image_placeholder),
                                error = painterResource(R.drawable.ic_image_placeholder),
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                        .padding(11.dp)
                                ) {
                                    FavoritePngIcon(
                                        isFavorite = view.isFavorite,
                                        onToggle = { viewModel.toggleFavorite() },
                                        size = 26.dp,
                                        contentDescription = stringResource(
                                            if (view.isFavorite) R.string.favorite_remove
                                            else R.string.favorite_add
                                        )
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
                                                text = { Text("Export RSVPs") },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    showExportSheet = true
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

                        if (showTicketDialog && state.myTicket != null) {
                            val ticket = state.myTicket!!
                            AlertDialog(
                                onDismissRequest = { showTicketDialog = false },
                                title = { Text(stringResource(R.string.my_ticket_title)) },
                                text = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        QrImage(
                                            value = ticket.ticketCode,
                                            contentDescription = stringResource(
                                                R.string.ticket_qr_desc, ticket.ticketCode
                                            ),
                                            modifier = Modifier
                                                .size(220.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            ticket.ticketCode,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.ticket_ready_for, event.title),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            stringResource(
                                                if (ticket.checkedInAt != null) R.string.ticket_checked_in
                                                else R.string.ticket_show_at_gate
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showTicketDialog = false }) {
                                        Text(stringResource(R.string.close))
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 28.dp,
                                        topEnd = 28.dp
                                    )
                                )
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                        ) {
                            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            // Provenance badge + event accessibility chips,
                            // all driven by real event fields (no decoration).
                            ProvenanceBadge(event.provenance)
                            if (event.wheelchairAccessible || event.loudMusic ||
                                event.sensoryFriendly || event.strobeLights
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    if (event.wheelchairAccessible) {
                                        AccessibilityChip(
                                            icon = Icons.Outlined.Accessible,
                                            label = "Wheelchair access"
                                        )
                                    }
                                    if (event.sensoryFriendly) {
                                        AccessibilityChip(
                                            icon = Icons.Outlined.Spa,
                                            label = "Sensory friendly"
                                        )
                                    }
                                    if (event.loudMusic) {
                                        AccessibilityChip(
                                            icon = Icons.Outlined.VolumeUp,
                                            label = "Loud music"
                                        )
                                    }
                                    if (event.strobeLights) {
                                        AccessibilityChip(
                                            icon = Icons.Outlined.FlashOn,
                                            label = "Strobe lights"
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(
                                    if (event.category == EventCategory.OTHER) R.string.cat_other
                                    else categoryLabel(event.category)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            if (event.isRecurring) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Repeat,
                                        contentDescription = stringResource(R.string.recurring_event),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        stringResource(R.string.recurring_event) +
                                            (event.recurrenceRule?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(R.drawable.evt_calendar), contentDescription = "Date", modifier = Modifier.size(20.dp))
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
                                Image(painter = painterResource(R.drawable.evt_venue), contentDescription = "Location", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(event.venueName, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                event.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOrganizerClick(event.organizerId) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Image(painter = painterResource(R.drawable.usr_account), contentDescription = "Organizer", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.organizer), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.size(4.dp))
                                Text(event.organizerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    stringResource(R.string.view_profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(R.drawable.evt_attendees), contentDescription = "Attendees", modifier = Modifier.size(20.dp))
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
                                            Image(
                                                painter = painterResource(R.drawable.evt_rating),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer(alpha = if (index < average.toInt()) 1f else 0.28f)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("%.1f (%d)".format(average, state.reviewCount), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text(stringResource(R.string.no_reviews_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (state.reviews.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReviewFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = state.reviewFilter == filter,
                                            onClick = { viewModel.setReviewFilter(filter) },
                                            label = { Text(filter.label) }
                                        )
                                    }
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
                                            InteractivePngIcon(
                                                iconRes = R.drawable.evt_rating,
                                                contentDescription = "$star star rating",
                                                active = state.reviewDraftRating >= star,
                                                size = 30.dp
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
                                val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                                ) { uri -> uri?.let { viewModel.addReviewPhoto(it.toString()) } }
                                if (state.reviewDraftPhotos.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.reviewDraftPhotos.forEach { uri ->
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { viewModel.removeReviewPhoto(uri) }
                                            ) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = stringResource(R.string.remove_photo),
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                OutlinedButton(
                                    onClick = { photoLauncher.launch("image/*") },
                                    enabled = state.reviewDraftPhotos.size < 4,
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(stringResource(R.string.add_photo))
                                }
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
                                                    Box(
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = review.authorName
                                                                .trim()
                                                                .split(' ')
                                                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                                                .take(2)
                                                                .joinToString(""),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Row {
                                                        repeat(5) { index ->
                                                            Image(
                                                                painter = painterResource(R.drawable.evt_rating),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .graphicsLayer(alpha = if (index < review.rating) 1f else 0.28f)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(review.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    if (review.verifiedAttendance) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                Icons.Outlined.Verified,
                                                                contentDescription = stringResource(R.string.review_verified),
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                            Spacer(Modifier.size(3.dp))
                                                            Text(
                                                                stringResource(R.string.review_verified),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.weight(1f))
                                                    Text(
                                                        DateTimeUtils.formatShortDate(review.createdAt),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.blockReviewer(review.userId)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_ms_block),
                                                            contentDescription = "Block this reviewer",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                                                if (review.photoUrls.isNotEmpty()) {
                                                    Spacer(Modifier.height(6.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        review.photoUrls.take(4).forEach { url ->
                                                            AsyncImage(
                                                                model = url,
                                                                contentDescription = stringResource(R.string.review_photo),
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .size(72.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            // Showcase primary action: Get Tickets issues the
                            // QR ticket, RSVPs attending and opens the code.
                            Button(
                                onClick = {
                                    if (view.rsvpStatus != RsvpStatus.ATTENDING) {
                                        viewModel.setRsvp(RsvpStatus.ATTENDING)
                                    }
                                    viewModel.issueMyTicket()
                                    showTicketDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ConfirmationNumber,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    if (state.myTicket != null) "View My Ticket"
                                    else stringResource(R.string.get_tickets),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            // RSVP tallies (Going / Maybe / Not Going) from the
                            // event's real attendee data, then the single RSVP
                            // action button with its confirmed/cancel states.
                            RsvpTallyChips(
                                going = event.attendeeCount,
                                maybe = event.maybeCount,
                                notGoing = event.notGoingCount
                            )
                            Spacer(Modifier.height(10.dp))
                            RsvpActionButton(
                                status = view.rsvpStatus,
                                onOpenSheet = {
                                    rsvpError = false
                                    rsvpJustConfirmed = false
                                    showRsvpSheet = true
                                },
                                onCancel = { viewModel.setRsvp(RsvpStatus.DECLINED) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            // Per-event reminder mute ("this event only"), so a
                            // single noisy event never forces the global
                            // reminders switch off.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Skip reminders",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "For this event only",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.reminderSkipped,
                                    onCheckedChange = viewModel::setReminderSkipped
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { openDirections(context, event) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ms_pin),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.directions),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                             // Showcase Group Planning card: avatar stack,
                             // going count and the green Invite Friends pill.
                             val groupPlans by container.groupPlanningRepository
                                 .observePlansForEvent(event.id)
                                 .collectAsState(initial = emptyList())
                             GroupPlanningCard(
                                 members = groupPlans.firstOrNull()?.members.orEmpty(),
                                 onInvite = { onPlanWithFriends(event.id) }
                             )
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
                            // Share (Share design system: filled green 48dp
                            // circular icon button) beside the outlined
                            // "Report this Event" pill with its real states.
                            val reportScope = rememberCoroutineScope()
                            var reportSubmitting by remember { mutableStateOf(false) }
                            var reportSubmitted by remember { mutableStateOf(false) }
                            var reportFailed by remember { mutableStateOf(false) }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = { showShareSheet = true },
                                    shape = CircleShape,
                                    color = Color(0xFF00C98D),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_ms_share),
                                            contentDescription = stringResource(R.string.share),
                                            tint = Color(0xFF0B1929),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                ReportEventButton(
                                    loading = false,
                                    submitted = false,
                                    error = false,
                                    enabled = true,
                                    onClick = { onReportEvent(event.id) },
                                    modifier = Modifier.weight(1f)
                                )
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
                            // About This Event button (About spec): the info
                            // disc + divider + label + chevron, which reveals
                            // the description when expanded.
                            AboutEventButton(
                                expanded = aboutExpanded,
                                onClick = { aboutExpanded = !aboutExpanded }
                            )
                            if (aboutExpanded) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // "Similar events": same category / organiser / nearby
                            // venues from the local catalogue (works offline).
                            if (state.similarEvents.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Similar events",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(
                                        items = state.similarEvents,
                                        key = { it.event.id }
                                    ) { similar ->
                                        EventCard(
                                            view = similar,
                                            onClick = { onOpenEvent(similar.event.id) },
                                            modifier = Modifier.width(260.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }

    // RSVP sheet (2026 spec): event header, three option pills, notify switch
    // and a Confirm RSVP button; submitting / failed / closed states included.
    val rsvpEvent = state.eventView
    if (showRsvpSheet && rsvpEvent != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pendingStatus by remember(rsvpEvent.event.id) {
            mutableStateOf(rsvpEvent.rsvpStatus ?: RsvpStatus.ATTENDING)
        }

        ModalBottomSheet(
            onDismissRequest = {
                showRsvpSheet = false
                rsvpAwaitingConfirm = false
                rsvpError = false
            },
            sheetState = sheetState,
            containerColor = com.eventfinder.app.ui.components.EventFinderTokens.Surface
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                if (rsvpJustConfirmed) {
                    // Success state: green check badge + confirmation + View Event.
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(com.eventfinder.app.ui.components.EventFinderTokens.Green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ms_check_circle),
                                contentDescription = null,
                                tint = com.eventfinder.app.ui.components.EventFinderTokens.Night,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "You're Going!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = com.eventfinder.app.ui.components.EventFinderTokens.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "You've successfully RSVP'd to ${rsvpEvent.event.title}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { showRsvpSheet = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.eventfinder.app.ui.components.EventFinderTokens.Green,
                                contentColor = com.eventfinder.app.ui.components.EventFinderTokens.Night
                            )
                        ) {
                            Text("View Event", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_ms_arrow_forward),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Event header card
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = rsvpEvent.event.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = rsvpEvent.event.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.eventfinder.app.ui.components.EventFinderTokens.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ms_calendar),
                                    contentDescription = null,
                                    tint = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = DateTimeUtils.formatFullDateTime(rsvpEvent.event.startDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ms_pin),
                                    contentDescription = null,
                                    tint = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${rsvpEvent.event.venueName}, ${rsvpEvent.event.address}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { showRsvpSheet = false }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ms_close),
                                contentDescription = stringResource(R.string.close),
                                tint = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "RSVP to this event",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.eventfinder.app.ui.components.EventFinderTokens.TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RsvpOptionPill(
                            status = RsvpStatus.ATTENDING,
                            selected = pendingStatus == RsvpStatus.ATTENDING,
                            onClick = { pendingStatus = RsvpStatus.ATTENDING },
                            modifier = Modifier.weight(1f)
                        )
                        RsvpOptionPill(
                            status = RsvpStatus.MAYBE,
                            selected = pendingStatus == RsvpStatus.MAYBE,
                            onClick = { pendingStatus = RsvpStatus.MAYBE },
                            modifier = Modifier.weight(1f)
                        )
                        RsvpOptionPill(
                            status = RsvpStatus.DECLINED,
                            selected = pendingStatus == RsvpStatus.DECLINED,
                            onClick = { pendingStatus = RsvpStatus.DECLINED },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ms_bell),
                            contentDescription = null,
                            tint = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Notify me about updates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.eventfinder.app.ui.components.EventFinderTokens.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = rsvpNotifyMe,
                            onCheckedChange = { rsvpNotifyMe = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.eventfinder.app.ui.components.EventFinderTokens.Green,
                                checkedBorderColor = com.eventfinder.app.ui.components.EventFinderTokens.Green
                            )
                        )
                    }

                    // Real RSVP states from the repository: the write is only
                    // acknowledged once the event's status actually changes.
                    val saving = state.rsvpSaving || rsvpAwaitingConfirm
                    val rsvpClosed = rsvpEvent.event.endDate < System.currentTimeMillis()
                    androidx.compose.runtime.LaunchedEffect(
                        rsvpAwaitingConfirm,
                        state.eventView?.rsvpStatus
                    ) {
                        if (rsvpAwaitingConfirm &&
                            state.eventView?.rsvpStatus == pendingStatus
                        ) {
                            rsvpAwaitingConfirm = false
                            rsvpJustConfirmed = pendingStatus == RsvpStatus.ATTENDING
                            if (pendingStatus != RsvpStatus.ATTENDING) showRsvpSheet = false
                        }
                    }

                    if (rsvpClosed) {
                        Spacer(Modifier.height(14.dp))
                        RsvpStatusNotice(
                            title = "RSVP Closed",
                            subtitle = "This event is no longer accepting RSVPs.",
                            accent = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary,
                            icon = R.drawable.ic_ms_info
                        )
                    } else if (saving) {
                        Spacer(Modifier.height(14.dp))
                        RsvpStatusNotice(
                            title = "Submitting RSVP…",
                            subtitle = "Please wait a moment.",
                            accent = com.eventfinder.app.ui.components.EventFinderTokens.Green,
                            loading = true
                        )
                    }

                    if (rsvpError) {
                        Spacer(Modifier.height(14.dp))
                        RsvpStatusNotice(
                            title = "Failed to RSVP",
                            subtitle = "Something went wrong. Please try again.",
                            accent = com.eventfinder.app.ui.components.EventFinderTokens.ErrorRed,
                            icon = R.drawable.ic_ms_warning,
                            onRetry = {
                                rsvpError = false
                                rsvpAwaitingConfirm = true
                                viewModel.setRsvp(pendingStatus)
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            rsvpError = false
                            rsvpAwaitingConfirm = true
                            viewModel.setRsvp(pendingStatus)
                        },
                        enabled = !saving && !rsvpClosed,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.eventfinder.app.ui.components.EventFinderTokens.Green,
                            contentColor = com.eventfinder.app.ui.components.EventFinderTokens.Night,
                            disabledContainerColor = Color(0xFF1E2933),
                            disabledContentColor = com.eventfinder.app.ui.components.EventFinderTokens.TextSecondary
                        )
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = com.eventfinder.app.ui.components.EventFinderTokens.Night
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Submitting…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_ms_check_circle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (rsvpClosed) "RSVP Closed" else "Confirm RSVP",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Branded share sheet (showcase3): WhatsApp / Messages / Email /
    // Copy Link / Nearby shortcuts plus the social row.
    if (showExportSheet) {
        val exportEvent = state.eventView?.event
        LaunchedEffect(exportEvent?.id) {
            if (exportEvent != null) {
                exportTickets = container.ticketRepository.getAllTicketsForEvent(exportEvent.id)
            }
        }
        if (exportEvent != null) {
            com.eventfinder.app.ui.components.ExportRsvpSheet(
                eventId = exportEvent.id,
                eventTitle = exportEvent.title,
                tickets = exportTickets,
                onDismiss = { showExportSheet = false }
            )
        } else {
            showExportSheet = false
        }
    }

    if (showShareSheet) {
        val shareEvent = state.eventView?.event
        if (shareEvent != null) {
            ShareSheet(
                shareText = "${shareEvent.title}\n" +
                    "${DateTimeUtils.formatFullDateTime(shareEvent.startDate)}\n" +
                    "${shareEvent.venueName}, ${shareEvent.address}\n" +
                    (shareEvent.sourceUrl?.let { "$it\n" } ?: "") +
                    "\nDiscover via EventFinder",
                link = shareEvent.sourceUrl,
                onDismiss = { showShareSheet = false }
            )
        } else {
            showShareSheet = false
        }
    }
}

/**
 * Opens Get Directions for [event].
 *
 * Tries the native `geo:` deep link first (Google Maps / any installed map
 * app), then the Google Maps web directions URL, then a plain web search for
 * the venue. Each step is attempted with try/catch rather than a pre-flight
 * resolveActivity() check: package visibility on Android 11+ can make
 * resolveActivity() report "no handler" even when one exists, which is what
 * made this button silently do nothing before. A chooser is the last resort
 * so the button always produces visible feedback.
 */
private fun openDirections(context: android.content.Context, event: com.eventfinder.app.domain.model.Event) {
    val label = Uri.encode(event.venueName.ifBlank { event.address })
    val query = Uri.encode(
        listOf(event.venueName, event.address)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    )
    val latitude = event.latitude
    val longitude = event.longitude

    val candidates = buildList {
        if (latitude != null && longitude != null) {
            add(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
                )
            )
            add(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
                    )
                )
            )
        }
        if (query.isNotEmpty()) {
            add(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                )
            )
        }
    }

    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // try the next candidate
        }
    }

    // Nothing resolved directly: let the user pick any browser/maps app.
    candidates.lastOrNull()?.let { last ->
        try {
            context.startActivity(Intent.createChooser(last, null))
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // fall through to the toast below
        }
    }

    Toast.makeText(
        context,
        context.getString(R.string.directions_unavailable),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * Branded share bottom sheet (showcase3): five solid-colour target circles
 * over a grayscale social-shortcut row. Targets open the matching app when
 * installed and fall back to the system share chooser otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    shareText: String,
    link: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun sendTo(packageName: String?, type: String = "text/plain") {
        val build = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (packageName != null) setPackage(packageName)
        }
        val target = if (packageName == null ||
            build.resolveActivity(context.packageManager) != null
        ) {
            build
        } else {
            Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        }
        context.startActivity(Intent.createChooser(target, null))
    }

    fun copyLink() {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("link", link ?: shareText)
        )
        Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT)
            .show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.share_with_friends),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareTarget(
                    label = "WhatsApp",
                    color = Color(0xFF25D366),
                    icon = R.drawable.ic_brand_whatsapp
                ) { sendTo("com.whatsapp") }
                ShareTarget(
                    label = "Messages",
                    color = Color(0xFF3DDC84),
                    icon = R.drawable.ic_ms_chat
                ) { sendTo("com.google.android.apps.messaging") }
                ShareTarget(
                    label = "Email",
                    color = Color(0xFF1A73E8),
                    icon = R.drawable.ic_ms_mail
                ) { sendTo(null, type = "message/rfc822") }
                ShareTarget(
                    label = "Copy Link",
                    color = Color(0xFF7C4DFF),
                    icon = R.drawable.ic_ms_copy
                ) { copyLink() }
                ShareTarget(
                    label = "Nearby",
                    color = Color(0xFF4285F4),
                    icon = R.drawable.ic_ms_share
                ) { sendTo(null) }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(16.dp))

            // Grayscale social shortcuts row (showcase3).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SocialShortcut("Facebook", R.drawable.ic_brand_facebook) { sendTo("com.facebook.katana") }
                SocialShortcut("X", R.drawable.ic_brand_x) { sendTo("com.twitter.android") }
                SocialShortcut("LinkedIn", R.drawable.ic_brand_linkedin) { sendTo("com.linkedin.android") }
                SocialShortcut("Pinterest", R.drawable.ic_brand_pinterest) { sendTo("com.pinterest") }
                SocialShortcut("Instagram", R.drawable.ic_brand_instagram) { sendTo("com.instagram.android") }
                SocialShortcut("TikTok", R.drawable.ic_brand_tiktok) { sendTo("com.zhiliaoapp.musically") }
            }
        }
    }
}

/** One solid-colour share target circle with its real brand/glyph icon. */
@Composable
private fun ShareTarget(
    label: String,
    color: Color,
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .widthIn(min = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** One grayscale social shortcut circle (real brand glyph) with its label. */
@Composable
private fun SocialShortcut(
    label: String,
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .widthIn(min = 44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * Rounded provenance pill so live, cached, imported and sample data are
 * distinguishable without reading a bare "Source:" string.
 */
@Composable
private fun ProvenanceBadge(provenance: String) {
    val (label, color) = when (provenance.lowercase()) {
        "live" -> "Live" to Color(0xFF00C98D)
        "cached" -> "Cached" to Color(0xFF4285F4)
        "imported" -> "Imported" to Color(0xFFF59E0B)
        else -> "Sample" to Color(0xFF94A3B8)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/** Compact event-accessibility chip: 14dp icon + label, 8dp radius. */
@Composable
private fun AccessibilityChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            Image(
                painter = painterResource(R.drawable.evt_weather),
                contentDescription = "Weather",
                modifier = Modifier.size(30.dp)
            )
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

/**
 * "Going together?" banner built to the Invite Friends design spec:
 * white avatar disc + vertical divider, #FFFFFF primary / #94A3BB secondary
 * copy, and a 44dp emerald pill (20dp icon, 22dp radius, #00C98D with
 * #0B1929 content) whose pressed state darkens and whose disabled state is
 * grey. Spacing: 16dp edge-to-avatar, 12dp divider-to-copy, 16dp copy-to-
 * button, 16dp button-to-edge.
 */
@Composable
private fun GroupPlanningCard(
    members: Set<String>,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The spec palette is dark-anchored (#0B1929); mirror it in dark mode and
    // fall back to theme colours in light mode so contrast stays readable.
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = if (isDarkSurface) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor =
        if (isDarkSurface) Color(0xFF94A3BB) else MaterialTheme.colorScheme.onSurfaceVariant

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonContainer by animateColorAsState(
        targetValue = if (pressed) Color(0xFF00A274) else Color(0xFF00C98D),
        label = "InviteButtonContainer"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // White avatar disc: ghost avatar when nobody has joined, a single
            // disc for one member, then an overlapping stack + "+N" badge.
            // Width tracks the stack so the divider and copy sit tight to it.
            Box(
                modifier = Modifier.width(
                    when {
                        members.size <= 1 -> 32.dp
                        members.size == 2 -> 48.dp
                        else -> 64.dp
                    }
                )
            ) {
                val shown = members.take(2)
                if (shown.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.usr_account),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                shown.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .offset(x = (index * 16).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.usr_account),
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (members.size > 2) {
                    Box(
                        modifier = Modifier
                            .offset(x = 32.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${members.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // Spec divider between the avatar and the copy block.
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Going together?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (members.isEmpty()) "Bring your crew along"
                    else "${members.size} going",
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onInvite,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainer,
                    contentColor = Color(0xFF0B1929),
                    disabledContainerColor = Color(0xFF334155),
                    disabledContentColor = Color(0xFF94A3BB)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                interactionSource = interactionSource
            ) {
                Image(
                    painter = painterResource(R.drawable.evt_attendees),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(LocalContentColor.current)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Invite Friends",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}



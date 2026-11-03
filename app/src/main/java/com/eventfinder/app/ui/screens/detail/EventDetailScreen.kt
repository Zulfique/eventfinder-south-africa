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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    onOrganizerClick: (String) -> Unit = {}
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
                            Text(
                                "Source: ${event.provenance}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                        openDirections(context, event)
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.evt_directions),
                                        contentDescription = "Directions",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.directions))
                                }
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
                            FilledTonalButton(
                                onClick = { showShareSheet = true },
                                 modifier = Modifier.fillMaxWidth().height(44.dp)
                             ) {
                                 Image(
                                     painter = painterResource(R.drawable.evt_share),
                                     contentDescription = "Share",
                                     modifier = Modifier.size(20.dp)
                                 )
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

    // Branded share sheet (showcase3): WhatsApp / Messages / Email /
    // Copy Link / Nearby shortcuts plus the social row.
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
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareTarget(
                    label = "WhatsApp",
                    color = Color(0xFF25D366),
                    icon = Icons.Filled.Chat
                ) { sendTo("com.whatsapp") }
                ShareTarget(
                    label = "Messages",
                    color = Color(0xFF3DDC84),
                    icon = Icons.Filled.Sms
                ) { sendTo("com.google.android.apps.messaging") }
                ShareTarget(
                    label = "Email",
                    color = Color(0xFF1A73E8),
                    icon = Icons.Filled.Email
                ) { sendTo(null, type = "message/rfc822") }
                ShareTarget(
                    label = "Copy Link",
                    color = Color(0xFF7C4DFF),
                    icon = Icons.Outlined.ContentCopy
                ) { copyLink() }
                ShareTarget(
                    label = "Nearby",
                    color = Color(0xFF4285F4),
                    icon = Icons.Outlined.Share
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
                SocialShortcut("f") { sendTo("com.facebook.katana") }
                SocialShortcut("X") { sendTo("com.twitter.android") }
                SocialShortcut("in") { sendTo("com.linkedin.android") }
                SocialShortcut("P") { sendTo("com.pinterest") }
                SocialShortcut("IG") { sendTo("com.instagram.android") }
                SocialShortcut("TT") { sendTo("com.zhiliaoapp.musically") }
            }
        }
    }
}

/** One solid-colour share target circle with its label. */
@Composable
private fun ShareTarget(
    label: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One grayscale social shortcut circle (monogram) with its label. */
@Composable
private fun SocialShortcut(
    monogram: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = monogram,
            fontSize = 10.sp,
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
 * Showcase "Going together?" card: overlapping attendee avatars (or a
 * friendly ghost avatar before anyone joins), the going count, and the green
 * Invite Friends pill that opens the full Group Planning screen.
 */
@Composable
private fun GroupPlanningCard(
    members: Set<String>,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Clear gaps so the avatar stack, the copy and the Invite
            // Friends pill never crowd or overlap each other.
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact stack: 32dp avatars overlapping on an 18dp grid keeps the
            // copy column wide enough for "Going together?" without ellipsis.
            Box(modifier = Modifier.width(82.dp)) {
                val shown = members.take(3)
                if (shown.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F5)),
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
                            .offset(x = (index * 18).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F5))
                            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.usr_account),
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (members.size > 3) {
                    Box(
                        modifier = Modifier
                            .offset(x = (3 * 18).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${members.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Going together?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (members.isEmpty()) "Bring your crew along"
                    else "${members.size} going",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onInvite,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = RoundedCornerShape(50)
            ) {
                Image(
                    painter = painterResource(R.drawable.evt_attendees),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
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



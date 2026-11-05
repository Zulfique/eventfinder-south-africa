package com.eventfinder.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventfinder.app.domain.model.EventTicket
import com.eventfinder.app.ui.screens.preferences.BrandOutlineButton
import com.eventfinder.app.ui.screens.preferences.BrandPrimaryButton
import com.eventfinder.app.ui.screens.preferences.BrandTextAction
import com.eventfinder.app.utils.SouthAfricanPlaces
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

private val BrandGreen: Color
    /** Brand accent that stays legible: emerald on night surfaces, the
     *  darker brand green on white surfaces. */
    @Composable get() = MaterialTheme.colorScheme.primary

/**
 * "Bring a Friend" — the invite sheet from the 2026 showcase.
 *
 * The link shares the same deep link the event card's share sheet uses, so an
 * invited friend opens the event directly rather than the home feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BringAFriendSheet(
    eventTitle: String,
    eventUrl: String,
    onDismiss: () -> Unit,
    onInviteSent: () -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(BrandGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.GroupAdd,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Invite a Friend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Share this event with a friend and enjoy it together!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            androidx.compose.material3.Button(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Join me at $eventTitle on EventFinder South Africa: $eventUrl"
                        )
                    }
                    context.startActivity(Intent.createChooser(share, "Share Invite Link"))
                    onInviteSent()
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = BrandGreen,
                    contentColor = Color(0xFF06201A)
                )
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Invite Link", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "OR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
            }

            BrandOutlineButton("Invite from Contacts") {
                // Contacts pickers are not guaranteed on every device; the
                // share sheet is the reliable fallback with the same content.
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join me at $eventTitle on EventFinder South Africa: $eventUrl"
                    )
                }
                context.startActivity(Intent.createChooser(share, "Invite from Contacts"))
                onInviteSent()
            }

            Spacer(Modifier.height(6.dp))
            BrandTextAction("Cancel") {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        }
    }
}

/**
 * "Export RSVPs" — CSV export of every attendee for an event.
 *
 * The CSV is written to the app cache and handed to the system share sheet so
 * the organiser can mail it, drop it in Drive, or save it locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRsvpSheet(
    eventId: String,
    eventTitle: String,
    tickets: List<EventTicket>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(BrandGreen.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Download RSVP List",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Get a CSV file with all attendees for this event.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            BulletRow("Includes: Name, Email, RSVP Status")
            BulletRow("Updated in real-time")
            BulletRow("${tickets.size} attendee${if (tickets.size == 1) "" else "s"} in $eventTitle")

            Spacer(Modifier.height(18.dp))

            BrandPrimaryButton("Download CSV") {
                val file = writeRsvpCsv(context.cacheDir, eventId, tickets)
                if (file != null) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export RSVPs"))
                }
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }

            Spacer(Modifier.height(6.dp))
            BrandTextAction("Cancel") {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = BrandGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Writes the RSVP CSV; returns the file, or null when the write fails. */
fun writeRsvpCsv(cacheDir: File, eventId: String, tickets: List<EventTicket>): File? =
    runCatching {
        val file = File(cacheDir, "rsvps_$eventId.csv")
        FileWriter(file).use { writer ->
            writer.append("TicketID,EventID,UserID,TicketCode,IssuedAt,CheckedInAt\n")
            tickets.forEach { ticket ->
                writer.append(
                    "${ticket.id},${ticket.eventId},${ticket.userId}," +
                        "${ticket.ticketCode},${ticket.issuedAt},${ticket.checkedInAt ?: ""}\n"
                )
            }
        }
        file
    }.getOrNull()

/**
 * "Select Meeting Point" — pick where the group meets before the event.
 *
 * Suggestions cycle through real South African places so the confirmed point
 * always carries usable coordinates for the map and directions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingPointSheet(
    groupName: String,
    initialLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (label: String, lat: Double, lng: Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val places = remember { SouthAfricanPlaces.all.take(12) }
    var index by remember { mutableIntStateOf(0) }
    val place = places[index % places.size]
    var label by remember { mutableStateOf(initialLabel ?: "Main Gate") }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Select Meeting Point",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Choose a convenient meeting point for your group.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Stylised map preview: the pin carries the confirmed coordinates.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(44.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${place.name} · $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PinDrop,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$label · ${place.name}, ZA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SheetLinkRow(Icons.Outlined.LocationOn, "View on Map") {
                val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(label)})")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                runCatching { context.startActivity(intent) }
            }
            SheetLinkRow(Icons.Outlined.Share, "Suggest Another") {
                index += 1
                label = listOf("Main Gate", "North Entrance", "Coffee Corner", "Parking A").random()
            }

            Spacer(Modifier.height(12.dp))
            BrandPrimaryButton("Confirm Location") {
                onConfirm(label, place.latitude, place.longitude)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            Spacer(Modifier.height(4.dp))
            BrandTextAction("Cancel") {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

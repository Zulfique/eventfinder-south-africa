package com.eventfinder.app.ui.screens.tickets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventTicket
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

private val BrandGreen = Color(0xFF00C98D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScannerScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ticketRepository = remember { container.ticketRepository }
    var ticketCode by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var checkedInTickets by remember { mutableStateOf<List<EventTicket>>(emptyList()) }
    var showSuccess by remember { mutableStateOf<EventTicket?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        checkedInTickets = ticketRepository.getAllTicketsForEvent(eventId)
            .filter { it.checkedInAt != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-in", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Green scanning frame, as in the 2026 showcase.
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .border(3.dp, BrandGreen, RoundedCornerShape(24.dp))
                    .background(BrandGreen.copy(alpha = 0.06f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Scan QR Code for Check-in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Point the scanner at the attendee's ticket, or type the ticket code below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = ticketCode,
                onValueChange = { ticketCode = it.uppercase() },
                label = { Text("Ticket code") },
                placeholder = { Text("e.g. A1B2C3D4") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (verifying || ticketCode.isBlank()) return@Button
                    verifying = true
                    scope.launch {
                        val result = ticketRepository.verifyAndCheckIn(ticketCode.trim().uppercase())
                        verifying = false
                        errorMessage = null
                        if (result.isSuccess) {
                            val ticket = result.getOrNull()
                            scanResult = ticket?.let { ScanResult.Success(it) }
                            showSuccess = ticket
                            checkedInTickets = ticketRepository.getAllTicketsForEvent(eventId)
                                .filter { it.checkedInAt != null }
                        } else {
                            val failure = result.exceptionOrNull()?.message ?: "Unknown error"
                            scanResult = ScanResult.Error(failure)
                            errorMessage = failure
                        }
                        ticketCode = ""
                    }
                },
                enabled = !verifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify & Check In", fontWeight = FontWeight.Bold)
                }
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Checked-in attendees (${checkedInTickets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val allTickets = ticketRepository.getAllTicketsForEvent(eventId)
                            val csvFile = File(context.cacheDir, "attendees_$eventId.csv")
                            FileWriter(csvFile).use { writer ->
                                writer.append("TicketID,EventID,UserID,TicketCode,IssuedAt,CheckedInAt\n")
                                allTickets.forEach { t ->
                                    writer.append(
                                        "${t.id},${t.eventId},${t.userId},${t.ticketCode},${t.issuedAt},${t.checkedInAt ?: ""}\n"
                                    )
                                }
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                csvFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export attendee CSV"))
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(checkedInTickets) { ticket -> CheckedInTicketRow(ticket) }
            }
        }
    }

    if (showSuccess != null) {
        AlertDialog(
            onDismissRequest = { showSuccess = null },
            confirmButton = {
                Button(
                    onClick = { showSuccess = null },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Done") }
            },
            text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(BrandGreen.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = BrandGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Checked In!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "You're all set. Enjoy the event.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ticket ${showSuccess?.ticketCode.orEmpty()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandGreen
                )
            }
            }
        )
    }
}

@Composable
fun CheckedInTicketRow(ticket: EventTicket) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = BrandGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Ticket ${ticket.ticketCode}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            "Checked in",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

sealed class ScanResult(val message: String) {
    class Success(val ticket: EventTicket) : ScanResult("Ticket verified and checked in!")
    class Error(val error: String) : ScanResult(error)
}

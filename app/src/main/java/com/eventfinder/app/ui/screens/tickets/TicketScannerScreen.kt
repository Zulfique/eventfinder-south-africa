package com.eventfinder.app.ui.screens.tickets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventTicket
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScannerScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ticketRepository = remember { container.ticketRepository }
    var ticketCode by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var checkedInTickets by remember { mutableStateOf<List<EventTicket>>(emptyList()) }

    LaunchedEffect(eventId) {
        ticketRepository.getAllTicketsForEvent(eventId).let { 
            checkedInTickets = it.filter { t -> t.checkedInAt != null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket Check-in") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Scan or Enter Ticket Code", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = ticketCode,
                        onValueChange = { ticketCode = it },
                        label = { Text("Ticket Code (e.g. A1B2C3D4)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val result = ticketRepository.verifyAndCheckIn(ticketCode.uppercase())
                                scanResult = if (result.isSuccess) {
                                    ScanResult.Success(result.getOrNull()!!)
                                } else {
                                    ScanResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                                }
                                if (result.isSuccess) {
                                    checkedInTickets = ticketRepository.getAllTicketsForEvent(eventId).filter { it.checkedInAt != null }
                                }
                                ticketCode = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify & Check In")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Checked-in Attendees (${checkedInTickets.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val allTickets = ticketRepository.getAllTicketsForEvent(eventId)
                            val csvFile = File(context.cacheDir, "attendees_$eventId.csv")
                            FileWriter(csvFile).use { writer ->
                                writer.append("TicketID,EventID,UserID,TicketCode,IssuedAt,CheckedInAt\n")
                                allTickets.forEach { t ->
                                    writer.append("${t.id},${t.eventId},${t.userId},${t.ticketCode},${t.issuedAt},${t.checkedInAt ?: ""}\n")
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
                            context.startActivity(Intent.createChooser(intent, "Export Attendee CSV"))
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export CSV")
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(checkedInTickets) { ticket ->
                    CheckedInTicketRow(ticket)
                }
            }
        }
    }
}

@Composable
fun CheckedInTicketRow(ticket: EventTicket) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
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

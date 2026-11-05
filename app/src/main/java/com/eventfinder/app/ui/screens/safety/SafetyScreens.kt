package com.eventfinder.app.ui.screens.safety

import com.eventfinder.app.ui.theme.LocalAccentText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.EventReport
import com.eventfinder.app.data.repository.ReportRepository
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.screens.preferences.BrandDangerButton
import com.eventfinder.app.ui.screens.preferences.BrandOutlineButton
import com.eventfinder.app.ui.screens.preferences.BrandPrimaryButton
import com.eventfinder.app.ui.screens.preferences.BrandTextAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val BrandGreen: Color
    /** Brand accent that stays legible: emerald on night surfaces, the
     *  darker brand green on white surfaces. */
    @Composable get() = MaterialTheme.colorScheme.primary
private val DangerRed = Color(0xFFEF4444)

/** Reasons offered on the Report a Scam screen, in showcase order. */
private val SCAM_REASONS = listOf(
    "Fake event listings",
    "Payment scams",
    "Suspicious organizers",
    "Other suspicious activity"
)

// ------------------------------------------------------------- report a scam

private class ReportScamViewModel(
    private val repository: ReportRepository,
    private val preferences: com.eventfinder.app.data.store.UserPreferences,
    private val targetId: String,
    private val targetType: String
) : ViewModel() {

    var isSubmitting by androidx.compose.runtime.mutableStateOf(false)
        private set

    var submitted by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun submit(reason: String, details: String, onDone: () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        viewModelScope.launch {
            val fullReason = if (details.isBlank()) reason else "$reason: $details"
            val reporterId = preferences.sessionUserId.first() ?: "local"
            repository.report(
                targetType = targetType,
                targetId = targetId,
                reason = fullReason.take(500),
                reporterId = reporterId
            )
            isSubmitting = false
            submitted = true
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, targetId: String, targetType: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReportScamViewModel(
                        repository = container.reportRepository,
                        preferences = container.preferences,
                        targetId = targetId,
                        targetType = targetType
                    )
                }
            }
    }
}

/**
 * "Report a Scam" — one screen, one decision: pick what is wrong, add optional
 * detail, submit. The report lands in Room and shows up on Moderation at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScamScreen(
    container: AppContainer,
    targetId: String,
    targetType: String = "event",
    onBack: () -> Unit
) {
    val vm: ReportScamViewModel = viewModel(
        factory = ReportScamViewModel.factory(container, targetId, targetType)
    )
    val context = LocalContext.current
    var selected by remember { mutableStateOf(SCAM_REASONS.first()) }
    var details by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Report Scam", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(DangerRed.copy(alpha = 0.10f), CircleShape)
                    .border(1.dp, DangerRed.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Report a Scam",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Help keep EventFinder safe for everyone. Report suspicious activity, fake events or fraud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            SCAM_REASONS.forEach { reason ->
                ScamReasonRow(
                    label = reason,
                    selected = selected == reason,
                    onClick = { selected = reason }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 500) details = it },
                placeholder = { Text("Add more details (optional)...") },
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("${details.length}/500", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(Modifier.height(16.dp))
            BrandPrimaryButton(if (vm.isSubmitting) "Submitting…" else "Submit Report") {
                vm.submit(selected, details) {
                    android.widget.Toast
                        .makeText(context, "Report submitted", android.widget.Toast.LENGTH_SHORT)
                        .show()
                    onBack()
                }
            }
            BrandTextAction("Cancel", onBack)
        }
    }
}

@Composable
private fun ScamReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ---------------------------------------------------------------- moderation

data class ModerationUiState(
    val reports: List<EventReport> = emptyList()
)

private class ModerationViewModel(private val repository: ReportRepository) : ViewModel() {

    val state = repository.observeReports()
        .map { ModerationUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModerationUiState())

    fun setResolved(id: String, resolved: Boolean) {
        viewModelScope.launch { repository.setReportResolved(id, resolved) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ModerationViewModel(container.reportRepository) }
        }
    }
}

/**
 * "Moderation" — organiser-facing queue of scam reports.
 *
 * Tab 1 lists every report with a Review action that expands it; the footer
 * filters by Pending / Resolved / All, and Actions explains what each flag means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: ModerationViewModel = viewModel(factory = ModerationViewModel.factory(container))
    val state by vm.state.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("Pending") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val pending = state.reports.filter { !it.resolved }
    val resolved = state.reports.filter { it.resolved }
    val visible = when (filter) {
        "Resolved" -> resolved
        "All" -> state.reports
        else -> pending
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Moderation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ModerationFooter(filter = filter, onFilterChange = { filter = it })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = BrandGreen,
                divider = {}
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Reports (${state.reports.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Actions (${resolved.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (tab == 0) {
                if (visible.isEmpty()) {
                    EmptyModeration(if (filter == "Resolved") "No resolved reports yet" else "No open reports")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { report ->
                            ModerationRow(
                                report = report,
                                expanded = expandedId == report.id,
                                onReview = {
                                    expandedId = if (expandedId == report.id) null else report.id
                                },
                                onToggleResolved = {
                                    vm.setResolved(report.id, !report.resolved)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Resolved reports stay in the archive so you can review past decisions. " +
                            "Reopen one from the Reports tab if a case needs another look.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionSummary(Icons.Outlined.TaskAlt, "Resolved", resolved.size)
                    ActionSummary(Icons.Outlined.Pending, "Pending", pending.size)
                    Spacer(Modifier.height(16.dp))
                    BrandOutlineButton("Export report history") { }
                }
            }
        }
    }
}

@Composable
private fun ActionSummary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        Text("$count", color = LocalAccentText.current, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyModeration(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModerationRow(
    report: EventReport,
    expanded: Boolean,
    onReview: () -> Unit,
    onToggleResolved: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.reason.substringBefore(':').ifBlank { "Scam alert" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${timeAgo(report.createdAt)} ago · ${report.targetType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onReview)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (report.resolved) "Resolved" else "Review",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalAccentText.current,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                    contentDescription = null,
                    tint = LocalAccentText.current,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(12.dp)
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            val detail = report.reason.substringAfter(':', "").trim()
            Text(
                text = detail.ifBlank { "No additional details were provided." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            BrandDangerButton(
                text = if (report.resolved) "Reopen report" else "Mark as resolved",
                onClick = onToggleResolved
            )
        }
    }
}

@Composable
private fun ModerationFooter(filter: String, onFilterChange: (String) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterTab("Pending", Icons.Outlined.Pending, filter, onFilterChange)
            FilterTab("Resolved", Icons.Outlined.TaskAlt, filter, onFilterChange)
            FilterTab("All", Icons.Outlined.History, filter, onFilterChange)
        }
    }
}

@Composable
private fun FilterTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: String,
    onSelect: (String) -> Unit
) {
    val selected = current == label
    Column(
        modifier = Modifier
            .clickable { onSelect(label) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun timeAgo(createdAt: Long): String {
    val minutes = ((System.currentTimeMillis() - createdAt) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        else -> "${minutes / 1_440}d"
    }
}

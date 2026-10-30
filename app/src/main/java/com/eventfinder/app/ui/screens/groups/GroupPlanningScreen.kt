package com.eventfinder.app.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.GroupPlan
import com.eventfinder.app.data.repository.GroupPlanningRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPlanningScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { container.groupPlanningRepository }
    var plans by remember { mutableStateOf<List<GroupPlan>>(emptyList()) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        repository.observePlansForEvent(eventId).collect { plansT ->
            plans = plansT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan with Friends") },
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
                .padding(16.dp)
        ) {
            Text(
                "Coordinate your trip to the event. Create a plan or join one already started by friends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No plans found for this event", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start a Group Plan")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(plans) { plan ->
                        PlanCard(
                            plan = plan,
                            isSelected = selectedPlanId == plan.id,
                            onSelect = { selectedPlanId = plan.id }
                        )
                    }
                }
            }

            if (selectedPlanId != null) {
                Spacer(Modifier.weight(1f))
                PlanDetailPanel(
                    plan = plans.find { it.id == selectedPlanId },
                    onJoin = {
                        scope.launch {
                            repository.joinPlan(selectedPlanId!!, "current_user") // simplified
                        }
                    }
                )
            }

            if (showCreateDialog) {
                CreatePlanDialog(
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { label, lat, lng ->
                        scope.launch {
                            repository.createPlan(
                                GroupPlan(
                                    id = java.util.UUID.randomUUID().toString(),
                                    eventId = eventId,
                                    creatorId = "current_user",
                                    members = setOf("current_user"),
                                    meetingPointLabel = label,
                                    meetingLat = lat,
                                    meetingLng = lng,
                                    proposedTime = System.currentTimeMillis(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            showCreateDialog = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: GroupPlan, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = plan.meetingPointLabel ?: "Unnamed Meeting Point",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${plan.members.size} friends joined",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PlanDetailPanel(plan: GroupPlan?, onJoin: () -> Unit) {
    if (plan == null) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Plan Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(plan.meetingPointLabel ?: "No meeting point set", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Join this Group Plan")
            }
        }
    }
}

@Composable
private fun CreatePlanDialog(onDismiss: () -> Unit, onConfirm: (String, Double?, Double?) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a Group Plan") },
        text = {
            Column {
                Text("Where should everyone meet?", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Meeting Point Label (e.g. 'Main Gate')") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label, null, null) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

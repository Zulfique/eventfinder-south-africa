package com.eventfinder.app.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventfinder.app.data.store.EventCollection
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.components.EventFinderImage
import com.eventfinder.app.ui.components.categoryPngIcon
import com.eventfinder.app.ui.screens.preferences.BrandOutlineButton
import com.eventfinder.app.ui.screens.preferences.PreferenceHeading
import com.eventfinder.app.ui.screens.preferences.PreferenceHeroIcon
import com.eventfinder.app.ui.screens.preferences.PreferenceScaffold
import com.eventfinder.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

private val BrandGreen: Color
    /** Brand accent that stays legible: emerald on night surfaces, the
     *  darker brand green on white surfaces. */
    @Composable get() = MaterialTheme.colorScheme.primary

/**
 * "Event Collections" — user-made lists of events (2026 showcase, screen 12).
 *
 * Each collection is either category-backed (its events are re-read from the
 * local cache on every open, so it never goes stale) or an explicit id list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEventClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val preferences = remember { container.preferences }
    val collections by preferences.eventCollections.collectAsState(initial = emptyList())
    val events by remember {
        container.eventRepository.observeAllEvents().map { list -> list.associateBy { it.id } }
    }.collectAsState(initial = emptyMap())

    var showCreate by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }

    fun collectionEvents(collection: EventCollection) =
        if (collection.categoryKey != null) {
            events.values.filter { it.category.labelKey == collection.categoryKey }
        } else {
            collection.eventIds.mapNotNull { events[it] }
        }

    PreferenceScaffold(title = "Event Collections", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Collections)
        PreferenceHeading(
            title = "Your Collections",
            subtitle = "Group events into lists you can come back to."
        )

        if (collections.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "No collections yet. Create one to start grouping events.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        collections.forEach { collection ->
            val items = collectionEvents(collection)
            val expanded = openId == collection.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openId = if (expanded) null else collection.id }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val category = EventCategory.entries.firstOrNull { it.labelKey == collection.categoryKey }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(BrandGreen.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (category != null) {
                            Image(
                                painter = painterResource(categoryPngIcon(category)),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Collections,
                                contentDescription = null,
                                tint = BrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.fillMaxWidth(0.75f)) {
                        Text(
                            collection.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${items.size} event${if (items.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { preferences.deleteCollection(collection.id) } }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Delete ${collection.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (expanded) {
                    if (items.isEmpty()) {
                        Text(
                            "No events match this collection yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items.take(12).forEach { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEventClick(event.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EventFinderImage(
                                imageUrl = event.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 10.dp
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    DateTimeUtils.formatShortDate(event.startDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        BrandOutlineButton("＋  Create Collection") { showCreate = true }
        Spacer(Modifier.height(24.dp))
    }

    if (showCreate) {
        CreateCollectionDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, category ->
                scope.launch {
                    preferences.saveCollection(
                        EventCollection(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            categoryKey = category?.labelKey
                        )
                    )
                }
                showCreate = false
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, category: EventCategory?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<EventCategory?>(EventCategory.MUSIC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Collection") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection name") },
                    placeholder = { Text("Weekend plans") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventCategory.entries.filter { it != EventCategory.OTHER }.take(8).forEach { option ->
                        val selected = option == category
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) BrandGreen.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(50)
                                )
                                .border(
                                    1.dp,
                                    if (selected) BrandGreen else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50)
                                )
                                .clickable { category = option }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                option.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) onCreate(trimmed, category)
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

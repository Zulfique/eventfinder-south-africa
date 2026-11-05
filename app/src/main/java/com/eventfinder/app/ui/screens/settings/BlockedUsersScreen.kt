package com.eventfinder.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.screens.preferences.PreferenceHeading
import com.eventfinder.app.ui.screens.preferences.PreferenceHeroIcon
import com.eventfinder.app.ui.screens.preferences.PreferenceScaffold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val BrandGreen: Color
    /** Brand accent that stays legible: emerald on night surfaces, the
     *  darker brand green on white surfaces. */
    @Composable get() = MaterialTheme.colorScheme.primary

/**
 * "Blocked users" — the management screen behind the block action on organizer
 * profiles. Blocking already hid the other person's events and reviews; until
 * now there was no way to see or reverse it.
 */
@Composable
fun BlockedUsersScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val reports = remember { container.reportRepository }
    val auth = remember { container.authRepository }

    var reload by remember { mutableIntStateOf(0) }

    val blocked by produceState(initialValue = emptyList<String>(), reload) {
        val user = auth.currentUser.first()
        value = if (user == null) emptyList() else reports.blockedIds(user.id).toList()
    }

    val organizerNames by produceState(initialValue = emptyMap<String, String>()) {
        container.eventRepository.observeAllEvents()
            .map { events ->
                events.groupBy { it.organizerId }
                    .mapValues { (_, list) -> list.first().organizerName }
            }
            .collect { value = it }
    }

    PreferenceScaffold(title = "Blocked Users", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Block)
        PreferenceHeading(
            title = "Blocked Users",
            subtitle = "Blocked people are hidden from your feed, events and reviews."
        )

        if (blocked.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "You haven't blocked anyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        blocked.forEach { blockedId ->
            val name = organizerNames[blockedId] ?: blockedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(BrandGreen.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.trim().take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = "Blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            val user = auth.currentUser.first() ?: return@launch
                            reports.unblock(user.id, blockedId)
                            reload += 1
                        }
                    }
                ) {
                    Text("Unblock", color = BrandGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

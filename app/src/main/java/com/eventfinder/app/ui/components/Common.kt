package com.eventfinder.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.EventView
import com.eventfinder.app.utils.DateTimeUtils

/** One-shot user feedback (snackbars / toasts) produced by ViewModels. */
sealed interface UiMessage {
    data class Resource(val resId: Int, val args: List<Any> = emptyList()) : UiMessage
    data class Literal(val text: String) : UiMessage
}

/** Resolves a [UiMessage] against the current resources. */
@Composable
fun UiMessage?.resolveText(): String? = resolve(androidx.compose.ui.platform.LocalContext.current)

/**
 * Non-composable resolver for use inside coroutine/Flow collectors (e.g. a
 * `LaunchedEffect` that observes snackbar messages).
 */
fun UiMessage?.resolve(context: android.content.Context): String? = when (this) {
    null -> null
    is UiMessage.Resource -> context.getString(resId, *args.toTypedArray())
    is UiMessage.Literal -> text
}

/** Full-width loading indicator. */
@Composable
fun LoadingView(message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Centred icon + message used when a list has nothing to show. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Horizontal scrolling category chips (Screen 4 of the design). */
@Composable
fun CategoryChips(
    selected: EventCategory?,
    onSelect: (EventCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_categories)) },
                leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) }
            )
        }
        items(EventCategory.entries.filter { it != EventCategory.OTHER }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(stringResource(categoryLabel(category))) }
            )
        }
    }
}

/** Maps a category to its localized label resource. */
fun categoryLabel(category: EventCategory): Int = when (category) {
    EventCategory.MUSIC -> R.string.cat_music
    EventCategory.SPORTS -> R.string.cat_sports
    EventCategory.FOOD -> R.string.cat_food
    EventCategory.ARTS -> R.string.cat_arts
    EventCategory.COMMUNITY -> R.string.cat_community
    EventCategory.BUSINESS -> R.string.cat_business
    EventCategory.OTHER -> R.string.all_categories
}

/** Reusable event card used on the Home list, Search and Favorites screens. */
@Composable
fun EventCard(
    view: EventView,
    onClick: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val event = view.event
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = stringResource(R.string.event_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        DateTimeUtils.formatShortDate(event.startDate),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        event.venueName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (view.distanceKm != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${com.eventfinder.app.utils.DistanceCalculator.displayDistanceKm(view.distanceKm)} km " +
                            stringResource(R.string.distance_away),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onFavoriteToggle != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (event.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (event.isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onFavoriteToggle() },
                    tint = if (event.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
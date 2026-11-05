package com.eventfinder.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
        // Brand-coloured illustration instead of a bare glyph (FR-04).
        Image(
            painter = painterResource(R.drawable.img_empty_state),
            contentDescription = null,
            modifier = Modifier.size(140.dp)
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

/**
 * Interactive category chip: a full-colour PNG icon that springs when tapped and
 * a chip container that flips to brand green with a white label while selected.
 */
@Composable
fun PngCategoryChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier
) {
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "chipContainer"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "chipBorder"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InteractivePngIcon(
            iconRes,
            contentDescription = null,
            active = selected,
            size = 18.dp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            PngCategoryChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = stringResource(R.string.all_categories),
                iconRes = R.drawable.cat_all
            )
        }
        items(EventCategory.entries.filter { it != EventCategory.OTHER }) { category ->
            PngCategoryChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = stringResource(categoryLabel(category)),
                iconRes = categoryPngIcon(category)
            )
        }
    }
}

/** Maps a category to its localized label resource. */
fun categoryLabel(category: EventCategory): Int = when (category) {
    EventCategory.MUSIC -> R.string.cat_music
    EventCategory.CONCERTS -> R.string.cat_concerts
    EventCategory.FESTIVALS -> R.string.cat_festivals
    EventCategory.NIGHTLIFE -> R.string.cat_nightlife
    EventCategory.FOOD -> R.string.cat_food
    EventCategory.SPORTS -> R.string.cat_sports
    EventCategory.ARTS -> R.string.cat_arts
    EventCategory.COMEDY -> R.string.cat_comedy
    EventCategory.BUSINESS -> R.string.cat_business
    EventCategory.NETWORKING -> R.string.cat_networking
    EventCategory.TECHNOLOGY -> R.string.cat_technology
    EventCategory.EDUCATION -> R.string.cat_education
    EventCategory.FAMILY -> R.string.cat_family
    EventCategory.MARKETS -> R.string.cat_markets
    EventCategory.OUTDOORS -> R.string.cat_outdoors
    EventCategory.TRAVEL -> R.string.cat_travel
    EventCategory.CHARITY -> R.string.cat_charity
    EventCategory.THEATRE -> R.string.cat_theatre
    EventCategory.MOVIES -> R.string.cat_movies
    EventCategory.FASHION -> R.string.cat_fashion
    EventCategory.CARS -> R.string.cat_cars
    EventCategory.WELLNESS -> R.string.cat_wellness
    EventCategory.RELIGION -> R.string.cat_religion
    EventCategory.COMMUNITY -> R.string.cat_community
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
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = "${stringResource(R.string.event_image)}: ${event.title}",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_image_placeholder),
                error = painterResource(R.drawable.ic_image_placeholder),
                modifier = Modifier
                    .size(width = 108.dp, height = 112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.category.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.evt_calendar),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(5.dp))

                    Text(
                        text = DateTimeUtils.formatShortDate(event.startDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.evt_venue),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(5.dp))

                    Text(
                        text = event.venueName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onFavoriteToggle != null) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FavoritePngIcon(
                        isFavorite = view.isFavorite,
                        size = 26.dp,
                        onToggle = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.TextHandleMove
                            )
                            onFavoriteToggle()
                        }
                    )
                }
            }
        }
    }
}

/**
 * True when Settings → Accessibility → Reduced motion is on. Components read
 * this instead of animating unconditionally: it respects the user's motion
 * preference and removes continuous animation work on low-RAM devices.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Shimmer placeholder used for skeleton loading states. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    // A static block instead of an endless shimmer when motion is reduced.
    if (LocalReducedMotion.current) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    ).value
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset(x - 200f, 0f),
        end = Offset(x, 0f)
    )
    Box(modifier = modifier.background(brush, RoundedCornerShape(8.dp)))
}






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
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

/** Category glyph used when an event has no published cover image. */
fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.MUSIC -> Icons.Outlined.MusicNote
    EventCategory.SPORTS -> Icons.Outlined.Sports
    EventCategory.FOOD -> Icons.Outlined.Restaurant
    EventCategory.ARTS -> Icons.Outlined.Palette
    EventCategory.BUSINESS -> Icons.Outlined.BusinessCenter
    EventCategory.COMMUNITY -> Icons.Outlined.Groups
    EventCategory.OTHER -> Icons.Outlined.Event
}

/**
 * Cover art for an event card or detail hero.
 *
 * Several feeds publish no artwork at all, and some sites expose nothing but
 * their own loading GIF as the first image - loading that literally rendered an
 * animated spinner on every card. When there is no image (or the request
 * fails) this draws the event's category glyph on the surface colour instead,
 * so the layout always looks deliberate.
 */
@Composable
fun EventImage(
    imageUrl: String?,
    category: EventCategory,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = contentDescription,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Tappable favourite heart. Fills red with a spring pop and a soft red glow
 * when favourited, and rests as a grey outline otherwise. Shared by the event
 * card and the detail hero so both render identically.
 */
@Composable
fun FavoriteHeart(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    val heartColor = MaterialTheme.colorScheme.error
    val tint by animateColorAsState(
        targetValue = if (isFavorite) heartColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "heartTint"
    )
    val glow by animateDpAsState(
        targetValue = if (isFavorite) 7.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "heartGlow"
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            // Punchy pop on fill, then settle back with a playful bounce.
            scale.snapTo(1f)
            scale.animateTo(1.4f, tween(durationMillis = 110))
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size)
                .shadow(glow, CircleShape, ambientColor = heartColor, spotColor = heartColor)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

/**
 * Which artwork to show for a profile avatar. Pure state mapping so it can be
 * unit tested without Compose: an uploaded photo always wins, then the chosen
 * generated style, then the user's initials.
 */
enum class AvatarKind { PHOTO, MALE, FEMALE, INITIALS }

fun resolveAvatarKind(avatarStyle: Int, hasPhoto: Boolean): AvatarKind = when {
    hasPhoto -> AvatarKind.PHOTO
    avatarStyle == 1 -> AvatarKind.MALE
    avatarStyle == 2 -> AvatarKind.FEMALE
    else -> AvatarKind.INITIALS
}

/**
 * Circular profile avatar: uploaded photo first, then the chosen male/female
 * silhouette, then the user's initials on the brand container colour.
 *
 * Exposes a single TalkBack description ("Profile avatar, female") instead of
 * announcing the decorative layers separately.
 */
@Composable
fun UserAvatar(
    fullName: String,
    photoUrl: String? = null,
    avatarStyle: Int = 0,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val kind = resolveAvatarKind(avatarStyle, hasPhoto = !photoUrl.isNullOrBlank())
    val semanticsDescription = when (kind) {
        AvatarKind.PHOTO -> stringResource(R.string.avatar_desc_photo)
        AvatarKind.MALE -> stringResource(R.string.avatar_desc_male)
        AvatarKind.FEMALE -> stringResource(R.string.avatar_desc_female)
        AvatarKind.INITIALS -> stringResource(R.string.avatar_desc_initials)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { contentDescription = semanticsDescription },
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            AvatarKind.PHOTO -> AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            AvatarKind.MALE -> Image(
                painter = painterResource(R.drawable.avatar_male),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            AvatarKind.FEMALE -> Image(
                painter = painterResource(R.drawable.avatar_female),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            AvatarKind.INITIALS -> Text(
                fullName.trim().take(2).ifBlank { "?" }.uppercase(),
                style = if (size >= 64.dp) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
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
            EventImage(
                imageUrl = event.imageUrl,
                category = event.category,
                contentDescription = stringResource(R.string.event_image),
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(categoryLabel(event.category)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (onFavoriteToggle != null) {
                Spacer(Modifier.width(8.dp))
                FavoriteHeart(
                    isFavorite = view.isFavorite,
                    onToggle = onFavoriteToggle,
                    contentDescription = stringResource(
                        if (view.isFavorite) R.string.favorite_remove else R.string.favorite_add
                    )
                )
            }
        }
    }
}
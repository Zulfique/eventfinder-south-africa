package com.eventfinder.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.theme.BrandGreen

/**
 * Full-colour PNG icon that responds to taps: it springs slightly while pressed and,
 * when [active], grows and brightens so selected states (bottom navigation,
 * category buttons) visibly change appearance.
 */
@Composable
fun InteractivePngIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    dimWhenInactive: Boolean = false,
    size: Dp = 24.dp,
    onToggle: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val target = when {
        pressed -> 0.88f
        active -> 1.14f
        else -> 1f
    }
    val scale by animateFloatAsState(targetValue = target, label = "PngIconAnimation")

    Image(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier = modifier
            .then(
                if (onToggle != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle
                    )
                } else {
                    Modifier
                }
            )
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = when {
                    active -> 1f
                    dimWhenInactive -> 0.55f
                    else -> 1f
                }
            }
    )
}

/**
 * Favourite heart rendered from `heart_outline` / `heart_filled` PNGs.
 * Tapping swaps the artwork and springs the icon to 1.2x, per the app's
 * interactive heart pattern.
 */
@Composable
fun FavoritePngIcon(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    @DrawableRes outlineRes: Int = R.drawable.heart_outline,
    contentDescription: String? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.2f else 1f,
        label = "Heart Animation"
    )

    Image(
        painter = painterResource(
            id = if (isFavorite) {
                R.drawable.heart_filled
            } else {
                outlineRes
            }
        ),
        contentDescription = contentDescription ?: if (isFavorite) {
            "Remove from favourites"
        } else {
            "Add to favourites"
        },
        modifier = modifier
            .scale(scale)
            .size(size)
            .clickable {
                onToggle()
            }
    )
}

/**
 * Bottom-navigation Create button: segmented South African colour ring
 * (red / gold / green / blue) around a recessed centre button.
 *
 * Light mode: white centre with a brand-green plus.
 * Dark mode: deep-green centre with a white plus.
 * Responds to taps with a press-scale spring and grows while selected.
 */
@Composable
fun CreatePngButton(
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val darkMode = isSystemInDarkTheme()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.90f
            selected -> 1.06f
            else -> 1f
        },
        label = "CreateButtonAnimation"
    )
    val centerColor by animateColorAsState(
        targetValue = if (darkMode) BrandGreen else Color.White,
        label = "CreateCenterColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.create_ring),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .shadow(6.dp, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(centerColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (darkMode) R.drawable.nav_create else R.drawable.create_plus_green
                ),
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.30f)
            )
        }
    }
}

/** Maps a category to its downloaded full-colour PNG icon. */
@DrawableRes
fun categoryPngIcon(category: EventCategory): Int = when (category) {
    EventCategory.MUSIC -> R.drawable.cat_music
    EventCategory.CONCERTS -> R.drawable.cat_concerts
    EventCategory.FESTIVALS -> R.drawable.cat_festivals
    EventCategory.NIGHTLIFE -> R.drawable.cat_nightlife
    EventCategory.FOOD -> R.drawable.cat_food
    EventCategory.SPORTS -> R.drawable.cat_sports
    EventCategory.ARTS -> R.drawable.cat_arts
    EventCategory.COMEDY -> R.drawable.cat_comedy
    EventCategory.BUSINESS -> R.drawable.cat_business
    EventCategory.NETWORKING -> R.drawable.cat_networking
    EventCategory.TECHNOLOGY -> R.drawable.cat_technology
    EventCategory.EDUCATION -> R.drawable.cat_education
    EventCategory.FAMILY -> R.drawable.cat_family
    EventCategory.MARKETS -> R.drawable.cat_markets
    EventCategory.OUTDOORS -> R.drawable.cat_outdoors
    EventCategory.TRAVEL -> R.drawable.cat_travel
    EventCategory.CHARITY -> R.drawable.cat_charity
    EventCategory.THEATRE -> R.drawable.cat_theatre
    EventCategory.MOVIES -> R.drawable.cat_movies
    EventCategory.FASHION -> R.drawable.cat_fashion
    EventCategory.CARS -> R.drawable.cat_cars
    EventCategory.WELLNESS -> R.drawable.cat_wellness
    EventCategory.RELIGION -> R.drawable.cat_religion
    EventCategory.COMMUNITY -> R.drawable.cat_community
    EventCategory.OTHER -> R.drawable.cat_all
}

package com.eventfinder.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.RsvpStatus

/** EventFinder 2026 design tokens (RSVP / About spec sheets). */
object EventFinderTokens {
    val Green = Color(0xFF00C98D)
    val GreenHover = Color(0xFF00B894)
    val GreenPressed = Color(0xFF009E7F)
    val Night = Color(0xFF0B1929)
    val Surface = Color(0xFF13293D)
    val SurfaceDark = Color(0xFF0F1F2C)
    val SurfaceDarker = Color(0xFF0F1F2C)
    val InfoBlue = Color(0xFF4285F4)
    val ErrorRed = Color(0xFFEF4444)
    val ErrorRedHover = Color(0xFFDC2626)
    val SuccessGreen = Color(0xFF22C55E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextSecondaryAlt = Color(0xFFA0A9B8)
    val Border = Color(0xFF334155)
    val IconMuted = Color(0xFF64748B)
}

/**
 * The single RSVP control on an event (spec: Default / Confirmed / Neutral /
 * Cancel). Height 48dp, radius 24dp, 16sp medium label, 20dp icon.
 */
@Composable
fun RsvpActionButton(
    status: RsvpStatus?,
    onOpenSheet: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = when (status) {
            RsvpStatus.ATTENDING -> Color(0xFF0F3D2E)
            else -> EventFinderTokens.Green
        },
        label = "rsvpBackground"
    )
    val content = when (status) {
        RsvpStatus.ATTENDING -> EventFinderTokens.Green
        else -> EventFinderTokens.Night
    }

    Surface(
        onClick = onOpenSheet,
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = if (status == RsvpStatus.ATTENDING) {
            BorderStroke(1.dp, EventFinderTokens.Green)
        } else {
            null
        },
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (status == RsvpStatus.ATTENDING) R.drawable.ic_ms_check_circle
                    else R.drawable.ic_ms_rsvp
                ),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (status) {
                    RsvpStatus.ATTENDING -> "Going"
                    RsvpStatus.MAYBE -> "Maybe"
                    RsvpStatus.DECLINED -> "Not Going"
                    null -> "RSVP"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content
            )
            if (status == null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_ms_chevron),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (status != null) {
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onCancel,
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2A3441),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ms_remove_circle),
                    contentDescription = null,
                    tint = EventFinderTokens.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Cancel RSVP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = EventFinderTokens.TextSecondary
                )
            }
        }
    }
}

/**
 * Compact attendee counts exactly as the RSVP spec shows them ("1.2K Going",
 * "324 Maybe", "178 Not Going").
 */
fun formatRsvpCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        .replace(".0K", "K")
    else -> count.toString()
}

/** RSVP tally chips: Going (green), Maybe (blue), Not Going (red). */
@Composable
fun RsvpTallyChips(
    going: Int,
    maybe: Int,
    notGoing: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TallyChip(
            icon = R.drawable.ic_ms_groups,
            label = "${formatRsvpCount(going)} Going",
            color = EventFinderTokens.Green,
            modifier = Modifier.weight(1f)
        )
        TallyChip(
            icon = R.drawable.ic_ms_help,
            label = "${formatRsvpCount(maybe)} Maybe",
            color = EventFinderTokens.InfoBlue,
            modifier = Modifier.weight(1f)
        )
        TallyChip(
            icon = R.drawable.ic_ms_close,
            label = "${formatRsvpCount(notGoing)} Not Going",
            color = EventFinderTokens.ErrorRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TallyChip(
    icon: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

/** One option inside the RSVP sheet (Going / Maybe / Not Going). */
@Composable
fun RsvpOptionPill(
    status: RsvpStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (status) {
        RsvpStatus.ATTENDING -> EventFinderTokens.Green
        RsvpStatus.MAYBE -> EventFinderTokens.InfoBlue
        RsvpStatus.DECLINED -> EventFinderTokens.ErrorRed
    }
    val icon = when (status) {
        RsvpStatus.ATTENDING -> R.drawable.ic_ms_check_circle
        RsvpStatus.MAYBE -> R.drawable.ic_ms_help
        RsvpStatus.DECLINED -> R.drawable.ic_ms_close
    }
    val label = when (status) {
        RsvpStatus.ATTENDING -> "Going"
        RsvpStatus.MAYBE -> "Maybe"
        RsvpStatus.DECLINED -> "Not Going"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) accent else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) accent else EventFinderTokens.Border
        ),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) EventFinderTokens.Night else EventFinderTokens.TextSecondaryAlt,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) EventFinderTokens.Night else EventFinderTokens.TextPrimary,
                maxLines = 1
            )
        }
    }
}

/**
 * "About This Event" button, built to its spec sheet: 48dp tall, 24dp radius,
 * 20dp leading icon disc, 8dp icon spacing, 20dp padding, #334155 border.
 *
 * States: default (dark surface / white), pressed (#1E293B), disabled (muted),
 * active (filled green / night label), loading (spinner + "Loading…") and error
 * (dark red surface, red border and red warning label).
 */
@Composable
fun AboutEventButton(
    expanded: Boolean,
    loading: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val background = when {
        !enabled -> EventFinderTokens.Surface
        error -> Color(0xFF2A161C)
        expanded -> EventFinderTokens.Green
        pressed -> Color(0xFF1E293B)
        else -> EventFinderTokens.Surface
    }
    val content = when {
        !enabled -> EventFinderTokens.TextSecondary.copy(alpha = 0.55f)
        error -> EventFinderTokens.ErrorRed
        expanded -> EventFinderTokens.Night
        else -> EventFinderTokens.TextPrimary
    }
    val border = when {
        !enabled -> EventFinderTokens.Border.copy(alpha = 0.45f)
        error -> EventFinderTokens.ErrorRed
        expanded -> EventFinderTokens.Green
        else -> EventFinderTokens.Border
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = EventFinderTokens.Green
                )
            } else {
                // Icon on a disc, as in the spec's icon column.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (expanded) EventFinderTokens.Night else content),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (error) R.drawable.ic_ms_warning else R.drawable.ic_ms_info
                        ),
                        contentDescription = null,
                        tint = when {
                            expanded -> EventFinderTokens.Green
                            error -> EventFinderTokens.ErrorRed
                            else -> EventFinderTokens.Surface
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(if (expanded) EventFinderTokens.Night.copy(alpha = 0.35f) else EventFinderTokens.Border)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    loading -> "Loading…"
                    error -> "Unable to load details"
                    else -> "About This Event"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Icon(
                painter = painterResource(R.drawable.ic_ms_chevron),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Two-line status card used by the RSVP sheet's "Other States": submitting,
 * failed (with retry) and closed. 16dp radius, icon disc, 16dp spacing.
 */
@Composable
fun RsvpStatusNotice(
    title: String,
    subtitle: String,
    accent: Color,
    loading: Boolean = false,
    icon: Int = R.drawable.ic_ms_info,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF16283C),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EventFinderTokens.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = EventFinderTokens.TextSecondary
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onRetry,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, EventFinderTokens.Border),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ms_refresh),
                            contentDescription = "Retry",
                            tint = EventFinderTokens.Green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Report this Event" button, built to its spec sheet: 48dp tall, 24dp radius,
 * 20dp horizontal padding, 20dp warning icon and a 1dp #334155 border.
 *
 * States: default, pressed (#1E2933), disabled (muted), loading
 * ("Reporting…" + spinner), success (green fill, "Report Submitted", check)
 * and error (red fill/border, "Try Again").
 */
@Composable
fun ReportEventButton(
    loading: Boolean = false,
    submitted: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // A dark surface keeps the white label readable in light mode too, where
    // a transparent pill would put white text on a white background.
    val background = when {
        submitted -> Color(0xFF0F3D2E)
        error -> Color(0xFF2A161C)
        !enabled -> EventFinderTokens.SurfaceDark
        pressed -> Color(0xFF1E2933)
        else -> EventFinderTokens.Surface
    }
    val border = when {
        submitted -> EventFinderTokens.SuccessGreen
        error -> EventFinderTokens.ErrorRed
        !enabled -> EventFinderTokens.Border.copy(alpha = 0.45f)
        else -> EventFinderTokens.Border
    }
    val content = when {
        submitted -> EventFinderTokens.SuccessGreen
        error -> EventFinderTokens.ErrorRed
        !enabled -> EventFinderTokens.TextSecondary.copy(alpha = 0.55f)
        else -> EventFinderTokens.TextPrimary
    }
    val label = when {
        submitted -> "Report Submitted"
        error -> "Try Again"
        loading -> "Reporting…"
        else -> "Report this Event"
    }

    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = EventFinderTokens.Green
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (submitted) R.drawable.ic_ms_check_circle else R.drawable.ic_ms_warning
                    ),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}


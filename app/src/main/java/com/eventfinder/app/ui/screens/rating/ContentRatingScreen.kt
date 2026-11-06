package com.eventfinder.app.ui.screens.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.ContentRatingRepositoryImpl
import com.eventfinder.app.data.repository.EventContentTag
import com.eventfinder.app.data.repository.EventContentType
import com.eventfinder.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Community content rating, built from the 2026 sheet: stars, content type,
 * what the event contains, and an optional concerns note. Submitting replaces
 * the signed-in user's previous rating for the event.
 *
 * The report flow is the same sheet pattern, and closes with a confirmation
 * dialog instead of navigating away, so the rating form is never lost.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContentRatingScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = container.contentRatingRepository

    var stars by remember { mutableStateOf(0) }
    var contentType by remember { mutableStateOf(EventContentType.MIXED) }
    var tags by remember { mutableStateOf(emptySet<EventContentTag>()) }
    var concerns by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportSubmitted by remember { mutableStateOf(false) }

    // Pre-fill from the user's earlier rating so re-rating is an edit, not a reset.
    LaunchedEffect(eventId) {
        repository.myRating(eventId)?.let { existing ->
            stars = existing.stars
            contentType = existing.contentType
            tags = existing.tags
            concerns = existing.concerns
        }
    }

    if (showAbout) {
        AboutRatingsDialog(onDismiss = { showAbout = false })
    }

    if (reportSubmitted) {
        AlertDialog(
            onDismissRequest = { reportSubmitted = false },
            icon = {
                Icon(
                    Icons.Filled.GppGood,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.report_submitted_title)) },
            text = { Text(stringResource(R.string.report_submitted_body)) },
            confirmButton = {
                TextButton(onClick = { reportSubmitted = false }) {
                    Text(stringResource(R.string.rating_done))
                }
            }
        )
    }

    if (showReport) {
        ReportContentSheet(
            onDismiss = { showReport = false },
            onSubmit = { reason, details ->
                showReport = false
                scope.launch {
                    val reporterId = container.preferences.sessionUserId.first().orEmpty()
                    val payload = if (details.isBlank()) reason else "$reason || $details"
                    container.reportRepository
                        .report("event", eventId, payload, reporterId)
                        .onSuccess { reportSubmitted = true }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        RatingTopBar(
            title = stringResource(R.string.content_rating_title),
            onBack = onBack,
            onInfo = { showAbout = true }
        )

        if (submitted) {
            RatingThanks(onDone = onBack)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                RatingHero()

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.content_rating_heading),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.content_rating_subtitle),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))
                SectionHeader(1, stringResource(R.string.rating_overall))
                StarPicker(stars = stars, onSelect = { stars = it })
                Spacer(Modifier.height(4.dp))
                Text(
                    if (stars == 0) stringResource(R.string.rating_star_prompt)
                    else stringResource(R.string.rating_out_of_five, "%.1f".format(stars.toFloat())),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(22.dp))
                SectionHeader(2, stringResource(R.string.rating_content_type))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventContentType.entries.forEach { type ->
                        ContentTypeChip(
                            label = stringResource(type.labelRes()),
                            selected = contentType == type,
                            onClick = { contentType = type }
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionHeader(3, stringResource(R.string.rating_contents))
                EventContentTag.entries.forEach { tag ->
                    val checked = tag in tags
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = {
                                    tags = if (checked) tags - tag else tags + tag
                                }
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(tag.labelRes()),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionHeader(4, stringResource(R.string.rating_concerns))
                OutlinedTextField(
                    value = concerns,
                    onValueChange = {
                        if (it.length <= ContentRatingRepositoryImpl.MAX_CONCERNS) concerns = it
                    },
                    placeholder = { Text(stringResource(R.string.rating_concerns_hint)) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.rating_counter,
                                concerns.length,
                                ContentRatingRepositoryImpl.MAX_CONCERNS
                            ),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                )

                if (failure) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.rating_submit_failed),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showReport = true }) {
                    Icon(
                        Icons.Filled.Report,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.rating_report_link),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.rating_cancel), fontSize = 15.sp)
                    }
                    Button(
                        onClick = {
                            failure = false
                            submitting = true
                            scope.launch {
                                repository
                                    .submit(eventId, stars, contentType, tags, concerns)
                                    .onSuccess { submitted = true }
                                    .onFailure { failure = true }
                                submitting = false
                            }
                        },
                        enabled = stars > 0 && !submitting,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                stringResource(R.string.rating_submit),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun RatingTopBar(title: String, onBack: () -> Unit, onInfo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.rating_cancel),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onInfo) {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.rating_about_title),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Shield-with-check and a star, the mood of the sheet's header illustration. */
@Composable
private fun RatingHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.GppGood,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 88.dp, bottom = 58.dp)
                .size(30.dp),
            tint = Color(0xFFFFC53D)
        )
    }
}

@Composable
private fun SectionHeader(number: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StarPicker(stars: Int, onSelect: (Int) -> Unit) {
    Row {
        (1..5).forEach { index ->
            Icon(
                Icons.Filled.Star,
                contentDescription = stringResource(R.string.rating_star_description, index),
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onSelect(index) }
                    .padding(2.dp),
                tint = if (index <= stars) Color(0xFFFFC53D)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun ContentTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RatingThanks(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.rating_thanks_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.rating_thanks_body),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                stringResource(R.string.rating_done),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.GppGood,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.rating_feedback_note),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AboutRatingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rating_about_title)) },
        text = { Text(stringResource(R.string.rating_about_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rating_done)) }
        }
    )
}

/** Radio-list report sheet, mirroring the design's "Report Inappropriate Content". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportContentSheet(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reasons = listOf(
        stringResource(R.string.report_spam),
        stringResource(R.string.report_inappropriate),
        stringResource(R.string.report_harassment),
        stringResource(R.string.report_fake),
        stringResource(R.string.report_other)
    )
    var selected by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.report_sheet_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.report_sheet_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            reasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = selected == reason,
                            role = Role.RadioButton,
                            onClick = { selected = reason }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == reason,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(reason, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 500) details = it },
                placeholder = { Text(stringResource(R.string.report_details_hint)) },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                supportingText = { Text("${details.length}/500", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.report_cancel), fontSize = 15.sp)
                }
                Button(
                    onClick = { selected?.let { onSubmit(it, details.trim()) } },
                    enabled = selected != null,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                ) {
                    Text(
                        stringResource(R.string.report_submit),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun EventContentType.labelRes(): Int = when (this) {
    EventContentType.FAMILY -> R.string.type_family
    EventContentType.ADULT -> R.string.type_adult
    EventContentType.MIXED -> R.string.type_mixed
    EventContentType.EIGHTEEN_PLUS -> R.string.type_18plus
}

private fun EventContentTag.labelRes(): Int = when (this) {
    EventContentTag.MUSIC -> R.string.content_music
    EventContentTag.FOOD_AND_DRINK -> R.string.content_food
    EventContentTag.SOCIAL -> R.string.content_social
    EventContentTag.OTHER -> R.string.content_other
}

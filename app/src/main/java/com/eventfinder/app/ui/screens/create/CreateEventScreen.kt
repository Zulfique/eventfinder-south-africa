package com.eventfinder.app.ui.screens.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.ui.components.categoryLabel
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.DateTimeUtils
import java.util.Calendar

/**
 * Screen 8 (Create Event): a multi-step wizard covering details, date/venue
 * and a final review before the event is published to the local Room catalogue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    container: AppContainer,
    onClose: () -> Unit,
    eventId: String? = null
) {
    val viewModel: CreateEventViewModel =
        viewModel(factory = CreateEventViewModel.factory(container, eventId))
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // System photo picker: free, no storage permission required (FR-06).
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImage(context, uri)
    }
    val pickImage: () -> Unit = {
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.completed.collect { onClose() }
    }

    if (showDatePicker) {
        val today = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = today.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(millis)
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = 18, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = state.dateMillis }
                    cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                    cal.set(Calendar.MINUTE, timeState.minute)
                    cal.set(Calendar.SECOND, 0)
                    viewModel.onDateChange(cal.timeInMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isEditMode) R.string.edit_event_title
                            else R.string.create_event_title
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.step > 1) {
                    FilledTonalButton(
                        onClick = viewModel::previousStep,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(stringResource(R.string.back)) }
                }
                if (state.step < CREATE_STEPS) {
                    Button(
                        onClick = viewModel::nextStep,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (state.step == CREATE_STEPS - 1) stringResource(R.string.preview)
                        else stringResource(R.string.next))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = viewModel::publish,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (viewModel.isEditMode) R.string.save_changes
                                    else R.string.publish
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { state.step / CREATE_STEPS.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                getString(R.string.step_pattern, state.step, CREATE_STEPS),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.step) {
                    1 -> StepDetails(viewModel, onPickImage = pickImage)
                    2 -> StepDateVenue(viewModel, onPickDate = { showDatePicker = true })
                    else -> StepReview(state)
                }
            }
        }
    }
}

@Composable
private fun getString(resId: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(resId, *args)

@Composable
private fun StepDetails(viewModel: CreateEventViewModel, onPickImage: () -> Unit) {
    val state = viewModel.uiState.collectAsState().value
    val titleError = state.showErrors && state.title.isBlank()
    val descriptionError = state.showErrors && state.description.length < MIN_DESCRIPTION_LENGTH

    if (state.imageUrl != null) {
        AsyncImage(
            model = state.imageUrl,
            contentDescription = stringResource(R.string.event_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickImage) { Text(stringResource(R.string.change_photo)) }
            TextButton(onClick = viewModel::removeImage) { Text(stringResource(R.string.remove_photo)) }
        }
    } else {
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_photo))
        }
    }

    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::onTitleChange,
        label = { Text(stringResource(R.string.title)) },
        singleLine = true,
        isError = titleError,
        supportingText = if (titleError) {
            { Text(stringResource(R.string.title_required)) }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text(stringResource(R.string.description)) },
        minLines = 4,
        isError = descriptionError,
        supportingText = if (descriptionError) {
            { Text(stringResource(R.string.description_required)) }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge)
    EventCategory.entries.forEach { category ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = state.category == category,
                    onClick = { viewModel.onCategoryChange(category) },
                    role = Role.RadioButton
                )
        ) {
            RadioButton(
                selected = state.category == category,
                onClick = null
            )
            Text(stringResource(categoryLabel(category)))
        }
    }
}

@Composable
private fun StepDateVenue(
    viewModel: CreateEventViewModel,
    onPickDate: () -> Unit
) {
    val state = viewModel.uiState.collectAsState().value
    val dateLabel = stringResource(R.string.date_time)
    val pickAction = stringResource(R.string.date_pick_action)
    val dateValue = if (state.dateMillis == 0L) {
        stringResource(R.string.tap_to_pick_date)
    } else {
        DateTimeUtils.formatFullDateTime(state.dateMillis)
    }

    val dateError = state.showErrors &&
        (state.dateMillis == 0L || !DateTimeUtils.isInFuture(state.dateMillis))
    val venueError = state.showErrors && state.venueName.isBlank()
    val latitudeError = state.showErrors && !isValidLatitude(state.latitude)
    val longitudeError = state.showErrors && !isValidLongitude(state.longitude)

    // A read-only text field consumes taps for its own cursor, so an overlay owns
    // the click. The overlay also carries the semantics the disabled field would
    // otherwise hide from accessibility services.
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (state.dateMillis == 0L) "" else DateTimeUtils.formatFullDateTime(state.dateMillis),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(dateLabel) },
            trailingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = pickAction) },
            placeholder = { Text(stringResource(R.string.tap_to_pick_date)) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = dateLabel
                    stateDescription = dateValue
                    role = Role.Button
                }
                .clickable(onClick = onPickDate)
        )
    }
    if (dateError) {
        Text(
            stringResource(
                if (state.dateMillis == 0L) R.string.date_required else R.string.date_in_past
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    OutlinedTextField(
        value = state.venueName,
        onValueChange = viewModel::onVenueChange,
        label = { Text(stringResource(R.string.venue)) },
        singleLine = true,
        isError = venueError,
        supportingText = if (venueError) {
            { Text(stringResource(R.string.venue_required)) }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = viewModel::onAddressChange,
        label = { Text(stringResource(R.string.address)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.latitude,
            onValueChange = viewModel::onLatitudeChange,
            label = { Text(stringResource(R.string.latitude)) },
            singleLine = true,
            isError = latitudeError,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.longitude,
            onValueChange = viewModel::onLongitudeChange,
            label = { Text(stringResource(R.string.longitude)) },
            singleLine = true,
            isError = longitudeError,
            modifier = Modifier.weight(1f)
        )
    }
    if (latitudeError || longitudeError) {
        Text(
            stringResource(R.string.invalid_coordinates),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        stringResource(R.string.coordinates_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepReview(state: CreateEventUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.imageUrl?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = stringResource(R.string.event_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(categoryLabel(state.category)), color = MaterialTheme.colorScheme.primary)
            Text(DateTimeUtils.formatFullDateTime(state.dateMillis))
            Text(state.venueName)
            Text(state.address)
            Text(state.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
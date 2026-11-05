package com.eventfinder.app.ui.screens.preferences

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.ui.components.categoryPngIcon
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.EventCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val BrandGreen: Color
    /** Brand accent that stays legible: emerald on night surfaces, the
     *  darker brand green on white surfaces. */
    @Composable get() = MaterialTheme.colorScheme.primary

data class CommuteUiState(
    val minutes: Int = 30,
    val mode: String = "car",
    val nearTransit: Boolean = false
)

private class CommuteViewModel(private val preferences: UserPreferences) : ViewModel() {

    val state = combine(
        preferences.commuteMinutes,
        preferences.commuteMode,
        preferences.commuteNearTransit
    ) { minutes, mode, transit -> CommuteUiState(minutes, mode, transit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommuteUiState())

    fun setMinutes(value: Int) = viewModelScope.launch { preferences.setCommuteMinutes(value) }
    fun setMode(value: String) = viewModelScope.launch { preferences.setCommuteMode(value) }
    fun setNearTransit(value: Boolean) = viewModelScope.launch { preferences.setCommuteNearTransit(value) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CommuteViewModel(container.preferences) }
        }
    }
}

/** "Commute Preferences — Your Commute". */
@Composable
fun CommutePreferencesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: CommuteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = CommuteViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "Commute Preferences", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.DirectionsCar)
        PreferenceHeading(
            title = "Your Commute",
            subtitle = "Set your preferred commute time to find events within your range."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Max commute time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${state.minutes} min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Slider(
            value = state.minutes.toFloat(),
            onValueChange = { vm.setMinutes(it.toInt()) },
            valueRange = 5f..180f,
            steps = 34,
            colors = SliderDefaults.colors(
                thumbColor = BrandGreen,
                activeTrackColor = BrandGreen,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        PreferenceSectionLabel("Travel Mode")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TravelModeCard("Car", "car", Icons.Outlined.DirectionsCar, state.mode, vm::setMode, Modifier.weight(1f))
            TravelModeCard("Public\nTransport", "transit", Icons.Outlined.DirectionsBus, state.mode, vm::setMode, Modifier.weight(1f))
            TravelModeCard("Walking", "walking", Icons.Outlined.DirectionsWalk, state.mode, vm::setMode, Modifier.weight(1f))
            TravelModeCard("Cycling", "cycling", Icons.Outlined.DirectionsBike, state.mode, vm::setMode, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        PreferenceToggleBlock(
            title = "Include events near transit",
            checked = state.nearTransit,
            onCheckedChange = vm::setNearTransit
        )

        Spacer(Modifier.height(20.dp))
        BrandPrimaryButton("Save Preferences") {
            Toast.makeText(context, "Commute preferences saved", Toast.LENGTH_SHORT).show()
            onBack()
        }
        BottomActionSpacer()
    }
}

@Composable
private fun TravelModeCard(
    label: String,
    value: String,
    icon: ImageVector,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = value == selectedValue
    Column(
        modifier = modifier
            .background(
                if (selected) BrandGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (selected) BrandGreen else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { onSelect(value) }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------------ interests

private class InterestsViewModel(private val preferences: UserPreferences) : ViewModel() {
    val selected = preferences.interests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggle(labelKey: String) = viewModelScope.launch {
        val current = selected.value
        val next = if (labelKey in current) current - labelKey else current + labelKey
        // The showcase caps interest selection at five categories.
        preferences.setInterests(next.take(5).toSet())
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { InterestsViewModel(container.preferences) }
        }
    }
}

/** Categories shown up-front; "Show More" reveals the rest of the catalogue. */
private val PRIMARY_INTERESTS = listOf(
    EventCategory.MUSIC,
    EventCategory.FOOD,
    EventCategory.SPORTS,
    EventCategory.ARTS,
    EventCategory.TECHNOLOGY,
    EventCategory.WELLNESS
)

private fun interestLabel(category: EventCategory): String = when (category) {
    EventCategory.MUSIC -> "Music"
    EventCategory.FOOD -> "Food & Drink"
    EventCategory.SPORTS -> "Sports"
    EventCategory.ARTS -> "Arts & Culture"
    EventCategory.TECHNOLOGY -> "Technology"
    EventCategory.WELLNESS -> "Health & Wellness"
    else -> category.name.lowercase().replaceFirstChar { it.uppercase() }
}

/** "Interest Management — Your Interests". */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InterestsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: InterestsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = InterestsViewModel.factory(container))
    val selected by vm.selected.collectAsState()
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current

    val shown = if (expanded) EventCategory.entries.filter { it != EventCategory.OTHER } else PRIMARY_INTERESTS

    PreferenceScaffold(title = "Your Interests", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Interests)
        PreferenceHeading(
            title = "Your Interests",
            subtitle = "Select categories you're interested in."
        )

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shown.forEach { category ->
                val isSelected = category.labelKey in selected
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .background(
                            if (isSelected) BrandGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) BrandGreen else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { vm.toggle(category.labelKey) }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isSelected) BrandGreen.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(categoryPngIcon(category)),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = interestLabel(category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        BrandOutlineButton(if (expanded) "Show Less" else "＋  Show More") { expanded = !expanded }

        Spacer(Modifier.height(18.dp))
        BrandPrimaryButton("Save Interests") {
            Toast.makeText(context, "Interests saved", Toast.LENGTH_SHORT).show()
            onBack()
        }
        BottomActionSpacer()
    }
}

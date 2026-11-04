package com.eventfinder.app.ui.screens.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SensorOccupied
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material.icons.outlined.SocialDistance
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Accessibility preferences shared by the hub and its detail screens. */
data class AccessibilityUiState(
    val wheelchairNeeded: Boolean = false,
    val loudNoiseWarning: Boolean = false,
    val noMusic: Boolean = false,
    val sensoryFriendly: Boolean = false,
    val accessibilityFirstSorting: Boolean = false,
    val noiseLevel: String = "off",
    val sensoryTriggers: Set<String> = emptySet(),
    val features: Set<String> = emptySet()
)

private class AccessibilityViewModel(private val preferences: UserPreferences) : ViewModel() {

    private val flags = combine(
        preferences.wheelchairNeeded,
        preferences.loudNoiseNeeded,
        preferences.noMusicNeeded,
        preferences.lowSensoryNeeded,
        preferences.accessibilityFirstSorting
    ) { wheelchair, loud, noMusic, sensory, sorting ->
        listOf(wheelchair, loud, noMusic, sensory, sorting)
    }

    private val details = combine(
        preferences.noiseLevel,
        preferences.sensoryTriggers,
        preferences.accessibilityFeatures
    ) { noise, triggers, features -> Triple(noise, triggers, features) }

    val state = combine(flags, details) { f, d ->
        AccessibilityUiState(
            wheelchairNeeded = f[0],
            loudNoiseWarning = f[1],
            noMusic = f[2],
            sensoryFriendly = f[3],
            accessibilityFirstSorting = f[4],
            noiseLevel = d.first,
            sensoryTriggers = d.second,
            features = d.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilityUiState())

    fun setWheelchairNeeded(value: Boolean) = viewModelScope.launch { preferences.setWheelchairNeeded(value) }
    fun setLoudNoise(value: Boolean) = viewModelScope.launch { preferences.setLoudNoiseNeeded(value) }
    fun setNoMusic(value: Boolean) = viewModelScope.launch { preferences.setNoMusicNeeded(value) }
    fun setSensoryFriendly(value: Boolean) = viewModelScope.launch { preferences.setLowSensoryNeeded(value) }
    fun setSorting(value: Boolean) = viewModelScope.launch { preferences.setAccessibilityFirstSorting(value) }
    fun setNoiseLevel(value: String) = viewModelScope.launch { preferences.setNoiseLevel(value) }
    fun toggleFeature(feature: String) = viewModelScope.launch {
        val current = state.value.features
        preferences.setAccessibilityFeatures(
            if (feature in current) current - feature else current + feature
        )
    }

    fun toggleTrigger(trigger: String) = viewModelScope.launch {
        val current = state.value.sensoryTriggers
        preferences.setSensoryTriggers(
            if (trigger in current) current - trigger else current + trigger
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccessibilityViewModel(container.preferences) }
        }
    }
}

/** Confirms instant-persisted changes, matching the showcase's save buttons. */
private fun confirmSaved(message: String, context: android.content.Context) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** "Accessibility — Make events more accessible for you". */
@Composable
fun AccessibilityScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWheelchair: () -> Unit,
    onOpenLoudNoise: () -> Unit,
    onOpenNoMusic: () -> Unit,
    onOpenSensory: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "Accessibility", onBack = onBack) {
        PreferenceHeading(
            title = "Accessibility",
            subtitle = "Make events more accessible for you"
        )

        PreferenceSwitchRow(
            icon = Icons.Outlined.Accessible,
            title = "Wheelchair Accessible",
            checked = state.wheelchairNeeded,
            onCheckedChange = vm::setWheelchairNeeded
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.Hearing,
            title = "Loud Noise Warnings",
            checked = state.loudNoiseWarning,
            onCheckedChange = vm::setLoudNoise
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.MusicOff,
            title = "No Music",
            checked = state.noMusic,
            onCheckedChange = vm::setNoMusic
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.SensorOccupied,
            title = "Sensory Friendly",
            checked = state.sensoryFriendly,
            onCheckedChange = vm::setSensoryFriendly
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.Bolt,
            title = "Strobe Lights",
            checked = "strobe" in state.sensoryTriggers,
            onCheckedChange = { vm.toggleTrigger("strobe") }
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.SettingsAccessibility,
            title = "Accessibility-First Event Sorting",
            checked = state.accessibilityFirstSorting,
            onCheckedChange = vm::setSorting
        )

        Spacer(Modifier.height(12.dp))
        PreferenceInfoBox("Help us create a more inclusive EventFinder for all.")

        Spacer(Modifier.height(14.dp))
        RowOfLinks(
            links = listOf(
                "Wheelchair details" to onOpenWheelchair,
                "Noise & sensory" to onOpenLoudNoise,
                "No music" to onOpenNoMusic,
                "Sensory warnings" to onOpenSensory,
                "Profile" to onOpenProfile
            )
        )

        Spacer(Modifier.height(18.dp))
        BrandPrimaryButton("Save Preferences") {
            confirmSaved("Accessibility preferences saved", context)
            onBack()
        }
        BottomActionSpacer()
    }
}

/** Compact pill row so every detail screen stays one tap away. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RowOfLinks(links: List<Pair<String, () -> Unit>>) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        links.forEach { (label, action) ->
            androidx.compose.material3.AssistChip(
                onClick = action,
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** "Wheelchair Accessibility — Find events with wheelchair accessibility features." */
@Composable
fun WheelchairAccessibilityScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "Wheelchair Accessibility", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Accessible)
        PreferenceHeading(
            title = "Wheelchair Accessibility",
            subtitle = "Find events with wheelchair accessibility features."
        )

        PreferenceToggleBlock(
            title = "Only show accessible events",
            checked = state.wheelchairNeeded,
            onCheckedChange = vm::setWheelchairNeeded
        )

        Spacer(Modifier.height(18.dp))
        PreferenceSectionLabel("Accessibility Features")
        AccessibilityFeatureRow("Ramp Access", Icons.Outlined.Stairs, state, vm)
        AccessibilityFeatureRow("Accessible Toilets", Icons.Outlined.Wc, state, vm)
        AccessibilityFeatureRow("Reserved Seating", Icons.Outlined.EventSeat, state, vm)
        AccessibilityFeatureRow("Other Features", Icons.Outlined.Accessibility, state, vm)

        Spacer(Modifier.height(18.dp))
        BrandPrimaryButton("Save Settings") {
            confirmSaved("Wheelchair settings saved", context)
            onBack()
        }
        BottomActionSpacer()
    }
}

@Composable
private fun AccessibilityFeatureRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: AccessibilityUiState,
    vm: AccessibilityViewModel
) {
    val key = label.lowercase().replace(" ", "_")
    PreferenceSwitchRow(
        icon = icon,
        title = label,
        checked = key in state.features,
        onCheckedChange = { vm.toggleFeature(key) }
    )
}

/** "Loud Noise Sensitivity". */
@Composable
fun LoudNoiseScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "Loud Noise Sensitivity", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Hearing)
        PreferenceHeading(
            title = "Loud Noise Sensitivity",
            subtitle = "Choose your preference for loud music and high noise events."
        )

        PreferenceChoiceRow("No Preference", state.noiseLevel == "off") { vm.setNoiseLevel("off") }
        PreferenceChoiceRow("Low Noise", state.noiseLevel == "low") { vm.setNoiseLevel("low") }
        PreferenceChoiceRow("Moderate Noise", state.noiseLevel == "moderate") { vm.setNoiseLevel("moderate") }
        PreferenceChoiceRow("High Noise (Warn Me)", state.noiseLevel == "high") { vm.setNoiseLevel("high") }

        Spacer(Modifier.height(12.dp))
        BrandPrimaryButton("Save Preference") {
            confirmSaved("Noise preference saved", context)
            onBack()
        }
        BottomActionSpacer()
    }
}

/** "No Music Preference". */
@Composable
fun NoMusicScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "No Music Preference", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.MusicOff)
        PreferenceHeading(
            title = "No Music Preference",
            subtitle = "Find events without live music or amplified sound."
        )

        PreferenceToggleBlock(
            title = "Only show no-music events",
            checked = state.noMusic,
            onCheckedChange = vm::setNoMusic
        )

        Spacer(Modifier.height(20.dp))
        BrandPrimaryButton("Save Settings") {
            confirmSaved("Music preference saved", context)
            onBack()
        }
        BottomActionSpacer()
    }
}

/** "Sensory Preferences — Sensory Warnings". */
@Composable
fun SensoryPreferencesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    PreferenceScaffold(title = "Sensory Preferences", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.SensorOccupied)
        PreferenceHeading(
            title = "Sensory Warnings",
            subtitle = "Get warnings for events with flashing lights, strobe effects, or intense sensory elements."
        )

        PreferenceSwitchRow(
            icon = Icons.Outlined.LightMode,
            title = "Flashing Lights",
            checked = "flashing" in state.sensoryTriggers,
            onCheckedChange = { vm.toggleTrigger("flashing") }
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.Bolt,
            title = "Strobe Effects",
            checked = "strobe" in state.sensoryTriggers,
            onCheckedChange = { vm.toggleTrigger("strobe") }
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.Groups,
            title = "Loud Crowds",
            checked = "crowds" in state.sensoryTriggers,
            onCheckedChange = { vm.toggleTrigger("crowds") }
        )
        PreferenceSwitchRow(
            icon = Icons.Outlined.SocialDistance,
            title = "Other Sensory Triggers",
            checked = "other" in state.sensoryTriggers,
            onCheckedChange = { vm.toggleTrigger("other") }
        )

        Spacer(Modifier.height(20.dp))
        BrandPrimaryButton("Save Preferences") {
            confirmSaved("Sensory preferences saved", context)
            onBack()
        }
        BottomActionSpacer()
    }
}

/** "Accessibility Profile — Customize your accessibility preferences for a better experience." */
@Composable
fun AccessibilityProfileScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val vm: AccessibilityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = AccessibilityViewModel.factory(container))
    val state by vm.state.collectAsState()

    PreferenceScaffold(title = "Accessibility Profile", onBack = onBack) {
        PreferenceHeroIcon(Icons.Outlined.Accessibility)
        PreferenceHeading(
            title = "Accessibility Profile",
            subtitle = "Customize your accessibility preferences for a better experience."
        )

        PreferenceSummaryRow(
            icon = Icons.Outlined.Accessible,
            title = "Wheelchair Access",
            value = if (state.wheelchairNeeded) "On" else "Off"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PreferenceSummaryRow(
            icon = Icons.Outlined.Hearing,
            title = "Loud Noise",
            value = when (state.noiseLevel) {
                "high" -> "Warn me"
                "moderate" -> "Moderate"
                "low" -> "Low"
                else -> if (state.loudNoiseWarning) "On" else "Off"
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PreferenceSummaryRow(
            icon = Icons.Outlined.MusicOff,
            title = "No Music",
            value = if (state.noMusic) "On" else "Off"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PreferenceSummaryRow(
            icon = Icons.Outlined.SensorOccupied,
            title = "Sensory Warnings",
            value = if (state.sensoryTriggers.isEmpty()) "Off" else "${state.sensoryTriggers.size} active"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PreferenceSummaryRow(
            icon = Icons.Outlined.Route,
            title = "Commute Preference",
            value = if (state.accessibilityFirstSorting) "Accessible first" else "Default"
        )

        Spacer(Modifier.height(20.dp))
        BrandPrimaryButton("Update Profile", onClick = onEdit)
        BottomActionSpacer()
    }
}

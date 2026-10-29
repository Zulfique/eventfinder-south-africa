package com.eventfinder.app.ui.screens.editprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.components.resolve

/** Edit-profile screen bound to [EditProfileViewModel]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.setAvatarUploading(true)
                val uploadedUrl = container.imageUploader.uploadEventImage(uri.toString(), "avatar")
                viewModel.onAvatarImageChange(uploadedUrl ?: uri.toString())
                viewModel.setAvatarUploading(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.fullName,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.full_name)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = {
                    state.nameError?.let { Text(stringResource(it)) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Avatar style", style = MaterialTheme.typography.titleSmall)
            Box(
                modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val url = state.avatarImageUrl ?: state.avatarUrl
                when (state.avatarType) {
                    "male" -> androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.avatar_male),
                        contentDescription = "Profile avatar, male",
                        modifier = Modifier.size(72.dp)
                    )
                    "female" -> androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.avatar_female),
                        contentDescription = "Profile avatar, female",
                        modifier = Modifier.size(72.dp)
                    )
                    "uploaded" -> if (!url.isNullOrBlank()) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Profile avatar, uploaded photo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        )
                    } else {
                        Text(state.fullName.take(2).uppercase().ifBlank { "?" })
                    }
                    else -> Text(state.fullName.take(2).uppercase().ifBlank { "?" })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.avatarType == "initials",
                    onClick = { viewModel.onAvatarTypeChange("initials") },
                    label = { Text("Use initials", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = state.avatarType == "male",
                    onClick = { viewModel.onAvatarTypeChange("male") },
                    label = { Text("Default style 1", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = state.avatarType == "female",
                    onClick = { viewModel.onAvatarTypeChange("female") },
                    label = { Text("Default style 2", style = MaterialTheme.typography.labelMedium) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.avatarType == "uploaded",
                    onClick = {
                        imagePickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    label = { Text(if (state.avatarUploading) "Uploading..." else "Upload photo", style = MaterialTheme.typography.labelMedium) },
                    enabled = !state.avatarUploading
                )
                FilterChip(
                    selected = state.avatarType == "initials" && (state.avatarUrl == null && state.avatarImageUrl == null),
                    onClick = { viewModel.onAvatarTypeChange("initials") },
                    label = { Text("Remove avatar", style = MaterialTheme.typography.labelMedium) }
                )
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = {
                    state.emailError?.let { Text(stringResource(it)) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                } else {
                    Text(stringResource(R.string.save_changes))
                }
            }

            OutlinedTextField(
                value = state.verificationToken,
                onValueChange = viewModel::onVerificationTokenChange,
                label = { Text("Email verification token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.verifyEmail(onBack) },
                enabled = !state.verifying && state.verificationToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.verifying) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                } else {
                    Text("Verify email change")
                }
            }
        }
    }
}

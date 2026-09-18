package com.eventfinder.app.ui.screens.editprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        }
    }
}
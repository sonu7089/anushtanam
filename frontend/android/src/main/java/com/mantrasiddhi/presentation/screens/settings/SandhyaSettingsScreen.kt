package com.mantrasiddhi.presentation.screens.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.presentation.components.*
import com.mantrasiddhi.resources.R

@Composable
fun SandhyaSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    locationViewModel: SandhyaLocationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsState()
    val settings by viewModel.reminderSettings.collectAsState()
    val minimums by viewModel.sandhyaMinimums.collectAsState()
    val saving by viewModel.savingMinimums.collectAsState()
    val saveError by viewModel.minimumsSaveError.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is SettingsEvent.Error) Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        }
    }
    key(user?.id) {
        SandhyaSettingsContent(onBack, minimums, viewModel::setSandhyaMinimums, saving, saveError,
            regionContent = { settings?.let { SandhyaLocationSettings(it, locationViewModel) } },
            reminderContent = { SandhyaReminderSettings(viewModel) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SandhyaSettingsContent(
    onBack: () -> Unit,
    minimums: SandhyaMinimums = SandhyaMinimums(),
    onMinimumsSave: (SandhyaMinimums) -> Unit = {},
    saving: Boolean = false,
    saveError: Boolean = false,
    regionContent: @Composable () -> Unit = {},
    reminderContent: @Composable () -> Unit = {}
) {
    Scaffold(topBar = {
        LotusTopAppBar(title = { Text(stringResource(R.string.sandhya_settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("sandhya-settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sandhya_settings_back))
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(R.string.sandhya_settings_intro), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            regionContent()
            LotusCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SandhyaMinimumSettings(minimums, onMinimumsSave, saving, saveError)
                }
            }
            reminderContent()
        }
    }
}

@Composable
internal fun SandhyaSettingsEntry(onOpen: () -> Unit) {
    LotusCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("sandhya-settings-entry")) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(com.mantrasiddhi.R.drawable.sandhya_vessel),
                contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sandhya_settings_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sandhya_settings_summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

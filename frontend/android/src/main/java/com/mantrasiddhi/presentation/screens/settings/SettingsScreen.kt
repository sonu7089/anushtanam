package com.mantrasiddhi.presentation.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.presentation.components.*

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageDisplay by viewModel.languageDisplay.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.ProfileLoggedOut -> onLogout()
                is SettingsEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    SettingsContent(currentUser, themeMode, languageDisplay,
        viewModel::setThemeMode, viewModel::setLanguageDisplay,
        viewModel::logout, modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(user: UserProfile?, themeMode: String, languageDisplay: String,
    onTheme: (String) -> Unit, onLanguage: (String) -> Unit,
    onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier, topBar = { LotusTopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 152.dp)) {
                LotusArtwork(LotusArt.JASMINE, Modifier.align(Alignment.CenterEnd).width(110.dp).height(180.dp))
                Column(Modifier.fillMaxWidth(0.74f).padding(vertical = 16.dp)) {
                    Text(stringResource(R.string.lotus_settings_intro), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.lotus_settings_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            user?.let {
                LotusCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(it.avatarIndex, it.name, 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it.name, style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(if (it.profileType == com.mantrasiddhi.domain.model.ProfileType.PARENT) R.string.badge_parent else R.string.badge_child), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.settings_change_profile_description),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_app_settings))
                Text(stringResource(R.string.settings_theme_preference), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LIGHT" to R.string.settings_theme_light, "DARK" to R.string.settings_theme_dark,
                        "SYSTEM" to R.string.settings_theme_system).forEach { (value, label) ->
                        PreferenceChip(stringResource(label), themeMode == value, { onTheme(value) })
                    }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.settings_script_display), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DEVANAGARI" to R.string.settings_script_devanagari,
                        "TRANSLITERATION" to R.string.settings_script_transliteration,
                        "BOTH" to R.string.settings_script_both).forEach { (value, label) ->
                        PreferenceChip(stringResource(label), languageDisplay == value, { onLanguage(value) })
                    }
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_about_anushtanam))
                AboutRow(R.string.settings_about_app_version, R.string.settings_about_app_version_value)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(R.string.settings_about_speech_engine, R.string.settings_about_speech_engine_value)
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.settings_switch_profile))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutRow(label: Int, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

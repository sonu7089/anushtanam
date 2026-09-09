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
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.presentation.components.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.presentation.screens.auth.*

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onLogout: () -> Unit, modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {}) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val languageDisplay by viewModel.languageDisplay.collectAsState()
    val minimums by viewModel.sandhyaMinimums.collectAsState()
    val savingMinimums by viewModel.savingMinimums.collectAsState()
    val minimumsSaveError by viewModel.minimumsSaveError.collectAsState()
    val googleViewModel: GoogleAuthViewModel = hiltViewModel()
    val googleState by googleViewModel.state.collectAsState()
    val observedAccount by googleViewModel.account.collectAsState()
    val account = googleState.account ?: observedAccount
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
        viewModel::logout, modifier,
        accountContent = {
            GoogleAccountContent(account, googleState,
                { account?.let { googleViewModel.start(context, linkTo = it) } },
                googleViewModel::retryProfile, googleViewModel::continueWithProfile, googleViewModel::refreshProfile)
        }, signOutEnabled = !googleState.busy,
        minimums = minimums, onMinimumsSave = viewModel::setSandhyaMinimums,
        savingMinimums = savingMinimums, minimumsSaveError = minimumsSaveError, onOpenGuide = onOpenGuide)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(user: UserProfile?, themeMode: String, languageDisplay: String,
    onTheme: (String) -> Unit, onLanguage: (String) -> Unit,
    onLogout: () -> Unit, modifier: Modifier = Modifier,
    accountContent: @Composable () -> Unit = {},
    signOutEnabled: Boolean = true,
    minimums: SandhyaMinimums = SandhyaMinimums(),
    onMinimumsSave: (SandhyaMinimums) -> Unit = {},
    savingMinimums: Boolean = false,
    minimumsSaveError: Boolean = false,
    onOpenGuide: () -> Unit = {}) {
    Scaffold(modifier, topBar = { LotusTopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.fillMaxWidth()) {
                LotusArtwork(LotusArt.JASMINE, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.22f).height(120.dp))
                Column(Modifier.fillMaxWidth(0.74f).padding(vertical = 16.dp)) {
                    Text(stringResource(R.string.lotus_settings_intro), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.lotus_settings_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            user?.let {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(it.avatarIndex, it.name, 56.dp,
                            photoUrl = it.photoUrl.takeIf { _ -> it.useGooglePhoto })
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(when (it.profileType) {
                                com.mantrasiddhi.domain.model.ProfileType.PARENT -> R.string.badge_parent
                                com.mantrasiddhi.domain.model.ProfileType.CHILD -> R.string.badge_child
                                com.mantrasiddhi.domain.model.ProfileType.INDIVIDUAL -> R.string.badge_individual
                            }), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    accountContent()
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
            key(user?.id) {
                SandhyaMinimumSettings(minimums, onMinimumsSave, savingMinimums, minimumsSaveError)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.guide_help))
                com.mantrasiddhi.presentation.screens.guide.PracticeGuideCard(onOpenGuide)
            }
            Column {
                SectionLabel(stringResource(R.string.settings_about_anushtanam))
                AboutRow(R.string.settings_about_app_version, R.string.settings_about_app_version_value)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(R.string.settings_about_speech_engine, R.string.settings_about_speech_engine_value)
            }
            OutlinedButton(onClick = onLogout, enabled = signOutEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
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

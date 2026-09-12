package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.data.sandhya.SandhyaContract
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.presentation.components.SectionLabel
import com.mantrasiddhi.resources.R

@Composable
internal fun SandhyaReminderSettings(viewModel: SettingsViewModel) {
    val settings by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val asked by viewModel.reminderPermissionAsked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    fun allowed(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(SandhyaContract.CHANNEL_ID)?.importance != 0
    }
    var notificationsAllowed by remember { mutableStateOf(allowed()) }
    fun openSettings(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show() }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = allowed()
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !asked && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            viewModel.markReminderPermissionAsked()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = allowed()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    settings?.let { value ->
        ReminderPreferencesCard(value, notificationsAllowed = notificationsAllowed,
            onEnabled = { enabled ->
                viewModel.setRemindersEnabled(enabled)
                if (enabled && !notificationsAllowed) requestNotifications()
            }, onStage = viewModel::setReminderStage, onAllow = { requestNotifications() },
            onSound = { openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, SandhyaContract.CHANNEL_ID)) },
            onBattery = { openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
    }
}

@Composable
internal fun ReminderPreferencesCard(
    settings: SandhyaSettings,
    notificationsAllowed: Boolean = true,
    onEnabled: (Boolean) -> Unit = {},
    onStage: (SandhyaReminderStage, Boolean) -> Unit = { _, _ -> },
    onAllow: () -> Unit = {}, onSound: () -> Unit = {}, onBattery: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.reminder_settings_title))
        Text(stringResource(R.string.reminder_settings_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LotusCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderSwitch(R.string.reminder_master, R.string.reminder_master_detail, settings.enabled, true, onEnabled)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderSwitch(R.string.reminder_preparation, R.string.reminder_preparation_detail,
                    settings.preparationEnabled, settings.enabled) { onStage(SandhyaReminderStage.PREPARATION, it) }
                ReminderSwitch(R.string.reminder_event, R.string.reminder_event_detail,
                    settings.eventEnabled, settings.enabled) { onStage(SandhyaReminderStage.EVENT, it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val status = when {
                    !settings.enabled -> R.string.reminder_off
                    !settings.preparationEnabled && !settings.eventEnabled -> R.string.reminder_no_stages
                    !notificationsAllowed -> R.string.reminder_blocked
                    else -> null
                }
                if (status != null) Text(stringResource(status), style = MaterialTheme.typography.titleSmall,
                    color = if (settings.enabled && !notificationsAllowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (settings.enabled) {
                    if (!notificationsAllowed) Text(stringResource(R.string.reminder_blocked_detail),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!notificationsAllowed) FilledTonalButton(onClick = onAllow) { Text(stringResource(R.string.reminder_allow)) }
                    OutlinedButton(onClick = onSound, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.reminder_sound))
                    }
                    Text(stringResource(R.string.reminder_battery_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onBattery, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.reminder_battery))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderSwitch(title: Int, detail: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

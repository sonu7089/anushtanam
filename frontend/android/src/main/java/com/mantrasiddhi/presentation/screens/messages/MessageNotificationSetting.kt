package com.mantrasiddhi.presentation.screens.messages

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mantrasiddhi.data.messages.TeachingNotifications
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R

@Composable
fun MessageNotificationSetting(viewModel: MessagesViewModel = hiltViewModel()) {
    val notifications = viewModel.notifications
    val enabled by notifications.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val owner = LocalLifecycleOwner.current
    fun systemAllows(): Boolean = notifications.hasPermission() &&
        NotificationManagerCompat.from(context).getNotificationChannel(TeachingNotifications.CHANNEL)?.importance != 0
    var systemAllowed by remember { mutableStateOf(systemAllows()) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications.permissionResult(granted)
        systemAllowed = systemAllows()
        requesting = false
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notifications.refreshPermission()
        systemAllowed = systemAllows()
        requesting = false
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifications.refreshPermission()
                systemAllowed = systemAllows()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    MessageNotificationSettingContent(enabled, systemAllowed, requesting) { wanted ->
        if (!wanted) notifications.setEnabled(false)
        else if (systemAllows()) notifications.setEnabled(true)
        else {
            val runtimeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val canAsk = !runtimeGranted && activity != null && (notifications.needsFirstRequest() ||
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
            requesting = true
            if (canAsk) {
                notifications.markPermissionAsked()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // The toggle records the wish to enable this category. Display remains off
                // until Android also allows it, including after returning from system settings.
                notifications.setEnabled(true)
                val channelBlocked = notifications.hasPermission() &&
                    NotificationManagerCompat.from(context).getNotificationChannel(TeachingNotifications.CHANNEL)?.importance == 0
                val intent = Intent(if (channelBlocked) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                if (channelBlocked) intent.putExtra(Settings.EXTRA_CHANNEL_ID, TeachingNotifications.CHANNEL)
                settings.launch(intent)
            }
        }
    }
}

@Composable
internal fun MessageNotificationSettingContent(enabled: Boolean, systemAllowed: Boolean,
    requesting: Boolean = false, onToggle: (Boolean) -> Unit) {
    LotusCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = enabled, enabled = !requesting, role = Role.Switch,
                onValueChange = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.messages_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = null, enabled = !requesting)
            }
            Text(stringResource(if (systemAllowed) R.string.messages_setting_body else R.string.messages_permission_off),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

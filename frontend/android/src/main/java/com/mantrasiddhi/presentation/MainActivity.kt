/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mantrasiddhi.presentation.navigation.SandhyaReminderTarget
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import com.mantrasiddhi.presentation.navigation.MantraApp
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var reminderScheduler: com.mantrasiddhi.data.sandhya.SandhyaScheduler
    @javax.inject.Inject lateinit var reminderNotifier: com.mantrasiddhi.data.sandhya.SandhyaNotifier
    @javax.inject.Inject lateinit var teachingNotifications: com.mantrasiddhi.data.messages.TeachingNotifications
    private var messageId by mutableStateOf<String?>(null)
    private var homeNotificationId by mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        teachingNotifications.permissionResult(granted)
        reminderScheduler.updateNotificationPermission(reminderNotifier.notificationsAllowed())
    }

    private fun readMessage(intent: Intent): String? {
        val uri = intent.data ?: return null
        return uri.lastPathSegment?.takeIf {
            uri.scheme == "anushtanam" && uri.host == "messages" &&
                com.mantrasiddhi.domain.model.TeachingDeliveryPolicy.validId(it)
        }
    }

    @javax.inject.Inject lateinit var calendarTime: com.mantrasiddhi.domain.util.CalendarTime

    override fun onResume() {
        super.onResume()
        calendarTime.invalidate()
        teachingNotifications.refreshPermission()
        reminderScheduler.updateNotificationPermission(reminderNotifier.notificationsAllowed())
    }

    private var reminderTarget by mutableStateOf<SandhyaReminderTarget?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderTarget = SandhyaReminderTarget.from(intent)
        messageId = readMessage(intent)
        homeNotificationId = com.mantrasiddhi.presentation.navigation.HomeNotificationTarget.from(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("reminderHandled", reminderTarget == null)
        outState.putBoolean("messageHandled", messageId == null)
        outState.putBoolean("homeNotificationHandled", homeNotificationId == null)
        super.onSaveInstanceState(outState)
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppWindow()
        // Reveal the already-laid-out Home frame directly; the system splash's default
        // exit animation otherwise overlaps it with a second whole-window reveal.
        splashScreen.setOnExitAnimationListener { it.remove() }
        if (savedInstanceState?.getBoolean("messageHandled") != true) messageId = readMessage(intent)
        if (savedInstanceState?.getBoolean("homeNotificationHandled") != true) {
            homeNotificationId = com.mantrasiddhi.presentation.navigation.HomeNotificationTarget.from(intent)
        }
        if (teachingNotifications.needsFirstRequest()) {
            teachingNotifications.markPermissionAsked()
            if (teachingNotifications.hasPermission()) teachingNotifications.refreshPermission()
            else notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState?.getBoolean("reminderHandled") != true) reminderTarget = SandhyaReminderTarget.from(intent)
        setContent {
            val startupReady = androidx.compose.runtime.remember { mutableStateOf(false) }
            androidx.activity.compose.ReportDrawnWhen { startupReady.value }
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            AnushtanamTheme(darkTheme = darkTheme) {
                MantraApp(mainViewModel = mainViewModel, reminderTarget = reminderTarget,
                    onStartupReady = { startupReady.value = true },
                    onReminderHandled = { reminderTarget = null; intent.action = null; intent.data = null },
                    messageId = messageId, onMessageHandled = { messageId = null; intent.data = null },
                    homeNotificationId = homeNotificationId,
                    onHomeNotificationHandled = { homeNotificationId = null; intent.data = null })
            }
        }
    }
}



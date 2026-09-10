/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mantrasiddhi.presentation.navigation.SandhyaReminderTarget
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mantrasiddhi.presentation.navigation.MantraApp
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var teachingNotifications: com.mantrasiddhi.data.messages.TeachingNotifications
    private var messageId by mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> teachingNotifications.permissionResult(granted) }

    private fun readMessage(intent: Intent): String? {
        val uri = intent.data ?: return null
        return uri.lastPathSegment?.takeIf {
            uri.scheme == "anushtanam" && uri.host == "messages" &&
                com.mantrasiddhi.domain.model.TeachingDeliveryPolicy.validId(it)
        }
    }

    override fun onResume() {
        super.onResume()
        teachingNotifications.refreshPermission()
    }

    private var reminderTarget by mutableStateOf<SandhyaReminderTarget?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderTarget = SandhyaReminderTarget.from(intent)
        messageId = readMessage(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("reminderHandled", reminderTarget == null)
        outState.putBoolean("messageHandled", messageId == null)
        super.onSaveInstanceState(outState)
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean("messageHandled") != true) messageId = readMessage(intent)
        if (teachingNotifications.needsFirstRequest()) {
            teachingNotifications.markPermissionAsked()
            if (teachingNotifications.hasPermission()) teachingNotifications.refreshPermission()
            else notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState?.getBoolean("reminderHandled") != true) reminderTarget = SandhyaReminderTarget.from(intent)
        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState(initial = "SYSTEM")
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            AnushtanamTheme(darkTheme = darkTheme) {
                MantraApp(mainViewModel = mainViewModel, reminderTarget = reminderTarget,
                    onReminderHandled = { reminderTarget = null; intent.action = null; intent.data = null },
                    messageId = messageId, onMessageHandled = { messageId = null; intent.data = null })
            }
        }
    }
}



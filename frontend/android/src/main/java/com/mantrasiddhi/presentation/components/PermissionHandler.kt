/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * A reusable Composable that checks and requests runtime permissions.
 *
 * @param permission The Android permission string to request.
 * @param onPermissionGranted Callback invoked when the permission is granted.
 * @param onPermissionDenied Callback invoked when the permission is denied.
 * @param rationaleContent Content to display when permission is not granted,
 *                           providing a function to trigger the request.
 */
@Composable
fun PermissionHandler(
    permission: String = android.Manifest.permission.RECORD_AUDIO,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {},
    rationaleContent: @Composable (onRequestPermission: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            onPermissionGranted()
        }
    }

    if (!permissionGranted) {
        rationaleContent {
            launcher.launch(permission)
        }
    }
}

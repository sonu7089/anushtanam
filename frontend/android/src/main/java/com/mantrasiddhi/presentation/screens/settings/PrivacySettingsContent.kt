package com.mantrasiddhi.presentation.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.LotusCard

@Composable
internal fun PrivacySettingsContent(uid: String?, onDeleted: () -> Unit,
    viewModel: AccountDeletionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmUid by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<String?>(null) }
    val emailUnavailable = stringResource(R.string.privacy_email_unavailable)
    fun email(address: Uri = Uri.parse("mailto:NitishSongara75@gmail.com?subject=Help%20with%20my%20Anushtanam%20account")) {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, address))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, emailUnavailable, Toast.LENGTH_LONG).show()
        }
    }
    PrivacySettingsCard(canDelete = uid != null, enabled = !state.busy && !state.accepted,
        onPolicy = { document = "privacy-policy.html" }, onDeletionInfo = { document = "delete-account.html" },
        onDelete = { confirmUid = uid }, onEmail = { email() })
    if (confirmUid != null && !state.accepted) {
        AlertDialog(onDismissRequest = { if (!state.busy) { confirmUid = null; password = "" } },
            title = { Text(stringResource(R.string.privacy_delete_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.privacy_delete_confirmation))
                    Text(stringResource(R.string.privacy_delete_other_accounts))
                    Text(stringResource(R.string.privacy_delete_phone), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (viewModel.passwordRequired) R.string.privacy_delete_verify_password
                        else R.string.privacy_delete_verify))
                    if (viewModel.passwordRequired) OutlinedTextField(value = password,
                        onValueChange = { password = it }, enabled = !state.busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.privacy_password)) })
                    if (state.failed) Text(stringResource(R.string.privacy_request_failed), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (state.busy) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.privacy_request_busy), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(enabled = !state.busy && (!viewModel.passwordRequired || password.isNotBlank()),
                onClick = { viewModel.request(context, requireNotNull(confirmUid), password); password = "" }) {
                Text(stringResource(if (viewModel.passwordRequired) R.string.privacy_confirm_delete_password
                    else R.string.privacy_confirm_delete))
            } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = { confirmUid = null; password = "" }) {
                Text(stringResource(R.string.privacy_cancel))
            } })
    }
    if (state.accepted) AlertDialog(onDismissRequest = {},
        title = { Text(stringResource(R.string.privacy_request_accepted)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.privacy_request_accepted_detail))
            Text(stringResource(R.string.privacy_delete_phone), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = onDeleted) { Text(stringResource(R.string.privacy_sign_out)) } })
    document?.let { filename ->
        val colors = MaterialTheme.colorScheme
        fun css(color: androidx.compose.ui.graphics.Color) = "#%06X".format(color.toArgb() and 0xffffff)
        val documentStyle = """
            <style>
            body { background:${css(colors.surface)};color:${css(colors.onSurface)};padding:16px 20px 32px; }
            h1 { font-size:1.4rem; } h2 { font-size:1.1rem; }
            h1,h2,a { color:${css(colors.primary)}; }
            .updated { color:${css(colors.onSurfaceVariant)}; }
            .callout,th,code { background:${css(colors.surfaceContainerHigh)}; }
            .callout { border-color:${css(colors.primary)}; }
            td,th { border-color:${css(colors.outlineVariant)}; }
            table { display:block;overflow-x:auto; }
            </style>
        """.trimIndent()
        val textZoom = (LocalDensity.current.fontScale * 100).toInt()
        Dialog(onDismissRequest = { document = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (filename == "privacy-policy.html") R.string.privacy_policy
                            else R.string.privacy_deletion_info), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { document = null }) { Text(stringResource(R.string.privacy_close)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.blockNetworkLoads = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    when {
                                        request.url.scheme == "mailto" -> email(request.url)
                                        request.url.lastPathSegment == "delete-account.html" -> document = "delete-account.html"
                                        request.url.lastPathSegment in listOf("index.html", "privacy-policy.html") -> document = "privacy-policy.html"
                                    }
                                    return true
                                }
                            }
                        }
                    }, update = { web ->
                        web.setBackgroundColor(colors.surface.toArgb())
                        web.settings.textZoom = textZoom
                        val version = filename + documentStyle
                        if (web.tag != version) {
                            web.tag = version
                            val html = context.assets.open("privacy/$filename").bufferedReader().use { it.readText() }
                            web.loadDataWithBaseURL("https://privacy.invalid/", html.replace("</head>", "$documentStyle</head>"),
                                "text/html", "UTF-8", null)
                        }
                    }, onRelease = { it.destroy() })
                }
            }
        }
    }
}

@Composable
internal fun PrivacySettingsCard(canDelete: Boolean, enabled: Boolean = true, onPolicy: () -> Unit,
    onDeletionInfo: () -> Unit, onDelete: () -> Unit, onEmail: () -> Unit) {
    LotusCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))
        PrivacyDocumentRow(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_summary), onPolicy)
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        PrivacyDocumentRow(stringResource(R.string.privacy_deletion_info), stringResource(R.string.privacy_deletion_summary), onDeletionInfo)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDelete) OutlinedButton(onClick = onDelete, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.privacy_delete_account))
            }
            TextButton(onClick = onEmail, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.privacy_email_request))
            }
        }
    }
}

@Composable
private fun PrivacyDocumentRow(title: String, summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

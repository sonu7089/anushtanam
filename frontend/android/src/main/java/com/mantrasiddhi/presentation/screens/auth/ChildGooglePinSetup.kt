package com.mantrasiddhi.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R

@Composable
internal fun ChildGooglePinSetup(state: GoogleAuthUiState, onSave: (String, String) -> Unit) {
    // Keep PINs only in memory, never in saved instance state or preferences.
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.child_pin_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.child_pin_body))
        state.account?.linkCode?.let {
            Text(stringResource(R.string.child_new_id, it), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(pin, { if (it.matches(Regex("[0-9]{0,6}"))) pin = it },
            label = { Text(stringResource(R.string.auth_pin_label)) },
            modifier = Modifier.fillMaxWidth(), enabled = !state.busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(confirmation, { if (it.matches(Regex("[0-9]{0,6}"))) confirmation = it },
            label = { Text(stringResource(R.string.child_pin_confirm)) },
            modifier = Modifier.fillMaxWidth(), enabled = !state.busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation())
        Button({ onSave(pin, confirmation); pin = ""; confirmation = "" },
            enabled = !state.busy && pin.length == 6 && confirmation.length == 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.child_pin_save))
        }
    }
}

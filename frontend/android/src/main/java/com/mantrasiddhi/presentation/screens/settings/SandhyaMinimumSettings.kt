package com.mantrasiddhi.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.SandhyaCountMath
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.presentation.screens.sandhya.countText
import com.mantrasiddhi.presentation.screens.sandhya.periodNameRes
import com.mantrasiddhi.resources.R

@Composable
internal fun SandhyaMinimumSettings(
    minimums: SandhyaMinimums,
    onSave: (SandhyaMinimums) -> Unit,
    saving: Boolean = false,
    saveError: Boolean = false,
    initiallyEditing: Boolean = false
) {
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    var pratah by rememberSaveable { mutableStateOf(minimums.pratah.toString()) }
    var madhyahna by rememberSaveable { mutableStateOf(minimums.madhyahna.toString()) }
    var sayam by rememberSaveable { mutableStateOf(minimums.sayam.toString()) }
    var submitted by remember { mutableStateOf<SandhyaMinimums?>(null) }
    LaunchedEffect(minimums, submitted) {
        if (submitted != null && minimums == submitted) {
            editing = false
            submitted = null
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sandhya_minimum_settings), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                pratah = minimums.pratah.toString()
                madhyahna = minimums.madhyahna.toString()
                sayam = minimums.sayam.toString()
                submitted = null
                editing = true
            }, contentPadding = PaddingValues(start = 12.dp)) { Text(stringResource(R.string.sandhya_change)) }
        }
        SandhyaPeriod.entries.forEach { period ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(periodNameRes(period)), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(countText(minimums[period]), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (editing) {
        val values = listOf(pratah, madhyahna, sayam).map(SandhyaCountMath::parse)
        AlertDialog(onDismissRequest = { if (!saving) editing = false },
            title = { Text(stringResource(R.string.sandhya_minimum_settings)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val inputs = listOf(pratah, madhyahna, sayam)
                    SandhyaPeriod.entries.forEachIndexed { index, period ->
                        OutlinedTextField(value = inputs[index], onValueChange = {
                            when (period) {
                                SandhyaPeriod.PRATAH -> pratah = it
                                SandhyaPeriod.MADHYAHNA -> madhyahna = it
                                SandhyaPeriod.SAYAM -> sayam = it
                            }
                        }, label = { Text(stringResource(periodNameRes(period))) },
                            singleLine = true, enabled = !saving,
                            isError = values[index] == null && inputs[index].isNotEmpty(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("minimum-${period.name}"))
                    }
                    if (values.any { it == null } && listOf(pratah, madhyahna, sayam).any { it.isNotEmpty() }) {
                        Text(stringResource(R.string.sandhya_count_invalid), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (saveError) Text(stringResource(R.string.sandhya_count_save_error),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = SandhyaMinimums(values[0]!!, values[1]!!, values[2]!!)
                    submitted = next
                    onSave(next)
                }, enabled = !saving && values.all { it != null }, modifier = Modifier.testTag("minimum-save")) {
                    Text(stringResource(R.string.sandhya_minimum_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }, enabled = !saving) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            })
    }
}

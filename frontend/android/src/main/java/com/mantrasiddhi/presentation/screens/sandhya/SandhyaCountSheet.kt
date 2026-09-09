package com.mantrasiddhi.presentation.screens.sandhya

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.util.SandhyaDateKey
import com.mantrasiddhi.resources.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.math.BigInteger

internal fun countText(count: Long): String = NumberFormat.getIntegerInstance().format(count)
@Composable
internal fun mantraCountText(count: Long): String = mantraCountText(BigInteger.valueOf(count))
@Composable
internal fun mantraCountText(count: BigInteger): String = stringResource(
    if (count == BigInteger.ONE) R.string.sandhya_one_mantra else R.string.sandhya_period_count,
    NumberFormat.getIntegerInstance().format(count))
internal fun practiceDateText(dateKey: String): String =
    SandhyaDateKey.parse(dateKey).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SandhyaCountSheet(
    editor: SandhyaCountEditor,
    onInput: (String) -> Unit,
    onStep: (Long) -> Unit,
    onPause: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit = {},
    onChooseDate: (() -> Unit)? = null,
    minimumCount: Long = editor.draft.period.minimumJapa
) {
    val draft = editor.draft
    val counter = draft.mode == SandhyaCountMode.COUNTER
    val enabled = !editor.saving
    val valid = draft.newTotal != null
    var showHistory by remember(draft.id) { mutableStateOf(false) }
    var editingCount by rememberSaveable(draft.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val countFocus = remember { FocusRequester() }
    LaunchedEffect(editingCount) { if (editingCount) { countFocus.requestFocus(); keyboard?.show() } }
    ModalBottomSheet(
        onDismissRequest = { if (enabled) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(when (draft.mode) {
                    SandhyaCountMode.COUNTER -> R.string.sandhya_mental_japa
                    SandhyaCountMode.ADD -> R.string.sandhya_add_more
                    SandhyaCountMode.EDIT -> R.string.sandhya_edit_total
                    SandhyaCountMode.RECORD -> R.string.sandhya_record_count
                }), style = MaterialTheme.typography.headlineSmall)
                val context = stringResource(R.string.sandhya_count_context,
                    stringResource(periodNameRes(draft.period)), practiceDateText(draft.dateKey))
                if (onChooseDate != null && draft.mode != SandhyaCountMode.EDIT) {
                    TextButton(onClick = onChooseDate, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                        Text(context, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(context, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (counter) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingCount) {
                        OutlinedTextField(value = draft.input, onValueChange = onInput,
                            label = { Text(stringResource(R.string.sandhya_gayatri_count)) },
                            enabled = enabled, singleLine = true, isError = !valid && draft.input.isNotEmpty(),
                            textStyle = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().focusRequester(countFocus).testTag("sandhya-count-input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (valid) { editingCount = false; keyboard?.hide() }
                            }))
                    } else {
                        TextButton(onClick = { editingCount = true }, enabled = enabled,
                            contentPadding = PaddingValues(0.dp)) {
                            Text(draft.quantity?.let(::countText) ?: draft.input,
                                modifier = Modifier.testTag("sandhya-counter-total"),
                                style = if (draft.input.length > 10) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayLarge,
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Edit, stringResource(R.string.sandhya_edit_count), Modifier.size(18.dp))
                        }
                    }
                    Text(stringResource(R.string.sandhya_minimum, countText(minimumCount)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { onStep(1) }, enabled = enabled && !editor.paused && draft.quantity != Long.MAX_VALUE,
                        shape = CircleShape, modifier = Modifier.size(144.dp).testTag("sandhya-counter-tap")) {
                        Text("+1", style = MaterialTheme.typography.displayMedium)
                    }
                    Text(stringResource(R.string.sandhya_tap_each), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = onUndo, enabled = enabled && editor.undoInput != null) {
                            Text(stringResource(R.string.sandhya_count_undo))
                        }
                        TextButton(onClick = { onStep(108) }, enabled = enabled && !editor.paused &&
                            (draft.quantity ?: Long.MAX_VALUE) <= Long.MAX_VALUE - 108) { Text("+108") }
                        TextButton(onClick = onPause, enabled = enabled) {
                            Text(stringResource(if (editor.paused) R.string.sandhya_count_resume else R.string.recitation_pause))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedIconButton(onClick = { onStep(-1) }, enabled = enabled && (draft.quantity ?: 0) > 0,
                        modifier = Modifier.size(48.dp)) {
                        val description = stringResource(R.string.sandhya_count_decrease)
                        Text("−", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = description })
                    }
                    OutlinedTextField(value = draft.input, onValueChange = onInput, enabled = enabled,
                        modifier = Modifier.weight(1f).testTag("sandhya-count-input"),
                        label = { Text(stringResource(R.string.sandhya_gayatri_count)) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                        singleLine = true, isError = !valid && draft.input.isNotEmpty(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedIconButton(onClick = { onStep(1) }, enabled = enabled && draft.quantity != null && draft.quantity != Long.MAX_VALUE,
                        modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Add, stringResource(R.string.sandhya_count_increase))
                    }
                }
                if (!valid && draft.input.isNotEmpty()) {
                    Text(stringResource(if (draft.input.all { it in '0'..'9' }) R.string.sandhya_count_too_large else R.string.sandhya_count_invalid), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onInput(minimumCount.toString()) }, enabled = enabled,
                        label = { Text(stringResource(R.string.sandhya_minimum, countText(minimumCount))) })
                    AssistChip(onClick = { onStep(10) }, enabled = enabled && draft.quantity != null &&
                        draft.quantity!! <= Long.MAX_VALUE - 10, label = { Text("+10") })
                }
            }

            if (counter && !valid) {
                Text(stringResource(if (draft.input.all { it in '0'..'9' } && draft.input.isNotEmpty())
                    R.string.sandhya_count_too_large else R.string.sandhya_count_invalid), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.baseQuantity?.let {
                        Text(stringResource(R.string.sandhya_already_recorded, countText(it)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    draft.newTotal?.let { total ->
                        Text(stringResource(R.string.sandhya_new_total, countText(total)),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
            editor.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onSave, enabled = enabled && valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("sandhya-count-save")) {
                Text(stringResource(if (editor.saving) R.string.sandhya_count_saving else R.string.sandhya_count_save))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDiscard, enabled = enabled) { Text(stringResource(R.string.sandhya_discard_draft)) }
                if (editor.history.isNotEmpty()) {
                    TextButton(onClick = { showHistory = !showHistory }) { Text(stringResource(R.string.sandhya_count_history)) }
                }
            }
            if (showHistory) {
                editor.history.forEach { change ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        val previous = change.previousQuantity
                        Text(if (previous == null) countText(change.quantity) else
                            stringResource(R.string.sandhya_count_change, countText(previous), countText(change.quantity)),
                            style = MaterialTheme.typography.bodyLarge)
                        Text(Instant.ofEpochMilli(change.createdAtMillis).atZone(ZoneId.of(change.zoneId))
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

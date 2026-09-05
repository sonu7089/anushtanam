/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.family

import androidx.compose.foundation.clickable
import com.mantrasiddhi.presentation.components.PreferenceChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.presentation.components.MantraTextField

private fun rewardTypeLabelRes(type: RewardType): Int = when (type) {
    RewardType.MONEY -> R.string.reward_type_money
    RewardType.FOOD -> R.string.reward_type_food
    RewardType.GIFT -> R.string.reward_type_gift
    RewardType.OUTING -> R.string.reward_type_outing
    RewardType.TOY -> R.string.reward_type_toy
    RewardType.OTHER -> R.string.reward_type_other
}

/**
 * Parent dialog to set a cloud recitation goal (count + reward) for a linked child.
 * Reuses the reward-type/description shape of the local SetTargetDialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudGoalDialog(
    childName: String,
    onDismiss: () -> Unit,
    onConfirm: (rewardType: RewardType, rewardDescription: String, targetCount: Int) -> Unit
) {
    val presets = listOf(108, 1008, 10008)
    var selectedPreset by remember { mutableStateOf(108) }
    var isCustom by remember { mutableStateOf(false) }
    var customCount by remember { mutableStateOf("") }
    var rewardType by remember { mutableStateOf(RewardType.OTHER) }
    var rewardDescription by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidCountMsg = stringResource(R.string.target_dialog_error_invalid_count)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.family_goal_dialog_title, childName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.target_dialog_recitation_count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { count ->
                        PreferenceChip(java.text.NumberFormat.getIntegerInstance().format(count),
                            !isCustom && selectedPreset == count, { isCustom = false; selectedPreset = count })
                    }
                    PreferenceChip(stringResource(R.string.target_dialog_custom), isCustom,
                        { isCustom = true })
                }
                if (isCustom) {
                    MantraTextField(
                        value = customCount,
                        onValueChange = { if (it.all(Char::isDigit)) customCount = it },
                        label = stringResource(R.string.target_dialog_custom_count_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = stringResource(R.string.target_dialog_reward_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RewardType.entries.forEach { type ->
                        PreferenceChip(
                                text = "${type.emoji} ${stringResource(rewardTypeLabelRes(type))}",
                                selected = rewardType == type,
                                onClick = { rewardType = type }
                            )
                    }
                }
                MantraTextField(
                    value = rewardDescription,
                    onValueChange = { rewardDescription = it },
                    label = stringResource(R.string.target_dialog_reward_desc_label),
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                val count = if (isCustom) customCount.toIntOrNull() ?: 0 else selectedPreset
                if (count <= 0) {
                    error = invalidCountMsg
                    return@FilledTonalButton
                }
                onConfirm(rewardType, rewardDescription.trim(), count)
            }) {
                Text(stringResource(R.string.family_goal_dialog_confirm), fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.target_dialog_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

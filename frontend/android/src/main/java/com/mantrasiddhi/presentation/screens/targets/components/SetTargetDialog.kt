@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.mantrasiddhi.presentation.components.PreferenceChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.presentation.components.MantraTextField
import com.mantrasiddhi.presentation.components.ProfileAvatar

/** Maps a [RewardType] to its display label string resource. */
private fun rewardTypeLabelRes(type: RewardType): Int = when (type) {
    RewardType.MONEY -> R.string.reward_type_money
    RewardType.FOOD -> R.string.reward_type_food
    RewardType.GIFT -> R.string.reward_type_gift
    RewardType.OUTING -> R.string.reward_type_outing
    RewardType.TOY -> R.string.reward_type_toy
    RewardType.OTHER -> R.string.reward_type_other
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetTargetDialog(
    currentUser: UserProfile,
    children: List<UserProfile>,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, rewardType: RewardType, rewardDescription: String, targetUserId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    initialUserId: Long? = null
) {
    val context = LocalContext.current
    var selectedUserId by remember { mutableStateOf<Long?>(initialUserId ?: currentUser.id) }
    var selectedPresetCount by remember { mutableStateOf(108) }
    var isCustomCount by remember { mutableStateOf(false) }
    var customCountString by remember { mutableStateOf("") }
    var selectedRewardType by remember { mutableStateOf(RewardType.OTHER) }
    var rewardDescription by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    val presets = listOf(108, 1008, 10008)
    val isParent = currentUser.profileType == ProfileType.PARENT
    // Only a parent profile may attach a reward to a target. A child setting their
    // own target picks a recitation count only; the reward defaults to OTHER/blank.
    val canSetReward = isParent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.target_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Parent profile child selector
                if (isParent && children.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.target_dialog_child_selector),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Option for parent themselves
                        ProfileSelectorItem(
                            user = currentUser,
                            isSelected = selectedUserId == currentUser.id,
                            onClick = { selectedUserId = currentUser.id }
                        )

                        // Options for each child
                        children.forEach { child ->
                            ProfileSelectorItem(
                                user = child,
                                isSelected = selectedUserId == child.id,
                                onClick = { selectedUserId = child.id }
                            )
                        }
                    }
                }

                // Recitation count configuration
                Text(
                    text = stringResource(id = R.string.target_dialog_recitation_count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { count ->
                        PreferenceChip(java.text.NumberFormat.getIntegerInstance().format(count),
                            !isCustomCount && selectedPresetCount == count, {
                                isCustomCount = false
                                selectedPresetCount = count
                            })
                    }
                    PreferenceChip(stringResource(R.string.target_dialog_custom), isCustomCount,
                        { isCustomCount = true })
                }

                if (isCustomCount) {
                    MantraTextField(
                        value = customCountString,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) {
                                customCountString = it
                            }
                        },
                        label = stringResource(id = R.string.target_dialog_custom_count_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Reward configuration — only available to parent profiles.
                if (canSetReward) {
                    Text(
                        text = stringResource(id = R.string.target_dialog_reward_title),
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
                                selected = selectedRewardType == type,
                                onClick = { selectedRewardType = type }
                            )
                        }
                    }

                    MantraTextField(
                        value = rewardDescription,
                        onValueChange = { rewardDescription = it },
                        label = stringResource(id = R.string.target_dialog_reward_desc_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.target_dialog_reward_parent_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                errorMsg?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val finalCount = if (isCustomCount) {
                        customCountString.toIntOrNull() ?: 0
                    } else {
                        selectedPresetCount
                    }

                    if (finalCount <= 0) {
                        errorMsg = context.getString(R.string.target_dialog_error_invalid_count)
                        return@FilledTonalButton
                    }

                    // Non-parents can never attach a reward: force OTHER/blank.
                    val finalRewardType = if (canSetReward) selectedRewardType else RewardType.OTHER
                    val finalRewardDescription = if (canSetReward) rewardDescription.trim() else ""

                    onConfirm(finalCount, finalRewardType, finalRewardDescription, selectedUserId)
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(id = R.string.target_dialog_confirm),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.target_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ProfileSelectorItem(
    user: UserProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(borderColor)
                .padding(2.dp)
        ) {
            ProfileAvatar(
                avatarIndex = user.avatarIndex,
                name = user.name,
                size = 60.dp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

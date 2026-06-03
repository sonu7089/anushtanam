/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.presentation.components.MantraTextField
import com.mantrasiddhi.presentation.components.ProfileAvatar

@Composable
fun SetTargetDialog(
    currentUser: UserProfile,
    children: List<UserProfile>,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, moneyAmount: Double, targetUserId: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedUserId by remember { mutableStateOf<Long?>(currentUser.id) }
    var selectedPresetCount by remember { mutableStateOf(108) }
    var isCustomCount by remember { mutableStateOf(false) }
    var customCountString by remember { mutableStateOf("") }
    var moneyAmountString by remember { mutableStateOf("") }
    
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val presets = listOf(108, 1008, 10008)
    val isParent = currentUser.profileType == ProfileType.PARENT
    // Only a parent profile may attach a virtual-money reward to a target.
    // A child setting their own target can pick a recitation count but never a money reward.
    val canSetMoney = isParent
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.target_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    fontWeight = FontWeight.Bold
                )
                
                presets.forEach { count ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCustomCount = false
                                selectedPresetCount = count
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isCustomCount && selectedPresetCount == count,
                            onClick = {
                                isCustomCount = false
                                selectedPresetCount = count
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.target_dialog_preset, count),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomCount = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomCount,
                        onClick = { isCustomCount = true },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.target_dialog_custom),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                
                // Money Amount configuration — only available to parent profiles.
                if (canSetMoney) {
                    Text(
                        text = stringResource(id = R.string.target_dialog_money),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    MantraTextField(
                        value = moneyAmountString,
                        onValueChange = {
                            if (it.isEmpty() || it.toDoubleOrNull() != null || it.all { char -> char.isDigit() || char == '.' }) {
                                moneyAmountString = it
                            }
                        },
                        label = stringResource(id = R.string.target_dialog_reward_amount_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.target_dialog_money_parent_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                    
                    val finalMoney = if (canSetMoney) moneyAmountString.toDoubleOrNull() ?: 0.0 else 0.0
                    
                    if (finalCount <= 0) {
                        errorMsg = context.getString(R.string.target_dialog_error_invalid_count)
                        return@FilledTonalButton
                    }
                    if (finalMoney < 0.0) {
                        errorMsg = context.getString(R.string.target_dialog_error_negative_money)
                        return@FilledTonalButton
                    }
                    
                    onConfirm(finalCount, finalMoney, selectedUserId)
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(id = R.string.target_dialog_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.target_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

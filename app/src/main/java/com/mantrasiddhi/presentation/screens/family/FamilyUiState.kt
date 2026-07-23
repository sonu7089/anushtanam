/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.family

import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.ParentChildLink

/**
 * State for the Family screen (adapts to the signed-in account's role).
 */
data class FamilyUiState(
    val account: AuthAccount? = null,
    val isBusy: Boolean = false,
    // Parent
    val linkedChildren: List<ParentChildLink> = emptyList(),
    val selectedChild: ParentChildLink? = null,
    val selectedChildGoals: List<CloudGoal> = emptyList(),
    // Child
    val pendingRequests: List<ParentChildLink> = emptyList(),
    val myGoals: List<CloudGoal> = emptyList()
)

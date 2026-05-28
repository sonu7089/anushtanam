/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.presentation.components.MantraButton
import com.mantrasiddhi.presentation.components.MantraCard
import com.mantrasiddhi.presentation.components.SavingsCard
import com.mantrasiddhi.presentation.screens.childprogress.components.ChildCard
import com.mantrasiddhi.presentation.screens.childprogress.components.ProgressChart
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog
import com.mantrasiddhi.presentation.screens.targets.components.TargetCard

@Composable
fun ChildProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ChildProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detailUiState by viewModel.detailUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showSetTargetDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(key1 = true) {
        viewModel.events.collect { event ->
            when (event) {
                is ChildProgressEvent.TargetSetSuccess -> {
                    showSetTargetDialog = false
                    snackbarHostState.showSnackbar("Target set successfully for child!")
                }
                is ChildProgressEvent.ShowError -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ChildProgressUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is ChildProgressUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is ChildProgressUiState.Success -> {
                    // List of Children Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.children_progress_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (state.childrenProgress.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No child profiles associated with this account.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(state.childrenProgress) { childProgress ->
                                    ChildCard(
                                        childProgress = childProgress,
                                        onClick = { viewModel.selectChild(childProgress.child.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Sliding Child Detail Screen Overlay
            AnimatedVisibility(
                visible = detailUiState !is ChildDetailUiState.Idle,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                // Intercept back button to close detail view instead of exit app
                BackHandler(enabled = detailUiState !is ChildDetailUiState.Idle) {
                    viewModel.clearSelectedChild()
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (val detailState = detailUiState) {
                        is ChildDetailUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is ChildDetailUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = detailState.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.clearSelectedChild() }) {
                                    Text(text = "Go Back")
                                }
                            }
                        }
                        is ChildDetailUiState.Success -> {
                            ChildDetailView(
                                childProgress = detailState.childProgress,
                                weakWords = detailState.weakWords,
                                onBackClick = { viewModel.clearSelectedChild() },
                                onSetTargetClick = { showSetTargetDialog = true }
                            )
                            
                            if (showSetTargetDialog) {
                                SetTargetDialog(
                                    currentUser = (uiState as? ChildProgressUiState.Success)?.parentUser ?: detailState.childProgress.child,
                                    children = emptyList(), // Dialog handles single user target setting directly
                                    onDismiss = { showSetTargetDialog = false },
                                    onConfirm = { count, money, _ ->
                                        viewModel.setChildTarget(detailState.childProgress.child.id, count, money)
                                    }
                                )
                            }
                        }
                        else -> { /* Idle */ }
                    }
                }
            }
        }
    }
}

@Composable
fun ChildDetailView(
    childProgress: ChildProgress,
    weakWords: List<String>,
    onBackClick: () -> Unit,
    onSetTargetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.child_details_title, childProgress.child.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Savings Card
            item {
                childProgress.savings?.let {
                    SavingsCard(savings = it)
                }
            }
            
            // Active Target Card
            item {
                Text(
                    text = "Active Target Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (childProgress.activeTarget != null) {
                item {
                    TargetCard(
                        target = childProgress.activeTarget,
                        onAbandonClick = { /* Child targets are managed by parent */ },
                        ownerName = childProgress.child.name
                    )
                }
            } else {
                item {
                    MantraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "No active target is set for ${childProgress.child.name}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            MantraButton(
                                text = "Set Target",
                                onClick = onSetTargetClick,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                    }
                }
            }
            
            // Recitation Activity Chart
            item {
                ProgressChart(sessions = childProgress.recentSessions)
            }
            
            // Improvement Areas (Weak Words)
            item {
                Text(
                    text = stringResource(id = R.string.child_struggle_words),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (weakWords.isNotEmpty()) {
                item {
                    MantraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Improvement area",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Below is the list of top 3 words ${childProgress.child.name} struggles to pronounce accurately:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                weakWords.forEach { word ->
                                    Text(
                                        text = "• $word",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    MantraCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(id = R.string.child_no_weak_words),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50), // Green for success
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

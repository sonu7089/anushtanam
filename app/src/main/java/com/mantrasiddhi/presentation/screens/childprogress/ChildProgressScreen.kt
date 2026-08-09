/*
 * Anushtanam - Gayatri Mantra Recitation App
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.domain.model.ChildProgress
import androidx.compose.material3.Card
import com.mantrasiddhi.presentation.screens.childprogress.components.ChildCard
import com.mantrasiddhi.presentation.screens.childprogress.components.ProgressChart
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog
import com.mantrasiddhi.presentation.screens.targets.components.TargetCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ChildProgressViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val detailUiState by viewModel.detailUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showSetTargetDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(key1 = true) {
        viewModel.events.collect { event ->
            when (event) {
                is ChildProgressEvent.TargetSetSuccess -> {
                    showSetTargetDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.child_target_set_success))
                }
                is ChildProgressEvent.ShowError -> {
                    // Prefer the failure's own detail when the layer below supplied one;
                    // it names the actual problem better than the generic sentence can.
                    val reason = event.detail?.takeIf { it.isNotBlank() }
                        ?: context.getString(event.messageResId)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.child_error_format, reason)
                    )
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.children_progress_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
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
                            text = stringResource(id = state.messageResId),
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
                        if (state.childrenProgress.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.child_no_profiles),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(state.childrenProgress, key = { it.child.id }) { childProgress ->
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
                                Text(
                                    text = stringResource(id = detailState.messageResId),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.clearSelectedChild() }) {
                                    Text(text = stringResource(id = R.string.child_button_go_back))
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
                                    onConfirm = { count, rewardType, rewardDescription, _ ->
                                        viewModel.setChildTarget(detailState.childProgress.child.id, count, rewardType, rewardDescription)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailView(
    childProgress: ChildProgress,
    weakWords: List<String>,
    onBackClick: () -> Unit,
    onSetTargetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.child_details_title, childProgress.child.name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.child_desc_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Target Card
            item {
                Text(
                    text = stringResource(id = R.string.child_active_target_status),
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.child_no_active_target, childProgress.child.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onSetTargetClick,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.child_set_target),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = stringResource(id = R.string.child_desc_improvement_area),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.child_struggle_words_desc, childProgress.child.name),
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = stringResource(id = R.string.child_no_weak_words),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extendedColors.success,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(16.dp)
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

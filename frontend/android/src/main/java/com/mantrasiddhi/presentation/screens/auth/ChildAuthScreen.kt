package com.mantrasiddhi.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.presentation.components.*
import com.mantrasiddhi.resources.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildAuthScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    googleViewModel: GoogleAuthViewModel = hiltViewModel()
) {
    val state by googleViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.completed) { if (state.completed) onAuthenticated() }
    Scaffold(modifier = modifier, topBar = {
        LotusTopAppBar(title = { Text(stringResource(R.string.auth_child_login_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.auth_back))
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            PageIntro(stringResource(R.string.lotus_child_intro),
                stringResource(R.string.child_google_only_help), LotusArt.LOTUS)
            GoogleSignInButton({ googleViewModel.start(context, AccountRole.CHILD) },
                enabled = !state.busy && !state.needsProfileRetry && !state.completed)
            GoogleAuthFeedback(state, googleViewModel::retryProfile,
                googleViewModel::continueWithProfile, googleViewModel::chooseAnotherAccount)
        }
    }
}

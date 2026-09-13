package com.mantrasiddhi.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.mantrasiddhi.presentation.navigation.FloatingNavigationBar
import com.mantrasiddhi.presentation.navigation.Screen
import com.mantrasiddhi.presentation.navigation.navigationDestinations
import com.mantrasiddhi.resources.R

/** Standalone preview/fallback of the same Home layout used before and after profile readiness. */
@Composable
internal fun HomeEntryPlaceholder(modifier: Modifier = Modifier, showNavigation: Boolean = true) {
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal), bottomBar = {
        if (showNavigation) FloatingNavigationBar(navigationDestinations(false), Screen.Home.route, {}, enabled = false)
    }) { padding ->
        HomeContent(null, {}, {}, {}, {}, {}, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/** Static slots avoid a per-value animation while retaining an accessible loading description. */
@Composable
internal fun HomeValuePlaceholder(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .clearAndSetSemantics { contentDescription = loading })
}

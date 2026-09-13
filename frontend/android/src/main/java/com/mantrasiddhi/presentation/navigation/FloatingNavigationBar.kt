package com.mantrasiddhi.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.mantrasiddhi.presentation.theme.EmphasizedEasing
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mantrasiddhi.R

internal data class NavigationDestination(val screen: Screen, val icon: Int)

internal fun navigationDestinations(showFamily: Boolean) = buildList {
    add(NavigationDestination(Screen.Home, R.drawable.nav_home))
    add(NavigationDestination(Screen.Practice, R.drawable.nav_practice))
    add(NavigationDestination(Screen.Recitation, R.drawable.nav_japa))
    add(NavigationDestination(Screen.Progress, R.drawable.nav_progress))
    if (showFamily) add(NavigationDestination(Screen.Family, R.drawable.nav_children))
    add(NavigationDestination(Screen.Settings, R.drawable.nav_settings))
}

@Composable
fun MantraBottomBar(navController: NavHostController, showFamily: Boolean) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val destinations = remember(showFamily) { navigationDestinations(showFamily) }
    FloatingNavigationBar(destinations, route, onNavigate = { screen ->
        if (route != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    })
}

/** A shared selection pill follows the active tab while native tab semantics stay immediate. */
@Composable
internal fun FloatingNavigationBar(
    destinations: List<NavigationDestination>, currentRoute: String?, onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val showLabels = maxWidth >= 336.dp && LocalDensity.current.fontScale <= 1.2f
        val itemHeight = if (showLabels) 64.dp else 56.dp
        val slotWidth = (maxWidth - 8.dp).coerceAtLeast(0.dp) / destinations.size
        Surface(
            modifier = Modifier.fillMaxWidth().shadow(12.dp, CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f)),
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            // The outer constraint scope already knows the available width. A second
            // subcomposition here used to rebuild the bar during its measurement.
            Box(Modifier.padding(4.dp)) {
                val selectedIndex = destinations.indexOfFirst { it.screen.route == currentRoute }
                val indicatorOffset = animateDpAsState(
                    targetValue = slotWidth * selectedIndex.coerceAtLeast(0),
                    animationSpec = tween(320, easing = EmphasizedEasing),
                    label = "navigationIndicatorOffset"
                )
                if (selectedIndex >= 0) {
                    Box(Modifier.offset { IntOffset(indicatorOffset.value.roundToPx(), 0) }
                        .width(slotWidth).height(itemHeight).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer))
                }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    destinations.forEach { item ->
                        NavigationTab(item.screen, item.icon, item.screen.route == currentRoute,
                            enabled, showLabels, itemHeight, onNavigate, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Color ticks invalidate only this tab, not the constraint scope and every sibling. */
@Composable
private fun NavigationTab(screen: Screen, icon: Int, selected: Boolean, enabled: Boolean,
    showLabel: Boolean, height: Dp, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(screen.labelResId)
    val ink by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(140), label = "navigationInk")
    val color = if (enabled) ink else ink.copy(alpha = 0.38f)
    Column(modifier.height(height).clip(CircleShape)
        .testTag("${if (enabled) "perf" else "entry"}_tab_${screen.route}")
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onNavigate(screen) })
        .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = color)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(label, Modifier.clearAndSetSemantics {}, color = color,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

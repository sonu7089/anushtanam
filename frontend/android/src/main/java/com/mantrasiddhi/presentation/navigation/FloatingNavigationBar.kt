package com.mantrasiddhi.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mantrasiddhi.R

internal data class NavigationDestination(val screen: Screen, val icon: Int)

internal fun navigationDestinations(isParent: Boolean) = buildList {
    add(NavigationDestination(Screen.Home, R.drawable.nav_home))
    add(NavigationDestination(Screen.Practice, R.drawable.nav_practice))
    add(NavigationDestination(Screen.Recitation, R.drawable.nav_japa))
    add(NavigationDestination(Screen.Progress, R.drawable.nav_progress))
    if (isParent) add(NavigationDestination(Screen.ChildProgress, R.drawable.nav_children))
    add(NavigationDestination(Screen.Settings, R.drawable.nav_settings))
}

@Composable
fun MantraBottomBar(navController: NavHostController, isParent: Boolean) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val destinations = remember(isParent) { navigationDestinations(isParent) }
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

/** A floating tab bar with shared selection motion and accessible native tab semantics. */
@Composable
internal fun FloatingNavigationBar(
    destinations: List<NavigationDestination>, currentRoute: String?, onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val showLabels = maxWidth >= 336.dp && LocalDensity.current.fontScale <= 1.2f
        val itemHeight = if (showLabels) 64.dp else 56.dp
        val itemWidth = (maxWidth - 8.dp) / destinations.size
        val selectedIndex = destinations.indexOfFirst { it.screen.route == currentRoute }
        val selectionOffset by animateDpAsState(itemWidth * selectedIndex.coerceAtLeast(0),
            spring(dampingRatio = 0.85f, stiffness = 480f), label = "navigationSelection")
        Surface(
            modifier = Modifier.fillMaxWidth().shadow(12.dp, CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f)),
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            Box(Modifier.padding(4.dp)) {
                if (selectedIndex >= 0) {
                    Surface(Modifier.offset(x = selectionOffset).width(itemWidth).height(itemHeight),
                        shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {}
                }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    destinations.forEach { item ->
                        val selected = item.screen.route == currentRoute
                        val label = stringResource(item.screen.labelResId)
                        val ink by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "navigationInk")
                        Column(Modifier.weight(1f).height(itemHeight).clip(CircleShape)
                            .selectable(selected, role = Role.Tab, onClick = { onNavigate(item.screen) })
                            .semantics { contentDescription = label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Icon(painterResource(item.icon), null, Modifier.size(24.dp), tint = ink)
                            if (showLabels) {
                                Spacer(Modifier.height(4.dp))
                                Text(label, Modifier.clearAndSetSemantics {}, color = ink,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

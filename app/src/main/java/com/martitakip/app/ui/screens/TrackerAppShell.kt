package com.martitakip.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.ui.MapUiState
import com.martitakip.app.ui.TrackerUiState

/** The two top-level destinations. */
enum class TrackerDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TRACKER("Sayaç", Icons.Rounded.Speed, Icons.Outlined.Speed),
    MAP("Harita", Icons.Rounded.Map, Icons.Outlined.Map),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): TrackerDestination =
            entries.getOrElse(ordinal) { TRACKER }
    }
}

/**
 * App shell: bottom navigation between the tracker and the heat map.
 *
 * [AnimatedContent] is used instead of a `NavHost` on purpose - there are only
 * two destinations and no deep links, so a full navigation library would add
 * startup cost without any benefit.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackerAppShell(
    destination: TrackerDestination,
    onDestinationChange: (TrackerDestination) -> Unit,
    trackerState: TrackerUiState,
    mapState: MapUiState,
    mapRangeDays: Int,
    onMapRangeChange: (Int) -> Unit,
    onIncrement: (VehicleType) -> Unit,
    onDecrement: (VehicleType) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onShare: () -> Unit,
    onShareMapSummary: () -> Unit,
    onArchiveShift: () -> Unit,
    onClearToday: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The inner tracker screen owns its own top app bar and insets; letting
        // this shell consume them too would double-pad the content.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                TrackerDestination.entries.forEach { item ->
                    val selected = item == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onDestinationChange(item) },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith
                    fadeOut(animationSpec = tween(140))
            },
            label = "destination",
        ) { target ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (target) {
                    TrackerDestination.TRACKER -> MainTrackerScreen(
                        state = trackerState,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory,
                        onShare = onShare,
                        onArchiveShift = onArchiveShift,
                        onClearToday = onClearToday,
                        onRequestPermission = onRequestPermission,
                    )

                    TrackerDestination.MAP -> OsmMapScreen(
                        state = mapState,
                        rangeDays = mapRangeDays,
                        onRangeChange = onMapRangeChange,
                        onShareSummary = onShareMapSummary,
                    )
                }
            }
        }
    }
}

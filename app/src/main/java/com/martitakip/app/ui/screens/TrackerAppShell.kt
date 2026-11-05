package com.martitakip.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.ui.MapUiState
import com.martitakip.app.ui.TrackerUiState
import com.martitakip.app.ui.theme.MartiTakipTheme
import java.time.LocalDate

/**
 * Widest the tracker content is allowed to grow.
 *
 * Beyond this a single-column list of cards stops reading as a list: the eye has
 * to travel too far between a counter and the figure it belongs to. The map
 * screen is deliberately exempt - a map is meant to be as large as the window.
 */
private val TRACKER_CONTENT_MAX_WIDTH = 760.dp

/**
 * Marks the tracker content region so a test can measure it.
 *
 * A screenshot frame cannot answer what this tag exists for: content sliced by
 * the navigation component's top edge renders identically whether the suite
 * reserved that space or drew the component over the content, because the
 * component is opaque either way. `ShellScreenshotTest` reads these bounds
 * instead of the pixels.
 */
const val TRACKER_CONTENT_TEST_TAG = "tracker-content"

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
 * App shell and top-level navigation.
 *
 * [NavigationSuiteScaffold] replaces the hand-rolled bottom bar: it reads the
 * window size class and picks the navigation component that fits - a bottom bar
 * while the window is compact, a rail once it is medium or expanded (tablet,
 * unfolded foldable, free-form desktop window), a drawer beyond that. The same
 * two [TrackerDestination]s are rendered either way, so nothing else in the app
 * has to know which one is on screen.
 *
 * [AnimatedContent] is used instead of a `NavHost` on purpose - there are only
 * two destinations and no deep links, so a full navigation library would add
 * startup cost without any benefit. The transition comes from
 * `MaterialTheme.motionScheme`, so it follows the same motion tokens as the
 * Material components around it instead of a locally invented duration.
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
    val motionScheme = MaterialTheme.motionScheme

    NavigationSuiteScaffold(
        // No `navigationSuiteColors` override: the Material defaults already
        // resolve the selected indicator to `secondaryContainer`, which is the
        // scooter green this app was built around.
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            TrackerDestination.entries.forEach { item ->
                val selected = item == destination
                item(
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
                )
            }
        },
    ) {
        // The content lambda takes no arguments in this version of the suite:
        // its layout reserves the space the navigation component occupies and
        // owns the insets that go with it, so there is no `PaddingValues` for
        // the shell to spend here. Adding padding of our own would either
        // double a gap the layout already left or swallow a system-bar inset
        // that the screens below still need.
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                // Spatial for the screen arriving (it changes bounds, so it gets
                // the spring), a cheaper effects spec for the one leaving.
                fadeIn(animationSpec = motionScheme.defaultSpatialSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            },
            label = "destination",
        ) { target ->
            when (target) {
                TrackerDestination.TRACKER -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TRACKER_CONTENT_TEST_TAG),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    MainTrackerScreen(
                        state = trackerState,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory,
                        onShare = onShare,
                        onArchiveShift = onArchiveShift,
                        onClearToday = onClearToday,
                        onRequestPermission = onRequestPermission,
                        // widthIn first so fillMaxSize fills the *clamped*
                        // width; the other order lets fillMaxSize win and the
                        // cap silently does nothing.
                        modifier = Modifier
                            .widthIn(max = TRACKER_CONTENT_MAX_WIDTH)
                            .fillMaxSize(),
                    )
                }

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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Sayaç", showBackground = true, heightDp = 900)
@Composable
private fun TrackerAppShellPreview() {
    MartiTakipTheme {
        TrackerAppShell(
            destination = TrackerDestination.TRACKER,
            onDestinationChange = {},
            trackerState = TrackerUiState(
                isLoading = false,
                dayKey = LocalDate.now().toString(),
                scooterCount = 12,
                mopedCount = 7,
                scooterEarnings = 180.0,
                mopedEarnings = 175.0,
                hasLocationPermission = true,
            ),
            mapState = MapUiState(isLoading = false),
            mapRangeDays = 7,
            onMapRangeChange = {},
            onIncrement = {},
            onDecrement = {},
            onOpenSettings = {},
            onOpenHistory = {},
            onShare = {},
            onShareMapSummary = {},
            onArchiveShift = {},
            onClearToday = {},
            onRequestPermission = {},
        )
    }
}

/**
 * The map screen on a wide window.
 *
 * The `AndroidView` inside is not rendered in a preview, so what this shows is
 * the floating chrome - summary card, legend, recenter FAB - which is exactly
 * the part that has to stay clear of the navigation rail.
 */
@Preview(name = "Harita · geniş", showBackground = true, widthDp = 900, heightDp = 700)
@Composable
private fun MapDestinationPreview() {
    MartiTakipTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            OsmMapScreen(
                state = MapUiState(
                    isLoading = false,
                    lifetimeScooter = 128,
                    lifetimeMoped = 64,
                    lifetimeEarnings = 3_520.0,
                    shiftCount = 9,
                ),
                rangeDays = 30,
                onRangeChange = {},
                onShareSummary = {},
            )
        }
    }
}

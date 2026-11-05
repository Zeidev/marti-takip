package com.martitakip.app.ui.screens

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.martitakip.app.ui.MapUiState
import com.martitakip.app.ui.TrackerUiState
import com.martitakip.app.ui.theme.MartiTakipTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders whole screens to PNGs through the adaptive shell.
 *
 * This is the file that answers what no compiler check can: the navigation
 * component's space and the window insets. Content hidden behind the bottom bar,
 * a doubled status-bar gap, or a rail that did not appear at tablet width all
 * show up in these images - in CI, without a device.
 *
 * The window size comes from the `qualifiers` on each test, which is what makes
 * `LocalConfiguration.screenWidthDp` and the window size class report a phone
 * (compact) or a tablet (expanded) layout.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Phone: bottom bar, one column of counters. */
    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun `tracker shell on a phone`() {
        capture("shell-phone") {
            TrackerAppShell(
                destination = TrackerDestination.TRACKER,
                onDestinationChange = {},
                trackerState = trackerState(),
                mapState = mapState(),
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

    /** Tablet: the suite swaps the bottom bar for a rail, counters side by side. */
    @Test
    @Config(qualifiers = "w1000dp-h800dp-land-xhdpi")
    fun `tracker shell on a tablet`() {
        capture("shell-tablet") {
            TrackerAppShell(
                destination = TrackerDestination.TRACKER,
                onDestinationChange = {},
                trackerState = trackerState(),
                mapState = mapState(),
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

    /** The tracker screen alone, without the shell around it. */
    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun `tracker screen on a phone`() {
        capture("tracker-screen-phone") {
            MainTrackerScreen(
                state = trackerState(),
                onIncrement = {},
                onDecrement = {},
                onOpenSettings = {},
                onOpenHistory = {},
                onShare = {},
                onArchiveShift = {},
                onClearToday = {},
                onRequestPermission = {},
            )
        }
    }

    private fun trackerState(): TrackerUiState = TrackerUiState(
        isLoading = false,
        dayKey = LocalDate.now().toString(),
        scooterCount = 12,
        mopedCount = 7,
        scooterEarnings = 180.0,
        mopedEarnings = 175.0,
        dailyGoal = 25,
        hasLocationPermission = true,
    )

    private fun mapState(): MapUiState = MapUiState(
        isLoading = false,
        lifetimeScooter = 128,
        lifetimeMoped = 64,
        lifetimeEarnings = 3_520.0,
        shiftCount = 9,
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MartiTakipTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

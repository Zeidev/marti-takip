package com.martitakip.app.ui.screens

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.martitakip.app.ui.MapUiState
import com.martitakip.app.ui.TrackerUiState
import com.martitakip.app.ui.captureAndVerify
import com.martitakip.app.ui.theme.MartiTakipTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders whole screens to PNGs through the adaptive shell, and measures the
 * geometry the PNGs cannot state.
 *
 * This is the file that answers what no compiler check can: the navigation
 * component's space and the window insets. Content hidden behind the bottom bar,
 * a doubled status-bar gap, or a rail that did not appear at tablet width all
 * show up in these images - in CI, without a device.
 *
 * The window size comes from the `qualifiers` on each test, which is what makes
 * `LocalConfiguration.screenWidthDp` and the window size class report a phone
 * (compact) or a tablet (expanded) layout.
 *
 * Each frame is also compared with the committed reference of the same name
 * (`src/test/screenshots/`), so an inset or layout change that nobody meant to
 * make turns this suite red. See `ScreenshotReference.kt`.
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
        capture("shell-phone") { TrackerShell() }
    }

    /** Tablet: the suite swaps the bottom bar for a rail, counters side by side. */
    @Test
    @Config(qualifiers = "w1000dp-h800dp-land-xhdpi")
    fun `tracker shell on a tablet`() {
        capture("shell-tablet") { TrackerShell() }
    }

    /**
     * The tracker screen alone, without the shell around it.
     *
     * Worth having next to `shell-phone`: the two differ only by the shell, so
     * anything that shifts between them is the shell's doing and not the
     * screen's.
     */
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

    /**
     * The frame above shows *where* the content stops; it cannot show *why*. A
     * card sliced by the bar's top edge renders identically whether the suite
     * reserved that space for the bar or drew the bar over the content, because
     * an opaque bar hides the difference either way.
     *
     * So this reads the geometry instead of the pixels. The content region must
     * end at or above the navigation component - that is the invariant the
     * frames were added to watch, and a frame alone can never fail on it when
     * the bar is what is doing the hiding.
     */
    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun `tracker content clears the bottom bar on a phone`() {
        renderShell()

        val content = compose.onNodeWithTag(TRACKER_CONTENT_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val barItem = compose.onNodeWithText(TrackerDestination.MAP.label)
            .fetchSemanticsNode().boundsInRoot
        println("[insets] phone: content=$content, bottom bar item=$barItem")

        assertTrue(
            "The tracker content region reaches y=${content.bottom} but the bottom bar starts at " +
                "y=${barItem.top}: content is behind the navigation component.",
            content.bottom <= barItem.top,
        )
    }

    /** The same invariant on the other window class, where the suite draws a rail instead. */
    @Test
    @Config(qualifiers = "w1000dp-h800dp-land-xhdpi")
    fun `tracker content clears the navigation rail on a tablet`() {
        renderShell()

        val content = compose.onNodeWithTag(TRACKER_CONTENT_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val railItem = compose.onNodeWithText(TrackerDestination.MAP.label)
            .fetchSemanticsNode().boundsInRoot
        println("[insets] tablet: content=$content, rail item=$railItem")

        assertTrue(
            "The tracker content region starts at x=${content.left} but the rail reaches " +
                "x=${railItem.right}: content is behind the navigation component.",
            content.left >= railItem.right,
        )
    }

    private fun renderShell() {
        setContent { TrackerShell() }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        setContent(content)
        captureAndVerify(compose.onRoot(), name)
    }

    /** One wrapper for both the frames and the geometry tests, so they see the same tree. */
    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            MartiTakipTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }
}

/** The shell as the app mounts it, shared by the frames and the geometry tests. */
@Composable
private fun TrackerShell() {
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

package com.martitakip.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.martitakip.app.ui.theme.MartiTakipTheme
import com.martitakip.app.ui.theme.MartiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the counter components to PNGs on the JVM, at the size and density of
 * the phone the operator carries.
 *
 * These exist because nothing else in the suite can see this: what matters about
 * a counter card is visual (does the giant "+1" still read as the primary action,
 * is the goal bar legible next to the accent), and that is exactly what regresses
 * silently. The images land in `build/outputs/roborazzi/`, which the CI job
 * uploads as an artifact - no emulator, no device.
 *
 * `@Config` pins the SDK because Robolectric renders against a downloaded
 * runtime, and an API level it has no runtime for fails the whole class.
 * `application` keeps `MartiTakipApplication` out of the way: it warms up Room,
 * which none of these compositions touch.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class ComponentScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `scooter counter with swaps`() {
        capture("counter-scooter") {
            CounterActionCard(
                title = "Scooter",
                subtitle = "Değişim başına 15,00 ₺",
                count = 12,
                earnings = 180.0,
                unitPrice = 15.0,
                accent = MartiTheme.vehicle.scooter,
                containerColor = MaterialTheme.colorScheme.surface,
                onContainerColor = MaterialTheme.colorScheme.onSurface,
                icon = Icons.Rounded.PedalBike,
                onIncrement = {},
                onDecrement = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    @Test
    fun `moped counter before the first swap`() {
        capture("counter-moped-empty") {
            CounterActionCard(
                title = "Moped",
                subtitle = "Değişim başına 20,00 ₺",
                count = 0,
                earnings = 0.0,
                unitPrice = 20.0,
                accent = MartiTheme.vehicle.moped,
                containerColor = MaterialTheme.colorScheme.surface,
                onContainerColor = MaterialTheme.colorScheme.onSurface,
                icon = Icons.Rounded.TwoWheeler,
                onIncrement = {},
                onDecrement = {},
                // The "-1" control must look disabled at zero, so the empty state
                // is worth a frame of its own.
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    @Test
    fun `goal card when the target is reached`() {
        capture("goal-reached") {
            GoalProgressCard(
                current = 25,
                goal = 25,
                progress = 1f,
                reached = true,
                remaining = 0,
                projectedEarnings = 375.0,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MartiTakipTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        // Explicit path: Roborazzi derives a default name from the test method,
        // and these names are what the artifact download shows.
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

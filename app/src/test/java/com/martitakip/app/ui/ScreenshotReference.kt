package com.martitakip.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Render a Compose node, write it out, and compare it with the committed
 * reference image.
 *
 * Why this is ~90 lines of our own code instead of Roborazzi's `verify` mode:
 *
 *  - The comparison has to be readable. "How much is allowed to differ" is a
 *    judgement about this app's UI, and it belongs in the test source, not in a
 *    system property that is invisible from the repository.
 *  - A missing reference must never fail a build. Roborazzi's verify mode fails
 *    on a golden it cannot find, which makes the harness break the very build it
 *    exists to protect - and the first reference can only come from a run of
 *    this very suite.
 *  - Nothing here depends on a Roborazzi Gradle plugin, on a directory default
 *    or on a property name. `ImageIO` is JDK-only and resolved by the unit-test
 *    runtime, so the rules below are exactly the rules that run.
 *
 * Recording instead of comparing: set `MT_RECORD_SCREENSHOTS=1`. That is what
 * the CI "Record UI references" job does, and it is the only path that writes
 * into `src/test/screenshots/` - an ordinary test run never touches it.
 */

/** Per-channel difference (0..255) that still counts as the same pixel. */
private const val CHANNEL_TOLERANCE = 8

/** Share of pixels allowed to differ before the frame counts as changed. */
private const val MAX_DIFFERENT_PIXEL_RATIO = 0.001

/** Set to `1` to write references instead of comparing against them. */
private const val RECORD_ENV = "MT_RECORD_SCREENSHOTS"

/** Where a run writes what it rendered; the CI uploads this folder. */
private val recordedDir: File get() = File("build/outputs/roborazzi").absoluteFile

/** Where the committed references live, relative to the Gradle module. */
private val referenceDir: File
    get() = listOf("src/test/screenshots", "app/src/test/screenshots")
        .map(::File)
        .firstOrNull { it.isDirectory }
        ?: File("src/test/screenshots")

/**
 * Renders [node] to `build/outputs/roborazzi/<name>.png`, then checks it against
 * `src/test/screenshots/<name>.png` when that reference exists.
 */
fun captureAndVerify(node: SemanticsNodeInteraction, name: String) {
    val recorded = File(recordedDir, "$name.png")
    recorded.parentFile?.mkdirs()

    node.captureRoboImage(recorded.absolutePath)

    // Roborazzi only documents writing frames alongside its Gradle plugin, and
    // this project deliberately does not apply it. A frame that quietly was not
    // written is the worst failure this file can have - the run stays green with
    // nothing to look at - so the write is checked, and Compose's own capture
    // stands in when Roborazzi produced nothing.
    if (!recorded.isFile) {
        val frame = node.captureToImage().asAndroidBitmap()
        recorded.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("[screenshot] $name written by Compose (Roborazzi wrote nothing) -> ${recorded.absolutePath}")
    }
    check(recorded.isFile) { "Screenshot $name was not written to ${recorded.absolutePath}." }

    if (System.getenv(RECORD_ENV) == "1") {
        println("[screenshot] recorded $name -> ${recorded.absolutePath}")
        return
    }

    val reference = File(referenceDir, "$name.png")
    if (!reference.isFile) {
        // Not a failure: the first run of a new frame has nothing to compare to.
        println("[screenshot] no reference for $name; rendered ${recorded.absolutePath}")
        return
    }
    compare(name, reference, recorded)
}

private fun compare(name: String, reference: File, recorded: File) {
    val expected = ImageIO.read(reference)
    val actual = ImageIO.read(recorded)
    if (expected == null || actual == null) {
        throw AssertionError(
            "Screenshot $name could not be decoded (reference=$reference, rendered=$recorded).",
        )
    }

    val sizeChanged = expected.width != actual.width || expected.height != actual.height
    if (sizeChanged) {
        throw AssertionError(
            "Screenshot $name changed size: reference is " +
                "${expected.width}x${expected.height}, this run rendered " +
                "${actual.width}x${actual.height}. Look at ${recorded.absolutePath}; if the new " +
                "size is intended, re-record the references (see README > Testler).",
        )
    }

    var different = 0
    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            if (differs(expected.getRGB(x, y), actual.getRGB(x, y))) different++
        }
    }

    val total = expected.width * expected.height
    val ratio = different.toDouble() / total
    if (ratio > MAX_DIFFERENT_PIXEL_RATIO) {
        val percent = String.format(Locale.US, "%.2f", ratio * 100)
        throw AssertionError(
            "Screenshot $name differs from its reference: $different of $total pixels ($percent%) " +
                "beyond a tolerance of $CHANNEL_TOLERANCE per channel. This run rendered " +
                "${recorded.absolutePath}. If the change is intended, re-record the references " +
                "(see README > Testler); if it is not, the layout regressed.",
        )
    }

    println("[screenshot] $name matches its reference ($different of $total pixels differ within tolerance)")
}

/** True when any colour channel of the two ARGB pixels is further apart than [CHANNEL_TOLERANCE]. */
private fun differs(expected: Int, actual: Int): Boolean {
    for (shift in intArrayOf(24, 16, 8, 0)) {
        val expectedChannel = (expected shr shift) and 0xFF
        val actualChannel = (actual shr shift) and 0xFF
        if (kotlin.math.abs(expectedChannel - actualChannel) > CHANNEL_TOLERANCE) return true
    }
    return false
}

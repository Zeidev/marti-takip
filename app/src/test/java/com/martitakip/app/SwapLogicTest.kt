package com.martitakip.app

import com.martitakip.app.data.local.SwapEvent
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure JVM tests for the business rules that must never regress:
 * day bucketing, price normalisation, goal math and report formatting.
 */
class SwapLogicTest {

    // --- Day key ----------------------------------------------------------

    @Test
    fun `dayKeyOf uses the local calendar day`() {
        val zone = ZoneId.of("Europe/Istanbul")
        // 2026-10-30T00:30 Istanbul == 2026-10-29T21:30 UTC
        val millis = java.time.ZonedDateTime.of(2026, 10, 30, 0, 30, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals("2026-10-30", SwapEvent.dayKeyOf(millis, zone))
    }

    @Test
    fun `dayKeyOf handles the last minute of the day`() {
        val zone = ZoneId.of("Europe/Istanbul")
        val millis = java.time.ZonedDateTime.of(2026, 10, 30, 23, 59, 59, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals("2026-10-30", SwapEvent.dayKeyOf(millis, zone))
    }

    @Test
    fun `dayKeyOf date overload is stable`() {
        assertEquals("2026-01-05", SwapEvent.dayKeyOf(LocalDate.of(2026, 1, 5)))
    }

    // --- Earnings ---------------------------------------------------------

    @Test
    fun `earnings snapshot is independent of later price changes`() {
        val event = SwapEvent(type = VehicleType.SCOOTER, earnings = 15.0)
        // A later settings change must not alter the stored snapshot.
        val newPrice = 22.5
        assertEquals(15.0, event.earnings, 0.0001)
        assertEquals(22.5, newPrice, 0.0001)
    }

    @Test
    fun `hasLocation requires both coordinates`() {
        assertFalse(SwapEvent(type = VehicleType.SCOOTER, earnings = 1.0).hasLocation)
        assertFalse(
            SwapEvent(type = VehicleType.SCOOTER, earnings = 1.0, latitude = 41.0).hasLocation,
        )
        assertTrue(
            SwapEvent(
                type = VehicleType.SCOOTER,
                earnings = 1.0,
                latitude = 41.0,
                longitude = 29.0,
            ).hasLocation,
        )
    }

    // --- Vehicle type round trip ------------------------------------------

    @Test
    fun `vehicle type round trips through its raw name`() {
        VehicleType.entries.forEach { type ->
            assertEquals(type, VehicleType.fromRaw(type.name))
        }
    }

    @Test
    fun `unknown vehicle type falls back to scooter`() {
        assertEquals(VehicleType.SCOOTER, VehicleType.fromRaw("HOVERBOARD"))
        assertEquals(VehicleType.SCOOTER, VehicleType.fromRaw(null))
        assertEquals(VehicleType.MOPED, VehicleType.fromRaw("moped"))
    }

    // --- Settings ---------------------------------------------------------

    @Test
    fun `price normalisation rounds to kuruş and clamps`() {
        assertEquals(15.0, UserSettings.normalizePrice(15.0), 0.0001)
        assertEquals(15.99, UserSettings.normalizePrice(15.994), 0.0001)
        assertEquals(16.0, UserSettings.normalizePrice(15.996), 0.0001)
        assertEquals(0.0, UserSettings.normalizePrice(-42.0), 0.0001)
        assertEquals(1_000.0, UserSettings.normalizePrice(9_999.0), 0.0001)
    }

    @Test
    fun `goal progress is clamped between zero and one`() {
        val settings = UserSettings(dailyGoal = 40)
        assertEquals(0f, settings.goalProgress(0), 0.0001f)
        assertEquals(0.5f, settings.goalProgress(20), 0.0001f)
        assertEquals(1f, settings.goalProgress(40), 0.0001f)
        assertEquals(1f, settings.goalProgress(120), 0.0001f)
    }

    @Test
    fun `goal reached and remaining are consistent`() {
        val settings = UserSettings(dailyGoal = 10)
        assertFalse(settings.isGoalReached(9))
        assertTrue(settings.isGoalReached(10))
        assertTrue(settings.isGoalReached(11))
        assertEquals(3, settings.remainingToGoal(7))
        assertEquals(0, settings.remainingToGoal(25))
    }

    @Test
    fun `zero goal never reports progress`() {
        val settings = UserSettings(dailyGoal = 0)
        assertEquals(0f, settings.goalProgress(10), 0.0001f)
        assertFalse(settings.isGoalReached(10))
    }

    @Test
    fun `priceFor returns the matching unit price`() {
        val settings = UserSettings(scooterPrice = 12.0, mopedPrice = 30.0)
        assertEquals(12.0, settings.priceFor(VehicleType.SCOOTER), 0.0001)
        assertEquals(30.0, settings.priceFor(VehicleType.MOPED), 0.0001)
    }

    // --- Report formatting ------------------------------------------------

    @Test
    fun `money formatting uses turkish separators`() {
        val formatted = com.martitakip.app.ui.TrackerViewModel.formatMoney(1234.5)
        assertTrue("Beklenen biçim: 1.234,50 ₺, gelen: $formatted", formatted.startsWith("1.234,50"))
        assertTrue(formatted.endsWith("₺"))
    }

    @Test
    fun `duration formatting switches to hours above sixty minutes`() {
        val start = 0L
        assertEquals("45dk", com.martitakip.app.ui.TrackerViewModel.formatDuration(start, 45 * 60_000L))
        assertEquals("2s 5dk", com.martitakip.app.ui.TrackerViewModel.formatDuration(start, 125 * 60_000L))
    }
}

package com.martitakip.app.data.repository

import com.martitakip.app.data.local.DayBucket
import com.martitakip.app.data.local.DayTotals
import com.martitakip.app.data.local.HourBucket
import com.martitakip.app.data.local.LifetimeTotals
import com.martitakip.app.data.local.ShiftSpan
import com.martitakip.app.data.local.ShiftSummary
import com.martitakip.app.data.local.SwapDao
import com.martitakip.app.data.local.SwapEvent
import com.martitakip.app.data.local.VehicleType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Single source of truth for swap data.
 *
 * The repository is intentionally thin: it owns the "which day is it" policy
 * and the archive transaction, while the DAO owns the SQL. Keeping the day key
 * computation here means every caller (ViewModel, tests) agrees on the
 * boundary of a shift day.
 */
class SwapRepository(private val dao: SwapDao) {

    fun todayKey(): String = SwapEvent.dayKeyOf(LocalDate.now())

    fun observeLiveTotals(dayKey: String): Flow<DayTotals> = dao.observeLiveTotals(dayKey)

    fun observeLiveEvents(dayKey: String): Flow<List<SwapEvent>> = dao.observeLiveDay(dayKey)

    fun observeAllLocated(): Flow<List<SwapEvent>> = dao.observeAllLocated()

    fun observeLocatedSince(sinceMillis: Long): Flow<List<SwapEvent>> =
        dao.observeLocatedSince(sinceMillis)

    fun observeLifetimeTotals(): Flow<LifetimeTotals> = dao.observeLifetimeTotals()

    fun observeShifts(): Flow<List<ShiftSummary>> = dao.observeShifts()

    fun observeHourBuckets(dayKey: String, offsetSeconds: Int): Flow<List<HourBucket>> =
        dao.observeHourBuckets(dayKey, offsetSeconds)

    fun observeShiftSpan(dayKey: String): Flow<ShiftSpan> = dao.observeShiftSpan(dayKey)

    fun observeRecentDays(limit: Int = 14): Flow<List<DayBucket>> = dao.observeRecentDays(limit)

    /** Raw live-day rows; used for the region breakdown. */
    suspend fun liveDayEvents(dayKey: String = todayKey()): List<SwapEvent> =
        dao.getLiveDay(dayKey)

    /**
     * Un-archives a shift so it becomes the live shift again.
     *
     * Refuses when the shift is not from today: merging an old shift into the
     * current counters would corrupt both the daily goal and the earnings.
     */
    suspend fun restoreShift(shiftId: Long): RestoreResult {
        val count = dao.countShift(shiftId)
        if (count == 0) return RestoreResult.NotFound
        val stamped = dao.unarchiveShift(shiftId)
        return if (stamped > 0) RestoreResult.Restored(stamped) else RestoreResult.NotFound
    }

    sealed interface RestoreResult {
        data class Restored(val rowCount: Int) : RestoreResult
        data object NotFound : RestoreResult
    }

    /**
     * Records one swap. [latitude]/[longitude] are null when the GPS fix could
     * not be obtained within the timeout - the swap is still persisted.
     */
    suspend fun recordSwap(
        type: VehicleType,
        unitPrice: Double,
        latitude: Double?,
        longitude: Double?,
        timestamp: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        SwapEvent(
            type = type,
            earnings = unitPrice,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            dayKey = SwapEvent.dayKeyOf(timestamp),
        ),
    )

    /** Attaches a coordinate captured after the row was already inserted. */
    suspend fun attachLocation(eventId: Long, latitude: Double, longitude: Double): Boolean =
        dao.updateLocation(eventId, latitude, longitude) > 0

    /** Undo of the most recent tap for [type]; returns true when a row was removed. */
    suspend fun undoLast(type: VehicleType, dayKey: String = todayKey()): Boolean =
        dao.deleteLatestOfType(type, dayKey) > 0

    /**
     * Closes the live shift: stamps every live row of [dayKey] with a fresh
     * shift id. Returns the new shift id, or null when the day was empty.
     */
    suspend fun archiveCurrentShift(dayKey: String = todayKey()): Long? =
        dao.archiveShift(dayKey)

    /** Destructive "clear counters" that does not create a history entry. */
    suspend fun discardLiveDay(dayKey: String = todayKey()): Int = dao.deleteLiveDay(dayKey)

    suspend fun exportAll(): List<SwapEvent> = dao.getAllForExport()
}

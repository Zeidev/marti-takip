package com.martitakip.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated per-vehicle numbers for the live shift, computed by SQLite.
 *
 * The earnings are summed from the *stored* per-row price snapshots, never
 * recomputed from the current settings, so changing a unit price mid-shift
 * cannot rewrite what was already earned.
 */
data class DayTotals(
    val scooterCount: Int,
    val mopedCount: Int,
    val scooterEarnings: Double,
    val mopedEarnings: Double,
) {
    val totalCount: Int get() = scooterCount + mopedCount
    val totalEarnings: Double get() = scooterEarnings + mopedEarnings
}

/** Aggregated lifetime numbers used by the map screen header. */
data class LifetimeTotals(
    val scooterCount: Int,
    val mopedCount: Int,
    val totalEarnings: Double,
    val locatedCount: Int,
    val shiftCount: Int,
) {
    val totalCount: Int get() = scooterCount + mopedCount
}

/** Swap count for one hour of the day (0..23), used by the pace card. */
data class HourBucket(
    val hour: Int,
    val scooterCount: Int,
    val mopedCount: Int,
) {
    val totalCount: Int get() = scooterCount + mopedCount
}

/** One day of the recent trend chart. */
data class DayBucket(
    val dayKey: String,
    val scooterCount: Int,
    val mopedCount: Int,
    val earnings: Double,
) {
    val totalCount: Int get() = scooterCount + mopedCount
}

/** First and last swap of a live shift, used to compute the active duration. */
data class ShiftSpan(
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val swapCount: Int,
)

/** One archived shift, ready to be rendered in the history sheet. */
data class ShiftSummary(
    val shiftId: Long,
    /**
     * The day the shift was archived on (`yyyy-MM-dd`).
     *
     * Every row of a shift carries the same key because `stampDay` only ever
     * stamps one day, which is what makes this safe to read with `MIN()`.
     * The history sheet uses it to decide whether the shift can still be
     * restored - see [unarchiveShift].
     */
    val dayKey: String,
    val startedAt: Long,
    val endedAt: Long,
    val scooterCount: Int,
    val mopedCount: Int,
    val totalEarnings: Double,
    val eventCount: Int,
)

/**
 * Data access for the swap archive.
 *
 * Every read is exposed as a [Flow] so the UI recomposes automatically when a
 * row is inserted, undone or archived - there is no manual refresh anywhere.
 *
 * Terminology used below:
 *  - "live" rows are the current shift (`shiftId IS NULL`) and drive the counters,
 *  - "archived" rows are stamped with a shift id and only drive history/map.
 */
@Dao
interface SwapDao {

    // ---------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: SwapEvent): Long

    /** Undo the most recent live tap of [type] for [dayKey]. Returns rows removed. */
    @Query(
        """
        DELETE FROM swap_events
        WHERE id = (
            SELECT id FROM swap_events
            WHERE type = :type
              AND dayKey = :dayKey
              AND shiftId IS NULL
            ORDER BY timestamp DESC, id DESC
            LIMIT 1
        )
        """,
    )
    suspend fun deleteLatestOfType(type: VehicleType, dayKey: String): Int

    /**
     * Attaches a coordinate to an already-inserted swap.
     *
     * The tracker inserts the row *first* (so the counter is instant) and then
     * patches the fix in when the one-shot GPS capture completes. If the user
     * already undid the swap the row is gone and this affects 0 rows, which is
     * exactly the desired behaviour.
     */
    @Query(
        """
        UPDATE swap_events
        SET latitude = :latitude, longitude = :longitude
        WHERE id = :eventId
        """,
    )
    suspend fun updateLocation(eventId: Long, latitude: Double, longitude: Double): Int

    @Delete
    suspend fun delete(event: SwapEvent)

    /** Drops the live rows of a day without archiving them (destructive reset). */
    @Query("DELETE FROM swap_events WHERE dayKey = :dayKey AND shiftId IS NULL")
    suspend fun deleteLiveDay(dayKey: String): Int

    @Query("DELETE FROM swap_events")
    suspend fun deleteAll(): Int

    // ---------------------------------------------------------------------
    // Live shift reads
    // ---------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL
        ORDER BY timestamp DESC, id DESC
        """,
    )
    fun observeLiveDay(dayKey: String): Flow<List<SwapEvent>>

    @Query(
        """
        SELECT COUNT(*) FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL AND type = :type
        """,
    )
    fun observeLiveCount(dayKey: String, type: VehicleType): Flow<Int>

    @Query(
        """
        SELECT
            CAST(COALESCE(SUM(CASE WHEN type = 'SCOOTER' THEN 1 ELSE 0 END), 0) AS INTEGER) AS scooterCount,
            CAST(COALESCE(SUM(CASE WHEN type = 'MOPED' THEN 1 ELSE 0 END), 0) AS INTEGER) AS mopedCount,
            COALESCE(SUM(CASE WHEN type = 'SCOOTER' THEN earnings ELSE 0 END), 0.0) AS scooterEarnings,
            COALESCE(SUM(CASE WHEN type = 'MOPED' THEN earnings ELSE 0 END), 0.0) AS mopedEarnings
        FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL
        """,
    )
    fun observeLiveTotals(dayKey: String): Flow<DayTotals>

    /** Number of live swaps recorded since [sinceMillis] (used by the pace hint). */
    @Query(
        """
        SELECT COUNT(*) FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL AND timestamp >= :sinceMillis
        """,
    )
    fun observeLiveCountSince(dayKey: String, sinceMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM swap_events WHERE dayKey = :dayKey AND shiftId IS NULL")
    suspend fun countLiveDay(dayKey: String): Int

    // ---------------------------------------------------------------------
    // Map reads
    // ---------------------------------------------------------------------

    /**
     * Every geolocated swap ever recorded, live or archived. Rows without a
     * fix are filtered out in SQL so the map never deals with null coordinates.
     */
    @Query(
        """
        SELECT * FROM swap_events
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        ORDER BY timestamp ASC
        """,
    )
    fun observeAllLocated(): Flow<List<SwapEvent>>

    @Query(
        """
        SELECT * FROM swap_events
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
          AND timestamp >= :sinceMillis
        ORDER BY timestamp ASC
        """,
    )
    fun observeLocatedSince(sinceMillis: Long): Flow<List<SwapEvent>>

    @Query(
        """
        SELECT
            CAST(COALESCE(SUM(CASE WHEN type = 'SCOOTER' THEN 1 ELSE 0 END), 0) AS INTEGER) AS scooterCount,
            CAST(COALESCE(SUM(CASE WHEN type = 'MOPED' THEN 1 ELSE 0 END), 0) AS INTEGER) AS mopedCount,
            COALESCE(SUM(earnings), 0.0) AS totalEarnings,
            CAST(COALESCE(SUM(CASE WHEN latitude IS NOT NULL THEN 1 ELSE 0 END), 0) AS INTEGER) AS locatedCount,
            CAST(COUNT(DISTINCT shiftId) AS INTEGER) AS shiftCount
        FROM swap_events
        """,
    )
    fun observeLifetimeTotals(): Flow<LifetimeTotals>

    // ---------------------------------------------------------------------
    // Archive reads
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Pace & trend reads
    // ---------------------------------------------------------------------

    /**
     * Live swaps bucketed by local hour.
     *
     * SQLite has no timezone support, so the hour is derived from the stored
     * epoch millis with a fixed offset passed in from Kotlin. This keeps the
     * bucketing consistent with the `dayKey` used everywhere else.
     */
    @Query(
        """
        SELECT
            CAST(((timestamp / 1000 + :offsetSeconds) % 86400) / 3600 AS INTEGER) AS hour,
            CAST(SUM(CASE WHEN type = 'SCOOTER' THEN 1 ELSE 0 END) AS INTEGER) AS scooterCount,
            CAST(SUM(CASE WHEN type = 'MOPED' THEN 1 ELSE 0 END) AS INTEGER) AS mopedCount
        FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL
        GROUP BY hour
        ORDER BY hour ASC
        """,
    )
    fun observeHourBuckets(dayKey: String, offsetSeconds: Int): Flow<List<HourBucket>>

    /** First/last live swap of the day, for the "active for X hours" figure. */
    @Query(
        """
        SELECT
            COALESCE(MIN(timestamp), 0) AS firstTimestamp,
            COALESCE(MAX(timestamp), 0) AS lastTimestamp,
            CAST(COUNT(*) AS INTEGER) AS swapCount
        FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL
        """,
    )
    fun observeShiftSpan(dayKey: String): Flow<ShiftSpan>

    /** Daily totals for the trend chart, newest day first. */
    @Query(
        """
        SELECT
            dayKey AS dayKey,
            CAST(SUM(CASE WHEN type = 'SCOOTER' THEN 1 ELSE 0 END) AS INTEGER) AS scooterCount,
            CAST(SUM(CASE WHEN type = 'MOPED' THEN 1 ELSE 0 END) AS INTEGER) AS mopedCount,
            COALESCE(SUM(earnings), 0.0) AS earnings
        FROM swap_events
        GROUP BY dayKey
        ORDER BY dayKey DESC
        LIMIT :limit
        """,
    )
    fun observeRecentDays(limit: Int): Flow<List<DayBucket>>

    /** Every swap of the live day, for region grouping in the ViewModel. */
    @Query(
        """
        SELECT * FROM swap_events
        WHERE dayKey = :dayKey AND shiftId IS NULL
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getLiveDay(dayKey: String): List<SwapEvent>

    // ---------------------------------------------------------------------
    // Archive reads
    // ---------------------------------------------------------------------

    /** One row per archived shift, newest first. */
    @Query(
        """
        SELECT
            shiftId AS shiftId,
            MIN(dayKey) AS dayKey,
            MIN(timestamp) AS startedAt,
            MAX(timestamp) AS endedAt,
            CAST(SUM(CASE WHEN type = 'SCOOTER' THEN 1 ELSE 0 END) AS INTEGER) AS scooterCount,
            CAST(SUM(CASE WHEN type = 'MOPED' THEN 1 ELSE 0 END) AS INTEGER) AS mopedCount,
            COALESCE(SUM(earnings), 0.0) AS totalEarnings,
            CAST(COUNT(*) AS INTEGER) AS eventCount
        FROM swap_events
        WHERE shiftId IS NOT NULL
        GROUP BY shiftId
        ORDER BY endedAt DESC
        """,
    )
    fun observeShifts(): Flow<List<ShiftSummary>>

    @Query("SELECT * FROM swap_events WHERE shiftId = :shiftId ORDER BY timestamp ASC")
    fun observeShiftEvents(shiftId: Long): Flow<List<SwapEvent>>

    /** Number of rows in a shift, used to validate a restore. */
    @Query("SELECT COUNT(*) FROM swap_events WHERE shiftId = :shiftId")
    suspend fun countShift(shiftId: Long): Int

    /**
     * Un-archives a shift: clears its stamp so the rows become "live" again and
     * feed the counters.
     *
     * The `dayKey` filter is enforced *here*, in the SQL, not by the caller:
     * "live" rows only ever count towards a day whose key matches, so clearing
     * the stamp of an older shift would remove it from the history sheet while
     * leaving its rows invisible to both the counters and the history - the
     * swaps would silently disappear from the app. Refusing to touch a
     * non-current day is what makes the restore safe; the caller turns the
     * resulting `0` into a "only today can be restored" message.
     */
    @Query("UPDATE swap_events SET shiftId = NULL WHERE shiftId = :shiftId AND dayKey = :dayKey")
    suspend fun unarchiveShift(shiftId: Long, dayKey: String): Int

    @Query("SELECT COALESCE(MAX(shiftId), 0) + 1 FROM swap_events")
    suspend fun nextShiftId(): Long

    @Query("UPDATE swap_events SET shiftId = :shiftId WHERE dayKey = :dayKey AND shiftId IS NULL")
    suspend fun stampDay(dayKey: String, shiftId: Long): Int

    /**
     * Archives the live rows of [dayKey] under a fresh shift id in one
     * transaction. Returns the new shift id, or `null` when there was nothing
     * to archive. Because the rows are only stamped (not moved) the operation
     * is atomic, instant, and cannot lose data if the process dies mid-way.
     */
    @Transaction
    suspend fun archiveShift(dayKey: String): Long? {
        val shiftId = nextShiftId()
        val stamped = stampDay(dayKey, shiftId)
        return if (stamped > 0) shiftId else null
    }

    // ---------------------------------------------------------------------
    // Maintenance
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM swap_events ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<SwapEvent>
}

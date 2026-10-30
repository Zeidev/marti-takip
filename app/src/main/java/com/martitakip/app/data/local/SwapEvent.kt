package com.martitakip.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The two vehicle families an operator can swap a battery on.
 *
 * The enum name is what gets persisted, which keeps a hand-inspected database
 * (or a CSV export) readable without a lookup table.
 */
enum class VehicleType {
    SCOOTER,
    MOPED,
    ;

    companion object {
        fun fromRaw(raw: String?): VehicleType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SCOOTER
    }
}

/**
 * A single battery swap.
 *
 * [earnings] is a *snapshot* of the unit price configured at the moment of the
 * tap. This is deliberate: changing the price in the settings dialog must never
 * rewrite the history of already recorded swaps.
 *
 * [latitude] / [longitude] are nullable because a swap must still be recorded
 * when the GPS fix cannot be obtained (indoors, permission revoked, timeout).
 *
 * [shiftId] is the archive marker:
 *  - `null`  -> the row belongs to the *live* shift and feeds the counters,
 *  - a value -> the row was archived by "Vardiyayı Kaydet & Sıfırla" and only
 *               feeds the history sheet and the heat map.
 * Keeping archived rows in the same table (instead of copying them elsewhere)
 * means the heat map keeps every point ever recorded while the daily counters
 * still drop to zero instantly and atomically.
 */
@Entity(
    tableName = "swap_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["dayKey", "shiftId"]),
        Index(value = ["shiftId"]),
        Index(value = ["latitude", "longitude"]),
    ],
)
data class SwapEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "type")
    val type: VehicleType,

    /** Price snapshot for this single swap, in TL. */
    @ColumnInfo(name = "earnings")
    val earnings: Double,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    /** Epoch millis, UTC. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Local calendar day (`yyyy-MM-dd`) used for the "today" queries.
     *
     * Storing the key instead of computing it inside SQL keeps daily grouping
     * index friendly and stable even if the device time zone changes mid-day.
     */
    @ColumnInfo(name = "dayKey")
    val dayKey: String = dayKeyOf(timestamp),

    @ColumnInfo(name = "shiftId")
    val shiftId: Long? = null,
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    companion object {
        fun dayKeyOf(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString()

        fun dayKeyOf(date: LocalDate): String = date.toString()
    }
}

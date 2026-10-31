package com.martitakip.app.util

import com.martitakip.app.data.local.SwapEvent

/**
 * Turns raw coordinates into a human readable neighbourhood label.
 *
 * Why offline geocoding instead of a reverse-geocoding API:
 *  - the app must stay at zero cost and work without network in the field,
 *  - a network call per swap would be slow, drain the battery and leak the
 *    operator's location to a third party.
 *
 * The approach is a coarse reverse lookup over a small, bundled table of
 * Istanbul neighbourhood centres. It is intentionally approximate: the goal is
 * "which area do most of my swaps happen in", not an address.
 *
 * Cells are ~500 m; the nearest centre wins. Anything outside the table is
 * reported as "Diğer".
 */
object RegionResolver {

    /** Name + centre of a known operating area. */
    data class Region(val name: String, val latitude: Double, val longitude: Double)

    /**
     * Operating areas commonly served by micromobility fleets in Istanbul.
     * Kept deliberately compact so the lookup is a few dozen comparisons.
     */
    private val REGIONS = listOf(
        Region("Kadıköy", 40.9901, 29.0290),
        Region("Moda", 40.9810, 29.0260),
        Region("Ataşehir", 40.9923, 29.1273),
        Region("Kozyatağı", 40.9720, 29.1000),
        Region("Bostancı", 40.9550, 29.0950),
        Region("Maltepe", 40.9350, 29.1550),
        Region("Kartal", 40.9100, 29.1900),
        Region("Pendik", 40.8770, 29.2330),
        Region("Üsküdar", 41.0260, 29.0150),
        Region("Ümraniye", 41.0160, 29.1250),
        Region("Beykoz", 41.1350, 29.0900),
        Region("Beşiktaş", 41.0430, 29.0090),
        Region("Levent", 41.0820, 29.0100),
        Region("Şişli", 41.0600, 28.9870),
        Region("Mecidiyeköy", 41.0670, 28.9950),
        Region("Karaköy", 41.0240, 28.9750),
        Region("Beyoğlu", 41.0360, 28.9770),
        Region("Fatih", 41.0180, 28.9500),
        Region("Bakırköy", 40.9800, 28.8700),
        Region("Bahçelievler", 41.0000, 28.8600),
        Region("Bağcılar", 41.0400, 28.8500),
        Region("Avcılar", 40.9800, 28.7200),
        Region("Beylikdüzü", 41.0050, 28.6400),
        Region("Sarıyer", 41.1700, 29.0500),
        Region("Maslak", 41.1100, 29.0200),
        Region("Kağıthane", 41.0800, 28.9700),
        Region("Esenyurt", 41.0350, 28.6750),
        Region("Küçükçekmece", 41.0000, 28.7800),
        Region("Sultanahmet", 41.0050, 28.9770),
    )

    /** Cells farther than this from any centre are reported as [UNKNOWN]. */
    private const val MAX_DISTANCE_METERS = 4_000.0

    const val UNKNOWN = "Diğer"

    /**
     * Nearest region for a coordinate, or [UNKNOWN] when nothing is close.
     * Pure and side-effect free, so it is trivially testable.
     */
    fun resolve(latitude: Double, longitude: Double): String {
        var bestName = UNKNOWN
        var bestDistance = Double.MAX_VALUE

        for (region in REGIONS) {
            val distance = haversineMeters(
                latitude, longitude,
                region.latitude, region.longitude,
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestName = region.name
            }
        }
        return if (bestDistance <= MAX_DISTANCE_METERS) bestName else UNKNOWN
    }

    /** Convenience for a nullable pair; returns null when there is no fix. */
    fun resolveOrNull(latitude: Double?, longitude: Double?): String? =
        if (latitude == null || longitude == null) null else resolve(latitude, longitude)

    /**
     * Groups swaps by region and returns them most active first.
     * Swaps without a fix are counted under [UNKNOWN].
     */
    fun summarize(events: List<SwapEvent>): List<RegionSummary> {
        if (events.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<String, MutableList<SwapEvent>>()
        for (event in events) {
            val key = resolveOrNull(event.latitude, event.longitude) ?: UNKNOWN
            buckets.getOrPut(key) { mutableListOf() }.add(event)
        }
        return buckets
            .map { (name, list) ->
                RegionSummary(
                    name = name,
                    scooterCount = list.count { it.type == com.martitakip.app.data.local.VehicleType.SCOOTER },
                    mopedCount = list.count { it.type == com.martitakip.app.data.local.VehicleType.MOPED },
                    earnings = list.sumOf { it.earnings },
                )
            }
            .sortedWith(
                compareByDescending<RegionSummary> { it.totalCount }
                    .thenByDescending { it.earnings },
            )
    }

    /** Great-circle distance in metres. */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/** Aggregated numbers for one operating area. */
data class RegionSummary(
    val name: String,
    val scooterCount: Int,
    val mopedCount: Int,
    val earnings: Double,
) {
    val totalCount: Int get() = scooterCount + mopedCount
}

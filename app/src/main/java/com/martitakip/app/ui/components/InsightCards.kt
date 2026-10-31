package com.martitakip.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martitakip.app.data.local.DayBucket
import com.martitakip.app.data.local.HourBucket
import com.martitakip.app.ui.TrackerViewModel.Companion.formatMoney
import com.martitakip.app.util.RegionSummary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pace card: swaps per hour, earnings per hour and the busiest hour.
 *
 * The rate is computed over the *active* part of the shift (first to last tap),
 * not wall-clock time since midnight, so a late start does not make the
 * operator look slow.
 */
@Composable
fun PaceCard(
    swapsPerHour: Double,
    earningsPerHour: Double,
    minutesPerSwap: Double,
    activeMinutes: Long,
    peakHour: HourBucket?,
    hourBuckets: List<HourBucket>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Saatlik hız",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDurationShort(activeMinutes) + " aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RateStat(
                    label = "Değişim / saat",
                    value = formatRate(swapsPerHour),
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                RateStat(
                    label = "Kazanç / saat",
                    value = formatMoney(earningsPerHour),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                RateStat(
                    label = "Dk / değişim",
                    value = formatRate(minutesPerSwap),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }

            if (hourBuckets.any { it.totalCount > 0 }) {
                Spacer(Modifier.height(14.dp))
                HourStrip(buckets = hourBuckets, accent = accent)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peakHour?.let { "En yoğun saat: %02d:00".format(it.hour) }
                            ?: "Henüz veri yok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "08 – 22",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Tiny bar strip for the working hours. Only 08:00–22:00 is drawn: outside that
 * window micromobility swaps are rare enough that the bars would just be noise.
 */
@Composable
private fun HourStrip(buckets: List<HourBucket>, accent: Color) {
    val byHour = buckets.associateBy { it.hour }
    val hours = 8..21
    val maxCount = (hours.maxOfOrNull { byHour[it]?.totalCount ?: 0 } ?: 0).coerceAtLeast(1)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        hours.forEach { hour ->
            val count = byHour[hour]?.totalCount ?: 0
            val fraction = count.toFloat() / maxCount
            val animated by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(350),
                label = "hourBar$hour",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((28 * animated.coerceAtLeast(0.06f)).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (count == 0) {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                } else {
                                    accent.copy(alpha = 0.35f + 0.65f * animated)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RateStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Region breakdown: where today's swaps happened, most active area first.
 *
 * Regions come from the offline [com.martitakip.app.util.RegionResolver], so
 * this works without network and without a geocoding bill.
 */
@Composable
fun RegionBreakdownCard(
    regions: List<RegionSummary>,
    modifier: Modifier = Modifier,
    maxRows: Int = 4,
) {
    val total = regions.sumOf { it.totalCount }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Bölge kırılımı",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${regions.size} bölge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (regions.isEmpty()) {
                Text(
                    text = "Bugün henüz bölge verisi yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                regions.take(maxRows).forEach { region ->
                    RegionRow(region = region, total = total)
                    Spacer(Modifier.height(10.dp))
                }
                if (regions.size > maxRows) {
                    Text(
                        text = "+${regions.size - maxRows} bölge daha",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionRow(region: RegionSummary, total: Int) {
    val fraction = if (total <= 0) 0f else region.totalCount.toFloat() / total
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "regionBar",
    )
    val vehicle = com.martitakip.app.ui.theme.MartiTheme.vehicle

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${region.totalCount} · ${formatMoney(region.earnings)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        ) {
            if (region.scooterCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (animated * region.scooterCount / region.totalCount.toFloat())
                                .coerceIn(0f, 1f),
                        )
                        .height(6.dp)
                        .background(vehicle.scooter),
                )
            }
            if (region.mopedCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (animated * region.mopedCount / region.totalCount.toFloat())
                                .coerceIn(0f, 1f),
                        )
                        .height(6.dp)
                        .background(vehicle.moped),
                )
            }
        }
    }
}

/**
 * Daily trend: a compact bar chart of the last two weeks with a delta badge.
 * Today's bar is highlighted.
 */
@Composable
fun TrendCard(
    days: List<DayBucket>,
    maxCount: Int,
    delta: Int,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val deltaUp = delta > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (deltaUp) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                    contentDescription = null,
                    tint = if (delta == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (deltaUp) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Son ${days.size} gün",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        delta > 0 -> "düne göre +$delta"
                        delta < 0 -> "düne göre $delta"
                        else -> "düne eşit"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        delta > 0 -> MaterialTheme.colorScheme.secondary
                        delta < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            if (days.isEmpty()) {
                Text(
                    text = "Trend için henüz yeterli veri yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    days.forEachIndexed { index, day ->
                        val isToday = index == days.lastIndex
                        val fraction = day.totalCount.toFloat() / maxCount
                        val animated by animateFloatAsState(
                            targetValue = fraction.coerceIn(0.04f, 1f),
                            animationSpec = tween(400),
                            label = "trendBar$index",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .height((64 * animated).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (isToday) accent else accent.copy(alpha = 0.28f),
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = days.firstOrNull()?.dayKey?.takeLast(5) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "bugün",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }
        }
    }
}

/**
 * Motivational footer: a short streak/pace message that changes with progress.
 * Kept deliberately simple - it is encouragement, not analytics.
 */
@Composable
fun MotivationBanner(
    totalCount: Int,
    goal: Int,
    progress: Float,
    swapsPerHour: Double,
    modifier: Modifier = Modifier,
) {
    val message = when {
        totalCount == 0 -> "İlk değişimle güne başla 🚀"
        goal > 0 && totalCount >= goal -> "Hedef tamamlandı, harika iş! 🎯"
        goal > 0 && progress >= 0.75f -> "Bitmesine az kaldı, tempoyu koru 💪"
        swapsPerHour >= 15 -> "Harika tempo, saatte ${formatRate(swapsPerHour)} değişim!"
        progress >= 0.5f -> "Yarısı bitti, devam! ⚡"
        else -> "İyi gidiyorsun, her değişim hedefe yaklaştırıyor"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "2sa 15dk" / "45dk" formatting for the pace card. */
fun formatDurationShort(minutes: Long): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}sa ${remaining}dk" else "${remaining}dk"
}

/** One decimal, Turkish comma - avoids "12.0" looking like a price. */
fun formatRate(value: Double): String =
    String.format(Locale.forLanguageTag("tr-TR"), "%.1f", value)

/** Rounds a fraction to a whole percent for labels. */
fun formatPercent(fraction: Float): String = "%${(fraction * 100).roundToInt()}"

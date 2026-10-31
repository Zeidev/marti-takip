package com.martitakip.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.martitakip.app.data.local.SwapEvent
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.ui.TrackerUiState
import com.martitakip.app.ui.TrackerViewModel
import com.martitakip.app.ui.TrackerViewModel.Companion.formatMoney
import com.martitakip.app.ui.components.CounterActionCard
import com.martitakip.app.ui.components.GoalProgressCard
import com.martitakip.app.ui.components.GpsStatusChip
import com.martitakip.app.ui.components.MotivationBanner
import com.martitakip.app.ui.components.PaceCard
import com.martitakip.app.ui.components.RegionBreakdownCard
import com.martitakip.app.ui.components.TrendCard
import com.martitakip.app.ui.theme.MartiTakipTheme
import com.martitakip.app.ui.theme.MartiTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The primary field screen: two giant counters, the live TL total, the daily
 * goal and the shift actions.
 *
 * The composable is stateless with respect to business data - it renders
 * [state] and forwards user intent through the lambdas, which keeps previews
 * and UI tests trivial.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainTrackerScreen(
    state: TrackerUiState,
    onIncrement: (VehicleType) -> Unit,
    onDecrement: (VehicleType) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onShare: () -> Unit,
    onArchiveShift: () -> Unit,
    onClearToday: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val haptics = LocalHapticFeedback.current
    val vehicle = MartiTheme.vehicle

    /**
     * Haptics fire *before* the state change so the confirmation is felt even
     * if the recomposition is a frame late.
     */
    val incrementWithHaptic: (VehicleType) -> Unit = { type ->
        if (state.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onIncrement(type)
    }
    val decrementWithHaptic: (VehicleType) -> Unit = { type ->
        if (state.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onDecrement(type)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Martı Takip",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = state.dayKey.toPrettyDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Günlük raporu paylaş",
                        )
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Vardiya geçmişi",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Ayarlar",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- Live earnings header -------------------------------------
            item(key = "header") {
                EarningsHeader(state = state)
            }

            // --- GPS status + permission prompt ---------------------------
            item(key = "gps") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GpsStatusChip(
                            isBusy = state.isGpsBusy,
                            hasPermission = state.hasLocationPermission,
                            lastFixLabel = state.lastFix?.let {
                                "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude)
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        if (!state.hasLocationPermission) {
                            TextButton(onClick = onRequestPermission) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("İzin ver")
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !state.hasLocationPermission,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = "Konum izni olmadan da değişimler kaydedilir; " +
                                    "ancak ısı haritasında görünmezler.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            // --- Scooter card ---------------------------------------------
            item(key = "scooter") {
                CounterActionCard(
                    title = "Scooter",
                    subtitle = "Elektrikli scooter batarya değişimi",
                    count = state.scooterCount,
                    earnings = state.scooterEarnings,
                    unitPrice = state.scooterPrice,
                    accent = vehicle.scooter,
                    containerColor = vehicle.scooterContainer,
                    onContainerColor = vehicle.onScooterContainer,
                    icon = Icons.Rounded.PedalBike,
                    onIncrement = { incrementWithHaptic(VehicleType.SCOOTER) },
                    onDecrement = { decrementWithHaptic(VehicleType.SCOOTER) },
                )
            }

            // --- Moped card -----------------------------------------------
            item(key = "moped") {
                CounterActionCard(
                    title = "Moped",
                    subtitle = "Moped batarya değişimi",
                    count = state.mopedCount,
                    earnings = state.mopedEarnings,
                    unitPrice = state.mopedPrice,
                    accent = vehicle.moped,
                    containerColor = vehicle.mopedContainer,
                    onContainerColor = vehicle.onMopedContainer,
                    icon = Icons.Rounded.TwoWheeler,
                    onIncrement = { incrementWithHaptic(VehicleType.MOPED) },
                    onDecrement = { decrementWithHaptic(VehicleType.MOPED) },
                )
            }

            // --- Goal -----------------------------------------------------
            item(key = "goal") {
                GoalProgressCard(
                    current = state.totalCount,
                    goal = state.dailyGoal,
                    progress = state.goalProgress,
                    reached = state.goalReached,
                    remaining = state.remainingToGoal,
                    projectedEarnings = (state.scooterPrice + state.mopedPrice) / 2.0 * state.dailyGoal,
                )
            }

            // --- Motivation ------------------------------------------------
            item(key = "motivation") {
                MotivationBanner(
                    totalCount = state.totalCount,
                    goal = state.dailyGoal,
                    progress = state.goalProgress,
                    swapsPerHour = state.swapsPerHour,
                )
            }

            // --- Pace ------------------------------------------------------
            item(key = "pace") {
                PaceCard(
                    swapsPerHour = state.swapsPerHour,
                    earningsPerHour = state.earningsPerHour,
                    minutesPerSwap = state.minutesPerSwap,
                    activeMinutes = state.activeMinutes,
                    peakHour = state.peakHour,
                    hourBuckets = state.hourBuckets,
                )
            }

            // --- Regions ---------------------------------------------------
            item(key = "regions") {
                RegionBreakdownCard(regions = state.regionSummaries)
            }

            // --- Trend -----------------------------------------------------
            item(key = "trend") {
                TrendCard(
                    days = state.recentDays,
                    maxCount = state.trendMax,
                    delta = state.trendDelta,
                )
            }

            // --- Shift actions --------------------------------------------
            item(key = "actions") {
                ShiftActions(
                    state = state,
                    onArchiveShift = onArchiveShift,
                    onShare = onShare,
                    onClearToday = onClearToday,
                )
            }

            // --- Recent taps ----------------------------------------------
            if (state.recentSwaps.isNotEmpty()) {
                item(key = "recent-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Bugünün son işlemleri",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "en yeni ${state.recentSwaps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.recentSwaps, key = { it.id }) { swap ->
                    RecentSwapRow(swap = swap)
                }
            }
        }
    }
}

@Composable
private fun EarningsHeader(state: TrackerUiState) {
    val vehicle = MartiTheme.vehicle
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BUGÜNKÜ KAZANÇ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatMoney(state.totalEarnings),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.totalCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "adet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderPill(
                    label = "Scooter",
                    value = "${state.scooterCount} · ${formatMoney(state.scooterEarnings)}",
                    dotColor = vehicle.scooter,
                    modifier = Modifier.weight(1f),
                )
                HeaderPill(
                    label = "Moped",
                    value = "${state.mopedCount} · ${formatMoney(state.mopedEarnings)}",
                    dotColor = vehicle.moped,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.hasPending) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun HeaderPill(
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ShiftActions(
    state: TrackerUiState,
    onArchiveShift: () -> Unit,
    onShare: () -> Unit,
    onClearToday: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = onArchiveShift,
            enabled = !state.isArchiving && !state.isEmptyDay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (state.isArchiving) "Kaydediliyor…" else "Vardiyayı Kaydet & Sıfırla",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Raporu paylaş (WhatsApp / Telegram)")
        }
        if (!state.isEmptyDay) {
            TextButton(
                onClick = onClearToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Bugünü arşivlemeden sil",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state.isEmptyDay) {
            Text(
                text = "Bugün henüz değişim kaydedilmedi. İlk +1 ile başla!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** `2026-10-30` -> `30 Ekim 2026, Cuma`. Falls back to the raw key. */
private fun String.toPrettyDate(): String = runCatching {
    val date = LocalDate.parse(this)
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR")))
}.getOrElse { this }

/**
 * One line in the "recent swaps" list.
 *
 * Shows the stored price snapshot (not the current setting), the local time and
 * whether a coordinate was captured - which is exactly the information an
 * operator needs to trust the heat map.
 */
@Composable
private fun RecentSwapRow(swap: SwapEvent) {
    val vehicle = MartiTheme.vehicle
    val isScooter = swap.type == VehicleType.SCOOTER
    val accent = vehicle.accentFor(isScooter)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScooter) "Scooter" else "Moped",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = TrackerViewModel.formatTime(swap.timestamp) +
                        if (swap.hasLocation) " · konum kaydedildi" else " · konumsuz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatMoney(swap.earnings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MainTrackerPreview() {
    MartiTakipTheme {
        MainTrackerScreen(
            state = TrackerUiState(
                isLoading = false,
                dayKey = LocalDate.now().toString(),
                scooterCount = 12,
                mopedCount = 7,
                scooterPrice = 15.0,
                mopedPrice = 25.0,
                dailyGoal = 40,
                hasLocationPermission = true,
            ),
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

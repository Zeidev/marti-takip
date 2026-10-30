package com.martitakip.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martitakip.app.data.local.AppDatabase
import com.martitakip.app.data.local.ShiftSummary
import com.martitakip.app.data.local.SwapEvent
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.data.repository.SwapRepository
import com.martitakip.app.data.settings.SettingsRepository
import com.martitakip.app.data.settings.UserSettings
import com.martitakip.app.location.LocationHelper
import com.martitakip.app.location.SwapCoordinate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/** Everything the tracker screen renders, in one immutable snapshot. */
data class TrackerUiState(
    val isLoading: Boolean = true,
    val dayKey: String = "",
    val scooterCount: Int = 0,
    val mopedCount: Int = 0,
    /** Sum of the stored price snapshots for today's scooter swaps. */
    val scooterEarnings: Double = 0.0,
    /** Sum of the stored price snapshots for today's moped swaps. */
    val mopedEarnings: Double = 0.0,
    val scooterPrice: Double = UserSettings.DEFAULT_SCOOTER_PRICE,
    val mopedPrice: Double = UserSettings.DEFAULT_MOPED_PRICE,
    val dailyGoal: Int = UserSettings.DEFAULT_DAILY_GOAL,
    val hapticsEnabled: Boolean = true,
    val autoGpsEnabled: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val isGpsBusy: Boolean = false,
    val isArchiving: Boolean = false,
    val pendingSwaps: Int = 0,
    val lastFix: SwapCoordinate? = null,
    val settingsOpen: Boolean = false,
    val historyOpen: Boolean = false,
    /** Newest-first slice of today's live swaps, for the "recent" list. */
    val recentSwaps: List<SwapEvent> = emptyList(),
) {
    val totalCount: Int get() = scooterCount + mopedCount

    /**
     * Real earnings of the live shift, summed from the per-row snapshots.
     *
     * [scooterPrice] / [mopedPrice] are only used for the *next* swap and for
     * the "per unit" hint, so raising the price in the settings dialog can
     * never inflate what was already earned today.
     */
    val totalEarnings: Double get() = scooterEarnings + mopedEarnings
    val goalProgress: Float
        get() = if (dailyGoal <= 0) 0f else (totalCount.toFloat() / dailyGoal).coerceIn(0f, 1f)
    val goalReached: Boolean get() = dailyGoal > 0 && totalCount >= dailyGoal
    val remainingToGoal: Int get() = (dailyGoal - totalCount).coerceAtLeast(0)
    val avgEarningPerSwap: Double get() = if (totalCount == 0) 0.0 else totalEarnings / totalCount
    val isEmptyDay: Boolean get() = totalCount == 0
    val hasPending: Boolean get() = pendingSwaps > 0
}

/** One-shot signals that must not be replayed on configuration change. */
sealed interface TrackerEvent {
    data class SwapRecorded(val type: VehicleType, val hasLocation: Boolean) : TrackerEvent
    data class SwapUndone(val type: VehicleType, val hadSomethingToUndo: Boolean) : TrackerEvent
    data class ShiftArchived(
        val swapCount: Int,
        val earnings: Double,
        val shiftId: Long,
    ) : TrackerEvent
    data object NothingToArchive : TrackerEvent
    data class Message(val text: String) : TrackerEvent
    data class ShareRequest(val text: String) : TrackerEvent
    data object GoalReached : TrackerEvent
}

/** Row model used by the history sheet and the map legend. */
data class ShiftHistoryItem(
    val shiftId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val scooterCount: Int,
    val mopedCount: Int,
    val totalEarnings: Double,
    val eventCount: Int,
) {
    val totalCount: Int get() = scooterCount + mopedCount
}

/** Aggregated data for the heat-map screen. */
data class MapUiState(
    val isLoading: Boolean = true,
    val points: List<SwapEvent> = emptyList(),
    val lifetimeScooter: Int = 0,
    val lifetimeMoped: Int = 0,
    val lifetimeEarnings: Double = 0.0,
    val lifetimeLocated: Int = 0,
    val shiftCount: Int = 0,
    val rangeDays: Int = 0,
) {
    val totalPoints: Int get() = points.size
}

/**
 * Owns all UI state and side effects.
 *
 * Concurrency model:
 *  - Writes are serialised through [swapMutex] so a "-1" undo can never race a
 *    "+1" insert and delete the wrong row.
 *  - The GPS lookup happens *before* taking the mutex: it is bounded by
 *    [LocationHelper.TIMEOUT_MS] and runs on [viewModelScope] (never on the
 *    main thread), so the UI keeps scrolling and tapping while the fix is in
 *    flight. [pendingSwaps] mirrors the in-flight taps so the screen can show
 *    activity without waiting for the row to land.
 *  - A missing fix is not an error: the row is still inserted with null
 *    coordinates and simply will not appear on the heat map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).swapDao()
    private val repository = SwapRepository(dao)
    private val settingsRepository = SettingsRepository(application)
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrackerEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<TrackerEvent> = _events.asSharedFlow()

    private val _mapState = MutableStateFlow(MapUiState())
    val mapState: StateFlow<MapUiState> = _mapState.asStateFlow()

    private val _mapRangeDays = MutableStateFlow(0) // 0 == all time
    val mapRangeDays: StateFlow<Int> = _mapRangeDays.asStateFlow()

    private val _history = MutableStateFlow<List<ShiftHistoryItem>>(emptyList())
    val history: StateFlow<List<ShiftHistoryItem>> = _history.asStateFlow()

    /** Serialises writes so undo always targets the row it means to. */
    private val swapMutex = Mutex()

    /** Guards against double-taps on "archive" while the transaction runs. */
    private val archiveMutex = Mutex()

    /** In-flight optimistic taps, used to keep the "+1" button responsive. */
    private val pendingSwaps = MutableStateFlow(0)

    /**
     * A single shared GPS capture.
     *
     * Tapping +1 four times in two seconds must not start four location
     * requests: every tap that arrives while a capture is in flight simply
     * awaits the same [Deferred]. That is the whole battery story of this app -
     * one chip wake-up per burst of taps, never one per tap.
     */
    private var inFlightFix: Deferred<SwapCoordinate?>? = null
    private val fixMutex = Mutex()

    private val dayKeyFlow = MutableStateFlow(repository.todayKey())

    private val settingsFlow: StateFlow<UserSettings> = settingsRepository.settings
        .catch { emit(UserSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    init {
        observeLiveCounters()
        observeSettings()
        observeMap()
        observeHistory()
        refreshPermissionState()
    }

    // -----------------------------------------------------------------------
    // Reactive plumbing
    // -----------------------------------------------------------------------

    private fun observeLiveCounters() {
        viewModelScope.launch {
            dayKeyFlow.flatMapLatest { day ->
                repository.observeLiveEvents(day)
            }.collect { events ->
                _uiState.update { it.copy(recentSwaps = events.take(RECENT_SWAP_LIMIT)) }
            }
        }

        viewModelScope.launch {
            dayKeyFlow.flatMapLatest { day ->
                repository.observeLiveTotals(day)
            }.collect { totals ->
                val previousTotal = _uiState.value.totalCount
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scooterCount = totals.scooterCount,
                        mopedCount = totals.mopedCount,
                        scooterEarnings = totals.scooterEarnings,
                        mopedEarnings = totals.mopedEarnings,
                    )
                }
                val goal = _uiState.value.dailyGoal
                // Celebrate exactly once, on the transition into the goal, so
                // an undo + redo does not spam the snackbar/haptics.
                if (goal > 0 && previousTotal < goal && totals.totalCount >= goal) {
                    emitEvent(TrackerEvent.GoalReached)
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                val previousGoal = _uiState.value.dailyGoal
                _uiState.update {
                    it.copy(
                        scooterPrice = settings.scooterPrice,
                        mopedPrice = settings.mopedPrice,
                        dailyGoal = settings.dailyGoal,
                        hapticsEnabled = settings.hapticsEnabled,
                        autoGpsEnabled = settings.autoGps,
                    )
                }
                // Lowering the goal below today's count should still celebrate,
                // because the operator genuinely reached it.
                val state = _uiState.value
                if (settings.dailyGoal > 0 &&
                    settings.dailyGoal != previousGoal &&
                    state.totalCount >= settings.dailyGoal
                ) {
                    emitEvent(TrackerEvent.GoalReached)
                }
            }
        }
    }

    private fun observeMap() {
        viewModelScope.launch {
            _mapRangeDays.flatMapLatest { days ->
                val since = if (days <= 0) {
                    Long.MIN_VALUE
                } else {
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                }
                if (days <= 0) {
                    repository.observeAllLocated()
                } else {
                    repository.observeLocatedSince(since)
                }
            }.collect { events ->
                _mapState.update { current ->
                    current.copy(isLoading = false, points = events)
                }
            }
        }

        viewModelScope.launch {
            repository.observeLifetimeTotals().collect { totals ->
                _mapState.update {
                    it.copy(
                        lifetimeScooter = totals.scooterCount,
                        lifetimeMoped = totals.mopedCount,
                        lifetimeEarnings = totals.totalEarnings,
                        lifetimeLocated = totals.locatedCount,
                        shiftCount = totals.shiftCount,
                    )
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.observeShifts().collect { shifts ->
                _history.value = shifts.map { it.toHistoryItem() }
            }
        }
    }

    private fun ShiftSummary.toHistoryItem() = ShiftHistoryItem(
        shiftId = shiftId,
        startedAt = startedAt,
        endedAt = endedAt,
        scooterCount = scooterCount,
        mopedCount = mopedCount,
        totalEarnings = totalEarnings,
        eventCount = eventCount,
    )

    // -----------------------------------------------------------------------
    // Permission state
    // -----------------------------------------------------------------------

    fun refreshPermissionState() {
        _uiState.update { it.copy(hasLocationPermission = locationHelper.hasPermission()) }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (!granted) {
            emitEvent(
                TrackerEvent.Message(
                    "Konum izni verilmedi. Değişimler koordinatsız olarak kaydedilecek.",
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Core actions: +1 / -1
    // -----------------------------------------------------------------------

    /**
     * Records a single swap.
     *
     * Order of operations matters and is what makes the screen feel instant:
     *  1. The row is inserted with `null` coordinates right away (a single
     *     indexed INSERT, sub-millisecond), so the counter and the TL figure
     *     update on the very next frame.
     *  2. The GPS capture then runs in the background and patches the
     *     coordinate onto that same row when the fix lands. The UI is never
     *     blocked and no swap is ever lost if the fix never arrives.
     *
     * Writes are serialised through [swapMutex] so a "-1" undo can never race
     * the insert and remove the wrong row.
     */
    fun onSwapIncrement(type: VehicleType) {
        viewModelScope.launch {
            pendingSwaps.update { it + 1 }
            _uiState.update { it.copy(isGpsBusy = true, pendingSwaps = pendingSwaps.value) }

            val eventId = try {
                swapMutex.withLock {
                    val price = settingsFlow.value.priceFor(type)
                    val timestamp = System.currentTimeMillis()
                    // Roll the day key over if the shift crossed midnight
                    // while the app stayed open.
                    val dayKey = SwapEvent.dayKeyOf(timestamp)
                    if (dayKey != dayKeyFlow.value) {
                        dayKeyFlow.value = dayKey
                        _uiState.update { it.copy(dayKey = dayKey) }
                    }
                    repository.recordSwap(
                        type = type,
                        unitPrice = price,
                        latitude = null,
                        longitude = null,
                        timestamp = timestamp,
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                pendingSwaps.update { max(0, it - 1) }
                _uiState.update { it.copy(pendingSwaps = pendingSwaps.value, isGpsBusy = pendingSwaps.value > 0) }
                throw cancellation
            } catch (error: Exception) {
                pendingSwaps.update { max(0, it - 1) }
                _uiState.update { it.copy(isGpsBusy = pendingSwaps.value > 0, pendingSwaps = pendingSwaps.value) }
                emitEvent(
                    TrackerEvent.Message(
                        "Değişim kaydedilemedi: ${error.message ?: "bilinmeyen hata"}",
                    ),
                )
                return@launch
            }

            emitEvent(TrackerEvent.SwapRecorded(type, hasLocation = false))

            // Step 2: best-effort coordinate capture, fully detached from the
            // insert so the UI has already moved on.
            val state = _uiState.value
            if (state.autoGpsEnabled && state.hasLocationPermission) {
                val coordinate = awaitSharedFix()
                if (coordinate != null && coordinate.isValid) {
                    runCatching {
                        repository.attachLocation(eventId, coordinate.latitude, coordinate.longitude)
                    }
                    _uiState.update { it.copy(lastFix = coordinate) }
                }
            }

            pendingSwaps.update { max(0, it - 1) }
            _uiState.update { it.copy(isGpsBusy = pendingSwaps.value > 0, pendingSwaps = pendingSwaps.value) }
        }
    }

    /**
     * Returns the coordinate of the capture currently in flight, or starts one
     * if there is none. Concurrent callers share the same [Deferred], so a
     * burst of taps costs exactly one GPS request.
     */
    private suspend fun awaitSharedFix(): SwapCoordinate? {
        val deferred = fixMutex.withLock {
            val existing = inFlightFix
            if (existing != null && existing.isActive) {
                existing
            } else {
                viewModelScope.async { captureCoordinate() }.also { inFlightFix = it }
            }
        }
        return try {
            deferred.await()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }

    /**
     * One-shot capture with a hard deadline. Never throws: a missing fix is a
     * normal outcome in the field, not an error.
     */
    private suspend fun captureCoordinate(): SwapCoordinate? = try {
        locationHelper.getCurrentLocation()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }

    /** Removes the most recent swap of [type] for today. */
    fun onSwapDecrement(type: VehicleType) {
        viewModelScope.launch {
            try {
                swapMutex.withLock {
                    repository.undoLast(type, dayKeyFlow.value)
                }.let { removed ->
                    emitEvent(TrackerEvent.SwapUndone(type, removed))
                }
            } catch (error: Exception) {
                emitEvent(
                    TrackerEvent.Message("Geri alınamadı: ${error.message ?: "bilinmeyen hata"}"),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shift management
    // -----------------------------------------------------------------------

    /**
     * Closes the shift: stamps the live rows into the archive, resets the
     * counters to zero and returns a summary that can be shared immediately.
     */
    fun onArchiveShift() {
        if (archiveMutex.isLocked) return
        viewModelScope.launch {
            archiveMutex.withLock {
                val state = _uiState.value
                if (state.totalCount == 0) {
                    emitEvent(TrackerEvent.NothingToArchive)
                    return@withLock
                }
                _uiState.update { it.copy(isArchiving = true) }
                try {
                    val swapCount = state.totalCount
                    val earnings = state.totalEarnings
                    val shiftId = repository.archiveCurrentShift(dayKeyFlow.value)
                    if (shiftId == null) {
                        emitEvent(TrackerEvent.NothingToArchive)
                    } else {
                        emitEvent(TrackerEvent.ShiftArchived(swapCount, earnings, shiftId))
                    }
                } catch (error: Exception) {
                    emitEvent(
                        TrackerEvent.Message(
                            "Vardiya kaydedilemedi: ${error.message ?: "bilinmeyen hata"}",
                        ),
                    )
                } finally {
                    _uiState.update { it.copy(isArchiving = false) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    fun openSettings() = _uiState.update { it.copy(settingsOpen = true) }

    fun closeSettings() = _uiState.update { it.copy(settingsOpen = false) }

    fun openHistory() = _uiState.update { it.copy(historyOpen = true) }

    fun closeHistory() = _uiState.update { it.copy(historyOpen = false) }

    fun saveSettings(scooterPrice: Double, mopedPrice: Double, dailyGoal: Int) {
        viewModelScope.launch {
            settingsRepository.updatePrices(scooterPrice, mopedPrice)
            settingsRepository.updateDailyGoal(dailyGoal)
            closeSettings()
            emitEvent(TrackerEvent.Message("Ayarlar güncellendi."))
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setAutoGpsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoGpsEnabled(enabled) }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            emitEvent(TrackerEvent.Message("Ayarlar varsayılana döndürüldü."))
        }
    }

    // -----------------------------------------------------------------------
    // Sharing
    // -----------------------------------------------------------------------

    /**
     * Builds the WhatsApp/Telegram summary. Kept in the ViewModel (not the
     * composable) so the exact same text can be reused by a future widget or
     * notification without duplicating formatting logic.
     */
    fun onShareToday() {
        val state = _uiState.value
        val text = buildShareText(state)
        emitEvent(TrackerEvent.ShareRequest(text))
    }

    fun buildShareText(state: TrackerUiState = _uiState.value): String {
        val dateLabel = runCatching {
            LocalDate.parse(state.dayKey)
                .format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("tr", "TR")))
        }.getOrElse { state.dayKey }

        val goalLine = if (state.dailyGoal > 0) {
            "🎯 Hedef: ${state.totalCount}/${state.dailyGoal} " +
                if (state.goalReached) "(tamamlandı ✅)" else "(%${(state.goalProgress * 100).roundToInt()})"
        } else {
            "🎯 Hedef: belirlenmedi"
        }

        return buildString {
            appendLine("🛴 Martı Batarya Değişim Raporu")
            appendLine("📅 $dateLabel")
            appendLine("──────────────")
            appendLine("🛴 Scooter: ${state.scooterCount} adet = ${formatMoney(state.scooterEarnings)}")
            appendLine("🛵 Moped: ${state.mopedCount} adet = ${formatMoney(state.mopedEarnings)}")
            appendLine("──────────────")
            appendLine("🔋 Toplam değişim: ${state.totalCount} adet")
            appendLine("💰 Toplam kazanç: ${formatMoney(state.totalEarnings)}")
            appendLine(goalLine)
            append("📊 Değişim başına ortalama: ${formatMoney(state.avgEarningPerSwap)}")
        }
    }

    fun onShareShift(item: ShiftHistoryItem) {
        val text = buildString {
            appendLine("🛴 Martı Vardiya Raporu")
            appendLine("📅 ${formatDateTime(item.startedAt)} → ${formatDateTime(item.endedAt)}")
            appendLine("──────────────")
            appendLine("🛴 Scooter: ${item.scooterCount} adet")
            appendLine("🛵 Moped: ${item.mopedCount} adet")
            appendLine("🔋 Toplam değişim: ${item.totalCount} adet")
            append("💰 Kazanç: ${formatMoney(item.totalEarnings)}")
        }
        emitEvent(TrackerEvent.ShareRequest(text))
    }

    /**
     * Builds a CSV of the whole archive and shares it.
     *
     * The database lives only on the device, so this is the operator's escape
     * hatch: it can be mailed to a spreadsheet or kept as a personal backup.
     */
    fun onExportCsv() {
        viewModelScope.launch {
            val events = runCatching { repository.exportAll() }.getOrElse { emptyList() }
            if (events.isEmpty()) {
                emitEvent(TrackerEvent.Message("Dışa aktarılacak kayıt yok."))
                return@launch
            }
            val csv = buildString {
                appendLine("id,tarih,saat,tur,kazanc_tl,enlem,boylam,vardiya")
                events.forEach { event ->
                    val zoned = Instant.ofEpochMilli(event.timestamp).atZone(ZoneId.systemDefault())
                    val date = zoned.toLocalDate().toString()
                    val time = zoned.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    append(event.id).append(',')
                    append(date).append(',')
                    append(time).append(',')
                    append(event.type.name).append(',')
                    append(String.format(Locale.US, "%.2f", event.earnings)).append(',')
                    append(event.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "").append(',')
                    append(event.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "").append(',')
                    append(event.shiftId ?: "")
                    appendLine()
                }
            }
            emitEvent(
                TrackerEvent.ShareRequest(
                    text = "Martı Takip CSV dışa aktarımı (${events.size} kayıt)\n\n$csv",
                ),
            )
        }
    }

    fun onShareLifetime() {
        val map = _mapState.value
        val text = buildString {
            appendLine("🛴 Martı Genel Özet")
            appendLine("──────────────")
            appendLine("🛴 Scooter: ${map.lifetimeScooter} adet")
            appendLine("🛵 Moped: ${map.lifetimeMoped} adet")
            appendLine("🔋 Toplam değişim: ${map.lifetimeScooter + map.lifetimeMoped} adet")
            appendLine("💰 Toplam kazanç: ${formatMoney(map.lifetimeEarnings)}")
            append("📍 Haritalanan nokta: ${map.lifetimeLocated}")
        }
        emitEvent(TrackerEvent.ShareRequest(text))
    }

    // -----------------------------------------------------------------------
    // Map controls
    // -----------------------------------------------------------------------

    fun setMapRangeDays(days: Int) {
        _mapRangeDays.value = days.coerceAtLeast(0)
    }

    // -----------------------------------------------------------------------
    // Housekeeping
    // -----------------------------------------------------------------------

    /** Called when the app returns to the foreground: rolls over the day key. */
    fun onAppResumed() {
        refreshPermissionState()
        val today = repository.todayKey()
        if (today != dayKeyFlow.value) {
            dayKeyFlow.value = today
            _uiState.update { it.copy(dayKey = today) }
        } else if (_uiState.value.dayKey != today) {
            _uiState.update { it.copy(dayKey = today) }
        }
    }

    fun emitMessage(text: String) = emitEvent(TrackerEvent.Message(text))

    private fun emitEvent(event: TrackerEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    // -----------------------------------------------------------------------
    // Formatting helpers (shared with the UI layer)
    // -----------------------------------------------------------------------

    companion object {
        /** How many recent taps the tracker screen lists. */
        const val RECENT_SWAP_LIMIT = 8

        private val TURKISH = Locale("tr", "TR")
        private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", TURKISH)
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", TURKISH)

        /**
         * Formats a TL amount with Turkish grouping (`1.234,50 ₺`).
         * A fresh formatter per call keeps this safe to call from any thread
         * (DecimalFormat is not thread safe).
         */
        fun formatMoney(value: Double): String {
            val formatter = java.text.NumberFormat.getNumberInstance(TURKISH).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            return "${formatter.format(value)} ₺"
        }

        fun formatDateTime(epochMillis: Long): String {
            val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
            return "${zoned.format(dateFormatter)} ${zoned.format(timeFormatter)}"
        }

        fun formatTime(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

        fun formatDuration(startMillis: Long, endMillis: Long): String {
            val minutes = abs((endMillis - startMillis) / 60_000L)
            val hours = minutes / 60
            val remaining = minutes % 60
            return if (hours > 0) "${hours}s ${remaining}dk" else "${remaining}dk"
        }
    }
}

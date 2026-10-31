package com.martitakip.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.data.settings.UserSettings
import com.martitakip.app.ui.TrackerEvent
import com.martitakip.app.ui.TrackerViewModel
import com.martitakip.app.ui.screens.ClearTodayDialog
import com.martitakip.app.ui.screens.RestoreShiftDialog
import com.martitakip.app.ui.screens.SettingsDialog
import com.martitakip.app.ui.screens.ShiftHistorySheet
import com.martitakip.app.ui.screens.TrackerAppShell
import com.martitakip.app.ui.screens.TrackerDestination
import com.martitakip.app.ui.theme.MartiTakipTheme
import com.martitakip.app.util.SoundFeedback

/**
 * Single-activity host.
 *
 * Responsibilities kept here (and deliberately *not* in the ViewModel):
 *  - runtime permission request flow (needs an ActivityResultLauncher),
 *  - building and dispatching share intents (needs a Context),
 *  - snackbar presentation and the small amount of pure-navigation UI state.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it } || hasLocationPermission()
        viewModel.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            MartiTakipTheme {
                TrackerApp(
                    viewModel = viewModel,
                    onRequestLocationPermission = ::requestLocationPermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Rolls the day key over at midnight and refreshes the permission flag.
        viewModel.onAppResumed()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
}

@Composable
private fun TrackerApp(
    viewModel: TrackerViewModel,
    onRequestLocationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Sound pool is tied to the composition, not the Activity, so it is
    // released as soon as the UI goes away.
    val soundFeedback = remember { SoundFeedback(context) }
    DisposableEffect(soundFeedback) {
        onDispose { soundFeedback.release() }
    }

    val trackerState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val mapRangeDays by viewModel.mapRangeDays.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var destinationOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val destination = TrackerDestination.fromOrdinal(destinationOrdinal)

    // Ask for the location permission once, on first launch, without blocking
    // the counter UI (swaps work fine with null coordinates).
    var askedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedPermissionOnce && !trackerState.hasLocationPermission) {
            askedPermissionOnce = true
            onRequestLocationPermission()
        }
    }

    // Keep the day rollover fresh even if the activity is never paused.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // One-shot events: snackbars, haptics and share intents.
    //
    // `showSnackbar` suspends until the snackbar is dismissed, so each one is
    // launched into its own coroutine: a lingering snackbar must never delay a
    // permission prompt or a share intent behind it.
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val hapticsOn = viewModel.uiState.value.hapticsEnabled
            when (event) {
                is TrackerEvent.SwapRecorded -> {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (viewModel.uiState.value.soundEnabled) soundFeedback.playConfirm()
                }

                is TrackerEvent.SwapUndone -> {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (event.hadSomethingToUndo && viewModel.uiState.value.soundEnabled) {
                        soundFeedback.playUndo()
                    }
                    if (!event.hadSomethingToUndo) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Geri alınacak kayıt bulunamadı.",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                }

                is TrackerEvent.ShiftArchived -> {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Vardiya kaydedildi: ${event.swapCount} değişim, " +
                                TrackerViewModel.formatMoney(event.earnings),
                            actionLabel = "Paylaş",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.history.value
                                .firstOrNull { it.shiftId == event.shiftId }
                                ?.let(viewModel::onShareShift)
                        }
                    }
                }

                is TrackerEvent.ShiftRestored -> {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "#${event.shiftId} vardiyası geri alındı " +
                                "(${event.rowCount} değişim sayaca döndü).",
                            duration = SnackbarDuration.Long,
                        )
                    }
                }

                TrackerEvent.RestoreFailed -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Geri alınacak vardiya bulunamadı.",
                        duration = SnackbarDuration.Short,
                    )
                }

                TrackerEvent.NothingToArchive -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Kaydedilecek değişim yok. Önce +1 ile sayaç ekle.",
                        duration = SnackbarDuration.Short,
                    )
                }

                is TrackerEvent.Message -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.text,
                        duration = SnackbarDuration.Short,
                    )
                }

                is TrackerEvent.ShareRequest -> shareText(context, event.text)

                TrackerEvent.GoalReached -> {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "🎯 Günlük hedef tamamlandı! Harika iş.",
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrackerAppShell(
            destination = destination,
            onDestinationChange = { destinationOrdinal = it.ordinal },
            trackerState = trackerState,
            mapState = mapState,
            mapRangeDays = mapRangeDays,
            onMapRangeChange = viewModel::setMapRangeDays,
            onIncrement = viewModel::onSwapIncrement,
            onDecrement = viewModel::onSwapDecrement,
            onOpenSettings = viewModel::openSettings,
            onOpenHistory = viewModel::openHistory,
            onShare = viewModel::onShareToday,
            onShareMapSummary = viewModel::onShareLifetime,
            onArchiveShift = viewModel::onArchiveShift,
            onClearToday = viewModel::requestClearToday,
            onRequestPermission = onRequestLocationPermission,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
        )
    }

    // --- Dialogs & sheets ------------------------------------------------

    SettingsDialog(
        visible = trackerState.settingsOpen,
        current = UserSettings(
            scooterPrice = trackerState.scooterPrice,
            mopedPrice = trackerState.mopedPrice,
            dailyGoal = trackerState.dailyGoal,
            hapticsEnabled = trackerState.hapticsEnabled,
            autoGps = trackerState.autoGpsEnabled,
            soundEnabled = trackerState.soundEnabled,
        ),
        onDismiss = viewModel::closeSettings,
        onSave = viewModel::saveSettings,
        onHapticsChanged = viewModel::setHapticsEnabled,
        onAutoGpsChanged = viewModel::setAutoGpsEnabled,
        onSoundChanged = viewModel::setSoundEnabled,
        onResetToDefaults = viewModel::resetSettingsToDefaults,
        onExportCsv = {
            viewModel.closeSettings()
            viewModel.onExportCsv()
        },
    )

    ShiftHistorySheet(
        visible = trackerState.historyOpen,
        shifts = history,
        onDismiss = viewModel::closeHistory,
        onShareShift = viewModel::onShareShift,
        onRestoreShift = { viewModel.requestRestoreShift(it.shiftId) },
    )

    RestoreShiftDialog(
        visible = trackerState.pendingRestoreShiftId != null,
        shiftId = trackerState.pendingRestoreShiftId ?: 0L,
        onConfirm = viewModel::confirmRestoreShift,
        onDismiss = viewModel::cancelRestoreShift,
    )

    ClearTodayDialog(
        visible = trackerState.pendingClear,
        swapCount = trackerState.totalCount,
        earnings = trackerState.totalEarnings,
        onConfirm = viewModel::confirmClearToday,
        onDismiss = viewModel::cancelClearToday,
    )
}

/**
 * Fires a `ACTION_SEND` chooser so the operator can push the daily summary to
 * WhatsApp, Telegram or anything else installed on the device.
 */
private fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Martı Takip - Günlük Rapor")
    }
    val chooser = Intent.createChooser(intent, "Raporu paylaş").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

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
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.data.settings.UserSettings
import com.martitakip.app.ui.TrackerEvent
import com.martitakip.app.ui.TrackerViewModel
import com.martitakip.app.ui.screens.ClearTodayDialog
import com.martitakip.app.ui.screens.RestoreShiftDialog
import com.martitakip.app.ui.screens.SettingsSheet
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
        // Before super.onCreate(): the call reads the splash style off the
        // activity and swaps in its `postSplashScreenTheme` for the rest of the
        // process. On API 31+ the platform draws the splash window itself; this
        // line is what makes API 26 look the same instead of a blank frame.
        installSplashScreen()
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

                TrackerEvent.RestoreNotToday -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Sadece bugünün vardiyası geri alınabilir. " +
                            "Önceki günlerin kayıtları geçmişte kalır.",
                        duration = SnackbarDuration.Long,
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

                is TrackerEvent.ShareRequest -> share(context, event)

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

    SettingsSheet(
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
 * Fires an `ACTION_SEND` chooser so the operator can push a report to WhatsApp,
 * Telegram or anything else installed on the device.
 *
 * Two transports, picked by the event:
 *  - no [TrackerEvent.ShareRequest.fileName] -> the payload goes into
 *    `EXTRA_TEXT` (short: a day's summary, one shift, the lifetime totals),
 *  - a file name -> the payload is written to the app cache and shared as a
 *    `content://` attachment. That is the only safe route for the CSV archive,
 *    which grows without bound and would otherwise overflow the ~1 MB Binder
 *    transaction limit and crash the share with
 *    `TransactionTooLargeException`.
 *
 * A failure to write the cache file degrades to a text share rather than
 * losing the export.
 */
private suspend fun share(context: android.content.Context, event: TrackerEvent.ShareRequest) {
    val fileName = event.fileName
    if (fileName != null) {
        // The archive is the one payload big enough to be worth a file, so the
        // write goes off the main thread for the same reason it is a file at
        // all: it is bounded by the number of rows the operator has ever
        // recorded, not by anything small.
        val uri = withContext(Dispatchers.IO) {
            runCatching { writeShareFile(context, event.text, fileName) }.getOrNull()
        }
        if (uri != null) {
            sendChooser(
                context,
                Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    putExtra(Intent.EXTRA_TEXT, event.subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            return
        }
    }
    sendChooser(
        context,
        Intent(Intent.ACTION_SEND).apply {
            type = event.mimeType
            putExtra(Intent.EXTRA_TEXT, event.text)
            putExtra(Intent.EXTRA_SUBJECT, event.subject)
        },
    )
}

/**
 * The chooser keeps a fixed title; [TrackerEvent.ShareRequest.subject] is only
 * the subject the target app shows for a mail/to-do share.
 */
private fun sendChooser(context: android.content.Context, intent: Intent) {
    // Launched from an Activity, so no NEW_TASK: adding it would hand the
    // chooser and the app the operator picks their own task, and "back" would
    // leave them somewhere other than the tracker.
    context.startActivity(Intent.createChooser(intent, "Raporu paylaş"))
}

/**
 * Writes [content] into `cacheDir/exports/` and returns a shareable URI.
 *
 * The directory is pruned on every export: this is the app cache, not the
 * operator's storage, and an older export has no value once a fresher one
 * exists. The file that is about to be shared always survives the prune.
 */
private fun writeShareFile(
    context: android.content.Context,
    content: String,
    fileName: String,
): android.net.Uri {
    val directory = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    directory.listFiles()?.forEach { stale ->
        if (stale.name != fileName) stale.delete()
    }
    val file = java.io.File(directory, fileName)
    file.writeText(content)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

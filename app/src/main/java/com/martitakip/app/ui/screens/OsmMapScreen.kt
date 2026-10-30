package com.martitakip.app.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.martitakip.app.BuildConfig
import com.martitakip.app.data.local.SwapEvent
import com.martitakip.app.data.local.VehicleType
import com.martitakip.app.ui.MapUiState
import com.martitakip.app.ui.TrackerViewModel.Companion.formatMoney
import com.martitakip.app.ui.theme.MartiTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Density / heat-map screen.
 *
 * Rendering strategy (chosen for battery and for old field phones):
 *  - One custom [Overlay] draws *all* points on a single canvas pass instead of
 *    creating thousands of [Marker] views. Markers are only used for the
 *    "latest swap" pin, which must be interactive.
 *  - Points are bucketed into a grid whose cell size follows the zoom level, so
 *    the visual density stays readable at any scale and the number of drawn
 *    circles stays bounded.
 *  - The tile cache is disk-backed (osmdroid default) and the User-Agent is set
 *    once per process, as required by the OSM tile usage policy.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OsmMapScreen(
    state: MapUiState,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
    onShareSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicle = MartiTheme.vehicle

    // osmdroid is a process-wide singleton; configure it before the first
    // MapView is inflated.
    remember { configureOsmdroid(context) }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnce by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_DESTROY -> view?.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.onPause()
            view?.onDetach()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setUseDataConnection(true)
                    // Keep the map light: no on-screen zoom buttons (pinch and
                    // double-tap are enough), no compass, no mini-map.
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 3.0
                    maxZoomLevel = 19.0
                    controller.setZoom(13.0)
                    // Istanbul as a neutral, on-brand default before any fix.
                    controller.setCenter(GeoPoint(41.0082, 28.9784))
                    mapView = this
                }
            },
            update = { view ->
                view.overlays.removeAll { it is HeatmapOverlay }
                view.overlays.add(HeatmapOverlay(state.points))
                view.invalidate()
            },
        )

        // --- Top summary card -------------------------------------------
        // The map itself is drawn edge-to-edge behind the status bar; only the
        // floating controls are inset.
        MapSummaryCard(
            state = state,
            rangeDays = rangeDays,
            onRangeChange = onRangeChange,
            onShareSummary = onShareSummary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp),
        )

        // --- Legend ------------------------------------------------------
        MapLegend(
            scooterColor = vehicle.scooter,
            mopedColor = vehicle.moped,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(16.dp),
        )

        // --- Recenter FAB ------------------------------------------------
        FloatingActionButton(
            onClick = {
                val points = state.points
                val view = mapView
                if (view != null && points.isNotEmpty()) {
                    val avgLat = points.sumOf { it.latitude ?: 0.0 } / points.size
                    val avgLng = points.sumOf { it.longitude ?: 0.0 } / points.size
                    view.controller.animateTo(GeoPoint(avgLat, avgLng))
                    view.controller.setZoom(15.0)
                } else {
                    view?.controller?.animateTo(GeoPoint(41.0082, 28.9784))
                    view?.controller?.setZoom(13.0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.CenterFocusStrong,
                contentDescription = "Haritayı kayıtlara ortala",
            )
        }

        // --- Empty state --------------------------------------------------
        if (!state.isLoading && state.points.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Henüz haritalanmış değişim yok",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Konum izni verip +1'e bastığında noktalar burada birikir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }
    }

    // Auto-frame the data once per dataset, without fighting the user's pans.
    LaunchedEffect(state.points.size, hasCenteredOnce) {
        val view = mapView
        if (view != null && !hasCenteredOnce && state.points.isNotEmpty()) {
            val points = state.points
            val avgLat = points.sumOf { it.latitude ?: 0.0 } / points.size
            val avgLng = points.sumOf { it.longitude ?: 0.0 } / points.size
            view.controller.setCenter(GeoPoint(avgLat, avgLng))
            view.controller.setZoom(if (points.size > 20) 15.0 else 16.0)
            hasCenteredOnce = true
        }
    }
}

@Composable
private fun MapSummaryCard(
    state: MapUiState,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
    onShareSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle = MartiTheme.vehicle
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Yoğunluk Haritası",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${state.totalPoints} nokta · ${state.shiftCount} vardiya",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShareSummary) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Genel özeti paylaş",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RangeChip(
                    label = "Bugün",
                    selected = rangeDays == 1,
                    onClick = { onRangeChange(1) },
                    modifier = Modifier.weight(1f),
                )
                RangeChip(
                    label = "7 gün",
                    selected = rangeDays == 7,
                    onClick = { onRangeChange(7) },
                    modifier = Modifier.weight(1f),
                )
                RangeChip(
                    label = "30 gün",
                    selected = rangeDays == 30,
                    onClick = { onRangeChange(30) },
                    modifier = Modifier.weight(1f),
                )
                RangeChip(
                    label = "Tümü",
                    selected = rangeDays == 0,
                    onClick = { onRangeChange(0) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LegendStat(
                    label = "Scooter",
                    value = state.lifetimeScooter.toString(),
                    color = vehicle.scooter,
                    modifier = Modifier.weight(1f),
                )
                LegendStat(
                    label = "Moped",
                    value = state.lifetimeMoped.toString(),
                    color = vehicle.moped,
                    modifier = Modifier.weight(1f),
                )
                LegendStat(
                    label = "Kazanç",
                    value = formatMoney(state.lifetimeEarnings),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun RangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun LegendStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MapLegend(
    scooterColor: Color,
    mopedColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            LegendRow(color = scooterColor, label = "Scooter")
            Spacer(Modifier.height(6.dp))
            LegendRow(color = mopedColor, label = "Moped")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.55f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Draws every recorded swap as a translucent circle.
 *
 * Grid bucketing: at low zoom, hundreds of points collapse into a handful of
 * cells whose alpha scales with the count, which is what produces the "heat"
 * look without a bitmap blur pass (cheap on old GPUs).
 */
private class HeatmapOverlay(
    private val points: List<SwapEvent>,
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return
        val projection = mapView.projection
        val zoom = mapView.zoomLevelDouble

        // Radius shrinks as we zoom in so the dots stay meaningful.
        val baseRadiusPx = (mapView.width / 42.0).coerceIn(6.0, 26.0)
        val radiusPx = when {
            zoom < 10 -> baseRadiusPx * 1.8f
            zoom < 13 -> baseRadiusPx * 1.35f
            zoom < 16 -> baseRadiusPx
            else -> baseRadiusPx * 0.75f
        }.toFloat()

        val cellPx = radiusPx * 2.2f
        val buckets = HashMap<Long, Bucket>()

        for (point in points) {
            val lat = point.latitude ?: continue
            val lng = point.longitude ?: continue
            val projected = projection.toPixels(GeoPoint(lat, lng), null)
            if (projected.x < -cellPx || projected.y < -cellPx) continue
            if (projected.x > mapView.width + cellPx || projected.y > mapView.height + cellPx) continue

            val cellX = (projected.x / cellPx).toInt()
            val cellY = (projected.y / cellPx).toInt()
            val key = (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)

            val bucket = buckets.getOrPut(key) {
                Bucket(x = projected.x.toFloat(), y = projected.y.toFloat())
            }
            bucket.add(point.type)
        }

        // Two passes so orange mopeds are never fully hidden by green scooters.
        for ((_, bucket) in buckets) {
            bucket.paintInto(canvas, paint, strokePaint, radiusPx)
        }
    }

    private class Bucket(val x: Float, val y: Float) {
        var scooterCount = 0
        var mopedCount = 0

        fun add(type: VehicleType) {
            when (type) {
                VehicleType.SCOOTER -> scooterCount++
                VehicleType.MOPED -> mopedCount++
            }
        }

        fun paintInto(canvas: Canvas, paint: Paint, strokePaint: Paint, radius: Float) {
            val total = scooterCount + mopedCount
            // More swaps in one cell -> more opaque, capped for legibility.
            val alpha = (70 + total * 22).coerceAtMost(190)
            val scooterShare = scooterCount.toFloat() / total
            val mopedShare = mopedCount.toFloat() / total

            if (scooterCount > 0 && mopedCount > 0) {
                // Pie-like split keeps both colours visible in mixed cells.
                paint.color = AndroidColor.argb(alpha, 0x00, 0xC8, 0x53)
                canvas.drawCircle(x, y, radius, paint)
                paint.color = AndroidColor.argb((alpha * 0.95f).toInt(), 0xFF, 0x7A, 0x00)
                canvas.drawCircle(
                    x + radius * 0.35f,
                    y - radius * 0.35f,
                    radius * (0.55f + mopedShare * 0.35f),
                    paint,
                )
            } else if (scooterCount > 0) {
                paint.color = AndroidColor.argb(alpha, 0x00, 0xC8, 0x53)
                canvas.drawCircle(x, y, radius * (0.8f + scooterShare * 0.25f), paint)
            } else {
                paint.color = AndroidColor.argb(alpha, 0xFF, 0x7A, 0x00)
                canvas.drawCircle(x, y, radius * (0.8f + mopedShare * 0.25f), paint)
            }

            // Crisp outline for the most recent/hot cells.
            if (total >= 3) {
                strokePaint.color = AndroidColor.argb(220, 0x1B, 0x1B, 0x1B)
                canvas.drawCircle(x, y, radius * 0.98f, strokePaint)
            }
        }
    }
}

/**
 * One-time osmdroid configuration.
 *
 * The User-Agent *must* identify the app, per the OSM tile usage policy:
 * https://operations.osmfoundation.org/policies/tiles/
 */
private fun configureOsmdroid(context: android.content.Context) {
    val configuration = Configuration.getInstance()
    configuration.load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
    configuration.userAgentValue = BuildConfig.OSM_USER_AGENT
    // Respect the tile policy: keep a bounded, disk-backed cache so repeated
    // field visits do not re-download the same tiles.
    configuration.tileFileSystemCacheMaxBytes = 300L * 1024L * 1024L
    configuration.tileFileSystemCacheTrimBytes = 250L * 1024L * 1024L
}

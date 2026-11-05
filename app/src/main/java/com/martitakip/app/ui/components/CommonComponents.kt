package com.martitakip.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martitakip.app.ui.TrackerViewModel.Companion.formatMoney
import kotlinx.coroutines.launch

/**
 * The giant "+1" tap target.
 *
 * Design constraints from the field:
 *  - It must be reachable with a thumb while holding a battery in the other hand.
 *  - It must give *immediate* visual feedback, independent of the database,
 *    so the operator knows the tap registered even with gloves on.
 *  - The whole card is the button; there is no small hit area anywhere.
 *
 * [onIncrement] is invoked synchronously from the click handler; the caller is
 * responsible for doing the (instant) insert off the composition.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CounterActionCard(
    title: String,
    subtitle: String,
    count: Int,
    /** Real earnings accumulated for this vehicle type (sum of price snapshots). */
    earnings: Double,
    unitPrice: Double,
    accent: Color,
    containerColor: Color,
    onContainerColor: Color,
    icon: ImageVector,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val bump = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // All timing comes from the Material motion scheme rather than hand-picked
    // durations, so this card moves in step with the Material components around
    // it and follows the scheme if the theme ever switches to the expressive
    // one: spatial for anything that changes bounds or position (scale, size),
    // effects for anything that only changes paint (colour).
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cardPressScale",
    )

    val borderColor by animateColorAsState(
        targetValue = if (count > 0) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cardBorder",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .semantics {
                contentDescription =
                    "$title sayacı: $count değişim, ${formatMoney(earnings)}. " +
                        "Artırmak için dokun."
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = {
            // Fire the counter update first, then run the bump animation.
            onIncrement()
            scope.launch {
                bump.snapTo(1f)
                // The growth is a spatial change, so it takes the fast spatial
                // spec and lands inside the tap-to-acknowledge window.
                bump.animateTo(
                    targetValue = 1.06f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                )
                // The settle back is deliberately *not* the motion scheme: a
                // bouncy spring here is the whole point of the bump, and the
                // scheme's spatial specs are tuned to be unobtrusive.
                bump.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
        },
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    width = if (count > 0) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(28.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.18f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onContainerColor,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainerColor.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    DecrementButton(
                        accent = accent,
                        enabled = enabled && count > 0,
                        onClick = onDecrement,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.scale(bump.value),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = onContainerColor,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "adet",
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainerColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatMoney(earnings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        Text(
                            text = "${formatMoney(unitPrice)} / adet",
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainerColor.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+1  DEĞİŞİM EKLE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Compact "-1" control. Deliberately small and visually quieter than the big
 * card so it cannot be hit by accident, but still a full 48dp accessibility
 * target.
 */
@Composable
fun DecrementButton(
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f),
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = "Son değişimi geri al",
                tint = accent,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Animated feedback chip shown while a GPS fix is in flight. */
@Composable
fun GpsStatusChip(
    isBusy: Boolean,
    hasPermission: Boolean,
    lastFixLabel: String?,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = when {
            isBusy -> MaterialTheme.colorScheme.secondaryContainer
            !hasPermission -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "gpsChipBackground",
    )
    val foreground by animateColorAsState(
        targetValue = when {
            isBusy -> MaterialTheme.colorScheme.onSecondaryContainer
            !hasPermission -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "gpsChipForeground",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isBusy -> MaterialTheme.colorScheme.secondary
                            !hasPermission -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    isBusy -> "Konum alınıyor…"
                    !hasPermission -> "Konum izni yok"
                    lastFixLabel != null -> "Son konum: $lastFixLabel"
                    else -> "Konum hazır"
                },
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
            )
        }
    }
}

/** Progress ring + numbers for the daily goal. */
@Composable
fun GoalProgressCard(
    current: Int,
    goal: Int,
    progress: Float,
    reached: Boolean,
    remaining: Int,
    projectedEarnings: Double,
    modifier: Modifier = Modifier,
) {
    val accent = if (reached) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "goalProgress",
    )

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
                Text(
                    text = if (reached) "🎯 Günlük hedef tamamlandı!" else "🎯 Günlük hedef",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$current / $goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            GoalBar(progress = animatedProgress, accent = accent)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (reached) {
                        "Hedefe ulaştın, harika iş!"
                    } else {
                        "Hedefe $remaining değişim kaldı"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "≈ ${formatMoney(projectedEarnings)} hedef",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Segmented goal bar with a subtle animated fill. */
@Composable
fun GoalBar(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.85f), accent),
                    ),
                ),
        )
    }
}

/** Small key/value stat used across the header and the map sheet. */
@Composable
fun StatPill(
    label: String,
    value: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

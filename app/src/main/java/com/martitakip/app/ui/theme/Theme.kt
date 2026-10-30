package com.martitakip.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Brand palette
// ---------------------------------------------------------------------------

/** Scooter identity: the "electric green" used by micromobility fleets. */
val ScooterGreen = Color(0xFF00C853)
val ScooterGreenDark = Color(0xFF00A344)
val ScooterGreenContainer = Color(0xFFB9F6CA)
val ScooterGreenOnContainer = Color(0xFF00390F)

/** Moped identity: warm orange, readable on both themes. */
val MopedOrange = Color(0xFFFF7A00)
val MopedOrangeDark = Color(0xFFE06A00)
val MopedOrangeContainer = Color(0xFFFFE0B2)
val MopedOrangeOnContainer = Color(0xFF4A2600)

/** Primary brand accent used by the app bar and the FAB. */
val MartiTeal = Color(0xFF00695C)
val MartiTealLight = Color(0xFF4DB6AC)

private val LightColors = lightColorScheme(
    primary = MartiTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = ScooterGreenDark,
    onSecondary = Color.White,
    secondaryContainer = ScooterGreenContainer,
    onSecondaryContainer = ScooterGreenOnContainer,
    tertiary = MopedOrangeDark,
    onTertiary = Color.White,
    tertiaryContainer = MopedOrangeContainer,
    onTertiaryContainer = MopedOrangeOnContainer,
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = MartiTealLight,
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = ScooterGreen,
    onSecondary = Color(0xFF00390F),
    secondaryContainer = Color(0xFF00531A),
    onSecondaryContainer = ScooterGreenContainer,
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2600),
    tertiaryContainer = Color(0xFF6B3A00),
    onTertiaryContainer = MopedOrangeContainer,
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDDE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDDE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ---------------------------------------------------------------------------
// Semantic extension: vehicle colours that adapt to the active theme
// ---------------------------------------------------------------------------

@Immutable
data class VehicleColors(
    val scooter: Color,
    val scooterContainer: Color,
    val onScooterContainer: Color,
    val moped: Color,
    val mopedContainer: Color,
    val onMopedContainer: Color,
    val goalTrack: Color,
    val success: Color,
    val warning: Color,
) {
    fun accentFor(isScooter: Boolean): Color = if (isScooter) scooter else moped
    fun containerFor(isScooter: Boolean): Color =
        if (isScooter) scooterContainer else mopedContainer
    fun onContainerFor(isScooter: Boolean): Color =
        if (isScooter) onScooterContainer else onMopedContainer
}

private val LightVehicleColors = VehicleColors(
    scooter = ScooterGreenDark,
    scooterContainer = ScooterGreenContainer,
    onScooterContainer = ScooterGreenOnContainer,
    moped = MopedOrangeDark,
    mopedContainer = MopedOrangeContainer,
    onMopedContainer = MopedOrangeOnContainer,
    goalTrack = Color(0xFFDBE5E0),
    success = Color(0xFF00875A),
    warning = Color(0xFFB26A00),
)

private val DarkVehicleColors = VehicleColors(
    scooter = ScooterGreen,
    scooterContainer = Color(0xFF00531A),
    onScooterContainer = ScooterGreenContainer,
    moped = Color(0xFFFFB74D),
    mopedContainer = Color(0xFF6B3A00),
    onMopedContainer = MopedOrangeContainer,
    goalTrack = Color(0xFF3F4945),
    success = Color(0xFF5CE0A0),
    warning = Color(0xFFFFC46B),
)

val LocalVehicleColors = staticCompositionLocalOf { LightVehicleColors }

// ---------------------------------------------------------------------------
// Typography: big, glanceable numbers for gloved outdoor use
// ---------------------------------------------------------------------------

private val MartiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ---------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------

@Composable
fun MartiTakipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val vehicleColors = if (darkTheme) DarkVehicleColors else LightVehicleColors

    CompositionLocalProvider(LocalVehicleColors provides vehicleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MartiTypography,
            shapes = MartiShapes,
            content = content,
        )
    }
}

/** Shorthand for the semantic vehicle palette. */
object MartiTheme {
    val vehicle: VehicleColors
        @Composable get() = LocalVehicleColors.current

    val spacing: Spacing
        @Composable get() = Spacing()
}

/** Consistent spacing scale (4dp base grid). */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

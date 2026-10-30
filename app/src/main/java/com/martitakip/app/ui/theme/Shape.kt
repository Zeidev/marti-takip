package com.martitakip.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale for the app.
 *
 * Cards are deliberately generous (24dp) because the operator taps them with
 * gloves on; dialogs and sheets use the Material 3 large/extra-large corner
 * tokens.
 */
val MartiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

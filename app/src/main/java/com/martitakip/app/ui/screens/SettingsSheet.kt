package com.martitakip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.martitakip.app.data.settings.UserSettings
import com.martitakip.app.ui.theme.MartiTakipTheme
import com.martitakip.app.ui.theme.MartiTheme

/**
 * Settings sheet: the two unit prices, the daily goal and the three toggles.
 *
 * Presented as a bottom sheet rather than a dialog. The settings are edited and
 * live-applied in place - each toggle writes through immediately - so there is
 * nothing modal to protect the operator from, and a sheet keeps the counters
 * visible behind it. The pattern matches [ShiftHistorySheet].
 *
 * Validation rules:
 *  - Prices accept both `15` and `15,50` (Turkish decimal comma) and are
 *    clamped to [UserSettings.MIN_PRICE]..[UserSettings.MAX_PRICE].
 *  - The goal is an integer clamped to [UserSettings.MIN_GOAL]..[UserSettings.MAX_GOAL].
 *  - The Save button is disabled while any field is invalid, so an accidental
 *    empty field can never wipe the configured prices.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsSheet(
    visible: Boolean,
    current: UserSettings,
    onDismiss: () -> Unit,
    onSave: (scooterPrice: Double, mopedPrice: Double, dailyGoal: Int) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onAutoGpsChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    onExportCsv: () -> Unit,
) {
    if (!visible) return

    var scooterText by remember { mutableStateOf(current.scooterPrice.toEditablePrice()) }
    var mopedText by remember { mutableStateOf(current.mopedPrice.toEditablePrice()) }
    var goalText by remember { mutableStateOf(current.dailyGoal.toString()) }
    var haptics by remember { mutableStateOf(current.hapticsEnabled) }
    var autoGps by remember { mutableStateOf(current.autoGps) }
    var sound by remember { mutableStateOf(current.soundEnabled) }

    // Re-seed the fields only when the dialog opens. Keying this on `current`
    // would wipe whatever the operator is typing every time a toggle writes
    // its new value to DataStore.
    LaunchedEffect(visible) {
        if (visible) {
            scooterText = current.scooterPrice.toEditablePrice()
            mopedText = current.mopedPrice.toEditablePrice()
            goalText = current.dailyGoal.toString()
            haptics = current.hapticsEnabled
            autoGps = current.autoGps
            sound = current.soundEnabled
        }
    }

    val scooterPrice = scooterText.parsePrice()
    val mopedPrice = mopedText.parsePrice()
    val goal = goalText.trim().toIntOrNull()
    val scooterError = scooterText.isNotBlank() && scooterPrice == null
    val mopedError = mopedText.isNotBlank() && mopedPrice == null
    val goalError = goalText.isNotBlank() && goal == null
    val canSave = scooterPrice != null && mopedPrice != null && goal != null

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Column {
                Text(
                    text = "Ayarlar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Birim fiyatlar ve günlük hedef",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PriceField(
                    label = "Scooter birim fiyatı (₺)",
                    value = scooterText,
                    isError = scooterError,
                    supporting = "Her scooter değişimi için kazanç",
                    accentColor = MartiTheme.vehicle.scooter,
                    onValueChange = { scooterText = it },
                )
                PriceField(
                    label = "Moped birim fiyatı (₺)",
                    value = mopedText,
                    isError = mopedError,
                    supporting = "Her moped değişimi için kazanç",
                    accentColor = MartiTheme.vehicle.moped,
                    onValueChange = { mopedText = it },
                )

                OutlinedTextField(
                    value = goalText,
                    onValueChange = { input ->
                        goalText = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Günlük hedef (adet)") },
                    supportingText = {
                        Text(
                            if (goalError) {
                                "1 ile ${UserSettings.MAX_GOAL} arasında bir sayı gir"
                            } else {
                                "Vardiya sonunda ulaşmak istediğin batarya sayısı"
                            },
                        )
                    },
                    isError = goalError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ToggleRow(
                    title = "Titreşimli geri bildirim",
                    subtitle = "Her +1 dokunuşunda haptik onay",
                    checked = haptics,
                    onCheckedChange = {
                        haptics = it
                        onHapticsChanged(it)
                    },
                )
                ToggleRow(
                    title = "Otomatik konum",
                    subtitle = "Her kayıtta tek seferlik GPS (pili korur)",
                    checked = autoGps,
                    onCheckedChange = {
                        autoGps = it
                        onAutoGpsChanged(it)
                    },
                )
                ToggleRow(
                    title = "Sesli onay",
                    subtitle = "Her değişimde kısa bip (gürültülü sahada)",
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        onSoundChanged(it)
                    },
                )

                TextButton(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Tüm kayıtları CSV olarak dışa aktar")
                }

                TextButton(
                    onClick = onResetToDefaults,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Varsayılan değerlere dön")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Filled primary action on the right, quiet dismiss on the left.
            // An AlertDialog laid the two out as equal text buttons, which
            // reads as two equally likely answers to a question whose default
            // is unambiguous.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Vazgeç")
                }
                Button(
                    onClick = {
                        if (scooterPrice != null && mopedPrice != null && goal != null) {
                            onSave(scooterPrice, mopedPrice, goal)
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Kaydet",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceField(
    label: String,
    value: String,
    isError: Boolean,
    supporting: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onValueChange: (String) -> Unit,
) {
    val isScooter = label.startsWith("Scooter")
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Allow digits plus a single decimal separator (`,` or `.`).
            val filtered = buildString {
                var separatorSeen = false
                for (char in input) {
                    when {
                        char.isDigit() -> append(char)
                        (char == ',' || char == '.') && !separatorSeen -> {
                            append(char)
                            separatorSeen = true
                        }
                    }
                }
            }.take(9)
            onValueChange(filtered)
        },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (isScooter) Icons.Rounded.PedalBike else Icons.Rounded.TwoWheeler,
                contentDescription = null,
                tint = accentColor,
            )
        },
        suffix = { Text("₺") },
        supportingText = { Text(supporting) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Formats a stored price without a trailing `,00` when it is a whole lira. */
private fun Double.toEditablePrice(): String =
    if (this % 1.0 == 0.0) this.toInt().toString()
    else String.format(java.util.Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

/**
 * Parses `15`, `15,5` and `15.50` into a normalised TL amount.
 * Returns null when the input is not a valid price in the accepted range.
 */
private fun String.parsePrice(): Double? {
    val normalized = trim().replace(',', '.')
    val value = normalized.toDoubleOrNull() ?: return null
    if (value < UserSettings.MIN_PRICE || value > UserSettings.MAX_PRICE) return null
    return UserSettings.normalizePrice(value)
}

@Preview(showBackground = true)
@Composable
private fun SettingsSheetPreview() {
    MartiTakipTheme {
        SettingsSheet(
            visible = true,
            current = UserSettings(),
            onDismiss = {},
            onSave = { _, _, _ -> },
            onHapticsChanged = {},
            onAutoGpsChanged = {},
            onSoundChanged = {},
            onResetToDefaults = {},
            onExportCsv = {},
        )
    }
}

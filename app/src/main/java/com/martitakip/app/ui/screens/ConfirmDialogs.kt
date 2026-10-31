package com.martitakip.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.martitakip.app.ui.TrackerViewModel.Companion.formatMoney

/**
 * Confirmation for restoring an archived shift.
 *
 * Restoring is not destructive, but it does re-open a closed shift, so it
 * deserves a checkpoint: the operator should not do it by accident while
 * scrolling the history list.
 */
@Composable
fun RestoreShiftDialog(
    visible: Boolean,
    shiftId: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Vardiya geri alınsın mı?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "#$shiftId numaralı vardiya arşivden çıkarılıp yeniden " +
                    "aktif vardiya olacak. Değişimler sayaca, günlük hedefe ve " +
                    "haritaya geri döner.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Geri al", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        },
    )
}

/**
 * Confirmation for the irreversible "clear today" action.
 *
 * This one really deletes rows (no archive entry is created), so the wording
 * says so explicitly and the destructive button is coloured accordingly.
 */
@Composable
fun ClearTodayDialog(
    visible: Boolean,
    swapCount: Int,
    earnings: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Bugün silinsin mi?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bugünün $swapCount değişimi (${formatMoney(earnings)}) " +
                    "kalıcı olarak silinecek. Arşive kaydedilmez ve geri alınamaz.\n\n" +
                    "Kayıtları saklamak istiyorsan bunun yerine " +
                    "\"Vardiyayı Kaydet & Sıfırla\" kullan.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Kalıcı olarak sil",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        },
    )
}

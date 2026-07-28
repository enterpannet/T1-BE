package com.getmoney.app.ui.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.ocr.SlipNoteCodec
import com.getmoney.app.ocr.SlipNoteParts
import com.getmoney.app.ocr.TransferDirection
import com.getmoney.app.ui.components.AppDialog
import com.getmoney.app.ui.slip.isValidSlipAmount
import com.getmoney.app.ui.theme.ErrorRed

@Composable
fun EditTransactionDialog(
    transaction: TransactionResponse,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (amount: String, note: String?) -> Unit,
) {
    val initialParts = remember(transaction.id) { SlipNoteCodec.parse(transaction.note) }
    var amount by remember(transaction.id) { mutableStateOf(transaction.amount) }
    var direction by remember(transaction.id) {
        mutableStateOf(initialParts.direction ?: TransferDirection.OUT)
    }
    var fromName by remember(transaction.id) { mutableStateOf(initialParts.fromName.orEmpty()) }
    var toName by remember(transaction.id) { mutableStateOf(initialParts.toName.orEmpty()) }
    var memo by remember(transaction.id) {
        mutableStateOf(
            initialParts.memo.orEmpty().ifBlank {
                SlipNoteCodec.displayTransferLine(initialParts).orEmpty()
            },
        )
    }
    val amountValid = isValidSlipAmount(amount)

    AppDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = "แก้ไขรายการ",
        icon = Icons.Outlined.Edit,
        supportingText = "ปรับยอดและรายละเอียดการโอน",
        dismissOnClickOutside = !saving,
        dismissOnBackPress = !saving,
        primaryLabel = if (saving) "กำลังบันทึก…" else "บันทึก",
        onPrimary = {
            val transferLine = SlipNoteCodec.displayTransferLine(
                SlipNoteParts(
                    direction = direction,
                    fromName = fromName,
                    toName = toName,
                ),
            )
            val memoForStore = memo.trim()
                .takeIf { it.isNotEmpty() }
                ?.takeUnless { it == transferLine }
            onSave(
                amount.trim(),
                SlipNoteCodec.compose(direction, fromName, toName, memoForStore),
            )
        },
        primaryEnabled = !saving && amountValid,
        secondaryLabel = "ยกเลิก",
        onSecondary = { if (!saving) onDismiss() },
    ) {
        Column {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = amount.isNotBlank() && !amountValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ทิศทาง",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == TransferDirection.OUT,
                    onClick = { direction = TransferDirection.OUT },
                    label = { Text("โอนออก") },
                )
                FilterChip(
                    selected = direction == TransferDirection.IN,
                    onClick = { direction = TransferDirection.IN },
                    label = { Text("รับเข้า") },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = fromName,
                onValueChange = { fromName = it },
                label = { Text("จาก (ต้นทาง)") },
                placeholder = { Text("คน / บริษัท / บัญชี ฯลฯ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = toName,
                onValueChange = { toName = it },
                label = { Text("ถึง (ปลายทาง)") },
                placeholder = { Text("คน / บริษัท / บัญชี ฯลฯ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("บันทึกช่วยจำ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
fun DeleteTransactionDialog(
    transaction: TransactionResponse,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val parts = SlipNoteCodec.parse(transaction.note)
    val detail = SlipNoteCodec.displayTransferLine(parts)
        ?: parts.memo
        ?: transaction.source

    AppDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = "ลบรายการ?",
        icon = Icons.Outlined.DeleteOutline,
        iconTint = ErrorRed,
        supportingText = "ลบ ${transaction.amount} ($detail) ออกจากบันทึกของคุณ",
        dismissOnClickOutside = !deleting,
        dismissOnBackPress = !deleting,
        primaryLabel = if (deleting) "กำลังลบ…" else "ลบ",
        onPrimary = onConfirm,
        primaryEnabled = !deleting,
        primaryDestructive = true,
        secondaryLabel = "ยกเลิก",
        onSecondary = { if (!deleting) onDismiss() },
    )
}

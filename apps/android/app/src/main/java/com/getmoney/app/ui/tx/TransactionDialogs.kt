package com.getmoney.app.ui.tx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.ui.slip.isValidSlipAmount
import com.getmoney.app.ui.theme.CarbonButtonDefaults

@Composable
fun EditTransactionDialog(
    transaction: TransactionResponse,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (amount: String, note: String?) -> Unit,
) {
    var amount by remember(transaction.id) { mutableStateOf(transaction.amount) }
    var note by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    val amountValid = isValidSlipAmount(amount)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit transaction") },
        text = {
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        amount.trim(),
                        note.trim().ifBlank { null },
                    )
                },
                enabled = !saving && amountValid,
                shape = MaterialTheme.shapes.small,
                colors = CarbonButtonDefaults.primaryButtonColors(),
                elevation = CarbonButtonDefaults.primaryButtonElevation(),
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeleteTransactionDialog(
    transaction: TransactionResponse,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Delete transaction?") },
        text = {
            Text(
                text = "Remove ${transaction.amount} " +
                    "(${transaction.note ?: transaction.source}) from your records.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !deleting,
                shape = MaterialTheme.shapes.small,
                colors = CarbonButtonDefaults.primaryButtonColors(),
                elevation = CarbonButtonDefaults.primaryButtonElevation(),
            ) {
                Text(if (deleting) "Deleting…" else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text("Cancel")
            }
        },
    )
}

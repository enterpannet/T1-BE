package com.getmoney.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.getmoney.app.data.api.TodaySummaryResponse
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.budget.BudgetRequiredException
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ui.components.CarbonPercentBar
import com.getmoney.app.ui.components.parsePercent
import com.getmoney.app.ui.slip.isValidSlipAmount
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
    onNavigateBudget: () -> Unit,
    onAddSlip: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<TodaySummaryResponse?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun loadSummary() {
        loading = true
        error = null
        budgetRepository.getTodaySummary()
            .onSuccess { summary = it }
            .onFailure { throwable ->
                if (throwable is BudgetRequiredException) {
                    onNavigateBudget()
                } else {
                    error = throwable.message ?: "Failed to load today"
                }
            }
        loading = false
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loadSummary()
        }
    }

    editingTransaction?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            saving = actionInProgress,
            onDismiss = { editingTransaction = null },
            onSave = { amount, note ->
                scope.launch {
                    actionInProgress = true
                    transactionRepository.updateTransaction(
                        transactionId = transaction.id,
                        amount = amount,
                        note = note,
                    ).fold(
                        onSuccess = {
                            editingTransaction = null
                            loadSummary()
                        },
                        onFailure = { error = it.message ?: "Update failed" },
                    )
                    actionInProgress = false
                }
            },
        )
    }

    deletingTransaction?.let { transaction ->
        DeleteTransactionDialog(
            transaction = transaction,
            deleting = actionInProgress,
            onDismiss = { deletingTransaction = null },
            onConfirm = {
                scope.launch {
                    actionInProgress = true
                    transactionRepository.deleteTransaction(transaction.id)
                        .fold(
                            onSuccess = {
                                deletingTransaction = null
                                loadSummary()
                            },
                            onFailure = { error = it.message ?: "Delete failed" },
                        )
                    actionInProgress = false
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))

        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            error != null -> {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            }

            summary != null -> {
                val data = summary!!
                val percent = parsePercent(data.percentUsed)

                Text(
                    text = "Daily allowance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
                Text(
                    text = data.dailyAllowance,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "${formatPercent(percent)} used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (percent > 100.0) ErrorRed else MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CarbonPercentBar(percent = percent)

                Spacer(modifier = Modifier.height(24.dp))

                SummaryRow(label = "Spent today", value = data.spent)
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Remaining today", value = data.remainingToday)

                if (data.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    data.items.forEach { item ->
                        TransactionRow(
                            transaction = item,
                            onEdit = { editingTransaction = item },
                            onDelete = { deletingTransaction = item },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onAddSlip,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text("Add slip")
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note ?: transaction.source,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = transaction.amount,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
        TextButton(onClick = onEdit) {
            Text("Edit")
        }
        TextButton(onClick = onDelete) {
            Text("Delete", color = ErrorRed)
        }
    }
}

@Composable
private fun EditTransactionDialog(
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
private fun DeleteTransactionDialog(
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
                    "(${transaction.note ?: transaction.source}) from today.",
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

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun formatPercent(percent: Double): String {
    val rounded = (percent * 100).toLong() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}%"
    } else {
        "$rounded%"
    }
}

package com.getmoney.app.ui.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ui.theme.ErrorRed
import kotlinx.coroutines.launch

@Composable
fun TransactionsScreen(
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionResponse>>(emptyList()) }
    var editingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun loadTransactions() {
        loading = true
        error = null
        transactionRepository.listTransactions()
            .onSuccess { items ->
                transactions = items.sortedByDescending { it.spentAt }
            }
            .onFailure { error = it.message ?: "Failed to load transactions" }
        loading = false
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loadTransactions()
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
                            loadTransactions()
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
                                slipImageStore.delete(transaction.id)
                                deletingTransaction = null
                                loadTransactions()
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
    ) {
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))

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

            transactions.isEmpty() -> {
                Text(
                    text = "No transactions yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionListItem(
                            transaction = transaction,
                            slipImageStore = slipImageStore,
                            onEdit = { editingTransaction = transaction },
                            onDelete = { deletingTransaction = transaction },
                        )
                    }
                }
            }
        }
    }
}

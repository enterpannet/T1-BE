package com.getmoney.app.ui.tx

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipNoteCodec
import com.getmoney.app.ui.components.AppDialog
import com.getmoney.app.ui.components.IconText
import com.getmoney.app.ui.components.SlipImagePreview
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.util.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TransactionsScreen(
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionResponse>>(emptyList()) }
    var selectedTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val today = remember { LocalDate.now(ZoneId.of("Asia/Bangkok")) }

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

    fun exportCsv() {
        if (transactions.isEmpty() || exporting) return
        scope.launch {
            exporting = true
            val intent = withContext(Dispatchers.IO) {
                TransactionCsvExporter.buildShareIntent(context, transactions)
            }
            if (intent != null) {
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Export CSV"))
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "ไม่มีรายการให้ export", Toast.LENGTH_SHORT).show()
            }
            exporting = false
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loadTransactions()
        }
    }

    val grouped = remember(transactions, today) {
        transactions
            .groupBy { dayKeyForTransaction(it.spentAt) ?: LocalDate.MIN }
            .toList()
            .sortedByDescending { it.first }
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailDialog(
            transaction = transaction,
            slipImageStore = slipImageStore,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                selectedTransaction = null
                editingTransaction = transaction
            },
            onDelete = {
                selectedTransaction = null
                deletingTransaction = transaction
            },
        )
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
        Spacer(modifier = Modifier.height(8.dp))
        if (!loading && error == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "บันทึกแล้ว ${transactions.size} รายการ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { exportCsv() },
                    enabled = transactions.isNotEmpty() && !exporting,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconText(
                            imageVector = Icons.Outlined.FileDownload,
                            text = "Export CSV",
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "แตะรายการเพื่อดูรายละเอียด แก้ไข หรือลบ",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(16.dp))

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
                    text = "ยังไม่มีรายการ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    grouped.forEach { (day, items) ->
                        item(key = "header-$day") {
                            Text(
                                text = dayHeaderLabel(day, today),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(items, key = { it.id }) { transaction ->
                            TransactionListItem(
                                transaction = transaction,
                                slipImageStore = slipImageStore,
                                onClick = { selectedTransaction = transaction },
                            )
                            HorizontalDivider(color = InkMuted.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailDialog(
    transaction: TransactionResponse,
    slipImageStore: SlipImageStore,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val localFile = remember(transaction.id) { slipImageStore.fileFor(transaction.id) }
    val previewData: Any? = localFile ?: transaction.imageUrl?.takeIf { it.isNotBlank() }
    val noteParts = remember(transaction.note) { SlipNoteCodec.parse(transaction.note) }
    val secondary = transactionSecondaryLine(noteParts, transaction.bank)

    AppDialog(
        onDismissRequest = onDismiss,
        title = formatMoney(transaction.amount),
        supportingText = formatSpentAtFull(transaction.spentAt),
        primaryLabel = "แก้ไข",
        onPrimary = onEdit,
        secondaryLabel = "ปิด",
        onSecondary = onDismiss,
        tertiaryLabel = "ลบ",
        onTertiary = onDelete,
        tertiaryDestructive = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (previewData != null) {
                SlipImagePreview(
                    data = previewData,
                    thumbSize = 112.dp,
                    showTapHint = true,
                    enableFullscreenOnTap = true,
                )
            }
            transaction.bank?.takeIf { it.isNotBlank() }?.let {
                Text("ธนาคาร: $it", style = MaterialTheme.typography.bodyMedium)
            }
            secondary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            noteParts.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                Text(
                    text = "บันทึกช่วยจำ: $memo",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

package com.getmoney.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.getmoney.app.data.api.TodaySummaryResponse
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.budget.BudgetRequiredException
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ui.components.CarbonPercentBar
import com.getmoney.app.ui.components.IconText
import com.getmoney.app.ui.components.parsePercent
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.tx.DeleteTransactionDialog
import com.getmoney.app.ui.tx.EditTransactionDialog
import com.getmoney.app.ui.tx.TransactionDetailDialog
import com.getmoney.app.ui.tx.TransactionListItem
import com.getmoney.app.ui.util.formatMoney
import com.getmoney.app.ui.util.formatPercentLabel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
    onNavigateBudget: () -> Unit,
    onAddSlip: () -> Unit,
    onViewAllTransactions: () -> Unit,
    pendingSlipCount: Int = 0,
    onReviewPendingSlips: () -> Unit = {},
    onExportReviewZip: () -> Unit = {},
    zipExporting: Boolean = false,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<TodaySummaryResponse?>(null) }
    var selectedTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionResponse?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

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
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Always visible — not buried under the transaction list
        Button(
            onClick = onAddSlip,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            IconText(
                imageVector = Icons.Outlined.AddAPhoto,
                text = "Add slip",
            )
        }

        // Soft notice — stays after dialog "ภายหลัง" so user can open the queue later
        if (pendingSlipCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "พบสลิปรอตรวจ $pendingSlipCount ใบ — กดดูเลยเมื่อพร้อม หรือส่ง ZIP ให้ช่วยดู",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onReviewPendingSlips) {
                    Text("ดูเลย")
                }
                TextButton(
                    onClick = onExportReviewZip,
                    enabled = !zipExporting,
                ) {
                    Text(if (zipExporting) "กำลังสร้าง ZIP…" else "ส่งออก ZIP")
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        when {
            loading && summary == null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            error != null && summary == null -> {
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
                    text = formatMoney(data.dailyAllowance),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${formatPercentLabel(percent)} used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (percent > 100.0) ErrorRed else MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CarbonPercentBar(percent = percent)

                Spacer(modifier = Modifier.height(20.dp))

                SummaryRow(label = "Spent today", value = data.spent)
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Remaining today", value = data.remainingToday)

                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ล่าสุดวันนี้",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = onViewAllTransactions) {
                        IconText(
                            imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                            text = "ดูทั้งหมด",
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val latest = takeLatestTransactions(data.items)
                if (latest.isEmpty()) {
                    Text(
                        text = "ยังไม่มีรายการวันนี้",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                } else {
                    latest.forEach { item ->
                        TransactionListItem(
                            transaction = item,
                            slipImageStore = slipImageStore,
                            onClick = { selectedTransaction = item },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                    )
                }
            }
        }
    }
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
            text = formatMoney(value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

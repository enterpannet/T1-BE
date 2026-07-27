package com.getmoney.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.getmoney.app.data.api.TodaySummaryResponse
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.budget.BudgetRequiredException
import com.getmoney.app.ui.components.CarbonPercentBar
import com.getmoney.app.ui.components.parsePercent
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted

@Composable
fun HomeScreen(
    budgetRepository: BudgetRepository,
    onNavigateBudget: () -> Unit,
    onAddSlip: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<TodaySummaryResponse?>(null) }
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
                        SummaryRow(
                            label = item.note ?: item.source,
                            value = item.amount,
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

package com.getmoney.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getmoney.app.data.api.MonthSummaryResponse
import com.getmoney.app.data.api.WeekSummaryResponse
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.ui.components.CarbonPercentBar
import com.getmoney.app.ui.components.parsePercent
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted

@Composable
fun SummaryScreen(budgetRepository: BudgetRepository) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Week", "Month")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Summary",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (selectedTab) {
            0 -> WeekSummaryTab(budgetRepository = budgetRepository)
            1 -> MonthSummaryTab(budgetRepository = budgetRepository)
        }
    }
}

@Composable
private fun WeekSummaryTab(budgetRepository: BudgetRepository) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<WeekSummaryResponse?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        budgetRepository.getWeekSummary()
            .onSuccess { summary = it }
            .onFailure { error = it.message }
        loading = false
    }

    SummaryContent(
        loading = loading,
        error = error,
        percent = summary?.percentUsed?.let(::parsePercent),
        rows = summary?.let {
            listOf(
                "Allowance" to it.allowance,
                "Spent" to it.spent,
                "Remaining" to it.remaining,
                "Daily allowance" to it.dailyAllowance,
            )
        },
    )
}

@Composable
private fun MonthSummaryTab(budgetRepository: BudgetRepository) {
    val monthKey = remember { budgetRepository.currentMonthKey() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<MonthSummaryResponse?>(null) }

    LaunchedEffect(monthKey) {
        loading = true
        budgetRepository.getMonthSummary(monthKey)
            .onSuccess { summary = it }
            .onFailure { error = it.message }
        loading = false
    }

    SummaryContent(
        loading = loading,
        error = error,
        percent = summary?.percentOfRemaining?.let(::parsePercent),
        rows = summary?.let {
            listOf(
                "Salary" to it.salary,
                "Fixed total" to it.fixedTotal,
                "Remaining" to it.remaining,
                "Spent (variable)" to it.spentVariable,
                "Daily allowance" to it.dailyAllowance,
            )
        },
    )
}

@Composable
private fun SummaryContent(
    loading: Boolean,
    error: String?,
    percent: Double?,
    rows: List<Pair<String, String>>?,
) {
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
            Text(text = error, color = ErrorRed)
        }

        rows != null && percent != null -> {
            Text(
                text = "${formatPercent(percent)} used",
                style = MaterialTheme.typography.bodyMedium,
                color = if (percent > 100.0) ErrorRed else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            CarbonPercentBar(percent = percent)
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { (label, value) ->
                    SummaryRow(label = label, value = value)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
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

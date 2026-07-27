package com.getmoney.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.getmoney.app.data.api.BudgetMonthResponse
import com.getmoney.app.data.api.FixedExpenseResponse
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch

@Composable
fun BudgetScreen(budgetRepository: BudgetRepository) {
    val monthKey = remember { budgetRepository.currentMonthKey() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var budget by remember { mutableStateOf<BudgetMonthResponse?>(null) }
    var salary by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    suspend fun reload() {
        loading = true
        error = null
        budgetRepository.getBudgetMonth(monthKey)
            .onSuccess {
                budget = it
                salary = it.salary
            }
            .onFailure {
                if (it.message?.contains("404") != true &&
                    it.message != "budget month required"
                ) {
                    error = it.message ?: "Failed to load budget"
                }
            }
        loading = false
    }

    LaunchedEffect(monthKey) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Budget",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = monthKey,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            loading -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            error != null && budget == null -> {
                if (error != null) {
                    Text(text = error!!, color = ErrorRed)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                CarbonMoneyField(
                    value = salary,
                    onValueChange = { salary = it },
                    label = "Monthly salary",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            budgetRepository.updateSalary(monthKey, salary.trim())
                                .onSuccess {
                                    budget = it
                                    error = null
                                }
                                .onFailure { error = it.message }
                            saving = false
                        }
                    },
                    enabled = !saving && salary.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text(if (saving) "Saving…" else "Create budget")
                }
            }

            else -> {
                CarbonMoneyField(
                    value = salary,
                    onValueChange = { salary = it },
                    label = "Monthly salary",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            budgetRepository.updateSalary(monthKey, salary.trim())
                                .onSuccess {
                                    budget = it
                                    error = null
                                }
                                .onFailure { error = it.message }
                            saving = false
                        }
                    },
                    enabled = !saving && salary.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text(if (saving) "Saving…" else "Save salary")
                }

                budget?.let { data ->
                    Spacer(modifier = Modifier.height(24.dp))
                    FiguresBlock(
                        remaining = data.remaining,
                        dailyAllowance = data.dailyAllowance,
                        fixedTotal = data.fixedTotal,
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Fixed expenses",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    data.fixedExpenses.forEach { expense ->
                        FixedExpenseRow(
                            expense = expense,
                            onDelete = {
                                scope.launch {
                                    saving = true
                                    budgetRepository.deleteFixedExpense(monthKey, expense.id)
                                        .onSuccess {
                                            budget = it
                                            error = null
                                        }
                                        .onFailure { error = it.message }
                                    saving = false
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    CarbonMoneyField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = "Expense name",
                        keyboardType = KeyboardType.Text,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CarbonMoneyField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = "Amount",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                budgetRepository.createFixedExpense(
                                    monthKey = monthKey,
                                    name = newName.trim(),
                                    amount = newAmount.trim(),
                                    sortOrder = data.fixedExpenses.size,
                                )
                                    .onSuccess {
                                        budget = it
                                        newName = ""
                                        newAmount = ""
                                        error = null
                                    }
                                    .onFailure { error = it.message }
                                saving = false
                            }
                        },
                        enabled = !saving && newName.isNotBlank() && newAmount.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = CarbonButtonDefaults.primaryButtonColors(),
                        elevation = CarbonButtonDefaults.primaryButtonElevation(),
                    ) {
                        Text("Add fixed expense")
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = error!!, color = ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun FiguresBlock(
    remaining: String,
    dailyAllowance: String,
    fixedTotal: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FigureRow(label = "Fixed total", value = fixedTotal)
        FigureRow(label = "Remaining", value = remaining)
        FigureRow(label = "Daily allowance", value = dailyAllowance)
    }
}

@Composable
private fun FigureRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FixedExpenseRow(
    expense: FixedExpenseResponse,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = expense.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = expense.amount, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        TextButton(onClick = onDelete) {
            Text("Remove", color = ErrorRed)
        }
    }
}

@Composable
private fun CarbonMoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BudgetGoal
import com.example.data.model.BudgetScope
import com.example.data.model.ExpenseCategory
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionType
import com.example.domain.IndianCurrencyAndTime
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PendingAmber

// 15. Budget & Savings Goal Management Screen
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetManagementScreen(
    budgets: List<BudgetGoal>,
    transactions: List<TransactionRecord>,
    categories: List<String>,
    onSaveBudget: (BudgetGoal) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onBack: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val todayStart = IndianCurrencyAndTime.startOfTodayIstMillis(now)
    val weekStart = IndianCurrencyAndTime.startOfWeekIstMillis(now)
    val monthStart = IndianCurrencyAndTime.startOfMonthIstMillis(now)

    val monthIncome = transactions.filter { it.type == TransactionType.CREDIT.name && it.transactionTimestampMillis >= monthStart }.sumOf { it.amount }
    val monthExpense = transactions.filter { it.type == TransactionType.DEBIT.name && it.transactionTimestampMillis >= monthStart }.sumOf { it.amount }
    val monthlySavings = monthIncome - monthExpense

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets & Savings Goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                modifier = Modifier.testTag("add_budget_fab"),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create budget")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Monthly Savings Tracker (${IndianCurrencyAndTime.formatMonthLabelIst(now)})", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = IndianCurrencyAndTime.formatInr(monthlySavings, showSign = true),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (monthlySavings >= 0) CreditGreen else DebitRed
                        )
                        Text(
                            text = "Income: ${IndianCurrencyAndTime.formatInr(monthIncome)} | Spent: ${IndianCurrencyAndTime.formatInr(monthExpense)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (budgets.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No budgets or spending limits configured yet. Tap + to create a monthly budget, category cap, daily/weekly limit, or savings goal.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(budgets, key = { it.id }) { budget ->
                    val spent = when (budget.scope) {
                        BudgetScope.DAILY_LIMIT.name -> transactions.filter {
                            it.type == TransactionType.DEBIT.name && it.transactionTimestampMillis >= todayStart
                        }.sumOf { it.amount }
                        BudgetScope.WEEKLY_LIMIT.name -> transactions.filter {
                            it.type == TransactionType.DEBIT.name && it.transactionTimestampMillis >= weekStart
                        }.sumOf { it.amount }
                        BudgetScope.CATEGORY_MONTHLY.name -> transactions.filter {
                            it.type == TransactionType.DEBIT.name &&
                                it.category == budget.category &&
                                it.transactionTimestampMillis >= monthStart
                        }.sumOf { it.amount }
                        BudgetScope.SAVINGS_GOAL.name -> monthlySavings.coerceAtLeast(0.0) + budget.savedOrTargetAmount
                        else -> monthExpense
                    }
                    val pct = if (budget.limitAmount > 0.0) ((spent / budget.limitAmount) * 100.0).toFloat() else 0f
                    val barColor = when {
                        budget.scope == BudgetScope.SAVINGS_GOAL.name -> CreditGreen
                        pct >= 100f -> DebitRed
                        pct >= budget.alertThresholdPercent -> PendingAmber
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(budget.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = BudgetScope.entries.firstOrNull { it.name == budget.scope }?.label ?: budget.scope,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteBudget(budget.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete budget")
                                }
                            }
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = barColor
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "${IndianCurrencyAndTime.formatInr(spent)} / ${IndianCurrencyAndTime.formatInr(budget.limitAmount)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily)
                                )
                                Text(
                                    text = "${pct.toInt()}% utilized",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = barColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var title by remember { mutableStateOf("") }
        var limitText by remember { mutableStateOf("") }
        var scope by remember { mutableStateOf(BudgetScope.MONTHLY_TOTAL) }
        var category by remember { mutableStateOf(categories.firstOrNull() ?: "Food & Dining") }
        var thresholdText by remember { mutableStateOf("80") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Create Budget / Limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Budget Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Target / Limit Amount (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BudgetScope.entries.forEach { s ->
                            FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
                        }
                    }
                    if (scope == BudgetScope.CATEGORY_MONTHLY) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.take(8).forEach { c ->
                                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("Alert Threshold (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = limitText.replace(",", "").toDoubleOrNull() ?: return@Button
                    if (amt <= 0.0 || title.isBlank()) return@Button
                    val threshold = thresholdText.toIntOrNull()?.coerceIn(1, 100) ?: 80
                    onSaveBudget(
                        BudgetGoal(
                            title = title.trim(),
                            scope = scope.name,
                            category = if (scope == BudgetScope.CATEGORY_MONTHLY) category else "",
                            limitAmount = amt,
                            monthKey = IndianCurrencyAndTime.formatMonthKeyIst(System.currentTimeMillis()),
                            alertThresholdPercent = threshold
                        )
                    )
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// 16. Expense Categories Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesScreen(
    categories: List<ExpenseCategory>,
    onSaveCategory: (ExpenseCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Categories (${categories.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_category_fab"),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add custom category")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                val dotColor = try {
                    Color(android.graphics.Color.parseColor(cat.colorHex))
                } catch (_: Exception) {
                    MaterialTheme.colorScheme.primary
                }
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(dotColor))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = buildString {
                                    append(if (cat.isIncomeCategory) "Income" else "Expense")
                                    append(" • ")
                                    append(if (cat.isCustom) "Custom" else "Standard")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cat.isCustom) {
                            IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete category")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var isIncome by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Income Category?")
                        Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onSaveCategory(
                            ExpenseCategory(
                                name = name.trim(),
                                iconKey = "category",
                                colorHex = if (isIncome) "#10B981" else "#F59E0B",
                                isCustom = true,
                                isIncomeCategory = isIncome
                            )
                        )
                        showAddDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

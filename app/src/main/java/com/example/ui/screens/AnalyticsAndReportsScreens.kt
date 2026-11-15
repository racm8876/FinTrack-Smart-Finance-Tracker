package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TransactionRecord
import com.example.domain.ChartPoint
import com.example.domain.FinancialAnalyticsEngine
import com.example.domain.IndianCurrencyAndTime
import com.example.domain.PeriodComparison
import com.example.domain.StatementParser
import com.example.ui.components.CashflowBarChart
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.MonthlyTrendLineChart
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Calendar

// 17. Financial Reports Hub + Custom Date-Range Report
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsHubScreen(
    transactions: List<TransactionRecord>,
    onOpenDaily: () -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenMonthly: () -> Unit,
    onOpenYearly: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startDateText by remember { mutableStateOf(IndianCurrencyAndTime.formatDateIst(IndianCurrencyAndTime.startOfMonthIstMillis())) }
    var endDateText by remember { mutableStateOf(IndianCurrencyAndTime.formatDateIst(System.currentTimeMillis())) }

    val customRangeReport = remember(transactions, startDateText, endDateText) {
        val s = StatementParser.parseIndianDateToMillis(startDateText) ?: IndianCurrencyAndTime.startOfMonthIstMillis()
        val e = (StatementParser.parseIndianDateToMillis(endDateText) ?: System.currentTimeMillis()) + 86_399_000L
        FinancialAnalyticsEngine.computePeriodReport(transactions, s, e)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics") },
                actions = {
                    IconButton(onClick = onOpenExport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export reports")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Standard Analytics Reports", style = MaterialTheme.typography.titleMedium)
            }
            item {
                ReportLinkCard("Daily Report", "Today's income, expenses, transfers & person spending", Icons.Default.CalendarToday, onOpenDaily, "open_daily_report")
            }
            item {
                ReportLinkCard("Weekly Report", "7-day spending comparison & frequent recipients", Icons.Default.DateRange, onOpenWeekly, "open_weekly_report")
            }
            item {
                ReportLinkCard("Monthly Report", "Savings, category breakdown & month-over-month delta", Icons.Default.CalendarMonth, onOpenMonthly, "open_monthly_report")
            }
            item {
                ReportLinkCard("Yearly Report", "Annual savings, 12-month trend & top categories", Icons.Default.EventNote, onOpenYearly, "open_yearly_report")
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenBudgets),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Budgets & Goals", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Limits & Alerts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenCategories),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Categories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("17+ Custom Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Custom Date-Range Report (IST)", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = startDateText,
                                onValueChange = { startDateText = it },
                                label = { Text("Start (yyyy-MM-dd)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endDateText,
                                onValueChange = { endDateText = it },
                                label = { Text("End (yyyy-MM-dd)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PeriodSummaryCard(report = customRangeReport)
                        CategoryDonutChart(breakdown = customRangeReport.categoryBreakdown)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLinkCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

// 11. Daily Analytics Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAnalyticsScreen(
    transactions: List<TransactionRecord>,
    onBack: () -> Unit
) {
    val now = System.currentTimeMillis()
    val start = IndianCurrencyAndTime.startOfTodayIstMillis(now)
    val report = remember(transactions) { FinancialAnalyticsEngine.computePeriodReport(transactions, start, now) }

    PeriodReportScaffold(
        title = "Daily Analytics (${IndianCurrencyAndTime.formatReadableDateIst(now)})",
        report = report,
        onBack = onBack
    )
}

// 12. Weekly Analytics Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyAnalyticsScreen(
    transactions: List<TransactionRecord>,
    onBack: () -> Unit
) {
    val now = System.currentTimeMillis()
    val start = IndianCurrencyAndTime.startOfWeekIstMillis(now)
    val report = remember(transactions) { FinancialAnalyticsEngine.computePeriodReport(transactions, start, now) }
    val trend = remember(transactions) { FinancialAnalyticsEngine.computeLast7DaysTrend(transactions, now) }

    PeriodReportScaffold(
        title = "Weekly Analytics",
        report = report,
        onBack = onBack,
        chartContent = {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Spending Comparison", style = MaterialTheme.typography.titleMedium)
                    CashflowBarChart(points = trend)
                }
            }
        }
    )
}

// 13. Monthly Analytics Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyAnalyticsScreen(
    transactions: List<TransactionRecord>,
    onBack: () -> Unit
) {
    val now = System.currentTimeMillis()
    val start = IndianCurrencyAndTime.startOfMonthIstMillis(now)
    val report = remember(transactions) { FinancialAnalyticsEngine.computePeriodReport(transactions, start, now) }

    val prevMonthCal = Calendar.getInstance(IndianCurrencyAndTime.istTimeZone).apply {
        timeInMillis = start
        add(Calendar.MONTH, -1)
    }
    val prevStart = prevMonthCal.timeInMillis
    val prevEnd = start - 1L
    val prevReport = remember(transactions) { FinancialAnalyticsEngine.computePeriodReport(transactions, prevStart, prevEnd) }

    PeriodReportScaffold(
        title = "Monthly Analytics (${IndianCurrencyAndTime.formatMonthLabelIst(now)})",
        report = report,
        onBack = onBack,
        chartContent = {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Month-over-Month Comparison", style = MaterialTheme.typography.titleMedium)
                    val delta = report.expenses - prevReport.expenses
                    Text(
                        text = "Previous Month Expenses: ${IndianCurrencyAndTime.formatInr(prevReport.expenses)} | Difference: ${IndianCurrencyAndTime.formatInr(delta, showSign = true)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CategoryDonutChart(breakdown = report.categoryBreakdown)
                }
            }
        }
    )
}

// 14. Yearly Analytics Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyAnalyticsScreen(
    transactions: List<TransactionRecord>,
    onBack: () -> Unit
) {
    val now = System.currentTimeMillis()
    val start = IndianCurrencyAndTime.startOfYearIstMillis(now)
    val cal = Calendar.getInstance(IndianCurrencyAndTime.istTimeZone).apply { timeInMillis = now }
    val year = cal.get(Calendar.YEAR)
    val report = remember(transactions) { FinancialAnalyticsEngine.computePeriodReport(transactions, start, now) }
    val monthlyPoints = remember(transactions, year) { FinancialAnalyticsEngine.computeMonthlyTrendForYear(transactions, year) }

    PeriodReportScaffold(
        title = "Yearly Analytics ($year)",
        report = report,
        onBack = onBack,
        chartContent = {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Monthly Spending Trend ($year)", style = MaterialTheme.typography.titleMedium)
                    MonthlyTrendLineChart(points = monthlyPoints)
                    CategoryDonutChart(breakdown = report.categoryBreakdown)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodReportScaffold(
    title: String,
    report: PeriodComparison,
    onBack: () -> Unit,
    chartContent: (@Composable () -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PeriodSummaryCard(report) }
            if (chartContent != null) {
                item { chartContent() }
            }
            item {
                Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
            }
            if (report.categoryBreakdown.isEmpty()) {
                item {
                    Text("No expense transactions in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(report.categoryBreakdown) { cat ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(cat.category, style = MaterialTheme.typography.titleMedium)
                                Text("${cat.count} txn(s) • ${cat.percentage.toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = IndianCurrencyAndTime.formatInr(cat.amount),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = DebitRed
                            )
                        }
                    }
                }
            }
            item {
                Text("Top Recipients / People", style = MaterialTheme.typography.titleMedium)
            }
            if (report.topPeople.isEmpty()) {
                item {
                    Text("No recipients in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(report.topPeople) { person ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${person.transactionCount} txn(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = IndianCurrencyAndTime.formatInr(person.totalSent + person.totalReceived),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSummaryCard(report: PeriodComparison) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        IndianCurrencyAndTime.formatInr(report.income),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = CreditGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        IndianCurrencyAndTime.formatInr(report.expenses),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = DebitRed
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Net Savings: ${IndianCurrencyAndTime.formatInr(report.savings, showSign = true)}", style = MaterialTheme.typography.bodyMedium)
                Text("Transfers: ${IndianCurrencyAndTime.formatInr(report.transfers)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Transactions: ${report.transactionCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserSecurityPreferences
import com.example.data.model.DashboardMetrics
import com.example.data.model.TransactionRecord
import com.example.domain.FinancialAnalyticsEngine
import com.example.domain.IndianCurrencyAndTime
import com.example.ui.components.CashflowBarChart
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.TransactionListItem
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PendingAmber

@Composable
fun DashboardScreen(
    metrics: DashboardMetrics,
    transactions: List<TransactionRecord>,
    unreadAlertCount: Int,
    preferences: UserSecurityPreferences,
    onAddManualClick: () -> Unit,
    onPasteSmsClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransactionDetail: (String) -> Unit,
    onOpenReports: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val last7Days = FinancialAnalyticsEngine.computeLast7DaysTrend(transactions)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("FinTrack", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (preferences.lastSyncMillis > 0L) {
                                "Synced ${IndianCurrencyAndTime.formatReadableDateTimeIst(preferences.lastSyncMillis)}"
                            } else {
                                "Ready • Authorized Sources Only"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSyncNowClick,
                            modifier = Modifier.testTag("dashboard_sync_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync now")
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("dashboard_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadAlertCount > 0) {
                                        Badge { Text(unreadAlertCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        }
                        IconButton(
                            onClick = onOpenSecurity,
                            modifier = Modifier.testTag("dashboard_security_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Security settings")
                        }
                    }
                }
            }

            // Hero Balance & Cashflow Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0B5D51),
                                    Color(0xFF0D3B36)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "VERIFIED ACCOUNT BALANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA7F3D0)
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable(onClick = onOpenAccounts)
                            ) {
                                Text(
                                    text = if (metrics.hasVerifiedBalanceAccounts) "Verified" else "Connect Account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (metrics.hasVerifiedBalanceAccounts && metrics.verifiedBalance != null) {
                            Text(
                                text = IndianCurrencyAndTime.formatInr(metrics.verifiedBalance),
                                style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFamily),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Balance Unavailable",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Connect an Account Aggregator (AA) or Bank API account with real balance support. Net recorded cashflow: ${IndianCurrencyAndTime.formatInr(metrics.totalIncome - metrics.totalExpenses, showSign = true)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD1FAE5)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeroMetricPill(
                                label = "Total Income",
                                value = IndianCurrencyAndTime.formatInr(metrics.totalIncome),
                                accent = Color(0xFF34D399),
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricPill(
                                label = "Total Expenses",
                                value = IndianCurrencyAndTime.formatInr(metrics.totalExpenses),
                                accent = Color(0xFFFCA5A5),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Quick Actions Row
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        QuickActionChip(Icons.Default.UploadFile, "Import Statement", onOpenAccounts)
                    }
                    item {
                        QuickActionChip(Icons.Default.Sms, "Parse Alert SMS", onPasteSmsClick)
                    }
                    item {
                        QuickActionChip(Icons.Default.AccountBalance, "Permissions", onOpenPermissions)
                    }
                    item {
                        QuickActionChip(Icons.Default.Savings, "Budgets", onOpenBudgets)
                    }
                    item {
                        QuickActionChip(Icons.Default.People, "People", onOpenPeople)
                    }
                    item {
                        QuickActionChip(Icons.Default.FileDownload, "Export", onOpenExport)
                    }
                }
            }

            // Period Spending Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile("Today", IndianCurrencyAndTime.formatInr(metrics.todaySpending), Modifier.weight(1f))
                        MetricTile("This Week", IndianCurrencyAndTime.formatInr(metrics.weeklySpending), Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile("This Month", IndianCurrencyAndTime.formatInr(metrics.monthlySpending), Modifier.weight(1f))
                        MetricTile("This Year", IndianCurrencyAndTime.formatInr(metrics.yearlySpending), Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile("Transferred", IndianCurrencyAndTime.formatInr(metrics.totalTransferred), Modifier.weight(1f), DebitRed)
                        MetricTile("Received", IndianCurrencyAndTime.formatInr(metrics.totalReceived), Modifier.weight(1f), CreditGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile("Total Txns", metrics.transactionCount.toString(), Modifier.weight(1f))
                        MetricTile("Pending", metrics.pendingCount.toString(), Modifier.weight(1f), PendingAmber)
                        MetricTile("Failed", metrics.failedCount.toString(), Modifier.weight(1f), DebitRed)
                    }
                }
            }

            // Interactive 7-Day Cashflow Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("7-Day Cashflow (IST)", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onOpenReports) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Full Reports")
                            }
                        }
                        CashflowBarChart(points = last7Days)
                    }
                }
            }

            // Recent Transactions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = onOpenTransactions,
                        modifier = Modifier.testTag("view_all_transactions_button")
                    ) {
                        Text("See All")
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        title = "No Verified Transactions Yet",
                        subtitle = "FinTrack never fabricates demo records. Import a bank statement (CSV/XLSX/PDF), paste an alert SMS, enable Notification Listener, or record a transaction.",
                        actionLabel = "Add or Import Transaction",
                        onAction = onOpenAccounts
                    )
                }
            } else {
                items(transactions.take(8), key = { it.id }) { tx ->
                    TransactionListItem(
                        transaction = tx,
                        onClick = { onOpenTransactionDetail(tx.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddManualClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("dashboard_add_tx_fab"),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add manual transaction")
        }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFD1FAE5))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.ThemeMode
import com.example.data.local.UserSecurityPreferences
import com.example.data.model.ConsentRecord
import com.example.data.model.ConsentScope
import com.example.data.model.FinanceAlert
import com.example.data.model.SyncAuditLog
import com.example.data.model.TransactionRecord
import com.example.domain.ExportFormat
import com.example.domain.IndianCurrencyAndTime
import kotlinx.coroutines.launch
import java.io.File

// 18. Notifications & Preferences Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    alerts: List<FinanceAlert>,
    prefs: UserSecurityPreferences,
    onMarkRead: (String) -> Unit,
    onSavePrefs: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Double) -> Unit,
    onBack: () -> Unit
) {
    var showPrefs by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications (${alerts.count { !it.isRead }})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showPrefs = !showPrefs }) {
                        Text(if (showPrefs) "Alerts Feed" else "Preferences")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showPrefs) {
            var newTx by remember(prefs) { mutableStateOf(prefs.notifyNewTransaction) }
            var largeTx by remember(prefs) { mutableStateOf(prefs.notifyLargeTransaction) }
            var budget by remember(prefs) { mutableStateOf(prefs.notifyBudgetExceeded) }
            var syncFail by remember(prefs) { mutableStateOf(prefs.notifySyncFailures) }
            var dup by remember(prefs) { mutableStateOf(prefs.notifyDuplicateDetected) }
            var monthly by remember(prefs) { mutableStateOf(prefs.notifyMonthlySummary) }
            var threshold by remember(prefs) { mutableStateOf(prefs.largeTransactionThreshold.toInt().toString()) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ToggleRow("New Detected Transactions", newTx) { newTx = it }
                    ToggleRow("Large Transaction Alerts", largeTx) { largeTx = it }
                    ToggleRow("Budget Limit Exceeded", budget) { budget = it }
                    ToggleRow("Sync Failures", syncFail) { syncFail = it }
                    ToggleRow("Duplicate Detection", dup) { dup = it }
                    ToggleRow("Monthly Financial Summary", monthly) { monthly = it }
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text("Large Transaction Threshold (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSavePrefs(
                                newTx, largeTx, budget, syncFail, dup, monthly,
                                threshold.toDoubleOrNull() ?: 5000.0
                            )
                            showPrefs = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Notification Preferences") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (alerts.isEmpty()) {
                    item {
                        Text(
                            "No alerts recorded yet. Alerts appear automatically when new transactions, budget warnings, or sync events occur.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(alerts, key = { it.id }) { alert ->
                        Surface(
                            color = if (alert.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onMarkRead(alert.id) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${alert.alertType} • ${IndianCurrencyAndTime.formatReadableDateTimeIst(alert.timestampMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 19. Data Export Screen (.xlsx, .csv, .pdf)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataExportScreen(
    transactions: List<TransactionRecord>,
    categories: List<String>,
    accounts: List<String>,
    onExport: suspend (ExportFormat, List<TransactionRecord>, String) -> Result<File>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFormat by remember { mutableStateOf(ExportFormat.XLSX) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    val filteredRecords = remember(transactions, selectedCategory, selectedAccount) {
        transactions.filter { tx ->
            (selectedCategory == null || tx.category == selectedCategory) &&
                (selectedAccount == null || tx.bankAccountName == selectedAccount)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Financial Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select Export Format", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt.label) }
                                )
                            }
                        }

                        Text("Scope by Category (optional)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All Categories") })
                            categories.take(8).forEach { cat ->
                                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
                            }
                        }

                        if (accounts.isNotEmpty()) {
                            Text("Scope by Account (optional)", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = selectedAccount == null, onClick = { selectedAccount = null }, label = { Text("All Accounts") })
                                accounts.forEach { acc ->
                                    FilterChip(selected = selectedAccount == acc, onClick = { selectedAccount = acc }, label = { Text(acc) })
                                }
                            }
                        }

                        Text(
                            "Records included: ${filteredRecords.size} (IST timestamps, Indian Rupee amounts, full UTR & source traceability)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    val res = onExport(
                                        selectedFormat,
                                        filteredRecords,
                                        selectedCategory ?: selectedAccount ?: "Full Ledger"
                                    )
                                    res.onSuccess { file ->
                                        exportStatus = "Generated ${file.name} (${file.length()} bytes)"
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = selectedFormat.mimeType
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Save or Share ${selectedFormat.label}"))
                                    }.onFailure {
                                        exportStatus = "Export failed: ${it.localizedMessage}"
                                    }
                                }
                            },
                            enabled = filteredRecords.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("export_generate_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Share ${selectedFormat.label}")
                        }

                        if (exportStatus != null) {
                            Text(exportStatus!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// 20. Security Settings + Sync Logs Screen
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecuritySettingsScreen(
    prefs: UserSecurityPreferences,
    syncLogs: List<SyncAuditLog>,
    onToggleBiometric: (Boolean) -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAccountMgmt: () -> Unit,
    onBack: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = prefs.themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("App Lock & Credential Safety", style = MaterialTheme.typography.titleMedium)
                        ToggleRow("Biometric Authentication", prefs.biometricLockEnabled, onToggleBiometric)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showPinDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (prefs.pinHash.isBlank()) "Set 4-Digit PIN" else "Change PIN")
                            }
                            if (prefs.pinHash.isNotBlank()) {
                                OutlinedButton(onClick = onClearPin) { Text("Remove PIN") }
                            }
                        }
                        Text(
                            "Security Guarantee: FinTrack never stores UPI PINs, OTPs, banking passwords, or card CVVs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy")
                    }
                    OutlinedButton(onClick = onOpenAccountMgmt, modifier = Modifier.weight(1f)) {
                        Text("Account & Sync")
                    }
                }
            }

            item {
                Text("Synchronization & Audit Logs (${syncLogs.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(syncLogs.take(25), key = { it.id }) { log ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${log.source} • ${log.status}", style = MaterialTheme.typography.titleMedium)
                        Text(log.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Processed: ${log.recordsProcessed} | Duplicates skipped: ${log.duplicatesSkipped} | ${IndianCurrencyAndTime.formatReadableDateTimeIst(log.timestampMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set Security PIN") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("4-6 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (pin.length >= 4) {
                        onSetPin(pin)
                        showPinDialog = false
                    }
                }) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// 21. Privacy Settings & Consent Records Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    consents: List<ConsentRecord>,
    onUpdateConsent: (ConsentScope, Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Consents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "FinTrack processes financial records strictly under your explicit, revocable consent in compliance with Indian DPDP Act, RBI AA, and Google Play policies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(ConsentScope.entries) { scope ->
                val record = consents.firstOrNull { it.scope == scope.name }
                val granted = record?.granted ?: false
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleRow(scope.title, granted) { onUpdateConsent(scope, it) }
                        Text(scope.defaultPurpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (record != null && record.updatedAtMillis > 0L) {
                            Text(
                                "Last updated: ${IndianCurrencyAndTime.formatReadableDateTimeIst(record.updatedAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// 22. Account Management Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    userEmail: String,
    userName: String,
    userId: String,
    prefs: UserSecurityPreferences,
    onForceSync: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAllData: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(userName.ifBlank { "FinTrack Member" }, style = MaterialTheme.typography.titleLarge)
                    Text(userEmail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text("UID: $userId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Currency: INR (₹) • Timezone: IST (Asia/Kolkata)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (prefs.lastSyncMillis > 0L) {
                            "Last Cloud Sync: ${IndianCurrencyAndTime.formatReadableDateTimeIst(prefs.lastSyncMillis)} (${prefs.lastSyncSource})"
                        } else {
                            "Last Cloud Sync: Real-time Firestore active"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = onForceSync, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Trigger Background Sync Now")
            }

            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("sign_out_button")) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("delete_account_data_button")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Financial Data & Reset Vault")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete All Financial Data?") },
            text = {
                Text("This permanently wipes your local encrypted database, Cloud Firestore transactions, accounts, budgets, and consent records, then signs you out.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

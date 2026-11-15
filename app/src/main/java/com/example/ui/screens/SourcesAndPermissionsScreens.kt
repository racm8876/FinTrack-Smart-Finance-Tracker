package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AccountType
import com.example.data.model.ConsentRecord
import com.example.data.model.ConsentScope
import com.example.data.model.FinancialAccount
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionSource
import com.example.data.remote.AccountAggregatorClient
import com.example.data.remote.IntegrationHealth
import com.example.service.BiometricAuthHelper
import com.example.service.FinTrackNotificationListenerService
import com.example.service.SmsTransactionReader
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.PendingAmber

// 4. Permission Management Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagementScreen(
    consents: List<ConsentRecord>,
    onUpdateConsent: (ConsentScope, Boolean) -> Unit,
    onScanSmsInbox: () -> Unit,
    onPasteSmsClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var notifAccessGranted by remember {
        mutableStateOf(FinTrackNotificationListenerService.isNotificationAccessGranted(context))
    }
    var smsGranted by remember { mutableStateOf(SmsTransactionReader.hasReadSmsPermission(context)) }
    var postNotifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsGranted = granted
        onUpdateConsent(ConsentScope.SMS_PARSING, granted)
        if (granted) onScanSmsInbox()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        postNotifGranted = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Collection") },
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
            item {
                PermissionCard(
                    title = "Android Notification Listener",
                    description = "Captures real-time UPI & banking notifications from GPay, PhonePe, Paytm, BHIM, and bank apps locally with zero database scraping.",
                    granted = notifAccessGranted,
                    buttonLabel = if (notifAccessGranted) "Refresh Status" else "Open Notification Access",
                    onAction = {
                        notifAccessGranted = FinTrackNotificationListenerService.isNotificationAccessGranted(context)
                        if (!notifAccessGranted) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else {
                            onUpdateConsent(ConsentScope.NOTIFICATION_LISTENER, true)
                        }
                    }
                )
            }

            item {
                PermissionCard(
                    title = "Transaction SMS Inbox Parsing (Optional)",
                    description = "Google Play Policy Notice: READ_SMS is strictly optional and only queried on user consent. Alternatively, paste or share any SMS directly without granting SMS permissions.",
                    granted = smsGranted,
                    buttonLabel = if (smsGranted) "Scan Bank SMS Now" else "Grant SMS Consent",
                    secondaryLabel = "Paste SMS Without Permission",
                    onAction = {
                        if (smsGranted) {
                            onScanSmsInbox()
                        } else {
                            smsLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                    onSecondaryAction = onPasteSmsClick
                )
            }

            item {
                PermissionCard(
                    title = "Transaction & Budget Alerts",
                    description = "Allows FinTrack to surface local notifications when budgets exceed thresholds or large transactions are detected.",
                    granted = postNotifGranted,
                    buttonLabel = if (postNotifGranted) "Notifications Active" else "Allow Notifications",
                    onAction = {
                        if (!postNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            item {
                val bioReady = BiometricAuthHelper.isBiometricAvailable(context)
                PermissionCard(
                    title = "Biometric Hardware Status",
                    description = "Used with Android BiometricPrompt to protect sensitive financial balances and exports.",
                    granted = bioReady,
                    buttonLabel = if (bioReady) "Hardware Ready" else "Configure in Device Settings",
                    onAction = {}
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    buttonLabel: String,
    secondaryLabel: String? = null,
    onAction: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) CreditGreen else PendingAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
            if (secondaryLabel != null && onSecondaryAction != null) {
                OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

// 5. Financial Account Connections & Statement Import Screen
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinancialAccountConnectionsScreen(
    accounts: List<FinancialAccount>,
    onUpsertAccount: (FinancialAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onImportStatementUri: (android.net.Uri, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var importAccountName by remember { mutableStateOf("HDFC Savings") }
    var importMaskedId by remember { mutableStateOf("XXXX0000") }

    val aaHealth = remember { AccountAggregatorClient.checkConfiguration() }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName = "statement.csv"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) {
                    displayName = cursor.getString(idx) ?: displayName
                }
            }
            onImportStatementUri(uri, displayName, importAccountName, importMaskedId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts & Data Sources") },
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
                modifier = Modifier.testTag("add_account_fab"),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
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
            // RBI Account Aggregator (AA) Status Card
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RBI Account Aggregator (ReBIT AA v2.0)", style = MaterialTheme.typography.titleMedium)
                        }
                        when (aaHealth) {
                            is IntegrationHealth.Configured -> {
                                Text(
                                    "AA FIU endpoint configured. Ready for user-consented FIP balance & statement fetch.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CreditGreen
                                )
                            }
                            is IntegrationHealth.SetupRequired -> {
                                Text(
                                    "Status: SETUP REQUIRED — ${aaHealth.instructions}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PendingAmber
                                )
                                Text(
                                    "Missing Secrets: ${aaHealth.missingKeys.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Statement Import Card (CSV, Excel .xlsx, PDF) via Storage Access Framework
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Bank Statement (CSV, Excel, PDF)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Select an official bank statement via Android's zero-permission Storage Access Framework. FinTrack parses Indian bank formats and prevents duplicates automatically.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = importAccountName,
                                onValueChange = { importAccountName = it },
                                label = { Text("Bank Account Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = importMaskedId,
                                onValueChange = { importMaskedId = it },
                                label = { Text("Masked A/c") },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                        }
                        Button(
                            onClick = {
                                documentLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("import_statement_button")
                        ) {
                            Text("Choose CSV / Excel (.xlsx) / PDF Statement")
                        }
                    }
                }
            }

            item {
                Text("Connected Accounts (${accounts.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (accounts.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No accounts linked yet. Tap + to add a bank account, card, or UPI handle for statement tracking.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.accountName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${acc.institutionName} • ${acc.maskedIdentifier}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Source: ${acc.sourceType} • Status: ${acc.syncStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteAccount(acc.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove account")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var accName by remember { mutableStateOf("") }
        var instName by remember { mutableStateOf("") }
        var masked by remember { mutableStateOf("") }
        var upiHandle by remember { mutableStateOf("") }
        var accType by remember { mutableStateOf(AccountType.SAVINGS) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Financial Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = accName, onValueChange = { accName = it }, label = { Text("Account Nickname *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = instName, onValueChange = { instName = it }, label = { Text("Bank / Provider *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = masked, onValueChange = { masked = it }, label = { Text("Masked Identifier (e.g. XXXX1234) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = upiHandle, onValueChange = { upiHandle = it }, label = { Text("UPI ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountType.entries.forEach { t ->
                            FilterChip(selected = accType == t, onClick = { accType = t }, label = { Text(t.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (accName.isNotBlank() && instName.isNotBlank() && masked.isNotBlank()) {
                        onUpsertAccount(
                            FinancialAccount(
                                accountName = accName.trim(),
                                institutionName = instName.trim(),
                                accountType = accType.name,
                                maskedIdentifier = masked.trim(),
                                upiId = upiHandle.trim(),
                                sourceType = TransactionSource.STATEMENT_CSV.name,
                                syncStatus = SyncStatus.MANUAL_ONLY.name,
                                supportsRealBalance = false
                            )
                        )
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

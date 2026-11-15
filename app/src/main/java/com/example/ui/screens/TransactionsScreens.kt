package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PaymentApp
import com.example.data.model.TransactionAuditEntry
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.domain.IndianCurrencyAndTime
import com.example.domain.SmartSearchResult
import com.example.domain.TransactionFilterCriteria
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.TransactionListItem
import com.example.ui.components.formatAppLabel
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.JetBrainsMonoFamily

// 7. All Transactions Screen with Smart Natural Language Search + Multi-Facet Filters
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllTransactionsScreen(
    searchResult: SmartSearchResult,
    filter: TransactionFilterCriteria,
    categories: List<String>,
    accounts: List<String>,
    onFilterChange: ((TransactionFilterCriteria) -> TransactionFilterCriteria) -> Unit,
    onClearFilters: () -> Unit,
    onOpenTransactionDetail: (String) -> Unit,
    onAddManualClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterDialog by remember { mutableStateOf(false) }

    val quickSuggestions = listOf(
        "Show all transactions above ₹5,000",
        "Show all PhonePe transactions from last week",
        "How much did I spend yesterday?",
        "Show all transactions made through Google Pay"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Transactions (${searchResult.matchedTransactions.size})") },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }, modifier = Modifier.testTag("tx_filter_button")) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter transactions")
                    }
                    IconButton(onClick = onExportClick, modifier = Modifier.testTag("tx_export_button")) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export transactions")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddManualClick,
                modifier = Modifier.testTag("all_tx_add_fab"),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { q -> onFilterChange { it.copy(query = q) } },
                placeholder = { Text("Search name, UPI ID, ₹ amount, UTR, or ask a question...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (filter.query.isNotBlank()) {
                        IconButton(onClick = { onFilterChange { it.copy(query = "") } }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("smart_search_input")
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickSuggestions) { prompt ->
                    FilterChip(
                        selected = filter.query == prompt,
                        onClick = { onFilterChange { it.copy(query = prompt) } },
                        label = { Text(prompt, maxLines = 1) }
                    )
                }
            }

            if (searchResult.answerBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = searchResult.answerBanner,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            val hasActiveFilters = filter.type != null || filter.paymentApp != null ||
                filter.category != null || filter.minAmount != null || filter.bankAccount != null
            if (hasActiveFilters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active filters applied",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onClearFilters) { Text("Reset Filters") }
                }
            }

            if (searchResult.matchedTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    EmptyLedgerCard(
                        title = "No Matching Transactions",
                        subtitle = "Adjust your smart search query or record a transaction from an authorized source.",
                        actionLabel = "Add Transaction",
                        onAction = onAddManualClick
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(searchResult.matchedTransactions, key = { it.id }) { tx ->
                        TransactionListItem(
                            transaction = tx,
                            onClick = { onOpenTransactionDetail(tx.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        var minAmtText by remember { mutableStateOf(filter.minAmount?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Transactions") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "All", TransactionType.DEBIT.name to "Debit", TransactionType.CREDIT.name to "Credit").forEach { (v, lbl) ->
                            FilterChip(
                                selected = filter.type == v,
                                onClick = { onFilterChange { it.copy(type = v) } },
                                label = { Text(lbl) }
                            )
                        }
                    }

                    Text("Payment App", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filter.paymentApp == null,
                            onClick = { onFilterChange { it.copy(paymentApp = null) } },
                            label = { Text("Any") }
                        )
                        listOf(PaymentApp.GOOGLE_PAY, PaymentApp.PHONEPE, PaymentApp.PAYTM, PaymentApp.BHIM_UPI, PaymentApp.BANK_APP).forEach { app ->
                            FilterChip(
                                selected = filter.paymentApp == app.name,
                                onClick = { onFilterChange { it.copy(paymentApp = app.name) } },
                                label = { Text(app.label) }
                            )
                        }
                    }

                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter.status == null, onClick = { onFilterChange { it.copy(status = null) } }, label = { Text("All") })
                        TransactionStatus.entries.forEach { st ->
                            FilterChip(
                                selected = filter.status == st.name,
                                onClick = { onFilterChange { it.copy(status = st.name) } },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    if (categories.isNotEmpty()) {
                        Text("Category", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = filter.category == null, onClick = { onFilterChange { it.copy(category = null) } }, label = { Text("All") })
                            categories.take(10).forEach { cat ->
                                FilterChip(
                                    selected = filter.category == cat,
                                    onClick = { onFilterChange { it.copy(category = cat) } },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }

                    if (accounts.isNotEmpty()) {
                        Text("Bank Account", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = filter.bankAccount == null, onClick = { onFilterChange { it.copy(bankAccount = null) } }, label = { Text("All") })
                            accounts.forEach { acc ->
                                FilterChip(
                                    selected = filter.bankAccount == acc,
                                    onClick = { onFilterChange { it.copy(bankAccount = acc) } },
                                    label = { Text(acc) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = minAmtText,
                        onValueChange = { minAmtText = it },
                        label = { Text("Minimum Amount (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minAmt = minAmtText.replace(",", "").toDoubleOrNull()
                    onFilterChange { it.copy(minAmount = minAmt) }
                    showFilterDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onClearFilters()
                    showFilterDialog = false
                }) { Text("Reset") }
            }
        )
    }
}

// 8. Transaction Details Screen (Complete 20-Field Audit View + Edit Category/Notes + Audit Trail)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailsScreen(
    transaction: TransactionRecord?,
    audits: List<TransactionAuditEntry>,
    categories: List<String>,
    onBack: () -> Unit,
    onUpdateMeta: (TransactionRecord, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (transaction != null) {
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.testTag("edit_tx_button")) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit category or notes")
                        }
                        IconButton(onClick = {
                            onDelete(transaction.id)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete transaction")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (transaction == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Transaction record not found.")
            }
            return@Scaffold
        }

        val isCredit = transaction.type == TransactionType.CREDIT.name
        val amtColor = if (isCredit) CreditGreen else DebitRed

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(transaction.status)
                    Text(
                        text = IndianCurrencyAndTime.formatInr(if (isCredit) transaction.amount else -transaction.amount, showSign = true),
                        style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = amtColor
                    )
                    Text(
                        text = transaction.counterpartyName.ifBlank { "Unknown Party" },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = IndianCurrencyAndTime.formatReadableDateTimeIst(transaction.transactionTimestampMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Complete Financial Traceability", style = MaterialTheme.typography.titleMedium)
                    DetailRow("Transaction ID", transaction.id)
                    DetailRow("Date (IST)", transaction.dateIst)
                    DetailRow("Time (IST)", transaction.timeIst)
                    DetailRow("Transaction Type", transaction.type)
                    DetailRow("Sender Name", transaction.senderName.ifBlank { "Unavailable" })
                    DetailRow("Receiver Name", transaction.receiverName.ifBlank { "Unavailable" })
                    DetailRow("Recipient Mobile", transaction.recipientMobile.ifBlank { "Unavailable" })
                    DetailRow("UPI ID / VPA", transaction.upiId.ifBlank { "Unavailable" })
                    DetailRow("Bank Account", transaction.bankAccountName.ifBlank { "Unavailable" })
                    DetailRow("Account Identifier", transaction.maskedAccountIdentifier.ifBlank { "Unavailable" })
                    DetailRow("Payment Application", formatAppLabel(transaction.paymentApp))
                    DetailRow("Payment Method", transaction.paymentMethod)
                    DetailRow("Reference Number", transaction.referenceNumber.ifBlank { "Unavailable" })
                    DetailRow("UTR / UPI Ref ID", transaction.utrOrUpiRef.ifBlank { "Unavailable" })
                    DetailRow("Category", transaction.category)
                    DetailRow("Source", transaction.source)
                    DetailRow("Description", transaction.description.ifBlank { "None" })
                    DetailRow("Notes", transaction.notes.ifBlank { "None" })
                    DetailRow("Dedup Fingerprint", transaction.dedupFingerprint)
                }
            }

            if (audits.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Modification Audit Trail", style = MaterialTheme.typography.titleMedium)
                        audits.forEach { entry ->
                            Text(
                                text = "• ${entry.fieldChanged}: '${entry.previousValue}' → '${entry.newValue}' (${IndianCurrencyAndTime.formatReadableDateTimeIst(entry.changedAtMillis)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Category or Notes (Preserves Original Source)")
            }
        }
    }

    if (showEditDialog && transaction != null) {
        var selectedCat by remember { mutableStateOf(transaction.category) }
        var notes by remember { mutableStateOf(transaction.notes) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Category & Notes") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Transaction Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateMeta(transaction, selectedCat, notes)
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(0.55f)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

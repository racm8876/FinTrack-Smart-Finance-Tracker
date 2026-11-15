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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PersonSummary
import com.example.domain.IndianCurrencyAndTime
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.TransactionListItem
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.JetBrainsMonoFamily

// 9. People & Contacts Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleAndContactsScreen(
    people: List<PersonSummary>,
    onOpenPerson: (String) -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(people, query) {
        if (query.isBlank()) people
        else people.filter { p ->
            p.displayName.contains(query, true) ||
                p.mobileNumber.contains(query, true) ||
                p.upiId.contains(query, true) ||
                p.transactions.any { it.referenceNumber.contains(query, true) || it.utrOrUpiRef.contains(query, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("People & Contacts (${filtered.size})") })
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name, mobile, UPI ID, or reference...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search people") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("people_search_input")
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    EmptyLedgerCard(
                        title = "No Counterparties Recorded",
                        subtitle = "People, merchants, and UPI contacts appear automatically as transactions are imported or captured.",
                        actionLabel = "Record Transaction",
                        onAction = onAddTransactionClick
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.key }) { person ->
                        PersonSummaryCard(person = person, onClick = { onOpenPerson(person.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonSummaryCard(
    person: PersonSummary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("person_card_${person.key}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(person.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.upiId.isNotBlank()) {
                    Text(person.upiId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = "Mobile: ${person.mobileNumber} • ${person.transactionCount} txn(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (person.totalSent > 0.0) {
                    Text(
                        text = "-${IndianCurrencyAndTime.formatInr(person.totalSent)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = DebitRed
                    )
                }
                if (person.totalReceived > 0.0) {
                    Text(
                        text = "+${IndianCurrencyAndTime.formatInr(person.totalReceived)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = CreditGreen
                    )
                }
            }
        }
    }
}

// 10. Person Transaction History Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonTransactionHistoryScreen(
    person: PersonSummary?,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.displayName ?: "Person Ledger") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No history found for this contact.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(person.displayName, style = MaterialTheme.typography.headlineMedium)
                        Text("UPI ID: ${person.upiId.ifBlank { "Unavailable" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Mobile: ${person.mobileNumber}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "First Tx: ${IndianCurrencyAndTime.formatReadableDateIst(person.firstTransactionMillis)}  |  Latest: ${IndianCurrencyAndTime.formatReadableDateIst(person.mostRecentTransactionMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PersonStatPill("Total Sent", IndianCurrencyAndTime.formatInr(person.totalSent), DebitRed, Modifier.weight(1f))
                            PersonStatPill("Total Received", IndianCurrencyAndTime.formatInr(person.totalReceived), CreditGreen, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PersonStatPill("Weekly", IndianCurrencyAndTime.formatInr(person.weeklyTotal), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            PersonStatPill("Monthly", IndianCurrencyAndTime.formatInr(person.monthlyTotal), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            PersonStatPill("Yearly", IndianCurrencyAndTime.formatInr(person.yearlyTotal), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Text("Complete Transaction History (${person.transactionCount})", style = MaterialTheme.typography.titleMedium)
            }

            items(person.transactions, key = { it.id }) { tx ->
                TransactionListItem(transaction = tx, onClick = { onOpenTransaction(tx.id) })
            }
        }
    }
}

@Composable
private fun PersonStatPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

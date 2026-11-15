package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DefaultCategories
import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.domain.CategoryBreakdown
import com.example.domain.ChartPoint
import com.example.domain.IndianCurrencyAndTime
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.FailedCrimson
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PendingAmber
import kotlin.math.max

@Composable
fun TransactionListItem(
    transaction: TransactionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCredit = transaction.type == TransactionType.CREDIT.name
    val amountColor = if (isCredit) CreditGreen else DebitRed
    val signAmount = if (isCredit) transaction.amount else -transaction.amount

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transaction_item_${transaction.id}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = transaction.type,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.counterpartyName.ifBlank { transaction.description.ifBlank { "Transaction" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("•", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatAppLabel(transaction.paymentApp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (transaction.status != TransactionStatus.SUCCESS.name && transaction.status != TransactionStatus.RECONCILED.name) {
                        StatusBadge(transaction.status)
                    }
                }
                Text(
                    text = "${transaction.dateIst} • ${transaction.timeIst} IST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = IndianCurrencyAndTime.formatInr(signAmount, showSign = true),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
                if (transaction.maskedAccountIdentifier.isNotBlank()) {
                    Text(
                        text = transaction.maskedAccountIdentifier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg, icon) = when (status) {
        TransactionStatus.PENDING.name -> Triple(PendingAmber.copy(alpha = 0.16f), PendingAmber, Icons.Default.Schedule)
        TransactionStatus.FAILED.name -> Triple(FailedCrimson.copy(alpha = 0.16f), FailedCrimson, Icons.Default.Error)
        else -> Triple(CreditGreen.copy(alpha = 0.16f), CreditGreen, Icons.Default.CheckCircle)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = status, tint = fg, modifier = Modifier.size(11.dp))
        Text(text = status, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun CashflowBarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    val expenseColor = MaterialTheme.colorScheme.secondary
    val incomeColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        if (points.isEmpty() || points.all { it.value == 0.0 && it.secondaryValue == 0.0 }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transaction activity recorded for this window yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        val maxVal = max(
            1.0,
            points.maxOf { max(it.value, it.secondaryValue) }
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotW = w / points.size.coerceAtLeast(1)

            // Horizontal reference lines
            for (i in 0..2) {
                val y = h * (i / 2f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            points.forEachIndexed { idx, pt ->
                val centerX = slotW * idx + slotW / 2f
                val barW = (slotW * 0.28f).coerceAtLeast(6.dp.toPx())
                val incH = ((pt.secondaryValue / maxVal) * (h * 0.9f)).toFloat()
                val expH = ((pt.value / maxVal) * (h * 0.9f)).toFloat()

                if (pt.secondaryValue > 0.0) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(centerX - barW - 2.dp.toPx(), h - incH),
                        size = Size(barW, incH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (pt.value > 0.0) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(centerX + 2.dp.toPx(), h - expH),
                        size = Size(barW, expH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = incomeColor, label = "Income / Credit")
            LegendDot(color = expenseColor, label = "Expense / Debit")
        }
    }
}

@Composable
fun CategoryDonutChart(
    breakdown: List<CategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF3B82F6),
        Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFEF4444)
    )
    if (breakdown.isEmpty()) {
        Text(
            text = "No category spend recorded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val top = breakdown.take(6)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = 20.dp.toPx()
            var startAngle = -90f
            top.forEachIndexed { idx, item ->
                val sweep = (item.percentage / 100f) * 360f
                drawArc(
                    color = palette[idx % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep.coerceAtLeast(2f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            top.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(palette[idx % palette.size])
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${item.percentage.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillStart = lineColor.copy(alpha = 0.25f)
    if (points.isEmpty() || points.all { it.value == 0.0 }) {
        Text(
            text = "No monthly expense trend available yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val maxVal = max(1.0, points.maxOf { it.value })
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (points.size > 1) w / (points.size - 1) else w
            val path = Path()
            val fillPath = Path()

            points.forEachIndexed { idx, pt ->
                val x = idx * stepX
                val y = h - ((pt.value / maxVal) * (h * 0.85f)).toFloat()
                if (idx == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((points.size - 1) * stepX, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(listOf(fillStart, Color.Transparent))
            )
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.filterIndexed { index, _ -> index % 2 == 0 }.forEach { pt ->
                Text(pt.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyLedgerCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_vault),
                contentDescription = "Empty FinTrack ledger",
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddManualTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (
        amount: Double,
        type: TransactionType,
        counterparty: String,
        mobile: String,
        upiId: String,
        bankName: String,
        maskedAcc: String,
        app: PaymentApp,
        method: PaymentMethod,
        refNo: String,
        status: TransactionStatus,
        category: String,
        description: String,
        notes: String
    ) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var txType by remember { mutableStateOf(TransactionType.DEBIT) }
    var counterparty by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var maskedAcc by remember { mutableStateOf("") }
    var paymentApp by remember { mutableStateOf(PaymentApp.GOOGLE_PAY) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var refNo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(TransactionStatus.SUCCESS) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "Food & Dining") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Real Transaction") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = txType == t,
                            onClick = { txType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_amount_input")
                )
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Counterparty / Merchant / Person *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_counterparty_input")
                )
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("UPI ID / VPA (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Recipient Mobile (if available)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Account Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maskedAcc,
                    onValueChange = { maskedAcc = it },
                    label = { Text("Masked Account (e.g. XXXX1234)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Payment App", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PaymentApp.GOOGLE_PAY, PaymentApp.PHONEPE, PaymentApp.PAYTM,
                        PaymentApp.BHIM_UPI, PaymentApp.BANK_APP, PaymentApp.MANUAL
                    ).forEach { app ->
                        FilterChip(
                            selected = paymentApp == app,
                            onClick = { paymentApp = app },
                            label = { Text(app.label) }
                        )
                    }
                }
                Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PaymentMethod.UPI, PaymentMethod.IMPS, PaymentMethod.NEFT,
                        PaymentMethod.DEBIT_CARD, PaymentMethod.CREDIT_CARD, PaymentMethod.CASH
                    ).forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method.label) }
                        )
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (categories.ifEmpty { DefaultCategories.all.map { it.name } }).take(10).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = refNo,
                    onValueChange = { refNo = it },
                    label = { Text("UTR / Reference No. (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.replace(",", "").toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "Enter a valid positive ₹ amount."
                        return@Button
                    }
                    if (counterparty.isBlank()) {
                        errorMsg = "Enter counterparty or merchant name."
                        return@Button
                    }
                    onSubmit(
                        amt, txType, counterparty.trim(), mobile.trim(), upiId.trim(),
                        bankName.trim(), maskedAcc.trim(), paymentApp, paymentMethod,
                        refNo.trim(), status, selectedCategory, description.trim(), notes.trim()
                    )
                },
                modifier = Modifier.testTag("confirm_manual_tx_button")
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PasteSmsAlertDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onParse: (String) -> Unit
) {
    var smsText by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parse Bank / UPI Alert Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste any genuine bank SMS or UPI alert notification. FinTrack parses amount, UTR, VPA, masked account, and counterparty strictly on-device without requesting broad SMS permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    label = { Text("Bank / UPI SMS or Notification Text") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("paste_sms_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (smsText.isNotBlank()) onParse(smsText) },
                enabled = smsText.isNotBlank(),
                modifier = Modifier.testTag("parse_sms_confirm_button")
            ) {
                Text("Parse & Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatAppLabel(appName: String): String {
    return PaymentApp.entries.firstOrNull { it.name == appName }?.label ?: appName
}

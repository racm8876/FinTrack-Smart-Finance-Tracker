package com.example.domain

import com.example.data.model.PaymentApp
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.Locale

data class TransactionFilterCriteria(
    val query: String = "",
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val paymentApp: String? = null,
    val bankAccount: String? = null,
    val type: String? = null,
    val category: String? = null,
    val counterparty: String? = null,
    val status: String? = null
)

data class SmartSearchResult(
    val matchedTransactions: List<TransactionRecord>,
    val answerBanner: String?
)

object SmartSearchEngine {

    fun search(
        transactions: List<TransactionRecord>,
        filter: TransactionFilterCriteria,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartSearchResult {
        val q = filter.query.trim()
        val lower = q.lowercase(Locale.ENGLISH)

        var nlStart: Long? = filter.startMillis
        var nlEnd: Long? = filter.endMillis
        var nlMinAmt: Double? = filter.minAmount
        var nlApp: String? = filter.paymentApp
        var nlType: String? = filter.type
        var nlPerson: String? = filter.counterparty

        var isQuestion = false

        if (q.isNotBlank()) {
            isQuestion = lower.startsWith("how much") || lower.startsWith("show all") ||
                lower.contains("this month") || lower.contains("last week") ||
                lower.contains("yesterday") || lower.contains("this year") ||
                lower.contains("above ") || lower.contains("over ")

            if (lower.contains("yesterday")) {
                val todayStart = IndianCurrencyAndTime.startOfTodayIstMillis(nowMillis)
                nlStart = todayStart - 86_400_000L
                nlEnd = todayStart - 1L
            } else if (lower.contains("today")) {
                nlStart = IndianCurrencyAndTime.startOfTodayIstMillis(nowMillis)
                nlEnd = nowMillis
            } else if (lower.contains("last week")) {
                val weekStart = IndianCurrencyAndTime.startOfWeekIstMillis(nowMillis)
                nlStart = weekStart - 7L * 86_400_000L
                nlEnd = weekStart - 1L
            } else if (lower.contains("this week")) {
                nlStart = IndianCurrencyAndTime.startOfWeekIstMillis(nowMillis)
                nlEnd = nowMillis
            } else if (lower.contains("this month")) {
                nlStart = IndianCurrencyAndTime.startOfMonthIstMillis(nowMillis)
                nlEnd = nowMillis
            } else if (lower.contains("this year")) {
                nlStart = IndianCurrencyAndTime.startOfYearIstMillis(nowMillis)
                nlEnd = nowMillis
            }

            if (lower.contains("phonepe")) nlApp = PaymentApp.PHONEPE.name
            else if (lower.contains("google pay") || lower.contains("gpay")) nlApp = PaymentApp.GOOGLE_PAY.name
            else if (lower.contains("paytm")) nlApp = PaymentApp.PAYTM.name
            else if (lower.contains("bhim")) nlApp = PaymentApp.BHIM_UPI.name

            if (lower.contains("send") || lower.contains("sent") || lower.contains("spend") || lower.contains("spent") || lower.contains("paid")) {
                nlType = TransactionType.DEBIT.name
            } else if (lower.contains("receive") || lower.contains("received") || lower.contains("income")) {
                nlType = TransactionType.CREDIT.name
            }

            Regex("""(?:above|over|greater than|more than)\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.\d+)?)""")
                .find(lower)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.let {
                    nlMinAmt = it
                }

            Regex("""(?:to|from)\s+([a-zA-Z][a-zA-Z0-9 ._-]{1,30}?)(?:\s+(?:this|last|in|on|yesterday|today|\?|$))""")
                .find(lower)?.groupValues?.getOrNull(1)?.trim()?.let { party ->
                    if (party !in listOf("all", "me", "my", "through", "made", "above")) {
                        nlPerson = party
                    }
                }
        }

        val filtered = transactions.filter { tx ->
            if (nlStart != null && tx.transactionTimestampMillis < nlStart) return@filter false
            if (nlEnd != null && tx.transactionTimestampMillis > nlEnd) return@filter false
            if (nlMinAmt != null && tx.amount < nlMinAmt) return@filter false
            if (filter.maxAmount != null && tx.amount > filter.maxAmount) return@filter false
            if (nlApp != null && !tx.paymentApp.equals(nlApp, ignoreCase = true)) return@filter false
            if (filter.bankAccount != null && !tx.bankAccountName.equals(filter.bankAccount, ignoreCase = true)) return@filter false
            if (nlType != null && !tx.type.equals(nlType, ignoreCase = true)) return@filter false
            if (filter.category != null && !tx.category.equals(filter.category, ignoreCase = true)) return@filter false
            if (filter.status != null && !tx.status.equals(filter.status, ignoreCase = true)) return@filter false
            if (nlPerson != null) {
                val pLower = nlPerson.lowercase(Locale.ENGLISH)
                val matchesParty = tx.counterpartyName.lowercase(Locale.ENGLISH).contains(pLower) ||
                    tx.senderName.lowercase(Locale.ENGLISH).contains(pLower) ||
                    tx.receiverName.lowercase(Locale.ENGLISH).contains(pLower) ||
                    tx.upiId.lowercase(Locale.ENGLISH).contains(pLower)
                if (!matchesParty) return@filter false
            }

            if (q.isNotBlank() && !isQuestion) {
                val matchesText = tx.counterpartyName.contains(q, true) ||
                    tx.recipientMobile.contains(q, true) ||
                    tx.upiId.contains(q, true) ||
                    tx.amount.toString().contains(q, true) ||
                    tx.dateIst.contains(q, true) ||
                    tx.id.contains(q, true) ||
                    tx.referenceNumber.contains(q, true) ||
                    tx.utrOrUpiRef.contains(q, true) ||
                    tx.bankAccountName.contains(q, true) ||
                    tx.paymentApp.contains(q, true) ||
                    tx.category.contains(q, true) ||
                    tx.description.contains(q, true)
                if (!matchesText) return@filter false
            }
            true
        }

        val banner = if (q.isNotBlank() && isQuestion) {
            val total = filtered.sumOf { it.amount }
            "Found ${filtered.size} matching transaction(s) totaling ${IndianCurrencyAndTime.formatInr(total)}."
        } else null

        return SmartSearchResult(filtered, banner)
    }
}

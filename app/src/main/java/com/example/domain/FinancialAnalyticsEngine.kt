package com.example.domain

import com.example.data.model.DashboardMetrics
import com.example.data.model.FinancialAccount
import com.example.data.model.PersonSummary
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.Locale

data class CategoryBreakdown(
    val category: String,
    val amount: Double,
    val count: Int,
    val percentage: Float
)

data class PeriodComparison(
    val income: Double,
    val expenses: Double,
    val transfers: Double,
    val savings: Double,
    val transactionCount: Int,
    val categoryBreakdown: List<CategoryBreakdown>,
    val topPeople: List<PersonSummary>
)

data class ChartPoint(
    val label: String,
    val value: Double,
    val secondaryValue: Double = 0.0
)

object FinancialAnalyticsEngine {

    fun computeDashboardMetrics(
        transactions: List<TransactionRecord>,
        accounts: List<FinancialAccount>,
        nowMillis: Long = System.currentTimeMillis()
    ): DashboardMetrics {
        val verifiedAccounts = accounts.filter { it.supportsRealBalance }
        val verifiedBalance = if (verifiedAccounts.isNotEmpty()) {
            verifiedAccounts.sumOf { it.currentBalance }
        } else null

        val successful = transactions.filter {
            it.status == TransactionStatus.SUCCESS.name || it.status == TransactionStatus.RECONCILED.name
        }
        val todayStart = IndianCurrencyAndTime.startOfTodayIstMillis(nowMillis)
        val weekStart = IndianCurrencyAndTime.startOfWeekIstMillis(nowMillis)
        val monthStart = IndianCurrencyAndTime.startOfMonthIstMillis(nowMillis)
        val yearStart = IndianCurrencyAndTime.startOfYearIstMillis(nowMillis)

        val totalIncome = successful.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
        val totalExpenses = successful.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount }
        val totalTransferred = successful.filter {
            it.type == TransactionType.DEBIT.name && it.category == "Money Transfer"
        }.sumOf { it.amount }
        val totalReceived = successful.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }

        val debits = successful.filter { it.type == TransactionType.DEBIT.name }
        val todaySpending = debits.filter { it.transactionTimestampMillis >= todayStart }.sumOf { it.amount }
        val weeklySpending = debits.filter { it.transactionTimestampMillis >= weekStart }.sumOf { it.amount }
        val monthlySpending = debits.filter { it.transactionTimestampMillis >= monthStart }.sumOf { it.amount }
        val yearlySpending = debits.filter { it.transactionTimestampMillis >= yearStart }.sumOf { it.amount }

        return DashboardMetrics(
            verifiedBalance = verifiedBalance,
            hasVerifiedBalanceAccounts = verifiedAccounts.isNotEmpty(),
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            totalTransferred = totalTransferred,
            totalReceived = totalReceived,
            todaySpending = todaySpending,
            weeklySpending = weeklySpending,
            monthlySpending = monthlySpending,
            yearlySpending = yearlySpending,
            transactionCount = transactions.size,
            pendingCount = transactions.count { it.status == TransactionStatus.PENDING.name },
            failedCount = transactions.count { it.status == TransactionStatus.FAILED.name }
        )
    }

    fun buildPeopleSummaries(
        transactions: List<TransactionRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PersonSummary> {
        val weekStart = IndianCurrencyAndTime.startOfWeekIstMillis(nowMillis)
        val monthStart = IndianCurrencyAndTime.startOfMonthIstMillis(nowMillis)
        val yearStart = IndianCurrencyAndTime.startOfYearIstMillis(nowMillis)

        val grouped = transactions.groupBy { tx ->
            when {
                tx.upiId.isNotBlank() -> tx.upiId.lowercase(Locale.ENGLISH)
                tx.recipientMobile.isNotBlank() && tx.recipientMobile != "Unavailable" -> tx.recipientMobile
                else -> tx.counterpartyName.ifBlank { "Unknown Party" }.lowercase(Locale.ENGLISH)
            }
        }

        return grouped.map { (key, list) ->
            val sorted = list.sortedByDescending { it.transactionTimestampMillis }
            val bestName = sorted.firstNotNullOfOrNull { it.counterpartyName.takeIf(String::isNotBlank) } ?: "Unknown Party"
            val bestMobile = sorted.firstNotNullOfOrNull {
                it.recipientMobile.takeIf { m -> m.isNotBlank() && m != "Unavailable" }
            } ?: "Unavailable"
            val bestUpi = sorted.firstNotNullOfOrNull { it.upiId.takeIf(String::isNotBlank) } ?: ""

            val totalSent = sorted.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount }
            val totalReceived = sorted.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }

            PersonSummary(
                key = key,
                displayName = bestName,
                mobileNumber = bestMobile,
                upiId = bestUpi,
                totalSent = totalSent,
                totalReceived = totalReceived,
                transactionCount = sorted.size,
                firstTransactionMillis = sorted.last().transactionTimestampMillis,
                mostRecentTransactionMillis = sorted.first().transactionTimestampMillis,
                weeklyTotal = sorted.filter { it.transactionTimestampMillis >= weekStart }.sumOf { it.amount },
                monthlyTotal = sorted.filter { it.transactionTimestampMillis >= monthStart }.sumOf { it.amount },
                yearlyTotal = sorted.filter { it.transactionTimestampMillis >= yearStart }.sumOf { it.amount },
                transactions = sorted
            )
        }.sortedByDescending { it.mostRecentTransactionMillis }
    }

    fun computePeriodReport(
        transactions: List<TransactionRecord>,
        startMillis: Long,
        endMillis: Long
    ): PeriodComparison {
        val inRange = transactions.filter {
            it.transactionTimestampMillis in startMillis..endMillis &&
                (it.status == TransactionStatus.SUCCESS.name || it.status == TransactionStatus.RECONCILED.name)
        }
        val income = inRange.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
        val expenses = inRange.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount }
        val transfers = inRange.filter {
            it.type == TransactionType.DEBIT.name && it.category == "Money Transfer"
        }.sumOf { it.amount }

        val debitByCat = inRange.filter { it.type == TransactionType.DEBIT.name }
            .groupBy { it.category.ifBlank { "Other" } }
        val breakdown = debitByCat.map { (cat, list) ->
            val amt = list.sumOf { it.amount }
            val pct = if (expenses > 0.0) ((amt / expenses) * 100f).toFloat() else 0f
            CategoryBreakdown(cat, amt, list.size, pct)
        }.sortedByDescending { it.amount }

        val topPeople = buildPeopleSummaries(inRange)
            .sortedByDescending { it.totalSent + it.totalReceived }
            .take(10)

        return PeriodComparison(
            income = income,
            expenses = expenses,
            transfers = transfers,
            savings = income - expenses,
            transactionCount = inRange.size,
            categoryBreakdown = breakdown,
            topPeople = topPeople
        )
    }

    fun computeLast7DaysTrend(
        transactions: List<TransactionRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ChartPoint> {
        val todayStart = IndianCurrencyAndTime.startOfTodayIstMillis(nowMillis)
        return (6 downTo 0).map { daysAgo ->
            val dayStart = todayStart - daysAgo * 86_400_000L
            val dayEnd = dayStart + 86_400_000L - 1L
            val dayTxs = transactions.filter { it.transactionTimestampMillis in dayStart..dayEnd }
            val expense = dayTxs.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount }
            val income = dayTxs.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
            ChartPoint(
                label = IndianCurrencyAndTime.formatShortDayIst(dayStart),
                value = expense,
                secondaryValue = income
            )
        }
    }

    fun computeMonthlyTrendForYear(
        transactions: List<TransactionRecord>,
        year: Int
    ): List<ChartPoint> {
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val cal = Calendar.getInstance(IndianCurrencyAndTime.istTimeZone)
        return (0..11).map { monthIdx ->
            val inMonth = transactions.filter { tx ->
                cal.timeInMillis = tx.transactionTimestampMillis
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == monthIdx
            }
            ChartPoint(
                label = monthNames[monthIdx],
                value = inMonth.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount },
                secondaryValue = inMonth.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
            )
        }
    }
}

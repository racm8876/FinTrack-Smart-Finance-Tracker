package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType(val label: String) {
    CREDIT("Credit"),
    DEBIT("Debit")
}

enum class TransactionStatus(val label: String) {
    SUCCESS("Success"),
    PENDING("Pending"),
    FAILED("Failed"),
    RECONCILED("Reconciled")
}

enum class PaymentApp(val label: String) {
    GOOGLE_PAY("Google Pay"),
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    BHIM_UPI("BHIM UPI"),
    BANK_APP("Bank App"),
    CARD_POS("Card / POS"),
    NET_BANKING("Net Banking"),
    OTHER("Other"),
    MANUAL("Manual")
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    NEFT("NEFT"),
    RTGS("RTGS"),
    IMPS("IMPS"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card"),
    ATM("ATM Withdrawal"),
    BANK_DEPOSIT("Bank Deposit"),
    CASH("Cash"),
    OTHER("Other")
}

enum class TransactionSource(val label: String) {
    NOTIFICATION_LISTENER("Notification Listener"),
    SMS_INBOX("SMS Inbox"),
    STATEMENT_CSV("CSV Statement"),
    STATEMENT_XLSX("Excel Statement"),
    STATEMENT_PDF("PDF Statement"),
    ACCOUNT_AGGREGATOR("Account Aggregator"),
    BANK_API("Official Bank API"),
    MANUAL_ENTRY("Manual Entry")
}

enum class AccountType(val label: String) {
    SAVINGS("Savings Account"),
    CURRENT("Current Account"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    UPI_WALLET("UPI Handle"),
    CASH("Cash Ledger")
}

enum class SyncStatus(val label: String) {
    CONNECTED("Connected"),
    DELAYED_SYNC("Delayed Sync"),
    SETUP_REQUIRED("Setup Required"),
    MANUAL_ONLY("Statement / Manual"),
    SYNC_FAILED("Sync Failed")
}

enum class BudgetScope(val label: String) {
    MONTHLY_TOTAL("Monthly Total Budget"),
    CATEGORY_MONTHLY("Category Monthly Budget"),
    DAILY_LIMIT("Daily Spending Limit"),
    WEEKLY_LIMIT("Weekly Spending Limit"),
    SAVINGS_GOAL("Savings Goal")
}

enum class AlertType(val label: String) {
    NEW_TRANSACTION("New Transaction"),
    LARGE_TRANSACTION("Large Transaction"),
    INCOMING_PAYMENT("Incoming Payment"),
    OUTGOING_PAYMENT("Outgoing Payment"),
    UNUSUAL_SPENDING("Unusual Spending"),
    BUDGET_EXCEEDED("Budget Exceeded"),
    SYNC_FAILED("Sync Failed"),
    DUPLICATE_DETECTED("Duplicate Detected"),
    MONTHLY_SUMMARY("Monthly Summary")
}

enum class ConsentScope(val title: String, val defaultPurpose: String) {
    NOTIFICATION_LISTENER(
        "Payment Notification Listener",
        "Captures real-time UPI and banking alert notifications strictly on-device when granted."
    ),
    SMS_PARSING(
        "Transaction SMS Inbox Parsing",
        "Reads bank & UPI transaction SMS alerts locally to extract verified transaction records."
    ),
    ACCOUNT_AGGREGATOR(
        "RBI Account Aggregator (AA)",
        "Fetches consented bank statements & balances via RBI-licensed AA FIPs."
    ),
    CLOUD_SYNC(
        "Encrypted Cloud Sync",
        "Synchronizes your financial ledger to your private authenticated Cloud Firestore vault."
    ),
    BIOMETRIC_LOCK(
        "Biometric & PIN Protection",
        "Secures local app access using Android BiometricPrompt and salted PIN."
    ),
    STATEMENT_IMPORT(
        "Bank Statement Document Import",
        "Parses user-selected PDF, CSV, and Excel statements via Storage Access Framework."
    )
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val currency: String = "INR",
    val timezone: String = "Asia/Kolkata",
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val monthlyBudgetLimit: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FinancialAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountName: String = "",
    val institutionName: String = "",
    val accountType: String = AccountType.SAVINGS.name,
    val maskedIdentifier: String = "",
    val upiId: String = "",
    val sourceType: String = TransactionSource.MANUAL_ENTRY.name,
    val syncStatus: String = SyncStatus.MANUAL_ONLY.name,
    val supportsRealBalance: Boolean = false,
    val currentBalance: Double = 0.0,
    val lastSyncedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = "",
    val transactionTimestampMillis: Long = 0L,
    val dateIst: String = "",
    val timeIst: String = "",
    val amount: Double = 0.0,
    val type: String = TransactionType.DEBIT.name,
    val senderName: String = "",
    val receiverName: String = "",
    val counterpartyName: String = "",
    val recipientMobile: String = "Unavailable",
    val upiId: String = "",
    val bankAccountName: String = "",
    val maskedAccountIdentifier: String = "",
    val paymentApp: String = PaymentApp.OTHER.name,
    val paymentMethod: String = PaymentMethod.UPI.name,
    val referenceNumber: String = "",
    val utrOrUpiRef: String = "",
    val status: String = TransactionStatus.SUCCESS.name,
    val category: String = "Other",
    val description: String = "",
    val source: String = TransactionSource.MANUAL_ENTRY.name,
    val notes: String = "",
    val dedupFingerprint: String = "",
    val isDuplicate: Boolean = false,
    val rawSourceHash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContactParty(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val mobileNumber: String = "Unavailable",
    val upiId: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExpenseCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val iconKey: String = "category",
    val colorHex: String = "#10B981",
    val isCustom: Boolean = false,
    val isIncomeCategory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BudgetGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val scope: String = BudgetScope.CATEGORY_MONTHLY.name,
    val category: String = "",
    val limitAmount: Double = 0.0,
    val savedOrTargetAmount: Double = 0.0,
    val monthKey: String = "",
    val alertThresholdPercent: Int = 80,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FinanceAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val alertType: String = AlertType.NEW_TRANSACTION.name,
    val relatedTransactionId: String = "",
    val isRead: Boolean = false,
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val source: String = "",
    val status: String = "SUCCESS",
    val recordsProcessed: Long = 0L,
    val duplicatesSkipped: Long = 0L,
    val message: String = "",
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConsentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scope: String = "",
    val granted: Boolean = false,
    val purpose: String = "",
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TransactionAuditEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transactionId: String = "",
    val fieldChanged: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    val changedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class PersonSummary(
    val key: String,
    val displayName: String,
    val mobileNumber: String,
    val upiId: String,
    val totalSent: Double,
    val totalReceived: Double,
    val transactionCount: Int,
    val firstTransactionMillis: Long,
    val mostRecentTransactionMillis: Long,
    val weeklyTotal: Double,
    val monthlyTotal: Double,
    val yearlyTotal: Double,
    val transactions: List<TransactionRecord>
)

data class DashboardMetrics(
    val verifiedBalance: Double?,
    val hasVerifiedBalanceAccounts: Boolean,
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalTransferred: Double,
    val totalReceived: Double,
    val todaySpending: Double,
    val weeklySpending: Double,
    val monthlySpending: Double,
    val yearlySpending: Double,
    val transactionCount: Int,
    val pendingCount: Int,
    val failedCount: Int
)

object DefaultCategories {
    data class Seed(
        val name: String,
        val iconKey: String,
        val colorHex: String,
        val isIncome: Boolean = false
    )

    val all: List<Seed> = listOf(
        Seed("Food & Dining", "restaurant", "#F59E0B"),
        Seed("Shopping", "shopping_bag", "#EC4899"),
        Seed("Transportation", "directions_car", "#3B82F6"),
        Seed("Rent", "home", "#8B5CF6"),
        Seed("Electricity", "bolt", "#EAB308"),
        Seed("Mobile Recharge", "smartphone", "#06B6D4"),
        Seed("Internet", "wifi", "#0EA5E9"),
        Seed("Healthcare", "medical_services", "#EF4444"),
        Seed("Education", "school", "#6366F1"),
        Seed("Entertainment", "movie", "#F97316"),
        Seed("Travel", "flight", "#14B8A6"),
        Seed("Groceries", "shopping_cart", "#22C55E"),
        Seed("Money Transfer", "swap_horiz", "#64748B"),
        Seed("Salary", "account_balance_wallet", "#10B981", isIncome = true),
        Seed("Business Income", "business_center", "#059669", isIncome = true),
        Seed("Investments", "trending_up", "#0D9488"),
        Seed("Other", "category", "#78716C")
    )
}

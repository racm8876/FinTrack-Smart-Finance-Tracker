package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinTrackDatabase
import com.example.data.local.LocalTransactionEntity
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSecurityPreferences
import com.example.data.model.AlertType
import com.example.data.model.BudgetGoal
import com.example.data.model.ConsentRecord
import com.example.data.model.ConsentScope
import com.example.data.model.DashboardMetrics
import com.example.data.model.ExpenseCategory
import com.example.data.model.FinanceAlert
import com.example.data.model.FinancialAccount
import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.PersonSummary
import com.example.data.model.SyncAuditLog
import com.example.data.model.TransactionAuditEntry
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.data.remote.FinTrackRepository
import com.example.domain.ExportFormat
import com.example.domain.FinancialAnalyticsEngine
import com.example.domain.FinancialExportEngine
import com.example.domain.IndianCurrencyAndTime
import com.example.domain.IndianTransactionParser
import com.example.domain.SmartSearchEngine
import com.example.domain.SmartSearchResult
import com.example.domain.StatementParser
import com.example.domain.TransactionFilterCriteria
import com.example.service.FinTrackSyncWorker
import com.example.service.NotificationHelper
import com.example.service.SmsTransactionReader
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class FinTrackViewModel(
    private val repository: FinTrackRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val localDatabase: FinTrackDatabase,
    private val currentUserId: String,
    userDisplayName: String,
    userEmail: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _filterCriteria = MutableStateFlow(TransactionFilterCriteria())
    val filterCriteria: StateFlow<TransactionFilterCriteria> = _filterCriteria.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfileInitialized(userDisplayName, userEmail)
        }
    }

    val userPreferences: StateFlow<UserSecurityPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserSecurityPreferences()
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { error ->
            if (error is CancellationException && unwrapFirestore(error) == null) throw error
            Log.w(TAG, "Profile observe error", error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val transactionsState: StateFlow<UiState<List<TransactionRecord>>> = repository.observeTransactions()
        .map<List<TransactionRecord>, UiState<List<TransactionRecord>>> { list ->
            syncToLocalRoomCache(list)
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = unwrapFirestore(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing transactions", root)
            emit(UiState.Error(root.message ?: "Failed to load transactions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val transactions: StateFlow<List<TransactionRecord>> = transactionsState
        .map { if (it is UiState.Success) it.data else emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<FinancialAccount>> = repository.observeAccounts()
        .catch { handleSafeFlowError("accounts", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val categories: StateFlow<List<ExpenseCategory>> = repository.observeCategories()
        .catch { handleSafeFlowError("categories", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val budgets: StateFlow<List<BudgetGoal>> = repository.observeBudgets()
        .catch { handleSafeFlowError("budgets", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val alerts: StateFlow<List<FinanceAlert>> = repository.observeAlerts()
        .catch { handleSafeFlowError("alerts", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val syncLogs: StateFlow<List<SyncAuditLog>> = repository.observeSyncLogs()
        .catch { handleSafeFlowError("sync_logs", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val consents: StateFlow<List<ConsentRecord>> = repository.observeConsents()
        .catch { handleSafeFlowError("consents", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(transactions, accounts) { txs, accs ->
        FinancialAnalyticsEngine.computeDashboardMetrics(txs, accs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        FinancialAnalyticsEngine.computeDashboardMetrics(emptyList(), emptyList())
    )

    val peopleSummaries: StateFlow<List<PersonSummary>> = transactions
        .map { FinancialAnalyticsEngine.buildPeopleSummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val smartSearchResult: StateFlow<SmartSearchResult> = combine(transactions, _filterCriteria) { txs, filter ->
        SmartSearchEngine.search(txs, filter)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SmartSearchResult(emptyList(), null)
    )

    fun observeTransactionAudits(txId: String): Flow<List<TransactionAuditEntry>> =
        repository.observeTransactionAuditLogs(txId)
            .catch { handleSafeFlowError("audit_logs", it) }

    fun updateFilter(transform: (TransactionFilterCriteria) -> TransactionFilterCriteria) {
        _filterCriteria.value = transform(_filterCriteria.value)
    }

    fun clearFilters() {
        _filterCriteria.value = TransactionFilterCriteria()
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun addManualTransaction(
        amount: Double,
        type: TransactionType,
        counterpartyName: String,
        recipientMobile: String,
        upiId: String,
        bankAccountName: String,
        maskedAccount: String,
        paymentApp: PaymentApp,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        status: TransactionStatus,
        category: String,
        description: String,
        notes: String,
        timestampMillis: Long = System.currentTimeMillis(),
        context: Context? = null
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val dateIst = IndianCurrencyAndTime.formatDateIst(timestampMillis)
            val timeIst = IndianCurrencyAndTime.formatTimeIst(timestampMillis)
            val fp = IndianTransactionParser.computeDedupFingerprint(
                amount = amount,
                dateIst = dateIst,
                type = type.name,
                utrOrRef = referenceNumber,
                counterparty = counterpartyName,
                upiId = upiId
            )
            val record = TransactionRecord(
                userId = currentUserId,
                transactionTimestampMillis = timestampMillis,
                dateIst = dateIst,
                timeIst = timeIst,
                amount = amount,
                type = type.name,
                senderName = if (type == TransactionType.CREDIT) counterpartyName else "Self",
                receiverName = if (type == TransactionType.DEBIT) counterpartyName else "Self",
                counterpartyName = counterpartyName.ifBlank { "Manual Party" },
                recipientMobile = recipientMobile.ifBlank { "Unavailable" },
                upiId = upiId.trim(),
                bankAccountName = bankAccountName.ifBlank { "Manual Account" },
                maskedAccountIdentifier = maskedAccount.trim(),
                paymentApp = paymentApp.name,
                paymentMethod = paymentMethod.name,
                referenceNumber = referenceNumber.trim(),
                utrOrUpiRef = referenceNumber.trim(),
                status = status.name,
                category = category.ifBlank { "Other" },
                description = description.ifBlank { "$type via ${paymentMethod.label}" },
                source = TransactionSource.MANUAL_ENTRY.name,
                notes = notes.trim(),
                dedupFingerprint = fp,
                rawSourceHash = IndianTransactionParser.sha256("MANUAL|$timestampMillis|$amount|$counterpartyName")
            )
            val res = repository.addTransaction(record)
            if (res.isSuccess) {
                evaluateBudgetAndLargeTxAlerts(record, context)
                _statusBanner.value = "Transaction recorded (${IndianCurrencyAndTime.formatInr(amount)})."
            } else {
                _statusBanner.value = "Save failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun parseAndSaveRawAlertText(rawText: String, context: Context? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            val parsed = IndianTransactionParser.parseMessage(
                rawText = rawText,
                timestampMillis = System.currentTimeMillis(),
                source = TransactionSource.SMS_INBOX,
                userId = currentUserId
            )
            if (parsed == null) {
                _statusBanner.value = "Could not extract a valid ₹ amount and transaction signal from text."
                _isBusy.value = false
                return@launch
            }
            val res = repository.addTransaction(parsed)
            if (res.isSuccess) {
                evaluateBudgetAndLargeTxAlerts(parsed, context)
                repository.recordSyncLog("SMS_PASTE_PARSE", "SUCCESS", 1, 0, "Parsed pasted alert text")
                _statusBanner.value = "Captured ${IndianCurrencyAndTime.formatInr(parsed.amount)} (${parsed.counterpartyName})."
            } else {
                _statusBanner.value = "Failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun scanSmsInbox(context: Context) {
        viewModelScope.launch {
            _isBusy.value = true
            val scanResult = withContext(Dispatchers.IO) {
                SmsTransactionReader.scanInboxTransactions(context, currentUserId)
            }
            if (!scanResult.permissionGranted) {
                _statusBanner.value = scanResult.message
                _isBusy.value = false
                return@launch
            }
            if (scanResult.transactions.isEmpty()) {
                repository.recordSyncLog("SMS_INBOX", "SUCCESS", 0, 0, scanResult.message)
                _statusBanner.value = scanResult.message
                _isBusy.value = false
                return@launch
            }
            val (added, dups) = repository.addTransactionsBatch(scanResult.transactions)
                .getOrElse {
                    repository.recordSyncLog("SMS_INBOX", "FAILED", 0, 0, it.localizedMessage ?: "Batch error")
                    _statusBanner.value = "SMS sync failed: ${it.localizedMessage}"
                    _isBusy.value = false
                    return@launch
                }
            preferencesRepository.updateLastSync("SMS Inbox Scan", System.currentTimeMillis())
            repository.recordSyncLog("SMS_INBOX", "SUCCESS", added, dups, scanResult.message)
            _statusBanner.value = "SMS sync complete: $added added, $dups duplicate(s) skipped."
            _isBusy.value = false
        }
    }

    fun importStatementUri(
        context: Context,
        uri: Uri,
        fileName: String,
        bankAccountName: String,
        maskedIdentifier: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                StatementParser.importFromUri(
                    context = context,
                    uri = uri,
                    fileName = fileName,
                    bankAccountName = bankAccountName,
                    maskedIdentifier = maskedIdentifier,
                    userId = currentUserId
                )
            }
            if (result.transactions.isEmpty()) {
                repository.recordSyncLog("STATEMENT_IMPORT", "FAILED", 0, 0, result.diagnosticMessage)
                _statusBanner.value = result.diagnosticMessage
                _isBusy.value = false
                return@launch
            }
            val (added, dups) = repository.addTransactionsBatch(result.transactions)
                .getOrElse {
                    repository.recordSyncLog("STATEMENT_IMPORT", "FAILED", 0, 0, it.localizedMessage ?: "Import failed")
                    _statusBanner.value = "Import failed: ${it.localizedMessage}"
                    _isBusy.value = false
                    return@launch
                }
            preferencesRepository.updateLastSync("Statement Import ($fileName)", System.currentTimeMillis())
            repository.recordSyncLog(
                "STATEMENT_IMPORT",
                "SUCCESS",
                added,
                dups,
                "File: $fileName — ${result.diagnosticMessage}"
            )
            _statusBanner.value = "Imported $added transactions ($dups duplicate(s) skipped)."
            _isBusy.value = false
        }
    }

    fun updateTransactionMetadata(record: TransactionRecord, category: String, notes: String) {
        viewModelScope.launch {
            val res = repository.updateTransactionMetadata(record, category, notes)
            _statusBanner.value = if (res.isSuccess) {
                "Updated transaction & recorded audit log."
            } else {
                "Update failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            val res = repository.deleteTransaction(txId)
            _statusBanner.value = if (res.isSuccess) "Transaction deleted." else "Delete failed."
        }
    }

    fun upsertAccount(account: FinancialAccount) {
        viewModelScope.launch {
            val res = repository.upsertAccount(account)
            _statusBanner.value = if (res.isSuccess) {
                "Account '${account.accountName}' saved."
            } else {
                "Account save failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
            _statusBanner.value = "Account removed."
        }
    }

    fun saveCategory(category: ExpenseCategory) {
        viewModelScope.launch {
            val res = repository.addOrUpdateCategory(category)
            _statusBanner.value = if (res.isSuccess) "Category saved." else "Category save failed."
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
        }
    }

    fun saveBudget(budget: BudgetGoal) {
        viewModelScope.launch {
            val res = repository.saveBudget(budget)
            _statusBanner.value = if (res.isSuccess) "Budget / goal saved." else "Budget save failed."
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            repository.deleteBudget(budgetId)
        }
    }

    fun updateConsent(scope: ConsentScope, granted: Boolean) {
        viewModelScope.launch {
            repository.updateConsent(scope, granted)
            _statusBanner.value = "${scope.title}: ${if (granted) "Consent Granted" else "Consent Revoked"}"
        }
    }

    fun markAlertRead(alertId: String) {
        viewModelScope.launch {
            repository.markAlertRead(alertId)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            val pinEnabled = userPreferences.value.pinHash.isNotBlank()
            repository.updateUserSecurityFlags(pinEnabled = pinEnabled, biometricEnabled = enabled)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            preferencesRepository.setPin(pin)
            repository.updateUserSecurityFlags(
                pinEnabled = true,
                biometricEnabled = userPreferences.value.biometricLockEnabled
            )
            _statusBanner.value = "4-digit PIN protection enabled."
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            preferencesRepository.clearPin()
            repository.updateUserSecurityFlags(
                pinEnabled = false,
                biometricEnabled = userPreferences.value.biometricLockEnabled
            )
            _statusBanner.value = "PIN lock removed."
        }
    }

    fun updateNotificationPrefs(
        newTx: Boolean,
        largeTx: Boolean,
        budget: Boolean,
        syncFail: Boolean,
        dup: Boolean,
        monthly: Boolean,
        threshold: Double
    ) {
        viewModelScope.launch {
            preferencesRepository.updateNotificationPrefs(
                newTx, largeTx, budget, syncFail, dup, monthly, threshold
            )
            _statusBanner.value = "Notification preferences saved."
        }
    }

    fun triggerSyncNow(context: Context) {
        FinTrackSyncWorker.triggerImmediateSync(context)
        _statusBanner.value = "Background synchronization scheduled via WorkManager."
    }

    suspend fun exportReportFile(
        context: Context,
        format: ExportFormat,
        records: List<TransactionRecord>,
        title: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            FinancialExportEngine.exportToFile(context, records, format, title)
        }
    }

    fun deleteAllFinancialData(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            localDatabase.localTransactionDao().deleteAllForUser(currentUserId)
            localDatabase.pendingSyncDao().deleteAllForUser(currentUserId)
            repository.deleteAllUserData()
            preferencesRepository.clearAllLocalPrefs()
            _isBusy.value = false
            onComplete()
        }
    }

    private suspend fun evaluateBudgetAndLargeTxAlerts(record: TransactionRecord, context: Context?) {
        val prefs = userPreferences.value
        val formattedAmt = IndianCurrencyAndTime.formatInr(record.amount)
        if (record.amount >= prefs.largeTransactionThreshold && prefs.notifyLargeTransaction) {
            val title = "Large Transaction Alert"
            val msg = "$formattedAmt ${record.type.lowercase()} (${record.counterpartyName})"
            repository.createAlert(
                FinanceAlert(
                    title = title,
                    message = msg,
                    alertType = AlertType.LARGE_TRANSACTION.name,
                    relatedTransactionId = record.id,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            if (context != null) {
                NotificationHelper.showLocalNotification(context, NotificationHelper.CHANNEL_ALERTS, title, msg)
            }
        }

        // Check category monthly budgets
        if (record.type == TransactionType.DEBIT.name && prefs.notifyBudgetExceeded) {
            val monthKey = IndianCurrencyAndTime.formatMonthKeyIst(record.transactionTimestampMillis)
            val monthStart = IndianCurrencyAndTime.startOfMonthIstMillis(record.transactionTimestampMillis)
            val currentTxs = transactions.value + record
            val catSpend = currentTxs.filter {
                it.type == TransactionType.DEBIT.name &&
                    it.category == record.category &&
                    it.transactionTimestampMillis >= monthStart
            }.sumOf { it.amount }

            val matchingBudget = budgets.value.firstOrNull {
                it.category == record.category && (it.monthKey == monthKey || it.monthKey == "RECURRING")
            }
            if (matchingBudget != null && matchingBudget.limitAmount > 0.0) {
                val pct = (catSpend / matchingBudget.limitAmount) * 100.0
                if (pct >= matchingBudget.alertThresholdPercent) {
                    val exceeded = pct >= 100.0
                    val title = if (exceeded) "Budget Exceeded: ${record.category}" else "Budget Warning: ${record.category}"
                    val msg = "Spent ${IndianCurrencyAndTime.formatInr(catSpend)} of ${IndianCurrencyAndTime.formatInr(matchingBudget.limitAmount)} (${pct.toInt()}%)."
                    repository.createAlert(
                        FinanceAlert(
                            title = title,
                            message = msg,
                            alertType = AlertType.BUDGET_EXCEEDED.name,
                            relatedTransactionId = record.id,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    if (context != null) {
                        NotificationHelper.showLocalNotification(context, NotificationHelper.CHANNEL_ALERTS, title, msg)
                    }
                }
            }
        }
    }

    private suspend fun syncToLocalRoomCache(list: List<TransactionRecord>) {
        withContext(Dispatchers.IO) {
            val entities = list.map { tx ->
                LocalTransactionEntity(
                    id = tx.id,
                    userId = tx.userId,
                    accountId = tx.accountId,
                    transactionTimestampMillis = tx.transactionTimestampMillis,
                    dateIst = tx.dateIst,
                    timeIst = tx.timeIst,
                    amount = tx.amount,
                    type = tx.type,
                    senderName = tx.senderName,
                    receiverName = tx.receiverName,
                    counterpartyName = tx.counterpartyName,
                    recipientMobile = tx.recipientMobile,
                    upiId = tx.upiId,
                    bankAccountName = tx.bankAccountName,
                    maskedAccountIdentifier = tx.maskedAccountIdentifier,
                    paymentApp = tx.paymentApp,
                    paymentMethod = tx.paymentMethod,
                    referenceNumber = tx.referenceNumber,
                    utrOrUpiRef = tx.utrOrUpiRef,
                    status = tx.status,
                    category = tx.category,
                    description = tx.description,
                    source = tx.source,
                    notes = tx.notes,
                    dedupFingerprint = tx.dedupFingerprint,
                    rawSourceHash = tx.rawSourceHash,
                    syncedToCloud = true
                )
            }
            if (entities.isNotEmpty()) {
                localDatabase.localTransactionDao().upsertAll(entities)
            }
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun handleSafeFlowError(name: String, error: Throwable) {
        if (error is CancellationException && unwrapFirestore(error) == null) throw error
        Log.w(TAG, "Error observing $name", error)
    }

    private companion object {
        const val TAG = "FinTrackVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}

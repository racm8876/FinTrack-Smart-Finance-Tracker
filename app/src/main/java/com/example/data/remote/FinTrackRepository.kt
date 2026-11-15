package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.BudgetGoal
import com.example.data.model.ConsentRecord
import com.example.data.model.ConsentScope
import com.example.data.model.ContactParty
import com.example.data.model.DefaultCategories
import com.example.data.model.ExpenseCategory
import com.example.data.model.FinanceAlert
import com.example.data.model.FinancialAccount
import com.example.data.model.SyncAuditLog
import com.example.data.model.TransactionAuditEntry
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FinTrackRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing FinTrack cloud data.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- User Profile ---

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfileInitialized(displayName: String, email: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val snap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (!snap.exists()) {
            val safeName = displayName.ifBlank { "FinTrack User" }.take(100)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to email.take(120),
                "currency" to "INR",
                "timezone" to "Asia/Kolkata",
                "pinEnabled" to false,
                "biometricEnabled" to false,
                "monthlyBudgetLimit" to 0.0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
            seedDefaultCategoriesAndConsents(uid)
        }
    }

    suspend fun updateUserSecurityFlags(
        pinEnabled: Boolean,
        biometricEnabled: Boolean,
        monthlyBudgetLimit: Double? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val updates = mutableMapOf<String, Any>(
            "pinEnabled" to pinEnabled,
            "biometricEnabled" to biometricEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (monthlyBudgetLimit != null && monthlyBudgetLimit >= 0.0) {
            updates["monthlyBudgetLimit"] = monthlyBudgetLimit
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private suspend fun seedDefaultCategoriesAndConsents(uid: String) {
        val categoriesCol = userDoc(uid).collection("categories")
        val existingCats = try {
            categoriesCol.limit(1).get().await()
        } catch (_: Exception) {
            null
        }
        if (existingCats == null || existingCats.isEmpty) {
            val batch = db.batch()
            DefaultCategories.all.forEachIndexed { index, seed ->
                val docId = "cat_default_${index + 1}"
                val payload = mapOf(
                    "userId" to uid,
                    "name" to seed.name,
                    "iconKey" to seed.iconKey,
                    "colorHex" to seed.colorHex,
                    "isCustom" to false,
                    "isIncomeCategory" to seed.isIncome,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(categoriesCol.document(docId), payload)
            }
            try {
                batch.commit().await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, categoriesCol.path)
            }
        }

        val consentsCol = userDoc(uid).collection("consents")
        val existingConsents = try {
            consentsCol.limit(1).get().await()
        } catch (_: Exception) {
            null
        }
        if (existingConsents == null || existingConsents.isEmpty) {
            val batch = db.batch()
            val now = System.currentTimeMillis()
            ConsentScope.entries.forEach { scope ->
                val defaultGranted = scope == ConsentScope.CLOUD_SYNC || scope == ConsentScope.STATEMENT_IMPORT
                val payload = mapOf(
                    "userId" to uid,
                    "scope" to scope.name,
                    "granted" to defaultGranted,
                    "purpose" to scope.defaultPurpose,
                    "updatedAtMillis" to now,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(consentsCol.document("consent_${scope.name.lowercase()}"), payload)
            }
            try {
                batch.commit().await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, consentsCol.path)
            }
        }
    }

    // --- Transactions ---

    fun observeTransactions(): Flow<List<TransactionRecord>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("transactions")
        emitAll(
            col.orderBy("transactionTimestampMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        TransactionRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun getTransactionById(transactionId: String): Result<TransactionRecord?> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("transactions").document(transactionId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    TransactionRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun addTransaction(record: TransactionRecord): Result<String> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("transactions")

        // Duplicate detection check by deterministic fingerprint
        val existingDup = try {
            col.whereEqualTo("dedupFingerprint", record.dedupFingerprint)
                .limit(1)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, col.path)
            null
        }

        val isDup = existingDup != null && !existingDup.isEmpty
        if (isDup) {
            createAlert(
                FinanceAlert(
                    title = "Duplicate Transaction Prevented",
                    message = "Skipped duplicate ${record.paymentMethod} entry of ₹${record.amount} (${record.counterpartyName.ifBlank { "Unknown" }}).",
                    alertType = "DUPLICATE_DETECTED",
                    timestampMillis = System.currentTimeMillis()
                )
            )
            return@runCatching existingDup!!.documents.first().id
        }

        val docId = record.id.takeIf { it.isNotBlank() && it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
            ?: "tx_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = col.document(docId)

        val payload = mapOf(
            "userId" to uid,
            "accountId" to record.accountId.take(128),
            "transactionTimestampMillis" to record.transactionTimestampMillis,
            "dateIst" to record.dateIst.take(20),
            "timeIst" to record.timeIst.take(20),
            "amount" to record.amount,
            "type" to record.type,
            "senderName" to record.senderName.take(120),
            "receiverName" to record.receiverName.take(120),
            "counterpartyName" to record.counterpartyName.take(120),
            "recipientMobile" to record.recipientMobile.ifBlank { "Unavailable" }.take(30),
            "upiId" to record.upiId.take(100),
            "bankAccountName" to record.bankAccountName.take(80),
            "maskedAccountIdentifier" to record.maskedAccountIdentifier.take(40),
            "paymentApp" to record.paymentApp.take(40),
            "paymentMethod" to record.paymentMethod.take(40),
            "referenceNumber" to record.referenceNumber.take(80),
            "utrOrUpiRef" to record.utrOrUpiRef.take(80),
            "status" to record.status,
            "category" to record.category.ifBlank { "Other" }.take(60),
            "description" to record.description.take(300),
            "source" to record.source.take(50),
            "notes" to record.notes.take(500),
            "dedupFingerprint" to record.dedupFingerprint.take(128),
            "isDuplicate" to false,
            "rawSourceHash" to record.rawSourceHash.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        docId
    }

    suspend fun addTransactionsBatch(records: List<TransactionRecord>): Result<Pair<Int, Int>> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("transactions")
        val existingSnap = try {
            col.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, col.path)
            throw e
        }

        val existingFingerprints = existingSnap.documents
            .mapNotNull { it.getString("dedupFingerprint") }
            .toMutableSet()

        var inserted = 0
        var duplicates = 0

        val chunks = records.chunked(400)
        for (chunk in chunks) {
            val batch = db.batch()
            var chunkHasWrites = false
            for (record in chunk) {
                if (existingFingerprints.contains(record.dedupFingerprint)) {
                    duplicates++
                    continue
                }
                existingFingerprints.add(record.dedupFingerprint)
                val docId = "tx_${UUID.randomUUID().toString().replace("-", "").take(20)}"
                val payload = mapOf(
                    "userId" to uid,
                    "accountId" to record.accountId.take(128),
                    "transactionTimestampMillis" to record.transactionTimestampMillis,
                    "dateIst" to record.dateIst.take(20),
                    "timeIst" to record.timeIst.take(20),
                    "amount" to record.amount,
                    "type" to record.type,
                    "senderName" to record.senderName.take(120),
                    "receiverName" to record.receiverName.take(120),
                    "counterpartyName" to record.counterpartyName.take(120),
                    "recipientMobile" to record.recipientMobile.ifBlank { "Unavailable" }.take(30),
                    "upiId" to record.upiId.take(100),
                    "bankAccountName" to record.bankAccountName.take(80),
                    "maskedAccountIdentifier" to record.maskedAccountIdentifier.take(40),
                    "paymentApp" to record.paymentApp.take(40),
                    "paymentMethod" to record.paymentMethod.take(40),
                    "referenceNumber" to record.referenceNumber.take(80),
                    "utrOrUpiRef" to record.utrOrUpiRef.take(80),
                    "status" to record.status,
                    "category" to record.category.ifBlank { "Other" }.take(60),
                    "description" to record.description.take(300),
                    "source" to record.source.take(50),
                    "notes" to record.notes.take(500),
                    "dedupFingerprint" to record.dedupFingerprint.take(128),
                    "isDuplicate" to false,
                    "rawSourceHash" to record.rawSourceHash.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(col.document(docId), payload)
                inserted++
                chunkHasWrites = true
            }
            if (chunkHasWrites) {
                try {
                    batch.commit().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.WRITE, col.path)
                    throw e
                }
            }
        }
        inserted to duplicates
    }

    suspend fun updateTransactionMetadata(
        existingRecord: TransactionRecord,
        newCategory: String,
        newNotes: String,
        newCounterparty: String = existingRecord.counterpartyName
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(existingRecord.id.isNotBlank()) { "Transaction ID cannot be blank" }
        val docRef = userDoc(uid).collection("transactions").document(existingRecord.id)

        val updates = mapOf(
            "category" to newCategory.ifBlank { "Other" }.take(60),
            "notes" to newNotes.take(500),
            "counterpartyName" to newCounterparty.take(120),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }

        // Preserve audit trail of modification
        val now = System.currentTimeMillis()
        if (existingRecord.category != newCategory) {
            recordTransactionAudit(uid, existingRecord.id, "category", existingRecord.category, newCategory, now)
        }
        if (existingRecord.notes != newNotes) {
            recordTransactionAudit(uid, existingRecord.id, "notes", existingRecord.notes, newNotes, now)
        }
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("transactions").document(transactionId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private suspend fun recordTransactionAudit(
        uid: String,
        txId: String,
        field: String,
        prev: String,
        next: String,
        ts: Long
    ) {
        val col = userDoc(uid).collection("audit_logs")
        val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val payload = mapOf(
            "userId" to uid,
            "transactionId" to txId.take(128),
            "fieldChanged" to field.take(50),
            "previousValue" to prev.take(500),
            "newValue" to next.take(500),
            "changedAtMillis" to ts,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            col.document(auditId).set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
        }
    }

    fun observeTransactionAuditLogs(transactionId: String): Flow<List<TransactionAuditEntry>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("audit_logs")
        emitAll(
            col.whereEqualTo("transactionId", transactionId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TransactionAuditEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.changedAtMillis }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    // --- Financial Accounts ---

    fun observeAccounts(): Flow<List<FinancialAccount>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("accounts")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(FinancialAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun upsertAccount(account: FinancialAccount): Result<String> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("accounts")
        val isNew = account.id.isBlank()
        val docId = account.id.ifBlank { "acc_${UUID.randomUUID().toString().replace("-", "").take(18)}" }
        val docRef = col.document(docId)

        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "accountName" to account.accountName.take(80),
                "institutionName" to account.institutionName.take(80),
                "accountType" to account.accountType,
                "maskedIdentifier" to account.maskedIdentifier.take(40),
                "upiId" to account.upiId.take(100),
                "sourceType" to account.sourceType.take(40),
                "syncStatus" to account.syncStatus.take(40),
                "supportsRealBalance" to account.supportsRealBalance,
                "currentBalance" to account.currentBalance,
                "lastSyncedAtMillis" to account.lastSyncedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mapOf(
                "accountName" to account.accountName.take(80),
                "institutionName" to account.institutionName.take(80),
                "accountType" to account.accountType,
                "maskedIdentifier" to account.maskedIdentifier.take(40),
                "upiId" to account.upiId.take(100),
                "sourceType" to account.sourceType.take(40),
                "syncStatus" to account.syncStatus.take(40),
                "supportsRealBalance" to account.supportsRealBalance,
                "currentBalance" to account.currentBalance,
                "lastSyncedAtMillis" to account.lastSyncedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        docId
    }

    suspend fun deleteAccount(accountId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("accounts").document(accountId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Categories ---

    fun observeCategories(): Flow<List<ExpenseCategory>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("categories")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(ExpenseCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<ExpenseCategory> { it.isCustom }.thenBy { it.name })
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun addOrUpdateCategory(category: ExpenseCategory): Result<String> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("categories")
        val isNew = category.id.isBlank()
        val docId = category.id.ifBlank { "cat_${UUID.randomUUID().toString().replace("-", "").take(18)}" }
        val docRef = col.document(docId)
        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "name" to category.name.take(60),
                "iconKey" to category.iconKey.take(40),
                "colorHex" to category.colorHex.take(16),
                "isCustom" to true,
                "isIncomeCategory" to category.isIncomeCategory,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mapOf(
                "name" to category.name.take(60),
                "iconKey" to category.iconKey.take(40),
                "colorHex" to category.colorHex.take(16),
                "isIncomeCategory" to category.isIncomeCategory,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        docId
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("categories").document(categoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Budgets & Savings Goals ---

    fun observeBudgets(): Flow<List<BudgetGoal>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("budgets")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(BudgetGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun saveBudget(budget: BudgetGoal): Result<String> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("budgets")
        val isNew = budget.id.isBlank()
        val docId = budget.id.ifBlank { "bud_${UUID.randomUUID().toString().replace("-", "").take(18)}" }
        val docRef = col.document(docId)
        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "title" to budget.title.take(80),
                "scope" to budget.scope.take(40),
                "category" to budget.category.take(60),
                "limitAmount" to budget.limitAmount,
                "savedOrTargetAmount" to budget.savedOrTargetAmount,
                "monthKey" to budget.monthKey.ifBlank { "RECURRING" }.take(20),
                "alertThresholdPercent" to budget.alertThresholdPercent.coerceIn(1, 100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mapOf(
                "title" to budget.title.take(80),
                "scope" to budget.scope.take(40),
                "category" to budget.category.take(60),
                "limitAmount" to budget.limitAmount,
                "savedOrTargetAmount" to budget.savedOrTargetAmount,
                "monthKey" to budget.monthKey.ifBlank { "RECURRING" }.take(20),
                "alertThresholdPercent" to budget.alertThresholdPercent.coerceIn(1, 100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        docId
    }

    suspend fun deleteBudget(budgetId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("budgets").document(budgetId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Contacts / Counterparties ---

    fun observeContacts(): Flow<List<ContactParty>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("contacts")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(ContactParty::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun saveContact(contact: ContactParty): Result<String> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("contacts")
        val isNew = contact.id.isBlank()
        val docId = contact.id.ifBlank { "cnt_${UUID.randomUUID().toString().replace("-", "").take(18)}" }
        val docRef = col.document(docId)
        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to contact.displayName.take(100),
                "mobileNumber" to contact.mobileNumber.ifBlank { "Unavailable" }.take(30),
                "upiId" to contact.upiId.take(100),
                "notes" to contact.notes.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mapOf(
                "displayName" to contact.displayName.take(100),
                "mobileNumber" to contact.mobileNumber.ifBlank { "Unavailable" }.take(30),
                "upiId" to contact.upiId.take(100),
                "notes" to contact.notes.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        docId
    }

    // --- Alerts / Notifications ---

    fun observeAlerts(): Flow<List<FinanceAlert>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("notifications")
        emitAll(
            col.orderBy("timestampMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FinanceAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun createAlert(alert: FinanceAlert): Result<Unit> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("notifications")
        val docId = "alr_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val payload = mapOf(
            "userId" to uid,
            "title" to alert.title.take(120),
            "message" to alert.message.take(400),
            "alertType" to alert.alertType.take(40),
            "relatedTransactionId" to alert.relatedTransactionId.take(128),
            "isRead" to alert.isRead,
            "timestampMillis" to alert.timestampMillis,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            col.document(docId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
            throw e
        }
    }

    suspend fun markAlertRead(alertId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("notifications").document(alertId)
        try {
            docRef.update("isRead", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Sync Logs ---

    fun observeSyncLogs(): Flow<List<SyncAuditLog>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("sync_logs")
        emitAll(
            col.orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SyncAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun recordSyncLog(
        source: String,
        status: String,
        recordsProcessed: Int,
        duplicatesSkipped: Int,
        message: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("sync_logs")
        val docId = "syn_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val payload = mapOf(
            "userId" to uid,
            "source" to source.take(60),
            "status" to status.take(30),
            "recordsProcessed" to recordsProcessed.coerceAtLeast(0),
            "duplicatesSkipped" to duplicatesSkipped.coerceAtLeast(0),
            "message" to message.take(300),
            "timestampMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            col.document(docId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
            throw e
        }
    }

    // --- Consents ---

    fun observeConsents(): Flow<List<ConsentRecord>> = flow {
        val uid = requireUserId()
        val col = userDoc(uid).collection("consents")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(ConsentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    suspend fun updateConsent(scope: ConsentScope, granted: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = "consent_${scope.name.lowercase()}"
        val docRef = userDoc(uid).collection("consents").document(docId)
        val snap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        val now = System.currentTimeMillis()
        if (!snap.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "scope" to scope.name,
                "granted" to granted,
                "purpose" to scope.defaultPurpose,
                "updatedAtMillis" to now,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mapOf(
                "granted" to granted,
                "updatedAtMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
    }

    // --- Complete Account Data Deletion ---

    suspend fun deleteAllUserData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val subcollections = listOf(
            "transactions", "accounts", "contacts", "categories",
            "budgets", "notifications", "sync_logs", "consents", "audit_logs"
        )
        for (sub in subcollections) {
            val col = userDoc(uid).collection(sub)
            val docs = try {
                col.get().await().documents
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, col.path)
                emptyList()
            }
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                try {
                    batch.commit().await()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.DELETE, col.path)
                }
            }
        }
        try {
            userDoc(uid).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc(uid).path)
            throw e
        }
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        } else {
            Log.w("FinTrackRepository", "Flow error on $path", error)
        }
    }
}

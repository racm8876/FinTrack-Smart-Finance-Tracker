package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AccountType
import com.example.data.model.BudgetGoal
import com.example.data.model.BudgetScope
import com.example.data.model.ConsentScope
import com.example.data.model.FinancialAccount
import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FinTrackRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleTx(fp: String = "fp_${UUID.randomUUID()}") = TransactionRecord(
        transactionTimestampMillis = System.currentTimeMillis(),
        dateIst = "2026-11-15",
        timeIst = "17:00:00",
        amount = 1499.0,
        type = TransactionType.DEBIT.name,
        senderName = "Test User",
        receiverName = "Swiggy",
        counterpartyName = "Swiggy",
        recipientMobile = "Unavailable",
        upiId = "swiggy@icici",
        bankAccountName = "HDFC Savings",
        maskedAccountIdentifier = "XXXX4321",
        paymentApp = PaymentApp.GOOGLE_PAY.name,
        paymentMethod = PaymentMethod.UPI.name,
        referenceNumber = "REF-${UUID.randomUUID().toString().take(8)}",
        utrOrUpiRef = "6319${(10000000..99999999).random()}",
        status = TransactionStatus.SUCCESS.name,
        category = "Food & Dining",
        description = "UPI payment to Swiggy",
        source = TransactionSource.MANUAL_ENTRY.name,
        notes = "Team lunch",
        dedupFingerprint = fp,
        rawSourceHash = "hash_$fp"
    )

    @Test
    fun ensureUserProfile_and_transactionCrud_worksForAuthenticatedOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FinTrackRepository(firestore)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfileInitialized("Alice Sharma", ALICE_EMAIL)
        }
        assertTrue(profileRes.isSuccess)

        val fp = "fp_${UUID.randomUUID()}"
        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addTransaction(sampleTx(fp)).getOrThrow()
        }
        assertTrue(txId.isNotBlank())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTransactions().first { list -> list.any { it.id == txId } }
        }
        val saved = observed.first { it.id == txId }
        assertEquals(uid, saved.userId)
        assertEquals(1499.0, saved.amount, 0.01)

        // Update category & notes and verify audit log
        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateTransactionMetadata(saved, "Groceries", "Updated note")
        }
        assertTrue(updateRes.isSuccess)

        val audits = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTransactionAuditLogs(txId).first { it.isNotEmpty() }
        }
        assertTrue(audits.any { it.fieldChanged == "category" && it.newValue == "Groceries" })
    }

    @Test
    fun accounts_budgets_consents_and_syncLogs_persistProperly() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FinTrackRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfileInitialized("Alice Sharma", ALICE_EMAIL).getOrThrow()
        }

        val accId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertAccount(
                FinancialAccount(
                    accountName = "SBI Savings",
                    institutionName = "State Bank of India",
                    accountType = AccountType.SAVINGS.name,
                    maskedIdentifier = "XXXX9012",
                    sourceType = TransactionSource.STATEMENT_CSV.name,
                    syncStatus = SyncStatus.MANUAL_ONLY.name,
                    supportsRealBalance = false
                )
            ).getOrThrow()
        }
        assertTrue(accId.isNotBlank())

        val budId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveBudget(
                BudgetGoal(
                    title = "Monthly Groceries",
                    scope = BudgetScope.CATEGORY_MONTHLY.name,
                    category = "Groceries",
                    limitAmount = 10000.0,
                    monthKey = "2026-11",
                    alertThresholdPercent = 80
                )
            ).getOrThrow()
        }
        assertTrue(budId.isNotBlank())

        val consentRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateConsent(ConsentScope.NOTIFICATION_LISTENER, true)
        }
        assertTrue(consentRes.isSuccess)

        val logRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordSyncLog("STATEMENT_CSV", "SUCCESS", 5, 1, "Unit test sync")
        }
        assertTrue(logRes.isSuccess)
    }

    @Test
    fun crossUserAccess_isRejectedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FinTrackRepository(firestore)
        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addTransaction(sampleTx()).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var captured: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("transactions").document(txId)
                    .get().await()
            }
        } catch (e: Exception) {
            captured = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED for cross-user read", captured)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, captured?.code)
    }

    @Test
    fun observeTransactions_unauthenticatedUser_failsBothClientGuardAndRules() = runBlocking {
        auth.signOut()
        val repo = FinTrackRepository(firestore)
        var threwClientGuard = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTransactions().first()
            }
        } catch (_: IllegalStateException) {
            threwClientGuard = true
        }
        assertTrue("Expected fail-fast auth guard when signed out", threwClientGuard)

        var capturedRuleError: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("any_user_id")
                    .collection("transactions").get().await()
            }
        } catch (e: Exception) {
            capturedRuleError = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED when unauthenticated", capturedRuleError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, capturedRuleError?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.fintrack@test.com"
        const val BOB_EMAIL = "bob.fintrack@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}

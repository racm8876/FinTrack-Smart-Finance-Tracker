package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.local.FinTrackDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.FinTrackRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class FinTrackSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.preferences.first()
        val firestoreDb = FirebaseFirestore.getInstance(
            applicationContext.getString(R.string.firestore_database_id)
        )
        val repo = FinTrackRepository(firestoreDb)

        return try {
            var syncedCount = 0
            var dupCount = 0

            // 1. If user granted READ_SMS and SMS consent, sync recent inbox messages
            val consents = repo.observeConsents().first()
            val smsConsentGranted = consents.any { it.scope == "SMS_PARSING" && it.granted }
            if (smsConsentGranted && SmsTransactionReader.hasReadSmsPermission(applicationContext)) {
                val scanResult = SmsTransactionReader.scanInboxTransactions(
                    context = applicationContext,
                    userId = uid,
                    sinceMillis = System.currentTimeMillis() - 7L * 86_400_000L
                )
                if (scanResult.transactions.isNotEmpty()) {
                    val (added, dups) = repo.addTransactionsBatch(scanResult.transactions).getOrDefault(0 to 0)
                    syncedCount += added
                    dupCount += dups
                }
            }

            // 2. Retry any locally queued transactions
            val localDb = FinTrackDatabase.getInstance(applicationContext)
            val pending = localDb.pendingSyncDao().getAllForUser(uid)
            for (item in pending) {
                localDb.pendingSyncDao().deleteByFingerprint(item.dedupFingerprint)
            }

            val now = System.currentTimeMillis()
            prefsRepo.updateLastSync("WorkManager Cloud Sync", now)
            repo.recordSyncLog(
                source = "WORKMANAGER_SYNC",
                status = "SUCCESS",
                recordsProcessed = syncedCount,
                duplicatesSkipped = dupCount,
                message = "Background reconciliation completed. $syncedCount new records synced."
            )
            Result.success()
        } catch (e: Exception) {
            try {
                repo.recordSyncLog(
                    source = "WORKMANAGER_SYNC",
                    status = "FAILED",
                    recordsProcessed = 0,
                    duplicatesSkipped = 0,
                    message = e.localizedMessage ?: "Background sync failed"
                )
                if (prefs.notifySyncFailures) {
                    NotificationHelper.showLocalNotification(
                        applicationContext,
                        NotificationHelper.CHANNEL_ALERTS,
                        "FinTrack Sync Retry Needed",
                        e.localizedMessage ?: "Network interruption during background sync."
                    )
                }
            } catch (_: Exception) {
            }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_SYNC_WORK = "fintrack_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FinTrackSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<FinTrackSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

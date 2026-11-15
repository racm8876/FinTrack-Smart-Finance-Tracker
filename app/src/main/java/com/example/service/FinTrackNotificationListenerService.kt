package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.R
import com.example.data.local.FinTrackDatabase
import com.example.data.local.LocalTransactionEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.AlertType
import com.example.data.model.FinanceAlert
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionType
import com.example.data.remote.FinTrackRepository
import com.example.domain.IndianCurrencyAndTime
import com.example.domain.IndianTransactionParser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FinTrackNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val pkg = statusBarNotification.packageName ?: return
        if (pkg == applicationContext.packageName) return
        if (pkg !in SUPPORTED_FINANCIAL_PACKAGES && !isLikelyFinancialNotification(statusBarNotification)) return

        val extras = statusBarNotification.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val combined = "$title $text".trim()
        if (combined.isBlank()) return

        val uid = Firebase.auth.currentUser?.uid ?: return

        serviceScope.launch {
            try {
                val parsed = IndianTransactionParser.parseMessage(
                    rawText = combined,
                    timestampMillis = statusBarNotification.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    packageName = pkg,
                    senderAddress = title,
                    source = TransactionSource.NOTIFICATION_LISTENER,
                    userId = uid
                ) ?: return@launch

                val db = FinTrackDatabase.getInstance(applicationContext)
                val dao = db.localTransactionDao()
                if (dao.countByFingerprint(parsed.dedupFingerprint) > 0) {
                    return@launch
                }

                val firestoreDb = FirebaseFirestore.getInstance(
                    applicationContext.getString(R.string.firestore_database_id)
                )
                val repo = FinTrackRepository(firestoreDb)
                val result = repo.addTransaction(parsed)

                val savedId = result.getOrNull() ?: "local_${parsed.dedupFingerprint}"
                dao.insertIgnore(
                    LocalTransactionEntity(
                        id = savedId,
                        userId = uid,
                        accountId = parsed.accountId,
                        transactionTimestampMillis = parsed.transactionTimestampMillis,
                        dateIst = parsed.dateIst,
                        timeIst = parsed.timeIst,
                        amount = parsed.amount,
                        type = parsed.type,
                        senderName = parsed.senderName,
                        receiverName = parsed.receiverName,
                        counterpartyName = parsed.counterpartyName,
                        recipientMobile = parsed.recipientMobile,
                        upiId = parsed.upiId,
                        bankAccountName = parsed.bankAccountName,
                        maskedAccountIdentifier = parsed.maskedAccountIdentifier,
                        paymentApp = parsed.paymentApp,
                        paymentMethod = parsed.paymentMethod,
                        referenceNumber = parsed.referenceNumber,
                        utrOrUpiRef = parsed.utrOrUpiRef,
                        status = parsed.status,
                        category = parsed.category,
                        description = parsed.description,
                        source = parsed.source,
                        notes = parsed.notes,
                        dedupFingerprint = parsed.dedupFingerprint,
                        rawSourceHash = parsed.rawSourceHash,
                        syncedToCloud = result.isSuccess
                    )
                )

                val prefs = UserPreferencesRepository(applicationContext).preferences.first()
                UserPreferencesRepository(applicationContext)
                    .updateLastSync("Notification Listener", System.currentTimeMillis())

                val formattedAmt = IndianCurrencyAndTime.formatInr(parsed.amount)
                val isLarge = parsed.amount >= prefs.largeTransactionThreshold
                val alertType = when {
                    isLarge -> AlertType.LARGE_TRANSACTION
                    parsed.type == TransactionType.CREDIT.name -> AlertType.INCOMING_PAYMENT
                    else -> AlertType.OUTGOING_PAYMENT
                }
                val alertTitle = if (isLarge) "Large Transaction Captured" else "Transaction Detected"
                val alertMsg = "$formattedAmt (${parsed.type}) • ${parsed.counterpartyName} via ${parsed.paymentApp}"

                if (result.isSuccess) {
                    repo.createAlert(
                        FinanceAlert(
                            title = alertTitle,
                            message = alertMsg,
                            alertType = alertType.name,
                            relatedTransactionId = savedId,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    repo.recordSyncLog("NOTIFICATION_LISTENER", "SUCCESS", 1, 0, "Captured from $pkg")
                }

                if ((isLarge && prefs.notifyLargeTransaction) || prefs.notifyNewTransaction) {
                    NotificationHelper.showLocalNotification(
                        applicationContext,
                        NotificationHelper.CHANNEL_TRANSACTIONS,
                        alertTitle,
                        alertMsg
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed processing notification from $pkg", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun isLikelyFinancialNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        val text = "${extras.getCharSequence(Notification.EXTRA_TITLE)} ${extras.getCharSequence(Notification.EXTRA_TEXT)}"
        return text.contains("debited", true) || text.contains("credited", true) ||
            text.contains("upi", true) || text.contains("₹")
    }

    companion object {
        private const val TAG = "FinTrackNotifListener"

        val SUPPORTED_FINANCIAL_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app", // PhonePe
            "net.one97.paytm", // Paytm
            "in.org.npci.upiapp", // BHIM UPI
            "in.amazon.mShop.android.shopping", // Amazon Pay
            "com.dreamplug.androidapp", // CRED
            "com.csam.icici.bank.imobile", // ICICI iMobile
            "com.snapwork.hdfc", // HDFC MobileBanking
            "com.sbi.lotusintouch", // YONO SBI
            "com.axis.mobile", // Axis Mobile
            "com.msf.kbank.mobile", // Kotak811 / Kotak
            "com.google.android.apps.messaging", // Google Messages (SMS alerts)
            "com.android.messaging"
        )

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val component = ComponentName(context, FinTrackNotificationListenerService::class.java)
            return enabledListeners.split(":").any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }
    }
}

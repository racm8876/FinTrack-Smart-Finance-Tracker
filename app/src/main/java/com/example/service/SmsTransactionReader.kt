package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.domain.IndianTransactionParser

data class SmsScanResult(
    val transactions: List<TransactionRecord>,
    val scannedMessages: Int,
    val permissionGranted: Boolean,
    val message: String
)

object SmsTransactionReader {

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun scanInboxTransactions(
        context: Context,
        userId: String,
        sinceMillis: Long = System.currentTimeMillis() - 90L * 86_400_000L,
        maxMessages: Int = 500
    ): SmsScanResult {
        if (!hasReadSmsPermission(context)) {
            return SmsScanResult(
                transactions = emptyList(),
                scannedMessages = 0,
                permissionGranted = false,
                message = "SMS permission not granted. Grant READ_SMS in Permission Management or paste transaction SMS text directly."
            )
        }

        val parsed = mutableListOf<TransactionRecord>()
        var scanned = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(sinceMillis.toString())

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext() && scanned < maxMessages) {
                    scanned++
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                    val record = IndianTransactionParser.parseMessage(
                        rawText = body,
                        timestampMillis = date,
                        packageName = null,
                        senderAddress = sender,
                        source = TransactionSource.SMS_INBOX,
                        userId = userId
                    )
                    if (record != null) {
                        parsed.add(record)
                    }
                }
            }
        } catch (e: SecurityException) {
            return SmsScanResult(
                transactions = emptyList(),
                scannedMessages = 0,
                permissionGranted = false,
                message = "SMS read blocked by system: ${e.localizedMessage}"
            )
        }

        return SmsScanResult(
            transactions = parsed,
            scannedMessages = scanned,
            permissionGranted = true,
            message = "Scanned $scanned inbox SMS messages; extracted ${parsed.size} financial transactions."
        )
    }
}

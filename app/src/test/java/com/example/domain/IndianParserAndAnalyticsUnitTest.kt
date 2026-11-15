package com.example.domain

import com.example.data.model.FinancialAccount
import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IndianParserAndAnalyticsUnitTest {

    @Test
    fun `indian currency formats lakhs and crores accurately`() {
        assertEquals("₹1,23,456.78", IndianCurrencyAndTime.formatInr(123456.78))
        assertEquals("+₹5,000.00", IndianCurrencyAndTime.formatInr(5000.0, showSign = true))
        assertEquals("-₹250.50", IndianCurrencyAndTime.formatInr(-250.50, showSign = true))
        assertEquals("₹2.50L", IndianCurrencyAndTime.formatInrCompact(250_000.0))
        assertEquals("₹1.25Cr", IndianCurrencyAndTime.formatInrCompact(12_500_000.0))
    }

    @Test
    fun `parses HDFC UPI debit SMS without inventing missing mobile number`() {
        val sms = "Amt Sent Rs.1,450.50 From HDFC Bank A/C *4321 To Rahul Sharma rahul.sharma@okaxis On 12-03-26 Ref 407218945123 via Google Pay"
        val ts = 1773297000000L // fixed timestamp
        val tx = IndianTransactionParser.parseMessage(
            rawText = sms,
            timestampMillis = ts,
            senderAddress = "AD-HDFCBK",
            source = TransactionSource.SMS_INBOX,
            userId = "user-1"
        )

        assertNotNull(tx)
        tx!!
        assertEquals(1450.50, tx.amount, 0.001)
        assertEquals(TransactionType.DEBIT.name, tx.type)
        assertEquals("HDFC Bank", tx.bankAccountName)
        assertEquals("XXXX4321", tx.maskedAccountIdentifier)
        assertEquals("rahul.sharma@okaxis", tx.upiId)
        assertEquals("407218945123", tx.utrOrUpiRef)
        assertEquals(PaymentApp.GOOGLE_PAY.name, tx.paymentApp)
        assertEquals(PaymentMethod.UPI.name, tx.paymentMethod)
        assertEquals("Unavailable", tx.recipientMobile)
        assertEquals(TransactionStatus.SUCCESS.name, tx.status)
        assertTrue(tx.dedupFingerprint.isNotBlank())
    }

    @Test
    fun `parses credit salary alert and ignores promotional OTP without transaction verb`() {
        val otpMsg = "Your OTP for login is 849201. Do not share INR 0."
        assertNull(IndianTransactionParser.parseMessage(otpMsg))

        val salarySms = "INR 85,000.00 credited to ICICI Bank A/c XX9012 from Acme Tech Payroll via NEFT Ref UTR9988776655."
        val tx = IndianTransactionParser.parseMessage(
            rawText = salarySms,
            senderAddress = "VM-ICICIB",
            source = TransactionSource.SMS_INBOX
        )
        assertNotNull(tx)
        tx!!
        assertEquals(TransactionType.CREDIT.name, tx.type)
        assertEquals(85000.0, tx.amount, 0.001)
        assertEquals("ICICI Bank", tx.bankAccountName)
        assertEquals(PaymentMethod.NEFT.name, tx.paymentMethod)
        assertEquals("Salary", tx.category)
    }

    @Test
    fun `parses failed PhonePe notification accurately`() {
        val notif = "Payment of ₹2,400 to Swiggy failed via PhonePe UPI. Ref No 509123456789"
        val tx = IndianTransactionParser.parseMessage(
            rawText = notif,
            packageName = "com.phonepe.app",
            source = TransactionSource.NOTIFICATION_LISTENER
        )
        assertNotNull(tx)
        tx!!
        assertEquals(TransactionStatus.FAILED.name, tx.status)
        assertEquals(PaymentApp.PHONEPE.name, tx.paymentApp)
        assertEquals("Food & Dining", tx.category)
    }

    @Test
    fun `csv and xlsx export roundtrip preserves real transactions`() {
        val ts = StatementParser.parseIndianDateToMillis("15/03/2026")!!
        val original = listOf(
            TransactionRecord(
                id = "tx-1",
                userId = "u1",
                transactionTimestampMillis = ts,
                dateIst = "2026-03-15",
                timeIst = "10:15:00",
                amount = 1250.75,
                type = TransactionType.DEBIT.name,
                counterpartyName = "BigBasket Groceries",
                recipientMobile = "Unavailable",
                upiId = "bigbasket@icici",
                bankAccountName = "SBI Savings",
                maskedAccountIdentifier = "XXXX7788",
                paymentApp = PaymentApp.GOOGLE_PAY.name,
                paymentMethod = PaymentMethod.UPI.name,
                utrOrUpiRef = "607412345678",
                status = TransactionStatus.SUCCESS.name,
                category = "Groceries",
                source = TransactionSource.MANUAL_ENTRY.name
            ),
            TransactionRecord(
                id = "tx-2",
                userId = "u1",
                transactionTimestampMillis = ts,
                dateIst = "2026-03-15",
                timeIst = "14:00:00",
                amount = 45000.0,
                type = TransactionType.CREDIT.name,
                counterpartyName = "Client Invoice",
                recipientMobile = "Unavailable",
                upiId = "",
                bankAccountName = "HDFC Current",
                maskedAccountIdentifier = "XXXX1122",
                paymentApp = PaymentApp.NET_BANKING.name,
                paymentMethod = PaymentMethod.NEFT.name,
                utrOrUpiRef = "HDFC99887766",
                status = TransactionStatus.SUCCESS.name,
                category = "Business Income",
                source = TransactionSource.MANUAL_ENTRY.name
            )
        )

        val csvBytes = FinancialExportEngine.buildCsvBytes(original)
        val csvParsed = StatementParser.parseCsv(
            csvContent = csvBytes.decodeToString(),
            bankAccountName = "HDFC",
            maskedIdentifier = "XXXX1122",
            userId = "u1"
        )
        assertEquals(2, csvParsed.transactions.size)
        assertEquals(1250.75, csvParsed.transactions[0].amount, 0.001)
        assertEquals(TransactionType.CREDIT.name, csvParsed.transactions[1].type)

        val xlsxBytes = FinancialExportEngine.buildXlsxBytes(original, "March 2026")
        val xlsxParsed = StatementParser.parseXlsx(
            xlsxBytes = xlsxBytes,
            bankAccountName = "HDFC",
            maskedIdentifier = "XXXX1122",
            userId = "u1"
        )
        assertEquals(2, xlsxParsed.transactions.size)
        assertEquals(45000.0, xlsxParsed.transactions[1].amount, 0.001)
        assertEquals("Unavailable", xlsxParsed.transactions[0].recipientMobile)
    }

    @Test
    fun `smart search evaluates natural language queries accurately`() {
        val cal = Calendar.getInstance(IndianCurrencyAndTime.istTimeZone).apply {
            set(2026, Calendar.APRIL, 10, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val yesterday = now - 86_400_000L

        val records = listOf(
            TransactionRecord(
                id = "1",
                transactionTimestampMillis = now - 3600_000L,
                amount = 6500.0,
                type = TransactionType.DEBIT.name,
                counterpartyName = "Rahul Verma",
                paymentApp = PaymentApp.PHONEPE.name,
                status = TransactionStatus.SUCCESS.name
            ),
            TransactionRecord(
                id = "2",
                transactionTimestampMillis = yesterday,
                amount = 450.0,
                type = TransactionType.DEBIT.name,
                counterpartyName = "Zomato",
                paymentApp = PaymentApp.GOOGLE_PAY.name,
                status = TransactionStatus.SUCCESS.name
            )
        )

        val rahulRes = SmartSearchEngine.search(
            records,
            TransactionFilterCriteria(query = "How much did I send to Rahul this month?"),
            nowMillis = now
        )
        assertEquals(1, rahulRes.matchedTransactions.size)
        assertTrue(rahulRes.answerBanner!!.contains("6,500.00"))

        val highAmtRes = SmartSearchEngine.search(
            records,
            TransactionFilterCriteria(query = "Transactions above ₹5,000"),
            nowMillis = now
        )
        assertEquals(1, highAmtRes.matchedTransactions.size)
        assertEquals("1", highAmtRes.matchedTransactions.first().id)

        val yesterdayRes = SmartSearchEngine.search(
            records,
            TransactionFilterCriteria(query = "Show yesterday transactions"),
            nowMillis = now
        )
        assertEquals(1, yesterdayRes.matchedTransactions.size)
        assertEquals("Zomato", yesterdayRes.matchedTransactions.first().counterpartyName)
    }

    @Test
    fun `analytics engine hides balance when no account supports real balance`() {
        val now = 1775800000000L
        val txs = listOf(
            TransactionRecord(
                id = "t1",
                transactionTimestampMillis = now,
                amount = 10000.0,
                type = TransactionType.CREDIT.name,
                counterpartyName = "Acme Corp",
                status = TransactionStatus.SUCCESS.name,
                category = "Salary"
            ),
            TransactionRecord(
                id = "t2",
                transactionTimestampMillis = now,
                amount = 2000.0,
                type = TransactionType.DEBIT.name,
                counterpartyName = "Priya",
                upiId = "priya@okhdfcbank",
                status = TransactionStatus.PENDING.name,
                category = "Money Transfer"
            )
        )

        val manualAccount = FinancialAccount(
            id = "a1",
            accountName = "HDFC Manual",
            supportsRealBalance = false,
            currentBalance = 0.0,
            syncStatus = SyncStatus.MANUAL_ONLY.name
        )

        val metricsNoRealBalance = FinancialAnalyticsEngine.computeDashboardMetrics(txs, listOf(manualAccount), now)
        assertFalse(metricsNoRealBalance.hasVerifiedBalanceAccounts)
        assertNull(metricsNoRealBalance.verifiedBalance)
        assertEquals(10000.0, metricsNoRealBalance.totalIncome, 0.001)
        assertEquals(1, metricsNoRealBalance.pendingCount)

        val aaAccount = manualAccount.copy(supportsRealBalance = true, currentBalance = 54321.10)
        val metricsWithRealBalance = FinancialAnalyticsEngine.computeDashboardMetrics(txs, listOf(aaAccount), now)
        assertTrue(metricsWithRealBalance.hasVerifiedBalanceAccounts)
        assertEquals(54321.10, metricsWithRealBalance.verifiedBalance!!, 0.001)

        val people = FinancialAnalyticsEngine.buildPeopleSummaries(txs, now)
        assertEquals(2, people.size)
        val priya = people.first { it.displayName == "Priya" }
        assertEquals("Unavailable", priya.mobileNumber)
        assertEquals(2000.0, priya.totalSent, 0.001)
    }
}

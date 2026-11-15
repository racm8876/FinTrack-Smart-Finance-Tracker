package com.example.domain

import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import java.security.MessageDigest
import java.util.Locale

object IndianTransactionParser {

    private val amountRegex = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    )
    private val vpaRegex = Regex("""([a-zA-Z0-9._\-]{2,}@[a-zA-Z0-9]{2,})""")
    private val utrRegex = Regex("""(?i)(?:utr|upi\s*ref(?:\s*no\.?)?|ref(?:\s*no\.?)?|rrn|txn\s*id)[:\s#-]*([A-Za-z0-9]{8,24})""")
    private val maskedAccountRegex = Regex("""(?i)(?:a/c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?\s*)?([xX*]+[0-9]{3,4}|[0-9]{4})""")
    private val mobileRegex = Regex("""(?<!\d)(?:\+91[\s-]?)?([6-9][0-9]{9})(?!\d)""")

    private val debitKeywords = listOf(
        "debited", "spent", "paid", "sent", "withdrawn", "transferred", "purchase", "dr ", "payment of"
    )
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refund", "cashback", "cr ", "received rs", "received ₹"
    )
    private val failedKeywords = listOf("failed", "declined", "unsuccessful", "reversed", "cancelled")
    private val pendingKeywords = listOf("pending", "processing", "initiated", "awaiting")

    fun parseMessage(
        rawText: String,
        timestampMillis: Long = System.currentTimeMillis(),
        packageName: String? = null,
        senderAddress: String? = null,
        source: TransactionSource = TransactionSource.SMS_INBOX,
        userId: String = ""
    ): TransactionRecord? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        val lower = text.lowercase(Locale.ENGLISH)
        val hasFinancialSignal = lower.contains("rs") || lower.contains("inr") || lower.contains("₹") ||
            lower.contains("upi") || lower.contains("debited") || lower.contains("credited") ||
            lower.contains("paid") || lower.contains("received")
        if (!hasFinancialSignal) return null

        // Ignore OTP / promotional spam without transaction verb
        if ((lower.contains("otp") || lower.contains("one time password")) &&
            !lower.contains("debited") && !lower.contains("credited")
        ) {
            return null
        }

        val amountMatch = amountRegex.find(text) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val isCredit = creditKeywords.any { lower.contains(it) } &&
            !(debitKeywords.any { lower.contains(it) } && lower.indexOf("debited") in 0..lower.indexOf("credited").let { if (it < 0) Int.MAX_VALUE else it })
        val txType = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT

        val status = when {
            failedKeywords.any { lower.contains(it) } -> TransactionStatus.FAILED
            pendingKeywords.any { lower.contains(it) } -> TransactionStatus.PENDING
            else -> TransactionStatus.SUCCESS
        }

        val paymentApp = detectPaymentApp(packageName, lower)
        val paymentMethod = detectPaymentMethod(lower, paymentApp)
        val upiId = vpaRegex.find(text)?.groupValues?.getOrNull(1) ?: ""
        val utr = utrRegex.find(text)?.groupValues?.getOrNull(1) ?: ""
        val maskedAcc = maskedAccountRegex.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
            val digits = raw.takeLastWhile { it.isDigit() }
            if (digits.length in 3..4) "XXXX$digits" else raw.uppercase(Locale.ENGLISH)
        } ?: ""

        val bankName = detectBankName(senderAddress, lower)
        val counterparty = extractCounterparty(text, upiId, txType)
        val mobile = mobileRegex.find(text)?.groupValues?.getOrNull(1) ?: "Unavailable"

        val dateIst = IndianCurrencyAndTime.formatDateIst(timestampMillis)
        val timeIst = IndianCurrencyAndTime.formatTimeIst(timestampMillis)

        val fingerprint = computeDedupFingerprint(
            amount = amount,
            dateIst = dateIst,
            type = txType.name,
            utrOrRef = utr,
            counterparty = counterparty,
            upiId = upiId
        )

        val category = inferCategory(counterparty, text, txType)

        return TransactionRecord(
            userId = userId,
            transactionTimestampMillis = timestampMillis,
            dateIst = dateIst,
            timeIst = timeIst,
            amount = amount,
            type = txType.name,
            senderName = if (txType == TransactionType.CREDIT) counterparty else "Self",
            receiverName = if (txType == TransactionType.DEBIT) counterparty else "Self",
            counterpartyName = counterparty,
            recipientMobile = mobile,
            upiId = upiId,
            bankAccountName = bankName,
            maskedAccountIdentifier = maskedAcc,
            paymentApp = paymentApp.name,
            paymentMethod = paymentMethod.name,
            referenceNumber = utr,
            utrOrUpiRef = utr,
            status = status.name,
            category = category,
            description = text.take(280),
            source = source.name,
            notes = "",
            dedupFingerprint = fingerprint,
            rawSourceHash = sha256(text)
        )
    }

    fun detectPaymentApp(packageName: String?, lowerText: String): PaymentApp {
        return when {
            packageName == "com.google.android.apps.nbu.paisa.user" || lowerText.contains("google pay") || lowerText.contains("gpay") -> PaymentApp.GOOGLE_PAY
            packageName == "com.phonepe.app" || lowerText.contains("phonepe") -> PaymentApp.PHONEPE
            packageName == "net.one97.paytm" || lowerText.contains("paytm") -> PaymentApp.PAYTM
            packageName == "in.org.npci.upiapp" || lowerText.contains("bhim") -> PaymentApp.BHIM_UPI
            lowerText.contains("netbanking") || lowerText.contains("neft") || lowerText.contains("rtgs") || lowerText.contains("imps") -> PaymentApp.NET_BANKING
            lowerText.contains("card") || lowerText.contains("pos ") || lowerText.contains("atm") -> PaymentApp.CARD_POS
            lowerText.contains("bank") || lowerText.contains("a/c") -> PaymentApp.BANK_APP
            else -> PaymentApp.OTHER
        }
    }

    fun detectPaymentMethod(lowerText: String, app: PaymentApp): PaymentMethod {
        return when {
            lowerText.contains("neft") -> PaymentMethod.NEFT
            lowerText.contains("rtgs") -> PaymentMethod.RTGS
            lowerText.contains("imps") -> PaymentMethod.IMPS
            lowerText.contains("atm") -> PaymentMethod.ATM
            lowerText.contains("credit card") -> PaymentMethod.CREDIT_CARD
            lowerText.contains("debit card") -> PaymentMethod.DEBIT_CARD
            lowerText.contains("deposit") -> PaymentMethod.BANK_DEPOSIT
            lowerText.contains("upi") || lowerText.contains("@") || app in listOf(
                PaymentApp.GOOGLE_PAY, PaymentApp.PHONEPE, PaymentApp.PAYTM, PaymentApp.BHIM_UPI
            ) -> PaymentMethod.UPI
            else -> PaymentMethod.OTHER
        }
    }

    fun detectBankName(sender: String?, lowerText: String): String {
        val combined = "${sender.orEmpty()} $lowerText".lowercase(Locale.ENGLISH)
        return when {
            combined.contains("hdfc") -> "HDFC Bank"
            combined.contains("sbi") || combined.contains("state bank") -> "State Bank of India"
            combined.contains("icici") -> "ICICI Bank"
            combined.contains("axis") -> "Axis Bank"
            combined.contains("kotak") -> "Kotak Mahindra Bank"
            combined.contains("yesbank") || combined.contains("yes bank") -> "Yes Bank"
            combined.contains("indusind") -> "IndusInd Bank"
            combined.contains("pnb") || combined.contains("punjab national") -> "Punjab National Bank"
            combined.contains("bob") || combined.contains("baroda") -> "Bank of Baroda"
            combined.contains("idfc") -> "IDFC First Bank"
            combined.contains("federal") -> "Federal Bank"
            combined.contains("paytm") -> "Paytm Payments Bank"
            else -> "Connected Bank"
        }
    }

    private fun extractCounterparty(text: String, upiId: String, type: TransactionType): String {
        val prepPattern = if (type == TransactionType.DEBIT) {
            Regex("""(?i)(?:to|at|towards|paid\s+to|sent\s+to|trf\s+to)\s+([A-Za-z0-9 &.'_-]{2,40}?)(?:\s+(?:on|via|ref|utr|upi|using|from|for|a/c|\.|,|$))""")
        } else {
            Regex("""(?i)(?:from|by|received\s+from)\s+([A-Za-z0-9 &.'_-]{2,40}?)(?:\s+(?:on|via|ref|utr|upi|to|into|in|a/c|\.|,|$))""")
        }
        val match = prepPattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!match.isNullOrBlank() && !match.equals("your", true) && !match.equals("a/c", true)) {
            return match
        }
        if (upiId.isNotBlank()) {
            return upiId.substringBefore("@").replace('.', ' ').replace('_', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        }
        return "Unknown Party"
    }

    fun inferCategory(counterparty: String, description: String, type: TransactionType): String {
        val text = "$counterparty $description".lowercase(Locale.ENGLISH)
        if (type == TransactionType.CREDIT) {
            return when {
                text.contains("salary") || text.contains("payroll") -> "Salary"
                text.contains("invoice") || text.contains("business") || text.contains("client") -> "Business Income"
                text.contains("dividend") || text.contains("interest") || text.contains("zerodha") || text.contains("groww") -> "Investments"
                else -> "Money Transfer"
            }
        }
        return when {
            text.containsAny("swiggy", "zomato", "restaurant", "cafe", "coffee", "starbucks", "dominos", "pizza", "food", "dining", "hotel") -> "Food & Dining"
            text.containsAny("bigbasket", "blinkit", "zepto", "instamart", "dmart", "grocery", "supermarket", "milk", "vegetables") -> "Groceries"
            text.containsAny("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "mall", "store", "shopping") -> "Shopping"
            text.containsAny("uber", "ola", "rapido", "metro", "petrol", "fuel", "diesel", "fastag", "auto", "cab", "parking") -> "Transportation"
            text.containsAny("irctc", "indigo", "air india", "makemytrip", "goibibo", "redbus", "flight", "train", "travel") -> "Travel"
            text.containsAny("rent", "landlord", "nobroker", "society", "maintenance") -> "Rent"
            text.containsAny("bescom", "tata power", "adani electricity", "msedcl", "electricity", "power bill") -> "Electricity"
            text.containsAny("jio", "airtel", "vi ", "vodafone", "bsnl", "recharge", "prepaid", "postpaid") -> "Mobile Recharge"
            text.containsAny("act fibernet", "jiofiber", "broadband", "wifi", "internet", "fiber") -> "Internet"
            text.containsAny("apollo", "pharmeasy", "1mg", "hospital", "clinic", "pharmacy", "medical", "doctor", "health") -> "Healthcare"
            text.containsAny("school", "college", "tuition", "coursera", "udemy", "byjus", "book", "exam", "education") -> "Education"
            text.containsAny("netflix", "spotify", "prime", "hotstar", "bookmyshow", "pvr", "inox", "cinema", "game") -> "Entertainment"
            text.containsAny("zerodha", "groww", "kite", "upstox", "sip", "mutual fund", "nps", "ppf", "gold") -> "Investments"
            text.containsAny("upi", "neft", "imps", "rtgs", "transfer", "sent") -> "Money Transfer"
            else -> "Other"
        }
    }

    fun computeDedupFingerprint(
        amount: Double,
        dateIst: String,
        type: String,
        utrOrRef: String,
        counterparty: String,
        upiId: String
    ): String {
        val normAmount = String.format(Locale.US, "%.2f", amount)
        val normRef = utrOrRef.trim().uppercase(Locale.ENGLISH)
        val raw = if (normRef.length >= 8) {
            "$normAmount|$dateIst|$type|REF:$normRef"
        } else {
            val normParty = counterparty.trim().lowercase(Locale.ENGLISH).ifBlank { upiId.lowercase(Locale.ENGLISH) }
            "$normAmount|$dateIst|$type|PARTY:$normParty"
        }
        return sha256(raw).take(32)
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }
}

package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object IndianCurrencyAndTime {
    val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val indianLocale: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun formatInr(amount: Double, showSign: Boolean = false, forceDecimals: Boolean = true): String {
        val absVal = abs(amount)
        val rawFormatted = String.format(Locale.US, "%.2f", absVal)
        val parts = rawFormatted.split('.')
        val intPart = parts[0]
        val fracPart = parts.getOrElse(1) { "00" }

        val groupedInt = if (intPart.length <= 3) {
            intPart
        } else {
            val lastThree = intPart.takeLast(3)
            val rest = intPart.dropLast(3)
            val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restGrouped,$lastThree"
        }

        val numberStr = when {
            forceDecimals -> "$groupedInt.$fracPart"
            fracPart == "00" -> groupedInt
            fracPart.endsWith('0') -> "$groupedInt.${fracPart.first()}"
            else -> "$groupedInt.$fracPart"
        }

        val prefix = when {
            amount < 0 -> "-₹"
            showSign && amount > 0 -> "+₹"
            else -> "₹"
        }
        return "$prefix$numberStr"
    }

    fun formatInrCompact(amount: Double): String {
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absVal >= 10_000_000.0 -> "${sign}₹${String.format(Locale.US, "%.2f", absVal / 10_000_000.0)}Cr"
            absVal >= 100_000.0 -> "${sign}₹${String.format(Locale.US, "%.2f", absVal / 100_000.0)}L"
            absVal >= 1_000.0 -> "${sign}₹${String.format(Locale.US, "%.1f", absVal / 1_000.0)}K"
            else -> formatInr(amount, forceDecimals = false)
        }
    }

    fun formatDateIst(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun formatTimeIst(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun formatReadableDateIst(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", indianLocale).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun formatReadableDateTimeIst(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a 'IST'", indianLocale).apply {
            timeZone = istTimeZone
        }
        return sdf.format(Date(millis))
    }

    fun formatShortDayIst(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM", indianLocale).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun formatMonthKeyIst(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun formatMonthLabelIst(millis: Long): String {
        val sdf = SimpleDateFormat("MMM yyyy", indianLocale).apply { timeZone = istTimeZone }
        return sdf.format(Date(millis))
    }

    fun startOfTodayIstMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun startOfWeekIstMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = startOfTodayIstMillis(nowMillis)
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return cal.timeInMillis
    }

    fun startOfMonthIstMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = startOfTodayIstMillis(nowMillis)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    fun startOfYearIstMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = startOfTodayIstMillis(nowMillis)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}

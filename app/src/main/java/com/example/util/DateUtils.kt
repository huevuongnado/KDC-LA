package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
    private val isoTimestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun nowIso(): String = isoTimestampFormat.format(Date())

    fun formatDateForDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val trimmed = raw.trim()
        // Try yyyy-MM-dd first
        if (trimmed.length >= 10) {
            val datePart = trimmed.substring(0, 10)
            runCatching {
                val parsed = isoDateFormat.parse(datePart)
                if (parsed != null) return displayDateFormat.format(parsed)
            }
        }
        return trimmed
    }

    fun calculateAge(dob: String?): Int? {
        if (dob.isNullOrBlank() || dob.length < 10) return null
        return runCatching {
            val parts = dob.substring(0, 10).split("-")
            if (parts.size != 3) return null
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val now = Calendar.getInstance()
            var age = now.get(Calendar.YEAR) - year
            val currentMonth = now.get(Calendar.MONTH) + 1
            val currentDay = now.get(Calendar.DAY_OF_MONTH)
            if (currentMonth < month || (currentMonth == month && currentDay < day)) {
                age--
            }
            age.takeIf { it >= 0 }
        }.getOrNull()
    }

    fun maskSensitive(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        val s = value.trim()
        return if (s.length < 5) "••••" else "${s.take(2)}••••${s.takeLast(2)}"
    }

    fun formatEpochMillisToIsoDate(millis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    const val ONLINE_TIMEOUT_MS = 3L * 60L * 1000L

    fun isOnlineActive(
        online: Boolean,
        lastActiveMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!online) return false
        if (lastActiveMillis == null) return true
        return (nowMillis - lastActiveMillis) <= ONLINE_TIMEOUT_MS
    }

    fun formatRelativeActivity(
        lastActiveMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        if (lastActiveMillis == null) return "Chưa ghi nhận"
        val diff = (nowMillis - lastActiveMillis).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "Vừa hoạt động"
            diff < 3_600_000L -> "Hoạt động ${diff / 60_000L} phút trước"
            diff < 86_400_000L -> "Hoạt động ${diff / 3_600_000L} giờ trước"
            else -> "Lần cuối: ${displayDateFormat.format(Date(lastActiveMillis))}"
        }
    }

    fun parseIsoDateToEpochMillis(dob: String?): Long? {
        if (dob.isNullOrBlank() || dob.length < 10) return null
        return runCatching {
            val parts = dob.substring(0, 10).split("-")
            if (parts.size != 3) return null
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            cal.timeInMillis
        }.getOrNull()
    }
}

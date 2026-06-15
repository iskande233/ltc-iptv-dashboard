package com.latchi.admin

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil

object AdminExpiryHelper {
    private const val DAY_MS = 86_400_000L

    fun daysLeft(raw: String): Int? {
        if (raw.isBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(raw.take(10)) ?: return null
            ceil((date.time - System.currentTimeMillis()).toDouble() / DAY_MS.toDouble()).toInt()
        } catch (_: Exception) {
            null
        }
    }

    fun isExpiringSoon(raw: String): Boolean {
        val days = daysLeft(raw) ?: return false
        return days in 0..7
    }

    fun isExpired(raw: String): Boolean {
        val days = daysLeft(raw) ?: return false
        return days < 0
    }

    fun color(raw: String): Int {
        val days = daysLeft(raw) ?: return Color.parseColor("#B8AFC8")
        return when {
            days < 0 -> Color.parseColor("#DC2626")
            days <= 7 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#16A34A")
        }
    }

    fun statusText(context: Context, raw: String): String {
        val days = daysLeft(raw) ?: return context.getString(R.string.link_expiry_not_set)
        return when {
            days < 0 -> context.getString(R.string.link_expired_status, raw.take(10))
            days == 0 -> context.getString(R.string.link_expires_today, raw.take(10))
            days <= 7 -> context.getString(R.string.link_expiring_soon_status, raw.take(10), days)
            else -> context.getString(R.string.link_safe_status, raw.take(10), days)
        }
    }

    fun normalizedOrBlank(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            val date = sdf.parse(clean.take(10)) ?: return clean
            sdf.format(date)
        } catch (_: Exception) {
            clean
        }
    }
}

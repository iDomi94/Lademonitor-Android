package com.dominiqueherbrigpersonalteam.lademonitor.ui.common

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Number/date formatting helpers, matching the iOS formatters. Follows the device's current
 * locale (rather than a fixed one) so both the calendar/number formatting and the short
 * relative-time strings automatically switch between German and English with the system language.
 */
object Fmt {
    private val locale: Locale get() = Locale.getDefault()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dateTimeMedium(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        )

    fun dateTimeFull(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale)
        )

    fun dateTimeShort(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)
        )

    fun km(value: Int): String = "%,d km".format(locale, value)

    /** Aussentemperatur, eine Nachkommastelle - "-3,5 °C" bzw. "-3.5 °C". */
    fun temperature(value: Double): String = "%.1f °C".format(locale, value)

    fun n(format: String, value: Double): String = String.format(locale, format, value)

    fun n(format: String, value: Int): String = String.format(locale, format, value)

    /** "5 min ago" style relative time for the last-sync line, localized to the device language. */
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - epochMillis).coerceAtLeast(0)
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        val context = LademonitorApp.appContext
        return when {
            minutes < 1 -> context.getString(R.string.relative_time_just_now)
            minutes < 60 -> context.getString(R.string.relative_time_minutes, minutes)
            hours < 24 -> context.getString(R.string.relative_time_hours, hours)
            else -> context.getString(R.string.relative_time_days, days)
        }
    }
}

package com.afam.galleria.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formattazione di date e durate in italiano, in stile iOS. */
object Dates {
    val locale: Locale = Locale.ITALIAN
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val weekdayDayMonthYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    private val shortDayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val monthName = DateTimeFormatter.ofPattern("MMMM", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun localDateTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    fun epochDay(ms: Long): Long = localDate(ms).toEpochDay()

    fun capitalize(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /** "Oggi", "Ieri", "lunedì 10 novembre" o con l'anno se diverso. */
    fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Oggi"
            today.minusDays(1) -> "Ieri"
            else -> capitalize(
                if (date.year == today.year) date.format(weekdayDayMonth)
                else date.format(weekdayDayMonthYear)
            )
        }
    }

    fun dayTitle(epochDay: Long): String = dayTitle(LocalDate.ofEpochDay(epochDay))

    /** Titolo del visualizzatore: "Oggi", "Ieri", "13 novembre" o "13 novembre 2024". */
    fun viewerTitle(ms: Long): String {
        val date = localDate(ms)
        val today = LocalDate.now(zone)
        return when {
            date == today -> "Oggi"
            date == today.minusDays(1) -> "Ieri"
            date.year == today.year -> date.format(dayMonth)
            else -> date.format(dayMonthYear)
        }
    }

    fun time(ms: Long): String = localDateTime(ms).format(time)

    fun fullDate(ms: Long): String = capitalize(localDate(ms).format(weekdayDayMonthYear))

    fun shortDate(ms: Long): String = localDate(ms).format(shortDayMonthYear)

    fun monthName(month: Int): String = capitalize(LocalDate.of(2000, month, 1).format(monthName))

    fun monthYear(year: Int, month: Int): String = capitalize(LocalDate.of(year, month, 1).format(monthYear))

    fun monthYear(ms: Long): String = capitalize(localDate(ms).format(monthYear))

    /** Intervallo compatto, es. "3–9 nov 2026" o "28 ott – 2 nov 2026". */
    fun range(fromMs: Long, toMs: Long): String {
        val a = localDate(minOf(fromMs, toMs))
        val b = localDate(maxOf(fromMs, toMs))
        val short = DateTimeFormatter.ofPattern("MMM", locale)
        return when {
            a == b -> b.format(shortDayMonthYear)
            a.year == b.year && a.month == b.month -> "${a.dayOfMonth}–${b.dayOfMonth} ${b.format(short)} ${b.year}"
            a.year == b.year -> "${a.dayOfMonth} ${a.format(short)} – ${b.dayOfMonth} ${b.format(short)} ${b.year}"
            else -> "${a.format(shortDayMonthYear)} – ${b.format(shortDayMonthYear)}"
        }
    }

    fun yearsAgo(ms: Long): Long = ChronoUnit.YEARS.between(localDate(ms), LocalDate.now(zone))

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s)
        else String.format(locale, "%d:%02d", m, s)
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(locale, "%.2f GB", gb)
            mb >= 1 -> String.format(locale, "%.1f MB", mb)
            else -> String.format(locale, "%.0f KB", kb)
        }
    }

    fun count(photos: Int, videos: Int): String {
        val parts = mutableListOf<String>()
        if (photos > 0) parts += if (photos == 1) "1 foto" else "${format(photos)} foto"
        if (videos > 0) parts += if (videos == 1) "1 video" else "${format(videos)} video"
        return if (parts.isEmpty()) "Nessun elemento" else parts.joinToString(", ")
    }

    fun elements(n: Int): String = if (n == 1) "1 elemento" else "${format(n)} elementi"

    fun format(n: Int): String = String.format(locale, "%,d", n).replace(',', '.')
}

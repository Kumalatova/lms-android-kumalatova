package com.kumalatova.lmsadmin

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeOnly = DateTimeFormatter.ofPattern("HH:mm")
private val dateTime = DateTimeFormatter.ofPattern("dd.MM HH:mm")

/** ISO-время с сервера в удобный вид: сегодня только часы, раньше дата. */
fun formatTime(iso: String): String {
    if (iso.isEmpty()) return ""
    return try {
        val t = Instant.parse(iso).atZone(ZoneId.systemDefault())
        if (t.toLocalDate() == LocalDate.now()) t.format(timeOnly) else t.format(dateTime)
    } catch (e: Exception) {
        ""
    }
}

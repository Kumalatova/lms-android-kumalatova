package com.kumalatova.lmsadmin

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Расписание опроса по московскому времени.
 * Будни с 9:00 до 19:00 — раз в 1,5 минуты. Остальное время — раз в 2 часа,
 * но не позже начала следующего рабочего окна (в 9:00 опрос уже частый).
 */
object Schedule {
    private val MSK: ZoneId = ZoneId.of("Europe/Moscow")
    private val START: LocalTime = LocalTime.of(9, 0)
    private val END: LocalTime = LocalTime.of(19, 0)

    private const val FAST_MS = 90_000L
    private const val SLOW_MS = 2 * 60 * 60 * 1000L
    private const val MIN_MS = 60_000L

    private fun isWeekday(d: DayOfWeek) = d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY

    private fun isWorkTime(now: ZonedDateTime): Boolean {
        val t = now.toLocalTime()
        return isWeekday(now.dayOfWeek) && !t.isBefore(START) && t.isBefore(END)
    }

    private fun nextWorkStart(now: ZonedDateTime): ZonedDateTime {
        var day = now.toLocalDate()
        // Сегодня ещё до 9:00 и день будний — окно откроется сегодня; иначе ищем с завтрашнего дня.
        if (!(isWeekday(day.dayOfWeek) && now.toLocalTime().isBefore(START))) {
            day = day.plusDays(1)
        }
        while (!isWeekday(day.dayOfWeek)) day = day.plusDays(1)
        return day.atTime(START).atZone(MSK)
    }

    /** Через сколько миллисекунд делать следующий опрос. */
    fun nextDelay(nowMillis: Long): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(MSK)
        if (isWorkTime(now)) return FAST_MS
        val untilStart = Duration.between(now, nextWorkStart(now)).toMillis()
        return maxOf(MIN_MS, minOf(SLOW_MS, untilStart))
    }
}

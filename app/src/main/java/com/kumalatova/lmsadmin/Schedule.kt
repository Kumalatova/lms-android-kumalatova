package com.kumalatova.lmsadmin

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Расписание опроса по московскому времени и настройкам из приложения (PollSettings).
 * В рабочее время — через workMinutes; в остальное — через offMinutes, но не позже
 * начала ближайшего рабочего окна (в его начале опрос уже частый).
 */
object Schedule {
    private val MSK: ZoneId = ZoneId.of("Europe/Moscow")
    private const val MIN_MS = 60_000L

    private fun minuteOfDay(t: ZonedDateTime) = t.hour * 60 + t.minute

    private fun isWorkTime(now: ZonedDateTime, s: PollSettings): Boolean {
        val m = minuteOfDay(now)
        return now.dayOfWeek.value in s.days && m >= s.startHour * 60 && m < s.endHour * 60
    }

    /** Начало ближайшего рабочего окна или null, если рабочих дней нет. */
    private fun nextWorkStart(now: ZonedDateTime, s: PollSettings): ZonedDateTime? {
        if (s.days.isEmpty()) return null
        var day = now.toLocalDate()
        // Сегодня рабочий день и окно ещё не началось — оно откроется сегодня.
        if (!(day.dayOfWeek.value in s.days && minuteOfDay(now) < s.startHour * 60)) {
            day = day.plusDays(1)
        }
        repeat(7) {
            if (day.dayOfWeek.value in s.days) return day.atTime(s.startHour, 0).atZone(MSK)
            day = day.plusDays(1)
        }
        return null
    }

    /** Через сколько миллисекунд делать следующий опрос. */
    fun nextDelay(nowMillis: Long, s: PollSettings): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(MSK)
        if (isWorkTime(now, s)) return maxOf(MIN_MS, s.workMinutes * 60_000L)
        val off = s.offMinutes * 60_000L
        val start = nextWorkStart(now, s) ?: return maxOf(MIN_MS, off)
        val untilStart = Duration.between(now, start).toMillis()
        return maxOf(MIN_MS, minOf(off, untilStart))
    }
}

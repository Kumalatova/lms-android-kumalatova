package com.kumalatova.lmsadmin

import android.content.Context

/**
 * Настройки опроса (время московское).
 * days — рабочие дни по номерам: 1 = Пн … 7 = Вс.
 * В рабочее время (дни и часы с startHour до endHour) опрос идёт раз в workMinutes минут,
 * в остальное время — раз в offMinutes минут.
 */
data class PollSettings(
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startHour: Int = 9,
    val endHour: Int = 19,
    val workMinutes: Int = 2,
    val offMinutes: Int = 120,
)

class PollSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("lms_poll_settings", Context.MODE_PRIVATE)

    fun load(): PollSettings {
        val d = PollSettings()
        val days = prefs.getString("days", null)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }?.filter { it in 1..7 }?.toSet()
            ?: d.days
        val start = prefs.getInt("start", d.startHour).coerceIn(0, 22)
        val end = prefs.getInt("end", d.endHour).coerceIn(start + 1, 24)
        return PollSettings(
            days = days,
            startHour = start,
            endHour = end,
            workMinutes = prefs.getInt("work", d.workMinutes).coerceIn(1, 60),
            offMinutes = prefs.getInt("off", d.offMinutes).coerceIn(15, 720),
        )
    }

    fun save(s: PollSettings) {
        prefs.edit()
            .putString("days", s.days.sorted().joinToString(","))
            .putInt("start", s.startHour)
            .putInt("end", s.endHour)
            .putInt("work", s.workMinutes)
            .putInt("off", s.offMinutes)
            .apply()
    }
}

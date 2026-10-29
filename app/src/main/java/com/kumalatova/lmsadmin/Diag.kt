package com.kumalatova.lmsadmin

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Когда в последний раз сработал фоновый опрос: показывается на главном экране. */
object Diag {
    private const val PREFS = "lms_diag"
    private const val KEY = "last_alarm"

    fun markAlarm(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, System.currentTimeMillis()).apply()
    }

    fun lastAlarmText(ctx: Context): String {
        val t = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)
        if (t == 0L) return "ещё не было"
        return SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(t))
    }
}

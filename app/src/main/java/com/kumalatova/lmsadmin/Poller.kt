package com.kumalatova.lmsadmin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Фоновый опрос без постоянной службы: будильник системы будит приложение по
 * расписанию (см. Schedule), приложение коротко опрашивает сайты и снова засыпает.
 */
object Poller {
    private const val ACTION = "com.kumalatova.lmsadmin.POLL"

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        0,
        Intent(ctx, PollReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ставит следующий опрос по расписанию. Если сайтов нет — будильник снимается. */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pending(app)
        if (SiteStore(app).load().isEmpty()) {
            alarms.cancel(operation)
            return
        }
        val triggerAt = SystemClock.elapsedRealtime() + Schedule.nextDelay(System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
        }
    }

    /** Один проход по всем сайтам: новые обращения превращаются в уведомления. */
    suspend fun pollOnce(ctx: Context) {
        val store = SiteStore(ctx)
        val seen = SeenStore(ctx)
        val sites = store.load()
        if (sites.isEmpty()) return
        coroutineScope {
            sites.map { site -> async { cycle(ctx, site, seen) } }.awaitAll()
        }
    }

    /** Один опрос сайта. Возвращает число непрочитанных или -1, если сайт не ответил. */
    private suspend fun cycle(ctx: Context, site: Site, seen: SeenStore): Int {
        val summary = try {
            Api.summary(site)
        } catch (e: Exception) {
            return -1
        }
        Notifier.ensureSiteChannel(ctx, site)

        val current = LinkedHashMap<String, ListItem>()
        for (group in Group.entries) {
            if (summary.counts.of(group) <= 0) continue
            try {
                Api.list(site, group)
                    .filter { it.unread > 0 }
                    .forEach { current[keyOf(it)] = it }
            } catch (e: Exception) {
                // Список не получили: ничего не меняем, попробуем в следующий раз.
                return summary.counts.total
            }
        }

        val old = seen.get(site.id)
        if (old != null) {
            current.filterKeys { it !in old }.values.forEach { Notifier.postItem(ctx, site, it) }
            // Прочитанное и отвеченное убираем из шторки.
            (old - current.keys).forEach { key ->
                val parts = key.split(":", limit = 3)
                val id = parts.getOrNull(1)?.toLongOrNull()
                if (parts.isNotEmpty() && id != null) {
                    Notifier.cancelItem(ctx, site.id, Group.from(parts[0]), id)
                }
            }
        }
        // Первый опрос сайта: накопившееся запоминаем без звука.
        seen.put(site.id, current.keys)
        return summary.counts.total
    }

    private fun keyOf(item: ListItem) = "${item.group.code}:${item.id}:${item.time}"
}

/** Срабатывает по будильнику: ставит следующий опрос и коротко опрашивает сайты. */
class PollReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Diag.markAlarm(app)
        // Следующий опрос ставим сразу: цепочка не оборвётся, даже если этот не удастся.
        Poller.schedule(app)
        // Если систему закрыла службу-якорь, поднимаем её снова.
        KeepAliveService.start(app)

        val result = goAsync()
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Телефон бодрствует только на время опроса, не дольше 15 секунд.
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lmsadmin:poll").apply {
            setReferenceCounted(false)
            acquire(15_000)
        }
        Thread {
            try {
                runBlocking { withTimeoutOrNull(9_000) { Poller.pollOnce(app) } }
            } catch (e: Exception) {
                // Сбой этого опроса не страшен: следующий уже поставлен.
            } finally {
                if (lock.isHeld) lock.release()
                result.finish()
            }
        }.start()
    }
}

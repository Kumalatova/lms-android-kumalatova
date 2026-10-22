package com.kumalatova.lmsadmin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Служба на переднем плане: раз в 30 секунд спрашивает все сайты и показывает
 * уведомление о каждом новом обращении. Работает, пока в приложении есть сайты.
 */
class PollService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureServiceChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote("Слежу за обращениями")
        if (job == null) {
            acquireWakeLock()
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun promote(text: String) {
        val notification = Notifier.foregroundNotification(this, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.FGS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.FGS_ID, notification)
        }
    }

    // Без этого телефон «засыпает» и опрос останавливается, пока экран выключен.
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lmsadmin:poll").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private suspend fun loop() {
        val store = SiteStore(this)
        val seen = SeenStore(this)
        while (scope.isActive) {
            val sites = store.load()
            if (sites.isEmpty()) {
                stopSelf()
                return
            }
            val totals = coroutineScope {
                sites.map { site -> async { cycle(site, seen) } }.awaitAll()
            }
            val unread = totals.filter { it >= 0 }.sum()
            Notifier.updateForeground(
                this,
                if (unread > 0) "Ждут ответа: $unread" else "Слежу за обращениями",
            )
            delay(30_000)
        }
    }

    /** Один опрос сайта. Возвращает число непрочитанных или -1, если сайт не ответил. */
    private suspend fun cycle(site: Site, seen: SeenStore): Int {
        val summary = try {
            Api.summary(site)
        } catch (e: Exception) {
            return -1
        }
        Notifier.ensureSiteChannel(this, site)

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
            current.filterKeys { it !in old }.values.forEach { Notifier.postItem(this, site, it) }
            // Прочитанное и отвеченное убираем из шторки.
            (old - current.keys).forEach { key ->
                val parts = key.split(":", limit = 3)
                val id = parts.getOrNull(1)?.toLongOrNull()
                if (parts.isNotEmpty() && id != null) {
                    Notifier.cancelItem(this, site.id, Group.from(parts[0]), id)
                }
            }
        }
        // Первый опрос сайта: накопившееся запоминаем без звука.
        seen.put(site.id, current.keys)
        return summary.counts.total
    }

    private fun keyOf(item: ListItem) = "${item.group.code}:${item.id}:${item.time}"

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, PollService::class.java))
            } catch (e: Exception) {
                // Система не разрешила запуск из фона: служба стартует при следующем открытии.
            }
        }
    }
}

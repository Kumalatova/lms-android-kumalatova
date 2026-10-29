package com.kumalatova.lmsadmin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Короткая служба для фонового опроса: будильник запускает её, она опрашивает сайты
 * без ограничения в 9 секунд и сразу останавливается. Пока опрос быстрый, значка в
 * шторке нет: система показывает уведомление службы, только если она работает
 * дольше нескольких секунд, и убирает его сама.
 */
class CheckService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureServiceChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this,
                Notifier.FGS_ID,
                Notifier.foregroundNotification(this, "Проверяю обращения…"),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0,
            )
        } catch (e: Exception) {
            // Система не дала стать службой: опрос всё равно выполним, пока процесс жив.
        }
        if (running) return START_NOT_STICKY
        running = true

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lmsadmin:check").apply {
            setReferenceCounted(false)
            acquire(180_000)
        }

        scope.launch {
            try {
                // Короткая служба живёт не дольше ~3 минут: останавливаемся раньше.
                withTimeoutOrNull(150_000) { Poller.pollOnce(applicationContext) }
            } catch (e: Exception) {
                // Сбой этого опроса не страшен: следующий уже поставлен.
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}

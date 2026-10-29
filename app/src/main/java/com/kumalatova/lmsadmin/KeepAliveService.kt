package com.kumalatova.lmsadmin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Лёгкая служба-«якорь»: ничего не опрашивает и не держит телефон бодрствующим,
 * только не даёт системе (особенно Xiaomi и Huawei) закрыть приложение в фоне.
 * Опрос делает будильник (Poller) по расписанию. Уведомление службы почти невидимо.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureServiceChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.foregroundNotification(this, "Слежу за обращениями")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.FGS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.FGS_ID, notification)
        }
        if (SiteStore(this).load().isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            if (SiteStore(ctx).load().isEmpty()) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) {
                // Система не разрешила запуск из фона: служба стартует при следующем открытии.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }
    }
}

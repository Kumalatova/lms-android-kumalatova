package com.kumalatova.lmsadmin

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant

/** Каналы и уведомления. У каждого сайта свой канал: звук настраивается в системе. */
object Notifier {
    const val EXTRA_SITE = "site_id"
    const val EXTRA_GROUP = "group"
    const val EXTRA_ID = "item_id"

    private const val ACCENT = 0xFFC98A4B.toInt()

    fun siteChannelId(siteId: String) = "site_$siteId"

    /** Постоянного уведомления больше нет: убираем каналы прежних версий. */
    fun removeOldServiceChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("service")
        manager.deleteNotificationChannel("service_quiet")
    }

    fun ensureSiteChannel(ctx: Context, site: Site) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            siteChannelId(site.id),
            site.name,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Новые обращения с сайта ${site.name}"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        // Повторное создание не меняет звук, который пользователь уже выбрал в системе.
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun deleteSiteChannel(ctx: Context, siteId: String) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.getSystemService(NotificationManager::class.java).deleteNotificationChannel(siteChannelId(siteId))
    }

    /** Системные настройки канала сайта: там выбирается звук. */
    fun openChannelSettings(ctx: Context, siteId: String) {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, siteChannelId(siteId))
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra("android.provider.extra.APP_PACKAGE", ctx.packageName)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // Нет такого экрана настроек: ничего не делаем.
        }
    }

    fun openAppNotificationSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // Нет такого экрана настроек: ничего не делаем.
        }
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun itemNotificationId(siteId: String, group: Group, id: Long): Int =
        ("$siteId:${group.code}:$id").hashCode()

    fun cancelItem(ctx: Context, siteId: String, group: Group, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(itemNotificationId(siteId, group, id))
    }

    /** Уведомление о новом обращении: сайт, кто, тема. Видно на экране блокировки. */
    fun postItem(ctx: Context, site: Site, item: ListItem) {
        if (!canNotify(ctx)) return
        ensureSiteChannel(ctx, site)

        val notificationId = itemNotificationId(site.id, item.group, item.id)
        val open = PendingIntent.getActivity(
            ctx,
            notificationId,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SITE, site.id)
                .putExtra(EXTRA_GROUP, item.group.code)
                .putExtra(EXTRA_ID, item.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = "${item.person} · ${item.topic}"
        val whenMillis = try {
            Instant.parse(item.time).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val notification = NotificationCompat.Builder(ctx, siteChannelId(site.id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(site.name)
            .setContentText(text)
            .setSubText(item.group.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setGroup("site_${site.id}")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Разрешения нет: уведомление не показываем.
        }
    }
}

/** Что уже показывали в уведомлениях. Пусто для сайта = первый опрос, показываем без звука. */
class SeenStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lms_seen", Context.MODE_PRIVATE)

    fun get(siteId: String): Set<String>? {
        val set = prefs.getStringSet("seen_$siteId", null) ?: return null
        return HashSet(set)
    }

    fun put(siteId: String, keys: Set<String>) {
        prefs.edit().putStringSet("seen_$siteId", HashSet(keys)).apply()
    }

    fun remove(siteId: String) {
        prefs.edit().remove("seen_$siteId").apply()
    }
}

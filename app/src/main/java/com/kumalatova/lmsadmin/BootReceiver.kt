package com.kumalatova.lmsadmin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки телефона и после обновления приложения снова запускает слежение. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (SiteStore(context).load().isNotEmpty()) {
                PollService.start(context)
            }
        }
    }
}

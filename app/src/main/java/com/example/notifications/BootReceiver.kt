package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootReceiver received intent action: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == action ||
            "android.intent.action.QUICKBOOT_POWERON" == action
        ) {
            try {
                NotificationEngine.createNotificationChannel(context)
                NotificationEngine.scheduleBackgroundWorkers(context)
                Log.i(TAG, "Notification channel and background WorkManager workers rescheduled after reboot/update.")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling workers in BootReceiver: ${e.localizedMessage}", e)
            }
        }
    }
}

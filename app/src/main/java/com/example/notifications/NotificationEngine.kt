package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import java.util.concurrent.TimeUnit

object NotificationEngine {
    private const val TAG = "NotificationEngine"
    const val CHANNEL_ID = "lic_reminders_channel_v2"
    private const val CHANNEL_NAME = "LIC Policy & Due Reminders"
    private const val CHANNEL_DESC = "Automated high-priority notifications for upcoming LIC premium dues, birthdays, and maturities."
    private const val PREFS_NAME = "lic_notification_prefs"
    private const val WORK_NAME_PERIODIC = "lic_reminder_periodic_work"
    private const val WORK_NAME_ONEOFF = "lic_reminder_oneoff_work"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("notifications_enabled", true)
    }

    fun isTodayReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("reminder_today_enabled", true)
    }

    fun isTomorrowReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("reminder_tomorrow_enabled", true)
    }

    fun isWeeklyReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("reminder_weekly_enabled", true)
    }

    fun isOverdueReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("reminder_overdue_enabled", true)
    }

    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("sound_enabled", true)
    }

    fun isVibrationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("vibration_enabled", true)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(isVibrationEnabled(context))
                if (isSoundEnabled(context)) {
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.i(TAG, "Notification channel initialized successfully: $CHANNEL_ID")
        }
    }

    fun scheduleBackgroundWorkers(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            // 1. One-time immediate sync & notification check
            val oneTimeRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_ONEOFF,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            // 2. Periodic background check every 6 hours
            val periodicRequest = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            Log.i(TAG, "WorkManager periodic & immediate workers successfully scheduled.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule WorkManager workers: ${e.localizedMessage}", e)
        }
    }

    fun sendPolicyNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        policyId: Long,
        policyNumber: String,
        customerName: String,
        customerMobile: String,
        dueAmount: Double
    ) {
        if (!isNotificationsEnabled(context)) {
            Log.i(TAG, "Notification skipped: Notifications disabled in settings.")
            return
        }

        createNotificationChannel(context)

        // Main Tap Intent -> Opens App at Reminders Screen
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "reminders")
            putExtra("policy_id", policyId)
            putExtra("customer_name", customerName)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Call Customer
        val callIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CALL
            putExtra("mobile", customerMobile)
            putExtra("notification_id", notificationId)
        }
        val callPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Mark Paid
        val markPaidIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_PAID
            putExtra("policy_id", policyId)
            putExtra("policy_number", policyNumber)
            putExtra("customer_name", customerName)
            putExtra("amount", dueAmount)
            putExtra("notification_id", notificationId)
        }
        val markPaidPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            markPaidIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Dismiss
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 4,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nPolicy: $policyNumber | Amount: ₹${String.format("%,.0f", dueAmount)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_call, "Call", callPendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Mark Paid", markPaidPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        if (!isVibrationEnabled(context)) {
            builder.setVibrate(longArrayOf(0L))
        }

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, builder.build())
            Log.i(TAG, "Notification delivered successfully: ID=$notificationId, Title='$title'")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for showing notification: ${e.localizedMessage}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error posting notification: ${e.localizedMessage}", e)
        }
    }
}

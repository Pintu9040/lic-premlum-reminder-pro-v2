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

    fun getSelectedReminderTime(context: Context): String {
        return getPrefs(context).getString("reminder_time", "09:00 AM") ?: "09:00 AM"
    }

    fun parseReminderTime(timeStr: String): Pair<Int, Int> {
        val clean = timeStr.trim().uppercase()
        val isPm = clean.contains("PM")
        val parts = clean.replace("AM", "").replace("PM", "").trim().split(":")
        var hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (isPm && hour < 12) hour += 12
        if (!isPm && hour == 12) hour = 0
        return Pair(hour, minute)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val isVib = isVibrationEnabled(context)
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(isVib)
                if (isVib) {
                    vibrationPattern = longArrayOf(0L, 250L, 250L, 250L)
                }
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
            Log.i(TAG, "Notification channel initialized successfully: $CHANNEL_ID (vibration=$isVib)")
        }
    }

    fun scheduleBackgroundWorkers(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)

            // Master switch check: if OFF, cancel all scheduled reminder workers
            if (!isNotificationsEnabled(context)) {
                workManager.cancelUniqueWork(WORK_NAME_ONEOFF)
                workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
                Log.i(TAG, "Automated Reminders OFF: All background workers cancelled.")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            // Calculate initial delay for selected daily schedule time
            val selectedTime = getSelectedReminderTime(context)
            val (targetHour, targetMinute) = parseReminderTime(selectedTime)

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)

            // 1. One-time immediate sync & notification check
            val oneTimeRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_ONEOFF,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            // 2. Daily periodic background check at scheduled time every 24 hours
            val periodicRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                periodicRequest
            )

            Log.i(TAG, "WorkManager scheduled for daily time '$selectedTime' with initial delay ${initialDelayMs / 1000}s.")
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

        val isVib = isVibrationEnabled(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nPolicy: $policyNumber | Amount: ₹${String.format("%,.0f", dueAmount)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, "Call", callPendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Mark Paid", markPaidPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        if (isVib) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            builder.setVibrate(longArrayOf(0L, 250L, 250L, 250L))
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
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

    fun runNotificationEngineDiagnostic(context: Context): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        val prefs = getPrefs(context)

        // 1. Master Switch Test
        val origMaster = isNotificationsEnabled(context)
        prefs.edit().putBoolean("notifications_enabled", false).apply()
        val masterOffCheck = !isNotificationsEnabled(context)
        prefs.edit().putBoolean("notifications_enabled", true).apply()
        val masterOnCheck = isNotificationsEnabled(context)
        prefs.edit().putBoolean("notifications_enabled", origMaster).apply()
        results["Automated Dues Reminders (Master Switch)"] = masterOffCheck && masterOnCheck

        // 2. Due Today Alerts Test
        val origToday = isTodayReminderEnabled(context)
        prefs.edit().putBoolean("reminder_today_enabled", false).apply()
        val todayOffCheck = !isTodayReminderEnabled(context)
        prefs.edit().putBoolean("reminder_today_enabled", true).apply()
        val todayOnCheck = isTodayReminderEnabled(context)
        prefs.edit().putBoolean("reminder_today_enabled", origToday).apply()
        results["Due Today Alerts"] = todayOffCheck && todayOnCheck

        // 3. Tomorrow Dues Warning Test
        val origTomorrow = isTomorrowReminderEnabled(context)
        prefs.edit().putBoolean("reminder_tomorrow_enabled", false).apply()
        val tomorrowOffCheck = !isTomorrowReminderEnabled(context)
        prefs.edit().putBoolean("reminder_tomorrow_enabled", true).apply()
        val tomorrowOnCheck = isTomorrowReminderEnabled(context)
        prefs.edit().putBoolean("reminder_tomorrow_enabled", origTomorrow).apply()
        results["Tomorrow Dues Warning"] = tomorrowOffCheck && tomorrowOnCheck

        // 4. Overdue/Lapsed Follow-up Test
        val origOverdue = isOverdueReminderEnabled(context)
        prefs.edit().putBoolean("reminder_overdue_enabled", false).apply()
        val overdueOffCheck = !isOverdueReminderEnabled(context)
        prefs.edit().putBoolean("reminder_overdue_enabled", true).apply()
        val overdueOnCheck = isOverdueReminderEnabled(context)
        prefs.edit().putBoolean("reminder_overdue_enabled", origOverdue).apply()
        results["Overdue/Lapsed Follow-up"] = overdueOffCheck && overdueOnCheck

        // 5. WhatsApp Automation Integration Test
        val origWa = com.example.whatsapp.WhatsAppAutomation.isWhatsAppRemindersEnabled(context)
        com.example.whatsapp.WhatsAppAutomation.setWhatsAppRemindersEnabled(context, false)
        val waOffCheck = !com.example.whatsapp.WhatsAppAutomation.isWhatsAppRemindersEnabled(context)
        com.example.whatsapp.WhatsAppAutomation.setWhatsAppRemindersEnabled(context, true)
        val waOnCheck = com.example.whatsapp.WhatsAppAutomation.isWhatsAppRemindersEnabled(context)
        com.example.whatsapp.WhatsAppAutomation.setWhatsAppRemindersEnabled(context, origWa)
        results["WhatsApp Automation Integration"] = waOffCheck && waOnCheck

        // 6. Daily Schedule Time Test
        val origTime = getSelectedReminderTime(context)
        prefs.edit().putString("reminder_time", "06:00 PM").apply()
        val parsedTime = parseReminderTime("06:00 PM")
        val timeCheck = getSelectedReminderTime(context) == "06:00 PM" && parsedTime == Pair(18, 0)
        prefs.edit().putString("reminder_time", origTime).apply()
        results["Daily Schedule Time"] = timeCheck

        // 7. Vibration Alert Test
        val origVib = isVibrationEnabled(context)
        prefs.edit().putBoolean("vibration_enabled", false).apply()
        val vibOffCheck = !isVibrationEnabled(context)
        prefs.edit().putBoolean("vibration_enabled", true).apply()
        val vibOnCheck = isVibrationEnabled(context)
        prefs.edit().putBoolean("vibration_enabled", origVib).apply()
        results["Vibration Alert"] = vibOffCheck && vibOnCheck

        Log.i(TAG, "NotificationEngine Diagnostic Results: $results")
        return results
    }
}

package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReminderWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting ReminderWorker background execution...")

        if (!NotificationEngine.isNotificationsEnabled(appContext)) {
            Log.i(TAG, "Notifications are globally disabled in settings. Worker complete.")
            return@withContext Result.success()
        }

        try {
            val db = AppDatabase.getDatabase(appContext)
            val syncManager = FirebaseSyncManager(appContext)

            val policies = db.policyDao().getAllPoliciesSync()
            val customers = db.customerDao().getAllCustomersSync().associateBy { it.id }

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val todayStr = sdf.format(Date())
            val todayDate = try { sdf.parse(todayStr) } catch (_: Exception) { Date() }

            val prefs = NotificationEngine.getPrefs(appContext)
            var notificationsSentCount = 0

            for (policy in policies) {
                // Rule 1: Skip Paid, Cancelled, Lapsed, or Inactive policies
                val statusUpper = policy.status.uppercase(Locale.getDefault())
                if (statusUpper == "PAID" || statusUpper == "CANCELLED" || statusUpper == "LAPSED" || statusUpper == "INACTIVE") {
                    Log.d(TAG, "Skipping Policy ${policy.policyNumber}: Status is '$statusUpper'")
                    continue
                }

                // Rule 2: Fetch Customer info
                val customer = customers[policy.customerId]

                val dueDate = try {
                    sdf.parse(policy.dueDate)
                } catch (e: Exception) {
                    null
                } ?: continue

                // Calculate difference in days
                val diffInMillis = dueDate.time - (todayDate?.time ?: 0L)
                val diffInDays = Math.round(diffInMillis.toDouble() / (1000 * 60 * 60 * 24)).toInt()

                var notificationType: String? = null
                var title: String? = null
                var message: String? = null

                when {
                    diffInDays == 0 -> {
                        if (NotificationEngine.isTodayReminderEnabled(appContext)) {
                            notificationType = "DUE_TODAY"
                            title = "🚨 LIC Premium Due TODAY!"
                            message = "Premium for Policy #${policy.policyNumber} (${policy.customerName}) is DUE TODAY!"
                        }
                    }
                    diffInDays == 1 -> {
                        if (NotificationEngine.isTomorrowReminderEnabled(appContext)) {
                            notificationType = "DUE_TOMORROW"
                            title = "⏰ LIC Premium Due Tomorrow"
                            message = "Premium for Policy #${policy.policyNumber} (${policy.customerName}) is due tomorrow."
                        }
                    }
                    diffInDays == 7 -> {
                        if (NotificationEngine.isWeeklyReminderEnabled(appContext)) {
                            notificationType = "DUE_IN_7_DAYS"
                            title = "📅 Upcoming LIC Premium Due (7 Days)"
                            message = "Policy #${policy.policyNumber} (${policy.customerName}) due in 7 days."
                        }
                    }
                    diffInDays < 0 -> {
                        if (NotificationEngine.isOverdueReminderEnabled(appContext)) {
                            notificationType = "OVERDUE"
                            title = "⚠️ OVERDUE LIC Premium Alert"
                            message = "Policy #${policy.policyNumber} (${policy.customerName}) is OVERDUE by ${Math.abs(diffInDays)} days!"
                        }
                    }
                }

                if (notificationType != null && title != null && message != null) {
                    val uniqueSentKey = "sent_${policy.id}_${notificationType}_$todayStr"

                    // Rule 3: Deduplication check
                    if (prefs.getBoolean(uniqueSentKey, false)) {
                        Log.d(TAG, "Duplicate notification skipped for policy ${policy.policyNumber} type $notificationType on $todayStr")
                        continue
                    }

                    val notificationId = (policy.id.hashCode() + notificationType.hashCode()).let { if (it < 0) -it else it }

                    NotificationEngine.sendPolicyNotification(
                        context = appContext,
                        notificationId = notificationId,
                        title = title,
                        message = message,
                        policyId = policy.id,
                        policyNumber = policy.policyNumber,
                        customerName = policy.customerName,
                        customerMobile = customer?.mobile ?: "",
                        dueAmount = policy.premiumAmount
                    )

                    // Mark sent in SharedPreferences
                    prefs.edit().putBoolean(uniqueSentKey, true).apply()
                    notificationsSentCount++
                    Log.i(TAG, "Dispatched $notificationType notification for Policy #${policy.policyNumber}")
                }
            }

            // Sync updated reminders snapshot to Firestore
            try {
                syncManager.backupReminders("", db)
            } catch (e: Exception) {
                Log.w(TAG, "Firestore backupReminders sync warning: ${e.localizedMessage}")
            }

            Log.i(TAG, "ReminderWorker completed successfully. $notificationsSentCount notifications dispatched.")
            Result.success()
        } catch (e: CancellationException) {
            Log.i(TAG, "ReminderWorker job cancelled.")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ReminderWorker failed with error: ${e.localizedMessage}", e)
            Result.retry()
        }
    }
}

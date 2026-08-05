package com.example.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.local.PaymentEntity
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_CALL = "com.example.notifications.ACTION_CALL"
        const val ACTION_MARK_PAID = "com.example.notifications.ACTION_MARK_PAID"
        const val ACTION_DISMISS = "com.example.notifications.ACTION_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra("notification_id", -1)

        Log.i(TAG, "NotificationActionReceiver received action: $action, notificationId: $notificationId")

        when (action) {
            ACTION_CALL -> {
                val mobile = intent.getStringExtra("mobile") ?: ""
                if (mobile.isNotEmpty()) {
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$mobile")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(dialIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch phone dialer: ${e.localizedMessage}")
                        Toast.makeText(context, "Cannot open dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
                cancelNotification(context, notificationId)
            }
            ACTION_MARK_PAID -> {
                val policyId = intent.getLongExtra("policy_id", -1L)
                val policyNumber = intent.getStringExtra("policy_number") ?: ""
                val customerName = intent.getStringExtra("customer_name") ?: ""
                val amount = intent.getDoubleExtra("amount", 0.0)

                if (policyId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val syncManager = FirebaseSyncManager(context)
                            val policy = db.policyDao().getPolicyById(policyId)

                            if (policy != null) {
                                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                val todayStr = sdf.format(Date())

                                // Calculate next due date (default +1 year for Yearly mode or +1 month)
                                val cal = Calendar.getInstance()
                                try {
                                    val parsedDate = sdf.parse(policy.dueDate)
                                    if (parsedDate != null) cal.time = parsedDate
                                } catch (_: Exception) {}

                                when (policy.premiumMode.uppercase(Locale.getDefault())) {
                                    "HALF-YEARLY", "HALF YEARLY", "H-Y" -> cal.add(Calendar.MONTH, 6)
                                    "QUARTERLY", "QTR" -> cal.add(Calendar.MONTH, 3)
                                    "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                                    else -> cal.add(Calendar.YEAR, 1)
                                }
                                val nextDueDateStr = sdf.format(cal.time)

                                // Create payment record
                                val payment = PaymentEntity(
                                    policyId = policy.id,
                                    policyNumber = policy.policyNumber,
                                    customerId = policy.customerId,
                                    customerName = policy.customerName,
                                    paidAmount = amount,
                                    lateFee = 0.0,
                                    paymentDate = todayStr,
                                    paymentMode = "CASH",
                                    receiptNumber = "RCP_${System.currentTimeMillis().toString().takeLast(6)}",
                                    notes = "Auto-collected via Notification Action"
                                )
                                val newPaymentId = db.paymentDao().insertPayment(payment)
                                val insertedPayment = payment.copy(id = newPaymentId)

                                // Update policy
                                val updatedPolicy = policy.copy(
                                    status = "Paid",
                                    dueDate = nextDueDateStr
                                )
                                db.policyDao().updatePolicy(updatedPolicy)

                                // Backup to Firestore
                                syncManager.backupPayment("", insertedPayment)
                                syncManager.backupPolicy("", updatedPolicy)

                                Log.i(TAG, "Policy $policyNumber successfully marked as Paid via notification action.")

                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(
                                        context,
                                        "Policy $policyNumber marked as PAID! Next due: $nextDueDateStr",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error marking policy paid in NotificationActionReceiver: ${e.localizedMessage}", e)
                        } finally {
                            cancelNotification(context, notificationId)
                            pendingResult.finish()
                        }
                    }
                } else {
                    cancelNotification(context, notificationId)
                }
            }
            ACTION_DISMISS -> {
                cancelNotification(context, notificationId)
            }
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}

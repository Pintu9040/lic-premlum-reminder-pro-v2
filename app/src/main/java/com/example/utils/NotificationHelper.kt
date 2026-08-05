package com.example.utils

import android.content.Context
import com.example.notifications.NotificationEngine

object NotificationHelper {
    fun createNotificationChannel(context: Context) {
        NotificationEngine.createNotificationChannel(context)
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        NotificationEngine.sendPolicyNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            policyId = -1L,
            policyNumber = "N/A",
            customerName = "Valued Customer",
            customerMobile = "",
            dueAmount = 0.0
        )
    }
}

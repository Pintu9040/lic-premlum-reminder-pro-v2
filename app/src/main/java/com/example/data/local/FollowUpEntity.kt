package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follow_ups")
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long = 0,
    val customerName: String = "",
    val customerMobile: String = "",
    val date: String = "", // YYYY-MM-DD
    val time: String = "", // e.g. 10:30 AM
    val notes: String = "",
    val status: String = "Pending", // Pending, Completed, Cancelled
    val createdAt: Long = System.currentTimeMillis()
)

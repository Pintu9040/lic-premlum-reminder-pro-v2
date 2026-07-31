package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val policyNumber: String,
    val customerId: Long,
    val customerName: String,
    val planName: String, // e.g. "Jeevan Labh (936)"
    val premiumAmount: Double,
    val sumAssured: Double,
    val premiumMode: String, // "Monthly", "Quarterly", "Half-Yearly", "Yearly"
    val dueDate: String, // YYYY-MM-DD
    val maturityDate: String, // YYYY-MM-DD
    val status: String = "Active", // "Active", "Due", "Lapsed", "Matured", "Paid-up"
    val nominee: String = "",
    val policyTerm: Int = 20,
    val premiumPayingTerm: Int = 16,
    val issueDate: String = "",
    val gracePeriodDays: Int = 30,
    val createdAt: Long = System.currentTimeMillis()
)


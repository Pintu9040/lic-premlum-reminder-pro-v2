package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val policyId: Long,
    val policyNumber: String,
    val customerId: Long,
    val customerName: String,
    val paidAmount: Double,
    val lateFee: Double = 0.0,
    val paymentDate: String, // YYYY-MM-DD
    val paymentMode: String, // "UPI", "Cash", "Cheque", "Net Banking"
    val receiptNumber: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


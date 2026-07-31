package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long? = null,
    val customerName: String = "",
    val policyId: Long? = null,
    val docType: String, // "Customer Photo", "Aadhaar Card", "PAN Card", "Policy Bond", "Address Proof", "Nominee Documents", "Other Documents"
    val title: String,
    val fileUri: String,
    val fileSize: String = "1.2 MB",
    val uploadDate: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

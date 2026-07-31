package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val mobile: String,
    val whatsapp: String = "",
    val email: String = "",
    val address: String = "",
    val dob: String = "", // YYYY-MM-DD
    val anniversary: String = "",
    val aadhaar: String = "",
    val pan: String = "",
    val occupation: String = "",
    val notes: String = "",
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)


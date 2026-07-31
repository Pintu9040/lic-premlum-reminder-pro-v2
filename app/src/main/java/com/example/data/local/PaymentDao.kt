package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    suspend fun getAllPaymentsSync(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE policyId = :policyId ORDER BY createdAt DESC")
    fun getPaymentsByPolicyId(policyId: Long): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("SELECT SUM(paidAmount + lateFee) FROM payments")
    fun getTotalPremiumCollected(): Flow<Double?>
}

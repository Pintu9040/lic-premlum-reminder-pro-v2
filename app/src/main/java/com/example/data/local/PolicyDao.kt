package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies ORDER BY dueDate ASC")
    fun getAllPolicies(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies ORDER BY dueDate ASC")
    suspend fun getAllPoliciesSync(): List<PolicyEntity>

    @Query("SELECT * FROM policies WHERE id = :id")
    suspend fun getPolicyById(id: Long): PolicyEntity?

    @Query("SELECT * FROM policies WHERE customerId = :customerId ORDER BY dueDate ASC")
    fun getPoliciesByCustomerId(customerId: Long): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE policyNumber LIKE '%' || :query || '%' OR planName LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%'")
    fun searchPolicies(query: String): Flow<List<PolicyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPolicy(policy: PolicyEntity): Long

    @Update
    suspend fun updatePolicy(policy: PolicyEntity)

    @Delete
    suspend fun deletePolicy(policy: PolicyEntity)

    @Query("SELECT COUNT(*) FROM policies")
    fun getPolicyCount(): Flow<Int>
}

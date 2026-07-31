package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getDocumentsForCustomer(customerId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE policyId = :policyId ORDER BY createdAt DESC")
    fun getDocumentsForPolicy(policyId: Long): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
}

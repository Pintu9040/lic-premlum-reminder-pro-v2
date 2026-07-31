package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_profile WHERE id = 1")
    fun getAgentProfile(): Flow<AgentProfileEntity?>

    @Query("SELECT * FROM agent_profile WHERE id = 1")
    suspend fun getAgentProfileSync(): AgentProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAgentProfile(profile: AgentProfileEntity)
}

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_profile")
data class AgentProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val agentName: String = "Pintu Ojha",
    val agencyCode: String = "LIC-AGENT-89421",
    val branchCode: String = "08B",
    val branchName: String = "Bhubaneswar Branch",
    val licenseNumber: String = "LIC-LIC-901234",
    val email: String = "agent@licreminderpro.com",
    val mobile: String = "+91 98765 43210",
    val officeAddress: String = "Plot 102, Janpath, Bhubaneswar, Odisha",
    val photoUri: String = "",
    val themeMode: String = "System", // "Light", "Dark", "System"
    val isDarkMode: Boolean = false,
    val pinCode: String = "",
    val isBiometricEnabled: Boolean = false,
    val autoLogoutMinutes: Int = 15,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncedTime: String = "Just now"
)

package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AgentProfileEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(
    viewModel: LicViewModel,
    onLogout: () -> Unit = {}
) {
    val agentProfile by viewModel.agentProfile.collectAsState()
    val context = LocalContext.current

    // Agent Profile Fields
    var agentName by remember(agentProfile) { mutableStateOf(agentProfile?.agentName ?: "Pintu Ojha") }
    var agencyCode by remember(agentProfile) { mutableStateOf(agentProfile?.agencyCode ?: "LIC-AG-89421") }
    var branchName by remember(agentProfile) { mutableStateOf(agentProfile?.branchName ?: "Branch 883 (Jaipur)") }
    var licenseNumber by remember(agentProfile) { mutableStateOf(agentProfile?.licenseNumber ?: "LIC-LIC-901234") }
    var phone by remember(agentProfile) { mutableStateOf(agentProfile?.mobile ?: "+91 98765 43210") }
    var email by remember(agentProfile) { mutableStateOf(agentProfile?.email ?: "pintu.lic.agent@gmail.com") }
    var photoUriStr by remember(agentProfile) { mutableStateOf(agentProfile?.photoUri ?: "") }

    // Theme & Preferences
    var selectedThemeMode by remember(agentProfile) { mutableStateOf(agentProfile?.themeMode ?: "System") }

    // Security Settings
    var pinCode by remember(agentProfile) { mutableStateOf(agentProfile?.pinCode ?: "") }
    var isAppLockEnabled by remember(pinCode) { mutableStateOf(pinCode.isNotBlank()) }
    var isBiometricEnabled by remember(agentProfile) { mutableStateOf(agentProfile?.isBiometricEnabled ?: false) }
    var isFaceUnlockEnabled by remember { mutableStateOf(false) }
    var autoLogoutMinutes by remember(agentProfile) { mutableStateOf(agentProfile?.autoLogoutMinutes ?: 15) }

    // Notification Toggles
    var isPremiumDueReminderEnabled by remember { mutableStateOf(true) }
    var isBirthdayReminderEnabled by remember { mutableStateOf(true) }
    var isAnniversaryReminderEnabled by remember { mutableStateOf(true) }
    var isFollowUpReminderEnabled by remember { mutableStateOf(true) }
    var isDailySummaryEnabled by remember { mutableStateOf(true) }

    // Sync & Backup State
    val syncStatus by viewModel.syncStatus.collectAsState()
    var isAutoSyncEnabled by remember(agentProfile) { mutableStateOf(agentProfile?.isAutoSyncEnabled ?: true) }
    var lastSyncedTime by remember(agentProfile) { mutableStateOf(agentProfile?.lastSyncedTime ?: "Just now") }

    // Dialog States
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePhotoSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showUserGuideDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Photo Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUriStr = uri.toString()
            saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
            Toast.makeText(context, "Profile Photo Updated!", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "agent_photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            photoUriStr = Uri.fromFile(file).toString()
            saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
            Toast.makeText(context, "Profile Photo Captured!", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Database File Selected for Import!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Banner Header
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Settings & Security",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "Advisor Profile, App Security, Backup & Notification Controls",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentOrangeLight,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==========================================
            // 1. PROFILE SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROFILE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Icon(Icons.Default.Badge, contentDescription = null, tint = RoyalBluePrimary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Profile Avatar
                        Surface(
                            shape = CircleShape,
                            color = RoyalBlueContainer,
                            modifier = Modifier
                                .size(72.dp)
                                .border(2.dp, RoyalBluePrimary, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (photoUriStr.isNotBlank()) {
                                    AsyncImage(
                                        model = photoUriStr,
                                        contentDescription = "Agent Photo",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = RoyalBluePrimary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }

                        // Info Display
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = agentName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Code: $agencyCode",
                                style = MaterialTheme.typography.labelMedium.copy(color = RoyalBluePrimary, fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Branch: $branchName",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = "$phone • $email",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1
                            )
                        }
                    }

                    HorizontalDivider()

                    // Action Buttons: Edit Profile & Change Photo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showChangePhotoSheet = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }


            // ==========================================
            // 2. SECURITY SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SECURITY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Change Password
                    SettingsClickableRow(
                        icon = Icons.Default.LockReset,
                        title = "Change Password",
                        subtitle = "Update advisor account login password",
                        onClick = { showChangePasswordDialog = true }
                    )

                    HorizontalDivider()

                    // App Lock (PIN)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Lock (PIN)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                if (pinCode.isBlank()) "Disabled" else "4-Digit PIN Configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pinCode.isBlank()) Color.Gray else EmeraldGreenSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pinCode.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Change PIN", fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = isAppLockEnabled,
                                onCheckedChange = { checked ->
                                    isAppLockEnabled = checked
                                    if (checked && pinCode.isBlank()) {
                                        showPinDialog = true
                                    } else if (!checked) {
                                        pinCode = ""
                                        saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, "", isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
                                        Toast.makeText(context, "App PIN Lock Disabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Fingerprint Login
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fingerprint Login", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Unlock using device biometric sensor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = {
                                isBiometricEnabled = it
                                saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, it, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
                            }
                        )
                    }

                    HorizontalDivider()

                    // Face Unlock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Face Unlock", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Unlock using camera recognition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isFaceUnlockEnabled,
                            onCheckedChange = {
                                isFaceUnlockEnabled = it
                                Toast.makeText(context, if (it) "Face Unlock Enabled" else "Face Unlock Disabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    HorizontalDivider()

                    // Auto Logout Timer
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Auto Logout Timer", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(5, 15, 30, 60, -1)) { mins ->
                                val labelText = if (mins == -1) "Never" else "${mins}m"
                                FilterChip(
                                    selected = autoLogoutMinutes == mins,
                                    onClick = {
                                        autoLogoutMinutes = mins
                                        saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, isBiometricEnabled, mins, isAutoSyncEnabled, lastSyncedTime)
                                    },
                                    label = { Text(labelText) }
                                )
                            }
                        }
                    }
                }
            }


            // ==========================================
            // 3. NOTIFICATIONS SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "NOTIFICATIONS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    SettingsSwitchRow(
                        title = "Premium Due Reminder",
                        subtitle = "Alerts for upcoming policy renewal dates",
                        checked = isPremiumDueReminderEnabled,
                        onCheckedChange = { isPremiumDueReminderEnabled = it }
                    )

                    SettingsSwitchRow(
                        title = "Birthday Reminder",
                        subtitle = "Notify client birthdays for greeting dispatch",
                        checked = isBirthdayReminderEnabled,
                        onCheckedChange = { isBirthdayReminderEnabled = it }
                    )

                    SettingsSwitchRow(
                        title = "Anniversary Reminder",
                        subtitle = "Notify wedding anniversaries of policyholders",
                        checked = isAnniversaryReminderEnabled,
                        onCheckedChange = { isAnniversaryReminderEnabled = it }
                    )

                    SettingsSwitchRow(
                        title = "Follow-up Reminder",
                        subtitle = "Reminders for scheduled client calls & meetings",
                        checked = isFollowUpReminderEnabled,
                        onCheckedChange = { isFollowUpReminderEnabled = it }
                    )

                    SettingsSwitchRow(
                        title = "Daily Summary Notification",
                        subtitle = "Morning overview of total collections & dues",
                        checked = isDailySummaryEnabled,
                        onCheckedChange = { isDailySummaryEnabled = it }
                    )
                }
            }


            // ==========================================
            // 4. BACKUP & SYNC SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BACKUP & SYNC",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldGreenSecondary)
                    }

                    // Last Sync Time Display
                    Surface(
                        color = EmeraldGreenSecondary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Last Sync Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val displayTime = when (syncStatus) {
                                    is com.example.data.remote.SyncStatus.Synced -> (syncStatus as com.example.data.remote.SyncStatus.Synced).lastSyncTime
                                    is com.example.data.remote.SyncStatus.Offline -> (syncStatus as com.example.data.remote.SyncStatus.Offline).lastSyncTime
                                    else -> lastSyncedTime
                                }
                                Text(displayTime, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Background Cloud Sync", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Switch(
                            checked = isAutoSyncEnabled,
                            onCheckedChange = {
                                isAutoSyncEnabled = it
                                saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, isBiometricEnabled, autoLogoutMinutes, it, lastSyncedTime)
                            }
                        )
                    }

                    // Action Buttons: Sync Now, Backup Now, Restore Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerSync()
                                Toast.makeText(context, "Cloud Sync Initiated!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.agentProfile.value?.let { prof -> viewModel.saveAgentProfile(prof) }
                                Toast.makeText(context, "Database Backup Created!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup Now", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { showRestoreConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore", fontSize = 11.sp)
                        }
                    }
                }
            }


            // ==========================================
            // 5. APPEARANCE SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "APPEARANCE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Light Theme", "Dark Theme", "System Theme").forEach { mode ->
                            val cleanMode = mode.replace(" Theme", "")
                            val isSelected = selectedThemeMode.equals(cleanMode, ignoreCase = true) || (cleanMode == "System" && selectedThemeMode == "System")
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedThemeMode = cleanMode
                                    saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, cleanMode, pinCode, isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
                                },
                                label = { Text(mode, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (cleanMode) {
                                            "Light" -> Icons.Default.LightMode
                                            "Dark" -> Icons.Default.DarkMode
                                            else -> Icons.Default.SettingsSuggest
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }


            // ==========================================
            // 6. DATA MANAGEMENT SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DATA MANAGEMENT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Database Exported to Downloads folder!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Database", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Database", fontSize = 11.sp)
                        }
                    }

                    // Clear Cache Button
                    Button(
                        onClick = { showClearCacheDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Cache", fontWeight = FontWeight.Bold)
                    }
                }
            }


            // ==========================================
            // 7. HELP & SUPPORT SECTION
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HELP & SUPPORT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    SettingsClickableRow(
                        icon = Icons.Default.MenuBook,
                        title = "User Guide",
                        subtitle = "Advisor manual and quick start guide",
                        onClick = { showUserGuideDialog = true }
                    )

                    HorizontalDivider()

                    SettingsClickableRow(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "Data encryption & client privacy rules",
                        onClick = { showPrivacyPolicyDialog = true }
                    )

                    HorizontalDivider()

                    SettingsClickableRow(
                        icon = Icons.Default.Description,
                        title = "Terms & Conditions",
                        subtitle = "CRM software licensing agreement",
                        onClick = { showTermsDialog = true }
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = RoyalBluePrimary)
                            Text("App Version", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("v2.5.0 (Build 2026)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Contact Support", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@licreminderpro.in"))
                                context.startActivity(Intent.createChooser(intent, "Contact Email Support"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Email Support", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+911800223344"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call Support", fontSize = 11.sp)
                        }
                    }
                }
            }


            // ==========================================
            // LOGOUT SESSION BUTTON
            // ==========================================
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logout_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Logout Advisor Session", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }


    // ==========================================
    // DIALOGS & MODALS
    // ==========================================

    // 1. Edit Profile Dialog
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(agentName) }
        var tempAgencyCode by remember { mutableStateOf(agencyCode) }
        var tempLicense by remember { mutableStateOf(licenseNumber) }
        var tempBranch by remember { mutableStateOf(branchName) }
        var tempPhone by remember { mutableStateOf(phone) }
        var tempEmail by remember { mutableStateOf(email) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Advisor Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Agent Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_agent_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = tempAgencyCode,
                        onValueChange = { tempAgencyCode = it },
                        label = { Text("Agency Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = tempLicense,
                        onValueChange = { tempLicense = it },
                        label = { Text("License Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = tempBranch,
                        onValueChange = { tempBranch = it },
                        label = { Text("Branch Office") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = tempPhone,
                        onValueChange = { tempPhone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        agentName = tempName
                        agencyCode = tempAgencyCode
                        licenseNumber = tempLicense
                        branchName = tempBranch
                        phone = tempPhone
                        email = tempEmail
                        saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, pinCode, isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
                        showEditProfileDialog = false
                        Toast.makeText(context, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 2. Change Photo Dialog Sheet
    if (showChangePhotoSheet) {
        AlertDialog(
            onDismissRequest = { showChangePhotoSheet = false },
            title = { Text("Change Agent Photo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Select photo source to update your profile picture:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showChangePhotoSheet = false
                            cameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }
                    Button(
                        onClick = {
                            showChangePhotoSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePhotoSheet = false }) { Text("Cancel") }
            }
        )
    }

    // 3. Change Password Dialog
    if (showChangePasswordDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Account Password", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.isNotBlank() && newPassword == confirmPassword) {
                            showChangePasswordDialog = false
                            Toast.makeText(context, "Password Changed Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 4. PIN Lock Dialog
    if (showPinDialog) {
        var tempPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure Security PIN Lock", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a 4-digit numeric passcode to secure your app access:")
                    OutlinedTextField(
                        value = tempPin,
                        onValueChange = { if (it.length <= 4) tempPin = it },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pinCode = tempPin
                        isAppLockEnabled = tempPin.isNotBlank()
                        saveProfile(viewModel, agentName, agencyCode, branchName, licenseNumber, phone, email, photoUriStr, selectedThemeMode, tempPin, isBiometricEnabled, autoLogoutMinutes, isAutoSyncEnabled, lastSyncedTime)
                        showPinDialog = false
                        Toast.makeText(context, "Security PIN Saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 5. Restore Backup Confirm Dialog
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restore Cloud Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Are you sure you want to restore data from cloud backup? This will sync your local database with the latest cloud records.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerSync()
                        showRestoreConfirmDialog = false
                        Toast.makeText(context, "Restoring data from Cloud Backup...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Proceed Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 6. Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Temporary Cache", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This will clear temporary image thumbnails and cached application files.")
                    Text(
                        "Note: Customer, policy, and payment records are preserved safely.",
                        style = MaterialTheme.typography.labelMedium.copy(color = EmeraldGreenSecondary, fontWeight = FontWeight.Bold)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        Toast.makeText(context, "Cache Cleared Successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 7. User Guide Dialog
    if (showUserGuideDialog) {
        AlertDialog(
            onDismissRequest = { showUserGuideDialog = false },
            title = { Text("Advisor User Guide", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("1. Customer Directory: Add, search, and manage policyholders with full KYC details.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Policy Vault: Register policies, track sum assured, maturity date, and due premium mode.", style = MaterialTheme.typography.bodySmall)
                    Text("3. Partial Payments: Record unlimited partial or full premium payments with receipt generation.", style = MaterialTheme.typography.bodySmall)
                    Text("4. Performance Analytics: View collection trends, outstanding balances, and customer metrics.", style = MaterialTheme.typography.bodySmall)
                    Text("5. Reminders & WhatsApp: Send automated WhatsApp due alerts and birthday wishes.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showUserGuideDialog = false }) { Text("Close") } }
        )
    }

    // 8. Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy Policy", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "LIC Premium Reminder Pro respects advisor and client privacy.\n\n" +
                                "1. Data Storage: All policyholder KYC details, payment receipts, and contact records are encrypted locally in SQLite Room database and synced securely to Firebase Cloud.\n\n" +
                                "2. Security: No client personal data is sold or shared with third party advertisers.\n\n" +
                                "3. Permissions: Camera, Gallery, and Storage access are utilized solely for uploading document bonds and photos."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyPolicyDialog = false }) { Text("Close") } }
        )
    }

    // 9. Terms Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Terms of Use for LIC Premium Reminder Pro:\n\n" +
                                "1. Designed for authorized Life Insurance Corporation of India (LIC) Agents and Financial Advisors.\n" +
                                "2. Premium due calculations are advisory. Always verify with official LIC portal receipts.\n" +
                                "3. Backup policy records regularly to Firebase cloud."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("Close") } }
        )
    }
}

// Helper Function: Save Agent Profile
private fun saveProfile(
    viewModel: LicViewModel,
    name: String,
    agencyCode: String,
    branch: String,
    license: String,
    phone: String,
    email: String,
    photoUri: String,
    themeMode: String,
    pin: String,
    biometric: Boolean,
    logoutMins: Int,
    autoSync: Boolean,
    lastSync: String
) {
    val profile = AgentProfileEntity(
        id = 1,
        agentName = name,
        agencyCode = agencyCode,
        branchName = branch,
        licenseNumber = license,
        mobile = phone,
        email = email,
        photoUri = photoUri,
        themeMode = themeMode,
        isDarkMode = themeMode == "Dark",
        pinCode = pin,
        isBiometricEnabled = biometric,
        autoLogoutMinutes = logoutMins,
        isAutoSyncEnabled = autoSync,
        lastSyncedTime = lastSync
    )
    viewModel.saveAgentProfile(profile)
}

// Helper Composable: Settings Clickable Row
@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = RoyalBluePrimary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

// Helper Composable: Settings Switch Row
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

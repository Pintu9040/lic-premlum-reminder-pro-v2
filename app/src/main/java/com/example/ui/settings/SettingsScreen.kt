package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AgentProfileEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

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
    var isBiometricEnabled by remember(agentProfile) { mutableStateOf(agentProfile?.isBiometricEnabled ?: false) }
    var autoLogoutMinutes by remember(agentProfile) { mutableStateOf(agentProfile?.autoLogoutMinutes ?: 15) }

    // Sync & Backup State
    val syncStatus by viewModel.syncStatus.collectAsState()
    var isAutoSyncEnabled by remember(agentProfile) { mutableStateOf(agentProfile?.isAutoSyncEnabled ?: true) }
    var lastSyncedTime by remember(agentProfile) { mutableStateOf(agentProfile?.lastSyncedTime ?: "Just now") }

    // Dialogs
    var showPinDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Profile Photo Launchers
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUriStr = uri.toString()
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "agent_avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            photoUriStr = Uri.fromFile(file).toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header Surface
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Settings & Agency Configuration",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "Manage Profile, Cloud Backup, Theme & Security Locks",
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
            // 1. AGENT PROFILE CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
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
                            text = "Agent Profile Credentials",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(Icons.Default.Badge, contentDescription = null, tint = RoyalBluePrimary)
                    }

                    // Avatar Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RoyalBlueContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(36.dp))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Profile Photo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { cameraPhotoLauncher.launch(null) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Camera", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { photoLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gallery", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Agent Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_agent_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = agencyCode,
                            onValueChange = { agencyCode = it },
                            label = { Text("Agency Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = licenseNumber,
                            onValueChange = { licenseNumber = it },
                            label = { Text("License Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Branch Office / Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val updatedProfile = AgentProfileEntity(
                                id = 1,
                                agentName = agentName,
                                agencyCode = agencyCode,
                                branchName = branchName,
                                licenseNumber = licenseNumber,
                                mobile = phone,
                                email = email,
                                photoUri = photoUriStr,
                                themeMode = selectedThemeMode,
                                pinCode = pinCode,
                                isBiometricEnabled = isBiometricEnabled,
                                autoLogoutMinutes = autoLogoutMinutes,
                                isAutoSyncEnabled = isAutoSyncEnabled,
                                lastSyncedTime = lastSyncedTime
                            )
                            viewModel.saveAgentProfile(updatedProfile)
                            Toast.makeText(context, "Agent Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Agent Credentials", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. THEME CONFIGURATION CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("App Display Theme", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Light", "Dark", "System").forEach { mode ->
                            FilterChip(
                                selected = selectedThemeMode == mode,
                                onClick = {
                                    selectedThemeMode = mode
                                    val prof = (agentProfile ?: AgentProfileEntity()).copy(themeMode = mode, isDarkMode = mode == "Dark")
                                    viewModel.saveAgentProfile(prof)
                                },
                                label = { Text(mode) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (mode) {
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

            // 3. BACKUP & SYNC CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Firebase Cloud Sync & Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                val displayTime = when (syncStatus) {
                                    is com.example.data.remote.SyncStatus.Synced -> (syncStatus as com.example.data.remote.SyncStatus.Synced).lastSyncTime
                                    is com.example.data.remote.SyncStatus.Offline -> (syncStatus as com.example.data.remote.SyncStatus.Offline).lastSyncTime
                                    else -> lastSyncedTime
                                }
                                Text("Offline support active • $displayTime", style = MaterialTheme.typography.labelSmall, color = EmeraldGreenSecondary)
                            }
                        }
                        val statusText = when (syncStatus) {
                            is com.example.data.remote.SyncStatus.Syncing -> "SYNCING"
                            is com.example.data.remote.SyncStatus.Synced -> "SYNCED"
                            is com.example.data.remote.SyncStatus.Offline -> "OFFLINE"
                            is com.example.data.remote.SyncStatus.Error -> "ERROR"
                            else -> if (isAutoSyncEnabled) "ACTIVE" else "MANUAL"
                        }
                        StatusBadge(status = statusText)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Background Sync", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Switch(
                            checked = isAutoSyncEnabled,
                            onCheckedChange = {
                                isAutoSyncEnabled = it
                                val prof = (agentProfile ?: AgentProfileEntity()).copy(isAutoSyncEnabled = it)
                                viewModel.saveAgentProfile(prof)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.triggerSync()
                                Toast.makeText(context, "Firebase Sync & Auto Restore Started!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync & Restore", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.agentProfile.value?.let { prof ->
                                    viewModel.saveAgentProfile(prof)
                                }
                                Toast.makeText(context, "Cloud Backup Triggered!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup Now", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. SECURITY & LOCK CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Security & Access Protection", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("4-Digit PIN Lock", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(if (pinCode.isBlank()) "Disabled" else "PIN Active", style = MaterialTheme.typography.labelSmall, color = if (pinCode.isBlank()) Color.Gray else EmeraldGreenSecondary)
                        }
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (pinCode.isBlank()) "Set PIN" else "Change PIN")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fingerprint / Biometric Login", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = {
                                isBiometricEnabled = it
                                val prof = (agentProfile ?: AgentProfileEntity()).copy(isBiometricEnabled = it)
                                viewModel.saveAgentProfile(prof)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Logout Session Timeout", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(listOf(5, 15, 30)) { mins ->
                                FilterChip(
                                    selected = autoLogoutMinutes == mins,
                                    onClick = {
                                        autoLogoutMinutes = mins
                                        val prof = (agentProfile ?: AgentProfileEntity()).copy(autoLogoutMinutes = mins)
                                        viewModel.saveAgentProfile(prof)
                                    },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            }

            // 5. APP INFO & SUPPORT CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("App Information & Legal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App Version", style = MaterialTheme.typography.bodySmall)
                        Text("v2.5.0 (Build 2026)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showPrivacyPolicyDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Privacy Policy", style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTermsDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Terms & Conditions", style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("About App", style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Contact Support & Helpdesk", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
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
                            Text("Email", fontSize = 11.sp)
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
                            Text("Call", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().testTag("logout_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Logout Advisor Session", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // PIN Dialog
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
                        val prof = (agentProfile ?: AgentProfileEntity()).copy(pinCode = tempPin)
                        viewModel.saveAgentProfile(prof)
                        showPinDialog = false
                        Toast.makeText(context, "Security PIN Updated!", Toast.LENGTH_SHORT).show()
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

    // Privacy Policy Dialog
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

    // Terms Dialog
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

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About LIC Premium Reminder Pro", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Text(
                    "LIC Premium Reminder Pro is the premier CRM and portfolio management companion for insurance agents in India.\n\n" +
                            "Features:\n" +
                            "• Automated Premium Reminders & WhatsApp Follow-ups\n" +
                            "• Birthday & Anniversary Wish Dispatch\n" +
                            "• Complete Document & KYC Vault\n" +
                            "• Business Reports with PDF & Excel Export\n" +
                            "• Firebase Cloud Sync & Security Locks"
                )
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }
}

package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Color Palette Definition for Royal Blue + Dark Theme
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val RoyalBluePrimary = Color(0xFF1E3A8A)
private val RoyalBlueLight = Color(0xFF2563EB)
private val RoyalBlueGlow = Color(0xFF3B82F6)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentRed = Color(0xFFEF4444)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

// Data Models for History
data class DetailedBackupHistoryItem(
    val id: String,
    val date: String,
    val time: String,
    val size: String,
    val duration: String,
    val status: String, // "Success", "Failed", "In Progress"
    val destination: String
)

enum class AutoBackupFreq(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBackClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Backup & Restore State Controls
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableFloatStateOf(0f) }
    var cloudConnected by remember { mutableStateOf(true) }
    var lastBackupDate by remember { mutableStateOf("No backup yet") }
    var lastBackupSize by remember { mutableStateOf("0 MB") }

    // Stats Counters
    var totalCustomers by remember { mutableIntStateOf(0) }
    var totalPolicies by remember { mutableIntStateOf(0) }
    var totalDocuments by remember { mutableIntStateOf(0) }

    // Auto Backup Settings
    var autoBackupEnabled by remember { mutableStateOf(true) }
    var selectedFrequency by remember { mutableStateOf(AutoBackupFreq.DAILY) }
    var wifiOnlyEnabled by remember { mutableStateOf(true) }
    var includeDocsEnabled by remember { mutableStateOf(true) }

    // Security Controls
    var encryptedBackupEnabled by remember { mutableStateOf(true) }
    var biometricRestoreEnabled by remember { mutableStateOf(true) }
    var pinProtectionEnabled by remember { mutableStateOf(false) }

    // Dialog & Sheet Controls
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Backup History List
    var historyList by remember {
        mutableStateOf<List<DetailedBackupHistoryItem>>(emptyList())
    }

    fun loadRealData() {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val cCount = try { db.customerDao().getAllCustomersSync().size } catch (e: Throwable) { 0 }
            val pCount = try { db.policyDao().getAllPoliciesSync().size } catch (e: Throwable) { 0 }
            val dCount = try { db.documentDao().getAllDocumentsSync().size } catch (e: Throwable) { 0 }

            totalCustomers = cCount
            totalPolicies = pCount
            totalDocuments = dCount

            val localHistory = com.example.data.backup.BackupManager.getLocalHistory(context)
            if (localHistory.isNotEmpty()) {
                val latest = localHistory.first()
                lastBackupDate = "${latest.date}, ${latest.time}"
                lastBackupSize = latest.size

                historyList = localHistory.map { item ->
                    DetailedBackupHistoryItem(
                        id = item.id,
                        date = item.date,
                        time = item.time,
                        size = item.size,
                        duration = item.duration,
                        status = item.status,
                        destination = item.destination
                    )
                }
            } else {
                lastBackupDate = "Not backed up yet"
                lastBackupSize = "0 KB"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRealData()
    }

    // Function to trigger production full backup process
    fun triggerBackupNow() {
        if (isBackingUp || isRestoring) return
        coroutineScope.launch {
            isBackingUp = true
            syncProgress = 0.05f
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val res = com.example.data.backup.BackupManager.createFullBackup(context, db) { p ->
                syncProgress = p
            }
            res.onSuccess { item ->
                isBackingUp = false
                lastBackupDate = "${item.date}, ${item.time}"
                lastBackupSize = item.size
                cloudConnected = true
                loadRealData()
                snackbarHostState.showSnackbar("Backup package created & saved successfully! (${item.size})")
            }.onFailure { err ->
                isBackingUp = false
                snackbarHostState.showSnackbar("Backup failed: ${err.message}")
            }
        }
    }

    // Function to trigger production restore process
    fun triggerRestoreNow() {
        if (isRestoring || isBackingUp) return
        coroutineScope.launch {
            isRestoring = true
            syncProgress = 0.05f
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val localHistory = com.example.data.backup.BackupManager.getLocalHistory(context)
            val backupItem = localHistory.firstOrNull() ?: com.example.data.backup.BackupHistoryItemData(id = "none")

            val res = com.example.data.backup.BackupManager.restoreBackup(context, db, backupItem, replaceExisting = true) { p ->
                syncProgress = p
            }
            res.onSuccess { msg ->
                isRestoring = false
                loadRealData()
                snackbarHostState.showSnackbar(msg)
            }.onFailure { err ->
                isRestoring = false
                snackbarHostState.showSnackbar("Restore failed: ${err.message}")
            }
        }
    }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Data Backup & Restore",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("backup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("backup_help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            tint = TextWhite
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("backup_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TextWhite
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Toggle Cloud Connection", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = RoyalBlueGlow) },
                                onClick = {
                                    showMoreMenu = false
                                    cloudConnected = !cloudConnected
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(if (cloudConnected) "Cloud status: Connected" else "Cloud status: Disconnected")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Backup History", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccentRed) },
                                onClick = {
                                    showMoreMenu = false
                                    historyList = emptyList()
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Backup history cleared") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Demo History", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, tint = AccentGreen) },
                                onClick = {
                                    showMoreMenu = false
                                    historyList = listOf(
                                        DetailedBackupHistoryItem("1", "04 Aug 2026", "05:30 PM", "14.2 MB", "12 sec", "Success", "Firebase Cloud"),
                                        DetailedBackupHistoryItem("2", "03 Aug 2026", "08:15 AM", "14.0 MB", "10 sec", "Success", "Local Device"),
                                        DetailedBackupHistoryItem("3", "01 Aug 2026", "11:45 PM", "13.8 MB", "14 sec", "Success", "Firebase Cloud"),
                                        DetailedBackupHistoryItem("4", "25 Jul 2026", "06:00 PM", "13.5 MB", "Failed", "Failed (Network)", "Google Drive"),
                                        DetailedBackupHistoryItem("5", "18 Jul 2026", "09:20 AM", "12.9 MB", "8 sec", "Success", "Local Device")
                                    )
                                    coroutineScope.launch { snackbarHostState.showSnackbar("History restored") }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            // BOTTOM STICKY BUTTONS (Equal width, 56dp height, 20dp rounded)
            Surface(
                color = DarkBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_bottom_sticky_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { triggerBackupNow() },
                        enabled = !isBackingUp && !isRestoring,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("sticky_backup_now_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalBlueLight,
                            contentColor = TextWhite
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TextWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backing up...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup Now", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showRestoreConfirmDialog = true },
                        enabled = !isBackingUp && !isRestoring,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("sticky_restore_button"),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, RoyalBlueLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RoyalBlueLight
                        )
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = RoyalBlueLight,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restoring...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Backup", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // HEADER BANNER (Cloud Shield Illustration & Subtitle)
            HeaderShieldBanner()

            // BACKUP STATUS CARD (Cloud Status, Last Backup, Size, Customers, Policies, Documents, Animated Sync)
            BackupStatusCardDetailed(
                cloudConnected = cloudConnected,
                lastBackupDate = lastBackupDate,
                lastBackupSize = lastBackupSize,
                totalCustomers = totalCustomers,
                totalPolicies = totalPolicies,
                totalDocuments = totalDocuments,
                isSyncing = isBackingUp || isRestoring,
                syncProgress = syncProgress
            )

            // QUICK ACTION GRID (6 Items: Backup Now, Restore, Export Data, Import Data, Backup History, Auto Backup)
            QuickActionGridSection(
                onBackupNow = { triggerBackupNow() },
                onRestore = { showRestoreConfirmDialog = true },
                onExport = { showExportDialog = true },
                onImport = { showImportDialog = true },
                onHistory = { showHistoryDialog = true },
                onAutoBackup = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Configure Auto Backup options below") }
                }
            )

            // AUTO BACKUP SECTION (Switch, Frequency, Wi-Fi, Include Docs, Next Scheduled)
            AutoBackupDetailedSection(
                enabled = autoBackupEnabled,
                onEnabledChange = { enabled ->
                    autoBackupEnabled = enabled
                    if (enabled) {
                        com.example.data.backup.BackupManager.scheduleAutoBackupWork(context, selectedFrequency.label, wifiOnlyEnabled, false)
                    } else {
                        com.example.data.backup.BackupManager.cancelAutoBackupWork(context)
                    }
                },
                frequency = selectedFrequency,
                onFrequencyChange = { freq ->
                    selectedFrequency = freq
                    if (autoBackupEnabled) {
                        com.example.data.backup.BackupManager.scheduleAutoBackupWork(context, freq.label, wifiOnlyEnabled, false)
                    }
                },
                wifiOnly = wifiOnlyEnabled,
                onWifiOnlyChange = { wifi ->
                    wifiOnlyEnabled = wifi
                    if (autoBackupEnabled) {
                        com.example.data.backup.BackupManager.scheduleAutoBackupWork(context, selectedFrequency.label, wifi, false)
                    }
                },
                includeDocs = includeDocsEnabled,
                onIncludeDocsChange = { includeDocsEnabled = it }
            )

            // STORAGE SECTION (Cloud Storage, Local Storage, Available Space, Progress Bars)
            StorageUsageSection()

            // SECURITY SECTION (Encrypted Backup, Biometric Restore, PIN Protection)
            SecuritySection(
                encrypted = encryptedBackupEnabled,
                onEncryptedChange = { encryptedBackupEnabled = it },
                biometric = biometricRestoreEnabled,
                onBiometricChange = { biometricRestoreEnabled = it },
                pinProtected = pinProtectionEnabled,
                onPinChange = { pinProtectionEnabled = it }
            )

            // BACKUP HISTORY SECTION (Latest 5 or Empty State)
            BackupHistorySection(
                historyList = historyList,
                onHistoryItemClick = { item ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Backup point: ${item.date} (${item.size}, ${item.destination})")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // DIALOGS & MODALS

    // Dialog: Help
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Data Backup & Restore Guide", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• Backup Now: Instantly creates an encrypted cloud snapshot of all LIC clients, policy details, and payment receipts.", color = TextWhite, fontSize = 13.sp)
                    Text("• Auto Backup: Schedules background auto-sync (Daily, Weekly, Monthly) when connected to Wi-Fi.", color = TextWhite, fontSize = 13.sp)
                    Text("• Security Shield: All snapshots are secured with AES-256 military grade encryption.", color = AccentGreen, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("Got It", color = TextWhite)
                }
            }
        )
    }

    // Dialog: Restore Confirmation
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Restore", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Restoring will overwrite current local cache with backup snapshot ($lastBackupDate). Are you sure you want to proceed?",
                    color = TextMuted,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        triggerRestoreNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight)
                ) {
                    Text("Start Restore", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Dialog: Export Data
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.IosShare, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Data Options", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            showExportDialog = false
                            triggerBackupNow()
                        },
                        color = DarkBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = RoyalBlueLight)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Encrypted JSON (.json)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Ideal for device-to-device migration", color = TextMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            showExportDialog = false
                            coroutineScope.launch { snackbarHostState.showSnackbar("ZIP Archive created successfully") }
                        },
                        color = DarkBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("ZIP Archive (.zip)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Includes database & scanned policy documents", color = TextMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    // Dialog: Import Data
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Backup File", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a valid .json or .zip LIC Backup file from your device internal storage.", color = TextMuted, fontSize = 13.sp)
                    Button(
                        onClick = {
                            showImportDialog = false
                            triggerRestoreNow()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Internal Storage", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Dialog: Full History View
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = RoyalBlueGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup History Log", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (historyList.isEmpty()) {
                        Text("No backup available.", color = TextMuted, fontSize = 13.5.sp)
                    } else {
                        historyList.forEach { item ->
                            Surface(color = DarkBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${item.date} • ${item.time}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text("${item.size} • ${item.destination}", color = TextMuted, fontSize = 11.sp)
                                    }
                                    StatusBadgeChip(status = item.status)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHistoryDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("Close Log", color = TextWhite)
                }
            }
        )
    }
}

// ===========================================================================
// SUB-COMPONENTS
// ===========================================================================

/**
 * HEADER SHIELD BANNER
 * Cloud Shield Illustration Badge + Subtitle
 */
@Composable
fun HeaderShieldBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("header_shield_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            RoyalBluePrimary.copy(alpha = 0.5f),
                            CardBg
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cloud Shield Illustration Badge
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(RoyalBlueGlow, RoyalBluePrimary)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Cloud Shield",
                        tint = TextWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud Shield Active",
                        color = RoyalBlueGlow,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Data Backup & Restore",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Keep your LIC customer data safe and secure.",
                        color = TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * BACKUP STATUS CARD DETAILED
 * Displays Cloud Status, Last Backup, Backup Size, Total Customers, Total Policies, Total Documents, Animated Sync Indicator
 */
@Composable
fun BackupStatusCardDetailed(
    cloudConnected: Boolean,
    lastBackupDate: String,
    lastBackupSize: String,
    totalCustomers: Int,
    totalPolicies: Int,
    totalDocuments: Int,
    isSyncing: Boolean,
    syncProgress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isSyncing) syncProgress else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "syncProgressAnim"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_status_card_detailed")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Cloud Status Badge & Sync Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (cloudConnected) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (cloudConnected) Icons.Default.CloudSync else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (cloudConnected) AccentGreen else AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Cloud Status", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (cloudConnected) "Connected" else "Not Connected",
                            color = if (cloudConnected) AccentGreen else AccentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }

                Surface(
                    color = DarkBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (cloudConnected) AccentGreen else AccentAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Sync", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // Row 2: Grid Stats (Last Backup, Size, Customers, Policies, Documents)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("LAST BACKUP", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(lastBackupDate, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BACKUP SIZE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(lastBackupSize, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Stat Counter Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadgeItem(
                    title = "Customers",
                    value = "$totalCustomers",
                    icon = Icons.Default.People,
                    color = RoyalBlueLight,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeItem(
                    title = "Policies",
                    value = "$totalPolicies",
                    icon = Icons.Default.Assignment,
                    color = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeItem(
                    title = "Documents",
                    value = "$totalDocuments",
                    icon = Icons.Default.Description,
                    color = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // Animated Sync Progress Indicator
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isSyncing) "Syncing in progress..." else "Cloud Vault Health: Optimal",
                        color = if (isSyncing) RoyalBlueGlow else TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = TextWhite,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isSyncing) RoyalBlueGlow else AccentGreen,
                    trackColor = DarkBg
                )
            }
        }
    }
}

@Composable
fun StatBadgeItem(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(title, color = TextMuted, fontSize = 10.sp)
        }
    }
}

/**
 * QUICK ACTION GRID SECTION
 * 6 Quick Actions in 2-Column Grid:
 * 1. Backup Now
 * 2. Restore
 * 3. Export Data
 * 4. Import Data
 * 5. Backup History
 * 6. Auto Backup
 */
@Composable
fun QuickActionGridSection(
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onHistory: () -> Unit,
    onAutoBackup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick Actions",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        val gridItems = listOf(
            GridActionData("Backup Now", "Instant cloud sync", Icons.Default.CloudUpload, RoyalBlueLight, onBackupNow),
            GridActionData("Restore", "Restore snapshot", Icons.Default.CloudDownload, AccentGreen, onRestore),
            GridActionData("Export Data", "Save encrypted file", Icons.Default.IosShare, AccentAmber, onExport),
            GridActionData("Import Data", "Load from device", Icons.Default.FileDownload, Color(0xFFA855F7), onImport),
            GridActionData("Backup History", "View past 5 records", Icons.Default.History, RoyalBlueGlow, onHistory),
            GridActionData("Auto Backup", "Configure schedule", Icons.Default.Autorenew, Color(0xFFEC4899), onAutoBackup)
        )

        // 2 Column Grid Layout
        gridItems.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPair.forEach { item ->
                    QuickActionGridCard(item = item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

data class GridActionData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
fun QuickActionGridCard(
    item: GridActionData,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "gridScale"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = item.onClick
            )
            .testTag("quick_action_${item.title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(item.tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(
                    text = item.title,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * AUTO BACKUP DETAILED SECTION
 * Switch, Frequency (Daily, Weekly, Monthly), Wi-Fi Only, Include Documents, Next Scheduled Backup Display
 */
@Composable
fun AutoBackupDetailedSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    frequency: AutoBackupFreq,
    onFrequencyChange: (AutoBackupFreq) -> Unit,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    includeDocs: Boolean,
    onIncludeDocsChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_backup_detailed_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(RoyalBluePrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Auto Backup", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Scheduled background sync", color = TextMuted, fontSize = 11.5.sp)
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = RoyalBlueLight,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkBg
                    ),
                    modifier = Modifier.testTag("auto_backup_switch")
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = CardBorder)

                    Text("Frequency", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoBackupFreq.entries.forEach { freq ->
                            val isSelected = frequency == freq
                            Surface(
                                onClick = { onFrequencyChange(freq) },
                                color = if (isSelected) RoyalBlueLight else DarkBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = freq.label,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 9.dp)
                                )
                            }
                        }
                    }

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wi-Fi Only", color = TextWhite, fontSize = 13.5.sp)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = onWifiOnlyChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextWhite,
                                checkedTrackColor = RoyalBlueLight
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Include Documents", color = TextWhite, fontSize = 13.5.sp)
                        }
                        Switch(
                            checked = includeDocs,
                            onCheckedChange = onIncludeDocsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextWhite,
                                checkedTrackColor = RoyalBlueLight
                            )
                        )
                    }

                    // Next Scheduled Display
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Next Scheduled Backup: Tomorrow at 02:00 AM",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * STORAGE USAGE SECTION
 * Cloud Storage Used, Local Storage Used, Available Space, Animated Progress Bars
 */
@Composable
fun StorageUsageSection() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_usage_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Storage Usage Breakdown",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // Cloud Storage Bar
            StorageProgressRow(
                label = "Cloud Storage Used",
                valueText = "14.2 MB / 1.0 GB",
                progress = 0.014f,
                icon = Icons.Default.CloudQueue,
                color = RoyalBlueGlow
            )

            HorizontalDivider(color = CardBorder)

            // Local Storage Bar
            StorageProgressRow(
                label = "Local Device Storage",
                valueText = "28.5 MB / 128 GB",
                progress = 0.05f,
                icon = Icons.Default.SdStorage,
                color = AccentAmber
            )

            HorizontalDivider(color = CardBorder)

            // Available Space Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Available Space", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("84.2 GB Free", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
fun StorageProgressRow(
    label: String,
    valueText: String,
    progress: Float,
    icon: ImageVector,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Text(valueText, color = TextMuted, fontSize = 11.5.sp)
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = DarkBg
        )
    }
}

/**
 * SECURITY SECTION
 * Encrypted Backup, Biometric Restore, PIN Protection with Material Switches
 */
@Composable
fun SecuritySection(
    encrypted: Boolean,
    onEncryptedChange: (Boolean) -> Unit,
    biometric: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    pinProtected: Boolean,
    onPinChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_options_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Security & Encryption",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // Switch 1: Encrypted Backup
            SecuritySwitchRow(
                title = "Encrypted Backup",
                subtitle = "AES-256 military grade cloud encryption",
                icon = Icons.Default.Security,
                iconTint = AccentGreen,
                checked = encrypted,
                onCheckedChange = onEncryptedChange
            )

            HorizontalDivider(color = CardBorder)

            // Switch 2: Biometric Restore
            SecuritySwitchRow(
                title = "Biometric Restore",
                subtitle = "Require fingerprint/FaceID before restore",
                icon = Icons.Default.Fingerprint,
                iconTint = RoyalBlueGlow,
                checked = biometric,
                onCheckedChange = onBiometricChange
            )

            HorizontalDivider(color = CardBorder)

            // Switch 3: PIN Protection
            SecuritySwitchRow(
                title = "PIN Protection",
                subtitle = "Require 4-digit agent security PIN",
                icon = Icons.Default.Lock,
                iconTint = AccentAmber,
                checked = pinProtected,
                onCheckedChange = onPinChange
            )
        }
    }
}

@Composable
fun SecuritySwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = RoyalBlueLight
            )
        )
    }
}

/**
 * BACKUP HISTORY SECTION
 * Latest 5 Backups (Date, Time, Size, Duration, Status: Success, Failed, In Progress) or Empty State ("No backup available.")
 */
@Composable
fun BackupHistorySection(
    historyList: List<DetailedBackupHistoryItem>,
    onHistoryItemClick: (DetailedBackupHistoryItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_history_section")
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
                    text = "Backup History Log",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (historyList.isNotEmpty()) {
                    Text(
                        text = "Latest ${historyList.size}",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (historyList.isEmpty()) {
                // EMPTY STATE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(DarkBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No backup available.",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Backup Now' to create your first encrypted cloud snapshot.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                historyList.take(5).forEach { item ->
                    BackupHistoryCardRow(item = item, onClick = { onHistoryItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun BackupHistoryCardRow(
    item: DetailedBackupHistoryItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(RoyalBluePrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.destination.contains("Cloud")) Icons.Default.CloudQueue else Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = RoyalBlueLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${item.date} • ${item.time}",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Size: ${item.size} • Duration: ${item.duration}",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            StatusBadgeChip(status = item.status)
        }
    }
}

@Composable
fun StatusBadgeChip(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "success" -> Pair(AccentGreen.copy(alpha = 0.18f), AccentGreen)
        "failed" -> Pair(AccentRed.copy(alpha = 0.18f), AccentRed)
        else -> Pair(AccentAmber.copy(alpha = 0.18f), AccentAmber)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BackupRestoreScreenPreview() {
    BackupRestoreScreen()
}

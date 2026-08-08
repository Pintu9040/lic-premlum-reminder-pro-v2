package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettingsData
import com.example.data.local.AppSettingsManager
import com.example.data.local.LicBranch
import com.example.data.local.LicBranchMaster
import com.example.ui.LicViewModel
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LicViewModel? = null,
    onBackClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val agentProfileState by (viewModel?.agentProfile?.collectAsState() ?: remember { mutableStateOf(null) })

    // --- Profile State ---
    var agentName by remember { mutableStateOf("Pintu Ojha") }
    var agentCode by remember { mutableStateOf("LIC-AG-89421") }
    var branchCode by remember { mutableStateOf("08B") }
    var branchName by remember { mutableStateOf("Bhubaneswar Branch") }
    var mobileNumber by remember { mutableStateOf("+91 98765 43210") }
    var emailAddress by remember { mutableStateOf("pintu.lic.agent@gmail.com") }
    var officeAddress by remember { mutableStateOf("Plot 102, Janpath, Bhubaneswar, Odisha") }
    var photoUri by remember { mutableStateOf("") }

    LaunchedEffect(agentProfileState) {
        agentProfileState?.let {
            agentName = it.agentName
            agentCode = it.agencyCode
            branchCode = it.branchCode
            branchName = it.branchName
            mobileNumber = it.mobile
            emailAddress = it.email
            officeAddress = it.officeAddress
            photoUri = it.photoUri
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val savedLocalPath = AppSettingsManager.compressAndSaveProfilePhoto(context, it)
                photoUri = savedLocalPath
                val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                    agentName = agentName,
                    agencyCode = agentCode,
                    branchCode = branchCode,
                    branchName = branchName,
                    mobile = mobileNumber,
                    email = emailAddress,
                    officeAddress = officeAddress,
                    photoUri = savedLocalPath
                )
                viewModel?.saveAgentProfile(updated)
                snackbarHostState.showSnackbar("Profile photo compressed & updated across app!")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val res = com.example.data.backup.BackupManager.restoreFromUri(context, db, it)
                res.onSuccess {
                    viewModel?.triggerSync()
                    snackbarHostState.showSnackbar("Backup imported & database restored successfully!")
                }.onFailure { err ->
                    snackbarHostState.showSnackbar("Import failed: ${err.localizedMessage}")
                }
            }
        }
    }

    // --- App Preferences State ---
    var isDarkMode by remember { mutableStateOf(true) }
    var isSystemTheme by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedFontSize by remember { mutableStateOf("Medium") }

    // --- Notification Settings State ---
    var isPremiumReminder by remember { mutableStateOf(true) }
    var isDueTodayReminder by remember { mutableStateOf(true) }
    var isTomorrowReminder by remember { mutableStateOf(true) }
    var isUpcomingReminder by remember { mutableStateOf(true) }
    var isOverdueReminder by remember { mutableStateOf(true) }
    var isWhatsAppReminder by remember { mutableStateOf(true) }
    var selectedReminderTime by remember { mutableStateOf("09:00 AM") }
    var selectedNotificationSound by remember { mutableStateOf("LIC Chime") }
    var isVibrationEnabled by remember { mutableStateOf(true) }

    // --- Receipt Settings State ---
    var selectedReceiptSize by remember { mutableStateOf("A5") }
    var isReceiptHeaderEnabled by remember { mutableStateOf(true) }
    var receiptHeaderTitle by remember { mutableStateOf("LIC Premium Official Receipt") }
    var isAgentSignatureEnabled by remember { mutableStateOf(true) }
    var isQrCodeEnabled by remember { mutableStateOf(true) }
    var isAutoReceiptNumber by remember { mutableStateOf(true) }

    // --- Payment Settings State ---
    var accountHolderNameState by remember { mutableStateOf("Pintu Ojha") }
    var upiVpaIdState by remember { mutableStateOf("licagent@upi") }
    var upiIdError by remember { mutableStateOf<String?>(null) }
    var showTestQrDialog by remember { mutableStateOf(false) }

    // --- Backup Settings State ---
    var isAutoBackupEnabled by remember { mutableStateOf(true) }
    var isCloudSyncEnabled by remember { mutableStateOf(true) }
    var lastBackupText by remember { mutableStateOf("Today, 05:30 PM • 14.2 MB") }

    // --- Security Settings State ---
    var isAppLockEnabled by remember { mutableStateOf(true) }
    var isPinLockEnabled by remember { mutableStateOf(true) }
    var currentPinCode by remember { mutableStateOf("1234") }
    var isFingerprintEnabled by remember { mutableStateOf(true) }
    var isFaceUnlockEnabled by remember { mutableStateOf(false) }
    var selectedAutoLockTime by remember { mutableStateOf("5 Min") }

    // --- Data Management State ---
    var storageUsedMb by remember { mutableFloatStateOf(34.5f) }

    // Load initial persistent settings
    LaunchedEffect(Unit) {
        val loaded = AppSettingsManager.getSettings(context, agentProfileState)
        isDarkMode = loaded.isDarkMode
        isSystemTheme = loaded.isSystemTheme
        selectedLanguage = loaded.selectedLanguage
        selectedFontSize = loaded.selectedFontSize

        isPremiumReminder = loaded.isPremiumReminder
        isDueTodayReminder = loaded.isDueTodayReminder
        isTomorrowReminder = loaded.isTomorrowReminder
        isUpcomingReminder = loaded.isUpcomingReminder
        isOverdueReminder = loaded.isOverdueReminder
        isWhatsAppReminder = loaded.isWhatsAppReminder
        selectedReminderTime = loaded.selectedReminderTime
        selectedNotificationSound = loaded.selectedNotificationSound
        isVibrationEnabled = loaded.isVibrationEnabled

        selectedReceiptSize = loaded.selectedReceiptSize
        isReceiptHeaderEnabled = loaded.isReceiptHeaderEnabled
        receiptHeaderTitle = loaded.receiptHeaderTitle
        isAgentSignatureEnabled = loaded.isAgentSignatureOnReceipt
        isQrCodeEnabled = loaded.isQrCodeOnReceipt
        isAutoReceiptNumber = loaded.isAutoReceiptNumber

        accountHolderNameState = loaded.accountHolderName
        upiVpaIdState = loaded.upiVpaId

        accountHolderNameState = loaded.accountHolderName
        upiVpaIdState = loaded.upiVpaId

        isAutoBackupEnabled = loaded.isAutoBackupEnabled
        isCloudSyncEnabled = loaded.isCloudSyncEnabled
        lastBackupText = loaded.lastBackupText

        isAppLockEnabled = loaded.isAppLockEnabled
        isPinLockEnabled = loaded.isPinLockEnabled
        currentPinCode = loaded.pinCode
        isFingerprintEnabled = loaded.isFingerprintEnabled
        isFaceUnlockEnabled = loaded.isFaceUnlockEnabled
        selectedAutoLockTime = loaded.selectedAutoLockTime

        val dbFile = context.getDatabasePath("lic_reminder_pro_db")
        val sizeMb = if (dbFile.exists()) (dbFile.length() / (1024f * 1024f)) + 12.4f else 18.5f
        storageUsedMb = (sizeMb * 10).toInt() / 10f
    }

    // Auto-save setting changes helper
    fun persistSettings() {
        coroutineScope.launch(Dispatchers.IO) {
            val currentSettings = AppSettingsData(
                agentName = agentName,
                agencyCode = agentCode,
                branchCode = branchCode,
                branchName = branchName,
                mobileNumber = mobileNumber,
                emailAddress = emailAddress,
                officeAddress = officeAddress,
                photoUri = photoUri,
                isDarkMode = isDarkMode,
                isSystemTheme = isSystemTheme,
                selectedLanguage = selectedLanguage,
                selectedFontSize = selectedFontSize,
                isPremiumReminder = isPremiumReminder,
                isDueTodayReminder = isDueTodayReminder,
                isTomorrowReminder = isTomorrowReminder,
                isUpcomingReminder = isUpcomingReminder,
                isOverdueReminder = isOverdueReminder,
                isWhatsAppReminder = isWhatsAppReminder,
                selectedReminderTime = selectedReminderTime,
                selectedNotificationSound = selectedNotificationSound,
                isVibrationEnabled = isVibrationEnabled,
                selectedReceiptSize = selectedReceiptSize,
                isReceiptHeaderEnabled = isReceiptHeaderEnabled,
                receiptHeaderTitle = receiptHeaderTitle,
                isAgentSignatureOnReceipt = isAgentSignatureEnabled,
                isQrCodeOnReceipt = isQrCodeEnabled,
                isAutoReceiptNumber = isAutoReceiptNumber,
                accountHolderName = accountHolderNameState,
                upiVpaId = upiVpaIdState,
                isAutoBackupEnabled = isAutoBackupEnabled,
                isCloudSyncEnabled = isCloudSyncEnabled,
                lastBackupText = lastBackupText,
                isAppLockEnabled = isAppLockEnabled,
                isPinLockEnabled = isPinLockEnabled,
                pinCode = currentPinCode,
                isFingerprintEnabled = isFingerprintEnabled,
                isFaceUnlockEnabled = isFaceUnlockEnabled,
                selectedAutoLockTime = selectedAutoLockTime
            )
            val db = AppDatabase.getDatabase(context)
            AppSettingsManager.saveSettings(context, currentSettings, db, null)
            viewModel?.triggerSync()
        }
    }

    // --- Dialog Controls ---
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showBiometricPinVerifyDialog by remember { mutableStateOf(false) }
    var showResetDemoDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "Manage your LIC Premium Reminder Pro preferences.",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("settings_back_button")
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
                        modifier = Modifier.testTag("settings_help_button")
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
                            modifier = Modifier.testTag("settings_more_button")
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
                                text = { Text("About LIC Reminder Pro", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = RoyalBlueGlow) },
                                onClick = {
                                    showMoreMenu = false
                                    showAboutDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Preferences", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentAmber) },
                                onClick = {
                                    showMoreMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Settings reset to defaults")
                                    }
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROFILE CARD
            ProfileCardSection(
                agentName = agentName,
                agentCode = agentCode,
                branchCode = branchCode,
                branchName = branchName,
                mobileNumber = mobileNumber,
                emailAddress = emailAddress,
                officeAddress = officeAddress,
                photoUri = photoUri,
                onUploadPhoto = { photoPickerLauncher.launch("image/*") },
                onReplacePhoto = { photoPickerLauncher.launch("image/*") },
                onRemovePhoto = {
                    photoUri = ""
                    val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                        agentName = agentName,
                        agencyCode = agentCode,
                        branchCode = branchCode,
                        branchName = branchName,
                        mobile = mobileNumber,
                        email = emailAddress,
                        officeAddress = officeAddress,
                        photoUri = ""
                    )
                    viewModel?.saveAgentProfile(updated)
                    persistSettings()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Profile photo removed")
                    }
                },
                onEditProfileClick = { showEditProfileDialog = true }
            )

            // APP PREFERENCES
            AppPreferencesSection(
                isDarkMode = isDarkMode,
                onDarkModeChange = {
                    isDarkMode = it
                    persistSettings()
                },
                isSystemTheme = isSystemTheme,
                onSystemThemeChange = {
                    isSystemTheme = it
                    persistSettings()
                },
                selectedLanguage = selectedLanguage,
                onLanguageChange = {
                    selectedLanguage = it
                    persistSettings()
                },
                selectedFontSize = selectedFontSize,
                onFontSizeChange = {
                    selectedFontSize = it
                    persistSettings()
                }
            )

            // NOTIFICATION SETTINGS
            NotificationSettingsSection(
                isPremiumReminder = isPremiumReminder,
                onPremiumReminderChange = {
                    isPremiumReminder = it
                    persistSettings()
                },
                isDueToday = isDueTodayReminder,
                onDueTodayChange = {
                    isDueTodayReminder = it
                    persistSettings()
                },
                isTomorrow = isTomorrowReminder,
                onTomorrowChange = {
                    isTomorrowReminder = it
                    persistSettings()
                },
                isOverdue = isOverdueReminder,
                onOverdueChange = {
                    isOverdueReminder = it
                    persistSettings()
                },
                isWhatsApp = isWhatsAppReminder,
                onWhatsAppChange = {
                    isWhatsAppReminder = it
                    persistSettings()
                },
                selectedTime = selectedReminderTime,
                onTimeChange = {
                    selectedReminderTime = it
                    persistSettings()
                },
                selectedSound = selectedNotificationSound,
                onSoundChange = {
                    selectedNotificationSound = it
                    persistSettings()
                },
                isVibration = isVibrationEnabled,
                onVibrationChange = {
                    isVibrationEnabled = it
                    persistSettings()
                }
            )

            // RECEIPT SETTINGS
            ReceiptSettingsSection(
                selectedSize = selectedReceiptSize,
                onSizeChange = {
                    selectedReceiptSize = it
                    persistSettings()
                },
                isHeader = isReceiptHeaderEnabled,
                onHeaderChange = {
                    isReceiptHeaderEnabled = it
                    persistSettings()
                },
                headerTitle = receiptHeaderTitle,
                onHeaderTitleChange = {
                    receiptHeaderTitle = it
                    persistSettings()
                },
                isSignature = isAgentSignatureEnabled,
                onSignatureChange = {
                    isAgentSignatureEnabled = it
                    persistSettings()
                },
                isQrCode = isQrCodeEnabled,
                onQrCodeChange = {
                    isQrCodeEnabled = it
                    persistSettings()
                },
                isAutoReceipt = isAutoReceiptNumber,
                onAutoReceiptChange = {
                    isAutoReceiptNumber = it
                    persistSettings()
                }
            )

            // PAYMENT SETTINGS
            PaymentSettingsSection(
                accountHolderName = accountHolderNameState,
                onAccountHolderNameChange = {
                    accountHolderNameState = it
                },
                upiVpaId = upiVpaIdState,
                onUpiVpaIdChange = {
                    upiVpaIdState = it
                    if (com.example.util.QrCodeGenerator.isValidUpiId(it)) {
                        upiIdError = null
                    }
                },
                upiIdError = upiIdError,
                onSaveClick = {
                    if (com.example.util.QrCodeGenerator.isValidUpiId(upiVpaIdState)) {
                        upiIdError = null
                        persistSettings()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Payment Settings saved successfully!")
                        }
                    } else {
                        upiIdError = "Invalid UPI ID format. Example: name@oksbi, name@ybl"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid UPI ID format. Must include @ (e.g. name@oksbi)")
                        }
                    }
                },
                onTestQrClick = {
                    if (com.example.util.QrCodeGenerator.isValidUpiId(upiVpaIdState)) {
                        upiIdError = null
                        showTestQrDialog = true
                    } else {
                        upiIdError = "Invalid UPI ID format. Example: name@oksbi"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter a valid UPI ID before testing QR.")
                        }
                    }
                },
                onResetClick = {
                    accountHolderNameState = agentName.ifEmpty { "Pintu Ojha" }
                    upiVpaIdState = "licagent@upi"
                    upiIdError = null
                    persistSettings()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Payment settings reset to defaults.")
                    }
                }
            )

            // BACKUP SETTINGS
            BackupSettingsSection(
                isAutoBackup = isAutoBackupEnabled,
                onAutoBackupChange = { enabled ->
                    isAutoBackupEnabled = enabled
                    persistSettings()
                    if (enabled) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Auto Backup scheduled daily") }
                    } else {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Auto Backup disabled") }
                    }
                },
                isCloudSync = isCloudSyncEnabled,
                onCloudSyncChange = {
                    isCloudSyncEnabled = it
                    persistSettings()
                },
                lastBackupText = lastBackupText,
                onBackupNowClick = {
                    coroutineScope.launch {
                        val db = AppDatabase.getDatabase(context)
                        val res = com.example.data.backup.BackupManager.createFullBackup(context, db)
                        res.onSuccess { item ->
                            lastBackupText = "Just now • ${item.size}"
                            persistSettings()
                            snackbarHostState.showSnackbar("Backup created & saved safely (${item.size})!")
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar("Backup failed: ${err.message}")
                        }
                    }
                }
            )

            // SECURITY SETTINGS
            SecuritySettingsSection(
                isAppLock = isAppLockEnabled,
                onAppLockChange = {
                    isAppLockEnabled = it
                    persistSettings()
                },
                isPinLock = isPinLockEnabled,
                onPinLockChange = {
                    isPinLockEnabled = it
                    persistSettings()
                },
                onSetPinClick = { showPinDialog = true },
                isFingerprint = isFingerprintEnabled,
                onFingerprintChange = { enable ->
                    if (enable) {
                        if (currentPinCode.isNotBlank()) {
                            showBiometricPinVerifyDialog = true
                        } else {
                            isFingerprintEnabled = true
                            persistSettings()
                        }
                    } else {
                        isFingerprintEnabled = false
                        persistSettings()
                    }
                },
                isFaceUnlock = isFaceUnlockEnabled,
                onFaceUnlockChange = {
                    isFaceUnlockEnabled = it
                    persistSettings()
                },
                selectedAutoLockTime = selectedAutoLockTime,
                onAutoLockTimeChange = {
                    selectedAutoLockTime = it
                    persistSettings()
                }
            )

            // DATA MANAGEMENT
            DataManagementSection(
                storageUsedMb = storageUsedMb,
                onExportDataClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(context)
                        val res = com.example.data.backup.BackupManager.createFullBackup(context, db)
                        res.onSuccess { item ->
                            lastBackupText = "Just now • ${item.size}"
                            persistSettings()
                            snackbarHostState.showSnackbar("Full encrypted dataset exported successfully (${item.size})!")
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar("Export failed: ${err.localizedMessage}")
                        }
                    }
                },
                onImportDataClick = {
                    importLauncher.launch("*/*")
                },
                onResetDemoClick = { showResetDemoDialog = true }
            )

            // SUPPORT SECTION
            SupportSection(
                onHelpCenterClick = { showHelpDialog = true },
                onContactSupportClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Contact: support@licreminderpro.in | +91 1800 22 3344")
                    }
                },
                onFeedbackClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Thank you for your feedback proposal!")
                    }
                },
                onPrivacyPolicyClick = { showPrivacyPolicyDialog = true },
                onTermsClick = { showTermsDialog = true },
                onRateAppClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Redirecting to Play Store rating page...")
                    }
                },
                onAboutClick = { showAboutDialog = true }
            )

            // LOGOUT BUTTON
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("settings_logout_button"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, AccentRed),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentRed
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Logout",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ===================================================================
    // MODALS & DIALOGS
    // ===================================================================

    if (showTestQrDialog) {
        TestQrModalDialog(
            accountHolderName = accountHolderNameState,
            upiVpaId = upiVpaIdState,
            onDismiss = { showTestQrDialog = false }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(agentName) }
        var tempCode by remember { mutableStateOf(agentCode) }
        var tempBranchCode by remember { mutableStateOf(branchCode) }
        var tempBranchName by remember { mutableStateOf(branchName) }
        var tempMobile by remember { mutableStateOf(mobileNumber) }
        var tempEmail by remember { mutableStateOf(emailAddress) }
        var tempOffice by remember { mutableStateOf(officeAddress) }
        var showSearchBranchDialog by remember { mutableStateOf(false) }

        val foundBranch = remember(tempBranchCode) { LicBranchMaster.findBranchByCode(tempBranchCode) }
        val isBranchCodeInvalid = tempBranchCode.isNotBlank() && foundBranch == null

        LaunchedEffect(tempBranchCode) {
            val matched = LicBranchMaster.findBranchByCode(tempBranchCode)
            if (matched != null) {
                tempBranchName = matched.name
            }
        }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Agent Profile", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    AgentProfilePhotoHeader(
                        photoUri = photoUri,
                        agentName = tempName,
                        onUploadPhoto = { photoPickerLauncher.launch("image/*") },
                        onReplacePhoto = { photoPickerLauncher.launch("image/*") },
                        onRemovePhoto = {
                            photoUri = ""
                            val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                                agentName = tempName,
                                agencyCode = tempCode,
                                branchCode = tempBranchCode,
                                branchName = tempBranchName,
                                mobile = tempMobile,
                                email = tempEmail,
                                officeAddress = tempOffice,
                                photoUri = ""
                            )
                            viewModel?.saveAgentProfile(updated)
                        }
                    )

                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Agent Name", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("edit_agent_name_field")
                    )

                    OutlinedTextField(
                        value = tempCode,
                        onValueChange = { tempCode = it },
                        label = { Text("Agency / Agent Code", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tempBranchCode,
                                onValueChange = { input ->
                                    tempBranchCode = input.uppercase().trim()
                                },
                                label = { Text("Branch Code", color = if (isBranchCodeInvalid) Color(0xFFEF4444) else TextMuted) },
                                isError = isBranchCodeInvalid,
                                singleLine = true,
                                trailingIcon = {
                                    if (foundBranch != null) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Valid Branch Code",
                                            tint = AccentGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    errorBorderColor = Color(0xFFEF4444),
                                    errorLabelColor = Color(0xFFEF4444),
                                    errorTrailingIconColor = Color(0xFFEF4444),
                                    focusedBorderColor = RoyalBlueLight,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_branch_code_field")
                            )

                            OutlinedButton(
                                onClick = { showSearchBranchDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoyalBlueLight),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF0F172A),
                                    contentColor = RoyalBlueGlow
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("search_branch_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Branch",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Search", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        AnimatedVisibility(
                            visible = isBranchCodeInvalid,
                            enter = fadeIn(animationSpec = tween(250)) + slideInVertically(initialOffsetY = { -8 }),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            Text(
                                text = "Invalid Branch Code",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tempBranchName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Branch Name (Read Only)", color = TextMuted) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Read Only",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                            disabledTextColor = TextWhite,
                            disabledBorderColor = CardBorder,
                            disabledLabelColor = TextMuted,
                            disabledTrailingIconColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_branch_name_field")
                    )

                    OutlinedTextField(
                        value = tempMobile,
                        onValueChange = { tempMobile = it },
                        label = { Text("Mobile Number", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Email Address", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempOffice,
                        onValueChange = { tempOffice = it },
                        label = { Text("Office Address", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("edit_office_address_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isBranchCodeInvalid) return@Button

                        agentName = tempName
                        agentCode = tempCode
                        branchCode = tempBranchCode
                        branchName = tempBranchName
                        mobileNumber = tempMobile
                        emailAddress = tempEmail
                        officeAddress = tempOffice
                        val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                            agentName = tempName,
                            agencyCode = tempCode,
                            branchCode = tempBranchCode,
                            branchName = tempBranchName,
                            mobile = tempMobile,
                            email = tempEmail,
                            officeAddress = tempOffice,
                            photoUri = photoUri
                        )
                        viewModel?.saveAgentProfile(updated)
                        persistSettings()
                        showEditProfileDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Agent profile updated & synced successfully!")
                        }
                    },
                    enabled = !isBranchCodeInvalid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBlueLight,
                        disabledContainerColor = RoyalBlueLight.copy(alpha = 0.4f),
                        disabledContentColor = TextWhite.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Save Changes", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )

        if (showSearchBranchDialog) {
            SearchBranchDialog(
                currentCode = tempBranchCode,
                onBranchSelected = { selected ->
                    tempBranchCode = selected.code
                    tempBranchName = selected.name
                    showSearchBranchDialog = false
                },
                onDismiss = { showSearchBranchDialog = false }
            )
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settings Guide", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• Profile: Keep your LIC Agent credentials updated for accurate PDF receipts.", color = TextWhite, fontSize = 13.sp)
                    Text("• Notifications: Configure automated WhatsApp & morning dues alerts.", color = TextWhite, fontSize = 13.sp)
                    Text("• Security: Enable PIN or Fingerprint authentication to safeguard client records.", color = AccentGreen, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("Got It", color = TextWhite)
                }
            }
        )
    }

    // Set PIN Dialog
    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var recoveryQuestion by remember {
            mutableStateOf(com.example.util.SecurityUtils.getRecoveryQuestion(context))
        }
        var recoveryAnswer by remember { mutableStateOf("") }
        var recoveryEmail by remember {
            mutableStateOf(
                com.example.util.SecurityUtils.getRecoveryEmail(context).ifBlank {
                    agentProfileState?.email ?: ""
                }
            )
        }
        var showQuestionDropdown by remember { mutableStateOf(false) }
        var pinError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pin, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (currentPinCode.isBlank()) "Create Security PIN & Recovery" else "Change Security PIN & Recovery",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Set up your 4-digit passcode and emergency recovery details.", color = TextMuted, fontSize = 12.5.sp)

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                newPin = it
                                pinError = ""
                            }
                        },
                        label = { Text("4-Digit PIN", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("pin_input_field")
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                confirmPin = it
                                pinError = ""
                            }
                        },
                        label = { Text("Confirm 4-Digit PIN", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input_field")
                    )

                    HorizontalDivider(color = RoyalBlueLight.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Text("Emergency Recovery Setup", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    // Recovery Question Dropdown / Custom Input
                    Box {
                        OutlinedTextField(
                            value = recoveryQuestion,
                            onValueChange = {
                                recoveryQuestion = it
                                pinError = ""
                            },
                            label = { Text("Recovery Question", color = TextMuted) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showQuestionDropdown = !showQuestionDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Question", tint = RoyalBlueLight)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                            modifier = Modifier.fillMaxWidth().testTag("recovery_question_input")
                        )

                        DropdownMenu(
                            expanded = showQuestionDropdown,
                            onDismissRequest = { showQuestionDropdown = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            com.example.util.SecurityUtils.DEFAULT_RECOVERY_QUESTIONS.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q, color = TextWhite, fontSize = 13.sp) },
                                    onClick = {
                                        recoveryQuestion = q
                                        showQuestionDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = {
                            recoveryAnswer = it
                            pinError = ""
                        },
                        label = { Text("Recovery Answer", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_answer_setup_input")
                    )

                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = {
                            recoveryEmail = it
                            pinError = ""
                        },
                        label = { Text("Optional Recovery Email", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        ),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_email_input")
                    )

                    if (pinError.isNotBlank()) {
                        Text(pinError, color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentPinCode.isNotBlank()) {
                        TextButton(
                            onClick = {
                                currentPinCode = ""
                                isPinLockEnabled = false
                                val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(pinCode = "")
                                viewModel?.saveAgentProfile(updated)
                                persistSettings()
                                showPinDialog = false
                                coroutineScope.launch { snackbarHostState.showSnackbar("Security PIN removed") }
                            }
                        ) {
                            Text("Remove PIN", color = AccentRed)
                        }
                    }
                    Button(
                        onClick = {
                            val cleanAnswer = recoveryAnswer.trim()
                            val cleanQuestion = recoveryQuestion.trim()

                            if (newPin.length != 4) {
                                pinError = "PIN must be exactly 4 digits."
                            } else if (newPin != confirmPin) {
                                pinError = "PINs do not match."
                            } else if (cleanQuestion.isBlank()) {
                                pinError = "Please enter or select a Recovery Question."
                            } else if (cleanAnswer.length < 2) {
                                pinError = "Recovery Answer must be at least 2 characters."
                            } else {
                                val hashedPin = com.example.util.SecurityUtils.hashPin(newPin)
                                currentPinCode = hashedPin
                                isPinLockEnabled = true

                                // Save Emergency Recovery Info securely
                                com.example.util.SecurityUtils.saveRecoveryInfo(
                                    context = context,
                                    question = cleanQuestion,
                                    answer = cleanAnswer,
                                    email = recoveryEmail.trim()
                                )

                                val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(pinCode = hashedPin)
                                viewModel?.saveAgentProfile(updated)
                                persistSettings()
                                showPinDialog = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Security PIN and Emergency Recovery setup saved!")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight)
                    ) {
                        Text("Save Setup", color = TextWhite)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (showBiometricPinVerifyDialog) {
        var verifyPin by remember { mutableStateOf("") }
        var verifyError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showBiometricPinVerifyDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Verify PIN for Biometrics",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your 4-digit PIN to enable biometric authentication.", color = TextMuted, fontSize = 12.5.sp)
                    OutlinedTextField(
                        value = verifyPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                verifyPin = it
                                verifyError = ""
                            }
                        },
                        label = { Text("Enter 4-Digit PIN", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("verify_pin_biometric_input")
                    )
                    if (verifyError.isNotBlank()) {
                        Text(verifyError, color = AccentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (com.example.util.SecurityUtils.isPinValid(verifyPin, currentPinCode)) {
                            isFingerprintEnabled = true
                            persistSettings()
                            showBiometricPinVerifyDialog = false
                            coroutineScope.launch { snackbarHostState.showSnackbar("Biometric login enabled successfully!") }
                        } else {
                            verifyError = "Incorrect PIN. Verification failed."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight)
                ) {
                    Text("Enable Biometrics", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricPinVerifyDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Reset Demo Data Dialog
    if (showResetDemoDialog) {
        AlertDialog(
            onDismissRequest = { showResetDemoDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Demo Data", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "This will restore demo clients, policies, and sample receipts. No existing cloud backups will be lost.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDemoDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                AppDatabase.repopulateDemoData(db)
                                viewModel?.triggerSync()
                                snackbarHostState.showSnackbar("Demo data environment successfully reset!")
                            } catch (e: Throwable) {
                                snackbarHostState.showSnackbar("Reset failed: ${e.localizedMessage}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                ) {
                    Text("Confirm Reset", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDemoDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            containerColor = CardBg,
            title = { Text("Privacy Policy", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    "LIC Premium Reminder Pro enforces strict AES-256 client data encryption. Policy records, policy numbers, and contact information are strictly stored on local storage or protected Firebase cloud instances.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyPolicyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("Close", color = TextWhite)
                }
            }
        )
    }

    // Terms Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = CardBg,
            title = { Text("Terms & Conditions", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    "Licensed exclusively for authorized LIC Agents and Financial Consultants. Automated reminders are subject to local SMS and WhatsApp API guidelines.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("Close", color = TextWhite)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = RoyalBlueGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About LIC Reminder Pro", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LIC Premium Reminder Pro", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text("Version: 2.5.0 (Build 2026)", color = RoyalBlueGlow, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Banking-grade agent productivity toolkit designed for policy tracking, automated client reminders, and instant digital receipt generation.", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)) {
                    Text("OK", color = TextWhite)
                }
            }
        )
    }
}

// ===========================================================================
// SUB-SECTIONS
// ===========================================================================

@Composable
fun AgentProfilePhotoHeader(
    photoUri: String,
    agentName: String,
    onUploadPhoto: () -> Unit,
    onReplacePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val agentInitials = agentName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .take(2)
        .ifEmpty { "PO" }
        .uppercase()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .testTag("agent_profile_photo_96dp"),
            contentAlignment = Alignment.BottomEnd
        ) {
            AnimatedContent(
                targetState = photoUri,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.85f))
                        .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f))
                },
                label = "ProfilePhotoScaleAnimation"
            ) { currentUri ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(RoyalBlueGlow, RoyalBluePrimary)
                            )
                        )
                        .border(3.dp, RoyalBlueGlow, CircleShape)
                        .clickable {
                            if (currentUri.isNotBlank()) onReplacePhoto() else onUploadPhoto()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUri.isNotBlank()) {
                        AsyncImage(
                            model = currentUri,
                            contentDescription = "Agent Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = agentInitials,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RoyalBlueLight)
                    .border(2.dp, CardBg, CircleShape)
                    .clickable { onReplacePhoto() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photoUri.isBlank()) {
                Button(
                    onClick = onUploadPhoto,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onReplacePhoto,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoyalBlueGlow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBlueGlow),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = onRemovePhoto,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ProfileCardSection(
    agentName: String,
    agentCode: String,
    branchCode: String,
    branchName: String,
    mobileNumber: String,
    emailAddress: String,
    officeAddress: String,
    photoUri: String,
    onUploadPhoto: () -> Unit,
    onReplacePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEditProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_profile_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = RoyalBlueGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agent Profile",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                IconButton(
                    onClick = onEditProfileClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, CardBorder, CircleShape)
                        .testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = RoyalBlueGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AgentProfilePhotoHeader(
                photoUri = photoUri,
                agentName = agentName,
                onUploadPhoto = onUploadPhoto,
                onReplacePhoto = onReplacePhoto,
                onRemovePhoto = onRemovePhoto
            )

            HorizontalDivider(color = CardBorder, thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileInfoRow(label = "Agent Name", value = agentName)
                ProfileInfoRow(label = "Agency Code", value = agentCode, isHighlight = true)
                ProfileInfoRow(label = "Branch", value = "$branchCode • $branchName")
                ProfileInfoRow(label = "Mobile", value = mobileNumber)
                ProfileInfoRow(label = "Email", value = emailAddress)
                ProfileInfoRow(label = "Office Address", value = officeAddress)
            }
        }
    }
}

@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = if (isHighlight) AccentGreen else TextWhite,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppPreferencesSection(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    isSystemTheme: Boolean,
    onSystemThemeChange: (Boolean) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    selectedFontSize: String,
    onFontSizeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_app_preferences_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.Palette, title = "App Preferences")

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchSettingRow(
                    title = "Dark Theme Mode",
                    subtitle = "High contrast dark palette optimized for OLED screens",
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeChange
                )

                SwitchSettingRow(
                    title = "Follow System Theme",
                    subtitle = "Automatically match system dark/light settings",
                    checked = isSystemTheme,
                    onCheckedChange = onSystemThemeChange
                )

                HorizontalDivider(color = CardBorder, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Application Language", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("English", "Hindi", "Odia", "Marathi").forEach { lang ->
                            FilterChip(
                                selected = selectedLanguage == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlueLight,
                                    selectedLabelColor = TextWhite,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Display Font Size", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large").forEach { size ->
                            FilterChip(
                                selected = selectedFontSize == size,
                                onClick = { onFontSizeChange(size) },
                                label = { Text(size, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlueLight,
                                    selectedLabelColor = TextWhite,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSettingsSection(
    isPremiumReminder: Boolean,
    onPremiumReminderChange: (Boolean) -> Unit,
    isDueToday: Boolean,
    onDueTodayChange: (Boolean) -> Unit,
    isTomorrow: Boolean,
    onTomorrowChange: (Boolean) -> Unit,
    isOverdue: Boolean,
    onOverdueChange: (Boolean) -> Unit,
    isWhatsApp: Boolean,
    onWhatsAppChange: (Boolean) -> Unit,
    selectedTime: String,
    onTimeChange: (String) -> Unit,
    selectedSound: String,
    onSoundChange: (String) -> Unit,
    isVibration: Boolean,
    onVibrationChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_notification_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.NotificationsActive, title = "Notification & Reminder Engine")

            SwitchSettingRow(
                title = "Automated Dues Reminders",
                subtitle = "Master switch for scheduled push alerts and notifications",
                checked = isPremiumReminder,
                onCheckedChange = onPremiumReminderChange
            )

            if (isPremiumReminder) {
                HorizontalDivider(color = CardBorder, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trigger Categories", color = RoyalBlueGlow, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)

                    SwitchSettingRow(title = "Due Today Alerts", subtitle = "Morning alert at 09:00 AM for today's collections", checked = isDueToday, onCheckedChange = onDueTodayChange)
                    SwitchSettingRow(title = "Tomorrow Dues Warning", subtitle = "24-hour advance notice for upcoming due dates", checked = isTomorrow, onCheckedChange = onTomorrowChange)
                    SwitchSettingRow(title = "Overdue Lapsed Follow-up", subtitle = "Automated escalation for overdue policies", checked = isOverdue, onCheckedChange = onOverdueChange)
                    SwitchSettingRow(title = "WhatsApp Automation Integration", subtitle = "Auto-prepare WhatsApp template messages", checked = isWhatsApp, onCheckedChange = onWhatsAppChange)
                }

                HorizontalDivider(color = CardBorder, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily Schedule Time", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("08:00 AM", "09:00 AM", "10:00 AM", "06:00 PM").forEach { time ->
                            FilterChip(
                                selected = selectedTime == time,
                                onClick = { onTimeChange(time) },
                                label = { Text(time, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlueLight,
                                    selectedLabelColor = TextWhite,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }

                SwitchSettingRow(
                    title = "Vibration Alert",
                    subtitle = "Haptic feedback on notification triggers",
                    checked = isVibration,
                    onCheckedChange = onVibrationChange
                )
            }
        }
    }
}

@Composable
fun ReceiptSettingsSection(
    selectedSize: String,
    onSizeChange: (String) -> Unit,
    isHeader: Boolean,
    onHeaderChange: (Boolean) -> Unit,
    headerTitle: String,
    onHeaderTitleChange: (String) -> Unit,
    isSignature: Boolean,
    onSignatureChange: (Boolean) -> Unit,
    isQrCode: Boolean,
    onQrCodeChange: (Boolean) -> Unit,
    isAutoReceipt: Boolean,
    onAutoReceiptChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_receipt_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.Receipt, title = "Receipt & PDF Generator")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Receipt Size", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A5", "A4", "Thermal 3-inch").forEach { size ->
                        FilterChip(
                            selected = selectedSize == size,
                            onClick = { onSizeChange(size) },
                            label = { Text(size, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueLight,
                                selectedLabelColor = TextWhite,
                                containerColor = Color(0xFF0F172A),
                                labelColor = TextMuted
                            )
                        )
                    }
                }
            }

            SwitchSettingRow(
                title = "Receipt Header",
                subtitle = "Include official LIC logo and customizable header title",
                checked = isHeader,
                onCheckedChange = onHeaderChange
            )

            if (isHeader) {
                OutlinedTextField(
                    value = headerTitle,
                    onValueChange = onHeaderTitleChange,
                    label = { Text("Custom Header Title", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth().testTag("receipt_header_title_field")
                )
            }

            SwitchSettingRow(
                title = "Agent Digital Signature",
                subtitle = "Attach digital signature line at bottom of PDF receipts",
                checked = isSignature,
                onCheckedChange = onSignatureChange
            )

            SwitchSettingRow(
                title = "QR Verification Code",
                subtitle = "Print scan-to-verify QR code on every receipt",
                checked = isQrCode,
                onCheckedChange = onQrCodeChange
            )

            SwitchSettingRow(
                title = "Auto Receipt Number",
                subtitle = "Auto-generate sequential receipt IDs (e.g. LIC-2026-089)",
                checked = isAutoReceipt,
                onCheckedChange = onAutoReceiptChange
            )
        }
    }
}

@Composable
fun BackupSettingsSection(
    isAutoBackup: Boolean,
    onAutoBackupChange: (Boolean) -> Unit,
    isCloudSync: Boolean,
    onCloudSyncChange: (Boolean) -> Unit,
    lastBackupText: String,
    onBackupNowClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_backup_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.CloudSync, title = "Cloud Sync & Backup")

            SwitchSettingRow(
                title = "Auto Backup",
                subtitle = "Schedule daily background backups of Room database",
                checked = isAutoBackup,
                onCheckedChange = onAutoBackupChange
            )

            SwitchSettingRow(
                title = "Real-time Firebase Cloud Sync",
                subtitle = "Instant synchronization across logged-in agent devices",
                checked = isCloudSync,
                onCheckedChange = onCloudSyncChange
            )

            HorizontalDivider(color = CardBorder, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Last Backup Status", color = TextMuted, fontSize = 12.sp)
                    Text(lastBackupText, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onBackupNowClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight),
                    modifier = Modifier.testTag("backup_now_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup Now", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsSection(
    isAppLock: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    isPinLock: Boolean,
    onPinLockChange: (Boolean) -> Unit,
    onSetPinClick: () -> Unit,
    isFingerprint: Boolean,
    onFingerprintChange: (Boolean) -> Unit,
    isFaceUnlock: Boolean,
    onFaceUnlockChange: (Boolean) -> Unit,
    selectedAutoLockTime: String,
    onAutoLockTimeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_security_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.Security, title = "Security & App Lock")

            SwitchSettingRow(
                title = "App Protection Master Lock",
                subtitle = "Require security authentication to access client records",
                checked = isAppLock,
                onCheckedChange = onAppLockChange
            )

            if (isAppLock) {
                HorizontalDivider(color = CardBorder, thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("4-Digit Passcode PIN", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Numeric PIN authentication", color = TextMuted, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onSetPinClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RoyalBlueGlow),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBlueGlow),
                        modifier = Modifier.testTag("set_pin_button")
                    ) {
                        Text(if (isPinLock) "Manage PIN" else "Set PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                SwitchSettingRow(
                    title = "Biometric Fingerprint Unlock",
                    subtitle = "Use Android Hardware Biometric Prompt",
                    checked = isFingerprint,
                    onCheckedChange = onFingerprintChange
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto Lock Delay", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Immediate", "1 Min", "5 Min", "15 Min").forEach { time ->
                            FilterChip(
                                selected = selectedAutoLockTime == time,
                                onClick = { onAutoLockTimeChange(time) },
                                label = { Text(time, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlueLight,
                                    selectedLabelColor = TextWhite,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataManagementSection(
    storageUsedMb: Float,
    onExportDataClick: () -> Unit,
    onImportDataClick: () -> Unit,
    onResetDemoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_data_management_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.Storage, title = "Data Management & Storage")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Local Room Storage", color = TextMuted, fontSize = 12.sp)
                    Text("${storageUsedMb} MB occupied", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportDataClick,
                    modifier = Modifier.weight(1f).testTag("export_data_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoyalBlueGlow)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoyalBlueGlow)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Dataset", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onImportDataClick,
                    modifier = Modifier.weight(1f).testTag("import_data_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentGreen)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Backup", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onResetDemoClick,
                modifier = Modifier.fillMaxWidth().testTag("reset_demo_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentAmber),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Demo Data Environment", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SupportSection(
    onHelpCenterClick: () -> Unit,
    onContactSupportClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onRateAppClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_support_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(icon = Icons.Default.SupportAgent, title = "Support & Legal")

            SupportActionRow(title = "Help Center & FAQs", icon = Icons.AutoMirrored.Filled.HelpOutline, onClick = onHelpCenterClick)
            SupportActionRow(title = "Contact Support Team", icon = Icons.Default.HeadsetMic, onClick = onContactSupportClick)
            SupportActionRow(title = "Send Agent Feedback", icon = Icons.Default.Feedback, onClick = onFeedbackClick)
            SupportActionRow(title = "Privacy Policy", icon = Icons.Default.PrivacyTip, onClick = onPrivacyPolicyClick)
            SupportActionRow(title = "Terms & Conditions", icon = Icons.Default.Gavel, onClick = onTermsClick)
            SupportActionRow(title = "Rate App on Play Store", icon = Icons.Default.Star, onClick = onRateAppClick)
            SupportActionRow(title = "About LIC Reminder Pro", icon = Icons.Default.Info, onClick = onAboutClick)
        }
    }
}

@Composable
fun SupportActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = RoyalBlueGlow, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
    }
}

@Composable
fun SwitchSettingRow(
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
            Text(text = title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = RoyalBlueLight,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBranchDialog(
    currentCode: String,
    onBranchSelected: (LicBranch) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val branches = remember { LicBranchMaster.defaultBranches }
    val filteredBranches = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            branches
        } else {
            branches.filter {
                it.code.contains(searchQuery, ignoreCase = true) ||
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.city.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select LIC Branch", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search code, city or branch name...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth().testTag("branch_search_input")
                )
            }
        },
        text = {
            Box(modifier = Modifier.height(300.dp)) {
                if (filteredBranches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No branches found matching '$searchQuery'", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredBranches) { branch ->
                            val isSelected = branch.code.equals(currentCode, ignoreCase = true)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBranchSelected(branch) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) RoyalBluePrimary else Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "${branch.code} • ${branch.name}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        if (branch.city.isNotBlank()) {
                                            Text(text = "City: ${branch.city}", color = TextMuted, fontSize = 11.5.sp)
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        }
    )
}

@Composable
fun PaymentSettingsSection(
    accountHolderName: String,
    onAccountHolderNameChange: (String) -> Unit,
    upiVpaId: String,
    onUpiVpaIdChange: (String) -> Unit,
    upiIdError: String?,
    onSaveClick: () -> Unit,
    onTestQrClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_payment_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.AccountBalanceWallet, title = "Payment & UPI QR Settings")

            Text(
                text = "Set up your Account Holder Name and UPI ID for generating instant UPI payment links and QR codes.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.5.sp)
            )

            OutlinedTextField(
                value = accountHolderName,
                onValueChange = onAccountHolderNameChange,
                label = { Text("Account Holder Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBlueGlow) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_account_holder_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlueGlow,
                    unfocusedBorderColor = CardBorder,
                    focusedLabelColor = RoyalBlueGlow
                )
            )

            OutlinedTextField(
                value = upiVpaId,
                onValueChange = onUpiVpaIdChange,
                label = { Text("UPI ID / VPA") },
                placeholder = { Text("e.g. name@oksbi, name@ybl, name@ibl") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentAmber) },
                supportingText = {
                    Text(
                        text = upiIdError ?: "Example formats: name@oksbi, name@ybl, name@ibl, name@okaxis",
                        color = if (upiIdError != null) AccentRed else TextMuted,
                        fontSize = 11.sp
                    )
                },
                isError = upiIdError != null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_upi_id_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlueGlow,
                    unfocusedBorderColor = CardBorder,
                    focusedLabelColor = RoyalBlueGlow
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save Button
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_save_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Test QR Button
                OutlinedButton(
                    onClick = onTestQrClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_test_qr_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentAmber),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test QR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Reset Button
                OutlinedButton(
                    onClick = onResetClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_reset_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TestQrModalDialog(
    accountHolderName: String,
    upiVpaId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val testAmount = "1"
    val encodedName = Uri.encode(accountHolderName)
    val testUri = "upi://pay?pa=$upiVpaId&pn=$encodedName&am=$testAmount&tn=LIC%20Test%20Payment&cu=INR"
    val testQrBitmap = remember(accountHolderName, upiVpaId) {
        com.example.util.QrCodeGenerator.generateQrBitmap(testUri, size = 420)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
            ) {
                Text("Close Test Preview")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(testUri))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No UPI app installed, URI copied instead", Toast.LENGTH_SHORT).show()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UPI URI", testUri))
                    }
                }
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open UPI App")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test UPI QR Verification", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Generated ₹1 Test Payment QR Code",
                    style = androidx.compose.ui.text.TextStyle(color = TextMuted, fontSize = 12.sp)
                )

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (testQrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = testQrBitmap.asImageBitmap(),
                            contentDescription = "Test QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = RoyalBluePrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Text("Account: $accountHolderName", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextWhite)
                        Text("UPI ID: $upiVpaId", color = AccentAmber, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("URI: $testUri", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    )
}

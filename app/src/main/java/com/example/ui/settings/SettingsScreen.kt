package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.local.LicBranch
import com.example.data.local.LicBranchMaster
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.LicViewModel
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
private val AccentOrange = Color(0xFFF97316)
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
    var photoUri by remember { mutableStateOf("") }

    LaunchedEffect(agentProfileState) {
        agentProfileState?.let {
            agentName = it.agentName
            agentCode = it.agencyCode
            branchCode = it.branchCode
            branchName = it.branchName
            mobileNumber = it.mobile
            emailAddress = it.email
            photoUri = it.photoUri
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val selectedUriStr = it.toString()
            photoUri = selectedUriStr
            val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                agentName = agentName,
                agencyCode = agentCode,
                branchName = branchName,
                mobile = mobileNumber,
                email = emailAddress,
                photoUri = selectedUriStr
            )
            viewModel?.saveAgentProfile(updated)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Profile photo updated successfully!")
            }
        }
    }

    // --- App Preferences State ---
    var isDarkMode by remember { mutableStateOf(true) }
    var isSystemTheme by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedFontSize by remember { mutableStateOf("Medium") }

    val context = androidx.compose.ui.platform.LocalContext.current

    // --- Notification Settings State ---
    var isPremiumReminder by remember { mutableStateOf(true) }
    var isDueTodayReminder by remember { mutableStateOf(true) }
    var isTomorrowReminder by remember { mutableStateOf(true) }
    var isOverdueReminder by remember { mutableStateOf(true) }
    var isWhatsAppReminder by remember { mutableStateOf(com.example.whatsapp.WhatsAppAutomation.isWhatsAppRemindersEnabled(context)) }
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

    // --- Backup Settings State ---
    var isAutoBackupEnabled by remember { mutableStateOf(true) }
    var isCloudSyncEnabled by remember { mutableStateOf(true) }
    var lastBackupText by remember { mutableStateOf("Today, 05:30 PM • 14.2 MB") }

    // --- Security Settings State ---
    var isAppLockEnabled by remember { mutableStateOf(true) }
    var isPinLockEnabled by remember { mutableStateOf(true) }
    var currentPinCode by remember { mutableStateOf("1234") }
    var isFingerprintEnabled by remember { mutableStateOf(true) }
    var isFaceUnlockEnabled by remember { mutableStateOf(false) } // Placeholder
    var selectedAutoLockTime by remember { mutableStateOf("5 Min") }

    // --- Data Management State ---
    var storageUsedMb by remember { mutableFloatStateOf(34.5f) }

    // --- Dialog Controls ---
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
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
            // ===================================================================
            // PROFILE CARD
            // ===================================================================
            ProfileCardSection(
                agentName = agentName,
                agentCode = agentCode,
                branchCode = branchCode,
                branchName = branchName,
                mobileNumber = mobileNumber,
                emailAddress = emailAddress,
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
                        photoUri = ""
                    )
                    viewModel?.saveAgentProfile(updated)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Profile photo removed")
                    }
                },
                onEditProfileClick = { showEditProfileDialog = true }
            )

            // ===================================================================
            // APP PREFERENCES
            // ===================================================================
            AppPreferencesSection(
                isDarkMode = isDarkMode,
                onDarkModeChange = { isDarkMode = it },
                isSystemTheme = isSystemTheme,
                onSystemThemeChange = { isSystemTheme = it },
                selectedLanguage = selectedLanguage,
                onLanguageChange = { selectedLanguage = it },
                selectedFontSize = selectedFontSize,
                onFontSizeChange = { selectedFontSize = it }
            )

            // ===================================================================
            // NOTIFICATION SETTINGS
            // ===================================================================
            NotificationSettingsSection(
                isPremiumReminder = isPremiumReminder,
                onPremiumReminderChange = { isPremiumReminder = it },
                isDueToday = isDueTodayReminder,
                onDueTodayChange = { isDueTodayReminder = it },
                isTomorrow = isTomorrowReminder,
                onTomorrowChange = { isTomorrowReminder = it },
                isOverdue = isOverdueReminder,
                onOverdueChange = { isOverdueReminder = it },
                isWhatsApp = isWhatsAppReminder,
                onWhatsAppChange = {
                    isWhatsAppReminder = it
                    com.example.whatsapp.WhatsAppAutomation.setWhatsAppRemindersEnabled(context, it)
                },
                selectedTime = selectedReminderTime,
                onTimeChange = { selectedReminderTime = it },
                selectedSound = selectedNotificationSound,
                onSoundChange = { selectedNotificationSound = it },
                isVibration = isVibrationEnabled,
                onVibrationChange = { isVibrationEnabled = it }
            )

            // ===================================================================
            // RECEIPT SETTINGS
            // ===================================================================
            ReceiptSettingsSection(
                selectedSize = selectedReceiptSize,
                onSizeChange = { selectedReceiptSize = it },
                isHeader = isReceiptHeaderEnabled,
                onHeaderChange = { isReceiptHeaderEnabled = it },
                headerTitle = receiptHeaderTitle,
                onHeaderTitleChange = { receiptHeaderTitle = it },
                isSignature = isAgentSignatureEnabled,
                onSignatureChange = { isAgentSignatureEnabled = it },
                isQrCode = isQrCodeEnabled,
                onQrCodeChange = { isQrCodeEnabled = it },
                isAutoReceipt = isAutoReceiptNumber,
                onAutoReceiptChange = { isAutoReceiptNumber = it }
            )

            // ===================================================================
            // BACKUP SETTINGS
            // ===================================================================
            BackupSettingsSection(
                isAutoBackup = isAutoBackupEnabled,
                onAutoBackupChange = { isAutoBackupEnabled = it },
                isCloudSync = isCloudSyncEnabled,
                onCloudSyncChange = { isCloudSyncEnabled = it },
                lastBackupText = lastBackupText,
                onBackupNowClick = {
                    coroutineScope.launch {
                        lastBackupText = "Just now • 14.3 MB"
                        snackbarHostState.showSnackbar("Backup snapshot generated successfully!")
                    }
                }
            )

            // ===================================================================
            // SECURITY
            // ===================================================================
            SecuritySettingsSection(
                isAppLock = isAppLockEnabled,
                onAppLockChange = { isAppLockEnabled = it },
                isPinLock = isPinLockEnabled,
                onPinLockChange = { isPinLockEnabled = it },
                onSetPinClick = { showPinDialog = true },
                isFingerprint = isFingerprintEnabled,
                onFingerprintChange = { isFingerprintEnabled = it },
                isFaceUnlock = isFaceUnlockEnabled,
                onFaceUnlockChange = { isFaceUnlockEnabled = it },
                selectedAutoLockTime = selectedAutoLockTime,
                onAutoLockTimeChange = { selectedAutoLockTime = it }
            )

            // ===================================================================
            // DATA MANAGEMENT
            // ===================================================================
            DataManagementSection(
                storageUsedMb = storageUsedMb,
                onExportDataClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Encrypted dataset exported to Downloads folder")
                    }
                },
                onImportDataClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Import backup file launcher opened")
                    }
                },
                onResetDemoClick = { showResetDemoDialog = true }
            )

            // ===================================================================
            // SUPPORT
            // ===================================================================
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

            // ===================================================================
            // LOGOUT BUTTON
            // ===================================================================
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

            // BOTTOM SAFE AREA SPACING
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ===================================================================
    // MODALS & DIALOGS
    // ===================================================================

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(agentName) }
        var tempCode by remember { mutableStateOf(agentCode) }
        var tempBranchCode by remember { mutableStateOf(branchCode) }
        var tempBranchName by remember { mutableStateOf(branchName) }
        var tempMobile by remember { mutableStateOf(mobileNumber) }
        var tempEmail by remember { mutableStateOf(emailAddress) }
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

                    // --- BRANCH CODE FIELD WITH SEARCH BUTTON & VALIDATION ---
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

                        // Smooth Validation Animation
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

                    // --- BRANCH NAME FIELD (READ ONLY) ---
                    OutlinedTextField(
                        value = tempBranchName,
                        onValueChange = {}, // Read Only - not manually editable
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
                        val updated = (agentProfileState ?: com.example.data.local.AgentProfileEntity()).copy(
                            agentName = tempName,
                            agencyCode = tempCode,
                            branchCode = tempBranchCode,
                            branchName = tempBranchName,
                            mobile = tempMobile,
                            email = tempEmail,
                            photoUri = photoUri
                        )
                        viewModel?.saveAgentProfile(updated)
                        showEditProfileDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Agent profile updated successfully!")
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

        // Search Branch Picker Modal
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
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pin, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set 4-Digit Security PIN", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new 4-digit passcode for instant application unlocking.", color = TextMuted, fontSize = 12.5.sp)
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4) newPin = it },
                        label = { Text("4-Digit PIN", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                        modifier = Modifier.fillMaxWidth().testTag("pin_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            currentPinCode = newPin
                            isPinLockEnabled = true
                            showPinDialog = false
                            coroutineScope.launch { snackbarHostState.showSnackbar("Security PIN updated!") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight)
                ) {
                    Text("Save PIN", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
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
                        coroutineScope.launch { snackbarHostState.showSnackbar("Demo data environment refreshed") }
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
// SUB-SECTIONS (20dp Rounded Cards, 16dp Spacing, Responsive Layouts)
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
        // Large Circular Profile Photo (96dp)
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
                        // Premium Placeholder Avatar
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = agentInitials,
                                style = TextStyle(
                                    color = TextWhite,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            }

            // Camera Icon Overlay at Bottom-Right
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(RoyalBlueLight)
                    .border(2.dp, CardBg, CircleShape)
                    .clickable {
                        if (photoUri.isNotBlank()) onReplacePhoto() else onUploadPhoto()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Change Profile Photo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Photo Action Buttons (Upload Photo, Replace Photo, Remove Photo)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photoUri.isBlank()) {
                Button(
                    onClick = onUploadPhoto,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBlueLight,
                        contentColor = TextWhite
                    ),
                    modifier = Modifier.testTag("upload_profile_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Upload Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onReplacePhoto,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoyalBlueGlow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBlueGlow),
                    modifier = Modifier.testTag("replace_profile_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Replace Photo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onRemovePhoto,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                    modifier = Modifier.testTag("remove_profile_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Remove Photo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 1. PROFILE CARD SECTION
 */
@Composable
fun ProfileCardSection(
    agentName: String,
    agentCode: String,
    branchCode: String = "",
    branchName: String,
    mobileNumber: String,
    emailAddress: String,
    photoUri: String = "",
    onUploadPhoto: () -> Unit = {},
    onReplacePhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onEditProfileClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_section")
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
                Text(
                    text = "AGENT PROFILE",
                    color = RoyalBlueGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = RoyalBluePrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Verified LIC Agent",
                        color = TextWhite,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Agent Profile Photo Section
            AgentProfilePhotoHeader(
                photoUri = photoUri,
                agentName = agentName,
                onUploadPhoto = onUploadPhoto,
                onReplacePhoto = onReplacePhoto,
                onRemovePhoto = onRemovePhoto
            )

            HorizontalDivider(color = CardBorder)

            // Details Column
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = agentName,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (branchCode.isNotBlank()) "Code: $agentCode • $branchName ($branchCode)" else "Code: $agentCode • $branchName",
                    color = RoyalBlueGlow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$mobileNumber • $emailAddress",
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = CardBorder)

            Button(
                onClick = onEditProfileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlueLight,
                    contentColor = TextWhite
                )
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Edit Profile Info", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/**
 * 2. APP PREFERENCES SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_preferences_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "APP PREFERENCES",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            // Dark Mode Switch
            SettingsSwitchRowItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Royal Blue banking dark interface",
                checked = isDarkMode,
                onCheckedChange = onDarkModeChange,
                tag = "dark_mode_switch"
            )

            HorizontalDivider(color = CardBorder)

            // System Theme Switch
            SettingsSwitchRowItem(
                icon = Icons.Default.SettingsSuggest,
                title = "System Theme",
                subtitle = "Match device display theme automatically",
                checked = isSystemTheme,
                onCheckedChange = onSystemThemeChange,
                tag = "system_theme_switch"
            )

            HorizontalDivider(color = CardBorder)

            // Language Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Language", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("English", "Hindi", "Marathi", "Gujarati")) { lang ->
                        val isSelected = selectedLanguage == lang
                        Surface(
                            onClick = { onLanguageChange(lang) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder)
                        ) {
                            Text(
                                text = lang,
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // Font Size Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Font Size", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Small", "Medium", "Large").forEach { size ->
                        val isSelected = selectedFontSize == size
                        Surface(
                            onClick = { onFontSizeChange(size) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = size,
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. NOTIFICATION SETTINGS SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_settings_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NOTIFICATION SETTINGS",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.NotificationsActive,
                title = "Premium Reminder",
                subtitle = "Master switch for policy renewal alerts",
                checked = isPremiumReminder,
                onCheckedChange = onPremiumReminderChange
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.Today,
                title = "Due Today",
                subtitle = "Alerts for policies renewing today",
                checked = isDueToday,
                onCheckedChange = onDueTodayChange
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.Event,
                title = "Tomorrow Reminder",
                subtitle = "1-day advance warning notifications",
                checked = isTomorrow,
                onCheckedChange = onTomorrowChange
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.Warning,
                title = "Overdue Reminder",
                subtitle = "Lapsed & grace period payment alerts",
                checked = isOverdue,
                onCheckedChange = onOverdueChange
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.Chat,
                title = "WhatsApp Reminder",
                subtitle = "Auto-formatted WhatsApp message triggers",
                checked = isWhatsApp,
                onCheckedChange = onWhatsAppChange
            )

            HorizontalDivider(color = CardBorder)

            // Reminder Time Picker Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reminder Time", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("08:00 AM", "09:00 AM", "02:00 PM", "07:00 PM").forEach { timeStr ->
                        val isSelected = selectedTime == timeStr
                        Surface(
                            onClick = { onTimeChange(timeStr) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = timeStr,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // Notification Sound Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Notification Sound", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("LIC Chime", "Gentle Bell", "Default Tone", "Silent")) { sound ->
                        val isSelected = selectedSound == sound
                        Surface(
                            onClick = { onSoundChange(sound) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder)
                        ) {
                            Text(
                                text = sound,
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.Vibration,
                title = "Vibration",
                subtitle = "Haptic feedback on reminder alerts",
                checked = isVibration,
                onCheckedChange = onVibrationChange
            )
        }
    }
}

/**
 * 4. RECEIPT SETTINGS SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_settings_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "RECEIPT SETTINGS",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            // Receipt Size Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Receipt Size", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("A5", "Thermal 3\"", "A4 Sheet").forEach { size ->
                        val isSelected = selectedSize == size
                        Surface(
                            onClick = { onSizeChange(size) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = size,
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.Title,
                title = "Receipt Header",
                subtitle = "Print custom title banner on receipts",
                checked = isHeader,
                onCheckedChange = onHeaderChange
            )

            if (isHeader) {
                OutlinedTextField(
                    value = headerTitle,
                    onValueChange = onHeaderTitleChange,
                    label = { Text("Custom Header Text", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = RoyalBlueLight,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.Draw,
                title = "Agent Signature",
                subtitle = "Attach digital agent signature mark",
                checked = isSignature,
                onCheckedChange = onSignatureChange
            )

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.QrCode,
                title = "QR Code",
                subtitle = "Embed UPI payment validation QR on PDFs",
                checked = isQrCode,
                onCheckedChange = onQrCodeChange
            )

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.Numbers,
                title = "Auto Receipt Number",
                subtitle = "Sequential automatic receipt numbering",
                checked = isAutoReceipt,
                onCheckedChange = onAutoReceiptChange
            )
        }
    }
}

/**
 * 5. BACKUP SETTINGS SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_settings_section")
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
                    text = "BACKUP SETTINGS",
                    color = RoyalBlueGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = AccentGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Encrypted Vault",
                        color = AccentGreen,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Backup Info Row
            Surface(
                color = DarkBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Last Backup Status", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(lastBackupText, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onBackupNowClick,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SettingsSwitchRowItem(
                icon = Icons.Default.Autorenew,
                title = "Auto Backup",
                subtitle = "Daily background cloud snapshots",
                checked = isAutoBackup,
                onCheckedChange = onAutoBackupChange
            )

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.CloudSync,
                title = "Cloud Sync",
                subtitle = "Real-time sync across registered agent devices",
                checked = isCloudSync,
                onCheckedChange = onCloudSyncChange
            )
        }
    }
}

/**
 * 6. SECURITY SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_settings_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "SECURITY & ACCESS",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            SettingsSwitchRowItem(
                icon = Icons.Default.Lock,
                title = "App Lock",
                subtitle = "Require authentication to open app",
                checked = isAppLock,
                onCheckedChange = onAppLockChange
            )

            HorizontalDivider(color = CardBorder)

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
                        Icon(Icons.Default.Pin, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("PIN Lock", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        Text("4-Digit Passcode Protection", color = TextMuted, fontSize = 11.5.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSetPinClick) {
                        Text("Set PIN", color = RoyalBlueGlow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isPinLock,
                        onCheckedChange = onPinLockChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextWhite,
                            checkedTrackColor = RoyalBlueLight,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBg
                        )
                    )
                }
            }

            HorizontalDivider(color = CardBorder)

            SettingsSwitchRowItem(
                icon = Icons.Default.Fingerprint,
                title = "Fingerprint",
                subtitle = "Biometric sensor authentication",
                checked = isFingerprint,
                onCheckedChange = onFingerprintChange
            )

            HorizontalDivider(color = CardBorder)

            // Face Unlock Placeholder
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
                        Icon(Icons.Default.Face, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Face Unlock", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            Surface(color = AccentAmber.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text("Experimental", color = AccentAmber, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text("Facial recognition login", color = TextMuted, fontSize = 11.5.sp)
                    }
                }

                Switch(
                    checked = isFaceUnlock,
                    onCheckedChange = onFaceUnlockChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = RoyalBlueLight,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkBg
                    )
                )
            }

            HorizontalDivider(color = CardBorder)

            // Auto Lock Duration Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Auto Lock Duration", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Immediate", "1 Min", "5 Min", "15 Min").forEach { duration ->
                        val isSelected = selectedAutoLockTime == duration
                        Surface(
                            onClick = { onAutoLockTimeChange(duration) },
                            color = if (isSelected) RoyalBlueLight else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueGlow else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = duration,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. DATA MANAGEMENT SECTION
 */
@Composable
fun DataManagementSection(
    storageUsedMb: Float,
    onExportDataClick: () -> Unit,
    onImportDataClick: () -> Unit,
    onResetDemoClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_management_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "DATA MANAGEMENT",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            // Storage Usage Display
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Storage Usage", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("${storageUsedMb} MB / 1.0 GB", color = TextMuted, fontSize = 12.sp)
                }

                LinearProgressIndicator(
                    progress = { storageUsedMb / 1024f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = RoyalBlueGlow,
                    trackColor = DarkBg
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportDataClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Data", color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onImportDataClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Data", color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = CardBorder)

            Surface(
                onClick = onResetDemoClick,
                color = DarkBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Demo Data", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("Re-populate demo clients and sample policies", color = TextMuted, fontSize = 11.5.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }
}

/**
 * 8. SUPPORT SECTION
 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SUPPORT & ABOUT",
                color = RoyalBlueGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.sp
            )

            SettingsClickableRowItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help Center",
                subtitle = "FAQs and agent usage guidelines",
                onClick = onHelpCenterClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.HeadsetMic,
                title = "Contact Support",
                subtitle = "Dedicated helpline & email support",
                onClick = onContactSupportClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.Feedback,
                title = "Feedback",
                subtitle = "Submit feature proposals or bug reports",
                onClick = onFeedbackClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "Data security and privacy commitments",
                onClick = onPrivacyPolicyClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.Description,
                title = "Terms & Conditions",
                subtitle = "Software terms of service agreement",
                onClick = onTermsClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.Star,
                title = "Rate App",
                subtitle = "Rate us 5-stars on Google Play Store",
                onClick = onRateAppClick
            )

            HorizontalDivider(color = CardBorder)

            SettingsClickableRowItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "LIC Premium Reminder Pro v2.5.0 (Build 2026)",
                onClick = onAboutClick
            )
        }
    }
}

// ===========================================================================
// HELPER COMPOSABLE ITEMS
// ===========================================================================

@Composable
fun SettingsSwitchRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(RoyalBluePrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text(text = subtitle, color = TextMuted, fontSize = 11.5.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = RoyalBlueLight,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkBg
            ),
            modifier = if (tag.isNotBlank()) Modifier.testTag(tag) else Modifier
        )
    }
}

@Composable
fun SettingsClickableRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(RoyalBluePrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text(text = subtitle, color = TextMuted, fontSize = 11.5.sp)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
fun SearchBranchDialog(
    currentCode: String,
    onBranchSelected: (LicBranch) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBranches = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            LicBranchMaster.defaultBranches
        } else {
            LicBranchMaster.defaultBranches.filter { branch ->
                branch.code.contains(searchQuery, ignoreCase = true) ||
                branch.name.contains(searchQuery, ignoreCase = true) ||
                branch.city.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = RoyalBlueLight
                )
                Text(
                    text = "Search LIC Branch Master",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by Code or Name (e.g. 02A, Balasore)...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = RoyalBlueLight,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_branch_input_field")
                )

                Text(
                    text = "Tap a branch to select code & auto-fill branch name:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(filteredBranches) { branch ->
                        val isSelected = currentCode.equals(branch.code, ignoreCase = true)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) RoyalBluePrimary.copy(alpha = 0.5f) else Color(0xFF0F172A)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AccentOrange else CardBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBranchSelected(branch) }
                                .testTag("branch_option_${branch.code}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentOrange else RoyalBlueLight)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = branch.code,
                                        style = TextStyle(
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = branch.name,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (branch.city.isNotBlank()) {
                                        Text(
                                            text = "Division / City: ${branch.city}",
                                            color = TextMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredBranches.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No branch found matching \"$searchQuery\"",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

// ===========================================================================
// PREVIEW
// ===========================================================================
@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        viewModel = null,
        onBackClick = {},
        onLogout = {}
    )
}

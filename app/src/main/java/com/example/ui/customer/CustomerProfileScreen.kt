package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CustomerEntity
import com.example.ui.LicViewModel
import kotlinx.coroutines.launch

// Royal Blue Dark Theme Palette
private val DarkBackground = Color(0xFF0B1120)
private val DarkCardSurface = Color(0xFF1E293B)
private val DarkCardSurfaceVariant = Color(0xFF0F172A)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val EmeraldGreen = Color(0xFF10B981)
private val EmeraldGreenContainer = Color(0xFF064E3B)
private val AmberDue = Color(0xFFF59E0B)
private val AmberDueContainer = Color(0xFF78350F)
private val CrimsonOverdue = Color(0xFFEF4444)
private val CrimsonOverdueContainer = Color(0xFF7F1D1D)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val BorderSlate = Color(0xFF334155)

// Data Models for UI Presentation
data class ProfilePolicyModel(
    val id: Long,
    val planName: String,
    val policyNumber: String,
    val premiumAmount: Double,
    val premiumMode: String,
    val dueDate: String,
    val status: String, // "Active", "Due", "Overdue"
    val yearsPaid: Int,
    val totalYears: Int
)

data class ProfilePaymentModel(
    val id: Long,
    val date: String,
    val amount: Double,
    val mode: String,
    val receiptNo: String
)

data class ProfileDocumentModel(
    val id: Long,
    val title: String,
    val docType: String,
    val status: String,
    val isUploaded: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
    customer: CustomerEntity? = null,
    viewModel: LicViewModel? = null,
    onEditCustomer: () -> Unit = {},
    onAddPolicyForCustomer: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Default Sample Customer Data if null
    val displayCustomer = remember(customer) {
        customer ?: CustomerEntity(
            id = 101L,
            name = "Rajesh Kumar Sharma",
            mobile = "+91 98765 43210",
            whatsapp = "+91 98765 43210",
            dob = "15/08/1982",
            address = "Flat 402, Royal Palms Apartments, M.G. Road, Sector 14, Gurgaon, Haryana - 122001",
            occupation = "Senior Business Consultant",
            pan = "ABCDE1234F",
            aadhaar = "9876 5432 1098",
            notes = "VIP Client. Prefers morning appointments between 10 AM and 12 PM."
        )
    }

    // Interactive Dialog States
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedPolicyForCollect by remember { mutableStateOf<ProfilePolicyModel?>(null) }
    var selectedPolicyForDetail by remember { mutableStateOf<ProfilePolicyModel?>(null) }
    var selectedPaymentForReceipt by remember { mutableStateOf<ProfilePaymentModel?>(null) }
    var showUploadDocDialog by remember { mutableStateOf(false) }

    // Snackbar and Photo Picker States
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedPhotoUri by remember(displayCustomer.id, displayCustomer.photoUri) {
        val prefs = context.getSharedPreferences("customer_photos", Context.MODE_PRIVATE)
        val savedUri = prefs.getString("customer_photo_${displayCustomer.id}", null)
        mutableStateOf(savedUri ?: displayCustomer.photoUri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {
                // Ignore if takePersistableUriPermission is not supported by uri provider
            }
            val uriStr = uri.toString()
            selectedPhotoUri = uriStr

            // Save to ViewModel if available
            viewModel?.updateCustomer(displayCustomer.copy(photoUri = uriStr))

            // Save to SharedPreferences for offline persistence
            val prefs = context.getSharedPreferences("customer_photos", Context.MODE_PRIVATE)
            prefs.edit().putString("customer_photo_${displayCustomer.id}", uriStr).apply()

            coroutineScope.launch {
                snackbarHostState.showSnackbar("Profile photo updated successfully.")
            }
        }
    }

    // Screen Entrance Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Sample Policies List
    val samplePolicies = remember {
        listOf(
            ProfilePolicyModel(
                id = 1L,
                planName = "Jeevan Umang (Plan 945)",
                policyNumber = "867452901",
                premiumAmount = 12500.0,
                premiumMode = "Yearly",
                dueDate = "15 Aug 2026",
                status = "Active",
                yearsPaid = 8,
                totalYears = 20
            ),
            ProfilePolicyModel(
                id = 2L,
                planName = "Jeevan Lakshya (Plan 933)",
                policyNumber = "741258963",
                premiumAmount = 8400.0,
                premiumMode = "Half-Yearly",
                dueDate = "28 Aug 2026",
                status = "Due",
                yearsPaid = 5,
                totalYears = 15
            ),
            ProfilePolicyModel(
                id = 3L,
                planName = "Jeevan Labh (Plan 936)",
                policyNumber = "963852741",
                premiumAmount = 15000.0,
                premiumMode = "Yearly",
                dueDate = "10 Jul 2026",
                status = "Overdue",
                yearsPaid = 12,
                totalYears = 21
            ),
            ProfilePolicyModel(
                id = 4L,
                planName = "Endowment Plan (Plan 914)",
                policyNumber = "321654987",
                premiumAmount = 6200.0,
                premiumMode = "Quarterly",
                dueDate = "01 Nov 2026",
                status = "Active",
                yearsPaid = 10,
                totalYears = 16
            )
        )
    }

    // Sample Payments Timeline
    val samplePayments = remember {
        listOf(
            ProfilePaymentModel(1L, "15 Jul 2025", 12500.0, "UPI (GPay)", "REC-2025-0891"),
            ProfilePaymentModel(2L, "28 Feb 2025", 8400.0, "Net Banking", "REC-2025-0312"),
            ProfilePaymentModel(3L, "14 Jul 2024", 12500.0, "Cheque (#40129)", "REC-2024-0744"),
            ProfilePaymentModel(4L, "25 Feb 2024", 8400.0, "Cash", "REC-2024-0199")
        )
    }

    // Sample Documents Vault
    var documentsList by remember {
        mutableStateOf(
            listOf(
                ProfileDocumentModel(1L, "Aadhaar Card", "Aadhaar", "Verified", true),
                ProfileDocumentModel(2L, "PAN Card", "PAN", "Verified", true),
                ProfileDocumentModel(3L, "Jeevan Umang Policy Bond", "Policy Bond", "Attached", true),
                ProfileDocumentModel(4L, "Nominee Declaration Form", "Nominee Form", "Verified", true)
            )
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Customer Profile",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 20.sp
                        )
                    );
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
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
                        onClick = onEditCustomer,
                        modifier = Modifier.testTag("edit_customer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = RoyalBlueLight
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TextWhite
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(DarkCardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Profile", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "Customer: ${displayCustomer.name}\nMobile: ${displayCustomer.mobile}\nPolicies: ${samplePolicies.size}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Customer Profile"))
                                }
                            )
                            HorizontalDivider(color = BorderSlate)
                            DropdownMenuItem(
                                text = { Text("Delete Customer", color = CrimsonOverdue) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonOverdue) },
                                onClick = {
                                    showMoreMenu = false
                                    Toast.makeText(context, "Delete option triggered for ${displayCustomer.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            // BOTTOM STICKY BUTTON: Add New Policy
            Surface(
                color = DarkBackground,
                border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = onAddPolicyForCustomer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, shape = RoundedCornerShape(28.dp), ambientColor = RoyalBlueGlow, spotColor = RoyalBlueGlow)
                            .testTag("add_new_policy_button"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(RoyalBluePrimary, RoyalBlueLight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddCard,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Add New Policy",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = tween(400)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ==================== 1. HEADER CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Large Circular Avatar with Photo Upload & Initials fallback
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("customer_avatar_container")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                            )
                                        )
                                        .border(2.dp, RoyalBlueLight.copy(alpha = 0.6f), CircleShape)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("customer_avatar_box"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!selectedPhotoUri.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = selectedPhotoUri,
                                            contentDescription = "Profile Photo",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        val initials = remember(displayCustomer.name) {
                                            displayCustomer.name.split(" ")
                                                .take(2)
                                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                                .joinToString("")
                                                .ifEmpty { "RK" }
                                        }
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 26.sp
                                            )
                                        )
                                    }
                                }

                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayCustomer.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite,
                                        fontSize = 20.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Status Badge
                                Surface(
                                    color = EmeraldGreenContainer,
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Active Customer",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = EmeraldGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Contact Details Row: Mobile & WhatsApp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mobile Clickable
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${displayCustomer.mobile}"))
                                        context.startActivity(intent)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call Mobile",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = displayCustomer.mobile,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            // WhatsApp Clickable
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val cleanNum = displayCustomer.whatsapp.replace(Regex("[^0-9]"), "")
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum"))
                                        context.startActivity(intent)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "WhatsApp",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "WhatsApp",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Address Row (2 lines)
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Address",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayCustomer.address,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    lineHeight = 18.sp,
                                    fontSize = 12.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ==================== 2. QUICK ACTION BUTTONS ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Call Quick Action
                    QuickActionButton(
                        icon = Icons.Default.Call,
                        label = "Call",
                        containerColor = DarkCardSurface,
                        contentColor = RoyalBlueLight,
                        borderColor = RoyalBluePrimary.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${displayCustomer.mobile}"))
                            context.startActivity(intent)
                        }
                    )

                    // WhatsApp Quick Action
                    QuickActionButton(
                        icon = Icons.Default.Chat,
                        label = "WhatsApp",
                        containerColor = DarkCardSurface,
                        contentColor = EmeraldGreen,
                        borderColor = EmeraldGreen.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val cleanNum = displayCustomer.whatsapp.replace(Regex("[^0-9]"), "")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum"))
                            context.startActivity(intent)
                        }
                    )

                    // Add Policy Quick Action
                    QuickActionButton(
                        icon = Icons.Default.AddCard,
                        label = "Add Policy",
                        containerColor = RoyalBluePrimary,
                        contentColor = Color.White,
                        borderColor = Color.Transparent,
                        modifier = Modifier.weight(1f),
                        onClick = onAddPolicyForCustomer
                    )

                    // Collect Premium Quick Action
                    QuickActionButton(
                        icon = Icons.Default.Payments,
                        label = "Collect",
                        containerColor = EmeraldGreen,
                        contentColor = Color.White,
                        borderColor = Color.Transparent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPolicyForCollect = samplePolicies.firstOrNull { it.status == "Due" || it.status == "Overdue" } ?: samplePolicies.first()
                        }
                    )
                }

                // ==================== 3. SUMMARY CARDS (2x2 GRID) ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryCard(
                            icon = Icons.Default.FolderSpecial,
                            iconTint = RoyalBlueLight,
                            value = "${samplePolicies.size}",
                            label = "Total Policies",
                            subtitle = "Registered",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            icon = Icons.Default.CheckCircle,
                            iconTint = EmeraldGreen,
                            value = "${samplePolicies.count { it.status == "Active" }}",
                            label = "Active Policies",
                            subtitle = "In-Force",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryCard(
                            icon = Icons.Default.Schedule,
                            iconTint = AmberDue,
                            value = "₹ 23,400",
                            label = "Due Premium",
                            subtitle = "1 Policy Due",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            icon = Icons.Default.MonetizationOn,
                            iconTint = EmeraldGreen,
                            value = "₹ 1,85,000",
                            label = "Total Collected",
                            subtitle = "Lifetime",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))

                // ==================== 4. POLICY LIST ====================
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Linked Policies (${samplePolicies.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 17.sp
                            )
                        )
                        TextButton(onClick = onAddPolicyForCustomer) {
                            Text("+ Add Policy", color = RoyalBlueLight, fontWeight = FontWeight.Bold)
                        }
                    }

                    samplePolicies.forEach { policy ->
                        ProfilePolicyCard(
                            policy = policy,
                            onCollect = { selectedPolicyForCollect = policy },
                            onDetails = { selectedPolicyForDetail = policy },
                            onReceipt = {
                                selectedPaymentForReceipt = samplePayments.first()
                            }
                        )
                    }
                }

                // ==================== 5. PAYMENT HISTORY (TIMELINE) ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Payment History",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                            Surface(
                                color = DarkCardSurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${samplePayments.size} Receipts",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timeline Items
                        samplePayments.forEachIndexed { index, payment ->
                            TimelineItem(
                                payment = payment,
                                isLast = index == samplePayments.lastIndex,
                                onViewReceipt = { selectedPaymentForReceipt = payment }
                            )
                        }
                    }
                }

                // ==================== 6. DOCUMENTS VAULT CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderShared,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Documents Vault",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                            OutlinedButton(
                                onClick = { showUploadDocDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoyalBlueLight),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload", color = RoyalBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        documentsList.forEach { doc ->
                            DocumentRowItem(doc = doc)
                            if (doc != documentsList.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // ==================== INTERACTIVE DIALOGS ====================

    // 1. Collect Premium Modal
    selectedPolicyForCollect?.let { policy ->
        AlertDialog(
            onDismissRequest = { selectedPolicyForCollect = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Collect Premium", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plan: ${policy.planName}", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    Text("Policy #: ${policy.policyNumber}", color = TextMuted, fontSize = 13.sp)
                    Text("Amount Due: ₹ ${"%.0f".format(policy.premiumAmount)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Due Date: ${policy.dueDate}", color = AmberDue, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Premium collected for ${policy.policyNumber}!", Toast.LENGTH_LONG).show()
                        selectedPolicyForCollect = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm Collection", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedPolicyForCollect = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // 2. Policy Detail Modal
    selectedPolicyForDetail?.let { policy ->
        AlertDialog(
            onDismissRequest = { selectedPolicyForDetail = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = { Text(policy.planName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Policy Number", policy.policyNumber)
                    DetailRow("Premium Amount", "₹ ${"%.0f".format(policy.premiumAmount)}")
                    DetailRow("Premium Mode", policy.premiumMode)
                    DetailRow("Next Due Date", policy.dueDate)
                    DetailRow("Status", policy.status)
                    DetailRow("Payment Tenure", "${policy.yearsPaid} of ${policy.totalYears} Years Paid")
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedPolicyForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // 3. Receipt View Modal
    selectedPaymentForReceipt?.let { payment ->
        AlertDialog(
            onDismissRequest = { selectedPaymentForReceipt = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Premium Receipt", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailRow("Receipt No", payment.receiptNo)
                    DetailRow("Date Paid", payment.date)
                    DetailRow("Amount", "₹ ${"%.0f".format(payment.amount)}")
                    DetailRow("Payment Mode", payment.mode)
                    DetailRow("Advisor", "LIC Premium Reminder Pro")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Receipt PDF shared!", Toast.LENGTH_SHORT).show()
                        selectedPaymentForReceipt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Receipt")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedPaymentForReceipt = null }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    // 4. Document Upload Modal
    if (showUploadDocDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDocDialog = false },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            title = { Text("Upload Document", fontWeight = FontWeight.Bold) },
            text = { Text("Select document type (Aadhaar, PAN, Policy Bond, or Nominee Form) to upload to Vault.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        documentsList = documentsList + ProfileDocumentModel(
                            id = System.currentTimeMillis(),
                            title = "Medical Medical Clearance Certificate",
                            docType = "Medical Form",
                            status = "Verified",
                            isUploaded = true
                        )
                        Toast.makeText(context, "Document uploaded to Vault!", Toast.LENGTH_SHORT).show()
                        showUploadDocDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Upload File")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUploadDocDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

// ==================== HELPER SUB-COMPONENTS ====================

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = modifier.height(104.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun ProfilePolicyCard(
    policy: ProfilePolicyModel,
    onCollect: () -> Unit,
    onDetails: () -> Unit,
    onReceipt: () -> Unit
) {
    val (statusColor, statusBg) = when (policy.status) {
        "Active" -> EmeraldGreen to EmeraldGreenContainer
        "Due" -> AmberDue to AmberDueContainer
        else -> CrimsonOverdue to CrimsonOverdueContainer
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = policy.planName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Policy #: ${policy.policyNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = policy.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Details Box
            Surface(
                color = DarkCardSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Premium Amount", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text("₹ ${"%.0f".format(policy.premiumAmount)} (${policy.premiumMode})", style = MaterialTheme.typography.bodyMedium.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Next Due Date", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(policy.dueDate, style = MaterialTheme.typography.bodyMedium.copy(color = AmberDue, fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar (Years Paid)
            val progress = remember(policy.yearsPaid, policy.totalYears) {
                policy.yearsPaid.toFloat() / policy.totalYears.toFloat()
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Progress", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text("${policy.yearsPaid} of ${policy.totalYears} Yrs (${(progress * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = RoyalBlueLight,
                    trackColor = BorderSlate
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Collect, Details, Receipt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCollect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.weight(1.1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Collect", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoyalBlueLight),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoyalBlueLight)
                }

                OutlinedButton(
                    onClick = onReceipt,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Receipt", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    payment: ProfilePaymentModel,
    isLast: Boolean,
    onViewReceipt: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Node Dot & Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen)
                    .border(2.dp, EmeraldGreenContainer, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(BorderSlate)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Card Content
        Surface(
            color = DarkCardSurfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .clickable { onViewReceipt() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "₹ ${"%.0f".format(payment.amount)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${payment.date} • ${payment.mode}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextWhite,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "Receipt: ${payment.receiptNo}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }

                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = "View Receipt",
                    tint = RoyalBlueLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentRowItem(doc: ProfileDocumentModel) {
    Surface(
        color = DarkCardSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (doc.docType) {
                        "Aadhaar" -> Icons.Default.Badge
                        "PAN" -> Icons.Default.CreditCard
                        "Policy Bond" -> Icons.Default.Description
                        else -> Icons.Default.FolderShared
                    },
                    contentDescription = null,
                    tint = RoyalBlueLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Type: ${doc.docType}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Surface(
                color = EmeraldGreenContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = doc.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Text(text = value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

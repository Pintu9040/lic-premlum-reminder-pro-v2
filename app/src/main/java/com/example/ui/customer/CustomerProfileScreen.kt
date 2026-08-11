package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.example.data.local.AgentProfileEntity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.payment.*
import com.example.ui.theme.*
import com.example.util.NoMatchingRecordsEmptyState
import com.example.util.PaymentAllocationEngine
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate

// Theme Palette Tokens for V2 Interface
private val DarkBackground = Color(0xFF0B1120)
private val DarkCardSurface = Color(0xFF1E293B)
private val DarkCardSurfaceVariant = Color(0xFF0F172A)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val EmeraldGreen = Color(0xFF10B981)
private val EmeraldGreenContainer = Color(0xFF064E3B)
private val AmberDue = Color(0xFFF59E0B)
private val AmberDueContainer = Color(0xFF78350F)
private val CrimsonOverdue = Color(0xFFEF4444)
private val CrimsonOverdueContainer = Color(0xFF7F1D1D)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val BorderSlate = Color(0xFF334155)
private val AccentOrange = Color(0xFFF97316)
private val AccentOrangeContainer = Color(0xFF7C2D12)

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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect real Room DB data from ViewModel if available
    val allCustomers by viewModel?.customers?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val allPolicies by viewModel?.policies?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val allPayments by viewModel?.payments?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val agentProfile by viewModel?.agentProfile?.collectAsState() ?: remember { mutableStateOf(null) }

    // Resolve current customer (real database or fallback sample)
    val currentCustomer = remember(customer, allCustomers) {
        if (customer != null) {
            allCustomers.find { it.id == customer.id } ?: customer
        } else if (allCustomers.isNotEmpty()) {
            allCustomers.first()
        } else {
            CustomerEntity(
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
    }

    // Derived policies and payments for this customer
    val customerPolicies = remember(currentCustomer.id, allPolicies) {
        allPolicies.filter { it.customerId == currentCustomer.id }
    }

    val customerPayments = remember(currentCustomer.id, allPayments, customerPolicies) {
        val policyIds = customerPolicies.map { it.id }.toSet()
        allPayments.filter { it.customerId == currentCustomer.id || policyIds.contains(it.policyId) }
            .sortedByDescending { it.paymentDate }
    }

    // Derived primary policy details
    val primaryPolicy = customerPolicies.firstOrNull()
    val policyNumberStr = if (customerPolicies.isNotEmpty()) {
        customerPolicies.joinToString(", ") { it.policyNumber }
    } else {
        "POL-867452901"
    }

    val planNameStr = primaryPolicy?.planName ?: "Jeevan Umang (Plan 945)"
    val policySummaries = customerPolicies.map { PaymentAllocationEngine.calculateCurrentDueSummary(it, customerPayments) }
    val totalPremiumAmount = if (policySummaries.isNotEmpty()) policySummaries.sumOf { it.premiumAmount } else 12500.0

    val primaryPremiumMode = primaryPolicy?.premiumMode ?: "Yearly"
    val primaryNextDueDate = primaryPolicy?.dueDate ?: "15 Aug 2026"

    // Outstanding balance & payment status calculations for current due cycle
    val totalPaidAmount = if (policySummaries.isNotEmpty()) policySummaries.sumOf { it.totalPaidForCurrentDue } else customerPayments.sumOf { it.paidAmount }
    val outstandingBalance = if (policySummaries.isNotEmpty()) policySummaries.sumOf { it.outstanding } else (totalPremiumAmount - totalPaidAmount).coerceAtLeast(0.0)

    val overallPaymentStatus = when {
        outstandingBalance <= 0.0 && totalPremiumAmount > 0.0 -> "Paid"
        totalPaidAmount > 0.0 -> "Partial"
        else -> "Pending"
    }

    // Interactive Dialog States
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    var selectedPaymentForDetails by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }

    // Search and Filter state for Payment History
    var paymentSearchQuery by remember { mutableStateOf("") }
    var activeFilterChip by remember { mutableStateOf("All") } // "All", "Paid", "Partial", "Pending", "This Month"

    // Profile Photo Picker State
    var selectedPhotoUri by remember(currentCustomer.id, currentCustomer.photoUri) {
        val prefs = context.getSharedPreferences("customer_photos", Context.MODE_PRIVATE)
        val savedUri = prefs.getString("customer_photo_${currentCustomer.id}", null)
        mutableStateOf(savedUri ?: currentCustomer.photoUri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {
                // Ignore if takePersistableUriPermission is not supported by provider
            }
            val uriStr = uri.toString()
            selectedPhotoUri = uriStr

            viewModel?.updateCustomer(currentCustomer.copy(photoUri = uriStr))

            val prefs = context.getSharedPreferences("customer_photos", Context.MODE_PRIVATE)
            prefs.edit().putString("customer_photo_${currentCustomer.id}", uriStr).apply()

            coroutineScope.launch {
                snackbarHostState.showSnackbar("Profile photo updated successfully.")
            }
        }
    }

    // Filtered Payment History list based on search and selected filter chip
    val filteredCustomerPayments = remember(customerPayments, paymentSearchQuery, activeFilterChip, primaryPolicy) {
        val today = LocalDate.now()
        val currentMonth = today.monthValue
        val currentYear = today.year

        customerPayments.filter { payment ->
            val remainingBal = getRemainingBalanceForPayment(payment, primaryPolicy, customerPayments)
            val isFullyPaid = remainingBal <= 0.0
            val isPartial = !isFullyPaid && payment.paidAmount > 0.0
            val paymentStatus = when {
                isFullyPaid -> "Paid"
                isPartial -> "Partial"
                else -> "Pending"
            }

            val pDate = try { LocalDate.parse(payment.paymentDate) } catch (e: Exception) { null }

            // Search Filter
            val matchesQuery = paymentSearchQuery.isBlank() ||
                    payment.paymentDate.contains(paymentSearchQuery, ignoreCase = true) ||
                    payment.paidAmount.toString().contains(paymentSearchQuery) ||
                    payment.paymentMode.contains(paymentSearchQuery, ignoreCase = true) ||
                    payment.receiptNumber.contains(paymentSearchQuery, ignoreCase = true) ||
                    paymentStatus.contains(paymentSearchQuery, ignoreCase = true)

            // Chip Filter
            val matchesChip = when (activeFilterChip) {
                "All" -> true
                "Paid" -> paymentStatus == "Paid"
                "Partial" -> paymentStatus == "Partial"
                "Pending" -> paymentStatus == "Pending"
                "This Month" -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
                "Cash" -> payment.paymentMode.contains("Cash", ignoreCase = true)
                "UPI" -> payment.paymentMode.contains("UPI", ignoreCase = true)
                "Bank" -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                "Cheque" -> payment.paymentMode.contains("Cheque", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesChip
        }
    }

    // Active row overflow menu state
    var activeRowMenuId by remember { mutableStateOf<Long?>(null) }

    // Screen Entrance Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
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
                    )
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
                            // 📄 Export PDF Report
                            DropdownMenuItem(
                                text = { Text("Export PDF Report", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AmberDue) },
                                onClick = {
                                    showMoreMenu = false
                                    val pdfText = "LIC CUSTOMER STATEMENT\n" +
                                            "====================================\n" +
                                            "Customer: ${currentCustomer.name}\n" +
                                            "Mobile: ${currentCustomer.mobile}\n" +
                                            "Address: ${currentCustomer.address}\n" +
                                            "Policy Number: $policyNumberStr\n" +
                                            "Plan Name: $planNameStr\n" +
                                            "Premium Amount: ₹${"%.0f".format(totalPremiumAmount)}\n" +
                                            "Next Due Date: $primaryNextDueDate\n" +
                                            "Outstanding Balance: ₹${"%.0f".format(outstandingBalance)}\n" +
                                            "====================================\n" +
                                            "Total Payments Recorded: ${customerPayments.size}\n" +
                                            "Total Paid: ₹${"%.0f".format(totalPaidAmount)}"

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, pdfText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Statement PDF"))
                                }
                            )

                            // 📊 Export Excel / CSV
                            DropdownMenuItem(
                                text = { Text("Export Excel / CSV", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldGreen) },
                                onClick = {
                                    showMoreMenu = false
                                    val csvText = "Date,Amount,Outstanding,Mode,Status\n" +
                                            customerPayments.joinToString("\n") { p ->
                                                "${p.paymentDate},${p.paidAmount},${getRemainingBalanceForPayment(p, primaryPolicy, customerPayments)},${p.paymentMode},Paid"
                                            }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, csvText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Statement CSV"))
                                }
                            )

                            // 📤 Share Statement
                            DropdownMenuItem(
                                text = { Text("Share Statement", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    val shareText = "LIC Customer Statement\n" +
                                            "Customer: ${currentCustomer.name}\n" +
                                            "Mobile: ${currentCustomer.mobile}\n" +
                                            "Policy: $policyNumberStr\n" +
                                            "Outstanding Balance: ₹${"%.0f".format(outstandingBalance)}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Customer Statement"))
                                }
                            )

                            HorizontalDivider(color = BorderSlate)

                            // ✏ Edit Customer
                            DropdownMenuItem(
                                text = { Text("Edit Customer", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    onEditCustomer()
                                }
                            )

                            // 🗑 Delete Customer
                            DropdownMenuItem(
                                text = { Text("Delete Customer", color = CrimsonOverdue, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonOverdue) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteCustomerDialog = true
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showRecordPaymentDialog = true },
                containerColor = AccentOrange,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_payment_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Payment",
                    tint = Color.White
                )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
            ) {

                // ==================== 1. TOP HEADER ====================
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circular Customer Photo
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
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
                                        val initials = remember(currentCustomer.name) {
                                            currentCustomer.name.split(" ")
                                                .take(2)
                                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                                .joinToString("")
                                                .ifEmpty { "C" }
                                        }
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 28.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Customer Name (Bold)
                            Text(
                                text = currentCustomer.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    fontSize = 22.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Mobile Number (Clickable to Dial)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentCustomer.mobile}"))
                                        context.startActivity(intent)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call Mobile",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentCustomer.mobile,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Compact Status Chips: Active Policy, Premium Mode, Paid / Partial / Pending
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Active Policy Chip
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
                                            text = "Active Policy",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = EmeraldGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 2. Premium Mode Chip
                                Surface(
                                    color = DarkCardSurfaceVariant,
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = primaryPremiumMode,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 3. Payment Status Chip (Paid / Partial / Pending)
                                val (statusColor, statusBg) = when (overallPaymentStatus) {
                                    "Paid" -> EmeraldGreen to EmeraldGreenContainer
                                    "Partial" -> AmberDue to AmberDueContainer
                                    else -> CrimsonOverdue to CrimsonOverdueContainer
                                }

                                Surface(
                                    color = statusBg,
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = overallPaymentStatus,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 2. CUSTOMER INFORMATION CARD ====================
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Customer Details",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    fontSize = 17.sp
                                )
                            )

                            HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))

                            // 👤 Customer Name
                            InfoRow(
                                icon = Icons.Default.Person,
                                label = "Customer Name",
                                value = currentCustomer.name
                            )

                            // 📱 Mobile Number
                            InfoRow(
                                icon = Icons.Default.Phone,
                                label = "Mobile Number",
                                value = currentCustomer.mobile
                            )

                            // 🏠 Address
                            InfoRow(
                                icon = Icons.Default.Home,
                                label = "Address",
                                value = currentCustomer.address
                            )

                            // 🛡 Policy Number
                            InfoRow(
                                icon = Icons.Default.Shield,
                                label = "Policy Number",
                                value = policyNumberStr
                            )

                            // 📋 Plan Name
                            InfoRow(
                                icon = Icons.Default.Assignment,
                                label = "Plan Name",
                                value = planNameStr
                            )

                            // 💰 Premium Amount
                            InfoRow(
                                icon = Icons.Default.AttachMoney,
                                label = "Premium Amount",
                                value = "₹ ${"%.0f".format(totalPremiumAmount)}",
                                valueColor = RoyalBlueLight
                            )

                            // 🔄 Premium Mode
                            InfoRow(
                                icon = Icons.Default.Autorenew,
                                label = "Premium Mode",
                                value = primaryPremiumMode
                            )

                            // 📅 Next Due Date
                            InfoRow(
                                icon = Icons.Default.Event,
                                label = "Next Due Date",
                                value = primaryNextDueDate,
                                valueColor = AmberDue
                            )

                            // ⚠ Outstanding Balance
                            InfoRow(
                                icon = Icons.Default.Warning,
                                label = "Outstanding Balance",
                                value = "₹ ${"%.0f".format(outstandingBalance)}",
                                valueColor = if (outstandingBalance > 0) CrimsonOverdue else EmeraldGreen
                            )
                        }
                    }
                }

                // ==================== 3. QUICK ACTIONS GRID (2 COLUMNS) ====================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 17.sp
                            )
                        )

                        // 2-Column Grid Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Call Customer
                            QuickActionGridCard(
                                icon = Icons.Default.Call,
                                label = "Call Customer",
                                containerColor = DarkCardSurface,
                                contentColor = RoyalBlueLight,
                                borderColor = BorderSlate,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentCustomer.mobile}"))
                                    context.startActivity(intent)
                                }
                            )

                            // 2. WhatsApp Reminder
                            QuickActionGridCard(
                                icon = Icons.Default.Chat,
                                label = "WhatsApp Reminder",
                                containerColor = DarkCardSurface,
                                contentColor = EmeraldGreen,
                                borderColor = BorderSlate,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val cleanNum = currentCustomer.whatsapp.ifBlank { currentCustomer.mobile }
                                        .replace(Regex("[^0-9]"), "")
                                    val formattedPhone = if (cleanNum.length == 10) "91$cleanNum" else cleanNum

                                    val msg = viewModel?.generatePremiumReminderMsg(
                                        customerName = currentCustomer.name,
                                        policyNo = policyNumberStr,
                                        planName = planNameStr,
                                        amount = totalPremiumAmount,
                                        dueDate = primaryNextDueDate,
                                        outstandingBalance = outstandingBalance
                                    ) ?: "Dear ${currentCustomer.name}, your LIC premium of ₹${"%.0f".format(totalPremiumAmount)} is due on $primaryNextDueDate."

                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${URLEncoder.encode(msg, "UTF-8")}")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, msg)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Reminder"))
                                    }
                                }
                            )
                        }

                        // 2-Column Grid Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 3. Edit Customer
                            QuickActionGridCard(
                                icon = Icons.Default.Edit,
                                label = "Edit Customer",
                                containerColor = DarkCardSurface,
                                contentColor = RoyalBlueLight,
                                borderColor = BorderSlate,
                                modifier = Modifier.weight(1f),
                                onClick = onEditCustomer
                            )

                            // 4. Delete Customer
                            QuickActionGridCard(
                                icon = Icons.Default.Delete,
                                label = "Delete Customer",
                                containerColor = DarkCardSurface,
                                contentColor = CrimsonOverdue,
                                borderColor = CrimsonOverdueContainer,
                                modifier = Modifier.weight(1f),
                                onClick = { showDeleteCustomerDialog = true }
                            )
                        }
                    }
                }

                // ==================== 4. PAYMENT HISTORY SECTION & STICKY SEARCH BAR + CHIPS ====================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    text = "Payment History (${filteredCustomerPayments.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = paymentSearchQuery,
                            onValueChange = { paymentSearchQuery = it },
                            placeholder = { Text("Search payment...", fontSize = 13.sp, color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBlueLight) },
                            trailingIcon = {
                                if (paymentSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { paymentSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardSurface,
                                unfocusedContainerColor = DarkCardSurface,
                                focusedBorderColor = RoyalBlueLight,
                                unfocusedBorderColor = BorderSlate,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_history_sticky_search")
                        )

                        // Material 3 Filter Chips: All, Paid, Partial, Pending, This Month, Cash, UPI, Bank, Cheque
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val chips = listOf("All", "Paid", "Partial", "Pending", "This Month", "Cash", "UPI", "Bank", "Cheque")
                            items(chips) { chipLabel ->
                                val isSelected = activeFilterChip == chipLabel
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeFilterChip = chipLabel },
                                    label = {
                                        Text(
                                            text = chipLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = DarkCardSurface,
                                        labelColor = TextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) RoyalBlueLight else BorderSlate
                                    )
                                )
                            }
                        }
                    }
                }

                // ==================== 5. PAYMENT HISTORY CARDS LIST ====================
                if (filteredCustomerPayments.isEmpty()) {
                    item {
                        NoMatchingRecordsEmptyState(
                            query = paymentSearchQuery,
                            onResetFilters = {
                                paymentSearchQuery = ""
                                activeFilterChip = "All"
                            }
                        )
                    }
                } else {
                    items(filteredCustomerPayments, key = { it.id }) { payment ->
                        val remainingBal = getRemainingBalanceForPayment(payment, primaryPolicy, customerPayments)
                        ProfilePaymentCard(
                            payment = payment,
                            remainingBalance = remainingBal,
                            agentProfile = agentProfile,
                            onEdit = { editingPayment = payment },
                            onDelete = { deletingPayment = payment },
                            onShare = {
                                val shareText = generateReceiptShareText(
                                    payment = payment,
                                    agentName = agentProfile?.agentName ?: "LIC Agent",
                                    agencyCode = agentProfile?.agencyCode ?: "",
                                    branch = agentProfile?.branchName ?: ""
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
                            },
                            onClick = { selectedPaymentForDetails = payment }
                        )
                    }
                }
            }
        }
    }

    // ==================== INTERACTIVE DIALOGS & MODALS ====================

    // 1. RECORD PAYMENT DIALOG
    if (showRecordPaymentDialog) {
        PaymentCollectionDialog(
            policy = primaryPolicy,
            customersList = allCustomers,
            policiesList = allPolicies,
            existingPayments = allPayments,
            onDismiss = { showRecordPaymentDialog = false },
            onSavePayment = { pol, paidAmt, mode, date, notes ->
                viewModel?.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    paymentMode = mode,
                    paymentDate = date,
                    notes = notes,
                    onSuccess = {
                        showRecordPaymentDialog = false
                        viewModel.refreshData()
                        Toast.makeText(context, "Payment recorded successfully!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // 2. PAYMENT DETAILS MODAL
    selectedPaymentForDetails?.let { payment ->
        val remainingBal = getRemainingBalanceForPayment(payment, primaryPolicy, customerPayments)
        val isFullyPaid = remainingBal <= 0.0
        val isPartial = !isFullyPaid && payment.paidAmount > 0.0
        val paymentStatus = when {
            isFullyPaid -> "Paid"
            isPartial -> "Partial"
            else -> "Pending"
        }

        AlertDialog(
            onDismissRequest = { selectedPaymentForDetails = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Payment Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("Receipt No", payment.receiptNumber)
                    DetailRow("Payment Date", payment.paymentDate)
                    DetailRow("Amount Paid", "₹ ${"%.0f".format(payment.paidAmount)}")
                    DetailRow("Outstanding Balance", "₹ ${"%.0f".format(remainingBal.coerceAtLeast(0.0))}")
                    DetailRow("Payment Mode", payment.paymentMode)
                    DetailRow("Status", paymentStatus)
                    if (payment.notes.isNotBlank()) {
                        DetailRow("Notes", payment.notes)
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit Payment Button
                        Button(
                            onClick = {
                                editingPayment = payment
                                selectedPaymentForDetails = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Delete Payment Button
                        Button(
                            onClick = {
                                deletingPayment = payment
                                selectedPaymentForDetails = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonOverdue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export PDF / Share Button
                        OutlinedButton(
                            onClick = {
                                val shareText = generateReceiptShareText(
                                    payment = payment,
                                    agentName = agentProfile?.agentName ?: "LIC Agent",
                                    agencyCode = agentProfile?.agencyCode ?: "",
                                    branch = agentProfile?.branchName ?: ""
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Receipt PDF"))
                                selectedPaymentForDetails = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RoyalBlueLight),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF", color = RoyalBlueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Close Button
                        OutlinedButton(
                            onClick = { selectedPaymentForDetails = null },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Close", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        )
    }

    // 3. EDIT PAYMENT DIALOG
    editingPayment?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            existingPayments = allPayments,
            onDismiss = { editingPayment = null },
            onSave = { updated ->
                viewModel?.updatePayment(updated) {
                    editingPayment = null
                    viewModel.refreshData()
                    Toast.makeText(context, "Payment updated successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 4. DELETE PAYMENT DIALOG
    deletingPayment?.let { payment ->
        DeletePaymentDialog(
            payment = payment,
            onDismiss = { deletingPayment = null },
            onConfirmDelete = {
                viewModel?.deletePayment(payment) {
                    deletingPayment = null
                    viewModel.refreshData()
                    Toast.makeText(context, "Payment deleted successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 5. DELETE CUSTOMER CONFIRMATION DIALOG
    if (showDeleteCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerDialog = false },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonOverdue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Customer", fontWeight = FontWeight.Bold, color = CrimsonOverdue)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${currentCustomer.name}? This will permanently remove the customer record.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.deleteCustomer(currentCustomer)
                        showDeleteCustomerDialog = false
                        Toast.makeText(context, "Customer deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonOverdue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteCustomerDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

// ==================== HELPER COMPOSABLES ====================

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = TextWhite
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = RoyalBlueLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 13.sp
                )
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun QuickActionGridCard(
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
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfilePaymentCard(
    payment: PaymentEntity,
    remainingBalance: Double,
    agentProfile: AgentProfileEntity? = null,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val isFullyPaid = remainingBalance <= 0.0
    val isPartial = !isFullyPaid && payment.paidAmount > 0.0
    val statusText = when {
        isFullyPaid -> "Paid"
        isPartial -> "Partial"
        else -> "Pending"
    }

    val statusBgColor = when (statusText) {
        "Paid" -> EmeraldGreenContainer
        "Partial" -> AmberDueContainer
        else -> CrimsonOverdueContainer
    }

    val statusTextColor = when (statusText) {
        "Paid" -> EmeraldGreen
        "Partial" -> AmberDue
        else -> CrimsonOverdue
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.2f))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Payment Date + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = RoyalBlueLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = payment.paymentDate,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 15.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusBgColor,
                        shape = RoundedCornerShape(50.dp),
                        border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    var showCardMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false },
                            modifier = Modifier.background(DarkCardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit", color = TextWhite, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = CrimsonOverdue, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonOverdue, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF", color = TextWhite, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showCardMenu = false
                                    onShare()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Premium Details Box
            Surface(
                color = DarkCardSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Amount Paid", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(
                            text = "₹ ${"%.0f".format(payment.paidAmount)}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(
                            text = "₹ ${"%.0f".format(remainingBalance.coerceAtLeast(0.0))}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (remainingBalance > 0) CrimsonOverdue else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Payment Mode & Receipt Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Mode: ${payment.paymentMode}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                }

                Text(
                    text = "Receipt: ${payment.receiptNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(color = RoyalBlueLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                )
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

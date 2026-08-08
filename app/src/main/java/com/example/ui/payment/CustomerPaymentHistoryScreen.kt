package com.example.ui.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.pdf.PdfReportData
import com.example.pdf.PdfReportGenerator
import com.example.pdf.ReportType
import com.example.ui.LicViewModel
import com.example.ui.theme.*
import com.example.util.NoMatchingRecordsEmptyState
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Theme Palette Tokens for Payment History V2
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPaymentHistoryScreen(
    customer: CustomerEntity,
    viewModel: LicViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Real-time StateFlow bindings for Room DB
    val allPayments by viewModel.payments.collectAsState()
    val allPolicies by viewModel.policies.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    // Current Customer entity (reactive)
    val currentCustomer = remember(customer, allCustomers) {
        allCustomers.find { it.id == customer.id } ?: customer
    }

    // Filter payments & policies for this specific customer
    val customerPolicies = remember(allPolicies, currentCustomer.id) {
        allPolicies.filter { it.customerId == currentCustomer.id }
    }

    val customerPayments = remember(allPayments, currentCustomer.id, customerPolicies) {
        val policyIds = customerPolicies.map { it.id }.toSet()
        allPayments.filter { it.customerId == currentCustomer.id || policyIds.contains(it.policyId) }
            .sortedByDescending { it.paymentDate }
    }

    // Policy number summary string
    val policyNumbersStr = remember(customerPolicies) {
        if (customerPolicies.isNotEmpty()) customerPolicies.joinToString(", ") { it.policyNumber }
        else "N/A"
    }

    // Summary calculations:
    // Business Logic: Outstanding = Premium Amount - Total Received
    // Examples: Premium ₹12500, Received ₹12500 -> Outstanding MUST be ₹0. Never show negative balance.
    val totalPremium = remember(customerPolicies) {
        if (customerPolicies.isNotEmpty()) customerPolicies.sumOf { it.premiumAmount } else 0.0
    }
    val totalCollected = remember(customerPayments) {
        customerPayments.sumOf { it.paidAmount }
    }
    val outstandingBalance = remember(totalPremium, totalCollected) {
        (totalPremium - totalCollected).coerceAtLeast(0.0)
    }

    // Interactive Dialog States
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }

    // Search and Filters State
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") } // "All", "Paid", "Partial", "Pending"
    var selectedModeFilter by remember { mutableStateOf("All") }  // "All", "Cash", "UPI", "Bank", "Cheque", "Online"
    var selectedDateFilter by remember { mutableStateOf("All") }  // "All", "Today", "This Month", "Custom Range"

    // Custom Date Range State
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    // Customer photo URI state
    val customerPhotoUri = remember(currentCustomer.id, currentCustomer.photoUri) {
        val prefs = context.getSharedPreferences("customer_photos", Context.MODE_PRIVATE)
        prefs.getString("customer_photo_${currentCustomer.id}", null) ?: currentCustomer.photoUri
    }

    // Filtered Payments computation
    val filteredPayments = remember(
        customerPayments,
        customerPolicies,
        searchQuery,
        selectedStatusFilter,
        selectedModeFilter,
        selectedDateFilter,
        customStartDate,
        customEndDate
    ) {
        val today = LocalDate.now()
        val currentMonth = today.monthValue
        val currentYear = today.year

        customerPayments.filter { payment ->
            val matchingPolicy = customerPolicies.find { it.id == payment.policyId }
            val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, customerPayments).coerceAtLeast(0.0)

            val isPaid = remainingBal <= 0.0
            val isPartial = !isPaid && payment.paidAmount > 0.0
            val paymentStatus = when {
                isPaid -> "Paid"
                isPartial -> "Partial"
                else -> "Pending"
            }

            val pDate = try { LocalDate.parse(payment.paymentDate) } catch (e: Exception) { null }

            // 1. Search filter: Date, Amount, Payment Mode, Remarks/Notes
            val matchesQuery = searchQuery.isBlank() ||
                    payment.paymentDate.contains(searchQuery, ignoreCase = true) ||
                    payment.paidAmount.toString().contains(searchQuery) ||
                    payment.paymentMode.contains(searchQuery, ignoreCase = true) ||
                    payment.notes.contains(searchQuery, ignoreCase = true) ||
                    payment.receiptNumber.contains(searchQuery, ignoreCase = true)

            // 2. Status filter: All, Paid, Partial, Pending
            val matchesStatus = when (selectedStatusFilter) {
                "All" -> true
                "Paid" -> paymentStatus == "Paid"
                "Partial" -> paymentStatus == "Partial"
                "Pending" -> paymentStatus == "Pending"
                else -> true
            }

            // 3. Payment Mode filter: Cash, UPI, Bank, Cheque, Online
            val matchesMode = when (selectedModeFilter) {
                "All" -> true
                "Cash" -> payment.paymentMode.equals("Cash", ignoreCase = true)
                "UPI" -> payment.paymentMode.equals("UPI", ignoreCase = true)
                "Bank" -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                "Cheque" -> payment.paymentMode.equals("Cheque", ignoreCase = true)
                "Online" -> payment.paymentMode.contains("Online", ignoreCase = true) || payment.paymentMode.contains("Portal", ignoreCase = true)
                else -> true
            }

            // 4. Date filter: Today, This Month, Custom Range
            val matchesDate = when (selectedDateFilter) {
                "All" -> true
                "Today" -> pDate?.isEqual(today) == true
                "This Month" -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
                "Custom Range" -> {
                    if (customStartDate != null && customEndDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate) && !pDate.isAfter(customEndDate)
                    } else if (customStartDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate)
                    } else true
                }
                else -> true
            }

            matchesQuery && matchesStatus && matchesMode && matchesDate
        }.sortedByDescending { it.paymentDate }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Payment History",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = currentCustomer.name,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        )
                    }
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
                        onClick = {
                            sharePaymentHistoryOnWhatsApp(context, currentCustomer, customerPayments, customerPolicies)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Payment History",
                            tint = TextWhite
                        )
                    }

                    var showTopMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showTopMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = TextWhite
                        )
                    }

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                        modifier = Modifier.background(DarkCardSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export PDF Report", color = TextWhite, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AmberDue) },
                            onClick = {
                                showTopMenu = false
                                coroutineScope.launch {
                                    val pdfData = PdfReportData(
                                        reportType = ReportType.COMPLETE_PORTFOLIO,
                                        customer = currentCustomer,
                                        paymentList = customerPayments,
                                        policyList = customerPolicies,
                                        agentProfile = agentProfile
                                    )
                                    val result = PdfReportGenerator.generatePdfReport(context, pdfData)
                                    result.onSuccess { file ->
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open Payment History PDF"))
                                    }.onFailure { err ->
                                        Toast.makeText(context, "PDF Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export Excel / CSV", color = TextWhite, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldGreen) },
                            onClick = {
                                showTopMenu = false
                                exportPaymentHistoryToCsv(context, currentCustomer.name, customerPayments, customerPolicies)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Share Statement", color = TextWhite, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = RoyalBlueLight) },
                            onClick = {
                                showTopMenu = false
                                sharePaymentHistoryOnWhatsApp(context, currentCustomer, customerPayments, customerPolicies)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {

            // ==================== 1. CUSTOMER SUMMARY CARD ====================
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Top section: Avatar + Customer Name + Mobile + Policy Number
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 60.dp), // Space for top-right 56dp FAB
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Customer Avatar (Initial or Photo)
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                            )
                                        )
                                        .border(1.5.dp, RoyalBlueLight.copy(alpha = 0.6f), CircleShape)
                                        .testTag("customer_summary_avatar"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!customerPhotoUri.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = customerPhotoUri,
                                            contentDescription = "Customer Photo",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
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
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentCustomer.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite,
                                            fontSize = 18.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📱 ${currentCustomer.mobile}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextMuted,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "🛡 Policy #: $policyNumbersStr",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))

                            // 3 Equal Summary Cards Below:
                            // 💰 Premium Amount | ✅ Total Received | ⚠ Outstanding Balance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. 💰 Premium Amount
                                SummaryMetricCard(
                                    title = "Premium Amount",
                                    icon = "💰",
                                    amount = totalPremium,
                                    valueColor = TextWhite,
                                    containerColor = DarkCardSurfaceVariant,
                                    borderColor = BorderSlate,
                                    modifier = Modifier.weight(1f)
                                )

                                // 2. ✅ Total Received
                                SummaryMetricCard(
                                    title = "Total Received",
                                    icon = "✅",
                                    amount = totalCollected,
                                    valueColor = EmeraldGreen,
                                    containerColor = EmeraldGreenContainer.copy(alpha = 0.4f),
                                    borderColor = EmeraldGreen.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                )

                                // 3. ⚠ Outstanding Balance
                                // Business logic: Outstanding = Premium Amount - Total Received.
                                // Never show negative balance.
                                SummaryMetricCard(
                                    title = "Outstanding",
                                    icon = "⚠",
                                    amount = outstandingBalance,
                                    valueColor = if (outstandingBalance > 0) CrimsonOverdue else EmeraldGreen,
                                    containerColor = if (outstandingBalance > 0) CrimsonOverdueContainer.copy(alpha = 0.4f) else EmeraldGreenContainer.copy(alpha = 0.4f),
                                    borderColor = if (outstandingBalance > 0) CrimsonOverdue.copy(alpha = 0.4f) else EmeraldGreen.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Requirement #3: Position FAB at Top Right corner of Customer Summary Card.
                    // Orange background, White plus icon, 56dp, Ripple animation, Shadow, Click: Open Record Payment screen.
                    FloatingActionButton(
                        onClick = { showAddPaymentDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(56.dp)
                            .testTag("summary_card_top_right_add_payment_fab"),
                        shape = CircleShape,
                        containerColor = AccentOrange,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Payment",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // ==================== 4. SEARCH BAR ====================
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search payment...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBlueLight) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = TextMuted)
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
                        .testTag("payment_history_search_input")
                )
            }

            // ==================== 5. MATERIAL 3 FILTER CHIPS ====================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Status Chips [All] [Paid] [Partial] [Pending]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            modifier = Modifier.width(60.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val statusList = listOf("All", "Paid", "Partial", "Pending")
                            items(statusList) { status ->
                                val isSelected = selectedStatusFilter == status
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedStatusFilter = status },
                                    label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(16.dp),
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

                    // Row 2: Payment Mode Chips [All] [Cash] [UPI] [Bank] [Cheque] [Online]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Mode:",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            modifier = Modifier.width(60.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val modeList = listOf("All", "Cash", "UPI", "Bank", "Cheque", "Online")
                            items(modeList) { mode ->
                                val isSelected = selectedModeFilter == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedModeFilter = mode },
                                    label = { Text(mode, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(16.dp),
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

                    // Row 3: Date Chips [All] [Today] [This Month] [Custom Range]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Date:",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            modifier = Modifier.width(60.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val dateList = listOf("All", "Today", "This Month", "Custom Range")
                            items(dateList) { dateOpt ->
                                val isSelected = selectedDateFilter == dateOpt
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDateFilter = dateOpt
                                        if (dateOpt == "Custom Range") {
                                            showCustomDateDialog = true
                                        }
                                    },
                                    label = { Text(dateOpt, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(16.dp),
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
            }

            // ==================== 6. PAYMENT HISTORY COMPACT TABLE ====================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payment History (${filteredPayments.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 17.sp
                        )
                    )
                }
            }

            if (filteredPayments.isEmpty()) {
                item {
                    NoMatchingRecordsEmptyState(
                        query = searchQuery,
                        onResetFilters = {
                            searchQuery = ""
                            selectedStatusFilter = "All"
                            selectedModeFilter = "All"
                            selectedDateFilter = "All"
                        }
                    )
                }
            } else {
                item {
                    var activeRowMenuId by remember { mutableStateOf<Long?>(null) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Scrollable Table Container
                            val tableScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tableScrollState)
                            ) {
                                // Table Header Row
                                Row(
                                    modifier = Modifier
                                        .background(DarkCardSurfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📅 Date", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBlueLight, fontSize = 12.sp), modifier = Modifier.width(95.dp))
                                    Text("💰 Paid Amount", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 12.sp, textAlign = TextAlign.End), modifier = Modifier.width(105.dp))
                                    Text("⚠ Outstanding", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = CrimsonOverdue, fontSize = 12.sp, textAlign = TextAlign.End), modifier = Modifier.width(110.dp))
                                    Text("💳 Mode", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 12.sp), modifier = Modifier.width(85.dp))
                                    Text("🟢 Status", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center), modifier = Modifier.width(90.dp))
                                    Spacer(modifier = Modifier.width(40.dp)) // Action Column Space
                                }

                                HorizontalDivider(color = BorderSlate)

                                // Table Rows
                                filteredPayments.forEachIndexed { index, payment ->
                                    val matchingPolicy = customerPolicies.find { it.id == payment.policyId }
                                    val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, customerPayments).coerceAtLeast(0.0)
                                    val isFullyPaid = remainingBal <= 0.0
                                    val isPartial = !isFullyPaid && payment.paidAmount > 0.0
                                    val statusText = when {
                                        isFullyPaid -> "Paid"
                                        isPartial -> "Partial"
                                        else -> "Pending"
                                    }

                                    val rowBg = if (index % 2 == 0) DarkCardSurface else DarkCardSurfaceVariant

                                    Row(
                                        modifier = Modifier
                                            .background(rowBg)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Date
                                        Text(
                                            text = payment.paymentDate,
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                            modifier = Modifier.width(95.dp),
                                            maxLines = 1
                                        )

                                        // Paid Amount (Right Aligned)
                                        Text(
                                            text = "₹${"%.0f".format(payment.paidAmount)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End),
                                            modifier = Modifier.width(105.dp),
                                            maxLines = 1
                                        )

                                        // Outstanding Balance (Right Aligned)
                                        Text(
                                            text = "₹${"%.0f".format(remainingBal)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (remainingBal > 0) CrimsonOverdue else TextMuted,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.End
                                            ),
                                            modifier = Modifier.width(110.dp),
                                            maxLines = 1
                                        )

                                        // Payment Mode
                                        Text(
                                            text = payment.paymentMode,
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                                            modifier = Modifier.width(85.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Status Chip
                                        Box(
                                            modifier = Modifier.width(90.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val (statusColor, statusBg) = when (statusText) {
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
                                                    text = statusText,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = statusColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // ⋮ More Menu
                                        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = { activeRowMenuId = payment.id },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Row Options",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = activeRowMenuId == payment.id,
                                                onDismissRequest = { activeRowMenuId = null },
                                                modifier = Modifier.background(DarkCardSurface)
                                            ) {
                                                // ✏ Edit
                                                DropdownMenuItem(
                                                    text = { Text("Edit", color = TextWhite, fontSize = 13.sp) },
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        activeRowMenuId = null
                                                        editingPayment = payment
                                                    }
                                                )

                                                // 🗑 Delete
                                                DropdownMenuItem(
                                                    text = { Text("Delete", color = CrimsonOverdue, fontSize = 13.sp) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonOverdue, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        activeRowMenuId = null
                                                        deletingPayment = payment
                                                    }
                                                )

                                                // 📄 Export PDF
                                                DropdownMenuItem(
                                                    text = { Text("Export PDF", color = TextWhite, fontSize = 13.sp) },
                                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AmberDue, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        activeRowMenuId = null
                                                        coroutineScope.launch {
                                                            val pdfData = PdfReportData(
                                                                reportType = ReportType.PREMIUM_RECEIPT,
                                                                customer = currentCustomer,
                                                                paymentList = listOf(payment),
                                                                policyList = customerPolicies.filter { it.id == payment.policyId },
                                                                agentProfile = agentProfile
                                                            )
                                                            val result = PdfReportGenerator.generatePdfReport(context, pdfData)
                                                            result.onSuccess { file ->
                                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                    setDataAndType(uri, "application/pdf")
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                }
                                                                context.startActivity(Intent.createChooser(intent, "Open Receipt PDF"))
                                                            }.onFailure { err ->
                                                                Toast.makeText(context, "PDF Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                )

                                                // 📤 Share
                                                DropdownMenuItem(
                                                    text = { Text("Share", color = TextWhite, fontSize = 13.sp) },
                                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        activeRowMenuId = null
                                                        val msg = "LIC PREMIUM RECEIPT\n" +
                                                                "Customer: ${currentCustomer.name}\n" +
                                                                "Date: ${payment.paymentDate}\n" +
                                                                "Amount Paid: ₹${"%.0f".format(payment.paidAmount)}\n" +
                                                                "Outstanding: ₹${"%.0f".format(remainingBal)}\n" +
                                                                "Mode: ${payment.paymentMode}\n" +
                                                                "Receipt #: ${payment.receiptNumber}"

                                                        val cleanNum = currentCustomer.whatsapp.ifBlank { currentCustomer.mobile }.replace(Regex("[^0-9]"), "")
                                                        try {
                                                            val intent = Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum&text=${URLEncoder.encode(msg, "UTF-8")}")
                                                            )
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                                putExtra(Intent.EXTRA_TEXT, msg)
                                                                type = "text/plain"
                                                            }
                                                            context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (index < filteredPayments.size - 1) {
                                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== INTERACTIVE DIALOGS ====================

    // 1. Record Payment Dialog (Auto refresh on save)
    if (showAddPaymentDialog) {
        PaymentCollectionDialog(
            policy = customerPolicies.firstOrNull(),
            customersList = listOf(currentCustomer),
            policiesList = customerPolicies,
            existingPayments = customerPayments,
            onDismiss = { showAddPaymentDialog = false },
            onSavePayment = { pol, paidAmt, mode, date, notes ->
                viewModel.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    paymentMode = mode,
                    paymentDate = date,
                    notes = notes,
                    onSuccess = {
                        showAddPaymentDialog = false
                        viewModel.refreshData() // REQUIREMENT #7: AUTO REFRESH
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Payment recorded successfully!")
                        }
                    }
                )
            }
        )
    }

    // 2. Edit Payment Dialog (Auto refresh on save)
    if (editingPayment != null) {
        EditPaymentDialog(
            payment = editingPayment!!,
            existingPayments = customerPayments,
            onDismiss = { editingPayment = null },
            onSave = { updatedPayment ->
                viewModel.updatePayment(updatedPayment) {
                    editingPayment = null
                    viewModel.refreshData() // REQUIREMENT #7: AUTO REFRESH
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Payment updated successfully!")
                    }
                }
            }
        )
    }

    // 3. Delete Payment Dialog (Auto refresh on confirm)
    if (deletingPayment != null) {
        DeletePaymentDialog(
            payment = deletingPayment!!,
            onDismiss = { deletingPayment = null },
            onConfirmDelete = {
                viewModel.deletePayment(deletingPayment!!) {
                    deletingPayment = null
                    viewModel.refreshData() // REQUIREMENT #7: AUTO REFRESH
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Payment deleted successfully!")
                    }
                }
            }
        )
    }

    // 4. Custom Date Range Dialog
    if (showCustomDateDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = { Text("Select Date Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter payments by date range:", fontSize = 13.sp, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                customStartDate = LocalDate.now().minusDays(30)
                                customEndDate = LocalDate.now()
                                showCustomDateDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                        ) {
                            Text("Last 30 Days", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                customStartDate = LocalDate.now().minusDays(90)
                                customEndDate = LocalDate.now()
                                showCustomDateDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCardSurfaceVariant)
                        ) {
                            Text("Last 90 Days", fontSize = 11.sp, color = TextWhite)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Close", color = RoyalBlueLight)
                }
            }
        )
    }
}

// ==================== HELPER COMPONENTS ====================

@Composable
private fun SummaryMetricCard(
    title: String,
    icon: String,
    amount: Double,
    valueColor: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$icon $title",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹${"%.0f".format(amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomerPaymentHistoryDataTable(
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customerName: String,
    onEdit: (PaymentEntity) -> Unit = {},
    onDelete: (PaymentEntity) -> Unit = {},
    onReceipt: (PaymentEntity) -> Unit = {}
) {
    var activeRowMenuId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val tableScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tableScrollState)
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(DarkCardSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 Date", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBlueLight, fontSize = 12.sp), modifier = Modifier.width(95.dp))
                    Text("💰 Paid", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 12.sp, textAlign = TextAlign.End), modifier = Modifier.width(105.dp))
                    Text("⚠ Outstanding", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = CrimsonOverdue, fontSize = 12.sp, textAlign = TextAlign.End), modifier = Modifier.width(110.dp))
                    Text("💳 Mode", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 12.sp), modifier = Modifier.width(85.dp))
                    Text("🟢 Status", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center), modifier = Modifier.width(90.dp))
                    Spacer(modifier = Modifier.width(40.dp))
                }

                HorizontalDivider(color = BorderSlate)

                // Table Rows
                payments.forEachIndexed { index, payment ->
                    val matchingPolicy = policies.find { it.id == payment.policyId }
                    val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, payments).coerceAtLeast(0.0)
                    val isFullyPaid = remainingBal <= 0.0
                    val isPartial = !isFullyPaid && payment.paidAmount > 0.0
                    val statusText = when {
                        isFullyPaid -> "Paid"
                        isPartial -> "Partial"
                        else -> "Pending"
                    }

                    val rowBg = if (index % 2 == 0) DarkCardSurface else DarkCardSurfaceVariant

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = payment.paymentDate,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            modifier = Modifier.width(95.dp),
                            maxLines = 1
                        )

                        Text(
                            text = "₹${"%.0f".format(payment.paidAmount)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End),
                            modifier = Modifier.width(105.dp),
                            maxLines = 1
                        )

                        Text(
                            text = "₹${"%.0f".format(remainingBal)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (remainingBal > 0) CrimsonOverdue else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.width(110.dp),
                            maxLines = 1
                        )

                        Text(
                            text = payment.paymentMode,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                            modifier = Modifier.width(85.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Box(
                            modifier = Modifier.width(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val (statusColor, statusBg) = when (statusText) {
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
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { activeRowMenuId = payment.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Row Options",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = activeRowMenuId == payment.id,
                                onDismissRequest = { activeRowMenuId = null },
                                modifier = Modifier.background(DarkCardSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit", color = TextWhite, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        activeRowMenuId = null
                                        onEdit(payment)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete", color = CrimsonOverdue, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonOverdue, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        activeRowMenuId = null
                                        onDelete(payment)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Export PDF", color = TextWhite, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AmberDue, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        activeRowMenuId = null
                                        onReceipt(payment)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Share", color = TextWhite, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        activeRowMenuId = null
                                        val msg = "LIC PREMIUM RECEIPT\n" +
                                                "Customer: $customerName\n" +
                                                "Date: ${payment.paymentDate}\n" +
                                                "Amount Paid: ₹${"%.0f".format(payment.paidAmount)}\n" +
                                                "Outstanding: ₹${"%.0f".format(remainingBal)}\n" +
                                                "Mode: ${payment.paymentMode}\n" +
                                                "Receipt #: ${payment.receiptNumber}"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, msg)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                                    }
                                )
                            }
                        }
                    }

                    if (index < payments.size - 1) {
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

fun exportPaymentHistoryToCsv(
    context: Context,
    customerName: String,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>
) {
    try {
        val fileName = "Payment_History_${customerName.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        val writer = file.bufferedWriter()

        writer.write("Payment Date,Customer Name,Policy Number,Premium Due,Amount Paid,Outstanding Balance,Payment Mode,Status,Remarks\n")

        for (payment in payments) {
            val matchingPolicy = policies.find { it.id == payment.policyId }
            val premiumDue = matchingPolicy?.premiumAmount ?: payment.paidAmount
            val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, payments).coerceAtLeast(0.0)
            val status = if (remainingBal <= 0) "Paid" else if (payment.paidAmount > 0) "Partial" else "Pending"

            val line = listOf(
                "\"${payment.paymentDate}\"",
                "\"$customerName\"",
                "\"${payment.policyNumber}\"",
                "\"${"%.2f".format(premiumDue)}\"",
                "\"${"%.2f".format(payment.paidAmount)}\"",
                "\"${"%.2f".format(remainingBal)}\"",
                "\"${payment.paymentMode}\"",
                "\"$status\"",
                "\"${payment.notes.replace("\"", "'")}\""
            ).joinToString(",")
            writer.write(line + "\n")
        }
        writer.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Payment History - $customerName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Payment History CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun sharePaymentHistoryOnWhatsApp(
    context: Context,
    customer: CustomerEntity,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>
) {
    try {
        val sb = StringBuilder()
        sb.append("📋 *CUSTOMER PAYMENT HISTORY STATEMENT*\n")
        sb.append("👤 *Client:* ${customer.name}\n")
        sb.append("📱 *Mobile:* ${customer.mobile}\n")
        sb.append("🗓️ *Date:* ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}\n\n")

        if (payments.isEmpty()) {
            sb.append("No payment history records found.\n")
        } else {
            payments.take(10).forEachIndexed { index, payment ->
                val matchingPolicy = policies.find { it.id == payment.policyId }
                val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, payments).coerceAtLeast(0.0)
                val status = if (remainingBal <= 0) "Paid" else "Partial"
                sb.append("${index + 1}. *Date:* ${payment.paymentDate}\n")
                sb.append("   • Policy #: ${payment.policyNumber}\n")
                sb.append("   • Amount Paid: ₹${"%.2f".format(payment.paidAmount)}\n")
                sb.append("   • Mode: ${payment.paymentMode}\n")
                sb.append("   • Status: $status\n\n")
            }
        }

        val cleanPhone = customer.whatsapp.ifBlank { customer.mobile }.replace(Regex("[^0-9]"), "")
        val encodedMessage = URLEncoder.encode(sb.toString(), "UTF-8")
        val uriStr = if (cleanPhone.isNotBlank()) "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage" else "https://api.whatsapp.com/send?text=$encodedMessage"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Customer Selection Dialog for Customer Payment History Quick Action
 */
@Composable
fun CustomerSelectionForHistoryDialog(
    customers: List<CustomerEntity>,
    policies: List<PolicyEntity>,
    onDismiss: () -> Unit,
    onCustomerSelected: (CustomerEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, policies, searchQuery) {
        if (searchQuery.isBlank()) customers
        else {
            customers.filter { cust ->
                val matchesName = cust.name.contains(searchQuery, ignoreCase = true)
                val matchesMobile = cust.mobile.contains(searchQuery) || cust.whatsapp.contains(searchQuery)
                val custPolicies = policies.filter { it.customerId == cust.id }
                val matchesPolicy = custPolicies.any { it.policyNumber.contains(searchQuery, ignoreCase = true) }
                matchesName || matchesMobile || matchesPolicy
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        titleContentColor = TextWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = RoyalBlueLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Customer Payment History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by Name, Mobile, Policy #...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBlueLight) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCardSurfaceVariant,
                        unfocusedContainerColor = DarkCardSurfaceVariant,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching customers found.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { cust ->
                            val custPolicies = policies.filter { it.customerId == cust.id }
                            val policyNumbers = custPolicies.joinToString(", ") { it.policyNumber }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCustomerSelected(cust) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = TextWhite)
                                        Text("Mobile: ${cust.mobile}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        if (policyNumbers.isNotBlank()) {
                                            Text("Policy #: $policyNumbers", style = MaterialTheme.typography.labelSmall, color = RoyalBlueLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Select", tint = RoyalBlueLight)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RoyalBlueLight)
            }
        }
    )
}

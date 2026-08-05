package com.example.ui.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.PaymentDateFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.components.*
import com.example.ui.theme.*
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Custom Date Filter Enum for Payment Dashboard
 */
enum class PaymentDashboardDateFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM_DATE,
    ALL
}

/**
 * Calculates remaining balance for a policy after a specific payment record in chronological order.
 */
fun getRemainingBalanceForPayment(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    allPaymentsForPolicy: List<PaymentEntity>
): Double {
    if (policy == null || policy.premiumAmount <= 0) return 0.0
    val installment = policy.premiumAmount
    val sortedPayments = allPaymentsForPolicy
        .filter { it.policyId == payment.policyId }
        .sortedBy { it.createdAt }

    var cumulativePaid = 0.0
    var remainingBalance = installment

    for (p in sortedPayments) {
        cumulativePaid += p.paidAmount
        val completedCycles = (cumulativePaid / installment).toInt()
        val paidInCurrentCycle = cumulativePaid - (completedCycles * installment)

        remainingBalance = if (paidInCurrentCycle > 0) {
            (installment - paidInCurrentCycle).coerceAtLeast(0.0)
        } else {
            0.0
        }

        if (p.id == payment.id) {
            return remainingBalance
        }
    }
    return remainingBalance
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    viewModel: LicViewModel,
    onBack: (() -> Unit)? = null
) {
    val stats by viewModel.paymentStats.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val allPolicies by viewModel.policies.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    val searchQuery by viewModel.paymentSearchQuery.collectAsState()
    val selectedModeFilter by viewModel.paymentModeFilter.collectAsState()

    // Dashboard state controls
    var activeDateFilter by remember { mutableStateOf(PaymentDashboardDateFilter.ALL) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var targetPolicyForCollection by remember { mutableStateOf<PolicyEntity?>(null) }

    val context = LocalContext.current
    val todayDateFormatted = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
        } catch (e: Exception) {
            "Today"
        }
    }

    // Filter payments locally based on date filter + customer search (Name, Policy Number, Mobile)
    val filteredPayments = remember(
        allPayments,
        allCustomers,
        allPolicies,
        searchQuery,
        activeDateFilter,
        selectedModeFilter,
        customStartDate,
        customEndDate
    ) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val currentMonth = today.monthValue
        val currentYear = today.year

        allPayments.filter { payment ->
            val matchingCust = allCustomers.find { it.id == payment.customerId }
            val custMobile = matchingCust?.mobile ?: ""
            val custWhatsapp = matchingCust?.whatsapp ?: ""

            // Search by Name, Policy Number, or Mobile Number
            val matchesQuery = searchQuery.isBlank() ||
                    payment.customerName.contains(searchQuery, ignoreCase = true) ||
                    payment.policyNumber.contains(searchQuery, ignoreCase = true) ||
                    payment.receiptNumber.contains(searchQuery, ignoreCase = true) ||
                    payment.notes.contains(searchQuery, ignoreCase = true) ||
                    custMobile.contains(searchQuery) ||
                    custWhatsapp.contains(searchQuery)

            val pDate = try { LocalDate.parse(payment.paymentDate) } catch (e: Exception) { null }

            val matchesDate = when (activeDateFilter) {
                PaymentDashboardDateFilter.ALL -> true
                PaymentDashboardDateFilter.TODAY -> pDate?.isEqual(today) == true
                PaymentDashboardDateFilter.YESTERDAY -> pDate?.isEqual(yesterday) == true
                PaymentDashboardDateFilter.THIS_WEEK -> pDate != null && !pDate.isBefore(startOfWeek) && !pDate.isAfter(today)
                PaymentDashboardDateFilter.THIS_MONTH -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
                PaymentDashboardDateFilter.CUSTOM_DATE -> {
                    if (customStartDate != null && customEndDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate) && !pDate.isAfter(customEndDate)
                    } else if (customStartDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate)
                    } else true
                }
            }

            val matchesMode = when (selectedModeFilter) {
                PaymentModeFilter.ALL -> true
                PaymentModeFilter.CASH -> payment.paymentMode.equals("Cash", ignoreCase = true)
                PaymentModeFilter.UPI -> payment.paymentMode.equals("UPI", ignoreCase = true)
                PaymentModeFilter.BANK_TRANSFER -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                PaymentModeFilter.CHEQUE -> payment.paymentMode.equals("Cheque", ignoreCase = true)
            }

            matchesQuery && matchesDate && matchesMode
        }.sortedByDescending { it.paymentDate }
    }

    // Pending Collections Calculation
    val pendingPoliciesCount = remember(allPolicies) {
        val today = LocalDate.now()
        allPolicies.count { pol ->
            val due = try { LocalDate.parse(pol.dueDate) } catch (e: Exception) { null }
            due != null && !due.isAfter(today.plusDays(30)) && pol.status.equals("Active", ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Payment Dashboard",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = todayDateFormatted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("payment_dashboard_back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showCustomDateDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Record Premium") },
                                leadingIcon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showRecordPaymentDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sync Payments") },
                                leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.triggerSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Summary") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val summaryText = "LIC Payment Collection Summary ($todayDateFormatted)\n" +
                                            "Today: ₹${"%.0f".format(stats.todayCollection)}\n" +
                                            "Month: ₹${"%.0f".format(stats.monthlyCollection)}\n" +
                                            "Outstanding: ₹${"%.0f".format(stats.outstandingAmount)}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, summaryText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Payment Summary"))
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RoyalBluePrimary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                // Expandable Search Bar inside Top Section
                AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = RoyalBluePrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setPaymentSearchQuery(it) },
                            placeholder = { Text("Search customer name, policy #, or mobile...", color = Color.White.copy(alpha = 0.6f)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setPaymentSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = Color.White)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("payment_dashboard_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    targetPolicyForCollection = null
                    showRecordPaymentDialog = true
                },
                containerColor = AccentOrange,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.AddCard, contentDescription = "Collect Premium", modifier = Modifier.size(20.dp)) },
                text = { Text("Collect Premium", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp,
                    hoveredElevation = 8.dp,
                    focusedElevation = 8.dp
                ),
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 58.dp)
                    .shadow(elevation = 8.dp, shape = FloatingActionButtonDefaults.extendedFabShape, spotColor = AccentOrange.copy(alpha = 0.5f))
                    .testTag("collect_premium_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // 1. SUMMARY CARDS SECTION (2x2 Grid)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "COLLECTION SUMMARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                letterSpacing = 1.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaymentSummaryCard(
                                title = "Today's Collection",
                                value = "₹${"%.0f".format(stats.todayCollection)}",
                                subtitle = "Today's total",
                                icon = Icons.Default.Today,
                                iconColor = EmeraldGreenSecondary,
                                containerColor = EmeraldGreenContainer,
                                modifier = Modifier.weight(1f)
                            )
                            PaymentSummaryCard(
                                title = "This Month Collection",
                                value = "₹${"%.0f".format(stats.monthlyCollection)}",
                                subtitle = "Current month",
                                icon = Icons.Default.CalendarMonth,
                                iconColor = RoyalBluePrimary,
                                containerColor = RoyalBlueContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaymentSummaryCard(
                                title = "Outstanding Amount",
                                value = "₹${"%.0f".format(stats.outstandingAmount)}",
                                subtitle = "Unpaid dues",
                                icon = Icons.Default.WarningAmber,
                                iconColor = ErrorRed,
                                containerColor = ErrorRedContainer,
                                modifier = Modifier.weight(1f)
                            )
                            PaymentSummaryCard(
                                title = "Pending Collections",
                                value = "$pendingPoliciesCount Policies",
                                subtitle = "Due soon",
                                icon = Icons.Default.PendingActions,
                                iconColor = AccentOrange,
                                containerColor = AccentOrangeContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // COLLECTION TARGET PROGRESS CARD
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetAmount = 100000.0
                                val currentAmount = stats.monthlyCollection
                                val progressFraction = (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f)
                                val percentFormatted = "%.0f".format(progressFraction * 100)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Monthly Collection Target",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        )
                                    }
                                    Text(
                                        text = "$percentFormatted% Goal",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = RoyalBluePrimary,
                                    trackColor = RoyalBlueContainer
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Achieved: ₹${"%.0f".format(currentAmount)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = EmeraldGreenSecondary, fontSize = 11.sp)
                                    )
                                    Text(
                                        text = "Target: ₹${"%.0f".format(targetAmount)}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. SEARCH INPUT BAR (ALWAYS VISIBLE IF NOT EXPANDED IN TOP BAR)
                if (!isSearchExpanded) {
                    item {
                        PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setPaymentSearchQuery(it) },
                            placeholder = { Text("Search customer name, policy #, or mobile...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBluePrimary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setPaymentSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("payment_main_search_bar"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // 3. FILTERS (DATE & PAYMENT MODE)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date Filter Chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(PaymentDashboardDateFilter.values()) { filter ->
                                val label = when (filter) {
                                    PaymentDashboardDateFilter.ALL -> "All"
                                    PaymentDashboardDateFilter.TODAY -> "Today"
                                    PaymentDashboardDateFilter.YESTERDAY -> "Yesterday"
                                    PaymentDashboardDateFilter.THIS_WEEK -> "This Week"
                                    PaymentDashboardDateFilter.THIS_MONTH -> "This Month"
                                    PaymentDashboardDateFilter.CUSTOM_DATE -> if (customStartDate != null) "Custom Range" else "Custom Date"
                                }
                                FilterChip(
                                    selected = activeDateFilter == filter,
                                    onClick = {
                                        if (filter == PaymentDashboardDateFilter.CUSTOM_DATE) {
                                            showCustomDateDialog = true
                                        } else {
                                            activeDateFilter = filter
                                        }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Payment Mode Chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(PaymentModeFilter.values()) { mode ->
                                val label = when (mode) {
                                    PaymentModeFilter.ALL -> "All Modes"
                                    PaymentModeFilter.CASH -> "Cash"
                                    PaymentModeFilter.UPI -> "UPI"
                                    PaymentModeFilter.BANK_TRANSFER -> "Bank"
                                    PaymentModeFilter.CHEQUE -> "Cheque"
                                }
                                FilterChip(
                                    selected = selectedModeFilter == mode,
                                    onClick = { viewModel.setPaymentModeFilter(mode) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }
                }

                // 4. HEADER TITLE FOR RECENT COLLECTIONS
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT COLLECTIONS (${filteredPayments.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }

                // 5. ERROR STATE CARD (IF ANY)
                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = ErrorRedContainer),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Unable to load transactions", fontWeight = FontWeight.Bold, color = ErrorRed)
                                    Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                                }
                                TextButton(onClick = { errorMessage = null; viewModel.triggerSync() }) {
                                    Text("Retry", fontWeight = FontWeight.Bold, color = ErrorRed)
                                }
                            }
                        }
                    }
                }

                // 6. LOADING STATE (SKELETON LOADER)
                if (isLoading) {
                    items(4) {
                        PaymentSkeletonCard()
                    }
                } else if (filteredPayments.isEmpty()) {
                    // 7. EMPTY STATE
                    item {
                        PaymentDashboardEmptyState(
                            onCollectFirst = {
                                targetPolicyForCollection = null
                                showRecordPaymentDialog = true
                            }
                        )
                    }
                } else {
                    // 8. RECENT COLLECTIONS LIST CARDS
                    itemsIndexed(filteredPayments, key = { _, payment -> payment.id }) { index, payment ->
                        val matchingPolicy = allPolicies.find { it.id == payment.policyId }
                        val matchingCustomer = allCustomers.find { it.id == payment.customerId }
                        val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, allPayments)

                        RecentCollectionCard(
                            payment = payment,
                            policy = matchingPolicy,
                            customer = matchingCustomer,
                            remainingBalance = remainingBal,
                            onCollectPremium = {
                                targetPolicyForCollection = matchingPolicy
                                showRecordPaymentDialog = true
                            },
                            onViewReceipt = { selectedPaymentForReceipt = payment },
                            onShareReceipt = {
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
                                context.startActivity(Intent.createChooser(intent, "Share Premium Receipt"))
                            },
                            onWhatsAppReceipt = {
                                val phone = matchingCustomer?.whatsapp?.ifEmpty { matchingCustomer.mobile }
                                    ?: matchingCustomer?.mobile ?: ""
                                val shareText = generateReceiptShareText(
                                    payment = payment,
                                    agentName = agentProfile?.agentName ?: "LIC Agent",
                                    agencyCode = agentProfile?.agencyCode ?: "",
                                    branch = agentProfile?.branchName ?: ""
                                )
                                if (phone.isNotBlank()) {
                                    val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                                    val formattedPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${URLEncoder.encode(shareText, "UTF-8")}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                                    }
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                                }
                            },
                            onEdit = { editingPayment = payment },
                            onDelete = { deletingPayment = payment }
                        )
                    }
                }
            }
        }
    }

    // RECORD PAYMENT DIALOG
    if (showRecordPaymentDialog) {
        PaymentCollectionDialog(
            policy = targetPolicyForCollection,
            customersList = allCustomers,
            policiesList = allPolicies,
            existingPayments = allPayments,
            onDismiss = {
                showRecordPaymentDialog = false
                targetPolicyForCollection = null
            },
            onSavePayment = { pol, paidAmt, mode, date, notes ->
                viewModel.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    paymentMode = mode,
                    paymentDate = date,
                    notes = notes,
                    onSuccess = {
                        showRecordPaymentDialog = false
                        targetPolicyForCollection = null
                    }
                )
            }
        )
    }

    editingPayment?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            existingPayments = allPayments,
            onDismiss = { editingPayment = null },
            onSave = { updated ->
                viewModel.updatePayment(updated) {
                    editingPayment = null
                }
            }
        )
    }

    deletingPayment?.let { payment ->
        DeletePaymentDialog(
            payment = payment,
            onDismiss = { deletingPayment = null },
            onConfirmDelete = {
                viewModel.deletePayment(payment) {
                    deletingPayment = null
                }
            }
        )
    }

    selectedPaymentForReceipt?.let { payment ->
        ReceiptDialog(
            payment = payment,
            agentName = agentProfile?.agentName ?: "LIC Agent",
            agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
            branch = agentProfile?.branchName ?: "LIC Branch",
            onDismiss = { selectedPaymentForReceipt = null }
        )
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            initialStart = customStartDate,
            initialEnd = customEndDate,
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { start, end ->
                customStartDate = start
                customEndDate = end
                activeDateFilter = PaymentDashboardDateFilter.CUSTOM_DATE
                showCustomDateDialog = false
            }
        )
    }
}

@Composable
fun PaymentSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.15f)
                ) {
                    Box(modifier = Modifier.size(6.dp))
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = iconColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RecentCollectionCard(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    customer: CustomerEntity?,
    remainingBalance: Double,
    onCollectPremium: () -> Unit,
    onViewReceipt: () -> Unit,
    onShareReceipt: () -> Unit,
    onWhatsAppReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (modeIcon, modeColor) = when (payment.paymentMode.uppercase()) {
        "UPI" -> Icons.Default.QrCodeScanner to RoyalBluePrimary
        "CASH" -> Icons.Default.Payments to EmeraldGreenSecondary
        "CHEQUE" -> Icons.AutoMirrored.Filled.ReceiptLong to AccentOrange
        else -> Icons.Default.AccountBalance to RoyalBlueLight
    }

    val planName = policy?.planName ?: "LIC Policy Plan"
    val isFullyPaid = remainingBalance == 0.0

    // Initials for Customer Photo Avatar
    val initials = remember(payment.customerName) {
        payment.customerName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "C" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Customer Row: Avatar + Name + Policy # + Paid Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Customer Photo / Initials Avatar
                Surface(
                    shape = CircleShape,
                    color = RoyalBluePrimary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$planName • Policy #${payment.policyNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${"%.0f".format(payment.paidAmount)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreenSecondary,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = payment.paymentDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info Badges Row: Mode, Receipt No, Status (Paid / Pending)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Payment Mode Tag
                    Surface(
                        color = modeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(modeIcon, contentDescription = null, tint = modeColor, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = payment.paymentMode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = modeColor, fontSize = 10.sp)
                            )
                        }
                    }

                    // Receipt Number Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = payment.receiptNumber.ifEmpty { "REC-${payment.id}" },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                // Status Badge (Paid / Pending)
                Surface(
                    color = if (isFullyPaid) EmeraldGreenContainer else AccentOrangeContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isFullyPaid) "Paid" else "Pending ₹${"%.0f".format(remainingBalance)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isFullyPaid) OnEmeraldGreenContainer else OnAccentOrangeContainer,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            if (payment.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notes: ${payment.notes}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // QUICK ACTIONS ROW (Collect Premium, View Receipt, Share Receipt, WhatsApp Receipt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Collect Premium (Royal Blue filled button)
                Button(
                    onClick = onCollectPremium,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBluePrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = "Collect", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Collect", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                }

                // 2. View Receipt (White outlined button)
                OutlinedButton(
                    onClick = onViewReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBluePrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "View Details", tint = RoyalBluePrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Receipt", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBluePrimary))
                }

                // 3. Share Receipt (White outlined button)
                OutlinedButton(
                    onClick = onShareReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBlueDark
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBlueDark),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = RoyalBlueDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBlueDark))
                }

                // 4. WhatsApp Receipt (WhatsApp Green filled button)
                Button(
                    onClick = onWhatsAppReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White))
                }
            }
        }
    }
}

@Composable
fun PaymentDashboardEmptyState(
    onCollectFirst: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = RoyalBlueContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Payments,
                        contentDescription = "No Payments",
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Payments Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "No premium collection receipts match your current date or search filter. Record your first collection now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onCollectFirst,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("collect_first_premium_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Collect First Premium", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PaymentSkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(120.dp).height(16.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.width(80.dp).height(12.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                }
                Box(modifier = Modifier.width(60.dp).height(20.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCollectionDialog(
    policy: PolicyEntity? = null,
    customersList: List<CustomerEntity> = emptyList(),
    policiesList: List<PolicyEntity> = emptyList(),
    existingPayments: List<PaymentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCollect: (amount: Double, lateFee: Double, mode: String, receiptNo: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onSavePayment: ((policy: PolicyEntity, paidAmount: Double, mode: String, date: String, notes: String) -> Unit)? = null
) {
    var customerSearchQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(
            if (policy != null) customersList.find { it.id == policy.customerId }
            else customersList.firstOrNull()
        )
    }

    val availablePolicies = remember(selectedCustomer, policiesList, policy) {
        if (selectedCustomer != null) {
            policiesList.filter { it.customerId == selectedCustomer!!.id }
        } else if (policy != null) {
            listOf(policy)
        } else {
            policiesList
        }
    }

    var selectedPolicy by remember {
        mutableStateOf<PolicyEntity?>(
            policy ?: availablePolicies.firstOrNull() ?: policiesList.firstOrNull()
        )
    }

    val paymentsForPolicy = remember(selectedPolicy, existingPayments) {
        if (selectedPolicy != null) {
            existingPayments.filter { it.policyId == selectedPolicy!!.id }
        } else {
            emptyList()
        }
    }

    val totalPaidSoFar = remember(paymentsForPolicy) {
        paymentsForPolicy.sumOf { it.paidAmount }
    }

    val installmentAmount = selectedPolicy?.premiumAmount ?: 0.0
    val completedCycles = if (installmentAmount > 0) (totalPaidSoFar / installmentAmount).toInt() else 0
    val paidInCurrentCycle = if (installmentAmount > 0) totalPaidSoFar - (completedCycles * installmentAmount) else 0.0
    val currentRemainingBeforeNew = if (installmentAmount > 0) (installmentAmount - paidInCurrentCycle).coerceAtLeast(0.0) else 0.0

    var amountStr by remember {
        mutableStateOf(if (currentRemainingBeforeNew > 0) currentRemainingBeforeNew.toString() else installmentAmount.toString())
    }
    var paymentDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var selectedMode by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")

    val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
    val newRemainingBalance = (currentRemainingBeforeNew - enteredAmount).coerceAtLeast(0.0)
    val isCompletingCycle = enteredAmount >= currentRemainingBeforeNew && currentRemainingBeforeNew > 0

    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showPolicyDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Record Premium",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = ErrorRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                // 1. CUSTOMER SEARCH & SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "1. Customer Search",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: customerSearchQuery,
                        onValueChange = {
                            customerSearchQuery = it
                            showCustomerDropdown = true
                            if (selectedCustomer != null && selectedCustomer?.name != it) {
                                selectedCustomer = null
                                selectedPolicy = null
                            }
                        },
                        placeholder = { Text("Search or select customer...") },
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showCustomerDropdown = !showCustomerDropdown }) {
                                Icon(
                                    if (showCustomerDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_customer_search"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showCustomerDropdown && customersList.isNotEmpty()) {
                        val filteredCustomers = customersList.filter {
                            it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                    it.mobile.contains(customerSearchQuery)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                filteredCustomers.forEach { cust ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedCustomer = cust
                                                customerSearchQuery = cust.name
                                                showCustomerDropdown = false
                                                val matchingPol = policiesList.firstOrNull { it.customerId == cust.id }
                                                selectedPolicy = matchingPol
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(cust.mobile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                // 2. POLICY SEARCH & SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "2. Policy Search",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = selectedPolicy?.let { "${it.planName} (#${it.policyNumber})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select policy...") },
                        leadingIcon = { Icon(Icons.Default.Policy, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPolicyDropdown = !showPolicyDropdown }) {
                                Icon(
                                    if (showPolicyDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_policy_search"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showPolicyDropdown && availablePolicies.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                availablePolicies.forEach { pol ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPolicy = pol
                                                showPolicyDropdown = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("${pol.planName} • Policy #${pol.policyNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Premium: ₹${pol.premiumAmount} • Due: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                // 3. AUTO PREMIUM DUE SUMMARY CARD
                selectedPolicy?.let { pol ->
                    Surface(
                        color = RoyalBlueContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Premium Due (Auto):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("₹${"%.2f".format(installmentAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Paid In Current Cycle:", style = MaterialTheme.typography.bodySmall)
                                Text("₹${"%.2f".format(paidInCurrentCycle)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Balance Due:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("₹${"%.2f".format(currentRemainingBeforeNew)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = AccentOrange))
                            }
                        }
                    }
                }

                // 4. AMOUNT RECEIVED INPUT
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "4. Amount Received (₹)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            errorMessage = null
                        },
                        placeholder = { Text("Enter amount received...") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = amountStr == currentRemainingBeforeNew.toString(),
                            onClick = { amountStr = currentRemainingBeforeNew.toString() },
                            label = { Text("Full Balance (₹${"%.0f".format(currentRemainingBeforeNew)})", style = MaterialTheme.typography.labelSmall) }
                        )
                        if (currentRemainingBeforeNew > 1000) {
                            FilterChip(
                                selected = amountStr == (currentRemainingBeforeNew / 2).toString(),
                                onClick = { amountStr = (currentRemainingBeforeNew / 2).toString() },
                                label = { Text("50% (₹${"%.0f".format(currentRemainingBeforeNew / 2)})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // 5. REMAINING BALANCE BADGE
                Surface(
                    color = if (newRemainingBalance == 0.0) EmeraldGreenContainer else AccentOrangeContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remaining Balance (Auto):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (newRemainingBalance == 0.0) OnEmeraldGreenContainer else OnAccentOrangeContainer
                            )
                            Text(
                                text = "₹${"%.2f".format(newRemainingBalance)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (newRemainingBalance == 0.0) EmeraldGreenSecondary else AccentOrange
                                )
                            )
                        }
                        if (isCompletingCycle || newRemainingBalance == 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Installment Paid! Next due date will advance automatically.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = EmeraldGreenSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // 6. PAYMENT DATE
                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it; errorMessage = null },
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // 7. PAYMENT MODE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Payment Mode",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modeOptions.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 8. NOTES
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Cheque No / Reference") },
                    placeholder = { Text("Optional payment remarks...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPolicy == null) {
                        errorMessage = "Please select a valid customer and policy."
                        return@Button
                    }
                    if (enteredAmount <= 0) {
                        errorMessage = "Please enter a valid payment amount greater than ₹0."
                        return@Button
                    }

                    val generatedReceiptNo = "REC-${System.currentTimeMillis()}"

                    if (onSavePayment != null) {
                        onSavePayment(selectedPolicy!!, enteredAmount, selectedMode, paymentDate, notes)
                    } else {
                        onCollect(enteredAmount, 0.0, selectedMode, generatedReceiptNo, notes)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_payment_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Payment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPaymentDialog(
    payment: PaymentEntity,
    existingPayments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onSave: (PaymentEntity) -> Unit
) {
    var amountStr by remember { mutableStateOf(payment.paidAmount.toString()) }
    var selectedMode by remember { mutableStateOf(payment.paymentMode) }
    var paymentDate by remember { mutableStateOf(payment.paymentDate) }
    var notes by remember { mutableStateOf(payment.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Payment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = RoyalBlueContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(payment.customerName, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                        Text("Policy #${payment.policyNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; errorMessage = null },
                    label = { Text("Amount Paid (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it; errorMessage = null },
                    label = { Text("Payment Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Payment Mode", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeOptions.forEach { mode ->
                        FilterChip(
                            selected = selectedMode.equals(mode, ignoreCase = true),
                            onClick = { selectedMode = mode },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: -1.0

                    if (amt <= 0) {
                        errorMessage = "Amount must be greater than ₹0."
                        return@Button
                    }

                    val updated = payment.copy(
                        paidAmount = amt,
                        paymentMode = selectedMode,
                        paymentDate = paymentDate.trim(),
                        notes = notes.trim()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
    )
}

@Composable
fun DeletePaymentDialog(
    payment: PaymentEntity,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Payment Record", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Text(
                "Are you sure you want to delete payment of ₹${"%.2f".format(payment.paidAmount)} made on ${payment.paymentDate} for ${payment.customerName}?\n\nThis will recalculate remaining balances automatically.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete Record", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReceiptDialog(
    payment: PaymentEntity,
    agentName: String,
    agencyCode: String,
    branch: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Payment Receipt", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        color = RoyalBluePrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "LIFE INSURANCE CORPORATION OF INDIA",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                "PREMIUM COLLECTION RECEIPT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentOrangeLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Date: ${payment.paymentDate}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Mode: ${payment.paymentMode}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ReceiptDetailRow("Customer Name", payment.customerName)
                    ReceiptDetailRow("Policy Number", payment.policyNumber)
                    ReceiptDetailRow("Receipt No", payment.receiptNumber.ifEmpty { "REC-${payment.id}" })
                    ReceiptDetailRow("Amount Paid", "₹${"%.2f".format(payment.paidAmount)}", isHighlight = true)

                    if (payment.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ReceiptDetailRow("Notes", payment.notes)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Authorized LIC Agent:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("$agentName ($agencyCode)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(branch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareText = generateReceiptShareText(
                        payment = payment,
                        agentName = agentName,
                        agencyCode = agencyCode,
                        branch = branch
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Receipt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
    )
}

@Composable
fun CustomDateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit
) {
    var startDateStr by remember { mutableStateOf(initialStart?.toString() ?: LocalDate.now().minusDays(7).toString()) }
    var endDateStr by remember { mutableStateOf(initialEnd?.toString() ?: LocalDate.now().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter By Custom Date Range", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startDateStr,
                    onValueChange = { startDateStr = it },
                    label = { Text("From Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endDateStr,
                    onValueChange = { endDateStr = it },
                    label = { Text("To Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = try { LocalDate.parse(startDateStr.trim()) } catch (e: Exception) { null }
                    val end = try { LocalDate.parse(endDateStr.trim()) } catch (e: Exception) { null }
                    onConfirm(start, end)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
            ) {
                Text("Apply Filter", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReceiptDetailRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isHighlight)
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
            else
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

fun generateReceiptShareText(
    payment: PaymentEntity,
    agentName: String,
    agencyCode: String,
    branch: String
): String {
    val remarksText = if (payment.notes.isNotBlank()) "• Notes: ${payment.notes}\n" else ""

    return "===================================\n" +
            "  LIC PREMIUM COLLECTION RECEIPT\n" +
            "===================================\n" +
            "• Date: ${payment.paymentDate}\n" +
            "• Customer Name: ${payment.customerName}\n" +
            "• Policy Number: ${payment.policyNumber}\n" +
            "• Receipt No: ${payment.receiptNumber.ifEmpty { "REC-${payment.id}" }}\n" +
            "• Payment Mode: ${payment.paymentMode}\n" +
            "• Amount Paid: ₹${"%.2f".format(payment.paidAmount)}\n" +
            remarksText +
            "-----------------------------------\n" +
            "Issued By: $agentName ($agencyCode)\n" +
            "Branch: $branch\n" +
            "==================================="
}

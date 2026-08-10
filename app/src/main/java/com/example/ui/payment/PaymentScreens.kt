package com.example.ui.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.pdf.PdfReportGenerator
import com.example.ui.LicViewModel
import com.example.ui.PaymentDateFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.NoMatchingRecordsEmptyState
import com.example.util.PaymentAllocationEngine
import com.example.util.SearchFilterEngine
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

data class PolicyPaymentSummary(
    val policyNumber: String,
    val policyId: Long,
    val customerId: Long,
    val customerName: String,
    val customerMobile: String,
    val policy: PolicyEntity?,
    val customer: CustomerEntity?,
    val payments: List<PaymentEntity>,
    val totalDue: Double,
    val totalPaid: Double,
    val balance: Double,
    val advance: Double,
    val status: PaymentRowStatus,
    val lastPaymentDate: String,
    val lastPaymentMode: String
)

/**
 * Calculates remaining balance for a policy after a specific payment record in chronological order.
 */
fun getRemainingBalanceForPayment(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    allPaymentsForPolicy: List<PaymentEntity>
): Double {
    return PaymentAllocationEngine.calculatePaymentRowDetails(payment, policy, allPaymentsForPolicy).outstandingAfterPayment
}

/**
 * Payment calculation helper adhering strictly to calculation rules.
 */
data class PaymentCalculationResult(
    val dueAmount: Double,
    val paidAmount: Double,
    val balance: Double,
    val advance: Double,
    val status: PaymentRowStatus
)

enum class PaymentRowStatus(val label: String) {
    PAID("Paid"),
    PARTIAL("Partial"),
    PENDING("Pending"),
    OVERPAID("Overpaid")
}

fun calculatePaymentStatus(dueAmount: Double, paidAmount: Double): PaymentCalculationResult {
    val due = if (dueAmount < 0) 0.0 else dueAmount
    val paid = if (paidAmount < 0) 0.0 else paidAmount

    return when {
        paid == 0.0 -> {
            PaymentCalculationResult(
                dueAmount = due,
                paidAmount = 0.0,
                balance = due,
                advance = 0.0,
                status = PaymentRowStatus.PENDING
            )
        }
        paid < due -> {
            PaymentCalculationResult(
                dueAmount = due,
                paidAmount = paid,
                balance = due - paid,
                advance = 0.0,
                status = PaymentRowStatus.PARTIAL
            )
        }
        paid == due -> {
            PaymentCalculationResult(
                dueAmount = due,
                paidAmount = paid,
                balance = 0.0,
                advance = 0.0,
                status = PaymentRowStatus.PAID
            )
        }
        else -> { // paid > due
            PaymentCalculationResult(
                dueAmount = due,
                paidAmount = paid,
                balance = 0.0,
                advance = paid - due,
                status = PaymentRowStatus.OVERPAID
            )
        }
    }
}

@Composable
fun PaymentStatusChip(status: PaymentRowStatus) {
    val (bgColor, textColor) = when (status) {
        PaymentRowStatus.PAID -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        PaymentRowStatus.PARTIAL -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        PaymentRowStatus.PENDING -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        PaymentRowStatus.OVERPAID -> Color(0xFFEDE7F6) to Color(0xFF512DA8)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            )
        )
    }
}

enum class PaymentFilterOption(val label: String) {
    ALL("All"),
    PAID("Paid"),
    PARTIAL("Partial"),
    PENDING("Pending"),
    OVERPAID("Overpaid"),
    CASH("Cash"),
    UPI("UPI"),
    BANK("Bank"),
    CHEQUE("Cheque"),
    TODAY("Today"),
    THIS_MONTH("This Month"),
    CUSTOM_RANGE("Custom Range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    viewModel: LicViewModel,
    initialCustomer: CustomerEntity? = null,
    initialPolicy: PolicyEntity? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val stats by viewModel.paymentStats.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()
    val allPolicies by viewModel.policies.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    var activeCustomer by remember { mutableStateOf<CustomerEntity?>(initialCustomer) }
    var activePolicy by remember { mutableStateOf<PolicyEntity?>(initialPolicy) }
    var selectedCustomerForHistory by remember { mutableStateOf<CustomerEntity?>(initialCustomer) }

    val searchQuery by viewModel.paymentSearchQuery.collectAsState()
    val selectedModeFilter by viewModel.paymentModeFilter.collectAsState()

    var activeDateFilter by remember { mutableStateOf(PaymentDashboardDateFilter.ALL) }
    var selectedFilter by remember { mutableStateOf("All") }

    // Custom Date Range
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }

    var isSearchExpanded by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var targetPolicyForCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var selectedPaymentForView by remember { mutableStateOf<PaymentEntity?>(null) }
    var selectedPolicySummaryForDetails by remember { mutableStateOf<PolicyPaymentSummary?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val todayDateFormatted = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
        } catch (e: Exception) {
            "Today"
        }
    }

    // 1. GROUP PAYMENTS BY POLICY NUMBER SO EACH UNIQUE POLICY APPEARS ONLY ONCE
    val policySummaries = remember(allPayments, allPolicies, allCustomers, activeCustomer, activePolicy) {
        val currentCust = activeCustomer
        val currentPol = activePolicy
        val policyNumbersFromPolicies = allPolicies.map { it.policyNumber }
        val policyNumbersFromPayments = allPayments.map { it.policyNumber }
        val allUniquePolicyNumbers = (policyNumbersFromPolicies + policyNumbersFromPayments)
            .filter { it.isNotBlank() }
            .distinct()

        allUniquePolicyNumbers.mapNotNull { polNum ->
            val matchingPolicy = allPolicies.find { it.policyNumber.equals(polNum, ignoreCase = true) }
            val matchingPayments = allPayments.filter {
                it.policyNumber.equals(polNum, ignoreCase = true) || (matchingPolicy != null && it.policyId == matchingPolicy.id)
            }.sortedByDescending { it.paymentDate }

            val custId = matchingPolicy?.customerId ?: matchingPayments.firstOrNull()?.customerId ?: -1L
            if (currentCust != null && custId != currentCust.id) return@mapNotNull null
            if (currentPol != null && matchingPolicy != null && matchingPolicy.id != currentPol.id) return@mapNotNull null

            val matchingCustomer = allCustomers.find { it.id == custId }
                ?: (if (currentCust != null && currentCust.id == custId) currentCust else null)

            val custName = matchingCustomer?.name ?: matchingPolicy?.customerName ?: matchingPayments.firstOrNull()?.customerName ?: "Policy $polNum"
            val custMobile = matchingCustomer?.mobile ?: matchingCustomer?.whatsapp ?: ""

            val due = matchingPolicy?.premiumAmount ?: matchingPayments.maxOfOrNull { it.paidAmount } ?: 0.0
            val paid = matchingPayments.sumOf { it.paidAmount }
            val calc = calculatePaymentStatus(due, paid)

            val lastDate = matchingPayments.firstOrNull()?.paymentDate
                ?: matchingPolicy?.issueDate?.ifEmpty { matchingPolicy.dueDate }
                ?: matchingPolicy?.dueDate
                ?: "N/A"
            val lastMode = matchingPayments.firstOrNull()?.paymentMode ?: "N/A"

            PolicyPaymentSummary(
                policyNumber = polNum,
                policyId = matchingPolicy?.id ?: matchingPayments.firstOrNull()?.policyId ?: -1L,
                customerId = custId,
                customerName = custName,
                customerMobile = custMobile,
                policy = matchingPolicy,
                customer = matchingCustomer,
                payments = matchingPayments,
                totalDue = calc.dueAmount,
                totalPaid = calc.paidAmount,
                balance = calc.balance,
                advance = calc.advance,
                status = calc.status,
                lastPaymentDate = lastDate,
                lastPaymentMode = lastMode
            )
        }.sortedByDescending { it.lastPaymentDate }
    }

    // 2. FILTER POLICY SUMMARIES
    val filteredPolicySummaries = remember(
        policySummaries,
        searchQuery,
        selectedFilter,
        customStartDate,
        customEndDate
    ) {
        val today = LocalDate.now()

        policySummaries.filter { summary ->
            val queryClean = searchQuery.trim().lowercase()
            val matchesSearch = queryClean.isEmpty() || listOf(
                summary.customerName,
                summary.policyNumber,
                summary.customerMobile,
                summary.lastPaymentDate,
                summary.lastPaymentMode,
                summary.totalPaid.toInt().toString()
            ).any { it.lowercase().contains(queryClean) } ||
            summary.payments.any { p ->
                p.receiptNumber.lowercase().contains(queryClean) || p.notes.lowercase().contains(queryClean)
            }

            val pDate = try { LocalDate.parse(summary.lastPaymentDate) } catch (e: Exception) { null }

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Paid" -> summary.status == PaymentRowStatus.PAID
                "Partial" -> summary.status == PaymentRowStatus.PARTIAL
                "Pending" -> summary.status == PaymentRowStatus.PENDING
                "Overpaid" -> summary.status == PaymentRowStatus.OVERPAID
                "Cash" -> summary.lastPaymentMode.equals("Cash", ignoreCase = true)
                "UPI" -> summary.lastPaymentMode.equals("UPI", ignoreCase = true)
                "Bank" -> summary.lastPaymentMode.contains("Bank", ignoreCase = true) || summary.lastPaymentMode.contains("Net", ignoreCase = true)
                "Cheque" -> summary.lastPaymentMode.equals("Cheque", ignoreCase = true)
                "Today" -> pDate?.isEqual(today) == true
                "This Month" -> pDate?.monthValue == today.monthValue && pDate?.year == today.year
                "Custom Range" -> {
                    if (pDate == null) false
                    else {
                        val matchesStart = customStartDate == null || !pDate.isBefore(customStartDate)
                        val matchesEnd = customEndDate == null || !pDate.isAfter(customEndDate)
                        matchesStart && matchesEnd
                    }
                }
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    // 3. COMPUTE SUMMARY STATS ACROSS UNIQUE POLICIES
    val (totalDue, totalPaid, totalOutstanding, totalAdvance) = remember(policySummaries) {
        var dueSum = 0.0
        var paidSum = 0.0
        var outSum = 0.0
        var advSum = 0.0

        policySummaries.forEach { summary ->
            dueSum += summary.totalDue
            paidSum += summary.totalPaid
            outSum += summary.balance
            advSum += summary.advance
        }

        listOf(dueSum, paidSum, outSum, advSum)
    }

    val overallStatus = when {
        totalOutstanding == 0.0 && totalAdvance > 0.0 -> PaymentRowStatus.OVERPAID
        totalOutstanding == 0.0 && totalPaid > 0.0 -> PaymentRowStatus.PAID
        totalPaid > 0.0 && totalOutstanding > 0.0 -> PaymentRowStatus.PARTIAL
        else -> PaymentRowStatus.PENDING
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payment History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("payment_history_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showCustomDateDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter Options", tint = Color.White)
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Payment") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
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
        },
        floatingActionButton = {}
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refreshData { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // STICKY SEARCH BAR BELOW APP BAR
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        AnimatedVisibility(
                            visible = isSearchExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setPaymentSearchQuery(it) },
                                placeholder = { Text("Search by customer, mobile, or policy #...", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBluePrimary) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setPaymentSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RoyalBluePrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("payment_history_sticky_search_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // COMPACT FILTER CHIPS BELOW SEARCH BAR (HORIZONTAL SCROLLABLE)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val allFilters = listOf(
                                "All", "Paid", "Partial", "Pending", "Overpaid",
                                "Cash", "UPI", "Bank", "Cheque",
                                "Today", "This Month", "Custom Range"
                            )

                            allFilters.forEach { filterLabel ->
                                FilterChip(
                                    selected = selectedFilter == filterLabel,
                                    onClick = {
                                        selectedFilter = filterLabel
                                        if (filterLabel == "Custom Range") {
                                            showCustomDateDialog = true
                                        }
                                    },
                                    label = { Text(filterLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
                ) {
                    // HEADER FOR LIST COUNT
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "POLICY RECORDS (${filteredPolicySummaries.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }

                    // ERROR STATE CARD (IF ANY)
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

                    // LOADING / EMPTY / LIST STATES
                    if (isLoading) {
                        items(4) {
                            PaymentSkeletonCard()
                        }
                    } else if (policySummaries.isEmpty()) {
                        item {
                            PaymentDashboardEmptyState(
                                onCollectFirst = {
                                    targetPolicyForCollection = null
                                    showRecordPaymentDialog = true
                                }
                            )
                        }
                    } else if (filteredPolicySummaries.isEmpty()) {
                        item {
                            NoMatchingRecordsEmptyState(
                                query = searchQuery,
                                onResetFilters = {
                                    viewModel.clearAllFilters()
                                    activeDateFilter = PaymentDashboardDateFilter.ALL
                                    selectedFilter = "All"
                                }
                            )
                        }
                    } else {
                        // UNIQUE POLICY SUMMARY CARDS
                        itemsIndexed(filteredPolicySummaries, key = { _, summary -> summary.policyNumber }) { index, summary ->
                            PolicySummaryCard(
                                summary = summary,
                                onCardClick = {
                                    selectedPolicySummaryForDetails = summary
                                },
                                onAddPayment = {
                                    targetPolicyForCollection = summary.policy
                                    showRecordPaymentDialog = true
                                }
                            )
                        }
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

    selectedPaymentForView?.let { payment ->
        val matchingPolicy = allPolicies.find { it.id == payment.policyId }
        val matchingCustomer = allCustomers.find { it.id == payment.customerId }
        PaymentDetailsDialog(
            payment = payment,
            policy = matchingPolicy,
            customer = matchingCustomer,
            agentName = agentProfile?.agentName ?: "LIC Agent",
            agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
            branch = agentProfile?.branchName ?: "LIC Branch",
            onDismiss = { selectedPaymentForView = null },
            onViewReceipt = {
                selectedPaymentForView = null
                selectedPaymentForReceipt = payment
            },
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
                context.startActivity(Intent.createChooser(intent, "Share Receipt"))
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
            onEdit = {
                selectedPaymentForView = null
                editingPayment = payment
            },
            onDelete = {
                selectedPaymentForView = null
                deletingPayment = payment
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

    selectedPolicySummaryForDetails?.let { summary ->
        val freshSummary = policySummaries.find { it.policyNumber == summary.policyNumber } ?: summary
        PolicyPaymentDetailsDialog(
            summary = freshSummary,
            agentName = agentProfile?.agentName ?: "LIC Agent",
            agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
            branch = agentProfile?.branchName ?: "LIC Branch",
            onDismiss = { selectedPolicySummaryForDetails = null },
            onAddPayment = {
                targetPolicyForCollection = freshSummary.policy
                showRecordPaymentDialog = true
            },
            onShareAllPdf = { summaryToShare ->
                scope.launch {
                    Toast.makeText(context, "Generating Payment History PDF...", Toast.LENGTH_SHORT).show()
                    val result = PdfReportGenerator.generateCustomerPaymentHistoryPdf(
                        context = context,
                        summary = summaryToShare,
                        agentProfile = agentProfile
                    )
                    result.onSuccess { pdfFile ->
                        PdfReportGenerator.sharePdf(context, pdfFile)
                    }.onFailure { err ->
                        Toast.makeText(context, "Failed to generate PDF: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onViewReceipt = { payment ->
                selectedPaymentForReceipt = payment
            },
            onShareReceipt = { payment ->
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
            onWhatsAppReceipt = { payment ->
                val phone = freshSummary.customerMobile
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
            onEditPayment = { payment ->
                editingPayment = payment
            },
            onDeletePayment = { payment ->
                deletingPayment = payment
            }
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
    onViewPayment: () -> Unit,
    onViewReceipt: () -> Unit,
    onShareReceipt: () -> Unit,
    onWhatsAppReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCustomerClick: ((CustomerEntity) -> Unit)? = null
) {
    var showCardMenu by remember { mutableStateOf(false) }

    val (modeIcon, modeColor) = when (payment.paymentMode.uppercase()) {
        "UPI" -> Icons.Default.QrCodeScanner to RoyalBluePrimary
        "CASH" -> Icons.Default.Payments to EmeraldGreenSecondary
        "CHEQUE" -> Icons.AutoMirrored.Filled.ReceiptLong to AccentOrange
        else -> Icons.Default.AccountBalance to RoyalBlueLight
    }

    val dueAmt = policy?.premiumAmount ?: payment.paidAmount
    val calc = calculatePaymentStatus(dueAmt, payment.paidAmount)

    // Initials for Customer Avatar
    val initials = remember(payment.customerName) {
        payment.customerName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "C" }
    }

    val fallbackCustomer = customer ?: CustomerEntity(
        id = payment.customerId,
        name = payment.customerName,
        mobile = "",
        email = "",
        address = "",
        dob = "",
        occupation = ""
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable {
                onViewPayment()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Customer Name + Status Chip + Three-Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = RoyalBluePrimary,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = payment.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Policy: ${payment.policyNumber}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PaymentStatusChip(status = calc.status)

                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Payment Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Payment") },
                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onViewPayment()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Payment") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Receipt") },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onViewReceipt()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onShareReceipt()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("WhatsApp") },
                                leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onWhatsAppReceipt()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = ErrorRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Row: Paid, Balance, Date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paid: ₹${"%.0f".format(payment.paidAmount)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreenSecondary,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = "Balance: ₹${"%.0f".format(remainingBalance.coerceAtLeast(0.0))}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (remainingBalance > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = payment.paymentDate,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(modeIcon, contentDescription = null, tint = modeColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = payment.paymentMode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = modeColor,
                                fontSize = 11.sp
                            )
                        )
                    }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Receipt Button
                OutlinedButton(
                    onClick = onViewReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBluePrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", tint = RoyalBluePrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Receipt", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBluePrimary))
                }

                // Share Button
                OutlinedButton(
                    onClick = onShareReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBlueDark
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBlueDark),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = RoyalBlueDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBlueDark))
                }

                // WhatsApp Button
                Button(
                    onClick = onWhatsAppReceipt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
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
                text = "No premium payment has been recorded yet.",
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
                Text("+ Collect First Premium", fontWeight = FontWeight.Bold)
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
fun RecordPaymentScreen(
    viewModel: LicViewModel,
    initialCustomer: CustomerEntity? = null,
    initialPolicy: PolicyEntity? = null,
    onBack: () -> Unit,
    onPaymentSaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val customersList by viewModel.customers.collectAsState()
    val policiesList by viewModel.policies.collectAsState()
    val existingPayments by viewModel.payments.collectAsState()

    var customerSearchQuery by remember { mutableStateOf("") }

    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(
            initialCustomer ?: initialPolicy?.let { pol -> customersList.find { it.id == pol.customerId } } ?: customersList.firstOrNull()
        )
    }

    var isCustomerPickerOpen by remember { mutableStateOf(selectedCustomer == null) }

    val availablePolicies = remember(selectedCustomer, policiesList, initialPolicy) {
        if (selectedCustomer != null) {
            policiesList.filter { it.customerId == selectedCustomer!!.id }
        } else if (initialPolicy != null) {
            listOf(initialPolicy)
        } else {
            policiesList
        }
    }

    var selectedPolicy by remember {
        mutableStateOf<PolicyEntity?>(
            initialPolicy ?: availablePolicies.firstOrNull() ?: policiesList.firstOrNull()
        )
    }

    LaunchedEffect(selectedCustomer) {
        if (selectedCustomer != null) {
            val matchingPols = policiesList.filter { it.customerId == selectedCustomer!!.id }
            if (matchingPols.isNotEmpty() && (selectedPolicy == null || selectedPolicy!!.customerId != selectedCustomer!!.id)) {
                selectedPolicy = matchingPols.first()
            }
        }
    }

    val currentSummary = remember(selectedPolicy, existingPayments) {
        if (selectedPolicy != null) {
            PaymentAllocationEngine.calculateCurrentDueSummary(selectedPolicy!!, existingPayments)
        } else null
    }

    val installmentAmount = selectedPolicy?.premiumAmount ?: 0.0
    val advanceAdjusted = currentSummary?.advanceAdjusted ?: 0.0
    val directPaidInCurrentCycle = currentSummary?.directPaid ?: 0.0
    val currentRemainingBeforeNew = currentSummary?.outstanding ?: installmentAmount

    var amountStr by remember { mutableStateOf("") }

    LaunchedEffect(selectedPolicy) {
        amountStr = ""
    }

    var paymentDate by remember {
        mutableStateOf(
            try {
                LocalDate.now().toString()
            } catch (e: Exception) {
                "2026-08-10"
            }
        )
    }

    var selectedMode by remember { mutableStateOf("UPI") }
    var chequeNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf(paymentDate) }
    var utrReference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")

    val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
    val isAmountExceeding = enteredAmount > currentRemainingBeforeNew && currentRemainingBeforeNew > 0

    val totalAllocated = advanceAdjusted + directPaidInCurrentCycle + enteredAmount
    val previewOutstanding = kotlin.math.max(0.0, installmentAmount - totalAllocated)
    val previewNextCycleAdvance = kotlin.math.max(0.0, totalAllocated - installmentAmount)

    val previewStatus = when {
        totalAllocated == 0.0 -> PaymentRowStatus.PENDING
        totalAllocated < installmentAmount -> PaymentRowStatus.PARTIAL
        totalAllocated == installmentAmount -> PaymentRowStatus.PAID
        else -> PaymentRowStatus.OVERPAID
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Record Premium Payment",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Record new payment & update due cycle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            if (selectedCustomer == null) {
                                errorMessage = "Please select a customer."
                                return@Button
                            }
                            if (selectedPolicy == null) {
                                errorMessage = "Please select a policy."
                                return@Button
                            }
                            if (enteredAmount <= 0) {
                                errorMessage = "Please enter an amount greater than ₹0."
                                return@Button
                            }

                            val fullNotes = buildString {
                                if (notes.isNotBlank()) append(notes)
                                if (selectedMode == "Cheque" && chequeNumber.isNotBlank()) {
                                    if (isNotEmpty()) append(" | ")
                                    append("Cheque #: $chequeNumber, Bank: $bankName, Date: $chequeDate")
                                }
                                if ((selectedMode == "UPI" || selectedMode == "Bank") && utrReference.isNotBlank()) {
                                    if (isNotEmpty()) append(" | ")
                                    append("Ref/UTR: $utrReference")
                                }
                            }

                            isSaving = true
                            viewModel.collectPremium(
                                policy = selectedPolicy!!,
                                paidAmount = enteredAmount,
                                paymentMode = selectedMode,
                                receiptNo = "REC-${System.currentTimeMillis()}",
                                paymentDate = paymentDate,
                                notes = fullNotes,
                                onSuccess = {
                                    Toast.makeText(context, "Payment recorded successfully!", Toast.LENGTH_SHORT).show()
                                    if (onPaymentSaved != null) onPaymentSaved() else onBack()
                                }
                            )
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_payment_screen_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Payment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                Surface(
                    color = ErrorRedContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = ErrorRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // 1. Customer Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "1. Select Customer",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                    )

                    if (!isCustomerPickerOpen && selectedCustomer != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = CircleShape,
                                    color = RoyalBluePrimary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val initials = selectedCustomer!!.name
                                            .split(" ")
                                            .mapNotNull { it.firstOrNull()?.toString() }
                                            .take(2)
                                            .joinToString("")
                                            .uppercase()
                                            .ifEmpty { "C" }
                                        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(selectedCustomer!!.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Mobile: ${selectedCustomer!!.mobile.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedButton(
                                onClick = { isCustomerPickerOpen = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = customerSearchQuery,
                            onValueChange = { customerSearchQuery = it },
                            placeholder = { Text("Search customer name, mobile or policy...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBluePrimary) },
                            trailingIcon = {
                                if (customerSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { customerSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        val filteredCustomers = remember(customerSearchQuery, customersList, policiesList) {
                            customersList.filter { cust ->
                                val matchesName = cust.name.contains(customerSearchQuery, ignoreCase = true)
                                val matchesMobile = cust.mobile.contains(customerSearchQuery)
                                val matchesPolicy = policiesList.any { pol -> pol.customerId == cust.id && pol.policyNumber.contains(customerSearchQuery, ignoreCase = true) }
                                matchesName || matchesMobile || matchesPolicy
                            }.distinctBy { it.id }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No matching customers found", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    filteredCustomers.forEach { cust ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCustomer = cust
                                                    isCustomerPickerOpen = false
                                                    customerSearchQuery = ""
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Mobile: ${cust.mobile.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Policy Selection
            if (selectedCustomer != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "2. Select Policy",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                        )

                        if (availablePolicies.isEmpty()) {
                            Text("No policies found for this customer.", style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                        } else if (availablePolicies.size == 1) {
                            val pol = availablePolicies.first()
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("${pol.planName} • #${pol.policyNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text("Premium: ₹${"%,.0f".format(pol.premiumAmount)} • Due Date: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            var showPolDropdown by remember { mutableStateOf(false) }
                            Box {
                                OutlinedTextField(
                                    value = selectedPolicy?.let { "${it.planName} (#${it.policyNumber})" } ?: "Select Policy",
                                    onValueChange = {},
                                    readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPolDropdown = !showPolDropdown }) {
                                            Icon(if (showPolDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { showPolDropdown = !showPolDropdown },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                DropdownMenu(
                                    expanded = showPolDropdown,
                                    onDismissRequest = { showPolDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    availablePolicies.forEach { pol ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("${pol.planName} (#${pol.policyNumber})", fontWeight = FontWeight.Bold)
                                                    Text("Premium: ₹${"%,.0f".format(pol.premiumAmount)} • Due: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            },
                                            onClick = {
                                                selectedPolicy = pol
                                                showPolDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Due Cycle & Allocation Summary Card
            if (selectedPolicy != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("record_payment_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PAYMENT STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            PaymentStatusChip(status = previewStatus)
                        }

                        val alreadyPaid = advanceAdjusted + directPaidInCurrentCycle
                        val isOverpaid = previewNextCycleAdvance > 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1: Premium Due
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Premium Due",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${"%,.0f".format(installmentAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Column 2: Already Paid
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (advanceAdjusted > 0 && directPaidInCurrentCycle == 0.0) "Adv. Adjusted" else "Already Paid",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        color = if (advanceAdjusted > 0) Color(0xFFA855F7) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${"%,.0f".format(alreadyPaid)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = if (alreadyPaid > 0) EmeraldGreenSecondary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Column 3: New Payment
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "New Payment",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${"%,.0f".format(enteredAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = if (enteredAmount > 0) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Column 4: Outstanding / Next Adv.
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = if (isOverpaid) "Next Adv." else "Outstanding",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        color = if (isOverpaid) Color(0xFFA855F7) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isOverpaid) "₹${"%,.0f".format(previewNextCycleAdvance)}" else "₹${"%,.0f".format(previewOutstanding)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = when {
                                            isOverpaid -> Color(0xFFA855F7)
                                            previewOutstanding > 0 -> ErrorRed
                                            else -> EmeraldGreenSecondary
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        if (advanceAdjusted > 0 && directPaidInCurrentCycle > 0) {
                            Text(
                                text = "Includes ₹${"%,.0f".format(advanceAdjusted)} advance + ₹${"%,.0f".format(directPaidInCurrentCycle)} direct paid",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }

            // 4. Amount Received Inputs
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("3. Payment Details", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { newValue ->
                            val cleaned = newValue.filter { it.isDigit() || it == '.' }
                            amountStr = cleaned
                            errorMessage = null
                        },
                        label = { Text("Amount Received (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        placeholder = { Text("Enter payment amount (e.g. 3000)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("record_payment_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val quickBaseAmount = if (currentRemainingBeforeNew > 0) currentRemainingBeforeNew else installmentAmount
                    if (quickBaseAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Full" to quickBaseAmount, "Half" to (quickBaseAmount / 2), "Quarter" to (quickBaseAmount / 4)).forEach { (label, amt) ->
                                val isChipSelected = (enteredAmount == amt)
                                FilterChip(
                                    selected = isChipSelected,
                                    onClick = {
                                        amountStr = amt.toInt().toString()
                                        errorMessage = null
                                    },
                                    label = { Text("$label (₹${amt.toInt()})", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (isAmountExceeding) {
                        Text(
                            text = "Note: Amount exceeds current outstanding balance (₹${"%,.0f".format(currentRemainingBeforeNew)}). Excess ₹${"%,.0f".format(enteredAmount - currentRemainingBeforeNew)} will be carried forward as advance.",
                            color = AccentOrange,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Payment Date
                    OutlinedTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it; errorMessage = null },
                        label = { Text("Payment Date (YYYY-MM-DD)") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Payment Mode
                    Text("Payment Mode", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modeOptions.forEach { mode ->
                            val isModeSelected = (selectedMode == mode)
                            FilterChip(
                                selected = isModeSelected,
                                onClick = { selectedMode = mode },
                                label = { Text(mode) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (selectedMode == "Cheque") {
                        OutlinedTextField(
                            value = chequeNumber,
                            onValueChange = { chequeNumber = it },
                            label = { Text("Cheque Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text("Bank Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = chequeDate,
                            onValueChange = { chequeDate = it },
                            label = { Text("Cheque Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedMode == "UPI" || selectedMode == "Bank") {
                        OutlinedTextField(
                            value = utrReference,
                            onValueChange = { utrReference = it },
                            label = { Text("Reference / UTR Number") },
                            placeholder = { Text("e.g. UTR982348921") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Remarks
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Remarks / Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
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
    
    // Initial selected customer
    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(
            if (policy != null) customersList.find { it.id == policy.customerId }
            else customersList.firstOrNull()
        )
    }

    // State controlling if search box & customer list are open/visible
    var isCustomerPickerOpen by remember { mutableStateOf(selectedCustomer == null) }

    // Filter available policies for selected customer
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

    // When selectedCustomer changes, auto-select policy
    LaunchedEffect(selectedCustomer) {
        if (selectedCustomer != null) {
            val matchingPols = policiesList.filter { it.customerId == selectedCustomer!!.id }
            if (matchingPols.isNotEmpty() && (selectedPolicy == null || selectedPolicy!!.customerId != selectedCustomer!!.id)) {
                selectedPolicy = matchingPols.first()
            }
        }
    }

    val paymentsForPolicy = remember(selectedPolicy, existingPayments) {
        if (selectedPolicy != null) {
            existingPayments.filter { it.policyId == selectedPolicy!!.id }
        } else {
            emptyList()
        }
    }

    val currentSummary = remember(selectedPolicy, existingPayments) {
        if (selectedPolicy != null) {
            PaymentAllocationEngine.calculateCurrentDueSummary(selectedPolicy!!, existingPayments)
        } else null
    }

    val installmentAmount = selectedPolicy?.premiumAmount ?: 0.0
    val advanceAdjusted = currentSummary?.advanceAdjusted ?: 0.0
    val directPaidInCurrentCycle = currentSummary?.directPaid ?: 0.0
    val currentRemainingBeforeNew = currentSummary?.outstanding ?: installmentAmount

    var amountStr by remember {
        mutableStateOf(if (currentRemainingBeforeNew > 0) currentRemainingBeforeNew.toInt().toString() else installmentAmount.toInt().toString())
    }

    // Payment Date
    var paymentDate by remember {
        mutableStateOf(
            try {
                LocalDate.now().toString()
            } catch (e: Exception) {
                "2026-08-08"
            }
        )
    }

    // Payment Mode & Conditional details
    var selectedMode by remember { mutableStateOf("UPI") } // Cash, UPI, Bank, Cheque
    var chequeNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf(paymentDate) }
    var utrReference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")

    val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
    val isAmountExceeding = enteredAmount > currentRemainingBeforeNew && currentRemainingBeforeNew > 0
    val newRemainingBalance = (currentRemainingBeforeNew - enteredAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Record Premium",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                if (errorMessage != null) {
                    Surface(
                        color = ErrorRedContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
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

                // 1. CUSTOMER SELECTION COMPONENT
                Text(
                    "Customer",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (!isCustomerPickerOpen && selectedCustomer != null) {
                    // SHOW ONLY THE SELECTED CUSTOMER CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("selected_customer_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, RoyalBluePrimary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = RoyalBluePrimary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val initials = selectedCustomer!!.name
                                            .split(" ")
                                            .mapNotNull { it.firstOrNull()?.toString() }
                                            .take(2)
                                            .joinToString("")
                                            .uppercase()
                                            .ifEmpty { "C" }
                                        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = selectedCustomer!!.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Mobile: ${selectedCustomer!!.mobile.ifBlank { "N/A" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // CHANGE CUSTOMER BUTTON
                            OutlinedButton(
                                onClick = {
                                    isCustomerPickerOpen = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, RoyalBluePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBluePrimary),
                                modifier = Modifier.testTag("change_customer_button")
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    // SHOW SEARCH FIELD AND CUSTOMER LIST
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = customerSearchQuery,
                            onValueChange = { customerSearchQuery = it },
                            placeholder = { Text("Search customer name, mobile or policy...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBluePrimary) },
                            trailingIcon = {
                                if (customerSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { customerSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("record_customer_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Customer list results
                        val filteredCustomers = remember(customerSearchQuery, customersList) {
                            customersList.filter { cust ->
                                val matchesName = cust.name.contains(customerSearchQuery, ignoreCase = true)
                                val matchesMobile = cust.mobile.contains(customerSearchQuery)
                                val matchesPolicy = policiesList.any { pol -> pol.customerId == cust.id && pol.policyNumber.contains(customerSearchQuery, ignoreCase = true) }
                                matchesName || matchesMobile || matchesPolicy
                            }.distinctBy { it.id } // No duplicate customer entries!
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No matching customers found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    filteredCustomers.forEach { cust ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCustomer = cust
                                                    isCustomerPickerOpen = false // Automatically close/collapse list!
                                                    customerSearchQuery = ""
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = "Mobile: ${cust.mobile.ifBlank { "N/A" }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. POLICY SELECTOR
                if (selectedCustomer != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Policy Selection",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        if (availablePolicies.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "No policies found for this customer.",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (availablePolicies.size == 1) {
                            val pol = availablePolicies.first()
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("${pol.planName} • #${pol.policyNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Premium: ₹${"%,.0f".format(pol.premiumAmount)} • Due: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            // Multiple policies exist - show policy dropdown
                            var showPolDropdown by remember { mutableStateOf(false) }
                            Box {
                                OutlinedTextField(
                                    value = selectedPolicy?.let { "${it.planName} (#${it.policyNumber})" } ?: "Select Policy",
                                    onValueChange = {},
                                    readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPolDropdown = !showPolDropdown }) {
                                            Icon(if (showPolDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showPolDropdown = !showPolDropdown },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                DropdownMenu(
                                    expanded = showPolDropdown,
                                    onDismissRequest = { showPolDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    availablePolicies.forEach { pol ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("${pol.planName} (#${pol.policyNumber})", fontWeight = FontWeight.Bold)
                                                    Text("Premium: ₹${"%,.0f".format(pol.premiumAmount)} • Due: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            },
                                            onClick = {
                                                selectedPolicy = pol
                                                showPolDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. COMPACT SUMMARY ROW: Premium Due | Already Paid | New Payment | Outstanding
                val totalAllocated = advanceAdjusted + directPaidInCurrentCycle + enteredAmount
                val previewOutstanding = kotlin.math.max(0.0, installmentAmount - totalAllocated)
                val previewNextCycleAdvance = kotlin.math.max(0.0, totalAllocated - installmentAmount)

                val previewStatus = when {
                    totalAllocated == 0.0 -> PaymentRowStatus.PENDING
                    totalAllocated < installmentAmount -> PaymentRowStatus.PARTIAL
                    totalAllocated == installmentAmount -> PaymentRowStatus.PAID
                    else -> PaymentRowStatus.OVERPAID
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_payment_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PAYMENT STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            PaymentStatusChip(status = previewStatus)
                        }

                        val alreadyPaid = advanceAdjusted + directPaidInCurrentCycle
                        val isOverpaid = previewNextCycleAdvance > 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Col 1: Premium Due
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    "Premium Due",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "₹${"%,.0f".format(installmentAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                    maxLines = 1
                                )
                            }

                            // Col 2: Already Paid
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (advanceAdjusted > 0 && directPaidInCurrentCycle == 0.0) "Adv. Adjusted" else "Already Paid",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (advanceAdjusted > 0) Color(0xFFA855F7) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "₹${"%,.0f".format(alreadyPaid)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (alreadyPaid > 0) EmeraldGreenSecondary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Col 3: New Payment
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "New Payment",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "₹${"%,.0f".format(enteredAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (enteredAmount > 0) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Col 4: Outstanding / Next Adv.
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    if (isOverpaid) "Next Adv." else "Outstanding",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (isOverpaid) Color(0xFFA855F7) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (isOverpaid) "₹${"%,.0f".format(previewNextCycleAdvance)}" else "₹${"%,.0f".format(previewOutstanding)}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = when {
                                            isOverpaid -> Color(0xFFA855F7)
                                            previewOutstanding > 0 -> ErrorRed
                                            else -> EmeraldGreenSecondary
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        if (advanceAdjusted > 0 && directPaidInCurrentCycle > 0) {
                            Text(
                                "Includes ₹${"%,.0f".format(advanceAdjusted)} advance + ₹${"%,.0f".format(directPaidInCurrentCycle)} direct paid",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                // 4. AMOUNT RECEIVED & QUICK CHIPS
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Amount Received",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { newValue ->
                            val cleaned = newValue.filter { it.isDigit() || it == '.' }
                            amountStr = cleaned
                            errorMessage = null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("Enter payment amount (e.g. 3000)") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_amount_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick Amount Chips
                    val quickBaseAmountDialog = if (currentRemainingBeforeNew > 0) currentRemainingBeforeNew else installmentAmount
                    if (quickBaseAmountDialog > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Full" to quickBaseAmountDialog,
                                "Half" to (quickBaseAmountDialog / 2),
                                "Quarter" to (quickBaseAmountDialog / 4)
                            ).forEach { (label, amt) ->
                                val isChipSelected = (enteredAmount == amt)
                                FilterChip(
                                    selected = isChipSelected,
                                    onClick = {
                                        amountStr = amt.toInt().toString()
                                        errorMessage = null
                                    },
                                    label = { Text("$label (₹${amt.toInt()})", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Exceeds warning helper text
                    if (isAmountExceeding) {
                        Text(
                            text = "Note: Amount exceeds current outstanding balance (₹${"%,.0f".format(currentRemainingBeforeNew)}). Excess ₹${"%,.0f".format(enteredAmount - currentRemainingBeforeNew)} will be recorded as Advance.",
                            color = AccentOrange,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 5. PAYMENT DATE
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Payment Date",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it; errorMessage = null },
                        placeholder = { Text("YYYY-MM-DD") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 6. PAYMENT MODE SELECTOR & CONDITIONAL EXTRA FIELDS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Payment Mode",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modeOptions.forEach { mode ->
                            val isModeSelected = (selectedMode == mode)
                            FilterChip(
                                selected = isModeSelected,
                                onClick = { selectedMode = mode },
                                label = { Text(mode, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Normal)) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Conditional Extra Fields based on Mode
                    if (selectedMode == "Cheque") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Cheque Details", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                                OutlinedTextField(
                                    value = chequeNumber,
                                    onValueChange = { chequeNumber = it },
                                    label = { Text("Cheque Number") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = bankName,
                                    onValueChange = { bankName = it },
                                    label = { Text("Bank Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = chequeDate,
                                    onValueChange = { chequeDate = it },
                                    label = { Text("Cheque Date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    } else if (selectedMode == "UPI" || selectedMode == "Bank") {
                        OutlinedTextField(
                            value = utrReference,
                            onValueChange = { utrReference = it },
                            label = { Text("Reference / UTR Number") },
                            placeholder = { Text("e.g. UTR1892049284") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // 7. REMARKS / NOTES
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Remarks",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Add notes or comments...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    if (selectedCustomer == null) {
                        errorMessage = "Please select a valid customer."
                        return@Button
                    }
                    if (selectedPolicy == null) {
                        errorMessage = "Please select a valid policy."
                        return@Button
                    }
                    if (enteredAmount <= 0) {
                        errorMessage = "Please enter a valid payment amount greater than ₹0."
                        return@Button
                    }

                    val fullNotes = buildString {
                        if (notes.isNotBlank()) append(notes)
                        if (selectedMode == "Cheque" && chequeNumber.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append("Cheque #: $chequeNumber, Bank: $bankName, Date: $chequeDate")
                        }
                        if ((selectedMode == "UPI" || selectedMode == "Bank") && utrReference.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append("Ref/UTR: $utrReference")
                        }
                    }

                    val generatedReceiptNo = "REC-${System.currentTimeMillis()}"

                    isSaving = true
                    if (onSavePayment != null) {
                        onSavePayment(selectedPolicy!!, enteredAmount, selectedMode, paymentDate, fullNotes)
                    } else {
                        onCollect(enteredAmount, 0.0, selectedMode, generatedReceiptNo, fullNotes)
                    }
                    onDismiss()
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_payment_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Payment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
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
fun PaymentDetailsDialog(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    customer: CustomerEntity?,
    agentName: String,
    agencyCode: String,
    branch: String,
    onDismiss: () -> Unit,
    onViewReceipt: () -> Unit,
    onShareReceipt: () -> Unit,
    onWhatsAppReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dueAmount = policy?.premiumAmount ?: payment.paidAmount
    val calc = calculatePaymentStatus(dueAmount, payment.paidAmount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Payment Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = RoyalBlueContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = payment.customerName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                            )
                            PaymentStatusChip(status = calc.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Policy #${payment.policyNumber}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ReceiptDetailRow("Date", payment.paymentDate)
                ReceiptDetailRow("Policy Number", payment.policyNumber)
                ReceiptDetailRow("Due Amount", "₹${"%.2f".format(calc.dueAmount)}")
                ReceiptDetailRow("Paid Amount", "₹${"%.2f".format(calc.paidAmount)}", isHighlight = true)
                ReceiptDetailRow("Outstanding", "₹${"%.2f".format(calc.balance)}")
                ReceiptDetailRow("Advance", "₹${"%.2f".format(calc.advance)}")
                ReceiptDetailRow("Payment Mode", payment.paymentMode)
                ReceiptDetailRow("Status", calc.status.label)
                ReceiptDetailRow("Receipt Number", payment.receiptNumber.ifEmpty { "REC-${payment.id}" })
                if (payment.notes.isNotBlank()) {
                    ReceiptDetailRow("Remarks", payment.notes)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text("Quick Actions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewReceipt,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Receipt", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onShareReceipt,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Share", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onWhatsAppReceipt,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("WhatsApp", fontSize = 11.sp, color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, color = ErrorRed)
                    }
                }
            }
        },
        confirmButton = {}
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

@Composable
fun PolicySummaryCard(
    summary: PolicyPaymentSummary,
    onCardClick: () -> Unit,
    onAddPayment: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Name + Policy No + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val initials = if (summary.customerName.isNotBlank()) {
                        summary.customerName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                    } else "P"
                    Surface(
                        shape = CircleShape,
                        color = RoyalBluePrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Policy No: ${summary.policyNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                PaymentStatusChip(status = summary.status)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Details Grid: Total Due | Total Paid | Balance | Advance/Excess
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.0f".format(summary.totalDue)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("Total Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.0f".format(summary.totalPaid)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                }
                Column {
                    Text("Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.0f".format(summary.balance)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (summary.balance > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant))
                }
                if (summary.advance > 0 || summary.status == PaymentRowStatus.OVERPAID) {
                    Column {
                        Text("Advance/Excess", style = MaterialTheme.typography.labelSmall, color = Color(0xFF512DA8))
                        Text("₹${"%.0f".format(summary.advance)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF512DA8)))
                    }
                } else {
                    Column {
                        Text("Last Payment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(summary.lastPaymentDate, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddPayment,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Record Payment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onCardClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("View Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun PolicyPaymentDetailsDialog(
    summary: PolicyPaymentSummary,
    agentName: String,
    agencyCode: String,
    branch: String,
    onDismiss: () -> Unit,
    onAddPayment: () -> Unit,
    onShareAllPdf: (PolicyPaymentSummary) -> Unit,
    onViewReceipt: (PaymentEntity) -> Unit,
    onShareReceipt: (PaymentEntity) -> Unit,
    onWhatsAppReceipt: (PaymentEntity) -> Unit,
    onEditPayment: (PaymentEntity) -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit
) {
    val context = LocalContext.current
    var showTopMenu by remember { mutableStateOf(false) }

    // Chronologically sort payments for row-by-row cumulative balance calculation
    val paymentsChronological = remember(summary.payments) {
        summary.payments.sortedBy { it.paymentDate }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.customerName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Policy No: ${summary.policyNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                PaymentStatusChip(status = summary.status)
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { showTopMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share All Payment History PDF", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = RoyalBluePrimary) },
                            onClick = {
                                showTopMenu = false
                                onShareAllPdf(summary)
                            }
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Box
                Surface(
                    color = RoyalBlueContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Due", style = MaterialTheme.typography.labelSmall, color = RoyalBluePrimary)
                            Text("₹${"%.0f".format(summary.totalDue)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Paid", style = MaterialTheme.typography.labelSmall, color = EmeraldGreenSecondary)
                            Text("₹${"%.0f".format(summary.totalPaid)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Balance", style = MaterialTheme.typography.labelSmall, color = if (summary.balance > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%.0f".format(summary.balance)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (summary.balance > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        if (summary.advance > 0 || summary.status == PaymentRowStatus.OVERPAID) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Advance/Excess", style = MaterialTheme.typography.labelSmall, color = Color(0xFF512DA8))
                                Text("₹${"%.0f".format(summary.advance)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF512DA8)))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payment History (${summary.payments.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onShareAllPdf(summary) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBluePrimary),
                            border = BorderStroke(1.dp, RoyalBluePrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onAddPayment,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Payment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (summary.payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No payments recorded yet for this policy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Scrollable Table for Payments
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .background(RoyalBluePrimary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("S.No", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Date", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Due Amount", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Paid Amount", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Balance", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Advance/Excess", modifier = Modifier.width(105.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Mode", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Status", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Actions", modifier = Modifier.width(140.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Table Rows (Chronological)
                                var cumulativePaid = 0.0
                                paymentsChronological.forEachIndexed { index, payment ->
                                    cumulativePaid += payment.paidAmount
                                    val rowCalc = calculatePaymentStatus(summary.totalDue, cumulativePaid)

                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        Text(payment.paymentDate, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                                        Text("₹${"%.0f".format(summary.totalDue)}", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                                        Text("₹${"%.0f".format(payment.paidAmount)}", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                        Text("₹${"%.0f".format(rowCalc.balance)}", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (rowCalc.balance > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant))
                                        Text("₹${"%.0f".format(rowCalc.advance)}", modifier = Modifier.width(105.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (rowCalc.advance > 0) Color(0xFF512DA8) else MaterialTheme.colorScheme.onSurfaceVariant))
                                        Text(payment.paymentMode, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall)
                                        Box(modifier = Modifier.width(80.dp)) {
                                            PaymentStatusChip(status = rowCalc.status)
                                        }
                                        Row(
                                            modifier = Modifier.width(140.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { onViewReceipt(payment) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { onShareReceipt(payment) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Share, contentDescription = "Share", tint = RoyalBlueDark, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { onWhatsAppReceipt(payment) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { onEditPayment(payment) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { onDeletePayment(payment) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    if (index < paymentsChronological.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

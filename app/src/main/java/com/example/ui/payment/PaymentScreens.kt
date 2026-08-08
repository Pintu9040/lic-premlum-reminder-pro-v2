package com.example.ui.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.util.NoMatchingRecordsEmptyState
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

    // Date filter state: ALL, TODAY, THIS_MONTH, PENDING, CUSTOM_DATE
    var activeDateFilter by remember { mutableStateOf(PaymentDashboardDateFilter.ALL) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    var isSearchExpanded by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var targetPolicyForCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var selectedCustomerForHistory by remember { mutableStateOf<CustomerEntity?>(null) }

    if (selectedCustomerForHistory != null) {
        CustomerPaymentHistoryScreen(
            customer = selectedCustomerForHistory!!,
            viewModel = viewModel,
            onBack = { selectedCustomerForHistory = null }
        )
        return
    }

    val context = LocalContext.current
    val todayDateFormatted = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
        } catch (e: Exception) {
            "Today"
        }
    }

    // Filter payments based on date, search query, mode, and pending status
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
        val currentMonth = today.monthValue
        val currentYear = today.year

        allPayments.filter { payment ->
            val matchingCust = allCustomers.find { it.id == payment.customerId }
            val matchingPol = allPolicies.find { it.id == payment.policyId }
            val custMobile = matchingCust?.mobile ?: ""
            val custWhatsapp = matchingCust?.whatsapp ?: ""
            val remainingBal = getRemainingBalanceForPayment(payment, matchingPol, allPayments)

            // Search by Name, Policy Number, Mobile Number, or Receipt Number
            val matchesQuery = searchQuery.isBlank() ||
                    payment.customerName.contains(searchQuery, ignoreCase = true) ||
                    payment.policyNumber.contains(searchQuery, ignoreCase = true) ||
                    payment.receiptNumber.contains(searchQuery, ignoreCase = true) ||
                    payment.notes.contains(searchQuery, ignoreCase = true) ||
                    custMobile.contains(searchQuery) ||
                    custWhatsapp.contains(searchQuery)

            val pDate = try { LocalDate.parse(payment.paymentDate) } catch (e: Exception) { null }

            val matchesFilter = when (activeDateFilter) {
                PaymentDashboardDateFilter.ALL -> true
                PaymentDashboardDateFilter.TODAY -> pDate?.isEqual(today) == true
                PaymentDashboardDateFilter.THIS_MONTH -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
                PaymentDashboardDateFilter.CUSTOM_DATE -> {
                    if (customStartDate != null && customEndDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate) && !pDate.isAfter(customEndDate)
                    } else if (customStartDate != null && pDate != null) {
                        !pDate.isBefore(customStartDate)
                    } else true
                }
                PaymentDashboardDateFilter.YESTERDAY -> pDate?.isEqual(today.minusDays(1)) == true
                PaymentDashboardDateFilter.THIS_WEEK -> {
                    val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                    pDate != null && !pDate.isBefore(startOfWeek) && !pDate.isAfter(today)
                }
            }

            val matchesMode = when (selectedModeFilter) {
                PaymentModeFilter.ALL -> true
                PaymentModeFilter.CASH -> payment.paymentMode.equals("Cash", ignoreCase = true)
                PaymentModeFilter.UPI -> payment.paymentMode.equals("UPI", ignoreCase = true)
                PaymentModeFilter.BANK_TRANSFER -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                PaymentModeFilter.CHEQUE -> payment.paymentMode.equals("Cheque", ignoreCase = true)
                PaymentModeFilter.ONLINE -> payment.paymentMode.contains("Online", ignoreCase = true) || payment.paymentMode.contains("Portal", ignoreCase = true)
            }

            matchesQuery && matchesFilter && matchesMode
        }.sortedByDescending { it.paymentDate }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    targetPolicyForCollection = null
                    showRecordPaymentDialog = true
                },
                containerColor = RoyalBluePrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Payment", modifier = Modifier.size(20.dp)) },
                text = { Text("Add Payment", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp,
                    hoveredElevation = 8.dp,
                    focusedElevation = 8.dp
                ),
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 16.dp)
                    .testTag("add_payment_fab")
            )
        }
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

                        // COMPACT FILTER CHIPS BELOW SEARCH BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chips = listOf(
                                PaymentDashboardDateFilter.ALL to "All",
                                PaymentDashboardDateFilter.TODAY to "Today",
                                PaymentDashboardDateFilter.THIS_MONTH to "This Month"
                            )

                            chips.forEach { (filterOption, label) ->
                                FilterChip(
                                    selected = activeDateFilter == filterOption,
                                    onClick = { activeDateFilter = filterOption },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            // Payment Mode / Custom Filter Button
                            FilterChip(
                                selected = selectedModeFilter != PaymentModeFilter.ALL || customStartDate != null,
                                onClick = { showCustomDateDialog = true },
                                label = { Text("Filters", style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                shape = RoundedCornerShape(20.dp)
                            )
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
                                text = "PAYMENT RECORDS (${filteredPayments.size})",
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
                    } else if (allPayments.isEmpty()) {
                        item {
                            PaymentDashboardEmptyState(
                                onCollectFirst = {
                                    targetPolicyForCollection = null
                                    showRecordPaymentDialog = true
                                }
                            )
                        }
                    } else if (filteredPayments.isEmpty()) {
                        item {
                            NoMatchingRecordsEmptyState(
                                query = searchQuery,
                                onResetFilters = {
                                    viewModel.clearAllFilters()
                                    activeDateFilter = PaymentDashboardDateFilter.ALL
                                }
                            )
                        }
                    } else {
                        // RECENT COLLECTIONS COMPACT CARDS
                        itemsIndexed(filteredPayments, key = { _, payment -> payment.id }) { index, payment ->
                            val matchingPolicy = allPolicies.find { it.id == payment.policyId }
                            val matchingCustomer = allCustomers.find { it.id == payment.customerId }
                            val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, allPayments)

                            val customerToPass = matchingCustomer ?: CustomerEntity(
                                id = payment.customerId,
                                name = payment.customerName,
                                mobile = "",
                                email = "",
                                address = "",
                                dob = "",
                                occupation = ""
                            )

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
                                onDelete = { deletingPayment = payment },
                                onCustomerClick = { selectedCustomerForHistory = customerToPass }
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
    onDelete: () -> Unit,
    onCustomerClick: ((CustomerEntity) -> Unit)? = null
) {
    val (modeIcon, modeColor) = when (payment.paymentMode.uppercase()) {
        "UPI" -> Icons.Default.QrCodeScanner to RoyalBluePrimary
        "CASH" -> Icons.Default.Payments to EmeraldGreenSecondary
        "CHEQUE" -> Icons.AutoMirrored.Filled.ReceiptLong to AccentOrange
        else -> Icons.Default.AccountBalance to RoyalBlueLight
    }

    val isFullyPaid = remainingBalance <= 0.0
    val isPartial = !isFullyPaid && payment.paidAmount > 0.0
    val statusText = when {
        isFullyPaid -> "Paid"
        isPartial -> "Partial"
        else -> "Pending"
    }

    val statusBgColor = when (statusText) {
        "Paid" -> EmeraldGreenContainer
        "Partial" -> AccentOrangeContainer
        else -> ErrorRedContainer
    }

    val statusTextColor = when (statusText) {
        "Paid" -> OnEmeraldGreenContainer
        "Partial" -> OnAccentOrangeContainer
        else -> ErrorRed
    }

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
                onCustomerClick?.invoke(fallbackCustomer)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Customer Name + Status Chip
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

                Surface(
                    color = statusBgColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor,
                            fontSize = 11.sp
                        )
                    )
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
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

                // 1. CUSTOMER SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Customer",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
                        placeholder = { Text("Select customer") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
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
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (showCustomerDropdown && customersList.isNotEmpty()) {
                        val filteredCustomers = customersList.filter {
                            it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                    it.mobile.contains(customerSearchQuery)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            shape = RoundedCornerShape(10.dp),
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
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
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

                // 2. POLICY SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Policy",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = selectedPolicy?.let { "${it.planName} (#${it.policyNumber})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select policy") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp)) },
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
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (showPolicyDropdown && availablePolicies.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            shape = RoundedCornerShape(10.dp),
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
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Policy, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
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

                // 3. COMPACT SUMMARY ROW: Premium Due | Paid Amount | Outstanding Balance
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Premium Due",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%.0f".format(installmentAmount)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Paid Amount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%.0f".format(paidInCurrentCycle)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Outstanding",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%.0f".format(newRemainingBalance)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (newRemainingBalance > 0) ErrorRed else EmeraldGreenSecondary
                                )
                            )
                        }
                    }
                }

                // 4. AMOUNT RECEIVED
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Amount Received",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            errorMessage = null
                        },
                        placeholder = { Text("Enter amount") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_amount_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
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

                // 6. PAYMENT MODE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Payment Mode",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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

                // 7. REMARKS
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Remarks",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Cheque No / Reference / Notes...") },
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_payment_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
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

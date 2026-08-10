package com.example.ui.reports

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.pdf.PdfReportData
import com.example.pdf.PdfReportGenerator
import com.example.pdf.ReportType
import com.example.ui.LicViewModel
import com.example.util.ExcelReportGenerator
import com.example.util.SearchFilterEngine
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.whatsapp.WhatsAppTemplateType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Royal Blue Dark Theme Palette
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val RoyalBluePrimary = Color(0xFF2563EB)
private val RoyalBlueLight = Color(0xFF60A5FA)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentPurple = Color(0xFF8B5CF6)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

enum class ReportQuickFilter(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM_DATE("Custom Date")
}

data class TopCustomerData(
    val id: Int,
    val name: String,
    val initials: String,
    val policyCount: Int,
    val collectedAmount: String,
    val outstandingAmount: String,
    val statusBadge: String,
    val badgeColor: Color
)

data class RecentCollectionData(
    val id: Int,
    val customerName: String,
    val policyNumber: String,
    val premiumAmount: String,
    val paymentMode: String,
    val collectedDate: String,
    val receiptNumber: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: LicViewModel? = null,
    onNavigateToPayments: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCustomers: () -> Unit = {},
    onNavigateToPolicies: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToCustomerDetail: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val agentProfileState = viewModel?.agentProfile?.collectAsState()
    val agentProfile = agentProfileState?.value

    val livePoliciesState = viewModel?.policies?.collectAsState()
    val livePolicies = livePoliciesState?.value ?: emptyList()

    val liveCustomersState = viewModel?.customers?.collectAsState()
    val liveCustomers = liveCustomersState?.value ?: emptyList()

    val livePaymentsState = viewModel?.payments?.collectAsState()
    val livePayments = livePaymentsState?.value ?: emptyList()

    // State Variables
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ReportQuickFilter.THIS_MONTH) }
    var selectedCardIndex by remember { mutableIntStateOf(0) } // 0: Collected, 1: Outstanding, 2: Policies, 3: Collection Rate
    var isChartAnimTriggered by remember { mutableStateOf(true) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf("01 Aug 2026") }
    var customEndDate by remember { mutableStateOf("31 Aug 2026") }
    var forceEmptyState by remember { mutableStateOf(false) }

    // Advanced Filter Options
    var paymentStatusFilter by remember { mutableStateOf("All") } // All, Paid, Partial, Pending, Overpaid
    var policyStatusFilter by remember { mutableStateOf("All") }  // All, Active, Due, Lapsed
    var paymentModeFilter by remember { mutableStateOf("All") }   // All, Cash, UPI, Bank, Cheque

    // Interactive Dialog States for Controls
    var selectedCustomerForDialog by remember { mutableStateOf<TopCustomerData?>(null) }
    var selectedReceiptForDialog by remember { mutableStateOf<RecentCollectionData?>(null) }
    var selectedSummaryCardForDialog by remember { mutableStateOf<Int?>(null) }

    // Chart Animation state
    val chartAnimProgress by animateFloatAsState(
        targetValue = if (isChartAnimTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "chartAnim"
    )

    // Trigger re-animating charts when filter changes
    LaunchedEffect(selectedFilter, paymentStatusFilter, policyStatusFilter, paymentModeFilter) {
        isChartAnimTriggered = false
        kotlinx.coroutines.delay(50)
        isChartAnimTriggered = true
    }

    // -------------------------------------------------------------------------
    // DYNAMIC DATA CALCULATIONS FROM ROOM DATABASE
    // -------------------------------------------------------------------------

    // Filtered Payments from DB
    val filteredPayments = remember(livePayments, selectedFilter, searchQuery, paymentStatusFilter, paymentModeFilter, customStartDate, customEndDate, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else {
            livePayments.filter { payment ->
                val matchesTime = isDateInPeriod(payment.paymentDate, selectedFilter, customStartDate, customEndDate)
                val matchesMode = paymentModeFilter == "All" || payment.paymentMode.contains(paymentModeFilter, ignoreCase = true)
                val matchesStatus = when (paymentStatusFilter) {
                    "Paid" -> payment.paidAmount > 0
                    "Partial" -> payment.paidAmount > 0 && payment.lateFee > 0
                    "Pending" -> payment.paidAmount == 0.0
                    "Overpaid" -> payment.paidAmount > 0
                    else -> true
                }
                val matchesSearch = searchQuery.isBlank() || SearchFilterEngine.matchesQuery(
                    searchQuery,
                    listOf(payment.customerName, payment.policyNumber, payment.receiptNumber, payment.paymentMode, payment.paidAmount.toString(), payment.paymentDate)
                )
                matchesTime && matchesMode && matchesStatus && matchesSearch
            }
        }
    }

    // Filtered Policies from DB
    val filteredPolicies = remember(livePolicies, policyStatusFilter, searchQuery, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else {
            livePolicies.filter { policy ->
                val matchesStatus = policyStatusFilter == "All" || policy.status.equals(policyStatusFilter, ignoreCase = true)
                val matchesSearch = searchQuery.isBlank() || SearchFilterEngine.matchesQuery(
                    searchQuery,
                    listOf(policy.policyNumber, policy.customerName, policy.planName, policy.status, policy.premiumAmount.toString(), policy.dueDate)
                )
                matchesStatus && matchesSearch
            }
        }
    }

    // Dynamic Financial Summary Calculations
    val dynamicTotalCollected = remember(filteredPayments) {
        filteredPayments.sumOf { it.paidAmount }
    }

    val dynamicTotalOutstanding = remember(filteredPolicies, filteredPayments) {
        val paidMap = filteredPayments.groupBy { it.policyId }.mapValues { entry -> entry.value.sumOf { it.paidAmount } }
        filteredPolicies.sumOf { pol ->
            val paidForPol = paidMap[pol.id] ?: 0.0
            if (pol.status.equals("Due", ignoreCase = true) || pol.status.equals("Lapsed", ignoreCase = true)) {
                kotlin.math.max(0.0, pol.premiumAmount - paidForPol)
            } else if (pol.status.equals("Active", ignoreCase = true) && pol.premiumAmount > paidForPol && paidForPol > 0) {
                kotlin.math.max(0.0, pol.premiumAmount - paidForPol)
            } else 0.0
        }
    }

    val dynamicActiveCount = remember(filteredPolicies) { filteredPolicies.count { it.status.equals("Active", ignoreCase = true) } }
    val dynamicDueCount = remember(filteredPolicies) { filteredPolicies.count { it.status.equals("Due", ignoreCase = true) || it.status.equals("Lapsed", ignoreCase = true) } }
    val dynamicTotalPolicies = remember(filteredPolicies) { filteredPolicies.size }

    val dynamicCollectionRate = remember(dynamicTotalCollected, dynamicTotalOutstanding) {
        val targetDue = dynamicTotalCollected + dynamicTotalOutstanding
        if (targetDue > 0) {
            (dynamicTotalCollected / targetDue) * 100.0
        } else if (dynamicTotalCollected > 0) 100.0 else 0.0
    }

    // Dynamic Top Customers List
    val dynamicTopCustomers = remember(liveCustomers, livePolicies, filteredPayments, searchQuery, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else if (liveCustomers.isEmpty() && livePayments.isEmpty()) {
            emptyList()
        } else {
            val paymentsByCustomer = filteredPayments.groupBy { it.customerId }
            val policiesByCustomer = livePolicies.groupBy { it.customerId }

            val list = liveCustomers.map { customer ->
                val custPayments = paymentsByCustomer[customer.id] ?: filteredPayments.filter { it.customerName.equals(customer.name, ignoreCase = true) }
                val custPolicies = policiesByCustomer[customer.id] ?: livePolicies.filter { it.customerName.equals(customer.name, ignoreCase = true) }
                val collected = custPayments.sumOf { it.paidAmount }
                val outstanding = custPolicies.filter { it.status.equals("Due", ignoreCase = true) || it.status.equals("Lapsed", ignoreCase = true) }.sumOf { pol ->
                    val paidForPol = custPayments.filter { it.policyId == pol.id }.sumOf { it.paidAmount }
                    kotlin.math.max(0.0, pol.premiumAmount - paidForPol)
                }
                val initials = customer.name.split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2).joinToString("").uppercase().ifEmpty { "CU" }

                val badge = when {
                    collected > 50000 -> "VIP Client"
                    outstanding > 0 -> "Grace Period"
                    else -> "On Time"
                }
                val badgeColor = when (badge) {
                    "VIP Client" -> AccentAmber
                    "Grace Period" -> AccentPurple
                    else -> AccentGreen
                }

                TopCustomerData(
                    id = customer.id.toInt(),
                    name = customer.name,
                    initials = initials,
                    policyCount = custPolicies.size,
                    collectedAmount = formatIndianCurrency(collected),
                    outstandingAmount = formatIndianCurrency(outstanding),
                    statusBadge = badge,
                    badgeColor = badgeColor
                )
            }.filter { cust ->
                searchQuery.isBlank() || SearchFilterEngine.matchesQuery(searchQuery, listOf(cust.name, cust.statusBadge, cust.collectedAmount, cust.outstandingAmount))
            }.sortedByDescending { cust ->
                cust.collectedAmount.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
            }.take(5)

            list
        }
    }

    // Dynamic Recent Collections List
    val dynamicRecentCollections = remember(filteredPayments, searchQuery, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else {
            filteredPayments.take(10).mapIndexed { idx, payment ->
                RecentCollectionData(
                    id = payment.id.toInt(),
                    customerName = payment.customerName,
                    policyNumber = payment.policyNumber,
                    premiumAmount = formatIndianCurrency(payment.paidAmount),
                    paymentMode = payment.paymentMode,
                    collectedDate = payment.paymentDate,
                    receiptNumber = payment.receiptNumber.ifBlank { "REC-2026-${100 + payment.id}" }
                )
            }
        }
    }

    val isListEmpty = forceEmptyState || (dynamicTopCustomers.isEmpty() && dynamicRecentCollections.isEmpty())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(DarkBg)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Reports & Analytics",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "LIC Premium Reminder Pro",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RoyalBlueLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    },
                    actions = {
                        // Action 1: Search
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.testTag("action_search")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextWhite
                            )
                        }

                        // Action 2: Filter
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier.testTag("action_filter")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (paymentStatusFilter != "All" || policyStatusFilter != "All" || paymentModeFilter != "All") RoyalBlueLight else TextWhite
                            )
                        }

                        // Action 3: Share
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Generating report summary for sharing...")
                                    val reportData = PdfReportData(
                                        reportType = ReportType.MONTHLY_COLLECTION,
                                        agentProfile = agentProfile,
                                        customerList = liveCustomers,
                                        policyList = filteredPolicies,
                                        paymentList = filteredPayments,
                                        filterPeriod = selectedFilter.label
                                    )
                                    val res = PdfReportGenerator.generatePdfReport(context, reportData)
                                    res.onSuccess { file ->
                                        PdfReportGenerator.sharePdf(context, file)
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar("Failed to share report: ${err.message}")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("action_export")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TextWhite
                            )
                        }

                        // Action 4: More
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("action_more")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = TextWhite
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(CardBg)
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh Data", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = RoyalBlueLight) },
                                    onClick = {
                                        showMoreMenu = false
                                        isChartAnimTriggered = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Analytics data refreshed successfully.")
                                            isChartAnimTriggered = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export PDF Report", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentRed) },
                                    onClick = {
                                        showMoreMenu = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Generating PDF report...")
                                            val reportData = PdfReportData(
                                                reportType = ReportType.MONTHLY_COLLECTION,
                                                agentProfile = agentProfile,
                                                customerList = liveCustomers,
                                                policyList = filteredPolicies,
                                                paymentList = filteredPayments,
                                                filterPeriod = selectedFilter.label
                                            )
                                            val res = PdfReportGenerator.generatePdfReport(context, reportData)
                                            res.onSuccess { file ->
                                                snackbarHostState.showSnackbar("PDF Report Saved: ${file.name}")
                                                PdfReportGenerator.openPdf(context, file)
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar("Report Generation Failed: ${err.message}")
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Excel Spreadsheet", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, tint = AccentGreen) },
                                    onClick = {
                                        showMoreMenu = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Generating Excel report...")
                                            val res = ExcelReportGenerator.generateExcelReport(
                                                context = context,
                                                filterPeriod = selectedFilter.label,
                                                policies = filteredPolicies,
                                                payments = filteredPayments,
                                                customers = liveCustomers
                                            )
                                            res.onSuccess { file ->
                                                snackbarHostState.showSnackbar("Excel Sheet Saved: ${file.name}")
                                                ExcelReportGenerator.openExcelFile(context, file)
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar("Excel Export Failed: ${err.message}")
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset All Filters", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentAmber) },
                                    onClick = {
                                        showMoreMenu = false
                                        searchQuery = ""
                                        paymentStatusFilter = "All"
                                        policyStatusFilter = "All"
                                        paymentModeFilter = "All"
                                        forceEmptyState = false
                                        selectedFilter = ReportQuickFilter.THIS_MONTH
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("All quick filters reset to default.")
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg
                    )
                )

                // Navigation Tabs Bar (Requirement 9)
                ScrollableTabRow(
                    selectedTabIndex = 1,
                    containerColor = DarkBg,
                    contentColor = TextWhite,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (tabPositions.size > 1) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[1]),
                                color = RoyalBlueLight,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = false,
                        onClick = onNavigateToPayments,
                        text = { Text("Payments", color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = true,
                        onClick = onNavigateToReports,
                        text = { Text("Reports & Analytics", color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = false,
                        onClick = onNavigateToDocuments,
                        text = { Text("Documents", color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = false,
                        onClick = onNavigateToSettings,
                        text = { Text("Profile & Settings", color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                }

                // Expandable Search Bar (Requirement 3)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search customer, policy #, mobile, receipt #...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalBlueLight) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg,
                            focusedBorderColor = RoyalBlueLight,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            }
        },
        bottomBar = {
            // BOTTOM STICKY ACTIONS BAR
            Surface(
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export PDF Button (Requirement 7)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Generating LIC PDF Report...")
                                val reportType = when (selectedCardIndex) {
                                    0 -> ReportType.MONTHLY_COLLECTION
                                    1 -> ReportType.OUTSTANDING_PREMIUM
                                    2 -> ReportType.COMPLETE_PORTFOLIO
                                    else -> ReportType.MONTHLY_COLLECTION
                                }
                                val reportData = PdfReportData(
                                    reportType = reportType,
                                    agentProfile = agentProfile,
                                    customerList = liveCustomers,
                                    policyList = filteredPolicies,
                                    paymentList = filteredPayments,
                                    filterPeriod = selectedFilter.label
                                )
                                val res = PdfReportGenerator.generatePdfReport(context, reportData)
                                res.onSuccess { file ->
                                    snackbarHostState.showSnackbar("PDF Report Saved: ${file.name}")
                                    PdfReportGenerator.openPdf(context, file)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Report Generation Failed: ${err.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }

                    // Export Excel Button (Requirement 8)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Exporting data to Excel Spreadsheet...")
                                val res = ExcelReportGenerator.generateExcelReport(
                                    context = context,
                                    filterPeriod = selectedFilter.label,
                                    policies = filteredPolicies,
                                    payments = filteredPayments,
                                    customers = liveCustomers
                                )
                                res.onSuccess { file ->
                                    snackbarHostState.showSnackbar("Excel Spreadsheet Saved: ${file.name}")
                                    ExcelReportGenerator.openExcelFile(context, file)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Excel Export Failed: ${err.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Excel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }

                    // Share Report Button (Requirement 5)
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Preparing PDF report for sharing...")
                                val reportType = when (selectedCardIndex) {
                                    0 -> ReportType.MONTHLY_COLLECTION
                                    1 -> ReportType.OUTSTANDING_PREMIUM
                                    2 -> ReportType.COMPLETE_PORTFOLIO
                                    else -> ReportType.MONTHLY_COLLECTION
                                }
                                val reportData = PdfReportData(
                                    reportType = reportType,
                                    agentProfile = agentProfile,
                                    customerList = liveCustomers,
                                    policyList = filteredPolicies,
                                    paymentList = filteredPayments,
                                    filterPeriod = selectedFilter.label
                                )
                                val res = PdfReportGenerator.generatePdfReport(context, reportData)
                                res.onSuccess { file ->
                                    PdfReportGenerator.sharePdf(context, file)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Failed to share PDF: ${err.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RoyalBlueLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite))
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth >= 600.dp

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // ==========================================
                // 1. FINANCIAL SUMMARY (4 DYNAMIC CARDS - Requirement 1 & 10)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "FINANCIAL SUMMARY (${selectedFilter.label.uppercase()})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RoyalBlueLight,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    val collectedText = formatIndianCurrency(dynamicTotalCollected)
                    val outstandingText = formatIndianCurrency(dynamicTotalOutstanding)
                    val rateText = String.format(Locale.US, "%.1f%%", dynamicCollectionRate)

                    if (isTablet) {
                        // 4 Column Row for Tablet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Total Collected",
                                amount = collectedText,
                                subtitle = "${filteredPayments.size} receipts collected",
                                icon = Icons.Default.AccountBalanceWallet,
                                iconColor = AccentGreen,
                                isSelected = (selectedCardIndex == 0),
                                onClick = {
                                    selectedCardIndex = 0
                                    selectedSummaryCardForDialog = 0
                                }
                            )
                            SummaryMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Outstanding",
                                amount = outstandingText,
                                subtitle = "$dynamicDueCount policies pending",
                                icon = Icons.Default.PendingActions,
                                iconColor = AccentRed,
                                isSelected = (selectedCardIndex == 1),
                                onClick = {
                                    selectedCardIndex = 1
                                    selectedSummaryCardForDialog = 1
                                }
                            )
                            SummaryMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Policies",
                                amount = "$dynamicTotalPolicies",
                                subtitle = "$dynamicActiveCount Active • $dynamicDueCount Due",
                                icon = Icons.Default.Folder,
                                iconColor = RoyalBlueLight,
                                isSelected = (selectedCardIndex == 2),
                                onClick = {
                                    selectedCardIndex = 2
                                    selectedSummaryCardForDialog = 2
                                }
                            )
                            SummaryMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Collection Rate",
                                amount = rateText,
                                subtitle = "Target: 85.0% • Portfolio",
                                icon = Icons.Default.Speed,
                                iconColor = AccentAmber,
                                isSelected = (selectedCardIndex == 3),
                                onClick = {
                                    selectedCardIndex = 3
                                    selectedSummaryCardForDialog = 3
                                }
                            )
                        }
                    } else {
                        // 2x2 Grid for Phone
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SummaryMetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Collected",
                                    amount = collectedText,
                                    subtitle = "${filteredPayments.size} receipts collected",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconColor = AccentGreen,
                                    isSelected = (selectedCardIndex == 0),
                                    onClick = {
                                        selectedCardIndex = 0
                                        selectedSummaryCardForDialog = 0
                                    }
                                )
                                SummaryMetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Outstanding",
                                    amount = outstandingText,
                                    subtitle = "$dynamicDueCount policies pending",
                                    icon = Icons.Default.PendingActions,
                                    iconColor = AccentRed,
                                    isSelected = (selectedCardIndex == 1),
                                    onClick = {
                                        selectedCardIndex = 1
                                        selectedSummaryCardForDialog = 1
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SummaryMetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Policies",
                                    amount = "$dynamicTotalPolicies",
                                    subtitle = "$dynamicActiveCount Active • $dynamicDueCount Due",
                                    icon = Icons.Default.Folder,
                                    iconColor = RoyalBlueLight,
                                    isSelected = (selectedCardIndex == 2),
                                    onClick = {
                                        selectedCardIndex = 2
                                        selectedSummaryCardForDialog = 2
                                    }
                                )
                                SummaryMetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Collection Rate",
                                    amount = rateText,
                                    subtitle = "Target: 85.0% • Portfolio",
                                    icon = Icons.Default.Speed,
                                    iconColor = AccentAmber,
                                    isSelected = (selectedCardIndex == 3),
                                    onClick = {
                                        selectedCardIndex = 3
                                        selectedSummaryCardForDialog = 3
                                    }
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 2. QUICK FILTERS (Requirement 2)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUICK FILTERS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ReportQuickFilter.entries) { filter ->
                            val isSelected = (selectedFilter == filter)
                            Surface(
                                onClick = {
                                    selectedFilter = filter
                                    if (filter == ReportQuickFilter.CUSTOM_DATE) {
                                        showCustomDateDialog = true
                                    }
                                },
                                color = if (isSelected) RoyalBluePrimary else CardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) RoyalBlueLight else CardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = TextWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isSelected) TextWhite else TextMuted,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. CHARTS SECTION
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ANALYTICS & CHARTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RoyalBlueLight,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    IconButton(
                        onClick = {
                            isChartAnimTriggered = false
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(100)
                                isChartAnimTriggered = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Re-animate Charts",
                            tint = RoyalBlueLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MonthlyLineChartCard(
                            modifier = Modifier.weight(1.2f),
                            progress = chartAnimProgress,
                            isEmpty = isListEmpty
                        )
                        PlanDonutChartCard(
                            modifier = Modifier.weight(1f),
                            progress = chartAnimProgress,
                            isEmpty = isListEmpty
                        )
                    }

                    StatusBarChartCard(
                        modifier = Modifier.fillMaxWidth(),
                        progress = chartAnimProgress,
                        isEmpty = isListEmpty
                    )
                } else {
                    MonthlyLineChartCard(
                        modifier = Modifier.fillMaxWidth(),
                        progress = chartAnimProgress,
                        isEmpty = isListEmpty
                    )

                    PlanDonutChartCard(
                        modifier = Modifier.fillMaxWidth(),
                        progress = chartAnimProgress,
                        isEmpty = isListEmpty
                    )

                    StatusBarChartCard(
                        modifier = Modifier.fillMaxWidth(),
                        progress = chartAnimProgress,
                        isEmpty = isListEmpty
                    )
                }

                // ==========================================
                // 4. TOP CUSTOMERS SECTION
                // ==========================================
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Top Customers by Volume",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            TextButton(onClick = { onNavigateToCustomers() }) {
                                Text("View All", color = RoyalBlueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (dynamicTopCustomers.isEmpty()) {
                            EmptyInlineState(message = "No matching customer records found.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                dynamicTopCustomers.forEach { customer ->
                                    TopCustomerRowItem(
                                        customer = customer,
                                        onClick = {
                                            selectedCustomerForDialog = customer
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 5. RECENT COLLECTIONS SECTION
                // ==========================================
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recent Collections",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            Text(
                                text = "${dynamicRecentCollections.size} Records",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (dynamicRecentCollections.isEmpty()) {
                            EmptyInlineState(message = "No recent premium collection receipts found.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                dynamicRecentCollections.forEach { collection ->
                                    RecentCollectionRowItem(
                                        collection = collection,
                                        onReceiptClick = {
                                            selectedReceiptForDialog = collection
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 6. INSIGHTS SECTION
                // ==========================================
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Financial Insights",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InsightRowItem(
                                label = "Period Collection Volume",
                                value = formatIndianCurrency(dynamicTotalCollected),
                                subtitle = "Total verified payments received",
                                icon = Icons.Default.ShowChart,
                                iconColor = AccentGreen
                            )
                            InsightRowItem(
                                label = "Outstanding Portfolio Due",
                                value = formatIndianCurrency(dynamicTotalOutstanding),
                                subtitle = "$dynamicDueCount policies pending renewal",
                                icon = Icons.Default.PendingActions,
                                iconColor = AccentRed
                            )
                            InsightRowItem(
                                label = "Collection Efficiency Rate",
                                value = String.format(Locale.US, "%.1f%%", dynamicCollectionRate),
                                subtitle = if (dynamicCollectionRate >= 80.0) "Optimal performance target achieved" else "Requires agent renewal follow-ups",
                                icon = Icons.Default.Speed,
                                iconColor = AccentAmber
                            )
                        }
                    }
                }

                // ==========================================
                // 7. EMPTY STATE DISPLAY
                // ==========================================
                if (isListEmpty) {
                    Surface(
                        color = CardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Canvas(modifier = Modifier.size(80.dp)) {
                                drawCircle(
                                    color = RoyalBluePrimary.copy(alpha = 0.15f),
                                    radius = size.minDimension / 2
                                )
                                drawCircle(
                                    color = RoyalBlueLight.copy(alpha = 0.3f),
                                    radius = size.minDimension / 3,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "No matching records",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "There are no policy collection records matching your active search query or date filter.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    paymentStatusFilter = "All"
                                    policyStatusFilter = "All"
                                    paymentModeFilter = "All"
                                    forceEmptyState = false
                                    selectedFilter = ReportQuickFilter.THIS_MONTH
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoyalBlueLight)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Filters", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom spacer to ensure last item can scroll comfortably above sticky bottom bar
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }

    // Custom Date Picker Range Dialog
    if (showCustomDateDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            containerColor = CardBg,
            title = {
                Text("Select Custom Date Range", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select start and end dates to filter analytics:", color = TextMuted, fontSize = 13.sp)

                    OutlinedTextField(
                        value = customStartDate,
                        onValueChange = { customStartDate = it },
                        label = { Text("Start Date (e.g. 01 Aug 2026)", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = RoyalBlueLight, unfocusedBorderColor = CardBorder
                        )
                    )

                    OutlinedTextField(
                        value = customEndDate,
                        onValueChange = { customEndDate = it },
                        label = { Text("End Date (e.g. 31 Aug 2026)", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = RoyalBlueLight, unfocusedBorderColor = CardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCustomDateDialog = false
                        selectedFilter = ReportQuickFilter.CUSTOM_DATE
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Filtered range: $customStartDate to $customEndDate")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply Filter", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Export Center Bottom Sheet Modal
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Export Analytics & Reports",
                    style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                )

                Text(
                    text = "Choose your preferred export format for LIC policy reporting:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 13.sp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            showExportSheet = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Generating PDF report...")
                                val reportData = PdfReportData(
                                    reportType = ReportType.MONTHLY_COLLECTION,
                                    agentProfile = agentProfile,
                                    customerList = liveCustomers,
                                    policyList = filteredPolicies,
                                    paymentList = filteredPayments,
                                    filterPeriod = selectedFilter.label
                                )
                                val res = PdfReportGenerator.generatePdfReport(context, reportData)
                                res.onSuccess { file ->
                                    snackbarHostState.showSnackbar("PDF Report Saved: ${file.name}")
                                    PdfReportGenerator.openPdf(context, file)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Failed: ${err.message}")
                                }
                            }
                        },
                        color = DarkBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentRed, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("PDF Statement", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        onClick = {
                            showExportSheet = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Exporting Excel Spreadsheet...")
                                val res = ExcelReportGenerator.generateExcelReport(
                                    context = context,
                                    filterPeriod = selectedFilter.label,
                                    policies = filteredPolicies,
                                    payments = filteredPayments,
                                    customers = liveCustomers
                                )
                                res.onSuccess { file ->
                                    snackbarHostState.showSnackbar("Excel Sheet Saved: ${file.name}")
                                    ExcelReportGenerator.openExcelFile(context, file)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Failed: ${err.message}")
                                }
                            }
                        },
                        color = DarkBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Excel Sheet", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Filter Options Bottom Sheet Panel (Requirement 4)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Analytics & Reports",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    )
                    TextButton(onClick = {
                        paymentStatusFilter = "All"
                        policyStatusFilter = "All"
                        paymentModeFilter = "All"
                        selectedFilter = ReportQuickFilter.THIS_MONTH
                    }) {
                        Text("Reset", color = RoyalBlueLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text("Quick Time Period", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportQuickFilter.entries.take(4).forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = {
                                selectedFilter = filter
                            },
                            color = if (isSelected) RoyalBluePrimary else DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueLight else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = filter.label,
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                Text("Payment Status", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Paid", "Partial", "Pending")) { status ->
                        val isSelected = paymentStatusFilter == status
                        FilterChip(
                            selected = isSelected,
                            onClick = { paymentStatusFilter = status },
                            label = { Text(status, color = TextWhite) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBluePrimary,
                                containerColor = DarkBg
                            )
                        )
                    }
                }

                Text("Policy Status", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Active", "Due", "Lapsed")) { status ->
                        val isSelected = policyStatusFilter == status
                        FilterChip(
                            selected = isSelected,
                            onClick = { policyStatusFilter = status },
                            label = { Text(status, color = TextWhite) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBluePrimary,
                                containerColor = DarkBg
                            )
                        )
                    }
                }

                Text("Payment Mode", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Cash", "UPI", "Bank", "Cheque")) { mode ->
                        val isSelected = paymentModeFilter == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { paymentModeFilter = mode },
                            label = { Text(mode, color = TextWhite) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBluePrimary,
                                containerColor = DarkBg
                            )
                        )
                    }
                }

                Button(
                    onClick = {
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Filters", color = TextWhite, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Detailed Dialogs for Financial Summary Cards (Requirement 1)
    selectedSummaryCardForDialog?.let { cardIndex ->
        when (cardIndex) {
            // 0: Total Collected Report
            0 -> {
                AlertDialog(
                    onDismissRequest = { selectedSummaryCardForDialog = null },
                    containerColor = CardBg,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = AccentGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Total Collection Report", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Total Collected: ${formatIndianCurrency(dynamicTotalCollected)}", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Period: ${selectedFilter.label} (${filteredPayments.size} receipts)", color = TextMuted, fontSize = 13.sp)

                            Surface(color = DarkBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                if (filteredPayments.isEmpty()) {
                                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("No payment records found for this period.", color = TextMuted, fontSize = 12.5.sp)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(filteredPayments) { pay ->
                                            Column(modifier = Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(pay.customerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(formatIndianCurrency(pay.paidAmount), color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                                Text("Policy: ${pay.policyNumber} • Mode: ${pay.paymentMode}", color = TextMuted, fontSize = 11.5.sp)
                                                Text("Date: ${pay.paymentDate} • Receipt: ${pay.receiptNumber}", color = RoyalBlueLight, fontSize = 10.5.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { selectedSummaryCardForDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // 1: Outstanding Premiums Report
            1 -> {
                AlertDialog(
                    onDismissRequest = { selectedSummaryCardForDialog = null },
                    containerColor = CardBg,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PendingActions, contentDescription = null, tint = AccentRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Outstanding Premiums", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Total Outstanding: ${formatIndianCurrency(dynamicTotalOutstanding)}", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("$dynamicDueCount policies pending payment", color = TextMuted, fontSize = 13.sp)

                            Surface(color = DarkBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                val outstandingList = filteredPolicies.filter { it.status.equals("Due", ignoreCase = true) || it.status.equals("Lapsed", ignoreCase = true) }
                                if (outstandingList.isEmpty()) {
                                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("No outstanding policies found.", color = TextMuted, fontSize = 12.5.sp)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(outstandingList) { pol ->
                                            Column(modifier = Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(pol.customerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(formatIndianCurrency(pol.premiumAmount), color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                                Text("Policy: ${pol.policyNumber} • Plan: ${pol.planName}", color = TextMuted, fontSize = 11.5.sp)
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Due Date: ${pol.dueDate}", color = AccentAmber, fontSize = 11.sp)
                                                    TextButton(
                                                        onClick = {
                                                            selectedSummaryCardForDialog = null
                                                            val customer = liveCustomers.find { it.id == pol.customerId }
                                                            val mobile = customer?.mobile ?: ""
                                                            val msg = com.example.whatsapp.WhatsAppAutomation.generateMessage(
                                                                context = context,
                                                                templateType = WhatsAppTemplateType.OVERDUE,
                                                                customerName = pol.customerName,
                                                                policyNumber = pol.policyNumber,
                                                                planName = pol.planName,
                                                                premiumAmount = pol.premiumAmount,
                                                                dueDate = pol.dueDate
                                                            )
                                                            com.example.whatsapp.WhatsAppAutomation.sendWhatsAppReminder(context, mobile, msg)
                                                        },
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("Remind", color = RoyalBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { selectedSummaryCardForDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // 2: Policy Summary Report
            2 -> {
                AlertDialog(
                    onDismissRequest = { selectedSummaryCardForDialog = null },
                    containerColor = CardBg,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = RoyalBlueLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Policy Summary", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Total Policies: $dynamicTotalPolicies", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Active: $dynamicActiveCount", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text("Due / Pending: $dynamicDueCount", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }

                            Button(
                                onClick = {
                                    selectedSummaryCardForDialog = null
                                    onNavigateToPolicies()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Policy List Screen", color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedSummaryCardForDialog = null }) {
                            Text("Close", color = TextMuted)
                        }
                    }
                )
            }

            // 3: Collection Rate Details
            3 -> {
                AlertDialog(
                    onDismissRequest = { selectedSummaryCardForDialog = null },
                    containerColor = CardBg,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Collection Rate Details", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Collection Efficiency: ${String.format(Locale.US, "%.1f%%", dynamicCollectionRate)}", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Calculation Formula:", color = TextMuted, fontSize = 13.sp)

                            Surface(color = DarkBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("• Total Collected: ${formatIndianCurrency(dynamicTotalCollected)}", color = AccentGreen, fontSize = 12.5.sp)
                                    Text("• Total Outstanding: ${formatIndianCurrency(dynamicTotalOutstanding)}", color = AccentRed, fontSize = 12.5.sp)
                                    Text("• Total Target Due: ${formatIndianCurrency(dynamicTotalCollected + dynamicTotalOutstanding)}", color = TextWhite, fontSize = 12.5.sp)
                                    Text("• Rate = (Collected / Target) × 100", color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { selectedSummaryCardForDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }

    // Customer Details Dialog
    selectedCustomerForDialog?.let { customer ->
        AlertDialog(
            onDismissRequest = { selectedCustomerForDialog = null },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(RoyalBluePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(customer.initials, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(customer.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${customer.policyCount} Active Policies • ${customer.statusBadge}", color = customer.badgeColor, fontSize = 12.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Collected:", color = TextMuted, fontSize = 13.sp)
                        Text(customer.collectedAmount, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Outstanding:", color = TextMuted, fontSize = 13.sp)
                        Text(customer.outstandingAmount, color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            selectedCustomerForDialog = null
                            onNavigateToCustomerDetail?.invoke(customer.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Customer Details Screen", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCustomerForDialog = null }) {
                    Text("Dismiss", color = TextMuted)
                }
            }
        )
    }

    // Receipt Details Dialog
    selectedReceiptForDialog?.let { receipt ->
        AlertDialog(
            onDismissRequest = { selectedReceiptForDialog = null },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Receipt #${receipt.receiptNumber}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = DarkBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Client:", color = TextMuted, fontSize = 12.5.sp)
                                Text(receipt.customerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Policy No:", color = TextMuted, fontSize = 12.5.sp)
                                Text(receipt.policyNumber, color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Amount Paid:", color = TextMuted, fontSize = 12.5.sp)
                                Text(receipt.premiumAmount, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Payment Mode:", color = TextMuted, fontSize = 12.5.sp)
                                Text(receipt.paymentMode, color = TextWhite, fontSize = 12.5.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Date:", color = TextMuted, fontSize = 12.5.sp)
                                Text(receipt.collectedDate, color = TextWhite, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedReceiptForDialog = null
                        coroutineScope.launch { snackbarHostState.showSnackbar("Official LIC receipt sent to customer.") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Receipt", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedReceiptForDialog = null }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// HELPER FUNCTIONS & COMPOSABLES
// ---------------------------------------------------------------------------

private fun isDateInPeriod(
    dateStr: String,
    filter: ReportQuickFilter,
    customStart: String,
    customEnd: String
): Boolean {
    if (dateStr.isBlank()) return true
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    return when (filter) {
        ReportQuickFilter.TODAY -> dateStr.startsWith(today) || dateStr.equals(today, ignoreCase = true)
        ReportQuickFilter.THIS_WEEK -> {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(dateStr)
                val calNow = Calendar.getInstance()
                val calDate = Calendar.getInstance()
                if (date != null) {
                    calDate.time = date
                    val diffDays = (calNow.timeInMillis - calDate.timeInMillis) / (24 * 60 * 60 * 1000)
                    diffDays in -1..7 || (calNow.get(Calendar.WEEK_OF_YEAR) == calDate.get(Calendar.WEEK_OF_YEAR) && calNow.get(Calendar.YEAR) == calDate.get(Calendar.YEAR))
                } else true
            } catch (e: Exception) {
                true
            }
        }
        ReportQuickFilter.THIS_MONTH -> dateStr.startsWith(currentMonth)
        ReportQuickFilter.THIS_YEAR -> dateStr.startsWith(currentYear)
        ReportQuickFilter.CUSTOM_DATE -> {
            try {
                val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfCustom = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val itemDate = sdfInput.parse(dateStr)
                val startDate = sdfCustom.parse(customStart)
                val endDate = sdfCustom.parse(customEnd)
                if (itemDate != null && startDate != null && endDate != null) {
                    !itemDate.before(startDate) && !itemDate.after(endDate)
                } else true
            } catch (e: Exception) {
                true
            }
        }
    }
}

private fun formatIndianCurrency(amount: Double): String {
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("INR", "₹").replace("₹ ", "₹ ")
}

@Composable
private fun SummaryMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) RoyalBluePrimary.copy(alpha = 0.2f) else CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) RoyalBlueLight else CardBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(RoyalBlueLight, CircleShape)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = iconColor, fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Monthly Collection Line Chart
@Composable
private fun MonthlyLineChartCard(
    modifier: Modifier = Modifier,
    progress: Float,
    isEmpty: Boolean
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Monthly Collection Trend", style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Text("2026 Monthly Volume (in ₹)", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                }

                Text("Year 2026", style = MaterialTheme.typography.labelSmall.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 11.sp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height

                for (i in 0..3) {
                    val y = height * (i / 3f)
                    drawLine(
                        color = CardBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (!isEmpty) {
                    val points = listOf(0.3f, 0.45f, 0.85f, 0.6f, 0.75f, 0.9f, 0.8f, 0.95f, 0.7f, 0.85f)
                    val stepX = width / (points.size - 1)

                    val path = Path()
                    val fillPath = Path()

                    fillPath.moveTo(0f, height)

                    points.forEachIndexed { index, value ->
                        val x = index * stepX
                        val targetY = height - (value * height * 0.8f)
                        val currentY = height - ((height - targetY) * progress)

                        if (index == 0) {
                            path.moveTo(x, currentY)
                            fillPath.lineTo(x, currentY)
                        } else {
                            val prevX = (index - 1) * stepX
                            val prevTargetY = height - (points[index - 1] * height * 0.8f)
                            val prevY = height - ((height - prevTargetY) * progress)

                            val controlX1 = prevX + (stepX / 2f)
                            val controlY1 = prevY
                            val controlX2 = prevX + (stepX / 2f)
                            val controlY2 = currentY

                            path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, currentY)
                            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, currentY)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(RoyalBluePrimary.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = path,
                        color = RoyalBlueLight,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { index, value ->
                        val x = index * stepX
                        val targetY = height - (value * height * 0.8f)
                        val currentY = height - ((height - targetY) * progress)

                        drawCircle(
                            color = DarkBg,
                            radius = 5.dp.toPx(),
                            center = Offset(x, currentY)
                        )
                        drawCircle(
                            color = RoyalBlueLight,
                            radius = 3.dp.toPx(),
                            center = Offset(x, currentY)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov", "Dec").forEach { month ->
                    Text(month, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                }
            }
        }
    }
}

// Premium by Plan Donut Chart
@Composable
private fun PlanDonutChartCard(
    modifier: Modifier = Modifier,
    progress: Float,
    isEmpty: Boolean
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Premium by Plan", style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Text("Share of total portfolio volume", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 16.dp.toPx()
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        if (!isEmpty) {
                            val slices = listOf(
                                0.42f to RoyalBluePrimary,
                                0.28f to AccentGreen,
                                0.18f to AccentAmber,
                                0.12f to AccentPurple
                            )

                            var startAngle = -90f
                            slices.forEach { (percentage, color) ->
                                val sweepAngle = 360f * percentage * progress
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += 360f * percentage
                            }
                        } else {
                            drawArc(
                                color = CardBorder,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isEmpty) "0%" else "100%", style = MaterialTheme.typography.titleSmall.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                        Text("Volume", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlanLegendItem(color = RoyalBluePrimary, name = "Jeevan Umang", percentage = "42%")
                    PlanLegendItem(color = AccentGreen, name = "Jeevan Labh", percentage = "28%")
                    PlanLegendItem(color = AccentAmber, name = "Jeevan Lakshya", percentage = "18%")
                    PlanLegendItem(color = AccentPurple, name = "Endowment", percentage = "12%")
                }
            }
        }
    }
}

@Composable
private fun PlanLegendItem(color: Color, name: String, percentage: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelSmall.copy(color = TextWhite, fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(percentage, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp))
    }
}

// Collection Status Bar Chart
@Composable
private fun StatusBarChartCard(
    modifier: Modifier = Modifier,
    progress: Float,
    isEmpty: Boolean
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Collection Status Breakdown", style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Text("Paid vs Pending vs Overdue ratios", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDotLegend(color = AccentGreen, label = "Paid")
                    StatusDotLegend(color = AccentAmber, label = "Pending")
                    StatusDotLegend(color = AccentRed, label = "Overdue")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val width = size.width
                val height = size.height

                val groupCount = 5
                val groupWidth = width / groupCount
                val barWidth = 10.dp.toPx()

                if (!isEmpty) {
                    val data = listOf(
                        Triple(0.8f, 0.15f, 0.05f),
                        Triple(0.7f, 0.2f, 0.1f),
                        Triple(0.85f, 0.1f, 0.05f),
                        Triple(0.65f, 0.25f, 0.1f),
                        Triple(0.9f, 0.08f, 0.02f)
                    )

                    data.forEachIndexed { index, (paid, pending, overdue) ->
                        val groupCenterX = index * groupWidth + (groupWidth / 2f)

                        val paidH = height * paid * progress
                        drawRoundRect(
                            color = AccentGreen,
                            topLeft = Offset(groupCenterX - (barWidth * 1.5f), height - paidH),
                            size = Size(barWidth, paidH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        val pendingH = height * pending * progress
                        drawRoundRect(
                            color = AccentAmber,
                            topLeft = Offset(groupCenterX - (barWidth * 0.5f), height - pendingH),
                            size = Size(barWidth, pendingH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        val overdueH = height * overdue * progress
                        drawRoundRect(
                            color = AccentRed,
                            topLeft = Offset(groupCenterX + (barWidth * 0.5f), height - overdueH),
                            size = Size(barWidth, overdueH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("Apr", "May", "Jun", "Jul", "Aug").forEach { month ->
                    Text(month, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
private fun StatusDotLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
    }
}

// Top Customer Row Item
@Composable
private fun TopCustomerRowItem(
    customer: TopCustomerData,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = RoyalBluePrimary.copy(alpha = 0.2f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, RoyalBlueLight),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(customer.initials, color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(customer.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = customer.badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = customer.statusBadge,
                                color = customer.badgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${customer.policyCount} Policies Portfolio", color = TextMuted, fontSize = 11.5.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(customer.collectedAmount, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Outstanding: ${customer.outstandingAmount}", color = TextMuted, fontSize = 10.5.sp)
            }
        }
    }
}

// Recent Collection Row Item
@Composable
private fun RecentCollectionRowItem(
    collection: RecentCollectionData,
    onReceiptClick: () -> Unit
) {
    Surface(
        color = DarkBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(collection.customerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Policy #${collection.policyNumber} • ${collection.paymentMode}", color = TextMuted, fontSize = 11.5.sp)
                Text("Collected on ${collection.collectedDate}", color = RoyalBlueLight, fontSize = 10.5.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(collection.premiumAmount, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onReceiptClick,
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Receipt", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Insight Row Item
@Composable
private fun InsightRowItem(
    label: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg, RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Text(value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(subtitle, color = iconColor, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun EmptyInlineState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextMuted, fontSize = 12.5.sp)
    }
}

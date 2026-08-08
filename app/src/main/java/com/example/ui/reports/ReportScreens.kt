package com.example.ui.reports

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
import com.example.ui.LicViewModel
import com.example.util.SearchFilterEngine
import kotlinx.coroutines.launch

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
    LaunchedEffect(selectedFilter) {
        isChartAnimTriggered = false
        kotlinx.coroutines.delay(50)
        isChartAnimTriggered = true
    }

    // Mock Sample Data for Top Customers
    val allTopCustomers = remember {
        listOf(
            TopCustomerData(1, "Rahul Kumar", "RK", 4, "₹ 48,500", "₹ 12,000", "VIP Client", AccentAmber),
            TopCustomerData(2, "Anita Das", "AD", 3, "₹ 36,200", "₹ 0", "On Time", AccentGreen),
            TopCustomerData(3, "Suresh Patel", "SP", 5, "₹ 62,000", "₹ 24,000", "Grace Period", AccentPurple),
            TopCustomerData(4, "Rajesh Sharma", "RS", 2, "₹ 18,500", "₹ 8,500", "High Volume", RoyalBlueLight),
            TopCustomerData(5, "Priya Verma", "PV", 3, "₹ 42,000", "₹ 0", "On Time", AccentGreen)
        )
    }

    // Mock Sample Data for Recent Collections
    val allRecentCollections = remember {
        listOf(
            RecentCollectionData(101, "Rahul Kumar", "847291038", "₹ 12,750", "UPI - Google Pay", "04 Aug 2026", "REC-2026-801"),
            RecentCollectionData(102, "Anita Das", "918237465", "₹ 18,200", "Net Banking (HDFC)", "04 Aug 2026", "REC-2026-802"),
            RecentCollectionData(103, "Vikram Malhotra", "321654987", "₹ 32,000", "Cheque #40291", "02 Aug 2026", "REC-2026-803"),
            RecentCollectionData(104, "Priya Verma", "543216879", "₹ 15,300", "Cash Receipt", "01 Aug 2026", "REC-2026-804"),
            RecentCollectionData(105, "Meenakshi S.", "321456987", "₹ 11,200", "UPI - PhonePe", "30 Jul 2026", "REC-2026-805")
        )
    }

    // Filtered lists based on search & empty state toggle
    val filteredTopCustomers = remember(searchQuery, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else if (searchQuery.isBlank()) allTopCustomers
        else allTopCustomers.filter {
            SearchFilterEngine.matchesQuery(searchQuery, listOf(it.name, it.statusBadge, it.collectedAmount, it.outstandingAmount))
        }
    }

    val filteredRecentCollections = remember(searchQuery, forceEmptyState) {
        if (forceEmptyState) emptyList()
        else if (searchQuery.isBlank()) allRecentCollections
        else allRecentCollections.filter {
            SearchFilterEngine.matchesQuery(searchQuery, listOf(it.customerName, it.policyNumber, it.receiptNumber, it.paymentMode, it.premiumAmount))
        }
    }

    val isListEmpty = forceEmptyState || (filteredTopCustomers.isEmpty() && filteredRecentCollections.isEmpty())

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
                                tint = TextWhite
                            )
                        }

                        // Action 3: Export
                        IconButton(
                            onClick = { showExportSheet = true },
                            modifier = Modifier.testTag("action_export")
                        ) {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = "Export",
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
                                    text = { Text("Refresh Analytics", color = TextWhite) },
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
                                    text = { Text("Toggle Empty State Demo", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, tint = AccentAmber) },
                                    onClick = {
                                        showMoreMenu = false
                                        forceEmptyState = !forceEmptyState
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Filters", color = TextWhite) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentRed) },
                                    onClick = {
                                        showMoreMenu = false
                                        searchQuery = ""
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

                // Expandable Interactive Search Bar
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search customer, policy, receipt #...", color = TextMuted, fontSize = 13.sp) },
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
                    // Export PDF Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Generating LIC PDF Report...")
                                val reportType = when (selectedCardIndex) {
                                    0 -> com.example.pdf.ReportType.MONTHLY_COLLECTION
                                    1 -> com.example.pdf.ReportType.OUTSTANDING_PREMIUM
                                    2 -> com.example.pdf.ReportType.COMPLETE_PORTFOLIO
                                    else -> com.example.pdf.ReportType.MONTHLY_COLLECTION
                                }
                                val reportData = com.example.pdf.PdfReportData(
                                    reportType = reportType,
                                    agentProfile = agentProfile,
                                    customerList = liveCustomers,
                                    policyList = livePolicies,
                                    paymentList = livePayments,
                                    filterPeriod = selectedFilter.label
                                )
                                val res = com.example.pdf.PdfReportGenerator.generatePdfReport(context, reportData)
                                res.onSuccess { file ->
                                    snackbarHostState.showSnackbar("PDF Report Saved: ${file.name}")
                                    com.example.pdf.PdfReportGenerator.openPdf(context, file)
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

                    // Export Excel Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Exporting data to Excel Spreadsheet...")
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

                    // Share Report Button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Preparing PDF report for sharing...")
                                val reportType = when (selectedCardIndex) {
                                    0 -> com.example.pdf.ReportType.MONTHLY_COLLECTION
                                    1 -> com.example.pdf.ReportType.OUTSTANDING_PREMIUM
                                    2 -> com.example.pdf.ReportType.COMPLETE_PORTFOLIO
                                    else -> com.example.pdf.ReportType.MONTHLY_COLLECTION
                                }
                                val reportData = com.example.pdf.PdfReportData(
                                    reportType = reportType,
                                    agentProfile = agentProfile,
                                    customerList = liveCustomers,
                                    policyList = livePolicies,
                                    paymentList = livePayments,
                                    filterPeriod = selectedFilter.label
                                )
                                val res = com.example.pdf.PdfReportGenerator.generatePdfReport(context, reportData)
                                res.onSuccess { file ->
                                    com.example.pdf.PdfReportGenerator.sharePdf(context, file)
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {

                // ==========================================
                // 1. DASHBOARD SUMMARY (4 PREMIUM CARDS)
                // ==========================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "FINANCIAL SUMMARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoyalBlueLight,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        if (isTablet) {
                            // 4 Column Row for Tablet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SummaryMetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Premium Collected",
                                    amount = if (forceEmptyState) "₹ 0" else "₹ 2,85,400",
                                    subtitle = "+14.2% vs last month",
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
                                    title = "Outstanding Premium",
                                    amount = if (forceEmptyState) "₹ 0" else "₹ 64,200",
                                    subtitle = "12 policies pending",
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
                                    amount = if (forceEmptyState) "0" else "128",
                                    subtitle = "112 Active • 16 Due",
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
                                    amount = if (forceEmptyState) "0%" else "81.6%",
                                    subtitle = "Target: 85.0%",
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
                                        amount = if (forceEmptyState) "₹ 0" else "₹ 2,85,400",
                                        subtitle = "+14.2% vs prev month",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        iconColor = AccentGreen,
                                        isSelected = (selectedCardIndex == 0),
                                        onClick = {
                                            selectedCardIndex = 0
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Filtering by Collected Premium records.") }
                                        }
                                    )
                                    SummaryMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "Outstanding",
                                        amount = if (forceEmptyState) "₹ 0" else "₹ 64,200",
                                        subtitle = "12 policies pending",
                                        icon = Icons.Default.PendingActions,
                                        iconColor = AccentRed,
                                        isSelected = (selectedCardIndex == 1),
                                        onClick = {
                                            selectedCardIndex = 1
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Filtering by Outstanding Premium policies.") }
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
                                        amount = if (forceEmptyState) "0" else "128",
                                        subtitle = "112 Active • 16 Due",
                                        icon = Icons.Default.Folder,
                                        iconColor = RoyalBlueLight,
                                        isSelected = (selectedCardIndex == 2),
                                        onClick = {
                                            selectedCardIndex = 2
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Viewing all active policy portfolios.") }
                                        }
                                    )
                                    SummaryMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "Collection Rate",
                                        amount = if (forceEmptyState) "0%" else "81.6%",
                                        subtitle = "Target: 85.0%",
                                        icon = Icons.Default.Speed,
                                        iconColor = AccentAmber,
                                        isSelected = (selectedCardIndex == 3),
                                        onClick = {
                                            selectedCardIndex = 3
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Target performance progress rate: 81.6%") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. QUICK FILTERS
                // ==========================================
                item {
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
                }

                // ==========================================
                // 3. CHARTS SECTION (3 MATERIAL CANVAS CHARTS)
                // ==========================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            // Side by side charts for tablet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MonthlyLineChartCard(
                                    modifier = Modifier.weight(1.2f),
                                    progress = chartAnimProgress,
                                    isEmpty = forceEmptyState
                                )
                                PlanDonutChartCard(
                                    modifier = Modifier.weight(1f),
                                    progress = chartAnimProgress,
                                    isEmpty = forceEmptyState
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            StatusBarChartCard(
                                modifier = Modifier.fillMaxWidth(),
                                progress = chartAnimProgress,
                                isEmpty = forceEmptyState
                            )
                        } else {
                            // Stacked charts for phone
                            MonthlyLineChartCard(
                                modifier = Modifier.fillMaxWidth(),
                                progress = chartAnimProgress,
                                isEmpty = forceEmptyState
                            )

                            PlanDonutChartCard(
                                modifier = Modifier.fillMaxWidth(),
                                progress = chartAnimProgress,
                                isEmpty = forceEmptyState
                            )

                            StatusBarChartCard(
                                modifier = Modifier.fillMaxWidth(),
                                progress = chartAnimProgress,
                                isEmpty = forceEmptyState
                            )
                        }
                    }
                }

                // ==========================================
                // 4. TOP CUSTOMERS SECTION
                // ==========================================
                item {
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

                            if (filteredTopCustomers.isEmpty()) {
                                EmptyInlineState(message = "No matching top customer records found.")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    filteredTopCustomers.forEach { customer ->
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
                }

                // ==========================================
                // 5. RECENT COLLECTIONS SECTION
                // ==========================================
                item {
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
                                    text = "${filteredRecentCollections.size} Records",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 12.sp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredRecentCollections.isEmpty()) {
                                EmptyInlineState(message = "No recent premium collection receipts found.")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    filteredRecentCollections.forEach { collection ->
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
                }

                // ==========================================
                // 6. INSIGHTS SECTION
                // ==========================================
                item {
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
                                    label = "Highest Collection Month",
                                    value = if (forceEmptyState) "N/A" else "March 2026 — ₹ 4,20,000",
                                    subtitle = "Peak financial year renewal cycle",
                                    icon = Icons.Default.ShowChart,
                                    iconColor = AccentGreen
                                )
                                InsightRowItem(
                                    label = "Lowest Collection Month",
                                    value = if (forceEmptyState) "N/A" else "November 2025 — ₹ 1,15,000",
                                    subtitle = "Diwali seasonal slowdown",
                                    icon = Icons.Default.TrendingDown,
                                    iconColor = AccentRed
                                )
                                InsightRowItem(
                                    label = "Best Selling Plan",
                                    value = if (forceEmptyState) "N/A" else "Jeevan Umang (Plan 945)",
                                    subtitle = "42% of total portfolio revenue",
                                    icon = Icons.Default.WorkspacePremium,
                                    iconColor = AccentAmber
                                )
                                InsightRowItem(
                                    label = "Pending Premium Volume",
                                    value = if (forceEmptyState) "₹ 0" else "₹ 38,400",
                                    subtitle = "8 policies currently in grace period",
                                    icon = Icons.Default.HourglassTop,
                                    iconColor = RoyalBlueLight
                                )
                                InsightRowItem(
                                    label = "Overdue Premium Volume",
                                    value = if (forceEmptyState) "₹ 0" else "₹ 25,800",
                                    subtitle = "4 policies requiring urgent follow-up",
                                    icon = Icons.Default.Warning,
                                    iconColor = AccentRed
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 7. EMPTY STATE DISPLAY (IF FORCE TOGGLED OR NO RESULTS)
                // ==========================================
                if (isListEmpty) {
                    item {
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
                                // Illustration Placeholder
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
                                    text = "No reports available",
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
                }
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
                        label = { Text("Start Date", color = TextMuted) },
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
                        label = { Text("End Date", color = TextMuted) },
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
                            coroutineScope.launch { snackbarHostState.showSnackbar("PDF Executive Summary downloaded.") }
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
                            coroutineScope.launch { snackbarHostState.showSnackbar("Excel Spreadsheet exported to Downloads.") }
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

    // Filter Options Bottom Sheet
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
                Text(
                    text = "Filter Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                )

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
                                showFilterSheet = false
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

                Button(
                    onClick = {
                        showFilterSheet = false
                        showCustomDateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg),
                    border = BorderStroke(1.dp, RoyalBlueLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Custom Date Range ($customStartDate - $customEndDate)", color = TextWhite, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Summary Metric Detail Dialog
    selectedSummaryCardForDialog?.let { cardIndex ->
        val titles = listOf("Total Premium Collected", "Outstanding Premium", "Policy Portfolio", "Collection Rate Target")
        val amounts = listOf("₹ 2,85,400", "₹ 64,200", "128 Policies", "81.6%")
        val title = titles.getOrElse(cardIndex) { "Metric Report" }
        val amount = amounts.getOrElse(cardIndex) { "N/A" }

        AlertDialog(
            onDismissRequest = { selectedSummaryCardForDialog = null },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current Value: $amount", color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Detailed audit trail breakdown for the selected period:", color = TextMuted, fontSize = 13.sp)

                    Surface(color = DarkBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("• Total Operations: 142 transactions", color = TextWhite, fontSize = 12.5.sp)
                            Text("• Digital Verification: 100% Validated", color = AccentGreen, fontSize = 12.5.sp)
                            Text("• Grace Period Remaining: 15 Days", color = AccentAmber, fontSize = 12.5.sp)
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
                    Text("Close Report", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }
        )
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
// HELPER COMPOSABLES & CHARTS
// ---------------------------------------------------------------------------

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

            // Line Chart Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height

                // Draw Horizontal Grid Lines
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

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(RoyalBluePrimary.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )

                    // Draw Line Path
                    drawPath(
                        path = path,
                        color = RoyalBlueLight,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Points
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

            // X-Axis Month Labels
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
                // Donut Canvas
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

                // Plan Legend Breakdown
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

            // Bar Chart Canvas
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

                        // Paid Bar
                        val paidH = height * paid * progress
                        drawRoundRect(
                            color = AccentGreen,
                            topLeft = Offset(groupCenterX - (barWidth * 1.5f), height - paidH),
                            size = Size(barWidth, paidH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Pending Bar
                        val pendingH = height * pending * progress
                        drawRoundRect(
                            color = AccentAmber,
                            topLeft = Offset(groupCenterX - (barWidth * 0.5f), height - pendingH),
                            size = Size(barWidth, pendingH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Overdue Bar
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

// Top Customer Item
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
                // Avatar
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

// Recent Collection Item
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

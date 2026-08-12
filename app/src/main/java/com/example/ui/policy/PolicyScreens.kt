package com.example.ui.policy

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.*
import com.example.util.PaymentAllocationEngine
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.PolicyFilterDue
import com.example.ui.PolicyFilterStatus
import com.example.ui.PolicyModeFilter
import com.example.ui.PolicySortOption
import com.example.ui.payment.PaymentCollectionDialog
import com.example.ui.components.*
import com.example.ui.customer.DetailItem
import com.example.ui.theme.*
import com.example.util.NoMatchingRecordsEmptyState
import com.example.util.SearchFilterEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

fun parsePlanDetails(fullPlanName: String): Pair<String, String> {
    if (fullPlanName.contains("(") && fullPlanName.contains(")")) {
        val name = fullPlanName.substringBefore("(").trim()
        val code = fullPlanName.substringAfter("(").substringBefore(")").trim()
        return Pair(name, code)
    }
    return Pair(fullPlanName, "Plan")
}

fun getPolicyOutstandingBalance(policy: PolicyEntity, payments: List<PaymentEntity>): Double {
    if (policy.premiumAmount <= 0) return 0.0
    return PaymentAllocationEngine.calculateCurrentDueSummary(policy, payments).outstanding
}

fun sharePolicySummaryText(
    context: android.content.Context,
    policy: PolicyEntity,
    customer: CustomerEntity?,
    payments: List<PaymentEntity>
) {
    val (planNameOnly, planCode) = parsePlanDetails(policy.planName)
    val totalPaid = payments.sumOf { it.paidAmount }
    val outstanding = getPolicyOutstandingBalance(policy, payments)

    val text = """
        📋 *LIC POLICY PORTFOLIO REPORT*
        ----------------------------------
        • Policy Number: ${policy.policyNumber}
        • Plan Name: $planNameOnly ($planCode)
        • Customer Name: ${policy.customerName}
        • Contact Phone: ${customer?.mobile ?: "N/A"}

        💰 *FINANCIAL DETAILS*
        • Sum Assured: ₹${"%.2f".format(policy.sumAssured)}
        • Premium Amount: ₹${"%.2f".format(policy.premiumAmount)} (${policy.premiumMode})
        • Total Paid to Date: ₹${"%.2f".format(totalPaid)}
        • Outstanding Balance: ₹${"%.2f".format(outstanding)}

        📅 *SCHEDULE & BENEFICIARY*
        • Next Due Date: ${policy.dueDate}
        • Grace Period: ${policy.gracePeriodDays} Days
        • Policy Term / PPT: ${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs
        • Issue Date: ${policy.issueDate}
        • Maturity Date: ${policy.maturityDate}
        • Nominee: ${policy.nominee.ifEmpty { "N/A" }}
        • Policy Status: ${policy.status.uppercase()}
        ----------------------------------
        Generated via LIC Agent CRM
    """.trimIndent()

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "LIC Policy Report - ${policy.policyNumber}")
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Policy Summary"))
}

data class DaysLeftInfo(
    val text: String,
    val textColor: Color,
    val containerColor: Color,
    val icon: ImageVector
)

fun getDaysLeftBadgeInfo(dueDateStr: String, status: String): DaysLeftInfo {
    if (status.equals("Matured", ignoreCase = true)) {
        return DaysLeftInfo("⚫ Matured", Color.White, Color(0xFF37474F), Icons.Default.CheckCircle)
    }
    return try {
        val today = LocalDate.now()
        val dueDate = try {
            LocalDate.parse(dueDateStr)
        } catch (e: Exception) {
            today
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
        when {
            days < 0 -> {
                val overdueDays = -days
                DaysLeftInfo(
                    text = "🔴 Overdue by $overdueDays ${if (overdueDays == 1L) "Day" else "Days"}",
                    textColor = Color.White,
                    containerColor = ErrorRed,
                    icon = Icons.Default.Warning
                )
            }
            days == 0L -> {
                DaysLeftInfo(
                    text = "🟡 Due Today",
                    textColor = Color.White,
                    containerColor = AccentOrange,
                    icon = Icons.Default.Schedule
                )
            }
            days == 1L -> {
                DaysLeftInfo(
                    text = "🟡 Due Tomorrow",
                    textColor = Color.White,
                    containerColor = AccentOrange,
                    icon = Icons.Default.Schedule
                )
            }
            days <= 30L -> {
                DaysLeftInfo(
                    text = "🟢 Due in $days Days",
                    textColor = Color.White,
                    containerColor = EmeraldGreenSecondary,
                    icon = Icons.Default.Event
                )
            }
            else -> {
                DaysLeftInfo(
                    text = "🟢 Due in $days Days",
                    textColor = Color.White,
                    containerColor = RoyalBluePrimary,
                    icon = Icons.Default.Event
                )
            }
        }
    } catch (e: Exception) {
        DaysLeftInfo("🟢 Active", Color.White, EmeraldGreenSecondary, Icons.Default.CheckCircle)
    }
}

data class InstallmentProgressInfo(
    val completedInstallments: Int,
    val totalInstallments: Int,
    val currentInstallmentPaid: Double,
    val currentInstallmentOutstanding: Double,
    val premiumAmount: Double,
    val progressFraction: Float,
    val overallPercentage: Float
)

fun calculateInstallmentProgress(
    policy: PolicyEntity,
    policyPayments: List<PaymentEntity>
): InstallmentProgressInfo {
    val termYears = if (policy.policyTerm > 0) policy.policyTerm else 20
    val pptYears = if (policy.premiumPayingTerm > 0) policy.premiumPayingTerm else termYears
    val mode = policy.premiumMode.uppercase()

    val installmentsPerYear = when {
        mode.contains("MONTH") -> 12
        mode.contains("QUARTER") -> 4
        mode.contains("HALF") -> 2
        mode.contains("YEAR") -> 1
        else -> 1
    }
    val totalInstallments = (pptYears * installmentsPerYear).coerceAtLeast(1)

    val totalValidPaidAmount = policyPayments.sumOf { it.paidAmount }
    val premiumAmount = policy.premiumAmount

    val completedInstallments: Int
    val currentInstallmentPaid: Double
    val currentInstallmentOutstanding: Double

    if (premiumAmount <= 0.0) {
        completedInstallments = 0
        currentInstallmentPaid = 0.0
        currentInstallmentOutstanding = 0.0
    } else {
        val rawCompleted = kotlin.math.floor(totalValidPaidAmount / premiumAmount).toInt()
        completedInstallments = rawCompleted.coerceIn(0, totalInstallments)

        if (completedInstallments >= totalInstallments) {
            currentInstallmentPaid = premiumAmount
            currentInstallmentOutstanding = 0.0
        } else {
            val remainder = totalValidPaidAmount - (completedInstallments * premiumAmount)
            if (remainder > 0.0) {
                currentInstallmentPaid = remainder
                currentInstallmentOutstanding = (premiumAmount - remainder).coerceAtLeast(0.0)
            } else {
                if (completedInstallments == 0) {
                    currentInstallmentPaid = 0.0
                    currentInstallmentOutstanding = premiumAmount
                } else {
                    currentInstallmentPaid = premiumAmount
                    currentInstallmentOutstanding = 0.0
                }
            }
        }
    }

    val totalExpected = totalInstallments * premiumAmount
    val progressFraction = if (totalExpected > 0.0) {
        (totalValidPaidAmount / totalExpected).toFloat().coerceIn(0f, 1f)
    } else 0f

    val overallPercentage = progressFraction * 100f

    return InstallmentProgressInfo(
        completedInstallments = completedInstallments,
        totalInstallments = totalInstallments,
        currentInstallmentPaid = currentInstallmentPaid,
        currentInstallmentOutstanding = currentInstallmentOutstanding,
        premiumAmount = premiumAmount,
        progressFraction = progressFraction,
        overallPercentage = overallPercentage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyListScreen(
    viewModel: LicViewModel,
    onSelectPolicy: (PolicyEntity) -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val dueFilter by viewModel.dueFilter.collectAsState()
    val modeFilter by viewModel.modeFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val context = LocalContext.current

    var policyToEdit by remember { mutableStateOf<PolicyEntity?>(null) }
    var policyToDelete by remember { mutableStateOf<PolicyEntity?>(null) }
    var policyForPayment by remember { mutableStateOf<PolicyEntity?>(null) }
    var policyForPlanEdit by remember { mutableStateOf<PolicyEntity?>(null) }

    // Header menus & Search visibility
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(false) }

    val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Policy document uploaded successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Loading & Error States
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Calculate status counts
    val activeCount = remember(policies) { policies.count { it.status.equals("Active", ignoreCase = true) } }
    val overdueCount = remember(policies) { policies.count { it.status.equals("Overdue", ignoreCase = true) || it.status.equals("Lapsed", ignoreCase = true) } }
    val dueTodayCount = remember(policies) {
        val todayStr = LocalDate.now().toString()
        policies.count { it.dueDate == todayStr || it.status.equals("Due", ignoreCase = true) }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Policies",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "$activeCount Active • ${policies.size} Total",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentOrangeLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    // Search Toggle
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }

                    // Sort Dropdown Menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest Policy First", fontWeight = if (sortOption == PolicySortOption.RECENTLY_ADDED) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.NewReleases, contentDescription = null, tint = RoyalBluePrimary) },
                                onClick = {
                                    viewModel.setSortOption(PolicySortOption.RECENTLY_ADDED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Next Due Date", fontWeight = if (sortOption == PolicySortOption.NEXT_DUE) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, tint = AccentOrange) },
                                onClick = {
                                    viewModel.setSortOption(PolicySortOption.NEXT_DUE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Customer Name", fontWeight = if (sortOption == PolicySortOption.CUSTOMER_NAME) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldGreenSecondary) },
                                onClick = {
                                    viewModel.setSortOption(PolicySortOption.CUSTOMER_NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Premium Amount (High to Low)", fontWeight = if (sortOption == PolicySortOption.PREMIUM_AMOUNT) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = ErrorRed) },
                                onClick = {
                                    viewModel.setSortOption(PolicySortOption.PREMIUM_AMOUNT)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // More Menu
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh Policy List") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = RoyalBluePrimary) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.triggerSync()
                                    Toast.makeText(context, "Refreshing policy records...", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Summary") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreenSecondary) },
                                onClick = {
                                    showMoreMenu = false
                                    val summaryText = buildString {
                                        appendLine("LIC POLICIES PORTFOLIO SUMMARY")
                                        appendLine("Total Policies: ${policies.size}")
                                        appendLine("Active: $activeCount | Overdue: $overdueCount")
                                        appendLine("----------------------------------")
                                        policies.take(10).forEach { p ->
                                            appendLine("• ${p.policyNumber} - ${p.customerName} - ${p.planName} (₹${"%.0f".format(p.premiumAmount)})")
                                        }
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, summaryText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Summary"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalBluePrimary)
            )
        },
        floatingActionButton = {
            val fabRotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                label = "fab_rotation"
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(end = 16.dp, bottom = 24.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = fabExpanded,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Action 1: Upload (56dp Green #34C759 Circular FAB with Label)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable {
                                fabExpanded = false
                                documentPickerLauncher.launch("*/*")
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Upload",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    documentPickerLauncher.launch("*/*")
                                },
                                containerColor = Color(0xFF34C759),
                                contentColor = Color.White,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 12.dp,
                                    hoveredElevation = 8.dp,
                                    focusedElevation = 8.dp
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color(0xFF34C759).copy(alpha = 0.5f))
                                    .testTag("fab_option_upload_policy")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Upload",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        // Action 2: Add Policy (56dp Orange Circular FAB with Label)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable {
                                fabExpanded = false
                                onAddPolicy()
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Add Policy",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onAddPolicy()
                                },
                                containerColor = AccentOrange,
                                contentColor = Color.White,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 12.dp,
                                    hoveredElevation = 8.dp,
                                    focusedElevation = 8.dp
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = AccentOrange.copy(alpha = 0.5f))
                                    .testTag("fab_option_add_policy")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Policy",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Main Floating Action Button (56dp Orange Circle)
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    shape = CircleShape,
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp,
                        hoveredElevation = 8.dp,
                        focusedElevation = 8.dp
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape, spotColor = AccentOrange.copy(alpha = 0.5f))
                        .testTag("add_policy_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (fabExpanded) "Close Menu" else "Policy Options",
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(fabRotation),
                        tint = Color.White
                    )
                }
            }
        }
    ) { padding ->
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
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SEARCH & FILTERS HEADER SECTION ITEM
                item(key = "policy_list_header") {
                    Surface(
                        color = RoyalBluePrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            if (isSearchExpanded) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("policy_list_search_input"),
                                    placeholder = {
                                        Text(
                                            text = "Search by Policy #, Customer Name, Mobile, Plan...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 13.sp
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = AccentOrangeLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.setSearchQuery("") },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color.White.copy(alpha = 0.9f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = NeutralSurfaceDark,
                                        unfocusedContainerColor = NeutralSurfaceDark,
                                        focusedBorderColor = AccentOrangeLight,
                                        unfocusedBorderColor = NeutralBorderDark,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // FILTER CHIPS ROW (All, Active, Due, Overdue)
                            val chipShape = RoundedCornerShape(12.dp)
                            val chipHeight = 38.dp
                            val minChipWidth = 84.dp

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = statusFilter == PolicyFilterStatus.ALL && dueFilter == PolicyFilterDue.ALL && modeFilter == PolicyModeFilter.ALL,
                                        onClick = {
                                            viewModel.setStatusFilter(PolicyFilterStatus.ALL)
                                            viewModel.setDueFilter(PolicyFilterDue.ALL)
                                            viewModel.setModeFilter(PolicyModeFilter.ALL)
                                        },
                                        label = {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                                                Text("All (${policies.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        shape = chipShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = NeutralSurfaceDark,
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = statusFilter == PolicyFilterStatus.ALL && dueFilter == PolicyFilterDue.ALL && modeFilter == PolicyModeFilter.ALL,
                                            borderColor = NeutralBorderDark,
                                            selectedBorderColor = AccentOrange
                                        ),
                                        modifier = Modifier.height(chipHeight).widthIn(min = minChipWidth)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = statusFilter == PolicyFilterStatus.ACTIVE,
                                        onClick = {
                                            viewModel.setStatusFilter(PolicyFilterStatus.ACTIVE)
                                            viewModel.setDueFilter(PolicyFilterDue.ALL)
                                        },
                                        label = {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                                                Text("🟢 Active", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        shape = chipShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldGreenSecondary,
                                            selectedLabelColor = Color.White,
                                            containerColor = NeutralSurfaceDark,
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = statusFilter == PolicyFilterStatus.ACTIVE,
                                            borderColor = NeutralBorderDark,
                                            selectedBorderColor = EmeraldGreenSecondary
                                        ),
                                        modifier = Modifier.height(chipHeight).widthIn(min = minChipWidth)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = dueFilter == PolicyFilterDue.DUE_TODAY,
                                        onClick = {
                                            viewModel.setDueFilter(PolicyFilterDue.DUE_TODAY)
                                            viewModel.setStatusFilter(PolicyFilterStatus.ALL)
                                        },
                                        label = {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                                                Text("🟡 Due", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        shape = chipShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = NeutralSurfaceDark,
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = dueFilter == PolicyFilterDue.DUE_TODAY,
                                            borderColor = NeutralBorderDark,
                                            selectedBorderColor = AccentOrange
                                        ),
                                        modifier = Modifier.height(chipHeight).widthIn(min = minChipWidth)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = dueFilter == PolicyFilterDue.OVERDUE || statusFilter == PolicyFilterStatus.LAPSED,
                                        onClick = {
                                            viewModel.setDueFilter(PolicyFilterDue.OVERDUE)
                                            viewModel.setStatusFilter(PolicyFilterStatus.ALL)
                                        },
                                        label = {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                                                Text("🔴 Overdue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        shape = chipShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ErrorRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = NeutralSurfaceDark,
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = dueFilter == PolicyFilterDue.OVERDUE || statusFilter == PolicyFilterStatus.LAPSED,
                                            borderColor = NeutralBorderDark,
                                            selectedBorderColor = ErrorRed
                                        ),
                                        modifier = Modifier.height(chipHeight).widthIn(min = minChipWidth)
                                    )
                                }
                            }
                        }
                    }
                }

                // BODY CONTENT (LOADING, ERROR, EMPTY OR POLICY CARDS)
                when {
                    isLoading -> {
                        item(key = "loading_state") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                PolicyListSkeletonLoader()
                            }
                        }
                    }
                    errorMessage != null -> {
                        item(key = "error_state") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = ErrorRedContainer)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Error Loading Policies", fontWeight = FontWeight.Bold, color = ErrorRed)
                                        Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                errorMessage = null
                                                viewModel.triggerSync()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    policies.isEmpty() -> {
                        item(key = "empty_state") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                if (searchQuery.isNotBlank() || statusFilter != PolicyFilterStatus.ALL || dueFilter != PolicyFilterDue.ALL || modeFilter != PolicyModeFilter.ALL) {
                                    NoMatchingRecordsEmptyState(
                                        query = searchQuery,
                                        onResetFilters = { viewModel.clearAllFilters() }
                                    )
                                } else {
                                    StandardEmptyState(
                                        title = "No Policies Found",
                                        description = "No policy records found in database. Tap '+ Add Policy' to create a new record.",
                                        icon = Icons.Outlined.FolderOff,
                                        actionLabel = "Add First Policy",
                                        onActionClick = onAddPolicy
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        items(policies, key = { it.id }) { policy ->
                            val customer = customers.find { it.id == policy.customerId }
                            val policyPayments = payments.filter { it.policyId == policy.id }
                            val outstanding = getPolicyOutstandingBalance(policy, policyPayments)

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            policyForPayment = policy
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            policyToEdit = policy
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        val color = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> EmeraldGreenSecondary
                                            SwipeToDismissBoxValue.EndToStart -> RoyalBluePrimary
                                            else -> Color.Transparent
                                        }
                                        val icon = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Payments
                                            SwipeToDismissBoxValue.EndToStart -> Icons.Default.Edit
                                            else -> Icons.Default.Circle
                                        }
                                        val text = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> "Collect Premium"
                                            SwipeToDismissBoxValue.EndToStart -> "Edit Policy"
                                            else -> ""
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(icon, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    },
                                    content = {
                                        PolicyCard(
                                            policy = policy,
                                            customer = customer,
                                            policyPayments = policyPayments,
                                            outstandingBalance = outstanding,
                                            onClick = { onSelectPolicy(policy) },
                                            onCollectPremium = { policyForPayment = policy },
                                            onEdit = { policyToEdit = policy },
                                            onEditPlanName = { policyForPlanEdit = policy },
                                            onDelete = { policyToDelete = policy },
                                            onShare = {
                                                sharePolicySummaryText(context, policy, customer, policyPayments)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // FILTER MODES BOTTOM SHEET
    if (showFilterMenu) {
        ModalBottomSheet(
            onDismissRequest = { showFilterMenu = false }
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
                        text = "Filter by Premium Mode",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = {
                        viewModel.setModeFilter(PolicyModeFilter.ALL)
                        showFilterMenu = false
                    }) {
                        Text("Reset Filter")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PolicyModeFilter.ALL to "All Modes",
                        PolicyModeFilter.YEARLY to "Yearly",
                        PolicyModeFilter.HALF_YEARLY to "Half-Yearly",
                        PolicyModeFilter.QUARTERLY to "Quarterly",
                        PolicyModeFilter.MONTHLY to "Monthly"
                    ).forEach { (mode, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (modeFilter == mode) RoyalBlueContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setModeFilter(mode)
                                    showFilterMenu = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (modeFilter == mode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (modeFilter == mode) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (modeFilter == mode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = RoyalBluePrimary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // DIALOGS
    if (policyForPlanEdit != null) {
        val currentPolicy = policyForPlanEdit!!
        var editedPlanName by remember { mutableStateOf(currentPolicy.planName) }

        AlertDialog(
            onDismissRequest = { policyForPlanEdit = null },
            title = { Text("Edit Plan Name", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Update Plan Name for Policy #${currentPolicy.policyNumber}:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedPlanName,
                        onValueChange = { editedPlanName = it },
                        label = { Text("Plan Name / Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedPlanName.isNotBlank()) {
                            viewModel.updatePolicy(currentPolicy.copy(planName = editedPlanName.trim()))
                            policyForPlanEdit = null
                            Toast.makeText(context, "Plan name updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Save Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { policyForPlanEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (policyForPayment != null) {
        val targetPolicy = policyForPayment!!
        PaymentCollectionDialog(
            policy = targetPolicy,
            customersList = customers,
            policiesList = policies,
            onDismiss = { policyForPayment = null },
            onSavePayment = { pol, paidAmt, mode, dateStr, notes ->
                viewModel.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    lateFee = 0.0,
                    paymentMode = mode,
                    receiptNo = "REC-${System.currentTimeMillis() % 100000}",
                    paymentDate = dateStr,
                    notes = notes,
                    onSuccess = {
                        policyForPayment = null
                        Toast.makeText(context, "Payment collected successfully", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (policyToEdit != null) {
        AddEditPolicyDialog(
            initialPolicy = policyToEdit,
            customersList = customers,
            existingPolicies = policies,
            onDismiss = { policyToEdit = null },
            onSave = { updatedPolicy ->
                viewModel.updatePolicy(updatedPolicy)
                policyToEdit = null
                Toast.makeText(context, "Policy updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (policyToDelete != null) {
        AlertDialog(
            onDismissRequest = { policyToDelete = null },
            title = { Text("Delete Policy Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete policy ${policyToDelete?.policyNumber}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        policyToDelete?.let { viewModel.deletePolicy(it) }
                        policyToDelete = null
                        Toast.makeText(context, "Policy record deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { policyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PolicyListSkeletonLoader() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.height(18.dp).fillMaxWidth(0.6f).background(Color.Gray.copy(alpha = 0.3f)))
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.4f).background(Color.Gray.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.height(30.dp).fillMaxWidth().background(Color.Gray.copy(alpha = 0.2f)))
                }
            }
        }
    }
}

@Composable
fun PolicyCard(
    policy: PolicyEntity,
    customer: CustomerEntity?,
    policyPayments: List<PaymentEntity> = emptyList(),
    outstandingBalance: Double,
    onClick: () -> Unit,
    onCollectPremium: () -> Unit,
    onEdit: () -> Unit,
    onEditPlanName: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val (planNameOnly, planCode) = remember(policy.planName) { parsePlanDetails(policy.planName) }
    val daysLeftInfo = remember(policy.dueDate, policy.status) { getDaysLeftBadgeInfo(policy.dueDate, policy.status) }
    val progressInfo = remember(policy, policyPayments) {
        calculateInstallmentProgress(policy, policyPayments)
    }

    val statusColor = when {
        policy.status.equals("Active", ignoreCase = true) -> EmeraldGreenSecondary
        policy.status.equals("Due", ignoreCase = true) || policy.status.equals("Upcoming", ignoreCase = true) -> AccentOrange
        policy.status.equals("Overdue", ignoreCase = true) || policy.status.equals("Lapsed", ignoreCase = true) -> ErrorRed
        else -> Color(0xFF37474F) // Matured / Slate
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = RoyalBluePrimary.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HEADER ROW: Gradient Avatar + Bold Name + Policy Number & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Premium Gradient Customer Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                )
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (customer?.name ?: policy.customerName).split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString("")
                                .ifEmpty { "C" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Policy #: ${policy.policyNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status Badge
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                policy.status.equals("Active", ignoreCase = true) -> "🟢 Active"
                                policy.status.equals("Due", ignoreCase = true) -> "🟡 Due"
                                policy.status.equals("Upcoming", ignoreCase = true) -> "🔵 Upcoming"
                                policy.status.equals("Overdue", ignoreCase = true) || policy.status.equals("Lapsed", ignoreCase = true) -> "🔴 Overdue"
                                else -> "⚫ Matured"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Days Left Badge
                    Surface(
                        color = daysLeftInfo.containerColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = daysLeftInfo.text,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = daysLeftInfo.containerColor,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plan Name Editable Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = RoyalBlueContainer.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditPlanName() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Plan Name",
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = planNameOnly,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RoyalBluePrimary
                    ) {
                        Text(
                            text = if (planCode.startsWith("Table") || planCode.startsWith("Plan")) planCode else "Table $planCode",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PROMINENT METRICS GRID (Premium Amount & Next Due Date)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Premium Amount Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "PREMIUM AMOUNT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${"%.0f".format(policy.premiumAmount)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Mode: ${policy.premiumMode}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Next Due Date Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "NEXT DUE DATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = policy.dueDate,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ErrorRed
                            )
                        )
                        if (outstandingBalance > 0) {
                            Text(
                                text = "Due: ₹${"%.0f".format(outstandingBalance)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                            )
                        } else {
                            Text(
                                text = "Paid Up To Date",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldGreenSecondary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PREMIUM PROGRESS BAR
            val formattedPercentage = if (progressInfo.overallPercentage % 1f == 0f) {
                "%.0f%%".format(progressInfo.overallPercentage)
            } else {
                "%.2f%%".format(progressInfo.overallPercentage)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Premium Progress",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "${progressInfo.completedInstallments} / ${progressInfo.totalInstallments} Paid ($formattedPercentage)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreenSecondary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressInfo.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = RoyalBluePrimary,
                    trackColor = RoyalBlueContainer.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹${"%.0f".format(progressInfo.currentInstallmentPaid)} / ₹${"%.0f".format(progressInfo.premiumAmount)} Current Premium",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "₹${"%.0f".format(progressInfo.currentInstallmentOutstanding)} Outstanding",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progressInfo.currentInstallmentOutstanding > 0) ErrorRed else EmeraldGreenSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // EQUAL-SIZED 42DP QUICK ACTION BUTTONS (4 Buttons: Collect, View Details, WhatsApp, Call)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Collect Premium (Royal Blue filled, white icon and text)
                Button(
                    onClick = onCollectPremium,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBluePrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = "Collect", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Collect", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                }

                // 2. Details (White outlined button, Royal Blue icon and text)
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBluePrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "View Details", tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Details", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBluePrimary))
                }

                // 3. WhatsApp (WhatsApp Green filled button, white icon and text)
                Button(
                    onClick = {
                        val phone = customer?.whatsapp?.ifEmpty { customer.mobile } ?: ""
                        if (phone.isNotEmpty()) {
                            val msg = "Hello ${policy.customerName}, regarding your LIC Policy #${policy.policyNumber} (Premium: ₹${policy.premiumAmount}, Due Date: ${policy.dueDate})."
                            launchWhatsAppMessage(context, phone, msg)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                }

                // 4. Call (White outlined button, Dark Blue icon and text)
                OutlinedButton(
                    onClick = {
                        val phone = customer?.mobile ?: ""
                        if (phone.isNotEmpty()) {
                            launchPhoneCall(context, phone)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = RoyalBlueDark
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBlueDark),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBlueDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Call", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoyalBlueDark))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyDetailScreen(
    policy: PolicyEntity,
    viewModel: LicViewModel,
    onEditPolicy: () -> Unit,
    onCollectPremium: () -> Unit,
    onBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val customer = remember(customers, policy.customerId) {
        customers.find { it.id == policy.customerId }
    }
    val policyPayments = remember(payments, policy.id) {
        payments.filter { it.policyId == policy.id }
    }
    val totalPaid = remember(policyPayments) { policyPayments.sumOf { it.paidAmount } }
    val lastPayment = remember(policyPayments) { policyPayments.maxByOrNull { it.paymentDate } }
    val outstandingBalance = remember(policy, policyPayments) {
        getPolicyOutstandingBalance(policy, policyPayments)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val (planNameOnly, planCode) = remember(policy.planName) { parsePlanDetails(policy.planName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        sharePolicySummaryText(context, policy, customer, policyPayments)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report")
                    }
                    IconButton(onClick = onEditPolicy) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Policy")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalBluePrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Customer Header Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CustomerAvatar(
                            name = customer?.name ?: policy.customerName,
                            photoUri = customer?.photoUri ?: "",
                            size = 52.dp
                        )
                        Column {
                            Text(
                                text = policy.customerName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            )
                            Text(
                                text = "Phone: ${customer?.mobile ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (customer != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { launchPhoneCall(context, customer.mobile) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlueContainer)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, "Hello ${customer.name}, regarding Policy No: ${policy.policyNumber}") },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreenContainer)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Comprehensive Policy Information Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Policy Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        StatusBadge(status = policy.status)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    DetailItem("Policy Number", policy.policyNumber)
                    DetailItem("Plan Name", planNameOnly)
                    DetailItem("Plan Code / Table", planCode)
                    DetailItem("Customer Name", policy.customerName)
                    DetailItem("Sum Assured", "₹${"%.2f".format(policy.sumAssured)}")
                    DetailItem("Premium Amount", "₹${"%.2f".format(policy.premiumAmount)}")
                    DetailItem("Premium Mode", policy.premiumMode)
                    DetailItem("Policy Term / PPT", "${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs")
                    DetailItem("Issue Date", policy.issueDate.ifEmpty { "N/A" })
                    DetailItem("Next Premium Due", policy.dueDate)
                    DetailItem("Grace Period", "${policy.gracePeriodDays} Days")
                    DetailItem("Maturity Date", policy.maturityDate)
                    DetailItem("Nominee Details", policy.nominee.ifEmpty { "N/A" })
                    DetailItem("Policy Status", policy.status)
                    DetailItem("Outstanding Balance", "₹${"%.2f".format(outstandingBalance)}")
                    DetailItem(
                        "Last Payment",
                        if (lastPayment != null) "₹${"%.2f".format(lastPayment.paidAmount)} on ${lastPayment.paymentDate}" else "No payments recorded"
                    )
                    DetailItem("Total Premium Paid", "₹${"%.2f".format(totalPaid)}")

                    val detailProgressInfo = remember(policy, policyPayments) {
                        calculateInstallmentProgress(policy, policyPayments)
                    }
                    val detailFormattedPct = if (detailProgressInfo.overallPercentage % 1f == 0f) {
                        "%.0f%%".format(detailProgressInfo.overallPercentage)
                    } else {
                        "%.2f%%".format(detailProgressInfo.overallPercentage)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Installment Progress",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "${detailProgressInfo.completedInstallments} / ${detailProgressInfo.totalInstallments} Paid ($detailFormattedPct)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreenSecondary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { detailProgressInfo.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = RoyalBluePrimary,
                            trackColor = RoyalBlueContainer.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "₹${"%.0f".format(detailProgressInfo.currentInstallmentPaid)} / ₹${"%.0f".format(detailProgressInfo.premiumAmount)} Current Premium",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "₹${"%.0f".format(detailProgressInfo.currentInstallmentOutstanding)} Outstanding",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (detailProgressInfo.currentInstallmentOutstanding > 0) ErrorRed else EmeraldGreenSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Record Payment",
                                onClick = onCollectPremium,
                                icon = Icons.Default.Payment,
                                modifier = Modifier.weight(1f),
                                containerColor = RoyalBluePrimary
                            )

                            PrimaryActionButton(
                                text = "Edit Policy",
                                onClick = onEditPolicy,
                                icon = Icons.Default.Edit,
                                modifier = Modifier.weight(1f),
                                containerColor = AccentOrange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Share Policy",
                                onClick = {
                                    sharePolicySummaryText(context, policy, customer, policyPayments)
                                },
                                icon = Icons.Default.Share,
                                modifier = Modifier.weight(1f),
                                containerColor = EmeraldGreenSecondary
                            )

                            PrimaryActionButton(
                                text = "Generate Report",
                                onClick = {
                                    coroutineScope.launch {
                                        val agentProfile = viewModel.agentProfile.value
                                        val reportData = com.example.pdf.PdfReportData(
                                            reportType = com.example.pdf.ReportType.POLICY_DETAILS,
                                            agentProfile = agentProfile,
                                            customer = customer,
                                            policy = policy,
                                            paymentList = policyPayments
                                        )
                                        val res = com.example.pdf.PdfReportGenerator.generatePdfReport(context, reportData)
                                        res.onSuccess { file ->
                                            com.example.pdf.PdfReportGenerator.openPdf(context, file)
                                        }
                                    }
                                },
                                icon = Icons.Default.PictureAsPdf,
                                modifier = Modifier.weight(1f),
                                containerColor = RoyalBlueLight
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Payment Receipts Section
            SectionHeader(
                title = "Payment History (${policyPayments.size})",
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (policyPayments.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "No premium payments recorded for this policy bond yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    policyPayments.forEach { payment ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Receipt: ${payment.receiptNumber}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Date: ${payment.paymentDate} • Mode: ${payment.paymentMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (payment.notes.isNotBlank()) {
                                        Text("Note: ${payment.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text("₹${"%.2f".format(payment.paidAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Policy Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove policy bond ${policy.policyNumber}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePolicy(policy)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Policy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PolicyFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isRequired: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                label = {
                    Text(
                        text = if (isRequired) "$label *" else label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                },
                placeholder = if (placeholder.isNotBlank()) {
                    { Text(placeholder, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) }
                } else null,
                leadingIcon = {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                readOnly = readOnly,
                isError = isError,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBluePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            if (onClick != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick() }
                )
            }
        }
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun PolicyDatePickerDialog(
    initialDateStr: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance()
    
    try {
        if (initialDateStr.isNotBlank()) {
            val parts = initialDateStr.split("-")
            if (parts.size == 3) {
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }
    } catch (e: Exception) { /* fallback to today */ }

    DisposableEffect(Unit) {
        val dpd = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        dpd.setOnDismissListener { onDismiss() }
        dpd.show()
        onDispose {
            dpd.dismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPolicyDialog(
    initialPolicy: PolicyEntity? = null,
    customersList: List<CustomerEntity>,
    existingPolicies: List<PolicyEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (PolicyEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1: Basics, 2: Details, 3: Review

    // Extract Nominee and Relation if present
    val initialNomineeRaw = initialPolicy?.nominee ?: ""
    val (parsedNomineeName, parsedNomineeRelation) = remember(initialNomineeRaw) {
        if (initialNomineeRaw.contains(" (") && initialNomineeRaw.endsWith(")")) {
            val namePart = initialNomineeRaw.substringBefore(" (").trim()
            val relPart = initialNomineeRaw.substringAfter(" (").removeSuffix(")").trim()
            Pair(namePart, relPart)
        } else {
            Pair(initialNomineeRaw, "")
        }
    }

    // Step 1 State
    var selectedCustomer by remember {
        mutableStateOf(customersList.find { it.id == initialPolicy?.customerId } ?: customersList.firstOrNull())
    }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showCustomerPickerDropdown by remember { mutableStateOf(false) }

    var policyNumber by remember { mutableStateOf(initialPolicy?.policyNumber ?: "") }
    var planName by remember { mutableStateOf(initialPolicy?.planName ?: "Jeevan Labh (936)") }
    var showPlanDropdown by remember { mutableStateOf(false) }
    var premiumAmountStr by remember { mutableStateOf(initialPolicy?.premiumAmount?.let { if (it > 0) it.toString() else "" } ?: "12000") }
    var sumAssuredStr by remember { mutableStateOf(initialPolicy?.sumAssured?.let { if (it > 0) it.toString() else "" } ?: "500000") }
    var premiumMode by remember { mutableStateOf(initialPolicy?.premiumMode ?: "Half-Yearly") }

    // Step 2 State
    var policyTermStr by remember { mutableStateOf(initialPolicy?.policyTerm?.toString() ?: "20") }
    var pptStr by remember { mutableStateOf(initialPolicy?.premiumPayingTerm?.toString() ?: "16") }
    var issueDate by remember { mutableStateOf(initialPolicy?.issueDate.takeIf { !it.isNullOrBlank() } ?: LocalDate.now().minusYears(2).toString()) }
    var dueDate by remember { mutableStateOf(initialPolicy?.dueDate ?: LocalDate.now().toString()) }
    var maturityDate by remember { mutableStateOf(initialPolicy?.maturityDate ?: LocalDate.now().plusYears(20).toString()) }
    var gracePeriodStr by remember { mutableStateOf(initialPolicy?.gracePeriodDays?.toString() ?: "30") }
    var status by remember { mutableStateOf(initialPolicy?.status ?: "Active") }
    var nomineeName by remember { mutableStateOf(parsedNomineeName) }
    var nomineeRelation by remember { mutableStateOf(parsedNomineeRelation) }

    // Validation & Date Picker states
    var customerError by remember { mutableStateOf<String?>(null) }
    var policyNumberError by remember { mutableStateOf<String?>(null) }
    var planError by remember { mutableStateOf<String?>(null) }
    var premiumError by remember { mutableStateOf<String?>(null) }
    var sumAssuredError by remember { mutableStateOf<String?>(null) }
    var activeDatePicker by remember { mutableStateOf<String?>(null) } // "ISSUE", "DUE", "MATURITY"
    var isSaving by remember { mutableStateOf(false) }

    val allLicPlans = remember {
        listOf(
            "Jeevan Labh (936)",
            "Jeevan Umang (945)",
            "Endowment Plan (914)",
            "Money Back Plan (920)",
            "Tech Term (854)",
            "SIIP (852)",
            "Jeevan Anand (915)",
            "Bima Jyoti (860)",
            "Cancer Cover (905)",
            "Jeevan Akshay VII (857)",
            "Jeevan Shanti (858)",
            "Nivesh Plus (849)",
            "Jeevan Lakshya (933)",
            "Single Premium Endowment (917)",
            "Jeevan Azad (868)",
            "Jeevan Amar (855)",
            "Dhan Sanchay (865)",
            "Dhan Rekha (863)",
            "Jeevan Utsav (871)",
            "Amritbaal (874)",
            "Jeevan Samarth (873)",
            "Index Plus (873)",
            "Bima Shree (948)",
            "Jeevan Shiromani (947)"
        )
    }

    val filteredPlans = remember(planName) {
        if (planName.isBlank()) allLicPlans else allLicPlans.filter { it.contains(planName, ignoreCase = true) }
    }

    val modeOptions = listOf("Monthly", "Quarterly", "Half-Yearly", "Yearly")
    val statusOptions = listOf("Active", "Due", "Lapsed", "Matured", "Paid-up")

    // Recalculate Next Due Date & Maturity Date automatically from Issue Date and Mode
    fun autoRecalculateDates(newIssueDate: String, mode: String, termStr: String) {
        try {
            val base = LocalDate.parse(newIssueDate)
            val nextDue = when (mode) {
                "Monthly" -> base.plusMonths(1)
                "Quarterly" -> base.plusMonths(3)
                "Half-Yearly" -> base.plusMonths(6)
                "Yearly" -> base.plusYears(1)
                else -> base.plusMonths(6)
            }
            dueDate = nextDue.toString()

            val termYears = termStr.toIntOrNull() ?: 20
            maturityDate = base.plusYears(termYears.toLong()).toString()
        } catch (e: Exception) {
            // Keep manually set dates
        }
    }

    // Date Picker Launcher
    if (activeDatePicker != null) {
        val initialVal = when (activeDatePicker) {
            "ISSUE" -> issueDate
            "DUE" -> dueDate
            else -> maturityDate
        }
        PolicyDatePickerDialog(
            initialDateStr = initialVal,
            onDateSelected = { selectedDate ->
                when (activeDatePicker) {
                    "ISSUE" -> {
                        issueDate = selectedDate
                        autoRecalculateDates(selectedDate, premiumMode, policyTermStr)
                    }
                    "DUE" -> dueDate = selectedDate
                    "MATURITY" -> maturityDate = selectedDate
                }
                activeDatePicker = null
            },
            onDismiss = { activeDatePicker = null }
        )
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .shadow(24.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))))
                            )
                            Text(
                                text = if (initialPolicy == null) "Add Policy Record" else "Edit Policy Info",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (currentStep) {
                                1 -> "Step 1 of 3: Policy Basics"
                                2 -> "Step 2 of 3: Policy Details"
                                else -> "Step 3 of 3: Review & Save"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color(0xFF60A5FA)
                        )
                    }
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step Indicator Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val steps = listOf("1. Basics", "2. Details", "3. Review")
                    steps.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isCurrent = currentStep == stepNum
                        val isCompleted = currentStep > stepNum

                        val bgBrush = when {
                            isCurrent -> Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))
                            isCompleted -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                            else -> Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155)))
                        }

                        Surface(
                            onClick = {
                                if (!isSaving) {
                                    if (stepNum == 1 || (stepNum == 2 && selectedCustomer != null && policyNumber.isNotBlank()) || (stepNum == 3 && currentStep >= 2)) {
                                        currentStep = stepNum
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .background(bgBrush, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent || isCompleted) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (currentStep) {
                            1 -> {
                                // STEP 1: Policy Basics
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Select Customer *",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    // Customer Selection Component
                                    if (selectedCustomer != null && !showCustomerPickerDropdown) {
                                        // Selected Customer Card with "Change Customer" button
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(1.5.dp, Color(0xFF60A5FA)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    brush = Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    CustomerAvatar(name = selectedCustomer!!.name, size = 40.dp)
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            selectedCustomer!!.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            "Mobile: ${selectedCustomer!!.mobile}",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 12.sp,
                                                                color = Color(0xFFCBD5E1)
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        showCustomerPickerDropdown = true
                                                        customerSearchQuery = ""
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "Change Customer",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Customer Search Field
                                        OutlinedTextField(
                                            value = customerSearchQuery,
                                            onValueChange = {
                                                customerSearchQuery = it
                                                customerError = null
                                            },
                                            placeholder = {
                                                Text(
                                                    "Search customer name or mobile...",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.PersonSearch,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            },
                                            trailingIcon = {
                                                if (customerSearchQuery.isNotBlank()) {
                                                    IconButton(onClick = { customerSearchQuery = "" }) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Clear",
                                                            tint = Color(0xFF94A3B8)
                                                        )
                                                    }
                                                } else if (selectedCustomer != null) {
                                                    IconButton(onClick = { showCustomerPickerDropdown = false }) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Cancel",
                                                            tint = Color(0xFF94A3B8)
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                unfocusedBorderColor = Color(0xFF334155),
                                                focusedContainerColor = Color(0xFF0F172A),
                                                unfocusedContainerColor = Color(0xFF0F172A),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Customer Search List Options
                                        val filteredCustomers = customersList.filter { cust ->
                                            customerSearchQuery.isBlank() ||
                                                    cust.name.contains(customerSearchQuery, ignoreCase = true) ||
                                                    cust.mobile.contains(customerSearchQuery)
                                        }

                                        if (filteredCustomers.isEmpty()) {
                                            Text(
                                                "No matching customers found. Please check spelling or add a client.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ErrorRed,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 160.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                filteredCustomers.take(8).forEach { cust ->
                                                    val isSelected = selectedCustomer?.id == cust.id
                                                    Surface(
                                                        onClick = {
                                                            selectedCustomer = cust
                                                            showCustomerPickerDropdown = false
                                                            customerError = null
                                                        },
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.25f) else Color(0xFF0F172A),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            CustomerAvatar(name = cust.name, size = 32.dp)
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    cust.name,
                                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color.White
                                                                    )
                                                                )
                                                                Text(
                                                                    "Mob: ${cust.mobile}",
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        color = Color(0xFF94A3B8)
                                                                    )
                                                                )
                                                            }
                                                            if (isSelected) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF60A5FA),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (customerError != null) {
                                        Text(
                                            customerError!!,
                                            color = ErrorRed,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        )
                                    }
                                }

                                // Policy Number Input with numeric keyboard & duplicate checking
                                PolicyFormField(
                                    value = policyNumber,
                                    onValueChange = {
                                        policyNumber = it
                                        val isDuplicate = existingPolicies.any { p ->
                                            p.policyNumber.trim().equals(it.trim(), ignoreCase = true) &&
                                                    p.id != (initialPolicy?.id ?: 0L)
                                        }
                                        if (isDuplicate) {
                                            policyNumberError = "Duplicate Policy Number! Already exists in records."
                                        } else {
                                            policyNumberError = null
                                        }
                                    },
                                    label = "Policy Number",
                                    leadingIcon = Icons.Default.ReceiptLong,
                                    placeholder = "e.g. 123456789",
                                    isRequired = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = policyNumberError != null,
                                    errorMessage = policyNumberError,
                                    testTag = "add_policy_number_input"
                                )

                                // Searchable Material3 ExposedDropdownMenu for LIC Plan
                                ExposedDropdownMenuBox(
                                    expanded = showPlanDropdown && filteredPlans.isNotEmpty(),
                                    onExpandedChange = { showPlanDropdown = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    PolicyFormField(
                                        value = planName,
                                        onValueChange = {
                                            planName = it
                                            showPlanDropdown = true
                                            planError = null
                                        },
                                        label = "LIC Plan Name & Code",
                                        leadingIcon = Icons.Default.Assignment,
                                        placeholder = "e.g. Jeevan Labh (936)",
                                        isRequired = true,
                                        isError = planError != null,
                                        errorMessage = planError,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPlanDropdown)
                                        },
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                                        testTag = "add_policy_plan_input"
                                    )

                                    ExposedDropdownMenu(
                                        expanded = showPlanDropdown && filteredPlans.isNotEmpty(),
                                        onDismissRequest = { showPlanDropdown = false },
                                        modifier = Modifier
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                            .clip(RoundedCornerShape(16.dp))
                                            .heightIn(max = 240.dp)
                                    ) {
                                        filteredPlans.forEach { p ->
                                            val isSelected = planName.equals(p, ignoreCase = true)
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = p,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) Color(0xFF60A5FA) else Color.White
                                                        )
                                                    )
                                                },
                                                onClick = {
                                                    planName = p
                                                    showPlanDropdown = false
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                                modifier = Modifier.background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else Color.Transparent)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PolicyFormField(
                                        value = premiumAmountStr,
                                        onValueChange = {
                                            premiumAmountStr = it
                                            premiumError = null
                                        },
                                        label = "Premium (₹)",
                                        leadingIcon = Icons.Default.AttachMoney,
                                        placeholder = "24500",
                                        isRequired = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        isError = premiumError != null,
                                        errorMessage = premiumError,
                                        modifier = Modifier.weight(1f),
                                        testTag = "add_policy_premium_input"
                                    )

                                    PolicyFormField(
                                        value = sumAssuredStr,
                                        onValueChange = {
                                            sumAssuredStr = it
                                            sumAssuredError = null
                                        },
                                        label = "Sum Assured (₹)",
                                        leadingIcon = Icons.Default.Shield,
                                        placeholder = "500000",
                                        isRequired = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        isError = sumAssuredError != null,
                                        errorMessage = sumAssuredError,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Premium Payment Mode (Equal width weight(1f), height 48dp, Green selected)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Premium Payment Mode",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        modeOptions.forEach { mode ->
                                            val isSelected = premiumMode == mode

                                            val animatedBgColor by animateColorAsState(
                                                targetValue = if (isSelected) Color(0xFF10B981) else Color(0xFF0F172A),
                                                animationSpec = tween(durationMillis = 200),
                                                label = "modeBg"
                                            )
                                            val animatedBorderColor by animateColorAsState(
                                                targetValue = if (isSelected) Color(0xFF34D399) else Color(0xFF334155),
                                                animationSpec = tween(durationMillis = 200),
                                                label = "modeBorder"
                                            )

                                            Surface(
                                                onClick = {
                                                    premiumMode = mode
                                                    autoRecalculateDates(issueDate, mode, policyTermStr)
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = animatedBgColor,
                                                border = BorderStroke(1.dp, animatedBorderColor),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                    }
                                                    Text(
                                                        text = mode,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                            fontSize = 11.sp,
                                                            textAlign = TextAlign.Center
                                                        ),
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // STEP 2: Policy Details
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PolicyFormField(
                                        value = policyTermStr,
                                        onValueChange = {
                                            policyTermStr = it
                                            autoRecalculateDates(issueDate, premiumMode, it)
                                        },
                                        label = "Policy Term (Yrs)",
                                        leadingIcon = Icons.Default.Timelapse,
                                        placeholder = "20",
                                        modifier = Modifier.weight(1f)
                                    )

                                    PolicyFormField(
                                        value = pptStr,
                                        onValueChange = { pptStr = it },
                                        label = "Paying Term (PPT)",
                                        leadingIcon = Icons.Default.Schedule,
                                        placeholder = "16",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                PolicyFormField(
                                    value = issueDate,
                                    onValueChange = { issueDate = it },
                                    label = "Issue Date",
                                    leadingIcon = Icons.Default.CalendarMonth,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "ISSUE" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "ISSUE" }) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                PolicyFormField(
                                    value = dueDate,
                                    onValueChange = { dueDate = it },
                                    label = "Next Premium Due Date",
                                    leadingIcon = Icons.Default.EventRepeat,
                                    placeholder = "YYYY-MM-DD",
                                    isRequired = true,
                                    readOnly = true,
                                    onClick = { activeDatePicker = "DUE" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "DUE" }) {
                                            Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    },
                                    testTag = "add_policy_due_date_input"
                                )

                                PolicyFormField(
                                    value = maturityDate,
                                    onValueChange = { maturityDate = it },
                                    label = "Maturity Date",
                                    leadingIcon = Icons.Default.EventAvailable,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "MATURITY" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "MATURITY" }) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                PolicyFormField(
                                    value = gracePeriodStr,
                                    onValueChange = { gracePeriodStr = it },
                                    label = "Grace Period (Days)",
                                    leadingIcon = Icons.Default.Timer,
                                    placeholder = "30"
                                )

                                Column {
                                    Text(
                                        "Policy Status",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(statusOptions) { st ->
                                            FilterChip(
                                                selected = status == st,
                                                onClick = { status = st },
                                                label = { Text(st, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PolicyFormField(
                                        value = nomineeName,
                                        onValueChange = { nomineeName = it },
                                        label = "Nominee Name",
                                        leadingIcon = Icons.Default.PersonOutline,
                                        placeholder = "e.g. Sunita Kumar",
                                        modifier = Modifier.weight(1.2f)
                                    )

                                    PolicyFormField(
                                        value = nomineeRelation,
                                        onValueChange = { nomineeRelation = it },
                                        label = "Relation",
                                        leadingIcon = Icons.Default.FamilyRestroom,
                                        placeholder = "e.g. Spouse, Son",
                                        modifier = Modifier.weight(0.8f)
                                    )
                                }
                            }

                            3 -> {
                                // STEP 3: Review & Confirm
                                val pAmt = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                val sAmt = sumAssuredStr.toDoubleOrNull() ?: 0.0

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Policy & Customer Summary",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBluePrimary
                                            )
                                            TextButton(onClick = { currentStep = 1 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CustomerAvatar(name = selectedCustomer?.name ?: "Client", size = 48.dp)
                                            Column {
                                                Text(selectedCustomer?.name ?: "No Customer Selected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text("Mobile: ${selectedCustomer?.mobile ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Policy Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(policyNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Plan Name & Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(planName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = RoyalBluePrimary)
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Premium Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("₹${"%.2f".format(pAmt)} ($premiumMode)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Sum Assured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("₹${"%.2f".format(sAmt)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Term & Schedule Details",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBluePrimary
                                            )
                                            TextButton(onClick = { currentStep = 2 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Policy Term / PPT:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$policyTermStr Yrs / $pptStr Yrs", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Issue Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(issueDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Next Due Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(dueDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Maturity Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(maturityDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Status & Grace Period:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$status ($gracePeriodStr Days Grace)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        if (nomineeName.isNotBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Nominee Details:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    if (nomineeRelation.isNotBlank()) "$nomineeName ($nomineeRelation)" else nomineeName,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Outstanding Balance Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = EmeraldGreenContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "Calculated Outstanding Balance",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                                            )
                                            Text(
                                                "₹${"%.2f".format(pAmt)}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = EmeraldGreenSecondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sticky Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep == 1) {
                        OutlinedButton(
                            onClick = { if (!isSaving) onDismiss() },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = {
                                if (currentStep == 1) {
                                    var valid = true
                                    if (selectedCustomer == null) {
                                        customerError = "Please select or search a customer"
                                        valid = false
                                    }
                                    if (policyNumber.isBlank()) {
                                        policyNumberError = "Policy Number is required"
                                        valid = false
                                    } else {
                                        // Check unique policy number
                                        val isDuplicate = existingPolicies.any {
                                            it.policyNumber.trim().equals(policyNumber.trim(), ignoreCase = true) &&
                                                    it.id != (initialPolicy?.id ?: 0L)
                                        }
                                        if (isDuplicate) {
                                            policyNumberError = "Policy Number already exists in records!"
                                            valid = false
                                        }
                                    }
                                    if (planName.isBlank()) {
                                        planError = "Plan Name is required"
                                        valid = false
                                    }

                                    val pVal = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                    if (pVal <= 0) {
                                        premiumError = "Enter valid premium > 0"
                                        valid = false
                                    }

                                    val sVal = sumAssuredStr.toDoubleOrNull() ?: 0.0
                                    if (sVal <= 0) {
                                        sumAssuredError = "Enter valid sum assured"
                                        valid = false
                                    } else if (sVal <= pVal) {
                                        sumAssuredError = "Sum Assured must be greater than Premium"
                                        valid = false
                                    }

                                    if (valid) {
                                        currentStep = 2
                                    }
                                } else if (currentStep == 2) {
                                    currentStep = 3
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Next Step", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // Save Button (Step 3)
                        Button(
                            onClick = {
                                val pAmt = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                val sAmt = sumAssuredStr.toDoubleOrNull() ?: 0.0
                                val pTerm = policyTermStr.toIntOrNull() ?: 20
                                val ppt = pptStr.toIntOrNull() ?: 16
                                val grace = gracePeriodStr.toIntOrNull() ?: 30
                                val cust = selectedCustomer

                                if (cust == null || policyNumber.isBlank() || pAmt <= 0) {
                                    Toast.makeText(context, "Please check form inputs", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSaving = true
                                coroutineScope.launch {
                                    val fullNomineeStr = if (nomineeRelation.isNotBlank()) {
                                        "${nomineeName.trim()} (${nomineeRelation.trim()})"
                                    } else {
                                        nomineeName.trim()
                                    }

                                    val policyToSave = (initialPolicy ?: PolicyEntity(
                                        policyNumber = policyNumber.trim(),
                                        customerId = cust.id,
                                        customerName = cust.name,
                                        planName = planName.trim(),
                                        premiumAmount = pAmt,
                                        sumAssured = sAmt,
                                        premiumMode = premiumMode,
                                        dueDate = dueDate.trim(),
                                        maturityDate = maturityDate.trim(),
                                        status = status,
                                        nominee = fullNomineeStr,
                                        policyTerm = pTerm,
                                        premiumPayingTerm = ppt,
                                        issueDate = issueDate.trim(),
                                        gracePeriodDays = grace
                                    )).copy(
                                        policyNumber = policyNumber.trim(),
                                        customerId = cust.id,
                                        customerName = cust.name,
                                        planName = planName.trim(),
                                        premiumAmount = pAmt,
                                        sumAssured = sAmt,
                                        premiumMode = premiumMode,
                                        dueDate = dueDate.trim(),
                                        maturityDate = maturityDate.trim(),
                                        status = status,
                                        nominee = fullNomineeStr,
                                        policyTerm = pTerm,
                                        premiumPayingTerm = ppt,
                                        issueDate = issueDate.trim(),
                                        gracePeriodDays = grace
                                    )

                                    delay(400) // Brief feedback UX
                                    onSave(policyToSave)
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_policy_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...")
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Policy", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}


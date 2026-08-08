package com.example.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.local.AppSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.SearchFilterOption
import com.example.ui.components.*
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class TodaysWorkTab {
    DUE_TODAY,
    FOLLOW_UPS,
    BIRTHDAYS,
    MATURITY,
    PENDING_COLLECTIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: LicViewModel,
    onNavigateToCustomers: () -> Unit,
    onNavigateToPolicies: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToPayments: () -> Unit,
    onNavigateToReports: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onAddCustomer: () -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit,
    onNavigateToCustomerPaymentHistory: ((CustomerEntity) -> Unit)? = null
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSearchFilters by viewModel.selectedSearchFilters.collectAsState()

    var activeWorkTab by remember { mutableStateOf(TodaysWorkTab.DUE_TODAY) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Dynamic greeting based on current time
    val timeOfDayGreeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Formatted date string
    val formattedTodayDate = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy"))
        } catch (e: Exception) {
            LocalDate.now().toString()
        }
    }

    val todayStr = remember { LocalDate.now().toString() }
    val currentMonth = remember { LocalDate.now().monthValue }

    // Search Filtering
    val searchFilteredPolicies = remember(policies, searchQuery) {
        if (searchQuery.isBlank()) policies else {
            policies.filter { p ->
                p.customerName.contains(searchQuery, ignoreCase = true) ||
                p.policyNumber.contains(searchQuery, ignoreCase = true) ||
                p.planName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val searchFilteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers else {
            customers.filter { c ->
                c.name.contains(searchQuery, ignoreCase = true) ||
                c.mobile.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Categorized lists for Today's Work
    val dueTodayPolicies = remember(searchFilteredPolicies, todayStr) {
        searchFilteredPolicies.filter { it.dueDate == todayStr || it.status == "Lapsed" }
    }

    val followUpPolicies = remember(searchFilteredPolicies) {
        searchFilteredPolicies.filter { it.status == "Lapsed" }
    }

    val birthdayCustomers = remember(searchFilteredCustomers, currentMonth) {
        searchFilteredCustomers.filter {
            val dobMonth = try { LocalDate.parse(it.dob).monthValue } catch (e: Exception) { -1 }
            dobMonth == currentMonth
        }
    }

    val maturityPolicies = remember(searchFilteredPolicies) {
        searchFilteredPolicies.filter { it.status == "Paid-up" }
    }

    val pendingCollectionPolicies = remember(searchFilteredPolicies) {
        searchFilteredPolicies.filter { it.status == "Active" }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                ) {
            // -------------------------------------------------------------
            // 1. HEADER SECTION (PREMIUM DARK BLUE THEME)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                RoyalBluePrimary,
                                RoyalBlueDark
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    // Profile + Greeting + Date + Bell Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Profile Picture & Greeting
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Clickable Profile Photo / Avatar (48dp)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { onNavigateToSettings() }
                                    .testTag("dashboard_profile_photo")
                            ) {
                                val currentPhotoUri = agentProfile?.photoUri.orEmpty()
                                val agentInitials = (agentProfile?.agentName ?: "Pintu Ojha")
                                    .split(" ")
                                    .mapNotNull { it.firstOrNull()?.toString() }
                                    .joinToString("")
                                    .take(2)
                                    .ifEmpty { "PO" }
                                    .uppercase()

                                AnimatedContent(
                                    targetState = currentPhotoUri,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.85f))
                                            .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f))
                                    },
                                    label = "DashboardProfilePhotoAnimation"
                                ) { uri ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(RoyalBluePrimary)
                                            .border(2.dp, AccentOrange, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (uri.isNotBlank()) {
                                            coil.compose.AsyncImage(
                                                model = uri,
                                                contentDescription = "Agent Profile Photo",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.radialGradient(
                                                            colors = listOf(Color(0xFF3B82F6), RoyalBluePrimary)
                                                        ),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = agentInitials,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 16.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "$timeOfDayGreeting 👋",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = AccentOrangeLight,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                )
                                Text(
                                    text = agentProfile?.agentName ?: "Pintu Ojha",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedTodayDate,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: Notification Bell & Compact Club Member Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Club Member Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                AccentOrange.copy(alpha = 0.25f),
                                                AccentOrangeLight.copy(alpha = 0.4f)
                                            )
                                        )
                                    )
                                    .border(1.dp, AccentOrange.copy(alpha = 0.6f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = AccentOrangeLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Club Member",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            // Notification Bell
                            IconButton(
                                onClick = onNavigateToReminders,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .testTag("dashboard_notification_bell")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (stats.dueTodayCount > 0) {
                                            Badge(
                                                containerColor = ErrorRed,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = if (stats.dueTodayCount > 99) "99+" else stats.dueTodayCount.toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Reminders & Notifications",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // -------------------------------------------------------------
                    // 2. SEARCH BAR & FILTER OPTIONS
                    // -------------------------------------------------------------
                    SearchBarComponent(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        placeholderText = "Search customer, mobile, policy...",
                        testTag = "dashboard_search_input",
                        selectedFilters = selectedSearchFilters,
                        onFilterClick = { showFilterBottomSheet = true },
                        onApplyFilters = { viewModel.setSearchFilters(it) },
                        onResetFilters = { viewModel.resetSearchFilters() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // -------------------------------------------------------------
                    // 2B. HORIZONTALLY SCROLLABLE FILTER CHIPS
                    // -------------------------------------------------------------
                    DashboardFilterChipsRow(
                        selectedFilters = selectedSearchFilters,
                        onSelectFilter = { option ->
                            if (option == null) {
                                viewModel.resetSearchFilters()
                            } else {
                                val updated = if (selectedSearchFilters.contains(option)) {
                                    selectedSearchFilters - option
                                } else {
                                    selectedSearchFilters + option
                                }
                                viewModel.setSearchFilters(updated)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -------------------------------------------------------------
            // 3. QUICK ACTIONS (3x3 GRID, EQUAL SPACING, 28DP FILLED ICONS)
            // -------------------------------------------------------------
            SectionHeader(
                title = "Quick Actions",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Add Client | Add Policy | Payment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionItemCard(
                        title = "Add Client",
                        icon = Icons.Default.PersonAdd,
                        iconGradient = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                        onClick = onAddCustomer,
                        modifier = Modifier.weight(1f),
                        testTag = "action_add_customer"
                    )

                    QuickActionItemCard(
                        title = "Add Policy",
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        iconGradient = listOf(Color(0xFFF97316), Color(0xFFC2410C)),
                        onClick = onAddPolicy,
                        modifier = Modifier.weight(1f),
                        testTag = "action_add_policy"
                    )

                    QuickActionItemCard(
                        title = "Payment",
                        icon = Icons.Default.Payments,
                        iconGradient = listOf(Color(0xFF10B981), Color(0xFF047857)),
                        onClick = onNavigateToPayments,
                        modifier = Modifier.weight(1f),
                        testTag = "action_record_payment"
                    )
                }

                // Row 2: Reminder | Payment History | Reports
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionItemCard(
                        title = "Reminder",
                        icon = Icons.Default.NotificationsActive,
                        iconGradient = listOf(Color(0xFFEF4444), Color(0xFFB91C1C)),
                        onClick = onNavigateToReminders,
                        modifier = Modifier.weight(1f),
                        testTag = "action_send_reminder"
                    )

                    QuickActionItemCard(
                        title = "Payment History",
                        icon = Icons.Default.History,
                        iconGradient = listOf(Color(0xFFA855F7), Color(0xFF7E22CE)),
                        onClick = onNavigateToPayments,
                        modifier = Modifier.weight(1f),
                        testTag = "action_customer_payment_history"
                    )

                    QuickActionItemCard(
                        title = "Reports",
                        icon = Icons.Default.PieChart,
                        iconGradient = listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
                        onClick = onNavigateToReports,
                        modifier = Modifier.weight(1f),
                        testTag = "action_reports"
                    )
                }

                // Row 3: Calendar | Documents | Scan QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionItemCard(
                        title = "Calendar",
                        icon = Icons.Default.CalendarMonth,
                        iconGradient = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(1f),
                        testTag = "action_calendar"
                    )

                    QuickActionItemCard(
                        title = "Documents",
                        icon = Icons.Default.FolderOpen,
                        iconGradient = listOf(Color(0xFF14B8A6), Color(0xFF0F766E)),
                        onClick = onNavigateToDocuments,
                        modifier = Modifier.weight(1f),
                        testTag = "action_documents"
                    )

                    QuickActionItemCard(
                        title = "Scan QR",
                        icon = Icons.Default.QrCodeScanner,
                        iconGradient = listOf(Color(0xFF06B6D4), Color(0xFF0E7490)),
                        onClick = { showQrDialog = true },
                        modifier = Modifier.weight(1f),
                        testTag = "action_scan_qr"
                    )
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // -------------------------------------------------------------
            // 4. DASHBOARD OVERVIEW (6 CARDS IN 2-COLUMN GRID)
            // -------------------------------------------------------------
            SectionHeader(
                title = "Dashboard Overview",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Total Customers & Total Policies
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardStatCard(
                        title = "Total Customers",
                        value = stats.totalCustomers.toString(),
                        subtitle = "Active Clients",
                        icon = Icons.Default.People,
                        iconBgColor = RoyalBlueContainer,
                        iconTintColor = RoyalBluePrimary,
                        onClick = onNavigateToCustomers,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_total_customers"
                    )
                    DashboardStatCard(
                        title = "Total Policies",
                        value = stats.totalPolicies.toString(),
                        subtitle = "Portfolios",
                        icon = Icons.Default.FolderSpecial,
                        iconBgColor = AccentOrangeContainer,
                        iconTintColor = AccentOrange,
                        onClick = onNavigateToPolicies,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_total_policies"
                    )
                }

                // Row 2: Due Today & Due This Month
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardStatCard(
                        title = "Due Today",
                        value = stats.dueTodayCount.toString(),
                        subtitle = "₹${"%.0f".format(stats.dueTodayAmount)}",
                        icon = Icons.Default.Alarm,
                        iconBgColor = ErrorRedContainer,
                        iconTintColor = ErrorRed,
                        onClick = onNavigateToReminders,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_due_today"
                    )
                    DashboardStatCard(
                        title = "Due This Month",
                        value = stats.dueThisMonthCount.toString(),
                        subtitle = "₹${"%.0f".format(stats.dueThisMonthAmount)}",
                        icon = Icons.Default.CalendarMonth,
                        iconBgColor = AccentOrangeContainer,
                        iconTintColor = AccentOrange,
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_due_month"
                    )
                }

                // Row 3: Today's Collection & Outstanding Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardStatCard(
                        title = "Today's Collection",
                        value = "₹${"%.0f".format(stats.premiumCollectedTotal)}",
                        subtitle = "Payments Recd",
                        icon = Icons.Default.CheckCircle,
                        iconBgColor = EmeraldGreenContainer,
                        iconTintColor = EmeraldGreenSecondary,
                        onClick = onNavigateToPayments,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_collected"
                    )
                    DashboardStatCard(
                        title = "Outstanding Balance",
                        value = "₹${"%.0f".format(stats.outstandingAmount)}",
                        subtitle = "Pending Dues",
                        icon = Icons.Default.AccountBalanceWallet,
                        iconBgColor = ErrorRedContainer,
                        iconTintColor = ErrorRed,
                        onClick = onNavigateToReminders,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_outstanding"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // -------------------------------------------------------------
            // 5. TODAY'S WORK SECTION (CAPSULE FILTER TABS & CARDS)
            // -------------------------------------------------------------
            SectionHeader(
                title = "Today's Work",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Rounded Capsule Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    WorkTabFilterChip(
                        text = "Premium Due (${dueTodayPolicies.size})",
                        selected = activeWorkTab == TodaysWorkTab.DUE_TODAY,
                        onClick = { activeWorkTab = TodaysWorkTab.DUE_TODAY }
                    )
                }
                item {
                    WorkTabFilterChip(
                        text = "Follow-ups (${followUpPolicies.size})",
                        selected = activeWorkTab == TodaysWorkTab.FOLLOW_UPS,
                        onClick = { activeWorkTab = TodaysWorkTab.FOLLOW_UPS }
                    )
                }
                item {
                    WorkTabFilterChip(
                        text = "Birthdays (${birthdayCustomers.size})",
                        selected = activeWorkTab == TodaysWorkTab.BIRTHDAYS,
                        onClick = { activeWorkTab = TodaysWorkTab.BIRTHDAYS }
                    )
                }
                item {
                    WorkTabFilterChip(
                        text = "Maturity (${maturityPolicies.size})",
                        selected = activeWorkTab == TodaysWorkTab.MATURITY,
                        onClick = { activeWorkTab = TodaysWorkTab.MATURITY }
                    )
                }
                item {
                    WorkTabFilterChip(
                        text = "Pending Collection (${pendingCollectionPolicies.size})",
                        selected = activeWorkTab == TodaysWorkTab.PENDING_COLLECTIONS,
                        onClick = { activeWorkTab = TodaysWorkTab.PENDING_COLLECTIONS }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Today's Work Customer Cards List
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeWorkTab) {
                    TodaysWorkTab.DUE_TODAY -> {
                        if (dueTodayPolicies.isEmpty()) {
                            EmptyWorkStateCard("No premium dues pending for today!")
                        } else {
                            dueTodayPolicies.take(8).forEach { policy ->
                                val custMobile = remember(customers, policy) {
                                    customers.find { it.id == policy.customerId }?.mobile ?: ""
                                }
                                WorkPolicyItemCard(
                                    policy = policy,
                                    customerMobile = custMobile,
                                    onCollect = { onCollectPremium(policy) },
                                    onCall = { launchPhoneCall(context, custMobile) },
                                    onRemind = {
                                        val msg = viewModel.generatePremiumReminderMsg(
                                            customerName = policy.customerName,
                                            policyNo = policy.policyNumber,
                                            planName = policy.planName,
                                            amount = policy.premiumAmount,
                                            dueDate = policy.dueDate
                                        )
                                        launchWhatsAppMessage(context, custMobile, msg)
                                    }
                                )
                            }
                        }
                    }

                    TodaysWorkTab.FOLLOW_UPS -> {
                        if (followUpPolicies.isEmpty()) {
                            EmptyWorkStateCard("No lapsed policies requiring follow-up.")
                        } else {
                            followUpPolicies.take(8).forEach { policy ->
                                val custMobile = remember(customers, policy) {
                                    customers.find { it.id == policy.customerId }?.mobile ?: ""
                                }
                                WorkPolicyItemCard(
                                    policy = policy,
                                    customerMobile = custMobile,
                                    onCollect = { onCollectPremium(policy) },
                                    onCall = { launchPhoneCall(context, custMobile) },
                                    onRemind = {
                                        val msg = viewModel.generatePremiumReminderMsg(
                                            customerName = policy.customerName,
                                            policyNo = policy.policyNumber,
                                            planName = policy.planName,
                                            amount = policy.premiumAmount,
                                            dueDate = policy.dueDate
                                        )
                                        launchWhatsAppMessage(context, custMobile, msg)
                                    }
                                )
                            }
                        }
                    }

                    TodaysWorkTab.BIRTHDAYS -> {
                        if (birthdayCustomers.isEmpty()) {
                            EmptyWorkStateCard("No customer birthdays this month.")
                        } else {
                            birthdayCustomers.take(8).forEach { customer ->
                                WorkBirthdayItemCard(
                                    customer = customer,
                                    onWish = {
                                        val msg = viewModel.generateBirthdayWishMsg(customer.name)
                                        launchWhatsAppMessage(context, customer.mobile, msg)
                                    }
                                )
                            }
                        }
                    }

                    TodaysWorkTab.MATURITY -> {
                        if (maturityPolicies.isEmpty()) {
                            EmptyWorkStateCard("No maturity policies found in current view.")
                        } else {
                            maturityPolicies.take(8).forEach { policy ->
                                val custMobile = remember(customers, policy) {
                                    customers.find { it.id == policy.customerId }?.mobile ?: ""
                                }
                                WorkPolicyItemCard(
                                    policy = policy,
                                    customerMobile = custMobile,
                                    onCollect = { onCollectPremium(policy) },
                                    onCall = { launchPhoneCall(context, custMobile) },
                                    onRemind = { }
                                )
                            }
                        }
                    }

                    TodaysWorkTab.PENDING_COLLECTIONS -> {
                        if (pendingCollectionPolicies.isEmpty()) {
                            EmptyWorkStateCard("No pending collections found.")
                        } else {
                            pendingCollectionPolicies.take(8).forEach { policy ->
                                val custMobile = remember(customers, policy) {
                                    customers.find { it.id == policy.customerId }?.mobile ?: ""
                                }
                                WorkPolicyItemCard(
                                    policy = policy,
                                    customerMobile = custMobile,
                                    onCollect = { onCollectPremium(policy) },
                                    onCall = { launchPhoneCall(context, custMobile) },
                                    onRemind = {
                                        val msg = viewModel.generatePremiumReminderMsg(
                                            customerName = policy.customerName,
                                            policyNo = policy.policyNumber,
                                            planName = policy.planName,
                                            amount = policy.premiumAmount,
                                            dueDate = policy.dueDate
                                        )
                                        launchWhatsAppMessage(context, custMobile, msg)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }

        // -------------------------------------------------------------
        // 6. FLOATING ACTION BUTTON (FAB) - Opens Existing Add Client Screen
        // -------------------------------------------------------------
        FloatingActionButton(
            onClick = onAddCustomer,
            containerColor = AccentOrange,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .size(60.dp)
                .testTag("dashboard_add_client_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Client",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // -------------------------------------------------------------
    // 7. PAYMENT LINK & SCAN QR MODAL
    // -------------------------------------------------------------
    if (showQrDialog) {
        PaymentLinkModal(
            onDismiss = { showQrDialog = false },
            duePolicies = dueTodayPolicies
        )
    }

    if (showFilterBottomSheet) {
        SearchFilterBottomSheet(
            initialFilters = selectedSearchFilters,
            onApply = { viewModel.setSearchFilters(it) },
            onReset = { viewModel.resetSearchFilters() },
            onDismiss = { showFilterBottomSheet = false }
        )
    }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPOSABLES
// -------------------------------------------------------------

@Composable
fun FabOptionRow(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shadowElevation = 2.dp
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun QuickActionItemCard(
    title: String,
    icon: ImageVector,
    iconGradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Card press scale animation (100% -> 95% -> 100%)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_scale"
    )

    // Icon bounce scale animation (1.0 -> 1.15 -> 1.0)
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_icon_scale"
    )

    // Elevation (14dp default -> 18dp on press)
    val elevationState by animateDpAsState(
        targetValue = if (isPressed) 18.dp else 14.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_elevation"
    )

    // Border: 1dp #3A4E6B 30% opacity when idle -> 2dp #38BDF8 glowing on press
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF38BDF8) else Color(0xFF3A4E6B).copy(alpha = 0.30f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_border_color"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_border_width"
    )

    // Soft blue glowing shadow
    val shadowColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF38BDF8).copy(alpha = 0.60f) else Color(0xFF2563EB).copy(alpha = 0.25f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "quick_action_shadow_color"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(130.dp)
            .testTag(testTag)
            .shadow(
                elevation = elevationState,
                shape = RoundedCornerShape(22.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = borderWidth,
            color = borderColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF263B55),
                            Color(0xFF162131)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 56dp Circular Background with Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            spotColor = iconGradient.first().copy(alpha = 0.4f),
                            ambientColor = iconGradient.first().copy(alpha = 0.2f)
                        )
                        .background(
                            brush = Brush.verticalGradient(iconGradient),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun WorkTabFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = if (selected) RoyalBluePrimary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun WorkPolicyItemCard(
    policy: PolicyEntity,
    customerMobile: String = "",
    onCollect: () -> Unit,
    onRemind: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, Customer Name, Policy #, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CustomerAvatar(
                        name = policy.customerName,
                        size = 42.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pol #: ${policy.policyNumber} • ${policy.planName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Container: Premium, Due Date & Outstanding Amount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Premium",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "₹${"%.0f".format(policy.premiumAmount)} (${policy.premiumMode})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RoyalBluePrimary,
                            fontSize = 13.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Due Date",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = policy.dueDate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Outstanding",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "₹${"%.0f".format(policy.premiumAmount)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row: Call, WhatsApp, Green Collect Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call & WhatsApp with 16dp spacing
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Button (Material3 Filled Icon Button, Blue background)
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RoyalBluePrimary)
                            .testTag("action_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Customer",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // WhatsApp Button (Material3 Filled Icon Button, Green background)
                    IconButton(
                        onClick = onRemind,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenSecondary)
                            .testTag("action_whatsapp")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "WhatsApp Reminder",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Green Collect Button (Material3 Filled Button, Rounded 16dp)
                Button(
                    onClick = onCollect,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                    modifier = Modifier.testTag("action_collect")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Collect",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentLinkModal(
    onDismiss: () -> Unit,
    duePolicies: List<PolicyEntity> = emptyList()
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettingsManager.getSettings(context) }

    var accountHolderName by remember { mutableStateOf(appSettings.accountHolderName) }
    var upiVpaText by remember { mutableStateOf(appSettings.upiVpaId) }

    var selectedPolicy by remember { mutableStateOf(duePolicies.firstOrNull()) }
    var customerNameText by remember(selectedPolicy) {
        mutableStateOf(selectedPolicy?.customerName ?: "Valued Customer")
    }
    var policyNumberText by remember(selectedPolicy) {
        mutableStateOf(selectedPolicy?.policyNumber ?: "LIC-POL-1001")
    }
    var amountText by remember(selectedPolicy) {
        mutableStateOf(selectedPolicy?.premiumAmount?.toInt()?.toString() ?: "5000")
    }

    val formattedAmount = amountText.ifEmpty { "0" }
    val pNo = policyNumberText.ifEmpty { "LIC-POL" }
    val encodedName = Uri.encode(accountHolderName)
    val upiPayLink = remember(upiVpaText, accountHolderName, formattedAmount, pNo) {
        "upi://pay?pa=$upiVpaText&pn=$encodedName&am=$formattedAmount&tn=${Uri.encode("LIC Policy $pNo")}&cu=INR"
    }

    val qrBitmap = remember(upiPayLink) {
        com.example.util.QrCodeGenerator.generateQrBitmap(upiPayLink, size = 500)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoyalBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Payment Link & Auto QR",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Auto Generated UPI Payment QR Code",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Customer & Policy Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customerNameText,
                        onValueChange = { customerNameText = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = policyNumberText,
                        onValueChange = { policyNumberText = it },
                        label = { Text("Policy No.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Account & Amount Input Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = upiVpaText,
                        onValueChange = { upiVpaText = it },
                        label = { Text("UPI ID") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Account Holder Name Field
                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { accountHolderName = it },
                    label = { Text("Account Holder Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Rendered Auto-Generated UPI QR Code Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Auto Generated Payment QR",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = RoyalBluePrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan with Google Pay / PhonePe / Paytm / BHIM",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "₹$formattedAmount • VPA: $upiVpaText",
                            style = TextStyle(
                                color = AccentOrangeLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Text(
                    text = "Quick Actions:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Row 1: Copy UPI ID, Copy Link, Download QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Copy UPI ID
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiVpaText))
                            Toast.makeText(context, "UPI ID copied: $upiVpaText", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy VPA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Copy Link
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UPI Payment Link", upiPayLink))
                            Toast.makeText(context, "Payment Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Link", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download QR
                    OutlinedButton(
                        onClick = {
                            val cardBitmap = com.example.util.QrCodeGenerator.createBrandedQrCardBitmap(
                                accountHolderName = accountHolderName,
                                upiId = upiVpaText,
                                amount = formattedAmount,
                                policyNumber = pNo,
                                customerName = customerNameText
                            )
                            val uri = com.example.util.QrCodeGenerator.saveQrBitmapToGallery(context, cardBitmap, "LIC_Payment_QR_$pNo")
                            if (uri != null) {
                                Toast.makeText(context, "QR saved to Gallery/Pictures/LIC_QR", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Could not save QR code image", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Row 2: Share QR, WhatsApp, Open UPI App
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Share QR Image
                    Button(
                        onClick = {
                            val cardBitmap = com.example.util.QrCodeGenerator.createBrandedQrCardBitmap(
                                accountHolderName = accountHolderName,
                                upiId = upiVpaText,
                                amount = formattedAmount,
                                policyNumber = pNo,
                                customerName = customerNameText
                            )
                            val cacheUri = com.example.util.QrCodeGenerator.saveQrBitmapToCache(context, cardBitmap)
                            if (cacheUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, cacheUri)
                                    putExtra(Intent.EXTRA_TEXT, "LIC Premium Payment QR for ₹$formattedAmount\nPolicy No: $pNo\nUPI ID: $upiVpaText\n$upiPayLink")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Payment QR"))
                            } else {
                                Toast.makeText(context, "Could not prepare QR image for sharing", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // WhatsApp Message
                    Button(
                        onClick = {
                            val custName = customerNameText.ifEmpty { "Valued Customer" }
                            val whatsAppMsg = """
Dear $custName,

Your LIC premium of ₹$formattedAmount for Policy No. $pNo is due.

Please pay using the secure UPI link below:
$upiPayLink

UPI ID: $upiVpaText ($accountHolderName)

Thank you.
""".trimIndent()
                            launchWhatsAppMessage(context, "", whatsAppMsg)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Open UPI App
                    Button(
                        onClick = {
                            val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiPayLink))
                            try {
                                context.startActivity(Intent.createChooser(upiIntent, "Pay via UPI App"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI app available on this device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay UPI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable
fun WorkBirthdayItemCard(
    customer: CustomerEntity,
    onWish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
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
                CustomerAvatar(name = customer.name, size = 42.dp)

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "🎂 DOB: ${customer.dob} | 📱 ${customer.mobile}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            SecondaryActionButton(
                text = "Wish 🎂",
                onClick = onWish,
                icon = Icons.Default.Cake,
                containerColor = AccentOrangeContainer,
                contentColor = AccentOrange
            )
        }
    }
}

@Composable
fun EmptyWorkStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldGreenSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private data class DashboardFilterChipItem(
    val label: String,
    val option: SearchFilterOption?
)

private val dashboardFilterChipList = listOf(
    DashboardFilterChipItem("All", null),
    DashboardFilterChipItem("Today Due", SearchFilterOption.TODAY_DUE),
    DashboardFilterChipItem("Tomorrow", SearchFilterOption.TOMORROW_DUE),
    DashboardFilterChipItem("This Week", SearchFilterOption.THIS_WEEK),
    DashboardFilterChipItem("This Month", SearchFilterOption.THIS_MONTH),
    DashboardFilterChipItem("Overdue", SearchFilterOption.OVERDUE),
    DashboardFilterChipItem("Paid", SearchFilterOption.PAID),
    DashboardFilterChipItem("Unpaid", SearchFilterOption.UNPAID),
    DashboardFilterChipItem("Quarterly", SearchFilterOption.QUARTERLY),
    DashboardFilterChipItem("Half-Yearly", SearchFilterOption.HALF_YEARLY),
    DashboardFilterChipItem("Yearly", SearchFilterOption.YEARLY)
)

@Composable
fun DashboardFilterChipsRow(
    selectedFilters: Set<SearchFilterOption>,
    onSelectFilter: (SearchFilterOption?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Find target index of selected filter chip to automatically scroll into view
    val selectedIndex = remember(selectedFilters) {
        if (selectedFilters.isEmpty()) {
            0
        } else {
            val idx = dashboardFilterChipList.indexOfFirst { item ->
                item.option != null && selectedFilters.contains(item.option)
            }
            if (idx >= 0) idx else 0
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(dashboardFilterChipList) { index, filterItem ->
            val isSelected = if (filterItem.option == null) {
                selectedFilters.isEmpty()
            } else {
                selectedFilters.contains(filterItem.option)
            }

            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) RoyalBluePrimary else Color.Transparent,
                animationSpec = tween(durationMillis = 250),
                label = "FilterChipBg"
            )

            val animatedBorderColor by animateColorAsState(
                targetValue = if (isSelected) RoyalBluePrimary else Color.White.copy(alpha = 0.35f),
                animationSpec = tween(durationMillis = 250),
                label = "FilterChipBorder"
            )

            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                animationSpec = tween(durationMillis = 250),
                label = "FilterChipText"
            )

            Surface(
                onClick = { onSelectFilter(filterItem.option) },
                shape = RoundedCornerShape(20.dp),
                color = animatedBgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, animatedBorderColor),
                modifier = Modifier.testTag("dashboard_filter_chip_${filterItem.label.lowercase().replace(" ", "_")}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected && filterItem.option != null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = filterItem.label,
                        style = TextStyle(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = animatedTextColor
                        )
                    )
                }
            }
        }
    }
}

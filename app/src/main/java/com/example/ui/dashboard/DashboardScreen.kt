package com.example.ui.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onCollectPremium: (PolicyEntity) -> Unit
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
                    .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                RoyalBluePrimary,
                                RoyalBlueDark
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp)
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
                            // Clickable Profile Photo / Avatar (56dp) - Tapping opens Agent Profile in Settings
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
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
                                            .border(2.5.dp, AccentOrange, CircleShape),
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
                                                        fontSize = 18.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "$timeOfDayGreeting 👋",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = AccentOrangeLight,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = agentProfile?.agentName ?: "Pintu Ojha",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 19.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedTodayDate,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
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
                            // Club Member Badge (Smaller & Premium)
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

                            // Notification Bell with Unread Badge
                            IconButton(
                                onClick = onNavigateToReminders,
                                modifier = Modifier
                                    .size(40.dp)
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // -------------------------------------------------------------
                    // 2. SEARCH BAR & FILTER OPTIONS
                    // -------------------------------------------------------------
                    SearchBarComponent(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        placeholderText = "Search customer, mobile, policy #, plan...",
                        testTag = "dashboard_search_input",
                        selectedFilters = selectedSearchFilters,
                        onFilterClick = { showFilterBottomSheet = true },
                        onApplyFilters = { viewModel.setSearchFilters(it) },
                        onResetFilters = { viewModel.resetSearchFilters() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // -------------------------------------------------------------
                    // 2B. HORIZONTALLY SCROLLABLE FILTER OPTIONS (M3 FILTER CHIPS)
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
            // 3. QUICK ACTIONS (8 EQUAL ROUNDED CARDS IN 2 ROWS)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Add Client, Add Policy, Payment, Reminder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionItemCard(
                        title = "Add Client",
                        icon = Icons.Default.PersonAdd,
                        iconBgColor = RoyalBlueContainer,
                        iconTintColor = RoyalBluePrimary,
                        onClick = onAddCustomer,
                        modifier = Modifier.weight(1f),
                        testTag = "action_add_customer"
                    )

                    QuickActionItemCard(
                        title = "Add Policy",
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        iconBgColor = AccentOrangeContainer,
                        iconTintColor = AccentOrange,
                        onClick = onAddPolicy,
                        modifier = Modifier.weight(1f),
                        testTag = "action_add_policy"
                    )

                    QuickActionItemCard(
                        title = "Payment",
                        icon = Icons.Default.Payments,
                        iconBgColor = EmeraldGreenContainer,
                        iconTintColor = EmeraldGreenSecondary,
                        onClick = onNavigateToPayments,
                        modifier = Modifier.weight(1f),
                        testTag = "action_record_payment"
                    )

                    QuickActionItemCard(
                        title = "Reminder",
                        icon = Icons.Default.NotificationsActive,
                        iconBgColor = ErrorRedContainer,
                        iconTintColor = ErrorRed,
                        onClick = onNavigateToReminders,
                        modifier = Modifier.weight(1f),
                        testTag = "action_send_reminder"
                    )
                }

                // Row 2: Reports, Calendar, Documents, Scan QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionItemCard(
                        title = "Reports",
                        icon = Icons.Default.PieChart,
                        iconBgColor = RoyalBlueContainer,
                        iconTintColor = RoyalBluePrimary,
                        onClick = onNavigateToReports,
                        modifier = Modifier.weight(1f),
                        testTag = "action_reports"
                    )

                    QuickActionItemCard(
                        title = "Calendar",
                        icon = Icons.Default.CalendarMonth,
                        iconBgColor = AccentOrangeContainer,
                        iconTintColor = AccentOrange,
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(1f),
                        testTag = "action_calendar"
                    )

                    QuickActionItemCard(
                        title = "Documents",
                        icon = Icons.Default.FolderOpen,
                        iconBgColor = EmeraldGreenContainer,
                        iconTintColor = EmeraldGreenSecondary,
                        onClick = onNavigateToDocuments,
                        modifier = Modifier.weight(1f),
                        testTag = "action_documents"
                    )

                    QuickActionItemCard(
                        title = "Scan QR",
                        icon = Icons.Default.QrCodeScanner,
                        iconBgColor = ErrorRedContainer,
                        iconTintColor = ErrorRed,
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
        // 6. FLOATING ACTION BUTTON WITH EXPANDABLE SPEED DIAL OPTIONS
        // -------------------------------------------------------------
        if (fabExpanded) {
            // Dismiss Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { fabExpanded = false }
            )
        }

        // FAB Speed Dial Items Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FabOptionRow(
                        label = "Add Client",
                        icon = Icons.Default.PersonAdd,
                        containerColor = RoyalBluePrimary,
                        onClick = {
                            fabExpanded = false
                            onAddCustomer()
                        },
                        testTag = "fab_option_add_client"
                    )

                    FabOptionRow(
                        label = "Add Policy",
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        containerColor = AccentOrange,
                        onClick = {
                            fabExpanded = false
                            onAddPolicy()
                        },
                        testTag = "fab_option_add_policy"
                    )

                    FabOptionRow(
                        label = "Record Payment",
                        icon = Icons.Default.Payments,
                        containerColor = EmeraldGreenSecondary,
                        onClick = {
                            fabExpanded = false
                            onNavigateToPayments()
                        },
                        testTag = "fab_option_record_payment"
                    )

                    FabOptionRow(
                        label = "Add Follow-up",
                        icon = Icons.Default.NotificationsActive,
                        containerColor = ErrorRed,
                        onClick = {
                            fabExpanded = false
                            onNavigateToReminders()
                        },
                        testTag = "fab_option_add_followup"
                    )
                }
            }

            // Main Rotating "+" FAB
            val rotation by animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "fab_rotate"
            )

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = AccentOrange,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .size(58.dp)
                    .testTag("dashboard_main_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Expand Quick Actions",
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(rotation)
                )
            }
        }
    }

    // -------------------------------------------------------------
    // 7. SCAN QR DIALOG
    // -------------------------------------------------------------
    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showQrDialog = false
                        Toast.makeText(context, "QR Scanner: Simulated Policy Scan Completed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Simulate QR Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Close")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIC Policy QR Scanner", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Point camera at an LIC Policy Bond or Payment Receipt QR Code to instant-load details.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RoyalBlueDark)
                            .border(2.dp, AccentOrange, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
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
    iconBgColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(82.dp)
            .testTag(testTag)
            .shadow(1.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTintColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Button
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoyalBlueContainer)
                            .testTag("action_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Customer",
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // WhatsApp Button
                    IconButton(
                        onClick = onRemind,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                            .testTag("action_whatsapp")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "WhatsApp Reminder",
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Green Collect Button
                Button(
                    onClick = onCollect,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Collect",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
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

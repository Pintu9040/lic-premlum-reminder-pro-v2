package com.example.ui.dashboard

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

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
    onNavigateToPayments: () -> Unit,
    onNavigateToReports: () -> Unit = {},
    onAddCustomer: () -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeWorkTab by remember { mutableStateOf(TodaysWorkTab.DUE_TODAY) }

    val context = LocalContext.current

    // Greeting logic based on time
    val timeOfDayGreeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Filter today's work items
    val todayStr = remember { LocalDate.now().toString() }
    val currentMonth = remember { LocalDate.now().monthValue }

    val dueTodayPolicies = remember(policies) {
        policies.filter { it.dueDate == todayStr || it.status == "Lapsed" }
    }

    val followUpPolicies = remember(policies) {
        policies.filter { it.status == "Lapsed" }
    }

    val birthdayCustomers = remember(customers) {
        customers.filter {
            val dobMonth = try { LocalDate.parse(it.dob).monthValue } catch (e: Exception) { -1 }
            dobMonth == currentMonth
        }
    }

    val maturityPolicies = remember(policies) {
        policies.filter { it.status == "Paid-up" }
    }

    val pendingCollectionPolicies = remember(policies) {
        policies.filter { it.status == "Active" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Redesigned Compact Blue Header
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
                // Agent Profile Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Editable Agent Avatar with Camera Icon Overlay
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onNavigateToReports() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, AccentOrange, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (agentProfile?.agentName ?: "Agent").take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBluePrimary,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                            // Small Camera Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(AccentOrange)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Edit Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "$timeOfDayGreeting 👋",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentOrangeLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = agentProfile?.agentName ?: "Pintu Ojha",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Code: ${agentProfile?.agencyCode ?: "LIC-AG-89421"} • ${agentProfile?.branchName ?: "Branch 883"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Compact Club Member Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = AccentOrangeLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Club Member",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modern Search Bar with Filter Icon
                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search customers, policy #, plans...",
                    testTag = "dashboard_search_input"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions Section (Exactly 4 cards in 1 row)
        SectionHeader(
            title = "Quick Actions",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                icon = Icons.Default.NoteAdd,
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

        Spacer(modifier = Modifier.height(18.dp))

        // 6 Statistic Cards in a Clean 2-Column Grid
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
                    onClick = onNavigateToReminders,
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

        Spacer(modifier = Modifier.height(18.dp))

        // Today's Work Section with Rounded Capsule Tabs
        SectionHeader(
            title = "Today's Work",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Modern Rounded Filter Tabs
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

        // Today's Work Dynamic Content List
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (activeWorkTab) {
                TodaysWorkTab.DUE_TODAY -> {
                    if (dueTodayPolicies.isEmpty()) {
                        EmptyWorkStateCard("No premium dues pending for today!")
                    } else {
                        dueTodayPolicies.take(5).forEach { policy ->
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
                        followUpPolicies.take(5).forEach { policy ->
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
                        birthdayCustomers.take(5).forEach { customer ->
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
                        maturityPolicies.take(5).forEach { policy ->
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
                        pendingCollectionPolicies.take(5).forEach { policy ->
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

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Quick Action Card (Fixed width weight for 1-row layout)
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

// Redesigned Customer Policy Card with Photo, Premium, Due Date, Outstanding, Green Collect & Contact Buttons
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
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Avatar, Name, Policy # & Status Badge
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
                        size = 40.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pol #: ${policy.policyNumber} • ${policy.planName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Row: Premium, Due Date & Outstanding Amount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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

            // Action Buttons Row: Call, WhatsApp, Green Collect
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(RoyalBlueContainer)
                            .testTag("action_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Customer",
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // WhatsApp Button
                    IconButton(
                        onClick = onRemind,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                            .testTag("action_whatsapp")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "WhatsApp Reminder",
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier.size(16.dp)
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
                CustomerAvatar(name = customer.name, size = 40.dp)

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

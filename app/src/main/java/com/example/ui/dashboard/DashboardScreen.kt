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
    val payments by viewModel.payments.collectAsState()
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
        // Hero Header: Agent Profile Section & Greeting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            RoyalBluePrimary,
                            RoyalBlueLight
                        )
                    )
                )
                .padding(20.dp)
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
                        // Agent Avatar with Photo Placeholder / Initials
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, AccentOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (agentProfile?.agentName ?: "Agent").take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$timeOfDayGreeting 👋",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentOrangeLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Text(
                                text = agentProfile?.agentName ?: "Pintu Ojha",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Code: ${agentProfile?.agencyCode ?: "LIC-AG-89421"} | ${agentProfile?.branchName ?: "Branch 883"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // Performance Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = AccentOrangeLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Club Member",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Search Bar integrated into Header
                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search customers, policy #, plans...",
                    testTag = "dashboard_search_input"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Actions Section
        SectionHeader(
            title = "Quick Actions",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                QuickActionItemCard(
                    title = "Add Customer",
                    icon = Icons.Default.PersonAdd,
                    iconBgColor = RoyalBlueContainer,
                    iconTintColor = RoyalBluePrimary,
                    onClick = onAddCustomer,
                    testTag = "action_add_customer"
                )
            }

            item {
                QuickActionItemCard(
                    title = "Add Policy",
                    icon = Icons.Default.NoteAdd,
                    iconBgColor = AccentOrangeContainer,
                    iconTintColor = AccentOrange,
                    onClick = onAddPolicy,
                    testTag = "action_add_policy"
                )
            }

            item {
                QuickActionItemCard(
                    title = "Record Payment",
                    icon = Icons.Default.Payments,
                    iconBgColor = EmeraldGreenContainer,
                    iconTintColor = EmeraldGreenSecondary,
                    onClick = onNavigateToPayments,
                    testTag = "action_record_payment"
                )
            }

            item {
                QuickActionItemCard(
                    title = "Send Reminder",
                    icon = Icons.Default.NotificationsActive,
                    iconBgColor = ErrorRedContainer,
                    iconTintColor = ErrorRed,
                    onClick = onNavigateToReminders,
                    testTag = "action_send_reminder"
                )
            }

            item {
                QuickActionItemCard(
                    title = "Reports",
                    icon = Icons.Default.BarChart,
                    iconBgColor = RoyalBlueContainer,
                    iconTintColor = OnRoyalBlueContainer,
                    onClick = onNavigateToReports,
                    testTag = "action_reports"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6 Key Dashboard Metric Cards Grid
        SectionHeader(
            title = "Dashboard Overview",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Row 1: Total Customers & Total Policies
            Row(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.width(12.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Due Today & Due This Month
            Row(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.width(12.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Today's Collection & Outstanding Balance
            Row(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.width(12.dp))
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

        Spacer(modifier = Modifier.height(24.dp))

        // Today's Work Section with Category Chips
        SectionHeader(
            title = "Today's Work",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips for Today's Work
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // Today's Work Dynamic Content List
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (activeWorkTab) {
                TodaysWorkTab.DUE_TODAY -> {
                    if (dueTodayPolicies.isEmpty()) {
                        EmptyWorkStateCard("No premium dues pending for today!")
                    } else {
                        dueTodayPolicies.take(5).forEach { policy ->
                            WorkPolicyItemCard(
                                policy = policy,
                                onCollect = { onCollectPremium(policy) },
                                onRemind = {
                                    val msg = viewModel.generatePremiumReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        amount = policy.premiumAmount,
                                        dueDate = policy.dueDate
                                    )
                                    launchWhatsAppMessage(context, "", msg)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                TodaysWorkTab.FOLLOW_UPS -> {
                    if (followUpPolicies.isEmpty()) {
                        EmptyWorkStateCard("No lapsed policies requiring follow-up.")
                    } else {
                        followUpPolicies.take(5).forEach { policy ->
                            WorkPolicyItemCard(
                                policy = policy,
                                onCollect = { onCollectPremium(policy) },
                                onRemind = {
                                    val msg = viewModel.generatePremiumReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        amount = policy.premiumAmount,
                                        dueDate = policy.dueDate
                                    )
                                    launchWhatsAppMessage(context, "", msg)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                TodaysWorkTab.MATURITY -> {
                    if (maturityPolicies.isEmpty()) {
                        EmptyWorkStateCard("No maturity policies found in current view.")
                    } else {
                        maturityPolicies.take(5).forEach { policy ->
                            WorkPolicyItemCard(
                                policy = policy,
                                onCollect = { onCollectPremium(policy) },
                                onRemind = { }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                TodaysWorkTab.PENDING_COLLECTIONS -> {
                    if (pendingCollectionPolicies.isEmpty()) {
                        EmptyWorkStateCard("No pending collections found.")
                    } else {
                        pendingCollectionPolicies.take(5).forEach { policy ->
                            WorkPolicyItemCard(
                                policy = policy,
                                onCollect = { onCollectPremium(policy) },
                                onRemind = {
                                    val msg = viewModel.generatePremiumReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        amount = policy.premiumAmount,
                                        dueDate = policy.dueDate
                                    )
                                    launchWhatsAppMessage(context, "", msg)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Subcomponents for Dashboard
@Composable
fun QuickActionItemCard(
    title: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(108.dp)
            .testTag(testTag)
            .shadow(2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTintColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )
        },
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RoyalBluePrimary,
            selectedLabelColor = Color.White,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun WorkPolicyItemCard(
    policy: PolicyEntity,
    onCollect: () -> Unit,
    onRemind: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pol #: ${policy.policyNumber} • ${policy.planName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "₹${"%.2f".format(policy.premiumAmount)} (${policy.premiumMode})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Due: ${policy.dueDate}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRemind,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "WhatsApp Reminder",
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onCollect,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Text(
                            text = "Collect",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                CustomerAvatar(name = customer.name, size = 44.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "🎂 DOB: ${customer.dob} | 📱 ${customer.mobile}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldGreenSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

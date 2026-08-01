package com.example.ui.reminders

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AgentProfileEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.FollowUpEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.policy.getPolicyOutstandingBalance
import com.example.ui.theme.*
import com.example.utils.NotificationHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class NotificationCenterCategory(val label: String, val badgeColor: Color, val icon: ImageVector) {
    DUE_TODAY("Due Today", Color(0xFFE65100), Icons.Default.Today),
    DUE_TOMORROW("Due Tomorrow", Color(0xFFF57C00), Icons.Default.NextPlan),
    UPCOMING_7_DAYS("Upcoming (7 Days)", Color(0xFF0288D1), Icons.Default.CalendarMonth),
    OVERDUE("Overdue Premiums", Color(0xFFD32F2F), Icons.Default.Warning),
    BIRTHDAY("Birthdays", Color(0xFFFF4081), Icons.Default.Cake),
    ANNIVERSARY("Anniversaries", Color(0xFFC2185B), Icons.Default.Favorite),
    FOLLOW_UP("Follow-ups", Color(0xFF7B1FA2), Icons.Default.TaskAlt),
    ALL_DUES("All Dues", Color(0xFF1976D2), Icons.Default.FormatListNumbered)
}

enum class DueSortOption(val label: String) {
    NEAREST_DUE("Nearest Due Date"),
    HIGHEST_OUTSTANDING("Highest Outstanding"),
    CUSTOMER_NAME("Customer Name")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    viewModel: LicViewModel,
    onCollectPremium: (PolicyEntity) -> Unit,
    onViewCustomerProfile: (CustomerEntity) -> Unit = {},
    onViewPolicyDetail: (PolicyEntity) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val followUps by viewModel.followUps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val context = LocalContext.current

    var selectedCategoryTab by remember { mutableStateOf(NotificationCenterCategory.DUE_TODAY) }
    var selectedSort by remember { mutableStateOf(DueSortOption.NEAREST_DUE) }

    var followUpStatusFilter by remember { mutableStateOf("ALL") }

    // Sent Reminders Tracker (Policy ID -> Last Sent String)
    val sentReminders = remember { mutableStateMapOf<Long, String>() }

    // Dialog States
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var whatsAppRecipientName by remember { mutableStateOf("") }
    var whatsAppRecipientMobile by remember { mutableStateOf("") }
    var whatsAppMessageText by remember { mutableStateOf("") }
    var whatsAppPolicyId by remember { mutableStateOf<Long?>(null) }

    var showFollowUpDialog by remember { mutableStateOf(false) }
    var followUpToEdit by remember { mutableStateOf<FollowUpEntity?>(null) }

    var showRescheduleDialog by remember { mutableStateOf(false) }
    var followUpToReschedule by remember { mutableStateOf<FollowUpEntity?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    // Notification Settings State (Local preferences)
    val prefs = remember { context.getSharedPreferences("lic_notification_settings", Context.MODE_PRIVATE) }
    var enableDueTodayReminder by remember { mutableStateOf(prefs.getBoolean("enable_due_today", true)) }
    var enableOverdueReminder by remember { mutableStateOf(prefs.getBoolean("enable_overdue", true)) }
    var enableBirthdayReminder by remember { mutableStateOf(prefs.getBoolean("enable_birthday", true)) }
    var enableAnniversaryReminder by remember { mutableStateOf(prefs.getBoolean("enable_anniversary", true)) }
    var enableFollowUpReminder by remember { mutableStateOf(prefs.getBoolean("enable_followup", true)) }
    var selectedReminderTiming by remember { mutableStateOf(prefs.getString("reminder_timing", "3 Days Before") ?: "3 Days Before") }

    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val tomorrow = remember { today.plusDays(1) }
    val tomorrowStr = remember { tomorrow.toString() }
    val next7DaysEnd = remember { today.plusDays(7) }

    // Helper for search matching
    fun matchesSearchQuery(name: String, policyNo: String, phone: String, extra: String = ""): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim().lowercase()
        return name.lowercase().contains(q) ||
                policyNo.lowercase().contains(q) ||
                phone.lowercase().contains(q) ||
                extra.lowercase().contains(q)
    }

    // --- COMPUTATIONS FOR OUTSTANDING & DUE LISTS ---
    val policyOutstandingPairs = remember(policies, payments) {
        policies.map { policy ->
            val policyPayments = payments.filter { it.policyId == policy.id }
            val outstanding = getPolicyOutstandingBalance(policy, policyPayments)
            val totalPaid = policyPayments.sumOf { it.paidAmount }
            Triple(policy, outstanding, totalPaid)
        }
    }

    val totalOutstandingAmount = remember(policyOutstandingPairs) {
        policyOutstandingPairs.sumOf { it.second }
    }

    // 1. Premium Due Today
    val dueTodayList = remember(policyOutstandingPairs, todayStr, searchQuery) {
        policyOutstandingPairs.filter { (p, out, _) ->
            val cust = customers.find { it.id == p.customerId }
            val custMobile = cust?.mobile ?: ""
            p.dueDate == todayStr && out > 0 && matchesSearchQuery(p.customerName, p.policyNumber, custMobile, p.planName)
        }.map { it.first }
    }

    // 2. Due Tomorrow
    val dueTomorrowList = remember(policyOutstandingPairs, tomorrowStr, searchQuery) {
        policyOutstandingPairs.filter { (p, out, _) ->
            val cust = customers.find { it.id == p.customerId }
            val custMobile = cust?.mobile ?: ""
            p.dueDate == tomorrowStr && out > 0 && matchesSearchQuery(p.customerName, p.policyNumber, custMobile, p.planName)
        }.map { it.first }
    }

    // 3. Upcoming Due (Next 7 Days)
    val upcoming7DaysList = remember(policyOutstandingPairs, today, next7DaysEnd, searchQuery) {
        policyOutstandingPairs.filter { (p, out, _) ->
            val cust = customers.find { it.id == p.customerId }
            val custMobile = cust?.mobile ?: ""
            try {
                val d = LocalDate.parse(p.dueDate)
                (d.isEqual(today) || d.isAfter(today)) && (d.isEqual(next7DaysEnd) || d.isBefore(next7DaysEnd)) && out > 0 &&
                        matchesSearchQuery(p.customerName, p.policyNumber, custMobile, p.planName)
            } catch (e: Exception) { false }
        }.map { it.first }
    }

    // 4. Overdue Premiums
    val overdueList = remember(policyOutstandingPairs, today, searchQuery) {
        policyOutstandingPairs.filter { (p, out, _) ->
            val cust = customers.find { it.id == p.customerId }
            val custMobile = cust?.mobile ?: ""
            try {
                val d = LocalDate.parse(p.dueDate)
                d.isBefore(today) && out > 0 && matchesSearchQuery(p.customerName, p.policyNumber, custMobile, p.planName)
            } catch (e: Exception) { false }
        }.map { it.first }
    }

    // 5. All Due Policies
    val allDuesList = remember(policyOutstandingPairs, searchQuery, selectedSort) {
        val list = policyOutstandingPairs.filter { (p, out, _) ->
            val cust = customers.find { it.id == p.customerId }
            val custMobile = cust?.mobile ?: ""
            out > 0 && matchesSearchQuery(p.customerName, p.policyNumber, custMobile, p.planName)
        }.map { it.first }

        when (selectedSort) {
            DueSortOption.NEAREST_DUE -> list.sortedBy { it.dueDate }
            DueSortOption.HIGHEST_OUTSTANDING -> list.sortedByDescending { p ->
                val pPayments = payments.filter { it.policyId == p.id }
                getPolicyOutstandingBalance(p, pPayments)
            }
            DueSortOption.CUSTOMER_NAME -> list.sortedBy { it.customerName }
        }
    }

    // 6. Follow-ups
    val filteredFollowUps = remember(followUps, searchQuery, followUpStatusFilter) {
        followUps.filter { fu ->
            val matchesQuery = matchesSearchQuery(fu.customerName, "", fu.customerMobile, fu.notes)
            val matchesStatus = when (followUpStatusFilter) {
                "PENDING" -> fu.status.equals("Pending", ignoreCase = true)
                "COMPLETED" -> fu.status.equals("Completed", ignoreCase = true)
                "CANCELLED" -> fu.status.equals("Cancelled", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesStatus
        }.sortedBy { fu -> fu.date }
    }

    // 7. Birthdays
    val birthdayCustomers = remember(customers, searchQuery) {
        customers.filter { cust ->
            matchesSearchQuery(cust.name, "", cust.mobile, cust.email) && cust.dob.isNotBlank()
        }
    }

    // 8. Anniversaries
    val anniversaryCustomers = remember(customers, searchQuery) {
        customers.filter { cust ->
            matchesSearchQuery(cust.name, "", cust.mobile, cust.email) && cust.anniversary.isNotBlank()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Notification & Reminder Center",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "LIC Automated WhatsApp & Payment Reminders",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("due_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("notification_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Notification Settings", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            val dueTodayCount = dueTodayList.size
                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = 101,
                                title = "LIC Reminder Alert",
                                message = "You have $dueTodayCount policy dues scheduled for collection today."
                            )
                        },
                        modifier = Modifier.testTag("trigger_due_notification_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Alert", tint = AccentOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalBluePrimary)
            )
        },
        floatingActionButton = {
            if (selectedCategoryTab == NotificationCenterCategory.FOLLOW_UP) {
                ExtendedFloatingActionButton(
                    onClick = {
                        followUpToEdit = null
                        showFollowUpDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Follow-up", fontWeight = FontWeight.Bold) },
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_followup_fab")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Search & Category Navigation Tabs
            Surface(
                color = RoyalBluePrimary,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchBarComponent(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        placeholderText = "Search by Customer Name, Policy #, Mobile #...",
                        testTag = "due_search_input"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedCategoryTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        NotificationCenterCategory.entries.forEach { category ->
                            val count = when (category) {
                                NotificationCenterCategory.DUE_TODAY -> dueTodayList.size
                                NotificationCenterCategory.DUE_TOMORROW -> dueTomorrowList.size
                                NotificationCenterCategory.UPCOMING_7_DAYS -> upcoming7DaysList.size
                                NotificationCenterCategory.OVERDUE -> overdueList.size
                                NotificationCenterCategory.BIRTHDAY -> birthdayCustomers.size
                                NotificationCenterCategory.ANNIVERSARY -> anniversaryCustomers.size
                                NotificationCenterCategory.FOLLOW_UP -> filteredFollowUps.size
                                NotificationCenterCategory.ALL_DUES -> allDuesList.size
                            }

                            Tab(
                                selected = selectedCategoryTab == category,
                                onClick = { selectedCategoryTab = category },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = category.label,
                                            fontWeight = if (selectedCategoryTab == category) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge(
                                            containerColor = if (selectedCategoryTab == category) Color.White else category.badgeColor,
                                            contentColor = if (selectedCategoryTab == category) category.badgeColor else Color.White
                                        ) {
                                            Text("$count", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("tab_${category.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Quick Stats Summary Header Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricPill(label = "Due Today", count = dueTodayList.size, color = Color(0xFFE65100))
                    MetricPill(label = "Due Tomorrow", count = dueTomorrowList.size, color = Color(0xFFF57C00))
                    MetricPill(label = "Next 7 Days", count = upcoming7DaysList.size, color = Color(0xFF0288D1))
                    MetricPill(label = "Overdue", count = overdueList.size, color = Color(0xFFD32F2F))
                    MetricPill(label = "Follow-ups", count = filteredFollowUps.size, color = Color(0xFF7B1FA2))
                }
            }

            // --- MAIN CONTENT LIST ACCORDING TO SELECTED TAB ---
            val activePolicyList = when (selectedCategoryTab) {
                NotificationCenterCategory.DUE_TODAY -> dueTodayList
                NotificationCenterCategory.DUE_TOMORROW -> dueTomorrowList
                NotificationCenterCategory.UPCOMING_7_DAYS -> upcoming7DaysList
                NotificationCenterCategory.OVERDUE -> overdueList
                NotificationCenterCategory.ALL_DUES -> allDuesList
                else -> emptyList()
            }

            if (selectedCategoryTab in listOf(
                    NotificationCenterCategory.DUE_TODAY,
                    NotificationCenterCategory.DUE_TOMORROW,
                    NotificationCenterCategory.UPCOMING_7_DAYS,
                    NotificationCenterCategory.OVERDUE,
                    NotificationCenterCategory.ALL_DUES
                )
            ) {
                if (activePolicyList.isEmpty()) {
                    EmptyReminderState("No policies found for ${selectedCategoryTab.label}.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedCategoryTab.label} (${activePolicyList.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                if (selectedCategoryTab == NotificationCenterCategory.ALL_DUES) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DueSortOption.entries.forEach { option ->
                                            FilterChip(
                                                selected = selectedSort == option,
                                                onClick = { selectedSort = option },
                                                label = { Text(option.label, fontSize = 10.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(activePolicyList, key = { it.id }) { policy ->
                            val matchedCustomer = customers.find { it.id == policy.customerId }
                            val policyPayments = remember(payments, policy.id) {
                                payments.filter { it.policyId == policy.id }
                            }
                            val totalPaid = policyPayments.sumOf { it.paidAmount }
                            val outstanding = getPolicyOutstandingBalance(policy, policyPayments)
                            val lastSent = sentReminders[policy.id]

                            DueCustomerCardItem(
                                policy = policy,
                                customer = matchedCustomer,
                                totalPaid = totalPaid,
                                outstandingBalance = outstanding,
                                lastSentTimestamp = lastSent,
                                agentName = agentProfile?.agentName ?: "LIC Advisor",
                                onCall = {
                                    launchPhoneCall(context, matchedCustomer?.mobile ?: "")
                                },
                                onWhatsApp = {
                                    whatsAppPolicyId = policy.id
                                    whatsAppRecipientName = policy.customerName
                                    whatsAppRecipientMobile = matchedCustomer?.whatsapp?.ifEmpty { matchedCustomer.mobile } ?: matchedCustomer?.mobile ?: ""
                                    whatsAppMessageText = viewModel.generatePremiumReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        amount = policy.premiumAmount,
                                        dueDate = policy.dueDate,
                                        outstandingBalance = outstanding
                                    )
                                    showWhatsAppDialog = true
                                },
                                onCollectPayment = {
                                    onCollectPremium(policy)
                                },
                                onViewProfile = {
                                    if (matchedCustomer != null) {
                                        onViewCustomerProfile(matchedCustomer)
                                    } else {
                                        onViewPolicyDetail(policy)
                                    }
                                }
                            )
                        }
                    }
                }
            } else if (selectedCategoryTab == NotificationCenterCategory.FOLLOW_UP) {
                if (filteredFollowUps.isEmpty()) {
                    EmptyReminderState("No follow-up tasks recorded.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Follow-up Action Center", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("ALL", "PENDING", "COMPLETED").forEach { statusKey ->
                                        FilterChip(
                                            selected = followUpStatusFilter == statusKey,
                                            onClick = { followUpStatusFilter = statusKey },
                                            label = { Text(statusKey, fontSize = 10.sp) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredFollowUps, key = { it.id }) { followUp ->
                            val matchedCustomer = customers.find { it.id == followUp.customerId }

                            FollowUpCard(
                                followUp = followUp,
                                customer = matchedCustomer,
                                onToggleComplete = {
                                    val newStatus = if (followUp.status.equals("Completed", ignoreCase = true)) "Pending" else "Completed"
                                    viewModel.updateFollowUp(followUp.copy(status = newStatus))
                                },
                                onEdit = {
                                    followUpToEdit = followUp
                                    showFollowUpDialog = true
                                },
                                onReschedule = {
                                    followUpToReschedule = followUp
                                    showRescheduleDialog = true
                                },
                                onCall = {
                                    launchPhoneCall(context, followUp.customerMobile)
                                },
                                onWhatsApp = {
                                    whatsAppPolicyId = null
                                    whatsAppRecipientName = followUp.customerName
                                    whatsAppRecipientMobile = followUp.customerMobile
                                    whatsAppMessageText = "Hello ${followUp.customerName},\n\nThis is regarding our scheduled follow-up: ${followUp.notes}\n\nPlease let me know if you have any questions.\n\nRegards,\n${agentProfile?.agentName ?: "LIC Advisor"}"
                                    showWhatsAppDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteFollowUp(followUp)
                                }
                            )
                        }
                    }
                }
            } else if (selectedCategoryTab == NotificationCenterCategory.BIRTHDAY) {
                if (birthdayCustomers.isEmpty()) {
                    EmptyReminderState("No customer birthdays found.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(birthdayCustomers, key = { it.id }) { cust ->
                            EventWishCard(
                                customer = cust,
                                dateLabel = "DOB: ${cust.dob}",
                                badgeText = "Birthday",
                                badgeColor = AccentOrange,
                                icon = Icons.Default.Cake,
                                onCall = { launchPhoneCall(context, cust.mobile) },
                                onWhatsApp = {
                                    whatsAppPolicyId = null
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.whatsapp.ifEmpty { cust.mobile }
                                    whatsAppMessageText = viewModel.generateBirthdayWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                },
                                onWish = {
                                    whatsAppPolicyId = null
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.whatsapp.ifEmpty { cust.mobile }
                                    whatsAppMessageText = viewModel.generateBirthdayWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                },
                                onViewProfile = { onViewCustomerProfile(cust) }
                            )
                        }
                    }
                }
            } else if (selectedCategoryTab == NotificationCenterCategory.ANNIVERSARY) {
                if (anniversaryCustomers.isEmpty()) {
                    EmptyReminderState("No marriage anniversaries found.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(anniversaryCustomers, key = { it.id }) { cust ->
                            EventWishCard(
                                customer = cust,
                                dateLabel = "Anniversary: ${cust.anniversary}",
                                badgeText = "Anniversary",
                                badgeColor = Color(0xFFE91E63),
                                icon = Icons.Default.Favorite,
                                onCall = { launchPhoneCall(context, cust.mobile) },
                                onWhatsApp = {
                                    whatsAppPolicyId = null
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.whatsapp.ifEmpty { cust.mobile }
                                    whatsAppMessageText = viewModel.generateAnniversaryWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                },
                                onWish = {
                                    whatsAppPolicyId = null
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.whatsapp.ifEmpty { cust.mobile }
                                    whatsAppMessageText = viewModel.generateAnniversaryWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                },
                                onViewProfile = { onViewCustomerProfile(cust) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive WhatsApp Reminder Dialog
    if (showWhatsAppDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = EmeraldGreenSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Automated WhatsApp Reminder", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Recipient: $whatsAppRecipientName", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text("Mobile: $whatsAppRecipientMobile", style = MaterialTheme.typography.labelSmall, color = RoyalBluePrimary)
                        }
                    }

                    Text("Message Template Quick-Pick:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                whatsAppMessageText = "Dear $whatsAppRecipientName,\n\nGentle reminder for your LIC Policy Premium Payment.\n\nAdvisor: ${agentProfile?.agentName ?: "LIC Advisor"}"
                            },
                            label = { Text("Short Notice", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                whatsAppMessageText = "URGENT NOTICE: Dear $whatsAppRecipientName, your LIC Policy premium is overdue. Please pay immediately to prevent policy lapse.\n\nAdvisor: ${agentProfile?.agentName ?: "LIC Advisor"}"
                            },
                            label = { Text("Urgent Overdue", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                whatsAppMessageText = "Dear $whatsAppRecipientName,\n\nYour policy is currently in Grace Period. You can still pay without late fees.\n\nAdvisor: ${agentProfile?.agentName ?: "LIC Advisor"}"
                            },
                            label = { Text("Grace Period", fontSize = 11.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = whatsAppRecipientMobile,
                        onValueChange = { whatsAppRecipientMobile = it },
                        label = { Text("Mobile / WhatsApp Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Message Body (Editable):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${whatsAppMessageText.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    OutlinedTextField(
                        value = whatsAppMessageText,
                        onValueChange = { whatsAppMessageText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        launchWhatsAppMessage(context, whatsAppRecipientMobile, whatsAppMessageText)
                        whatsAppPolicyId?.let { id ->
                            val time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                            sentReminders[id] = "Sent Today at $time"
                        }
                        showWhatsAppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_send_whatsapp_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send via WhatsApp", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notification Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = RoyalBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Notification Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Reminder Toggles:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))

                    SettingToggleItem(
                        title = "Premium Due Today",
                        subtitle = "Notify every morning for today's dues",
                        checked = enableDueTodayReminder,
                        onCheckedChange = { enableDueTodayReminder = it }
                    )

                    SettingToggleItem(
                        title = "Overdue Premiums Alert",
                        subtitle = "Highlight lapsed or overdue policies",
                        checked = enableOverdueReminder,
                        onCheckedChange = { enableOverdueReminder = it }
                    )

                    SettingToggleItem(
                        title = "Birthday Wishes",
                        subtitle = "Alert on customer birthdays",
                        checked = enableBirthdayReminder,
                        onCheckedChange = { enableBirthdayReminder = it }
                    )

                    SettingToggleItem(
                        title = "Anniversary Wishes",
                        subtitle = "Alert on customer anniversaries",
                        checked = enableAnniversaryReminder,
                        onCheckedChange = { enableAnniversaryReminder = it }
                    )

                    SettingToggleItem(
                        title = "Follow-up Tasks",
                        subtitle = "Remind scheduled follow-up calls",
                        checked = enableFollowUpReminder,
                        onCheckedChange = { enableFollowUpReminder = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Advance Reminder Schedule:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))

                    listOf("7 Days Before", "3 Days Before", "1 Day Before", "Due Day Only").forEach { timing ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReminderTiming = timing },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReminderTiming == timing,
                                onClick = { selectedReminderTiming = timing }
                            )
                            Text(timing, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit()
                            .putBoolean("enable_due_today", enableDueTodayReminder)
                            .putBoolean("enable_overdue", enableOverdueReminder)
                            .putBoolean("enable_birthday", enableBirthdayReminder)
                            .putBoolean("enable_anniversary", enableAnniversaryReminder)
                            .putBoolean("enable_followup", enableFollowUpReminder)
                            .putString("reminder_timing", selectedReminderTiming)
                            .apply()
                        showSettingsDialog = false
                        Toast.makeText(context, "Notification preferences saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Follow-Up Reschedule Dialog
    if (showRescheduleDialog && followUpToReschedule != null) {
        val currentFu = followUpToReschedule!!
        var newDate by remember { mutableStateOf(currentFu.date) }
        var newTime by remember { mutableStateOf(currentFu.time) }

        AlertDialog(
            onDismissRequest = { showRescheduleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Update, contentDescription = null, tint = AccentOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reschedule Follow-up", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Customer: ${currentFu.customerName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                    Text("Quick Reschedule Presets:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { newDate = LocalDate.now().plusDays(1).toString() },
                            label = { Text("+1 Day") }
                        )
                        AssistChip(
                            onClick = { newDate = LocalDate.now().plusDays(3).toString() },
                            label = { Text("+3 Days") }
                        )
                        AssistChip(
                            onClick = { newDate = LocalDate.now().plusWeeks(1).toString() },
                            label = { Text("+1 Week") }
                        )
                    }

                    OutlinedTextField(
                        value = newDate,
                        onValueChange = { newDate = it },
                        label = { Text("New Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newTime,
                        onValueChange = { newTime = it },
                        label = { Text("New Time (e.g. 10:30 AM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateFollowUp(currentFu.copy(date = newDate, time = newTime, status = "Pending"))
                        showRescheduleDialog = false
                        Toast.makeText(context, "Follow-up rescheduled to $newDate!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Follow-up", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescheduleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Follow-Up Create / Edit Dialog
    if (showFollowUpDialog) {
        FollowUpFormDialog(
            existingFollowUp = followUpToEdit,
            customers = customers,
            onDismiss = { showFollowUpDialog = false },
            onSave = { followUp ->
                if (followUpToEdit != null) {
                    viewModel.updateFollowUp(followUp)
                } else {
                    viewModel.addFollowUp(followUp)
                }
                showFollowUpDialog = false
            }
        )
    }
}

// ==========================================
// COMPONENT HELPERS & CARDS
// ==========================================

@Composable
fun MetricPill(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $count",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color)
            )
        }
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DueCustomerCardItem(
    policy: PolicyEntity,
    customer: CustomerEntity?,
    totalPaid: Double,
    outstandingBalance: Double,
    lastSentTimestamp: String?,
    agentName: String,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onCollectPayment: () -> Unit,
    onViewProfile: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val pDueDate = remember(policy.dueDate) {
        try { LocalDate.parse(policy.dueDate) } catch (e: Exception) { today }
    }
    val daysDiff = remember(pDueDate, today) {
        ChronoUnit.DAYS.between(today, pDueDate)
    }

    val isOverdue = daysDiff < 0 && outstandingBalance > 0
    val isDueToday = daysDiff == 0L && outstandingBalance > 0
    val isDueSoon = daysDiff in 1..30 && outstandingBalance > 0

    val (badgeBg, badgeLabel) = when {
        isOverdue -> Pair(ErrorRed, "${-daysDiff} Days Overdue")
        isDueToday -> Pair(AccentOrange, "Due Today")
        isDueSoon -> Pair(Color(0xFF0288D1), "Due in $daysDiff Days")
        else -> Pair(EmeraldGreenSecondary, "Active")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .testTag("due_customer_card_${policy.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeBg.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header Row: Avatar, Name, Mobile, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onViewProfile() }
                ) {
                    CustomerAvatar(
                        name = customer?.name ?: policy.customerName,
                        photoUri = customer?.photoUri ?: "",
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mobile: ${customer?.mobile ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = RoyalBluePrimary)
                        )
                    }
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Policy Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(policy.policyNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Premium Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(policy.premiumAmount)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }

                Column {
                    Text("Plan Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(policy.planName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Total Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(totalPaid)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(policy.premiumMode, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "₹${"%.2f".format(outstandingBalance)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold, color = if (outstandingBalance > 0) ErrorRed else EmeraldGreenSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next Due Date: ${policy.dueDate}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )

                if (lastSentTimestamp != null) {
                    Surface(
                        color = EmeraldGreenContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = lastSentTimestamp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = EmeraldGreenSecondary, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Call, WhatsApp, Collect Payment, Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RoyalBlueContainer)
                        .testTag("due_call_btn_${policy.id}")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onWhatsApp,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenContainer)
                        .testTag("due_whatsapp_btn_${policy.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                }

                OutlinedButton(
                    onClick = onViewProfile,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Profile", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onCollectPayment,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("collect_payment_btn_${policy.id}"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Collect Payment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FollowUpCard(
    followUp: FollowUpEntity,
    customer: CustomerEntity?,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = followUp.status.equals("Completed", ignoreCase = true)
    val statusColor = when {
        isCompleted -> EmeraldGreenSecondary
        followUp.status.equals("Cancelled", ignoreCase = true) -> MaterialTheme.colorScheme.outline
        else -> AccentOrange
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        name = customer?.name ?: followUp.customerName,
                        photoUri = customer?.photoUri ?: "",
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = followUp.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Scheduled: ${followUp.date} • ${followUp.time}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = followUp.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = statusColor)
                    )
                }
            }

            if (followUp.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = followUp.notes,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoyalBlueContainer)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onWhatsApp,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onReschedule,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Update, contentDescription = "Reschedule", tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ErrorRed.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }

                Button(
                    onClick = onToggleComplete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) MaterialTheme.colorScheme.outline else EmeraldGreenSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (isCompleted) Icons.Default.Undo else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isCompleted) "Reopen" else "Complete",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun EventWishCard(
    customer: CustomerEntity,
    dateLabel: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onWish: () -> Unit,
    onViewProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onViewProfile() }
                ) {
                    CustomerAvatar(
                        name = customer.name,
                        photoUri = customer.photoUri ?: "",
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Phone: ${customer.mobile}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(badgeText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = badgeColor))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RoyalBlueContainer)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onWhatsApp,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenContainer)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onWish,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Wish", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpFormDialog(
    existingFollowUp: FollowUpEntity?,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (FollowUpEntity) -> Unit
) {
    var selectedCustomer by remember {
        mutableStateOf(customers.find { it.id == existingFollowUp?.customerId } ?: customers.firstOrNull())
    }
    var expandedCustomerDropdown by remember { mutableStateOf(false) }

    var dateStr by remember { mutableStateOf(existingFollowUp?.date ?: LocalDate.now().toString()) }
    var timeStr by remember { mutableStateOf(existingFollowUp?.time ?: "10:00 AM") }
    var notesStr by remember { mutableStateOf(existingFollowUp?.notes ?: "") }
    var statusStr by remember { mutableStateOf(existingFollowUp?.status ?: "Pending") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingFollowUp == null) "New Follow-up Task" else "Edit Follow-up",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedCustomerDropdown,
                    onExpandedChange = { expandedCustomerDropdown = !expandedCustomerDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: "Select Customer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Customer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCustomerDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCustomerDropdown,
                        onDismissRequest = { expandedCustomerDropdown = false }
                    ) {
                        customers.forEach { cust ->
                            DropdownMenuItem(
                                text = { Text("${cust.name} (${cust.mobile})") },
                                onClick = {
                                    selectedCustomer = cust
                                    expandedCustomerDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = timeStr,
                        onValueChange = { timeStr = it },
                        label = { Text("Time") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    label = { Text("Notes / Objective") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Status:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pending", "Completed", "Cancelled").forEach { st ->
                        FilterChip(
                            selected = statusStr.equals(st, ignoreCase = true),
                            onClick = { statusStr = st },
                            label = { Text(st) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer
                    if (cust == null) {
                        Toast.makeText(context, "Please select a customer", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val item = FollowUpEntity(
                        id = existingFollowUp?.id ?: 0L,
                        customerId = cust.id,
                        customerName = cust.name,
                        customerMobile = cust.mobile,
                        date = dateStr.ifBlank { LocalDate.now().toString() },
                        time = timeStr.ifBlank { "10:00 AM" },
                        notes = notesStr,
                        status = statusStr
                    )
                    onSave(item)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_followup_btn")
            ) {
                Text("Save Follow-up", fontWeight = FontWeight.Bold)
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
fun EmptyReminderState(message: String) {
    StandardEmptyState(
        title = "No Reminders Found",
        description = message,
        icon = Icons.Outlined.NotificationsNone
    )
}

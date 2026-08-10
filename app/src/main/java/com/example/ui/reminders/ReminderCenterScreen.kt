package com.example.ui.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.payment.PaymentCollectionDialog
import com.example.util.CurrentDueSummary
import com.example.util.PaymentAllocationEngine
import com.example.util.SearchFilterEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Royal Blue & Dark Theme Palette
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val AccentAmber = Color(0xFFF59E0B) // Orange for Due Today
private val AccentRed = Color(0xFFEF4444)   // Red for Overdue
private val AccentGreen = Color(0xFF10B981) // Green for Upcoming
private val AccentBlue = Color(0xFF3B82F6)  // Blue for Completed
private val AccentIndigo = Color(0xFF6366F1)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

enum class ReminderCategory(val displayName: String) {
    ALL("All"),
    TODAY("Due Today"),
    TOMORROW("Due Tomorrow"),
    THIS_WEEK("Due This Week"),
    THIS_MONTH("Due This Month"),
    OVERDUE("Overdue"),
    LAPSED("Lapsed")
}

/**
 * Real Data Wrapper for Reminders calculated dynamically from Room Database.
 */
data class ReminderWrapper(
    val policy: PolicyEntity,
    val customer: CustomerEntity?,
    val summary: CurrentDueSummary,
    val dueCategory: ReminderCategory,
    val daysStatus: String,
    val isCompleted: Boolean = false
) {
    val id: Long get() = policy.id
    val customerName: String get() = policy.customerName.ifBlank { customer?.name ?: "Valued Customer" }
    val customerMobile: String get() = customer?.mobile?.ifBlank { customer?.whatsapp ?: "" } ?: ""
    val customerAddress: String get() = customer?.address ?: ""
    val policyNumber: String get() = policy.policyNumber
    val planName: String get() = policy.planName
    val planCode: String get() = extractDigits(policy.planName)
    val premiumAmount: Double get() = summary.premiumAmount
    val outstandingAmount: Double get() = summary.outstanding
    val premiumMode: String get() = policy.premiumMode
    val dueDate: String get() = summary.currentDueDate
    val isLapsed: Boolean get() = policy.status.equals("Lapsed", ignoreCase = true)
    val avatarInitials: String get() = extractInitials(customerName)

    private fun extractDigits(str: String): String {
        val digits = str.filter { it.isDigit() }
        return digits.ifBlank { str }
    }

    private fun extractInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "CU"
        }
    }

    fun matchesSearch(query: String): Boolean {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return true

        val custName = customerName.lowercase()
        val rawPhone = customerMobile.lowercase()
        val cleanPhone = customerMobile.replace(" ", "").replace("+", "").replace("-", "").lowercase()
        val polNo = policyNumber.lowercase()
        val plan = planName.lowercase()
        val code = planCode.lowercase()

        val terms = cleanQuery.split(" ").filter { it.isNotBlank() }
        return terms.all { term ->
            val cleanTerm = term.replace("+", "").replace("-", "")
            custName.contains(term) ||
                    rawPhone.contains(term) ||
                    (cleanTerm.isNotEmpty() && cleanPhone.contains(cleanTerm)) ||
                    polNo.contains(term) ||
                    plan.contains(term) ||
                    (cleanTerm.isNotEmpty() && code.contains(cleanTerm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCenterScreen(
    viewModel: LicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit = {},
    onCollectPremium: (PolicyEntity) -> Unit = {},
    onViewPolicyDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Database flows
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()

    // Local UI States
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ReminderCategory.TODAY) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedReminderForSheet by remember { mutableStateOf<ReminderWrapper?>(null) }
    var selectedPolicyForCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var showThisWeekNotificationDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Date references
    val today = remember { LocalDate.now() }
    val startOfWeek = remember(today) { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val endOfWeek = remember(today) { today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)) }
    val startOfMonth = remember(today) { today.withDayOfMonth(1) }
    val endOfMonth = remember(today) { today.with(TemporalAdjusters.lastDayOfMonth()) }

    // Convert room entities into dynamic ReminderWrappers (SINGLE DATA SOURCE)
    val allReminders = remember(policies, customers, payments, today) {
        policies.mapNotNull { policy ->
            if (policy.status.equals("Cancelled", ignoreCase = true)) return@mapNotNull null

            val customer = customers.find { it.id == policy.customerId }
            val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, payments)
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(policy.dueDate)
            val isLapsed = policy.status.equals("Lapsed", ignoreCase = true)
            val isPaid = summary.status.equals("Paid", ignoreCase = true) || summary.outstanding <= 0.0

            val dueCategory = when {
                isLapsed -> ReminderCategory.LAPSED
                isPaid -> ReminderCategory.ALL
                dueLocalDate == null -> ReminderCategory.ALL
                dueLocalDate == today -> ReminderCategory.TODAY
                dueLocalDate == today.plusDays(1) -> ReminderCategory.TOMORROW
                dueLocalDate >= startOfWeek && dueLocalDate <= endOfWeek -> ReminderCategory.THIS_WEEK
                dueLocalDate >= startOfMonth && dueLocalDate <= endOfMonth -> ReminderCategory.THIS_MONTH
                dueLocalDate < today -> ReminderCategory.OVERDUE
                else -> ReminderCategory.ALL
            }

            val daysStatus = when {
                isLapsed -> "Lapsed Policy"
                isPaid -> "Paid Current Cycle"
                dueLocalDate == null -> "Due: ${summary.currentDueDate}"
                dueLocalDate == today -> "Due Today"
                dueLocalDate == today.plusDays(1) -> "Due Tomorrow"
                dueLocalDate < today -> {
                    val diffDays = ChronoUnit.DAYS.between(dueLocalDate, today)
                    "$diffDays Days Overdue"
                }
                else -> {
                    val diffDays = ChronoUnit.DAYS.between(today, dueLocalDate)
                    "Due in $diffDays Days"
                }
            }

            ReminderWrapper(
                policy = policy,
                customer = customer,
                summary = summary,
                dueCategory = dueCategory,
                daysStatus = daysStatus,
                isCompleted = isPaid
            )
        }
    }

    // Category Specific Subsets derived directly from allReminders
    val dueTodayItems = remember(allReminders, today) {
        allReminders.filter { item ->
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(item.summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(item.policy.dueDate)
            dueLocalDate == today && item.outstandingAmount > 0.0 && !item.isLapsed && !item.isCompleted
        }
    }

    val dueTomorrowItems = remember(allReminders, today) {
        allReminders.filter { item ->
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(item.summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(item.policy.dueDate)
            dueLocalDate == today.plusDays(1) && item.outstandingAmount > 0.0 && !item.isLapsed && !item.isCompleted
        }
    }

    val dueThisWeekItems = remember(allReminders, startOfWeek, endOfWeek) {
        allReminders.filter { item ->
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(item.summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(item.policy.dueDate)
            dueLocalDate != null && dueLocalDate >= startOfWeek && dueLocalDate <= endOfWeek && item.outstandingAmount > 0.0 && !item.isLapsed && !item.isCompleted
        }
    }

    val dueThisMonthItems = remember(allReminders, startOfMonth, endOfMonth) {
        allReminders.filter { item ->
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(item.summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(item.policy.dueDate)
            dueLocalDate != null && dueLocalDate >= startOfMonth && dueLocalDate <= endOfMonth && item.outstandingAmount > 0.0 && !item.isLapsed && !item.isCompleted
        }
    }

    val overdueItems = remember(allReminders, today) {
        allReminders.filter { item ->
            val dueLocalDate = SearchFilterEngine.parseLocalDateSafe(item.summary.currentDueDate)
                ?: SearchFilterEngine.parseLocalDateSafe(item.policy.dueDate)
            dueLocalDate != null && dueLocalDate < today && item.outstandingAmount > 0.0 && !item.isLapsed && !item.isCompleted
        }
    }

    val lapsedItems = remember(allReminders) {
        allReminders.filter { it.isLapsed }
    }

    // Metric Summary Numbers
    val dueTodayCount = dueTodayItems.size
    val dueTodayAmount = dueTodayItems.sumOf { it.outstandingAmount }

    val dueTomorrowCount = dueTomorrowItems.size
    val dueTomorrowAmount = dueTomorrowItems.sumOf { it.outstandingAmount }

    val dueThisWeekCount = dueThisWeekItems.size
    val dueThisWeekAmount = dueThisWeekItems.sumOf { it.outstandingAmount }

    val overdueCount = overdueItems.size
    val overdueAmount = overdueItems.sumOf { it.outstandingAmount }

    // Auto-select category with actionable records if Due Today is 0 on load
    LaunchedEffect(dueTodayCount, overdueCount) {
        if (selectedCategory == ReminderCategory.TODAY && dueTodayCount == 0 && overdueCount > 0) {
            selectedCategory = ReminderCategory.OVERDUE
        }
    }

    // Actionable Notification Bell logic: Count active, non-lapsed, non-cancelled policies due THIS WEEK or OVERDUE with outstanding > 0
    val notificationThisWeekReminders = remember(allReminders, overdueItems, dueTodayItems, dueTomorrowItems, dueThisWeekItems) {
        (overdueItems + dueTodayItems + dueTomorrowItems + dueThisWeekItems)
            .filter { item ->
                item.outstandingAmount > 0.0 &&
                !item.isLapsed &&
                !item.isCompleted
            }
            .distinctBy { "${it.policy.id}_${it.policyNumber}" }
            .sortedBy { SearchFilterEngine.parseLocalDateSafe(it.summary.currentDueDate) ?: SearchFilterEngine.parseLocalDateSafe(it.policy.dueDate) ?: LocalDate.MAX }
    }
    val thisWeekNotificationCount = notificationThisWeekReminders.size

    // Apply Filter Chips
    val categoryFilteredReminders = remember(selectedCategory, allReminders, dueTodayItems, dueTomorrowItems, dueThisWeekItems, dueThisMonthItems, overdueItems, lapsedItems) {
        when (selectedCategory) {
            ReminderCategory.ALL -> allReminders.filter { !it.isCompleted || it.isLapsed }
            ReminderCategory.TODAY -> dueTodayItems
            ReminderCategory.TOMORROW -> dueTomorrowItems
            ReminderCategory.THIS_WEEK -> dueThisWeekItems
            ReminderCategory.THIS_MONTH -> dueThisMonthItems
            ReminderCategory.OVERDUE -> overdueItems
            ReminderCategory.LAPSED -> lapsedItems
        }
    }

    // Combine Filter + Search Query in Real-Time
    val filteredReminders = remember(searchQuery, categoryFilteredReminders) {
        val cleanQuery = searchQuery.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            categoryFilteredReminders
        } else {
            categoryFilteredReminders.filter { item ->
                item.matchesSearch(cleanQuery)
            }
        }
    }

    // Empty state messages per filter category
    val emptyStateTitle = "No Reminders Found"
    val emptyStateSubMessage = when (selectedCategory) {
        ReminderCategory.TODAY -> "No premiums are due today."
        ReminderCategory.TOMORROW -> "No premiums are due tomorrow."
        ReminderCategory.THIS_WEEK -> "No premiums are due this week."
        ReminderCategory.THIS_MONTH -> "No premiums are due this month."
        ReminderCategory.OVERDUE -> "No overdue premiums."
        ReminderCategory.LAPSED -> "No lapsed policies."
        ReminderCategory.ALL -> "No matching policy reminders found."
    }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CardBg,
                    contentColor = TextWhite,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Premium Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "LIC Premium Collection Center",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reminder_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    // FLOATING NOTIFICATION BELL AT TOP-RIGHT HEADER AREA
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("notification_bell_button")
                    ) {
                        Surface(
                            onClick = { showThisWeekNotificationDialog = true },
                            shape = CircleShape,
                            color = RoyalBlueGlow,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Reminders This Week",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Badge showing count of reminders due THIS WEEK
                        Surface(
                            color = if (thisWeekNotificationCount > 0) AccentRed else CardBorder,
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, DarkBg),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$thisWeekNotificationCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.refreshData { success, msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextWhite
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("more_menu_button")
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
                            modifier = Modifier.background(CardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark All as Contacted", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch { snackbarHostState.showSnackbar("All reminders marked as contacted") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Reminder List", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch { snackbarHostState.showSnackbar("Reminder summary generated") }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextWhite
                )
            )
        },
    ) { innerPadding ->
        // Pull To Refresh Container
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refreshData { _, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ==================== DASHBOARD SUMMARY CARDS ====================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DUE SUMMARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 2x2 Equal Grid Dashboard Summary Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryMetricCard(
                                title = "Due Today",
                                countNumber = dueTodayCount,
                                totalAmount = dueTodayAmount,
                                color = AccentAmber,
                                icon = Icons.Default.Today,
                                isSelected = selectedCategory == ReminderCategory.TODAY,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategory = ReminderCategory.TODAY }
                            )

                            SummaryMetricCard(
                                title = "Due Tomorrow",
                                countNumber = dueTomorrowCount,
                                totalAmount = dueTomorrowAmount,
                                color = RoyalBlueLight,
                                icon = Icons.Default.Event,
                                isSelected = selectedCategory == ReminderCategory.TOMORROW,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategory = ReminderCategory.TOMORROW }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryMetricCard(
                                title = "Due This Week",
                                countNumber = dueThisWeekCount,
                                totalAmount = dueThisWeekAmount,
                                color = AccentIndigo,
                                icon = Icons.Default.DateRange,
                                isSelected = selectedCategory == ReminderCategory.THIS_WEEK,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategory = ReminderCategory.THIS_WEEK }
                            )

                            SummaryMetricCard(
                                title = "Overdue",
                                countNumber = overdueCount,
                                totalAmount = overdueAmount,
                                color = AccentRed,
                                icon = Icons.Default.Warning,
                                isSelected = selectedCategory == ReminderCategory.OVERDUE,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategory = ReminderCategory.OVERDUE }
                            )
                        }
                    }
                }

                // ==================== REAL-TIME SEARCH BAR ====================
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("search_text_input"),
                            placeholder = {
                                Text(
                                    text = "Search customer, policy or mobile...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Icon",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search",
                                                tint = TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardBg,
                                unfocusedContainerColor = CardBg,
                                focusedBorderColor = RoyalBlueLight,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = RoyalBlueLight
                            )
                        )
                    }
                }

                // ==================== EXACT FILTER CHIPS ====================
                item {
                    val categoriesToDisplay = listOf(
                        ReminderCategory.ALL,
                        ReminderCategory.TODAY,
                        ReminderCategory.TOMORROW,
                        ReminderCategory.THIS_WEEK,
                        ReminderCategory.THIS_MONTH,
                        ReminderCategory.OVERDUE,
                        ReminderCategory.LAPSED
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        items(categoriesToDisplay) { category ->
                            val isSelected = selectedCategory == category

                            val animatedBgColor by animateColorAsState(
                                targetValue = if (isSelected) RoyalBluePrimary else CardBg,
                                animationSpec = tween(durationMillis = 200),
                                label = "chipBg"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) RoyalBlueLight else CardBorder,
                                animationSpec = tween(durationMillis = 200),
                                label = "chipBorder"
                            )

                            Surface(
                                onClick = { selectedCategory = category },
                                shape = RoundedCornerShape(20.dp),
                                color = animatedBgColor,
                                border = BorderStroke(1.dp, animatedBorderColor),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("filter_chip_${category.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else TextMuted,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== REMINDER LIST / EMPTY STATE ====================
                if (isLoading) {
                    items(3) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            LoadingSkeletonCard()
                        }
                    }
                } else if (filteredReminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(110.dp)
                                ) {
                                    Surface(
                                        color = RoyalBluePrimary.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(100.dp)
                                    ) {}

                                    Surface(
                                        color = CardBg,
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.dp, CardBorder),
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Notifications,
                                                contentDescription = null,
                                                tint = RoyalBlueLight,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = AccentGreen,
                                        shape = CircleShape,
                                        border = BorderStroke(2.dp, DarkBg),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = emptyStateTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = emptyStateSubMessage,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextMuted,
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        selectedCategory = ReminderCategory.ALL
                                        searchQuery = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                                    border = BorderStroke(1.dp, CardBorder),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Reset Filters & Clear Search", color = TextWhite, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredReminders, key = { "${it.policy.id}_${it.policyNumber}_${it.dueDate}_${it.outstandingAmount}" }) { reminder ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ReminderListCard(
                                item = reminder,
                                onCardClick = { selectedReminderForSheet = reminder },
                                onWhatsAppClick = {
                                    sendWhatsAppReminder(context, reminder.customerMobile, reminder.customerName, reminder.premiumAmount.toString(), reminder.policyNumber)
                                },
                                onCallClick = {
                                    callCustomer(context, reminder.customerMobile)
                                },
                                onCollectClick = {
                                    selectedPolicyForCollection = reminder.policy
                                    onCollectPremium(reminder.policy)
                                },
                                onDetailsClick = {
                                    selectedReminderForSheet = reminder
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== THIS WEEK NOTIFICATION DIALOG ====================
    if (showThisWeekNotificationDialog) {
        RemindersThisWeekDialog(
            thisWeekReminders = notificationThisWeekReminders,
            onDismiss = { showThisWeekNotificationDialog = false },
            onCollectClick = { policy ->
                showThisWeekNotificationDialog = false
                selectedPolicyForCollection = policy
                onCollectPremium(policy)
            },
            onWhatsAppClick = { item ->
                sendWhatsAppReminder(context, item.customerMobile, item.customerName, item.premiumAmount.toString(), item.policyNumber)
            },
            onCallClick = { item ->
                callCustomer(context, item.customerMobile)
            },
            onDetailsClick = { item ->
                showThisWeekNotificationDialog = false
                onViewPolicyDetail(item.policyNumber)
            }
        )
    }

    // ==================== RECORD PAYMENT / COLLECT DIALOG ====================
    selectedPolicyForCollection?.let { policy ->
        PaymentCollectionDialog(
            policy = policy,
            customersList = customers,
            policiesList = policies,
            existingPayments = payments,
            onDismiss = { selectedPolicyForCollection = null },
            onCollect = { amount, lateFee, mode, receiptNo, notes ->
                viewModel.collectPremium(
                    policy = policy,
                    paidAmount = amount,
                    lateFee = lateFee,
                    paymentMode = mode,
                    receiptNo = receiptNo,
                    notes = notes,
                    onSuccess = {
                        selectedPolicyForCollection = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Payment of ₹${String.format("%,.0f", amount)} collected successfully!")
                        }
                    }
                )
            }
        )
    }

    // ==================== BOTTOM SHEET DETAILS MODAL ====================
    selectedReminderForSheet?.let { reminder ->
        ModalBottomSheet(
            onDismissRequest = { selectedReminderForSheet = null },
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = reminder.customerName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Policy Details & Collection Status",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }

                    Surface(
                        color = RoyalBluePrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = reminder.avatarInitials,
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                DetailSheetRow("Policy Number", reminder.policyNumber)
                DetailSheetRow("Plan & Code", reminder.planName)
                DetailSheetRow("Customer Mobile", reminder.customerMobile.ifBlank { "N/A" })
                DetailSheetRow("Premium Due", "₹${String.format("%,.2f", reminder.premiumAmount)}")
                DetailSheetRow("Outstanding Amount", "₹${String.format("%,.2f", reminder.outstandingAmount)}", isHighlight = true)
                DetailSheetRow("Payment Mode", reminder.premiumMode)
                DetailSheetRow("Due Date", reminder.dueDate)
                DetailSheetRow("Policy Status", reminder.policy.status)

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val pol = reminder.policy
                            selectedReminderForSheet = null
                            selectedPolicyForCollection = pol
                            onCollectPremium(pol)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Collect Premium", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val polNum = reminder.policyNumber
                            selectedReminderForSheet = null
                            onViewPolicyDetail(polNum)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        border = BorderStroke(1.dp, RoyalBlueLight),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Full Policy Details", color = RoyalBlueLight, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ==================== REMINDERS THIS WEEK DIALOG ====================
@Composable
private fun RemindersThisWeekDialog(
    thisWeekReminders: List<ReminderWrapper>,
    onDismiss: () -> Unit,
    onCollectClick: (PolicyEntity) -> Unit,
    onWhatsAppClick: (ReminderWrapper) -> Unit,
    onCallClick: (ReminderWrapper) -> Unit,
    onDetailsClick: (ReminderWrapper) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = RoyalBlueGlow,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Reminders This Week",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "${thisWeekReminders.size} policies due in current week",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.5.sp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                if (thisWeekReminders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No premiums are due this week.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(thisWeekReminders, key = { "${it.policy.id}_${it.policyNumber}_${it.dueDate}_${it.outstandingAmount}" }) { item ->
                            Surface(
                                color = DarkBg.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.customerName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = TextWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Surface(
                                            color = AccentAmber.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = item.daysStatus,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = AccentAmber,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Policy: ${item.policyNumber}  •  ${item.planName}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = RoyalBlueLight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Due Date", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                            Text(item.dueDate, style = MaterialTheme.typography.bodySmall.copy(color = TextWhite, fontWeight = FontWeight.SemiBold))
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Premium", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                            Text("₹${String.format("%,.0f", item.premiumAmount)}", style = MaterialTheme.typography.bodySmall.copy(color = TextWhite, fontWeight = FontWeight.SemiBold))
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Outstanding", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                            Text("₹${String.format("%,.0f", item.outstandingAmount)}", style = MaterialTheme.typography.bodySmall.copy(color = AccentAmber, fontWeight = FontWeight.Bold))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onWhatsAppClick(item) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF166534).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", tint = Color(0xFF4ADE80), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Chat", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF4ADE80), fontSize = 10.sp))
                                            }
                                        }

                                        IconButton(
                                            onClick = { onCallClick(item) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(RoyalBluePrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBlueLight, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Call", style = MaterialTheme.typography.labelSmall.copy(color = RoyalBlueLight, fontSize = 10.sp))
                                            }
                                        }

                                        Button(
                                            onClick = { onCollectClick(item.policy) },
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Payment, contentDescription = "Collect", tint = Color.Black, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Collect", style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== DASHBOARD METRIC CARD ====================
@Composable
private fun SummaryMetricCard(
    title: String,
    countNumber: Int,
    totalAmount: Double,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) color else CardBorder
        ),
        shadowElevation = if (isSelected) 6.dp else 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$countNumber Records",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "₹${String.format("%,.0f", totalAmount)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}

// ==================== REMINDER LIST ITEM CARD ====================
@Composable
private fun ReminderListCard(
    item: ReminderWrapper,
    onCardClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onCollectClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Surface(
        onClick = onCardClick,
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // TOP SECTION: Customer Avatar, Customer Name & Status Badge
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
                        color = RoyalBluePrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.avatarInitials,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                StatusBadge(
                    category = item.dueCategory,
                    statusText = item.daysStatus,
                    isCompleted = item.isCompleted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            // MIDDLE SECTION: Policy Number, Plan Name, Premium Amount & Payment Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Policy & Plan",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.policyNumber,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = item.planName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RoyalBlueLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Premium Amount",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${String.format("%,.2f", item.premiumAmount)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Surface(
                        color = RoyalBluePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = item.premiumMode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoyalBlueLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM SECTION: Due Date & Outstanding Amount
            Surface(
                color = DarkBg.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.5.sp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = RoyalBlueLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = item.dueDate,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = RoyalBlueLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Outstanding Amount",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.5.sp)
                        )
                        Text(
                            text = "₹${String.format("%,.0f", item.outstandingAmount)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (item.outstandingAmount > 0) AccentAmber else AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // QUICK ACTION BAR: 4 Functional Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. WhatsApp
                FilledTonalButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_whatsapp_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF166534).copy(alpha = 0.35f),
                        contentColor = Color(0xFF4ADE80)
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "WhatsApp",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "WhatsApp",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // 2. Call
                FilledTonalButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_call_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RoyalBluePrimary.copy(alpha = 0.25f),
                        contentColor = RoyalBlueLight
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = RoyalBlueLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // 3. Collect (Fully Functional!)
                FilledTonalButton(
                    onClick = onCollectClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_collect_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentAmber.copy(alpha = 0.25f),
                        contentColor = AccentAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Collect",
                            tint = AccentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Collect",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // 4. Details
                FilledTonalButton(
                    onClick = onDetailsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_details_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CardBorder.copy(alpha = 0.6f),
                        contentColor = TextWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(category: ReminderCategory, statusText: String, isCompleted: Boolean) {
    val (bgColor, textColor) = when {
        isCompleted -> AccentGreen.copy(alpha = 0.2f) to AccentGreen
        category == ReminderCategory.TODAY -> AccentAmber.copy(alpha = 0.2f) to AccentAmber
        category == ReminderCategory.TOMORROW -> RoyalBlueLight.copy(alpha = 0.2f) to RoyalBlueLight
        category == ReminderCategory.THIS_WEEK -> AccentIndigo.copy(alpha = 0.2f) to AccentIndigo
        category == ReminderCategory.THIS_MONTH -> AccentBlue.copy(alpha = 0.2f) to AccentBlue
        category == ReminderCategory.OVERDUE -> AccentRed.copy(alpha = 0.2f) to AccentRed
        category == ReminderCategory.LAPSED -> Color.Gray.copy(alpha = 0.2f) to Color.LightGray
        else -> RoyalBlueLight.copy(alpha = 0.2f) to RoyalBlueLight
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailSheetRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isHighlight) AccentAmber else TextWhite,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun LoadingSkeletonCard() {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RoyalBlueLight, modifier = Modifier.size(32.dp))
        }
    }
}

private fun sendWhatsAppReminder(
    context: Context,
    mobile: String,
    customerName: String,
    amount: String,
    policyNo: String
) {
    try {
        val cleanNumber = mobile.replace(" ", "").replace("+", "")
        val message = Uri.encode(
            "Dear $customerName,\n\nThis is a friendly reminder regarding your LIC Policy No. #$policyNo. Premium amount ₹ $amount is due for collection.\n\nPlease keep the amount ready or pay online.\n\nThank you,\nYour LIC Premium Advisor"
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$message")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
    }
}

private fun callCustomer(context: Context, mobile: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$mobile")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch phone dialer", Toast.LENGTH_SHORT).show()
    }
}

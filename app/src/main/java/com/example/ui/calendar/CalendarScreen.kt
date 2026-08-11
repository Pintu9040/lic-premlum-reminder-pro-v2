package com.example.ui.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.util.PaymentAllocationEngine
import com.example.util.SearchFilterEngine
import com.example.util.OdishaHoliday
import com.example.util.OdishaHolidays
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Royal Blue Dark Theme Palette
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentPurple = Color(0xFFA855F7)

enum class CalendarViewMode {
    MONTH, WEEK
}

enum class CalendarFilterType {
    ALL, TODAY_DUE, TOMORROW, THIS_WEEK, THIS_MONTH, OVERDUE, LAPSED
}

enum class FullScreenDueType {
    NONE, TODAY, TOMORROW, THIS_WEEK, THIS_MONTH, OVERDUE, LAPSED, CUSTOM_DATE
}

data class CalendarCustomerItem(
    val id: Long,
    val policyId: Long,
    val customerId: Long,
    val customerName: String,
    val policyNumber: String,
    val planName: String,
    val premiumAmount: Double,
    val mode: String,
    val dueDate: String,
    val parsedDueDate: LocalDate?,
    val dueDayNumber: Int,
    val outstandingAmount: Double,
    val advanceAdjusted: Double,
    val status: String, // "Today", "Tomorrow", "Overdue", "Paid", "Lapsed", "Upcoming"
    val phone: String,
    val avatarInitials: String,
    val policy: PolicyEntity,
    val customer: CustomerEntity?
)

data class MonthDayInfo(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val hasOverdue: Boolean,
    val hasDue: Boolean,
    val hasPaid: Boolean,
    val customerCount: Int,
    val holiday: OdishaHoliday? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: LicViewModel? = null,
    onBackClick: () -> Unit = {},
    onCollectPremium: (PolicyEntity) -> Unit = {},
    onViewPolicyDetail: (PolicyEntity) -> Unit = {},
    onNavigateToCustomerDetail: (CustomerEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val monthNames = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }

    var selectedMonthIndex by remember { mutableIntStateOf(today.monthValue - 1) }
    var currentYear by remember { mutableIntStateOf(today.year) }

    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDayNumber by remember { mutableIntStateOf(today.dayOfMonth) }
    var selectedWeekIndex by rememberSaveable { mutableIntStateOf(getWeekIndexForDayStatic(today.dayOfMonth)) }

    var selectedFilter by remember { mutableStateOf(CalendarFilterType.ALL) }
    var fullScreenType by remember { mutableStateOf(FullScreenDueType.NONE) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Live Room DB Data
    val dbPolicies by viewModel?.policies?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val dbCustomers by viewModel?.customers?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val dbPayments by viewModel?.payments?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    // Combine policies, customers, payments with PaymentAllocationEngine logic
    val allCalendarItems = remember(dbPolicies, dbCustomers, dbPayments, today) {
        if (dbPolicies.isNotEmpty()) {
            dbPolicies.map { policy ->
                val customer = dbCustomers.find { it.id == policy.customerId }
                val policyPayments = dbPayments.filter { it.policyId == policy.id }
                val dueSummary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, policyPayments)

                val custName = customer?.name?.ifBlank { policy.customerName } ?: policy.customerName
                val phone = customer?.mobile?.ifBlank { customer.whatsapp } ?: ""
                val pDueDate = SearchFilterEngine.parseLocalDateSafe(policy.dueDate)
                val dueDayNum = pDueDate?.dayOfMonth ?: 1

                val isOverdue = pDueDate != null && pDueDate.isBefore(today) && dueSummary.outstanding > 0.0
                val isLapsed = policy.status.equals("Lapsed", ignoreCase = true)
                val isPaid = dueSummary.outstanding == 0.0 && dueSummary.totalPaidForCurrentDue >= dueSummary.totalDue

                val statusText = when {
                    isLapsed -> "Lapsed"
                    pDueDate != null && pDueDate.isEqual(today) -> "Today"
                    pDueDate != null && pDueDate.isEqual(today.plusDays(1)) -> "Tomorrow"
                    isOverdue -> "Overdue"
                    isPaid -> "Paid"
                    else -> "Upcoming"
                }

                val initials = custName.split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifBlank { "CU" }

                CalendarCustomerItem(
                    id = policy.id,
                    policyId = policy.id,
                    customerId = policy.customerId,
                    customerName = custName,
                    policyNumber = policy.policyNumber,
                    planName = policy.planName,
                    premiumAmount = policy.premiumAmount,
                    mode = policy.premiumMode,
                    dueDate = policy.dueDate,
                    parsedDueDate = pDueDate,
                    dueDayNumber = dueDayNum,
                    outstandingAmount = dueSummary.outstanding,
                    advanceAdjusted = dueSummary.advanceAdjusted,
                    status = statusText,
                    phone = phone,
                    avatarInitials = initials,
                    policy = policy,
                    customer = customer
                )
            }
        } else {
            // Demo/Fallback data when database is completely empty
            listOf(
                CalendarCustomerItem(1, 1, 101, "Rahul Kumar", "847291038", "Jeevan Umang (Plan 945)", 12750.0, "Yearly", today.toString(), today, today.dayOfMonth, 12750.0, 0.0, "Today", "+91 98765 43210", "RK", createDemoPolicy(1, "847291038", "Rahul Kumar", 12750.0, today.toString()), null),
                CalendarCustomerItem(2, 2, 102, "Anita Das", "918237465", "Jeevan Labh (Plan 936)", 18200.0, "Half-Yearly", today.toString(), today, today.dayOfMonth, 15200.0, 3000.0, "Today", "+91 98123 45678", "AD", createDemoPolicy(2, "918237465", "Anita Das", 18200.0, today.toString()), null),
                CalendarCustomerItem(3, 3, 103, "Suresh Patel", "654321987", "Jeevan Lakshya (Plan 933)", 24000.0, "Yearly", today.minusDays(3).toString(), today.minusDays(3), today.minusDays(3).dayOfMonth, 24000.0, 0.0, "Overdue", "+91 96543 21098", "SP", createDemoPolicy(3, "654321987", "Suresh Patel", 24000.0, today.minusDays(3).toString()), null),
                CalendarCustomerItem(4, 4, 104, "Rajesh Sharma", "736451928", "Endowment Plan (Plan 914)", 8500.0, "Quarterly", today.plusDays(1).toString(), today.plusDays(1), today.plusDays(1).dayOfMonth, 8500.0, 0.0, "Tomorrow", "+91 97654 32109", "RS", createDemoPolicy(4, "736451928", "Rajesh Sharma", 8500.0, today.plusDays(1).toString()), null),
                CalendarCustomerItem(5, 5, 105, "Priya Verma", "543216879", "Jeevan Anand (Plan 915)", 15300.0, "Yearly", today.plusDays(5).toString(), today.plusDays(5), today.plusDays(5).dayOfMonth, 15300.0, 0.0, "Upcoming", "+91 95432 10987", "PV", createDemoPolicy(5, "543216879", "Priya Verma", 15300.0, today.plusDays(5).toString()), null),
                CalendarCustomerItem(6, 6, 106, "Amit Sahoo", "432156789", "Money Back (Plan 920)", 9800.0, "Half-Yearly", today.plusDays(12).toString(), today.plusDays(12), today.plusDays(12).dayOfMonth, 9800.0, 0.0, "Upcoming", "+91 94321 09876", "AS", createDemoPolicy(6, "432156789", "Amit Sahoo", 9800.0, today.plusDays(12).toString()), null)
            )
        }
    }

    // Filtered lists
    val startOfWeek = remember(today) { today.minusDays(today.dayOfWeek.value.toLong() - 1) }
    val endOfWeek = remember(today) { startOfWeek.plusDays(6) }

    val todayItems = remember(allCalendarItems, today) {
        allCalendarItems.filter { it.parsedDueDate?.isEqual(today) == true || it.status == "Today" }
    }
    val tomorrowItems = remember(allCalendarItems, today) {
        allCalendarItems.filter { it.parsedDueDate?.isEqual(today.plusDays(1)) == true || it.status == "Tomorrow" }
    }
    val thisWeekItems = remember(allCalendarItems, startOfWeek, endOfWeek) {
        allCalendarItems.filter { item ->
            val d = item.parsedDueDate
            d != null && !d.isBefore(startOfWeek) && !d.isAfter(endOfWeek)
        }
    }
    val thisMonthItems = remember(allCalendarItems, today, selectedMonthIndex, currentYear) {
        allCalendarItems.filter { item ->
            val d = item.parsedDueDate
            d != null && d.monthValue == (selectedMonthIndex + 1) && d.year == currentYear
        }
    }
    val overdueItems = remember(allCalendarItems, today) {
        allCalendarItems.filter { item ->
            val d = item.parsedDueDate
            (d != null && d.isBefore(today) && item.outstandingAmount > 0.0) || item.status == "Overdue"
        }
    }
    val lapsedItems = remember(allCalendarItems) {
        allCalendarItems.filter { item ->
            item.status == "Lapsed" || item.policy.status.equals("Lapsed", ignoreCase = true)
        }
    }

    // Currently filtered items for standard calendar view search/filters
    val filteredCalendarItems = remember(selectedFilter, searchQuery, allCalendarItems) {
        val baseList = when (selectedFilter) {
            CalendarFilterType.ALL -> allCalendarItems
            CalendarFilterType.TODAY_DUE -> todayItems
            CalendarFilterType.TOMORROW -> tomorrowItems
            CalendarFilterType.THIS_WEEK -> thisWeekItems
            CalendarFilterType.THIS_MONTH -> thisMonthItems
            CalendarFilterType.OVERDUE -> overdueItems
            CalendarFilterType.LAPSED -> lapsedItems
        }
        if (searchQuery.isBlank()) baseList
        else {
            baseList.filter { item ->
                SearchFilterEngine.matchesQuery(
                    query = searchQuery,
                    fields = listOf(item.customerName, item.policyNumber, item.planName, item.phone, item.status, item.mode)
                )
            }
        }
    }

    // Month grid cells computation
    val daysInMonth = remember(selectedMonthIndex, currentYear) {
        try {
            LocalDate.of(currentYear, selectedMonthIndex + 1, 1).lengthOfMonth()
        } catch (e: Exception) { 31 }
    }
    val firstDayOffset = remember(selectedMonthIndex, currentYear) {
        try {
            val firstDate = LocalDate.of(currentYear, selectedMonthIndex + 1, 1)
            firstDate.dayOfWeek.value % 7 // Sunday = 0
        } catch (e: Exception) { 0 }
    }

    val allMonthCells = remember(selectedMonthIndex, currentYear, filteredCalendarItems) {
        val cells = mutableListOf<MonthDayInfo>()
        var currentDayCounter = 1
        var prevMonthCounter = 31 - firstDayOffset + 1
        var nextMonthCounter = 1

        for (cellIndex in 0 until 42) {
            val info = when {
                cellIndex < firstDayOffset -> {
                    MonthDayInfo(prevMonthCounter++, false, false, false, false, false, 0)
                }
                currentDayCounter <= daysInMonth -> {
                    val dayNum = currentDayCounter++
                    val dayCusts = filteredCalendarItems.filter { it.parsedDueDate?.dayOfMonth == dayNum }
                    val cellDate = try { LocalDate.of(currentYear, selectedMonthIndex + 1, dayNum) } catch (e: Exception) { null }
                    val holiday = cellDate?.let { OdishaHolidays.getHoliday(it) }

                    MonthDayInfo(
                        dayNumber = dayNum,
                        isCurrentMonth = true,
                        isToday = (dayNum == today.dayOfMonth && (selectedMonthIndex + 1) == today.monthValue && currentYear == today.year),
                        hasOverdue = dayCusts.any { it.status == "Overdue" },
                        hasDue = dayCusts.any { it.status == "Today" || it.status == "Upcoming" || it.status == "Tomorrow" },
                        hasPaid = dayCusts.any { it.status == "Paid" },
                        customerCount = dayCusts.size,
                        holiday = holiday
                    )
                }
                else -> {
                    MonthDayInfo(nextMonthCounter++, false, false, false, false, false, 0)
                }
            }
            cells.add(info)
        }
        cells
    }

    // FULL SCREEN VIEW OVERLAY HANDLER
    if (fullScreenType != FullScreenDueType.NONE) {
        val (targetTitle, targetBadgeText, rawItems) = when (fullScreenType) {
            FullScreenDueType.TODAY -> Triple("Today Due List", "Today Due", todayItems)
            FullScreenDueType.TOMORROW -> Triple("Tomorrow Due List", "Tomorrow Due", tomorrowItems)
            FullScreenDueType.THIS_WEEK -> Triple("This Week Dues", "This Week", thisWeekItems)
            FullScreenDueType.THIS_MONTH -> Triple("This Month Dues", "This Month", thisMonthItems)
            FullScreenDueType.OVERDUE -> Triple("Overdue Premium Dues", "Overdue", overdueItems)
            FullScreenDueType.LAPSED -> Triple("Lapsed Policies List", "Lapsed", lapsedItems)
            FullScreenDueType.CUSTOM_DATE -> {
                val dateStr = "$selectedDayNumber ${monthNames[selectedMonthIndex]} $currentYear"
                val dayItems = allCalendarItems.filter { it.parsedDueDate?.dayOfMonth == selectedDayNumber }
                Triple("Dues for $dateStr", dateStr, dayItems)
            }
            else -> Triple("Premium Dues List", "Dues", allCalendarItems)
        }

        val targetHoliday = when (fullScreenType) {
            FullScreenDueType.CUSTOM_DATE -> {
                try { LocalDate.of(currentYear, selectedMonthIndex + 1, selectedDayNumber) } catch (e: Exception) { null }
                    ?.let { OdishaHolidays.getHoliday(it) }
            }
            FullScreenDueType.TODAY -> OdishaHolidays.getHoliday(today)
            FullScreenDueType.TOMORROW -> OdishaHolidays.getHoliday(today.plusDays(1))
            else -> null
        }

        val fullScreenFilteredItems = remember(rawItems, searchQuery) {
            if (searchQuery.isBlank()) rawItems
            else {
                rawItems.filter { item ->
                    SearchFilterEngine.matchesQuery(
                        query = searchQuery,
                        fields = listOf(item.customerName, item.policyNumber, item.planName, item.phone, item.status, item.mode)
                    )
                }
            }
        }

        FullScreenDueListView(
            title = targetTitle,
            badgeText = targetBadgeText,
            fullScreenType = fullScreenType,
            items = fullScreenFilteredItems,
            searchQuery = searchQuery,
            selectedHoliday = targetHoliday,
            onSearchQueryChange = { searchQuery = it },
            onBack = { fullScreenType = FullScreenDueType.NONE },
            onSelectFilter = { newType -> fullScreenType = newType },
            onWhatsAppClick = { item ->
                openWhatsApp(context, item.phone, item.customerName, item.policyNumber, formatFullAmountDouble(item.premiumAmount), item.dueDate)
            },
            onCallClick = { item ->
                openDialer(context, item.phone)
            },
            onCollectClick = { item ->
                if (item.policy.id > 0) onCollectPremium(item.policy)
                else Toast.makeText(context, "Initiating payment for ${item.customerName}...", Toast.LENGTH_SHORT).show()
            },
            onDetailsClick = { item ->
                if (item.policy.id > 0) onViewPolicyDetail(item.policy)
                else if (item.customer != null) onNavigateToCustomerDetail(item.customer)
                else Toast.makeText(context, "Opening policy details...", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    // MAIN PREMIUM CALENDAR SCREEN
    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextWhite,
                    navigationIconContentColor = TextWhite,
                    actionIconContentColor = TextWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Premium Calendar",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "${monthNames[selectedMonthIndex]} $currentYear",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RoyalBlueLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                },
                actions = {
                    // TODAY BUTTON -> Opens FULL SCREEN Premium Due List for Today
                    TextButton(
                        onClick = {
                            selectedMonthIndex = today.monthValue - 1
                            currentYear = today.year
                            selectedDayNumber = today.dayOfMonth
                            selectedWeekIndex = getWeekIndexForDayStatic(today.dayOfMonth)
                            selectedFilter = CalendarFilterType.TODAY_DUE
                            fullScreenType = FullScreenDueType.TODAY
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = RoyalBlueLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = "Today",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Search Toggle
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Filter Icon -> Quick toggle to Today Due or Filter
                    IconButton(onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.ALL) CalendarFilterType.TODAY_DUE else CalendarFilterType.ALL
                    }) {
                        Icon(
                            imageVector = if (selectedFilter != CalendarFilterType.ALL) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedFilter != CalendarFilterType.ALL) RoyalBlueLight else TextWhite
                        )
                    }

                    // Three-dot Menu Dropdown
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Today Due List", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.TODAY
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tomorrow Due List", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.TOMORROW
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("This Week Dues", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.THIS_WEEK
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("This Month Dues", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.THIS_MONTH
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Overdue Dues", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.OVERDUE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lapsed Policies", color = TextWhite) },
                                onClick = {
                                    showMenuDropdown = false
                                    fullScreenType = FullScreenDueType.LAPSED
                                }
                            )
                        }
                    }
                }
            )
        }
        // FloatingActionButton REMOVED completely per Requirement 1
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SEARCH BAR
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search customer name, phone or policy #...", color = TextMuted, fontSize = 14.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = RoyalBlueLight,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
            }

            // MONTH / WEEK SEGMENTED TOGGLE
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val monthSelected = (viewMode == CalendarViewMode.MONTH)
                    val weekSelected = (viewMode == CalendarViewMode.WEEK)

                    Surface(
                        onClick = { viewMode = CalendarViewMode.MONTH },
                        color = if (monthSelected) RoyalBluePrimary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarViewMonth,
                                contentDescription = "Month View",
                                tint = if (monthSelected) TextWhite else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Month",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (monthSelected) TextWhite else TextMuted,
                                    fontWeight = if (monthSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Surface(
                        onClick = { viewMode = CalendarViewMode.WEEK },
                        color = if (weekSelected) RoyalBluePrimary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarViewWeek,
                                contentDescription = "Week View",
                                tint = if (weekSelected) TextWhite else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Week",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (weekSelected) TextWhite else TextMuted,
                                    fontWeight = if (weekSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }

            // DUE SUMMARY CARDS ROW (Today Due, Tomorrow, This Week, Overdue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Today Due",
                    count = "${todayItems.size}",
                    amount = formatCompactAmountDouble(todayItems.sumOf { it.outstandingAmount.ifZero(it.premiumAmount) }),
                    accentColor = AccentAmber,
                    isSelected = (selectedFilter == CalendarFilterType.TODAY_DUE),
                    onClick = {
                        fullScreenType = FullScreenDueType.TODAY
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Tomorrow",
                    count = "${tomorrowItems.size}",
                    amount = formatCompactAmountDouble(tomorrowItems.sumOf { it.outstandingAmount.ifZero(it.premiumAmount) }),
                    accentColor = RoyalBlueLight,
                    isSelected = (selectedFilter == CalendarFilterType.TOMORROW),
                    onClick = {
                        fullScreenType = FullScreenDueType.TOMORROW
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "This Week",
                    count = "${thisWeekItems.size}",
                    amount = formatCompactAmountDouble(thisWeekItems.sumOf { it.outstandingAmount.ifZero(it.premiumAmount) }),
                    accentColor = AccentGreen,
                    isSelected = (selectedFilter == CalendarFilterType.THIS_WEEK),
                    onClick = {
                        fullScreenType = FullScreenDueType.THIS_WEEK
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Overdue",
                    count = "${overdueItems.size}",
                    amount = formatCompactAmountDouble(overdueItems.sumOf { it.outstandingAmount }),
                    accentColor = AccentRed,
                    isSelected = (selectedFilter == CalendarFilterType.OVERDUE),
                    onClick = {
                        fullScreenType = FullScreenDueType.OVERDUE
                    }
                )
            }

            // CALENDAR GRID CONTAINER (MONTH OR WEEK VIEW)
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    AnimatedContent(
                        targetState = viewMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f) togetherWith
                                    fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.96f)
                        },
                        label = "calendarViewModeTransition"
                    ) { currentMode ->
                        Column {
                            if (currentMode == CalendarViewMode.MONTH) {
                                // Month Switcher Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (selectedMonthIndex > 0) {
                                                selectedMonthIndex--
                                            } else {
                                                selectedMonthIndex = 11
                                                currentYear--
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Month",
                                            tint = TextWhite
                                        )
                                    }

                                    Text(
                                        text = "${monthNames[selectedMonthIndex]} $currentYear",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    )

                                    IconButton(
                                        onClick = {
                                            if (selectedMonthIndex < 11) {
                                                selectedMonthIndex++
                                            } else {
                                                selectedMonthIndex = 0
                                                currentYear++
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Next Month",
                                            tint = TextWhite
                                        )
                                    }
                                }
                            } else {
                                // Week Switcher Header
                                val weekStartCell = allMonthCells.getOrNull(selectedWeekIndex * 7)
                                val weekEndCell = allMonthCells.getOrNull(selectedWeekIndex * 7 + 6)
                                val weekRangeText = if (weekStartCell != null && weekEndCell != null) {
                                    "${weekStartCell.dayNumber} - ${weekEndCell.dayNumber} ${monthNames[selectedMonthIndex]} $currentYear"
                                } else {
                                    "Week ${selectedWeekIndex + 1}"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (selectedWeekIndex > 0) {
                                                selectedWeekIndex--
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Week",
                                            tint = TextWhite
                                        )
                                    }

                                    Text(
                                        text = weekRangeText,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    )

                                    IconButton(
                                        onClick = {
                                            if (selectedWeekIndex < 5) {
                                                selectedWeekIndex++
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Next Week",
                                            tint = TextWhite
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // DAYS OF WEEK HEADER
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { day ->
                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // GRID DISPLAY (MONTH OR WEEK)
                            if (currentMode == CalendarViewMode.MONTH) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (week in 0..5) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            for (dayOfWeek in 0..6) {
                                                val cellIndex = week * 7 + dayOfWeek
                                                val dayInfo = allMonthCells.getOrElse(cellIndex) {
                                                    MonthDayInfo(0, false, false, false, false, false, 0)
                                                }

                                                CalendarDayCell(
                                                    modifier = Modifier.weight(1f),
                                                    dayInfo = dayInfo,
                                                    isSelected = (dayInfo.isCurrentMonth && dayInfo.dayNumber == selectedDayNumber),
                                                    isFilterActive = (selectedFilter != CalendarFilterType.ALL),
                                                    isFilterMatching = (dayInfo.customerCount > 0),
                                                    onClick = {
                                                        if (dayInfo.isCurrentMonth) {
                                                            selectedDayNumber = dayInfo.dayNumber
                                                            selectedWeekIndex = getWeekIndexForDayStatic(dayInfo.dayNumber)
                                                            fullScreenType = FullScreenDueType.CUSTOM_DATE
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // WEEK VIEW
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (dayOfWeek in 0..6) {
                                        val cellIndex = selectedWeekIndex * 7 + dayOfWeek
                                        val dayInfo = allMonthCells.getOrElse(cellIndex) {
                                            MonthDayInfo(0, false, false, false, false, false, 0)
                                        }

                                        CalendarDayCell(
                                            modifier = Modifier.weight(1f),
                                            dayInfo = dayInfo,
                                            isSelected = (dayInfo.isCurrentMonth && dayInfo.dayNumber == selectedDayNumber),
                                            isFilterActive = (selectedFilter != CalendarFilterType.ALL),
                                            isFilterMatching = (dayInfo.customerCount > 0),
                                            onClick = {
                                                if (dayInfo.isCurrentMonth) {
                                                    selectedDayNumber = dayInfo.dayNumber
                                                    selectedWeekIndex = getWeekIndexForDayStatic(dayInfo.dayNumber)
                                                    fullScreenType = FullScreenDueType.CUSTOM_DATE
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SELECTED DATE HOLIDAY BANNER (if selected date is a holiday)
            val selectedDateObj = remember(selectedDayNumber, selectedMonthIndex, currentYear) {
                try { LocalDate.of(currentYear, selectedMonthIndex + 1, selectedDayNumber) } catch (e: Exception) { today }
            }
            val mainSelectedHoliday = remember(selectedDateObj) { OdishaHolidays.getHoliday(selectedDateObj) }

            if (mainSelectedHoliday != null) {
                OdishaGovernmentHolidayCard(holiday = mainSelectedHoliday)
            }

            // LEGEND & GUIDE CARD
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = AccentAmber, label = "Due Premium")
                        LegendItem(color = AccentRed, label = "Overdue")
                        LegendItem(color = AccentGreen, label = "Collected")
                    }
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = RoyalBlueLight, label = "Current Day")
                        LegendItem(color = AccentPurple, label = "Government Holiday")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// FULL-SCREEN PREMIUM DUE LIST COMPOSABLE (For Requirements 3, 4, 5, 6, 7, 13)
@Composable
private fun FullScreenDueListView(
    title: String,
    badgeText: String,
    fullScreenType: FullScreenDueType,
    items: List<CalendarCustomerItem>,
    searchQuery: String,
    selectedHoliday: OdishaHoliday? = null,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSelectFilter: (FullScreenDueType) -> Unit,
    onWhatsAppClick: (CalendarCustomerItem) -> Unit,
    onCallClick: (CalendarCustomerItem) -> Unit,
    onCollectClick: (CalendarCustomerItem) -> Unit,
    onDetailsClick: (CalendarCustomerItem) -> Unit
) {
    val totalRecords = items.size
    val totalPremium = items.sumOf { it.premiumAmount }
    val totalOutstanding = items.sumOf { it.outstandingAmount }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // HEADER ITEM (Back Button + Title + Subtitle)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Calendar",
                            tint = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RoyalBlueLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // SEARCH INPUT FIELD
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text("Search customer name, mobile or policy #...", color = TextMuted, fontSize = 14.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = RoyalBlueLight,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
            }

            // CATEGORY FILTER CHIPS ROW
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipItem(
                        label = "Today",
                        isSelected = (fullScreenType == FullScreenDueType.TODAY),
                        onClick = { onSelectFilter(FullScreenDueType.TODAY) }
                    )
                    FilterChipItem(
                        label = "Tomorrow",
                        isSelected = (fullScreenType == FullScreenDueType.TOMORROW),
                        onClick = { onSelectFilter(FullScreenDueType.TOMORROW) }
                    )
                    FilterChipItem(
                        label = "This Week",
                        isSelected = (fullScreenType == FullScreenDueType.THIS_WEEK),
                        onClick = { onSelectFilter(FullScreenDueType.THIS_WEEK) }
                    )
                    FilterChipItem(
                        label = "This Month",
                        isSelected = (fullScreenType == FullScreenDueType.THIS_MONTH),
                        onClick = { onSelectFilter(FullScreenDueType.THIS_MONTH) }
                    )
                    FilterChipItem(
                        label = "Overdue",
                        isSelected = (fullScreenType == FullScreenDueType.OVERDUE),
                        onClick = { onSelectFilter(FullScreenDueType.OVERDUE) }
                    )
                }
            }

            // GOVERNMENT HOLIDAY BANNER (IF SELECTED DATE IS A HOLIDAY)
            if (selectedHoliday != null) {
                item {
                    OdishaGovernmentHolidayCard(holiday = selectedHoliday)
                }
            }

            // SUMMARY BANNER (Total Records, Total Premium Due, Total Outstanding)
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (fullScreenType == FullScreenDueType.OVERDUE) "Overdue Records" else "Total Records",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$totalRecords",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            )
                        }

                        if (fullScreenType != FullScreenDueType.OVERDUE) {
                            VerticalDivider(modifier = Modifier.height(30.dp), color = CardBorder)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Total Premium Due",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatFullAmountDouble(totalPremium),
                                    style = MaterialTheme.typography.titleMedium.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                )
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(30.dp), color = CardBorder)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Outstanding",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatFullAmountDouble(totalOutstanding),
                                style = MaterialTheme.typography.titleMedium.copy(color = if (totalOutstanding > 0) AccentRed else AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            )
                        }
                    }
                }
            }

            // FULL SCREEN VERTICALLY SCROLLABLE LIST OF ALL MATCHING RECORDS
            if (items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = "No Dues",
                            tint = RoyalBlueLight,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Premium Dues Found",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "There are no policy records matching this view or search filter.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, textAlign = TextAlign.Center)
                        )
                    }
                }
            } else {
                items(items, key = { it.id }) { item ->
                    CalendarCustomerCardFull(
                        item = item,
                        onWhatsAppClick = { onWhatsAppClick(item) },
                        onCallClick = { onCallClick(item) },
                        onCollectClick = { onCollectClick(item) },
                        onDetailsClick = { onDetailsClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OdishaGovernmentHolidayCard(
    holiday: OdishaHoliday,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AccentPurple.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, AccentPurple),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AccentPurple.copy(alpha = 0.25f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = "Government Holiday",
                        tint = AccentPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = AccentPurple,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Government Holiday",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = holiday.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = OdishaHolidays.formatHolidayDate(holiday.date),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
                if (holiday.description.isNotBlank() && holiday.description != holiday.name) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = holiday.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

// FULL CUSTOMER CARD WITH ALL REQUIRED FIELDS AND ACTIONS
@Composable
private fun CalendarCustomerCardFull(
    item: CalendarCustomerItem,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onCollectClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Customer Name, Avatar Initials, Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = RoyalBluePrimary.copy(alpha = 0.25f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.avatarInitials,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.phone.isNotBlank()) {
                            Text(
                                text = item.phone,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Status Badge
                val (badgeColor, badgeText) = when (item.status) {
                    "Today" -> AccentAmber to "Due Today"
                    "Tomorrow" -> RoyalBlueLight to "Tomorrow"
                    "Overdue" -> AccentRed to "Overdue"
                    "Paid" -> AccentGreen to "Paid"
                    "Lapsed" -> TextMuted to "Lapsed"
                    else -> RoyalBlueLight to "Upcoming"
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

            // Details Grid: Policy Number, Plan Name, Due Date, Premium Amount, Outstanding
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Policy Number", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(item.policyNumber, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Plan Name", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(item.planName, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Due Date", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(item.dueDate, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Premium Amount", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(formatFullAmountDouble(item.premiumAmount), style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }
                }

                // Outstanding Amount & Advance Carryover info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Outstanding Amount", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        if (item.advanceAdjusted > 0) {
                            Text("Advance Adjusted: ${formatFullAmountDouble(item.advanceAdjusted)}", style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen, fontSize = 10.sp))
                        }
                    }

                    Text(
                        text = formatFullAmountDouble(item.outstandingAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (item.outstandingAmount > 0) AccentRed else AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Optional Odisha Govt Holiday Badge if due date is a holiday
                val cardHoliday = item.parsedDueDate?.let { OdishaHolidays.getHoliday(it) }
                if (cardHoliday != null) {
                    Surface(
                        color = AccentPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Government Holiday: ${cardHoliday.name}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // ACTION BUTTONS: WhatsApp, Call, Collect, Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // WhatsApp
                Surface(
                    onClick = onWhatsAppClick,
                    color = AccentGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = AccentGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    }
                }

                // Call
                Surface(
                    onClick = onCallClick,
                    color = RoyalBlueLight.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBlueLight, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", style = MaterialTheme.typography.labelSmall.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    }
                }

                // Collect
                Surface(
                    onClick = onCollectClick,
                    color = RoyalBluePrimary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = "Collect", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Collect", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    }
                }

                // Details
                Surface(
                    onClick = onDetailsClick,
                    color = CardBorder,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = TextWhite, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Details", style = MaterialTheme.typography.labelSmall.copy(color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) RoyalBluePrimary else CardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) RoyalBlueLight else CardBorder)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isSelected) TextWhite else TextMuted,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CalendarSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    amount: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) accentColor.copy(alpha = 0.2f) else CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (isSelected) accentColor else CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = amount,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier = Modifier,
    dayInfo: MonthDayInfo,
    isSelected: Boolean,
    isFilterActive: Boolean,
    isFilterMatching: Boolean,
    onClick: () -> Unit
) {
    val cellAlpha = if (dayInfo.isCurrentMonth) 1f else 0.35f
    val cellBg = when {
        isSelected -> RoyalBluePrimary
        dayInfo.isToday -> RoyalBlueLight.copy(alpha = 0.25f)
        dayInfo.holiday != null -> AccentPurple.copy(alpha = 0.15f)
        else -> DarkBg.copy(alpha = 0.6f)
    }
    val cellBorderColor = when {
        isSelected -> RoyalBlueLight
        dayInfo.isToday -> RoyalBlueLight
        dayInfo.holiday != null -> AccentPurple.copy(alpha = 0.6f)
        else -> CardBorder.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onClick,
        color = cellBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, cellBorderColor),
        modifier = modifier
            .height(52.dp)
            .padding(1.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = if (dayInfo.dayNumber > 0) "${dayInfo.dayNumber}" else "",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isSelected) TextWhite else if (dayInfo.isToday) RoyalBlueLight else if (dayInfo.holiday != null) AccentPurple else TextWhite.copy(alpha = cellAlpha),
                    fontWeight = if (isSelected || dayInfo.isToday || dayInfo.holiday != null) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            )

            if (dayInfo.isCurrentMonth && (dayInfo.hasOverdue || dayInfo.hasDue || dayInfo.hasPaid || dayInfo.holiday != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dayInfo.hasOverdue) {
                        Box(modifier = Modifier.size(5.dp).background(AccentRed, CircleShape))
                    }
                    if (dayInfo.hasDue) {
                        Box(modifier = Modifier.size(5.dp).background(AccentAmber, CircleShape))
                    }
                    if (dayInfo.hasPaid) {
                        Box(modifier = Modifier.size(5.dp).background(AccentGreen, CircleShape))
                    }
                    if (dayInfo.holiday != null) {
                        Box(modifier = Modifier.size(5.dp).background(AccentPurple, CircleShape))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// Helper Utilities
private fun openWhatsApp(context: Context, phone: String, name: String, policyNo: String, amountStr: String, dueDate: String) {
    val cleanPhone = phone.replace("[^0-9]".toRegex(), "")
    val msg = "Dear $name, your LIC Policy #$policyNo premium of $amountStr is due on $dueDate. Kindly pay at your earliest convenience."
    try {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(msg)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Opening WhatsApp for $name...", Toast.LENGTH_SHORT).show()
    }
}

private fun openDialer(context: Context, phone: String) {
    val cleanPhone = phone.replace("[^0-9]".toRegex(), "")
    try {
        val uri = Uri.parse("tel:$cleanPhone")
        val intent = Intent(Intent.ACTION_DIAL, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Calling $phone...", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFullAmountDouble(amount: Double): String {
    return String.format("₹ %,.0f", amount)
}

private fun formatCompactAmountDouble(total: Double): String {
    return when {
        total >= 100000 -> String.format("₹%.1fL", total / 100000.0)
        total >= 1000 -> String.format("₹%.1fK", total / 1000.0)
        else -> String.format("₹%.0f", total)
    }
}

private fun Double.ifZero(fallback: Double): Double = if (this > 0) this else fallback

private fun getWeekIndexForDayStatic(dayNum: Int): Int {
    return when {
        dayNum in 1..7 -> 0
        dayNum in 8..14 -> 1
        dayNum in 15..21 -> 2
        dayNum in 22..28 -> 3
        else -> 4
    }
}

private fun createDemoPolicy(id: Long, num: String, name: String, amount: Double, due: String): PolicyEntity {
    return PolicyEntity(
        id = id,
        policyNumber = num,
        customerId = id + 100,
        customerName = name,
        planName = "LIC Policy",
        premiumAmount = amount,
        sumAssured = amount * 10,
        premiumMode = "Yearly",
        dueDate = due,
        maturityDate = "2035-08-10"
    )
}

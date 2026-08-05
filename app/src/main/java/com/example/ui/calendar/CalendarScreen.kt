package com.example.ui.calendar

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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

enum class CalendarViewMode {
    MONTH, WEEK
}

enum class CalendarFilterType {
    ALL, TODAY_DUE, TOMORROW, THIS_WEEK, OVERDUE
}

data class CalendarCustomer(
    val id: Int,
    val name: String,
    val policyNumber: String,
    val planName: String,
    val amount: String,
    val mode: String, // e.g., "Yearly", "Half-Yearly"
    val dueDate: String,
    val dueDayNumber: Int,
    val status: String, // "Today", "Tomorrow", "Upcoming", "Overdue", "Paid"
    val avatarInitials: String,
    val phone: String
)

data class MonthDayInfo(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val hasOverdue: Boolean,
    val hasDue: Boolean,
    val hasPaid: Boolean,
    val customerCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {}
) {
    var selectedMonthIndex by remember { mutableIntStateOf(7) } // August 2026 (0-indexed: 7)
    val monthNames = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }
    val currentYear = 2026

    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDayNumber by remember { mutableIntStateOf(4) } // Default to Today (04 Aug)
    var selectedWeekIndex by rememberSaveable { mutableIntStateOf(1) } // Week 1 (contains Aug 4 Today)
    var selectedFilter by remember { mutableStateOf(CalendarFilterType.ALL) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun getWeekIndexForDay(dayNum: Int): Int {
        return when {
            dayNum == 1 -> 0
            dayNum in 2..8 -> 1
            dayNum in 9..15 -> 2
            dayNum in 16..22 -> 3
            dayNum in 23..29 -> 4
            else -> 5
        }
    }

    // Mock Customer Data linked to specific days in August 2026
    val allCustomers = remember {
        listOf(
            CalendarCustomer(1, "Rahul Kumar", "847291038", "Jeevan Umang (Plan 945)", "₹ 12,750", "Yearly", "04 Aug 2026", 4, "Today", "RK", "+91 98765 43210"),
            CalendarCustomer(2, "Anita Das", "918237465", "Jeevan Labh (Plan 936)", "₹ 18,200", "Half-Yearly", "04 Aug 2026", 4, "Today", "AD", "+91 98123 45678"),
            CalendarCustomer(3, "Suresh Patel", "654321987", "Jeevan Lakshya (Plan 933)", "₹ 24,000", "Yearly", "02 Aug 2026", 2, "Overdue", "SP", "+91 96543 21098"),
            CalendarCustomer(4, "Rajesh Sharma", "736451928", "Endowment Plan (Plan 914)", "₹ 8,500", "Quarterly", "05 Aug 2026", 5, "Tomorrow", "RS", "+91 97654 32109"),
            CalendarCustomer(5, "Priya Verma", "543216879", "Jeevan Anand (Plan 915)", "₹ 15,300", "Yearly", "15 Aug 2026", 15, "Upcoming", "PV", "+91 95432 10987"),
            CalendarCustomer(6, "Amit Sahoo", "432156789", "Money Back (Plan 920)", "₹ 9,800", "Half-Yearly", "15 Aug 2026", 15, "Upcoming", "AS", "+91 94321 09876"),
            CalendarCustomer(7, "Vikram Malhotra", "321654987", "SIIP Plan (Plan 852)", "₹ 32,000", "Yearly", "15 Aug 2026", 15, "Paid", "VM", "+91 93210 98765"),
            CalendarCustomer(8, "Sunil Verma", "214365879", "Tech Term (Plan 854)", "₹ 14,500", "Quarterly", "20 Aug 2026", 20, "Upcoming", "SV", "+91 92109 87654"),
            CalendarCustomer(9, "Meenakshi S.", "321456987", "Cancer Cover (Plan 905)", "₹ 11,200", "Yearly", "25 Aug 2026", 25, "Upcoming", "MS", "+91 91098 76543"),
            CalendarCustomer(10, "Kiran Bedi", "123987456", "Jeevan Shanti (Plan 858)", "₹ 19,000", "Half-Yearly", "28 Aug 2026", 28, "Upcoming", "KB", "+91 90987 65432")
        )
    }

    // Filter calculations
    val todayCustomers = remember(allCustomers) {
        allCustomers.filter { it.status == "Today" || it.dueDayNumber == 4 }
    }
    val tomorrowCustomers = remember(allCustomers) {
        allCustomers.filter { it.dueDayNumber == 5 }
    }
    val thisWeekCustomers = remember(allCustomers) {
        allCustomers.filter { it.dueDayNumber in 2..10 }
    }
    val overdueCustomers = remember(allCustomers) {
        allCustomers.filter { it.status == "Overdue" }
    }

    // Currently filtered customer list
    val filteredCustomers = remember(selectedFilter, searchQuery, allCustomers) {
        val baseList = when (selectedFilter) {
            CalendarFilterType.ALL -> allCustomers
            CalendarFilterType.TODAY_DUE -> todayCustomers
            CalendarFilterType.TOMORROW -> tomorrowCustomers
            CalendarFilterType.THIS_WEEK -> thisWeekCustomers
            CalendarFilterType.OVERDUE -> overdueCustomers
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.policyNumber.contains(searchQuery, ignoreCase = true) ||
                        it.status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Customers for currently selected date
    val selectedDayCustomers = remember(selectedDayNumber, selectedFilter, allCustomers) {
        val dateCustomers = allCustomers.filter { it.dueDayNumber == selectedDayNumber }
        when (selectedFilter) {
            CalendarFilterType.ALL -> dateCustomers
            CalendarFilterType.TODAY_DUE -> dateCustomers.filter { it.status == "Today" || it.dueDayNumber == 4 }
            CalendarFilterType.TOMORROW -> dateCustomers.filter { it.dueDayNumber == 5 }
            CalendarFilterType.THIS_WEEK -> dateCustomers.filter { it.dueDayNumber in 2..10 }
            CalendarFilterType.OVERDUE -> dateCustomers.filter { it.status == "Overdue" }
        }
    }

    // Precomputed 42 cells for August 2026 grid
    val firstDayOffset = 6 // Saturday
    val daysInMonth = 31
    val previousMonthDays = 31

    val allMonthCells = remember(selectedMonthIndex, filteredCustomers) {
        val cells = mutableListOf<MonthDayInfo>()
        var currentDayCounter = 1
        var prevMonthCounter = previousMonthDays - firstDayOffset + 1
        var nextMonthCounter = 1

        for (cellIndex in 0 until 42) {
            val info = when {
                cellIndex < firstDayOffset -> {
                    MonthDayInfo(
                        dayNumber = prevMonthCounter++,
                        isCurrentMonth = false,
                        isToday = false,
                        hasOverdue = false,
                        hasDue = false,
                        hasPaid = false,
                        customerCount = 0
                    )
                }
                currentDayCounter <= daysInMonth -> {
                    val dayNum = currentDayCounter++
                    val dayCusts = filteredCustomers.filter { it.dueDayNumber == dayNum }

                    MonthDayInfo(
                        dayNumber = dayNum,
                        isCurrentMonth = true,
                        isToday = (dayNum == 4 && selectedMonthIndex == 7),
                        hasOverdue = dayCusts.any { it.status == "Overdue" },
                        hasDue = dayCusts.any { it.status == "Today" || it.status == "Upcoming" },
                        hasPaid = dayCusts.any { it.status == "Paid" },
                        customerCount = dayCusts.size
                    )
                }
                else -> {
                    MonthDayInfo(
                        dayNumber = nextMonthCounter++,
                        isCurrentMonth = false,
                        isToday = false,
                        hasOverdue = false,
                        hasDue = false,
                        hasPaid = false,
                        customerCount = 0
                    )
                }
            }
            cells.add(info)
        }
        cells
    }

    // FAB Pulse & Scale Animation
    val infiniteTransition = rememberInfiniteTransition(label = "fabPulse")
    val fabGlowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabGlowScale"
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    // Today button
                    TextButton(
                        onClick = {
                            selectedMonthIndex = 7 // Aug
                            selectedDayNumber = 4 // Today is 04 Aug
                            selectedWeekIndex = getWeekIndexForDay(4)
                            selectedFilter = CalendarFilterType.ALL
                            showBottomSheet = true
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

                    // Search icon
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Filter icon
                    IconButton(onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.ALL) CalendarFilterType.TODAY_DUE else CalendarFilterType.ALL
                    }) {
                        Icon(
                            imageVector = if (selectedFilter != CalendarFilterType.ALL) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedFilter != CalendarFilterType.ALL) RoyalBlueLight else TextWhite
                        )
                    }

                    // More menu
                    IconButton(onClick = { /* UI Menu */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Circular Royal Blue FAB with Calendar+ icon, tap animation & glow
            Box(contentAlignment = Alignment.Center) {
                // Outer Glow Ring
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(fabGlowScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    RoyalBlueGlow.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                FloatingActionButton(
                    onClick = { /* UI Only */ },
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Add Calendar Reminder",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SEARCH BAR (Visible when toggled)
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search policy, name or status...", color = TextMuted, fontSize = 14.sp)
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

            // SEGMENTED TOGGLE (MONTH / WEEK)
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

            // SUMMARY CARDS ROW (Today Due, Tomorrow, This Week, Overdue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Today Due",
                    count = "${todayCustomers.size}",
                    amount = formatCompactAmount(sumCustomerAmountsRaw(todayCustomers)),
                    accentColor = AccentAmber,
                    isSelected = (selectedFilter == CalendarFilterType.TODAY_DUE),
                    onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.TODAY_DUE) {
                            CalendarFilterType.ALL
                        } else {
                            selectedDayNumber = 4
                            selectedWeekIndex = getWeekIndexForDay(4)
                            CalendarFilterType.TODAY_DUE
                        }
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Tomorrow",
                    count = "${tomorrowCustomers.size}",
                    amount = formatCompactAmount(sumCustomerAmountsRaw(tomorrowCustomers)),
                    accentColor = RoyalBlueLight,
                    isSelected = (selectedFilter == CalendarFilterType.TOMORROW),
                    onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.TOMORROW) {
                            CalendarFilterType.ALL
                        } else {
                            selectedDayNumber = 5
                            selectedWeekIndex = getWeekIndexForDay(5)
                            CalendarFilterType.TOMORROW
                        }
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "This Week",
                    count = "${thisWeekCustomers.size}",
                    amount = formatCompactAmount(sumCustomerAmountsRaw(thisWeekCustomers)),
                    accentColor = AccentGreen,
                    isSelected = (selectedFilter == CalendarFilterType.THIS_WEEK),
                    onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.THIS_WEEK) {
                            CalendarFilterType.ALL
                        } else {
                            selectedDayNumber = 4
                            selectedWeekIndex = getWeekIndexForDay(4)
                            CalendarFilterType.THIS_WEEK
                        }
                    }
                )
                CalendarSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Overdue",
                    count = "${overdueCustomers.size}",
                    amount = formatCompactAmount(sumCustomerAmountsRaw(overdueCustomers)),
                    accentColor = AccentRed,
                    isSelected = (selectedFilter == CalendarFilterType.OVERDUE),
                    onClick = {
                        selectedFilter = if (selectedFilter == CalendarFilterType.OVERDUE) {
                            CalendarFilterType.ALL
                        } else {
                            selectedDayNumber = 2
                            selectedWeekIndex = getWeekIndexForDay(2)
                            CalendarFilterType.OVERDUE
                        }
                    }
                )
            }

            // CALENDAR GRID CONTAINER (MONTH OR WEEK VIEW WITH ANIMATIONS)
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
                                            if (selectedMonthIndex > 0) selectedMonthIndex--
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Month",
                                            tint = TextWhite
                                        )
                                    }

                                    AnimatedContent(
                                        targetState = "${monthNames[selectedMonthIndex]} $currentYear",
                                        transitionSpec = {
                                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                                    slideOutHorizontally { width -> -width } + fadeOut()
                                        },
                                        label = "monthTransition"
                                    ) { monthText ->
                                        Text(
                                            text = monthText,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (selectedMonthIndex < 11) selectedMonthIndex++
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
                                    val startDay = if (weekStartCell.dayNumber < 10) "0${weekStartCell.dayNumber}" else "${weekStartCell.dayNumber}"
                                    val endDay = if (weekEndCell.dayNumber < 10) "0${weekEndCell.dayNumber}" else "${weekEndCell.dayNumber}"
                                    val startM = if (weekStartCell.isCurrentMonth) monthNames[selectedMonthIndex] else "Jul"
                                    val endM = if (weekEndCell.isCurrentMonth) monthNames[selectedMonthIndex] else "Sep"
                                    "$startDay $startM - $endDay $endM $currentYear"
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
                                            } else if (selectedMonthIndex > 0) {
                                                selectedMonthIndex--
                                                selectedWeekIndex = 5
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Week",
                                            tint = TextWhite
                                        )
                                    }

                                    AnimatedContent(
                                        targetState = weekRangeText,
                                        transitionSpec = {
                                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                                    slideOutHorizontally { width -> -width } + fadeOut()
                                        },
                                        label = "weekTransition"
                                    ) { weekText ->
                                        Text(
                                            text = weekText,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (selectedWeekIndex < 5) {
                                                selectedWeekIndex++
                                            } else if (selectedMonthIndex < 11) {
                                                selectedMonthIndex++
                                                selectedWeekIndex = 0
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
                                                            selectedWeekIndex = getWeekIndexForDay(dayInfo.dayNumber)
                                                            showBottomSheet = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // WEEK VIEW: 1 row corresponding to selectedWeekIndex
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
                                                    selectedWeekIndex = getWeekIndexForDay(dayInfo.dayNumber)
                                                    showBottomSheet = true
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

            // FILTERED RESULTS SECTION (Visible when filter or search is active)
            AnimatedVisibility(
                visible = selectedFilter != CalendarFilterType.ALL || searchQuery.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, RoyalBlueLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filterTitle = when (selectedFilter) {
                                CalendarFilterType.TODAY_DUE -> "Filter: Today Due"
                                CalendarFilterType.TOMORROW -> "Filter: Tomorrow"
                                CalendarFilterType.THIS_WEEK -> "Filter: This Week"
                                CalendarFilterType.OVERDUE -> "Filter: Overdue"
                                else -> "Search Results"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(RoyalBlueLight, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = filterTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            Surface(
                                color = RoyalBluePrimary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                                onClick = {
                                    selectedFilter = CalendarFilterType.ALL
                                    searchQuery = ""
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Clear Filter",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = RoyalBlueLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Count & Total Amount Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filteredCustomers.size} Customers Found",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "Total: ${formatFullAmount(sumCustomerAmountsRaw(filteredCustomers))}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        if (filteredCustomers.isEmpty()) {
                            // PREMIUM EMPTY STATE ILLUSTRATION
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    color = DarkBg,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.EventAvailable,
                                            contentDescription = "No Premium Due",
                                            tint = RoyalBlueLight,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "No Premium Due",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "There are no policy renewals or collections matching this filter.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                filteredCustomers.forEach { customer ->
                                    CalendarCustomerCard(customer = customer)
                                }
                            }
                        }
                    }
                }
            }

            // LEGEND & GUIDE CARD
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = AccentAmber, label = "Due Premium")
                    LegendItem(color = AccentRed, label = "Overdue")
                    LegendItem(color = AccentGreen, label = "Collected")
                    LegendItem(color = RoyalBlueLight, label = "Current Day")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // DATE DETAILS BOTTOM SHEET
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = CardBg,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sheet Header: Selected Date, Customer Count & Total Premium Summary
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected Date",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "$selectedDayNumber ${monthNames[selectedMonthIndex]} $currentYear",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                        }

                        IconButton(
                            onClick = { showBottomSheet = false },
                            modifier = Modifier
                                .size(32.dp)
                                .background(DarkBg, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Summary Banner: Customer Count & Total Premium Amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg, RoundedCornerShape(14.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = RoyalBlueLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedDayCustomers.size} Customers",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Text(
                            text = "Total Premium: ${formatFullAmount(sumCustomerAmountsRaw(selectedDayCustomers))}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))

                if (selectedDayCustomers.isEmpty()) {
                    // EMPTY STATE IF NO PREMIUM DUE ON SELECTED DATE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = DarkBg,
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = "No Due",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No Premium Due",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "No premium is scheduled on this date.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    // CUSTOMER LIST FOR SELECTED DATE
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedDayCustomers, key = { it.id }) { customer ->
                            CalendarCustomerCard(
                                customer = customer,
                                onCallClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Calling ${customer.name} (${customer.phone})...")
                                    }
                                },
                                onWhatsAppClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Opening WhatsApp chat with ${customer.name}...")
                                    }
                                },
                                onCollectClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Initiating payment collection for ${customer.amount}...")
                                    }
                                },
                                onReceiptClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Generating receipt for Policy #${customer.policyNumber}...")
                                    }
                                },
                                onProfileClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Opening profile of ${customer.name}...")
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

// Helper functions for amount formatting
private fun sumCustomerAmountsRaw(customers: List<CalendarCustomer>): Int {
    return customers.sumOf { cust ->
        cust.amount.replace("₹", "").replace(",", "").trim().toIntOrNull() ?: 0
    }
}

private fun formatFullAmount(amount: Int): String {
    return String.format("₹ %,d", amount)
}

private fun formatCompactAmount(total: Int): String {
    return when {
        total >= 100000 -> String.format("₹%.1fL", total / 100000f)
        total >= 1000 -> String.format("₹%.1fK", total / 1000f)
        else -> "₹$total"
    }
}

@Composable
private fun CalendarSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    amount: String,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, RoyalBlueLight)
    } else {
        BorderStroke(1.dp, CardBorder)
    }

    val containerColor = if (isSelected) {
        RoyalBluePrimary.copy(alpha = 0.3f)
    } else {
        CardBg
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = borderStroke,
        modifier = modifier
            .scale(scale)
            .then(
                if (isSelected) {
                    Modifier.background(
                        brush = Brush.radialGradient(
                            colors = listOf(RoyalBlueGlow.copy(alpha = 0.35f), Color.Transparent),
                            radius = 200f
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = if (isSelected) Color.White else accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) TextWhite else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) RoyalBlueLight else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier = Modifier,
    dayInfo: MonthDayInfo,
    isSelected: Boolean,
    isFilterActive: Boolean = false,
    isFilterMatching: Boolean = false,
    onClick: () -> Unit
) {
    val isCurrent = dayInfo.isCurrentMonth
    val alpha = when {
        !isCurrent -> 0.3f
        isFilterActive && !isFilterMatching -> 0.4f
        else -> 1f
    }

    val bgModifier = when {
        isSelected -> Modifier.background(RoyalBluePrimary, RoundedCornerShape(12.dp))
        dayInfo.isToday -> Modifier.border(1.5.dp, RoyalBlueLight, RoundedCornerShape(12.dp))
        isFilterActive && isFilterMatching -> Modifier.border(1.dp, RoyalBlueLight.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
        else -> Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(bgModifier)
            .clickable(enabled = isCurrent, onClick = onClick)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${dayInfo.dayNumber}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isSelected) Color.White else TextWhite,
                    fontWeight = if (dayInfo.isToday || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            )

            if (isCurrent && (dayInfo.hasDue || dayInfo.hasOverdue || dayInfo.hasPaid)) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                }
            }

            // Customer Count Badge if multiple
            if (isCurrent && dayInfo.customerCount > 1) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = if (isSelected) Color.White.copy(alpha = 0.3f) else RoyalBluePrimary.copy(alpha = 0.4f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${dayInfo.customerCount}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCustomerCard(
    customer: CalendarCustomer,
    onCallClick: () -> Unit = {},
    onWhatsAppClick: () -> Unit = {},
    onCollectClick: () -> Unit = {},
    onReceiptClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Surface(
        color = DarkBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Avatar, Customer Name (18sp SemiBold), Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Customer Photo/Avatar
                Surface(
                    color = RoyalBluePrimary.copy(alpha = 0.25f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, RoyalBlueLight.copy(alpha = 0.6f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = customer.avatarInitials,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = RoyalBlueLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RoyalBlueLight,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                val (statusBg, statusColor) = when (customer.status) {
                    "Today" -> AccentAmber.copy(alpha = 0.15f) to AccentAmber
                    "Tomorrow" -> RoyalBlueLight.copy(alpha = 0.15f) to RoyalBlueLight
                    "Overdue" -> AccentRed.copy(alpha = 0.15f) to AccentRed
                    "Paid" -> AccentGreen.copy(alpha = 0.15f) to AccentGreen
                    else -> RoyalBlueLight.copy(alpha = 0.15f) to RoyalBlueLight
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = customer.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

            // Details Grid: Policy Number, Plan Name, Payment Mode, Due Date, Premium Amount
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Policy Number", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text("#${customer.policyNumber}", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp))
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Plan Name", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(customer.planName, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Payment Mode", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(customer.mode, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp))
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Due Date", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(customer.dueDate, style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Premium Amount", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                        Text(customer.amount, style = MaterialTheme.typography.titleMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp))
                    }
                }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

            // Quick Actions: Equal width 56dp buttons (Call, WhatsApp, Collect Payment, Receipt, Profile)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalendarActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Phone,
                    label = "Call",
                    onClick = onCallClick
                )
                CalendarActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Chat,
                    label = "WhatsApp",
                    onClick = onWhatsAppClick
                )
                CalendarActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Payment,
                    label = "Collect",
                    onClick = onCollectClick
                )
                CalendarActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ReceiptLong,
                    label = "Receipt",
                    onClick = onReceiptClick
                )
                CalendarActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person,
                    label = "Profile",
                    onClick = onProfileClick
                )
            }
        }
    }
}

@Composable
private fun CalendarActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = RoyalBlueLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
    }
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
    CalendarScreen()
}

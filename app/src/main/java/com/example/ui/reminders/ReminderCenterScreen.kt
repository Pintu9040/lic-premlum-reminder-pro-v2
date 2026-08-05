package com.example.ui.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Mock Data Model for Premium Reminders
data class PremiumReminderItem(
    val id: Long,
    val customerName: String,
    val customerMobile: String,
    val customerAddress: String,
    val policyNumber: String,
    val planName: String,
    val premiumAmount: Double,
    val premiumMode: String,
    val dueDate: String,
    val nextDueDate: String,
    val sumAssured: String,
    val dueCategory: ReminderCategory,
    val daysStatus: String,
    val avatarInitials: String,
    val isCompleted: Boolean = false,
    val outstandingAmount: Double = premiumAmount,
    val lastReminderSent: String = "Yesterday, 4:30 PM"
)

enum class ReminderCategory(val displayName: String) {
    ALL("All"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("This Week"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

enum class BroadcastStep {
    NONE,
    SETUP_DIALOG,     // Step 1: Dialog
    SENDING_PROGRESS, // Step 2: Fullscreen progress
    SUCCESS_REPORT    // Step 3: Success screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCenterScreen(
    onBack: () -> Unit = {},
    onCollectPremium: (String, String) -> Unit = { _, _ -> },
    onViewPolicyDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // States
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ReminderCategory.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedReminderForSheet by remember { mutableStateOf<PremiumReminderItem?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showQuickReminderDialog by remember { mutableStateOf(false) }

    // Broadcast Workflow Step State
    var broadcastStep by remember { mutableStateOf(BroadcastStep.NONE) }
    var isTodayDueChecked by remember { mutableStateOf(true) }
    var isOverdueChecked by remember { mutableStateOf(true) }
    var isIncludeWhatsAppChecked by remember { mutableStateOf(true) }

    var progressCurrentIndex by remember { mutableStateOf(1) }
    val totalCustomers = 12
    val sampleCustomers = remember {
        listOf(
            "Priya Das", "Amit Sahoo", "Rahul Kumar", "Rajesh Sharma",
            "Anita Verma", "Suresh Patel", "Priya Mukherji", "Vikramjit Singh",
            "Meenakshi S.", "Sunil Verma", "Kavita Rao", "Deepak Gupta"
        )
    }

    LaunchedEffect(broadcastStep) {
        if (broadcastStep == BroadcastStep.SENDING_PROGRESS) {
            progressCurrentIndex = 1
            for (i in 1..totalCustomers) {
                progressCurrentIndex = i
                delay(350)
            }
            delay(300)
            broadcastStep = BroadcastStep.SUCCESS_REPORT
        }
    }

    // Mock Reminders Data
    val allReminders = remember {
        mutableStateListOf(
            PremiumReminderItem(
                id = 1,
                customerName = "Rajesh Kumar Sharma",
                customerMobile = "+91 98765 43210",
                customerAddress = "Plot 42, Sector 14, Gurgaon, HR",
                policyNumber = "867452901",
                planName = "Jeevan Umang (Plan 945)",
                premiumAmount = 25050.00,
                premiumMode = "Yearly",
                dueDate = "04 Aug 2026",
                nextDueDate = "04 Aug 2027",
                sumAssured = "₹ 10,000,000",
                dueCategory = ReminderCategory.TODAY,
                daysStatus = "Due Today",
                avatarInitials = "RS"
            ),
            PremiumReminderItem(
                id = 2,
                customerName = "Anita Verma",
                customerMobile = "+91 98123 76543",
                customerAddress = "Flat 302, Cyber Heights, Noida, UP",
                policyNumber = "729481023",
                planName = "Jeevan Anand (Plan 915)",
                premiumAmount = 18400.00,
                premiumMode = "Half-Yearly",
                dueDate = "04 Aug 2026",
                nextDueDate = "04 Feb 2027",
                sumAssured = "₹ 500,000",
                dueCategory = ReminderCategory.TODAY,
                daysStatus = "Due Today",
                avatarInitials = "AV"
            ),
            PremiumReminderItem(
                id = 3,
                customerName = "Suresh Chand Patel",
                customerMobile = "+91 97654 12389",
                customerAddress = "House No 18, MG Road, Jaipur, RJ",
                policyNumber = "658392014",
                planName = "Tech Term Plan (Plan 854)",
                premiumAmount = 32100.00,
                premiumMode = "Yearly",
                dueDate = "05 Aug 2026",
                nextDueDate = "05 Aug 2027",
                sumAssured = "₹ 15,000,000",
                dueCategory = ReminderCategory.TOMORROW,
                daysStatus = "Due Tomorrow",
                avatarInitials = "SP"
            ),
            PremiumReminderItem(
                id = 4,
                customerName = "Priya Mukherji",
                customerMobile = "+91 99001 88223",
                customerAddress = "Salt Lake Sector 5, Kolkata, WB",
                policyNumber = "918273645",
                planName = "SIIP Endowment (Plan 852)",
                premiumAmount = 12500.00,
                premiumMode = "Quarterly",
                dueDate = "01 Aug 2026",
                nextDueDate = "01 Nov 2026",
                sumAssured = "₹ 750,000",
                dueCategory = ReminderCategory.OVERDUE,
                daysStatus = "3 Days Overdue",
                avatarInitials = "PM"
            ),
            PremiumReminderItem(
                id = 5,
                customerName = "Vikramjit Singh",
                customerMobile = "+91 98888 11223",
                customerAddress = "Model Town, Ludhiana, PB",
                policyNumber = "543216789",
                planName = "Jeevan Labh (Plan 936)",
                premiumAmount = 45000.00,
                premiumMode = "Yearly",
                dueDate = "08 Aug 2026",
                nextDueDate = "08 Aug 2027",
                sumAssured = "₹ 2,500,000",
                dueCategory = ReminderCategory.THIS_WEEK,
                daysStatus = "Due in 4 Days",
                avatarInitials = "VS"
            ),
            PremiumReminderItem(
                id = 6,
                customerName = "Meenakshi Sundaram",
                customerMobile = "+91 94440 99887",
                customerAddress = "Anna Nagar, Chennai, TN",
                policyNumber = "314253647",
                planName = "Jeevan Umang (Plan 945)",
                premiumAmount = 21000.00,
                premiumMode = "Half-Yearly",
                dueDate = "30 Jul 2026",
                nextDueDate = "30 Jan 2027",
                sumAssured = "₹ 1,000,000",
                dueCategory = ReminderCategory.COMPLETED,
                daysStatus = "Paid On Time",
                avatarInitials = "MS",
                isCompleted = true
            )
        )
    }

    // Filtered items based on search and category
    val filteredReminders = remember(searchQuery, selectedCategory, allReminders) {
        allReminders.filter { item ->
            val matchesCategory = when (selectedCategory) {
                ReminderCategory.ALL -> true
                ReminderCategory.COMPLETED -> item.isCompleted
                else -> !item.isCompleted && item.dueCategory == selectedCategory
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.customerName.contains(searchQuery, ignoreCase = true) ||
                    item.policyNumber.contains(searchQuery, ignoreCase = true) ||
                    item.customerMobile.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    // Category Counts & Total Amounts
    val dueTodayCount = allReminders.count { it.dueCategory == ReminderCategory.TODAY && !it.isCompleted }
    val dueTomorrowCount = allReminders.count { it.dueCategory == ReminderCategory.TOMORROW && !it.isCompleted }
    val dueThisWeekCount = allReminders.count { it.dueCategory == ReminderCategory.THIS_WEEK && !it.isCompleted }
    val overdueCount = allReminders.count { it.dueCategory == ReminderCategory.OVERDUE && !it.isCompleted }

    val dueTodayAmount = allReminders.filter { it.dueCategory == ReminderCategory.TODAY && !it.isCompleted }.sumOf { it.premiumAmount }
    val dueTomorrowAmount = allReminders.filter { it.dueCategory == ReminderCategory.TOMORROW && !it.isCompleted }.sumOf { it.premiumAmount }
    val dueThisWeekAmount = allReminders.filter { it.dueCategory == ReminderCategory.THIS_WEEK && !it.isCompleted }.sumOf { it.premiumAmount }
    val overdueAmount = allReminders.filter { it.dueCategory == ReminderCategory.OVERDUE && !it.isCompleted }.sumOf { it.premiumAmount }

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
                    IconButton(
                        onClick = {
                            isLoading = !isLoading
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (isLoading) "Simulating loading skeleton..." else "Refreshed reminder queue"
                                )
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
                                    scope.launch { snackbarHostState.showSnackbar("Reminder CSV downloaded to storage") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Notification Settings", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch { snackbarHostState.showSnackbar("Opening Reminder Settings...") }
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
        floatingActionButton = {
            // Infinite transition for floating & pulse animations
            val infiniteTransition = rememberInfiniteTransition(label = "fab_infinite")

            // 1. Floating Animation (slow up & down, 4–6dp)
            val dy by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fab_float_dy"
            )

            // 2. Soft Pulse / Glow every 2 seconds
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fab_pulse_scale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fab_pulse_alpha"
            )

            // 3. Screen Launch Scale & Fade-in
            var fabLaunched by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                fabLaunched = true
            }
            val launchScale by animateFloatAsState(
                targetValue = if (fabLaunched) 1.0f else 0.95f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "fab_launch_scale"
            )
            val launchAlpha by animateFloatAsState(
                targetValue = if (fabLaunched) 1.0f else 0.0f,
                animationSpec = tween(durationMillis = 350),
                label = "fab_launch_alpha"
            )

            // 4. Press Scale Animation (scale to 0.92 then back)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "fab_press_scale"
            )

            val totalScale = launchScale * pressScale

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = dy.dp.toPx()
                        scaleX = totalScale
                        scaleY = totalScale
                        alpha = launchAlpha
                    }
            ) {
                // Outer Pulse/Glow Halo
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = pulseAlpha
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(RoyalBlueGlow.copy(alpha = 0.7f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                // Circular FAB (64dp) with Royal Blue Gradient & Elevation Shadow
                Surface(
                    onClick = {
                        showQuickReminderDialog = true
                        broadcastStep = BroadcastStep.SETUP_DIALOG
                    },
                    interactionSource = interactionSource,
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            spotColor = RoyalBlueGlow,
                            ambientColor = RoyalBlueGlow
                        )
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(RoyalBlueGlow, RoyalBluePrimary)
                            ),
                            shape = CircleShape
                        )
                        .testTag("add_reminder_fab")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // White Notifications Bell Icon
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Quick Reminder",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )

                        // Small "+" badge on top-right of the FAB
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(16.dp)
                                .background(AccentAmber, CircleShape)
                                .border(1.5.dp, DarkBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Pull To Refresh Container
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    delay(1200)
                    isRefreshing = false
                    snackbarHostState.showSnackbar("Reminders updated successfully")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ==================== DASHBOARD SUMMARY CARDS ====================
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

                    // 2x2 Equal Grid Dashboard Summary (Equal Height & Vertically Centered)
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

                Spacer(modifier = Modifier.height(12.dp))

                // ==================== FULL-WIDTH 56DP OUTLINED SEARCH BAR ====================
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
                                            contentDescription = "Clear",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { /* Filter action */ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter Icon",
                                        tint = if (selectedCategory != ReminderCategory.ALL) AccentAmber else TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
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

                Spacer(modifier = Modifier.height(12.dp))

                // ==================== CATEGORY FILTER CHIPS ====================
                val categoriesToDisplay = listOf(
                    ReminderCategory.ALL,
                    ReminderCategory.TODAY,
                    ReminderCategory.TOMORROW,
                    ReminderCategory.THIS_WEEK,
                    ReminderCategory.OVERDUE
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
                        val animatedElevation by animateDpAsState(
                            targetValue = if (isSelected) 4.dp else 0.dp,
                            animationSpec = tween(durationMillis = 200),
                            label = "chipElevation"
                        )

                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(20.dp),
                            color = animatedBgColor,
                            border = BorderStroke(1.dp, animatedBorderColor),
                            shadowElevation = animatedElevation,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("filter_chip_${category.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(RoyalBluePrimary, RoyalBlueGlow)
                                                )
                                            )
                                        } else Modifier
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

                Spacer(modifier = Modifier.height(8.dp))

                // ==================== REMINDER LIST / SHIMMER / EMPTY STATE ====================
                if (isLoading) {
                    // Loading Skeleton State
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(3) {
                            LoadingSkeletonCard()
                        }
                    }
                } else if (filteredReminders.isEmpty()) {
                    // Premium Illustration Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Premium Illustration: Layered Bell, Checkmark & Calendar
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

                                // Mini Checkmark Badge
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

                                // Mini Calendar Badge
                                Surface(
                                    color = AccentAmber,
                                    shape = CircleShape,
                                    border = BorderStroke(2.dp, DarkBg),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.BottomStart)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarToday,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "No Premium Reminders",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "All customers are up to date.",
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
                                Text("Reset Filters", color = TextWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    // Reminder Items List (Bottom padding 110.dp avoids FAB overlap)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredReminders, key = { it.id }) { reminder ->
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
                                    onCollectPremium(reminder.policyNumber, reminder.customerName)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = RoyalBluePrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = reminder.avatarInitials,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = reminder.customerName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = reminder.customerMobile,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }

                    StatusBadge(
                        category = reminder.dueCategory,
                        statusText = reminder.daysStatus,
                        isCompleted = reminder.isCompleted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Policy Details Grid
                Text(
                    text = "POLICY SPECIFICATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, shape = RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailSheetRow("Policy Number", reminder.policyNumber)
                    DetailSheetRow("Plan Name", reminder.planName)
                    DetailSheetRow("Premium Mode", reminder.premiumMode)
                    DetailSheetRow("Due Date", reminder.dueDate)
                    DetailSheetRow("Next Due Date", reminder.nextDueDate, isHighlight = true)
                    DetailSheetRow("Sum Assured", reminder.sumAssured)
                    DetailSheetRow("Premium Amount", "₹ ${String.format("%,.2f", reminder.premiumAmount)}", isHighlight = true)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons inside BottomSheet
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            selectedReminderForSheet = null
                            sendWhatsAppReminder(context, reminder.customerMobile, reminder.customerName, reminder.premiumAmount.toString(), reminder.policyNumber)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send WhatsApp Reminder", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedReminderForSheet = null
                                callCustomer(context, reminder.customerMobile)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RoyalBlueLight)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = RoyalBlueLight)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", color = RoyalBlueLight)
                        }

                        Button(
                            onClick = {
                                selectedReminderForSheet = null
                                onCollectPremium(reminder.policyNumber, reminder.customerName)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Collect", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ==================== REMINDER BROADCAST WORKFLOW (STEP 1, 2, 3) ====================
    if (broadcastStep == BroadcastStep.SETUP_DIALOG || (showQuickReminderDialog && broadcastStep == BroadcastStep.NONE)) {
        Dialog(
            onDismissRequest = {
                showQuickReminderDialog = false
                broadcastStep = BroadcastStep.NONE
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bell Illustration & Title
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(RoyalBlueGlow.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Surface(
                            color = RoyalBluePrimary.copy(alpha = 0.2f),
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Reminder Bell",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Broadcast Premium Reminder",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Summary Card
                    Surface(
                        color = DarkBg,
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
                            Text(
                                text = "SUMMARY CARD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(AccentAmber, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Today Due Customers", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 13.sp))
                                }
                                Text("8", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(AccentRed, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Overdue Customers", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 13.sp))
                                }
                                Text("4", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold))
                            }

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Selected Customers", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 13.sp))
                                Text("12", style = MaterialTheme.typography.bodyMedium.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Premium Amount", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 13.sp))
                                Text("₹ 1,53,050.00", style = MaterialTheme.typography.bodyMedium.copy(color = AccentGreen, fontWeight = FontWeight.ExtraBold))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message Preview
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "MESSAGE PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Dear {CustomerName},\n\nYour LIC premium of ₹{PremiumAmount} for Policy No. {PolicyNumber} is due on {DueDate}.\n\nPlease pay your premium before the due date.\n\nThank you.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextWhite,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "OPTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isTodayDueChecked = !isTodayDueChecked },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isTodayDueChecked,
                                onCheckedChange = { isTodayDueChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = RoyalBluePrimary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Today Due", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontSize = 14.sp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isOverdueChecked = !isOverdueChecked },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isOverdueChecked,
                                onCheckedChange = { isOverdueChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = RoyalBluePrimary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Overdue", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontSize = 14.sp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isIncludeWhatsAppChecked = !isIncludeWhatsAppChecked },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isIncludeWhatsAppChecked,
                                onCheckedChange = { isIncludeWhatsAppChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = RoyalBluePrimary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Include WhatsApp", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontSize = 14.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Equal Buttons Row (56dp height, weight 1f each, 16dp spacing)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        Button(
                            onClick = {
                                showQuickReminderDialog = false
                                broadcastStep = BroadcastStep.NONE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }

                        // Send Reminders Button
                        Button(
                            onClick = {
                                showQuickReminderDialog = false
                                broadcastStep = BroadcastStep.SENDING_PROGRESS
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(RoyalBluePrimary, RoyalBlueGlow)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Reminders",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Send Reminders",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== STEP 2: SENDING PROGRESS SCREEN ====================
    if (broadcastStep == BroadcastStep.SENDING_PROGRESS) {
        Dialog(
            onDismissRequest = { broadcastStep = BroadcastStep.NONE },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Title
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = "Sending Premium Reminders...",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Broadcasting collection alerts to selected customers",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    // Center Content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large Circular Progress Indicator
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(130.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progressCurrentIndex / 12f },
                                modifier = Modifier.fillMaxSize(),
                                color = RoyalBlueLight,
                                strokeWidth = 10.dp,
                                trackColor = CardBorder
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$progressCurrentIndex / $totalCustomers",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 26.sp
                                    )
                                )
                                Text(
                                    text = "Progress",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Linear Progress Bar
                        Column(
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            LinearProgressIndicator(
                                progress = { progressCurrentIndex / 12f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = RoyalBlueLight,
                                trackColor = CardBorder
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Progress: ${(progressCurrentIndex * 100 / totalCustomers)}%",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                )
                                Text(
                                    text = "${totalCustomers - progressCurrentIndex} customers remaining",
                                    style = MaterialTheme.typography.bodySmall.copy(color = RoyalBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Current Customer
                        val activeName = sampleCustomers.getOrElse(progressCurrentIndex - 1) { "Rahul Kumar" }
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = RoyalBluePrimary.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = RoyalBlueLight,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Sending to:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                                    )
                                    Text(
                                        text = activeName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Status List
                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(totalCustomers) { idx ->
                                    val itemNum = idx + 1
                                    val name = sampleCustomers.getOrElse(idx) { "Customer #$itemNum" }
                                    val (statusIcon, statusColor, statusLabel) = when {
                                        itemNum < progressCurrentIndex -> Triple(Icons.Default.Check, AccentGreen, "Sent")
                                        itemNum == progressCurrentIndex -> Triple(Icons.Default.HourglassTop, AccentAmber, "Sending...")
                                        else -> Triple(Icons.Outlined.Schedule, TextMuted, "Pending")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = if (itemNum <= progressCurrentIndex) TextWhite else TextMuted,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (itemNum == progressCurrentIndex) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        }
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = statusColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Button: Cancel Sending
                    Button(
                        onClick = { broadcastStep = BroadcastStep.NONE },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cancel Sending",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== STEP 3: SUCCESS SCREEN ====================
    if (broadcastStep == BroadcastStep.SUCCESS_REPORT) {
        Dialog(
            onDismissRequest = { broadcastStep = BroadcastStep.NONE },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Center Content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Large Success Animation Icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(110.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(AccentGreen.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        ) {
                            Surface(
                                color = AccentGreen.copy(alpha = 0.15f),
                                shape = CircleShape,
                                border = BorderStroke(2.dp, AccentGreen.copy(alpha = 0.6f)),
                                modifier = Modifier.size(88.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Broadcast Completed",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "All reminder notifications were delivered successfully.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Summary Card
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "BROADCAST SUMMARY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Customers", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                    Text("12", style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold))
                                }

                                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Successfully Sent", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                    Text("12", style = MaterialTheme.typography.bodyMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Failed", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                    Text("0", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Skipped", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                    Text("0", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                                }

                                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Time Taken", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                    Text("18 Seconds", style = MaterialTheme.typography.bodyMedium.copy(color = RoyalBlueLight, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    // Buttons: View Report & Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Detailed report saved.")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = "View Report",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        Button(
                            onClick = { broadcastStep = BroadcastStep.NONE },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(RoyalBluePrimary, RoyalBlueGlow)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== DASHBOARD SUMMARY METRIC CARD ====================
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
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "summaryElevation"
    )

    Surface(
        onClick = onClick,
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) color else color.copy(alpha = 0.35f)
        ),
        shadowElevation = animatedElevation,
        modifier = modifier
            .height(116.dp)
            .shadow(
                elevation = animatedElevation,
                shape = RoundedCornerShape(20.dp),
                spotColor = color,
                ambientColor = color.copy(alpha = 0.4f)
            )
            .testTag("summary_card_${title.lowercase().replace(" ", "_")}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = if (isSelected) 0.16f else 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(26.dp)
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

                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isSelected) color else TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }

                Column {
                    Text(
                        text = countNumber.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 30.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${String.format("%,.0f", totalAmount)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

// ==================== STATUS BADGES ====================
@Composable
private fun StatusBadge(
    category: ReminderCategory,
    statusText: String,
    isCompleted: Boolean
) {
    val (badgeBg, badgeText) = when {
        isCompleted -> Pair(AccentBlue.copy(alpha = 0.2f), AccentBlue)
        category == ReminderCategory.OVERDUE -> Pair(AccentRed.copy(alpha = 0.2f), AccentRed)
        category == ReminderCategory.TODAY -> Pair(AccentAmber.copy(alpha = 0.2f), AccentAmber)
        else -> Pair(AccentGreen.copy(alpha = 0.2f), AccentGreen) // Upcoming (Tomorrow & This Week)
    }

    Surface(
        color = badgeBg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = badgeText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

// ==================== REMINDER LIST CARD (20DP ROUNDED) ====================
@Composable
private fun ReminderListCard(
    item: PremiumReminderItem,
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
                    // Customer Avatar
                    Surface(
                        color = RoyalBluePrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.avatarInitials,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
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
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Material 3 Status Badge
                StatusBadge(
                    category = item.dueCategory,
                    statusText = item.daysStatus,
                    isCompleted = item.isCompleted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                            fontSize = 18.sp
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

            // BOTTOM SECTION: Due Date, Outstanding Amount, Last Reminder Sent
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
                                text = item.nextDueDate,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = RoyalBlueLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Outstanding",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.5.sp)
                        )
                        Text(
                            text = "₹${String.format("%,.0f", item.outstandingAmount)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AccentAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Last Reminder",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.5.sp)
                        )
                        Text(
                            text = item.lastReminderSent,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // QUICK ACTION BAR: 4 Equal-Width 56dp Buttons (Icon above Text, Material Ripple)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. WhatsApp (Filled Tonal Green)
                FilledTonalButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_whatsapp_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF166534).copy(alpha = 0.35f),
                        contentColor = Color(0xFF4ADE80)
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
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

                // 2. Call (Filled Tonal Blue)
                FilledTonalButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_call_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RoyalBluePrimary.copy(alpha = 0.25f),
                        contentColor = RoyalBlueLight
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
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

                // 3. Collect (Filled Tonal Amber)
                FilledTonalButton(
                    onClick = onCollectClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_collect_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentAmber.copy(alpha = 0.25f),
                        contentColor = AccentAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
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

                // 4. Details (Filled Tonal Slate)
                FilledTonalButton(
                    onClick = onDetailsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_details_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CardBorder.copy(alpha = 0.6f),
                        contentColor = TextWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
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

// ==================== HELPER COMPOSABLES ====================
@Composable
private fun DetailSheetRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isHighlight) RoyalBlueLight else TextWhite,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CardBorder)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardBorder)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardBorder)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardBorder)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBorder)
                    )
                }
            }
        }
    }
}

// Intent Launcher Helpers
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

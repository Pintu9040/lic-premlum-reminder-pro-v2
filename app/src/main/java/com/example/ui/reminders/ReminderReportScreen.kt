package com.example.ui.reminders

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Royal Blue Dark Theme Colors
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

data class CustomerReportItem(
    val id: Int,
    val name: String,
    val policyNumber: String,
    val mobileNumber: String,
    val reminderType: String, // "WhatsApp" or "SMS"
    val sentTime: String,
    val status: String, // "Sent" or "Failed"
    val avatarInitials: String,
    val messagePreview: String,
    val deliveryTime: String,
    val policyDueDate: String,
    val premiumAmount: String,
    val timelineStarted: String,
    val timelineDelivered: String,
    val timelineCompleted: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderReportScreen(
    onBackClick: () -> Unit = {},
    onExportClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Sent", "Failed"
    var expandedCardId by remember { mutableStateOf<Int?>(null) }
    var snackbarHostState = remember { SnackbarHostState() }

    val reportItems = remember {
        listOf(
            CustomerReportItem(
                id = 1,
                name = "Rahul Kumar",
                policyNumber = "847291038",
                mobileNumber = "+91 98765 43210",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:45 PM",
                status = "Sent",
                avatarInitials = "RK",
                messagePreview = "Dear Rahul Kumar, your LIC premium of ₹12,750 for Policy No. 847291038 is due on 15 Aug 2026. Please pay before the due date.",
                deliveryTime = "03:45:12 PM",
                policyDueDate = "15 Aug 2026",
                premiumAmount = "₹ 12,750.00",
                timelineStarted = "03:45:00 PM",
                timelineDelivered = "03:45:08 PM",
                timelineCompleted = "03:45:12 PM"
            ),
            CustomerReportItem(
                id = 2,
                name = "Anita Das",
                policyNumber = "918237465",
                mobileNumber = "+91 98123 45678",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:45 PM",
                status = "Sent",
                avatarInitials = "AD",
                messagePreview = "Dear Anita Das, your LIC premium of ₹18,200 for Policy No. 918237465 is due on 10 Aug 2026. Please pay before the due date.",
                deliveryTime = "03:45:15 PM",
                policyDueDate = "10 Aug 2026",
                premiumAmount = "₹ 18,200.00",
                timelineStarted = "03:45:12 PM",
                timelineDelivered = "03:45:14 PM",
                timelineCompleted = "03:45:15 PM"
            ),
            CustomerReportItem(
                id = 3,
                name = "Rajesh Sharma",
                policyNumber = "736451928",
                mobileNumber = "+91 97654 32109",
                reminderType = "SMS",
                sentTime = "03 Aug 2026, 03:46 PM",
                status = "Sent",
                avatarInitials = "RS",
                messagePreview = "Dear Rajesh Sharma, your LIC premium of ₹8,500 for Policy No. 736451928 is due on 18 Aug 2026.",
                deliveryTime = "03:46:02 PM",
                policyDueDate = "18 Aug 2026",
                premiumAmount = "₹ 8,500.00",
                timelineStarted = "03:45:58 PM",
                timelineDelivered = "03:46:01 PM",
                timelineCompleted = "03:46:02 PM"
            ),
            CustomerReportItem(
                id = 4,
                name = "Suresh Patel",
                policyNumber = "654321987",
                mobileNumber = "+91 96543 21098",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:46 PM",
                status = "Failed",
                avatarInitials = "SP",
                messagePreview = "Dear Suresh Patel, your LIC premium of ₹24,000 for Policy No. 654321987 is due on 05 Aug 2026.",
                deliveryTime = "Failed - Network Timeout",
                policyDueDate = "05 Aug 2026",
                premiumAmount = "₹ 24,000.00",
                timelineStarted = "03:46:05 PM",
                timelineDelivered = "Failed (Retry available)",
                timelineCompleted = "Failed"
            ),
            CustomerReportItem(
                id = 5,
                name = "Priya Verma",
                policyNumber = "543216879",
                mobileNumber = "+91 95432 10987",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:46 PM",
                status = "Sent",
                avatarInitials = "PV",
                messagePreview = "Dear Priya Verma, your LIC premium of ₹15,300 for Policy No. 543216879 is due on 22 Aug 2026.",
                deliveryTime = "03:46:18 PM",
                policyDueDate = "22 Aug 2026",
                premiumAmount = "₹ 15,300.00",
                timelineStarted = "03:46:12 PM",
                timelineDelivered = "03:46:16 PM",
                timelineCompleted = "03:46:18 PM"
            ),
            CustomerReportItem(
                id = 6,
                name = "Amit Sahoo",
                policyNumber = "432156789",
                mobileNumber = "+91 94321 09876",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:46 PM",
                status = "Sent",
                avatarInitials = "AS",
                messagePreview = "Dear Amit Sahoo, your LIC premium of ₹9,800 for Policy No. 432156789 is due on 12 Aug 2026.",
                deliveryTime = "03:46:25 PM",
                policyDueDate = "12 Aug 2026",
                premiumAmount = "₹ 9,800.00",
                timelineStarted = "03:46:20 PM",
                timelineDelivered = "03:46:23 PM",
                timelineCompleted = "03:46:25 PM"
            ),
            CustomerReportItem(
                id = 7,
                name = "Meenakshi S.",
                policyNumber = "321456987",
                mobileNumber = "+91 93210 98765",
                reminderType = "SMS",
                sentTime = "03 Aug 2026, 03:47 PM",
                status = "Sent",
                avatarInitials = "MS",
                messagePreview = "Dear Meenakshi S., your LIC premium of ₹11,200 for Policy No. 321456987 is due on 25 Aug 2026.",
                deliveryTime = "03:47:01 PM",
                policyDueDate = "25 Aug 2026",
                premiumAmount = "₹ 11,200.00",
                timelineStarted = "03:46:55 PM",
                timelineDelivered = "03:47:00 PM",
                timelineCompleted = "03:47:01 PM"
            ),
            CustomerReportItem(
                id = 8,
                name = "Sunil Verma",
                policyNumber = "214365879",
                mobileNumber = "+91 92109 87654",
                reminderType = "WhatsApp",
                sentTime = "03 Aug 2026, 03:47 PM",
                status = "Sent",
                avatarInitials = "SV",
                messagePreview = "Dear Sunil Verma, your LIC premium of ₹14,500 for Policy No. 214365879 is due on 30 Aug 2026.",
                deliveryTime = "03:47:10 PM",
                policyDueDate = "30 Aug 2026",
                premiumAmount = "₹ 14,500.00",
                timelineStarted = "03:47:05 PM",
                timelineDelivered = "03:47:08 PM",
                timelineCompleted = "03:47:10 PM"
            )
        )
    }

    // Filter and Search logic
    val filteredList = remember(searchQuery, selectedFilter, reportItems) {
        reportItems.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "Sent" -> item.status == "Sent"
                "Failed" -> item.status == "Failed"
                else -> true
            }
            val matchesQuery = searchQuery.isEmpty() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.policyNumber.contains(searchQuery) ||
                    item.mobileNumber.contains(searchQuery)

            matchesFilter && matchesQuery
        }
    }

    val totalCount = reportItems.size
    val sentCount = reportItems.count { it.status == "Sent" }
    val failedCount = reportItems.count { it.status == "Failed" }
    val successRate = if (totalCount > 0) (sentCount * 100 / totalCount) else 100

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextWhite,
                    navigationIconContentColor = TextWhite
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
                    Text(
                        text = "Broadcast Report",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 19.sp
                        )
                    )
                }
            )
        },
        bottomBar = {
            // Bottom Sticky Action: Export Report (Royal Blue Filled, 56dp Height, Download Icon)
            Surface(
                color = DarkBg,
                border = BorderStroke(1.dp, CardBorder),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = onExportClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(RoyalBluePrimary, RoyalBlueGlow)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export Report",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Export Report",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            // SUMMARY STAT CARDS (Total, Sent, Failed, Success Rate)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Total",
                        value = "$totalCount",
                        valueColor = TextWhite
                    )
                    ReportStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Sent",
                        value = "$sentCount",
                        valueColor = AccentGreen
                    )
                    ReportStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Failed",
                        value = "$failedCount",
                        valueColor = if (failedCount > 0) AccentRed else TextMuted
                    )
                    ReportStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Rate",
                        value = "$successRate%",
                        valueColor = RoyalBlueLight
                    )
                }
            }

            // SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search customer, policy, or phone...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 14.sp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
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

            // FILTER CHIPS (All, Sent, Failed)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All", "Sent", "Failed").forEach { filterOption ->
                        val isSelected = selectedFilter == filterOption
                        val chipBg = if (isSelected) RoyalBluePrimary else CardBg
                        val chipBorder = if (isSelected) RoyalBlueLight else CardBorder
                        val chipTextColor = if (isSelected) Color.White else TextMuted

                        Surface(
                            color = chipBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, chipBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = filterOption }
                        ) {
                            Text(
                                text = filterOption,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = chipTextColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // CUSTOMER REPORT CARDS LIST
            items(filteredList, key = { it.id }) { item ->
                val isExpanded = expandedCardId == item.id

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
                        // Top Header: Avatar, Name, Policy, Status Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                color = RoyalBluePrimary.copy(alpha = 0.25f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.avatarInitials,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Name & Policy
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
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
                                    text = "Policy: ${item.policyNumber}  •  ${item.mobileNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Status Badge (Sent = Green, Failed = Red)
                            val statusBg = if (item.status == "Sent") AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f)
                            val statusColor = if (item.status == "Sent") AccentGreen else AccentRed

                            Surface(
                                color = statusBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = item.status,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary Info Row: Reminder Type & Sent Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = DarkBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.reminderType == "WhatsApp") Icons.Default.Chat else Icons.Default.Sms,
                                        contentDescription = null,
                                        tint = RoyalBlueLight,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = item.reminderType,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextWhite,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = item.sentTime,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Card Action Buttons: View Details & Resend (UI only)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    expandedCardId = if (isExpanded) null else item.id
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                            ) {
                                Text(
                                    text = if (isExpanded) "Hide Details" else "View Details",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Button(
                                onClick = { /* UI Only */ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalBluePrimary.copy(alpha = 0.2f),
                                    contentColor = RoyalBlueLight
                                ),
                                border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resend",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = RoyalBlueLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }

                        // EXPANDABLE DETAILS SECTION
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))

                                // Message Preview
                                Column {
                                    Text(
                                        text = "MESSAGE PREVIEW",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = DarkBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Text(
                                            text = item.messagePreview,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextWhite,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp
                                            ),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                // Key Value Grid (Delivery Time, Due Date, Premium Amount)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DetailMetaBlock("Delivery Time", item.deliveryTime)
                                    DetailMetaBlock("Policy Due Date", item.policyDueDate)
                                    DetailMetaBlock("Premium Amount", item.premiumAmount, isHighlight = true)
                                }

                                // Timeline
                                Column {
                                    Text(
                                        text = "DELIVERY TIMELINE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        color = DarkBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            TimelineRow("Sending Started", item.timelineStarted, AccentAmber)
                                            TimelineRow("Delivered", item.timelineDelivered, RoyalBlueLight)
                                            TimelineRow("Completed", item.timelineCompleted, if (item.status == "Sent") AccentGreen else AccentRed)
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

@Composable
private fun ReportStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueColor: Color
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = valueColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            )
        }
    }
}

@Composable
private fun DetailMetaBlock(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 10.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isHighlight) AccentGreen else TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
        )
    }
}

@Composable
private fun TimelineRow(
    stage: String,
    time: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stage,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.5.sp)
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextWhite,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReminderReportScreenPreview() {
    ReminderReportScreen()
}

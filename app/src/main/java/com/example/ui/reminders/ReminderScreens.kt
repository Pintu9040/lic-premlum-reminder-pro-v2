package com.example.ui.reminders

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.NotificationHelper
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ReminderCategoryTab {
    PREMIUM_DUE,
    BIRTHDAY,
    ANNIVERSARY,
    MATURITY
}

enum class PremiumDueSubFilter {
    ALL,
    DUE_TODAY,
    DUE_TOMORROW,
    DUE_THIS_WEEK,
    DUE_THIS_MONTH,
    OVERDUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    viewModel: LicViewModel,
    onCollectPremium: (PolicyEntity) -> Unit
) {
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ReminderCategoryTab.PREMIUM_DUE) }
    var premiumSubFilter by remember { mutableStateOf(PremiumDueSubFilter.ALL) }

    // WhatsApp Dialog State
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var whatsAppRecipientName by remember { mutableStateOf("") }
    var whatsAppRecipientMobile by remember { mutableStateOf("") }
    var whatsAppMessageText by remember { mutableStateOf("") }

    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val tomorrowStr = remember { today.plusDays(1).toString() }
    val endOfWeek = remember { today.plusDays(7) }

    // 1. Premium Due Filtering
    val filteredPolicies = remember(policies, searchQuery, premiumSubFilter) {
        policies.filter { p ->
            val matchesQuery = searchQuery.isBlank() ||
                    p.customerName.contains(searchQuery, ignoreCase = true) ||
                    p.policyNumber.contains(searchQuery, ignoreCase = true) ||
                    p.planName.contains(searchQuery, ignoreCase = true)

            val pDueDate = try { LocalDate.parse(p.dueDate) } catch (e: Exception) { null }

            val matchesFilter = when (premiumSubFilter) {
                PremiumDueSubFilter.ALL -> true
                PremiumDueSubFilter.DUE_TODAY -> p.dueDate == todayStr
                PremiumDueSubFilter.DUE_TOMORROW -> p.dueDate == tomorrowStr
                PremiumDueSubFilter.DUE_THIS_WEEK -> pDueDate != null && !pDueDate.isBefore(today) && !pDueDate.isAfter(endOfWeek)
                PremiumDueSubFilter.DUE_THIS_MONTH -> pDueDate != null && pDueDate.monthValue == today.monthValue && pDueDate.year == today.year
                PremiumDueSubFilter.OVERDUE -> pDueDate != null && pDueDate.isBefore(today) && p.status != "Paid-up" && p.status != "Matured"
            }
            matchesQuery && matchesFilter
        }
    }

    // 2. Birthday Filtering
    val birthdayCustomers = remember(customers, searchQuery) {
        customers.filter { cust ->
            val matchesQuery = searchQuery.isBlank() ||
                    cust.name.contains(searchQuery, ignoreCase = true) ||
                    cust.mobile.contains(searchQuery)
            val isBirthdayMonth = try {
                val dob = LocalDate.parse(cust.dob)
                dob.monthValue == today.monthValue
            } catch (e: Exception) { false }
            matchesQuery && isBirthdayMonth
        }
    }

    // 3. Anniversary Filtering
    val anniversaryCustomers = remember(customers, searchQuery) {
        customers.filter { cust ->
            val matchesQuery = searchQuery.isBlank() ||
                    cust.name.contains(searchQuery, ignoreCase = true) ||
                    cust.mobile.contains(searchQuery)
            val isAnniversaryMonth = try {
                if (cust.anniversary.isBlank()) false else {
                    val anniv = LocalDate.parse(cust.anniversary)
                    anniv.monthValue == today.monthValue
                }
            } catch (e: Exception) { false }
            matchesQuery && isAnniversaryMonth
        }
    }

    // 4. Maturity Filtering
    val maturityPolicies = remember(policies, searchQuery) {
        policies.filter { p ->
            val matchesQuery = searchQuery.isBlank() ||
                    p.customerName.contains(searchQuery, ignoreCase = true) ||
                    p.policyNumber.contains(searchQuery, ignoreCase = true)
            val pMaturity = try { LocalDate.parse(p.maturityDate) } catch (e: Exception) { null }
            val isMaturing = p.status == "Matured" || (pMaturity != null && pMaturity.year <= today.year + 1)
            matchesQuery && isMaturing
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Banner
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reminder & Follow-up Center",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp
                            )
                        )
                        Text(
                            text = "Automated policy dues, birthday & anniversary wishes",
                            style = MaterialTheme.typography.bodySmall.copy(color = AccentOrangeLight)
                        )
                    }

                    // Test Local Notification Button
                    Button(
                        onClick = {
                            val dueTodayCount = policies.count { it.dueDate == todayStr }
                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = 101,
                                title = "LIC Reminder System Active",
                                message = "You have $dueTodayCount premium dues scheduled for follow-up today."
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("test_notification_button")
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Notify", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by name, policy #, mobile...",
                    testTag = "reminder_search_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == ReminderCategoryTab.PREMIUM_DUE,
                        onClick = { selectedTab = ReminderCategoryTab.PREMIUM_DUE },
                        text = { Text("Premium Dues (${policies.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReminderCategoryTab.BIRTHDAY,
                        onClick = { selectedTab = ReminderCategoryTab.BIRTHDAY },
                        text = { Text("Birthdays (${birthdayCustomers.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReminderCategoryTab.ANNIVERSARY,
                        onClick = { selectedTab = ReminderCategoryTab.ANNIVERSARY },
                        text = { Text("Anniversaries (${anniversaryCustomers.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReminderCategoryTab.MATURITY,
                        onClick = { selectedTab = ReminderCategoryTab.MATURITY },
                        text = { Text("Maturity (${maturityPolicies.size})", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Sub-filter Row for Premium Dues
        if (selectedTab == ReminderCategoryTab.PREMIUM_DUE) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.ALL,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.ALL },
                        label = { Text("All Dues") }
                    )
                }
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.DUE_TODAY,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.DUE_TODAY },
                        label = { Text("Due Today") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ErrorRed, selectedLabelColor = Color.White)
                    )
                }
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.DUE_TOMORROW,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.DUE_TOMORROW },
                        label = { Text("Due Tomorrow") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange, selectedLabelColor = Color.White)
                    )
                }
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.DUE_THIS_WEEK,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.DUE_THIS_WEEK },
                        label = { Text("Due This Week") }
                    )
                }
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.DUE_THIS_MONTH,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.DUE_THIS_MONTH },
                        label = { Text("Due This Month") }
                    )
                }
                item {
                    FilterChip(
                        selected = premiumSubFilter == PremiumDueSubFilter.OVERDUE,
                        onClick = { premiumSubFilter = PremiumDueSubFilter.OVERDUE },
                        label = { Text("Overdue") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ErrorRed, selectedLabelColor = Color.White)
                    )
                }
            }
        }

        // Content Lists based on Active Tab
        when (selectedTab) {
            ReminderCategoryTab.PREMIUM_DUE -> {
                if (filteredPolicies.isEmpty()) {
                    EmptyReminderState(message = "No premium dues found for this filter.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPolicies, key = { it.id }) { policy ->
                            PremiumReminderCard(
                                policy = policy,
                                onCollect = { onCollectPremium(policy) },
                                onSendWhatsApp = {
                                    whatsAppRecipientName = policy.customerName
                                    whatsAppRecipientMobile = ""
                                    whatsAppMessageText = viewModel.generatePremiumReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        amount = policy.premiumAmount,
                                        dueDate = policy.dueDate
                                    )
                                    showWhatsAppDialog = true
                                },
                                onNotify = {
                                    NotificationHelper.showNotification(
                                        context = context,
                                        notificationId = policy.id.toInt(),
                                        title = "LIC Due: ${policy.customerName}",
                                        message = "Policy #${policy.policyNumber} premium of ₹${policy.premiumAmount} is due on ${policy.dueDate}."
                                    )
                                }
                            )
                        }
                    }
                }
            }

            ReminderCategoryTab.BIRTHDAY -> {
                if (birthdayCustomers.isEmpty()) {
                    EmptyReminderState(message = "No customer birthdays in current month.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(birthdayCustomers, key = { it.id }) { cust ->
                            EventWishCard(
                                title = cust.name,
                                subtitle = "Date of Birth: ${cust.dob} • Mobile: ${cust.mobile}",
                                icon = Icons.Default.Cake,
                                badgeText = "Birthday",
                                badgeColor = AccentOrange,
                                onSendWhatsApp = {
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.mobile
                                    whatsAppMessageText = viewModel.generateBirthdayWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                }
                            )
                        }
                    }
                }
            }

            ReminderCategoryTab.ANNIVERSARY -> {
                if (anniversaryCustomers.isEmpty()) {
                    EmptyReminderState(message = "No customer marriage anniversaries in current month.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(anniversaryCustomers, key = { it.id }) { cust ->
                            EventWishCard(
                                title = cust.name,
                                subtitle = "Anniversary: ${cust.anniversary} • Mobile: ${cust.mobile}",
                                icon = Icons.Default.Favorite,
                                badgeText = "Anniversary",
                                badgeColor = Color(0xFFE91E63),
                                onSendWhatsApp = {
                                    whatsAppRecipientName = cust.name
                                    whatsAppRecipientMobile = cust.mobile
                                    whatsAppMessageText = viewModel.generateAnniversaryWishMsg(cust.name)
                                    showWhatsAppDialog = true
                                }
                            )
                        }
                    }
                }
            }

            ReminderCategoryTab.MATURITY -> {
                if (maturityPolicies.isEmpty()) {
                    EmptyReminderState(message = "No policy maturities scheduled.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(maturityPolicies, key = { it.id }) { policy ->
                            EventWishCard(
                                title = "${policy.customerName} - ${policy.planName}",
                                subtitle = "Policy #: ${policy.policyNumber} • Sum Assured: ₹${"%.0f".format(policy.sumAssured)} • Maturity Date: ${policy.maturityDate}",
                                icon = Icons.Default.Verified,
                                badgeText = "Maturity",
                                badgeColor = EmeraldGreenSecondary,
                                onSendWhatsApp = {
                                    whatsAppRecipientName = policy.customerName
                                    whatsAppRecipientMobile = ""
                                    whatsAppMessageText = viewModel.generateMaturityReminderMsg(
                                        customerName = policy.customerName,
                                        policyNo = policy.policyNumber,
                                        planName = policy.planName,
                                        sumAssured = policy.sumAssured,
                                        maturityDate = policy.maturityDate
                                    )
                                    showWhatsAppDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Editable WhatsApp Dialog
    if (showWhatsAppDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = EmeraldGreenSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send WhatsApp Reminder", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recipient: $whatsAppRecipientName", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))

                    OutlinedTextField(
                        value = whatsAppRecipientMobile,
                        onValueChange = { whatsAppRecipientMobile = it },
                        label = { Text("Mobile Number (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Edit Message Preview:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))

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
                        showWhatsAppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_send_whatsapp_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open WhatsApp", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PremiumReminderCard(
    policy: PolicyEntity,
    onCollect: () -> Unit,
    onSendWhatsApp: () -> Unit,
    onNotify: () -> Unit
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
                    modifier = Modifier.weight(1f)
                ) {
                    CustomerAvatar(name = policy.customerName, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${policy.planName} • Pol #: ${policy.policyNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Premium Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(policy.premiumAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                    Text("Due Date: ${policy.dueDate}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNotify,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Trigger Notification",
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onSendWhatsApp,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "WhatsApp Reminder",
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onCollect,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Text("Collect", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun EventWishCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    badgeColor: Color,
    onSendWhatsApp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSendWhatsApp,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wish", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun EmptyReminderState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

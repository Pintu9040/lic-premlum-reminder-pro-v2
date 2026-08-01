package com.example.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.local.AgentProfileEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.policy.getPolicyOutstandingBalance
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReportType(val title: String, val description: String) {
    TODAYS_COLLECTION("Today's Collection", "Summary and list of payments collected today"),
    MONTHLY_COLLECTION("Monthly Collection", "Summary and list of payments collected this month"),
    OUTSTANDING_REPORT("Outstanding Report", "Policies with unpaid premium balances"),
    DUE_CUSTOMERS("Due Customers", "Clients with pending or overdue policy premiums"),
    CUSTOMER_LIST("Customer List", "Complete directory of all registered customers"),
    POLICY_LIST("Policy List", "Complete list of all policy records and status"),
    PAYMENT_HISTORY("Payment History", "Detailed ledger of all recorded premium payments")
}

enum class FilterOption(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom Date Range"),
    ALL("All Time")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: LicViewModel
) {
    val payments by viewModel.payments.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val context = LocalContext.current

    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val yesterdayStr = remember { today.minusDays(1).toString() }
    val currentMonth = remember { today.monthValue }
    val currentYear = remember { today.year }

    // Search and Filter State
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FilterOption.ALL) }
    var customStartDate by remember { mutableStateOf(today.minusDays(7).toString()) }
    var customEndDate by remember { mutableStateOf(todayStr) }

    // Dialog States
    var viewingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // --- DATE PERIOD BOUNDARIES ---
    val startOfWeek = remember(today) { today.with(DayOfWeek.MONDAY) }
    val endOfWeek = remember(today) { today.with(DayOfWeek.SUNDAY) }

    // --- COLLECTION COMPUTATIONS (DAILY, WEEKLY, MONTHLY, YEARLY) ---
    val todayPayments = remember(payments, todayStr) {
        payments.filter { it.paymentDate == todayStr }
    }
    val todayTotalCollected = remember(todayPayments) {
        todayPayments.sumOf { it.paidAmount + it.lateFee }
    }
    val todayPaymentCount = todayPayments.size

    val weeklyPayments = remember(payments, startOfWeek, endOfWeek) {
        payments.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                (d.isEqual(startOfWeek) || d.isAfter(startOfWeek)) && (d.isEqual(endOfWeek) || d.isBefore(endOfWeek))
            } catch (e: Exception) { false }
        }
    }
    val weeklyTotalCollected = remember(weeklyPayments) {
        weeklyPayments.sumOf { it.paidAmount + it.lateFee }
    }

    val monthlyPayments = remember(payments, currentMonth, currentYear) {
        payments.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.monthValue == currentMonth && d.year == currentYear
            } catch (e: Exception) { false }
        }
    }
    val monthlyTotalCollected = remember(monthlyPayments) {
        monthlyPayments.sumOf { it.paidAmount + it.lateFee }
    }
    val monthlyPaymentCount = monthlyPayments.size

    val yearlyPayments = remember(payments, currentYear) {
        payments.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.year == currentYear
            } catch (e: Exception) { false }
        }
    }
    val yearlyTotalCollected = remember(yearlyPayments) {
        yearlyPayments.sumOf { it.paidAmount + it.lateFee }
    }

    // --- OUTSTANDING & POLICY METRICS ---
    val totalOutstandingBalance = remember(policies, payments) {
        policies.sumOf { p ->
            getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
        }
    }

    val totalCustomersCount = customers.size

    val activePoliciesCount = remember(policies) {
        policies.count {
            !it.status.equals("Lapsed", ignoreCase = true) &&
                    !it.status.equals("Matured", ignoreCase = true) &&
                    !it.status.equals("Cancelled", ignoreCase = true)
        }
    }

    val duePoliciesCount = remember(policies, payments) {
        policies.count { p ->
            getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0 ||
                    p.status.equals("Due", ignoreCase = true) ||
                    p.status.equals("Overdue", ignoreCase = true) ||
                    p.status.equals("Grace", ignoreCase = true)
        }
    }

    val lapsedPoliciesCount = remember(policies) {
        policies.count { it.status.equals("Lapsed", ignoreCase = true) }
    }

    val maturedPoliciesCount = remember(policies) {
        policies.count { it.status.equals("Matured", ignoreCase = true) || it.status.equals("Paid-up", ignoreCase = true) }
    }

    // --- CUSTOMER ANALYTICS COMPUTATIONS ---
    val newCustomersThisMonth = remember(customers, policies, currentMonth, currentYear) {
        customers.count { cust ->
            val custPolicies = policies.filter { it.customerId == cust.id }
            custPolicies.any { p ->
                try {
                    val issueDate = LocalDate.parse(p.issueDate)
                    issueDate.monthValue == currentMonth && issueDate.year == currentYear
                } catch (e: Exception) { false }
            }
        }
    }

    val activeCustomersCount = remember(customers, policies) {
        customers.count { cust ->
            policies.any { p -> p.customerId == cust.id && (!p.status.equals("Lapsed", ignoreCase = true) && !p.status.equals("Matured", ignoreCase = true)) }
        }
    }

    val customersWithDuePremium = remember(customers, policies, payments) {
        customers.count { cust ->
            val custPolicies = policies.filter { it.customerId == cust.id }
            custPolicies.any { p ->
                getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0 ||
                        p.status.equals("Due", ignoreCase = true) ||
                        p.status.equals("Overdue", ignoreCase = true) ||
                        p.status.equals("Grace", ignoreCase = true)
            }
        }
    }

    val customersWithOutstandingBalance = remember(customers, policies, payments) {
        customers.count { cust ->
            val custPolicies = policies.filter { it.customerId == cust.id }
            val custOutstanding = custPolicies.sumOf { p ->
                getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
            }
            custOutstanding > 0
        }
    }

    // --- FOLLOW-UP ANALYTICS COMPUTATIONS ---
    val totalFollowUps = remember(policies, payments) {
        policies.count { p ->
            p.status.equals("Due", ignoreCase = true) ||
                    p.status.equals("Overdue", ignoreCase = true) ||
                    p.status.equals("Lapsed", ignoreCase = true) ||
                    p.status.equals("Grace", ignoreCase = true) ||
                    getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0
        }
    }

    val completedFollowUps = remember(policies, payments) {
        policies.count { p ->
            val pPayments = payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }
            pPayments.isNotEmpty() && getPolicyOutstandingBalance(p, pPayments) <= 0
        }
    }

    val pendingFollowUps = remember(policies, payments, today) {
        policies.count { p ->
            val outstanding = getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
            val dueDate = try { LocalDate.parse(p.dueDate) } catch (e: Exception) { null }
            outstanding > 0 && (dueDate == null || !dueDate.isBefore(today))
        }
    }

    val overdueFollowUps = remember(policies, payments, today) {
        policies.count { p ->
            val outstanding = getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
            val dueDate = try { LocalDate.parse(p.dueDate) } catch (e: Exception) { null }
            p.status.equals("Lapsed", ignoreCase = true) || p.status.equals("Overdue", ignoreCase = true) || (outstanding > 0 && dueDate != null && dueDate.isBefore(today))
        }
    }

    // --- PAYMENT ANALYTICS COMPUTATIONS ---
    val totalScheduledPremium = remember(policies) {
        policies.sumOf { it.premiumAmount }
    }

    val totalAmountCollected = remember(payments) {
        payments.sumOf { it.paidAmount + it.lateFee }
    }

    val collectionPercentage = remember(totalAmountCollected, totalOutstandingBalance) {
        val totalTarget = totalAmountCollected + totalOutstandingBalance
        if (totalTarget > 0) {
            ((totalAmountCollected / totalTarget) * 100).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    // --- FILTERED PAYMENT LIST ---
    val filteredPayments = remember(payments, selectedFilter, customStartDate, customEndDate, searchQuery, customers) {
        payments.filter { payment ->
            val dateMatches = when (selectedFilter) {
                FilterOption.TODAY -> payment.paymentDate == todayStr
                FilterOption.YESTERDAY -> payment.paymentDate == yesterdayStr
                FilterOption.THIS_WEEK -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        (d.isEqual(startOfWeek) || d.isAfter(startOfWeek)) && (d.isEqual(endOfWeek) || d.isBefore(endOfWeek))
                    } catch (e: Exception) { false }
                }
                FilterOption.THIS_MONTH -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        d.monthValue == currentMonth && d.year == currentYear
                    } catch (e: Exception) { false }
                }
                FilterOption.THIS_YEAR -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        d.year == currentYear
                    } catch (e: Exception) { false }
                }
                FilterOption.CUSTOM -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        val startD = LocalDate.parse(customStartDate)
                        val endD = LocalDate.parse(customEndDate)
                        (d.isEqual(startD) || d.isAfter(startD)) && (d.isEqual(endD) || d.isBefore(endD))
                    } catch (e: Exception) { false }
                }
                FilterOption.ALL -> true
            }

            if (!dateMatches) return@filter false

            if (searchQuery.isBlank()) return@filter true

            val query = searchQuery.trim().lowercase()
            val matchedCust = customers.find { it.id == payment.customerId || it.name.equals(payment.customerName, ignoreCase = true) }
            val mobile = matchedCust?.mobile?.lowercase() ?: ""

            payment.customerName.lowercase().contains(query) ||
                    payment.policyNumber.lowercase().contains(query) ||
                    mobile.contains(query) ||
                    payment.receiptNumber.lowercase().contains(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- TOP HEADER BANNER ---
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Performance Analytics",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Advisor: ${agentProfile?.agentName ?: "Pintu Ojha"} • Real-Time Business Dashboard",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AccentOrangeLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showExportDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("export_center_button")
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = "Export Report", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Center", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // --- MAIN SCROLLABLE CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ==========================================
            // 1. PERFORMANCE OVERVIEW
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PERFORMANCE OVERVIEW",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Row 1: Today's Collection & Weekly Collection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_todays_collection"),
                        title = "Today's Collection",
                        value = "₹${"%.2f".format(todayTotalCollected)}",
                        subtitle = "$todayPaymentCount Receipts Today",
                        icon = Icons.Default.Today,
                        color = EmeraldGreenSecondary
                    )

                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_weekly_collection"),
                        title = "Weekly Collection",
                        value = "₹${"%.2f".format(weeklyTotalCollected)}",
                        subtitle = "Current Week",
                        icon = Icons.Default.DateRange,
                        color = RoyalBluePrimary
                    )
                }

                // Row 2: Monthly Collection & Yearly Collection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_monthly_collection"),
                        title = "Monthly Collection",
                        value = "₹${"%.2f".format(monthlyTotalCollected)}",
                        subtitle = "$monthlyPaymentCount Receipts (${today.month.name})",
                        icon = Icons.Default.CalendarMonth,
                        color = Color(0xFF0288D1)
                    )

                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_yearly_collection"),
                        title = "Yearly Collection",
                        value = "₹${"%.2f".format(yearlyTotalCollected)}",
                        subtitle = "Year $currentYear",
                        icon = Icons.Default.CalendarToday,
                        color = Color(0xFF673AB7)
                    )
                }

                // Row 3: Total Outstanding & Total Customers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_outstanding_balance"),
                        title = "Total Outstanding",
                        value = "₹${"%.2f".format(totalOutstandingBalance)}",
                        subtitle = "Unpaid Balance",
                        icon = Icons.Default.AccountBalanceWallet,
                        color = ErrorRed
                    )

                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_total_customers"),
                        title = "Total Customers",
                        value = "$totalCustomersCount",
                        subtitle = "$activeCustomersCount Active Clients",
                        icon = Icons.Default.People,
                        color = Color(0xFF00897B)
                    )
                }

                // Row 4: Active Policies, Due Policies, Lapsed Policies
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_active_policies"),
                        title = "Active Policies",
                        value = "$activePoliciesCount",
                        subtitle = "In Force",
                        icon = Icons.Default.Verified,
                        color = EmeraldGreenSecondary
                    )

                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_due_policies"),
                        title = "Due Policies",
                        value = "$duePoliciesCount",
                        subtitle = "Premium Due",
                        icon = Icons.Default.Schedule,
                        color = AccentOrange
                    )

                    DashboardMetricCard(
                        modifier = Modifier.weight(1f).testTag("card_lapsed_policies"),
                        title = "Lapsed Policies",
                        value = "$lapsedPoliciesCount",
                        subtitle = "Needs Revival",
                        icon = Icons.Default.Warning,
                        color = ErrorRed
                    )
                }
            }


            // ==========================================
            // 2. COLLECTION ANALYTICS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_collection_analytics"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RoyalBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "COLLECTION ANALYTICS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Collection Grid Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Daily",
                            value = "₹${"%.0f".format(todayTotalCollected)}",
                            color = EmeraldGreenSecondary
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Weekly",
                            value = "₹${"%.0f".format(weeklyTotalCollected)}",
                            color = RoyalBluePrimary
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Monthly",
                            value = "₹${"%.0f".format(monthlyTotalCollected)}",
                            color = Color(0xFF0288D1)
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Yearly",
                            value = "₹${"%.0f".format(yearlyTotalCollected)}",
                            color = Color(0xFF7B1FA2)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Simple Collection Trend Chart
                    CollectionTrendChart(payments = payments)
                }
            }


            // ==========================================
            // 3. CUSTOMER ANALYTICS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_customer_analytics"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00897B).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "CUSTOMER ANALYTICS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "New This Month",
                            value = "$newCustomersThisMonth",
                            icon = Icons.Default.PersonAdd,
                            color = EmeraldGreenSecondary
                        )
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Active Customers",
                            value = "$activeCustomersCount",
                            icon = Icons.Default.Person,
                            color = RoyalBluePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "With Due Premium",
                            value = "$customersWithDuePremium",
                            icon = Icons.Default.EventRepeat,
                            color = AccentOrange
                        )
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Outstanding Balance",
                            value = "$customersWithOutstandingBalance",
                            icon = Icons.Default.MoneyOff,
                            color = ErrorRed
                        )
                    }
                }
            }


            // ==========================================
            // 4. POLICY ANALYTICS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_policy_analytics"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RoyalBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "POLICY ANALYTICS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Total",
                            value = "${policies.size}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Active",
                            value = "$activePoliciesCount",
                            color = EmeraldGreenSecondary
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Due",
                            value = "$duePoliciesCount",
                            color = AccentOrange
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Lapsed",
                            value = "$lapsedPoliciesCount",
                            color = ErrorRed
                        )
                        AnalyticsSmallStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Matured",
                            value = "$maturedPoliciesCount",
                            color = Color(0xFF0288D1)
                        )
                    }
                }
            }


            // ==========================================
            // 5. FOLLOW-UP ANALYTICS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_followup_analytics"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AccentOrange.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "FOLLOW-UP ANALYTICS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Total Follow-ups",
                            value = "$totalFollowUps",
                            icon = Icons.Default.RingVolume,
                            color = RoyalBluePrimary
                        )
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Completed",
                            value = "$completedFollowUps",
                            icon = Icons.Default.CheckCircle,
                            color = EmeraldGreenSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Pending",
                            value = "$pendingFollowUps",
                            icon = Icons.Default.HourglassTop,
                            color = AccentOrange
                        )
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Overdue",
                            value = "$overdueFollowUps",
                            icon = Icons.Default.WarningAmber,
                            color = ErrorRed
                        )
                    }
                }
            }


            // ==========================================
            // 6. PAYMENT ANALYTICS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_payment_analytics"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreenSecondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "PAYMENT ANALYTICS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Total Scheduled",
                            value = "₹${"%.0f".format(totalScheduledPremium)}",
                            icon = Icons.Default.Receipt,
                            color = RoyalBluePrimary
                        )
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Total Collected",
                            value = "₹${"%.0f".format(totalAmountCollected)}",
                            icon = Icons.Default.Paid,
                            color = EmeraldGreenSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Total Outstanding",
                            value = "₹${"%.0f".format(totalOutstandingBalance)}",
                            icon = Icons.Default.AccountBalance,
                            color = ErrorRed
                        )
                        
                        // Collection Percentage Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = EmeraldGreenSecondary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Collection Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${"%.1f".format(collectionPercentage)}%",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldGreenSecondary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (collectionPercentage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = EmeraldGreenSecondary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }


            // ==========================================
            // 7. FILTERS & PAYMENT TRANSACTIONS LIST
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_payment_list"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payment Ledger",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "${filteredPayments.size} Records Found",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- SEARCH BAR (Customer Name, Policy Number, Mobile Number) ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_payment_input"),
                        placeholder = { Text("Search by Customer Name, Policy #, Mobile #") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- FILTER CHIPS (Today, Yesterday, This Week, This Month, This Year, Custom Date Range, All) ---
                    Text("Filter By Period:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { selectedFilter = option },
                                label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (selectedFilter == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // --- CUSTOM DATE RANGE PICKER INPUTS ---
                    if (selectedFilter == FilterOption.CUSTOM) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = customStartDate,
                                onValueChange = { customStartDate = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Start Date (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = customEndDate,
                                onValueChange = { customEndDate = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("End Date (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- PAYMENT CARDS LIST ---
                    if (filteredPayments.isEmpty()) {
                        StandardEmptyState(
                            title = "No Reports Data",
                            description = "No payment records match your search or date filter range.",
                            icon = Icons.Outlined.Assessment
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            filteredPayments.forEach { payment ->
                                val matchedPolicy = policies.find { it.id == payment.policyId || it.policyNumber == payment.policyNumber }
                                val matchedCustomer = customers.find { it.id == payment.customerId || it.name.equals(payment.customerName, ignoreCase = true) }

                                PaymentCardItem(
                                    payment = payment,
                                    policy = matchedPolicy,
                                    customer = matchedCustomer,
                                    onView = { viewingPayment = payment },
                                    onEdit = { editingPayment = payment },
                                    onDelete = { deletingPayment = payment },
                                    onShare = { shareReceipt(context, payment, matchedPolicy, matchedCustomer) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // DIALOGS: VIEW, EDIT, DELETE
    // ==========================================

    // 1. VIEW PAYMENT DIALOG
    viewingPayment?.let { payment ->
        val matchedPolicy = policies.find { it.id == payment.policyId || it.policyNumber == payment.policyNumber }
        val matchedCustomer = customers.find { it.id == payment.customerId || it.name.equals(payment.customerName, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { viewingPayment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = RoyalBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Payment Receipt Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Receipt No", payment.receiptNumber)
                    DetailRow("Customer Name", payment.customerName)
                    DetailRow("Mobile Number", matchedCustomer?.mobile ?: "N/A")
                    DetailRow("Policy Number", payment.policyNumber)
                    DetailRow("Plan Name", matchedPolicy?.planName ?: "LIC Policy")
                    DetailRow("Scheduled Premium", "₹${"%.2f".format(matchedPolicy?.premiumAmount ?: 0.0)}")
                    DetailRow("Amount Paid", "₹${"%.2f".format(payment.paidAmount)}")
                    DetailRow("Late Fee / Fine", "₹${"%.2f".format(payment.lateFee)}")
                    DetailRow("Total Received", "₹${"%.2f".format(payment.paidAmount + payment.lateFee)}", highlight = true)
                    val remaining = (matchedPolicy?.premiumAmount ?: 0.0) - payment.paidAmount
                    DetailRow("Remaining Balance", "₹${"%.2f".format(if (remaining > 0) remaining else 0.0)}")
                    DetailRow("Payment Mode", payment.paymentMode)
                    DetailRow("Payment Date", payment.paymentDate)
                    if (payment.notes.isNotBlank()) {
                        DetailRow("Notes", payment.notes)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareReceipt(context, payment, matchedPolicy, matchedCustomer)
                        viewingPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingPayment = null }) {
                    Text("Close")
                }
            }
        )
    }

    // 2. EDIT PAYMENT DIALOG
    editingPayment?.let { payment ->
        var editPaidAmount by remember { mutableStateOf(payment.paidAmount.toString()) }
        var editLateFee by remember { mutableStateOf(payment.lateFee.toString()) }
        var editMode by remember { mutableStateOf(payment.paymentMode) }
        var editDate by remember { mutableStateOf(payment.paymentDate) }
        var editNotes by remember { mutableStateOf(payment.notes) }

        AlertDialog(
            onDismissRequest = { editingPayment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = AccentOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Payment Record", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Customer: ${payment.customerName} (Pol #${payment.policyNumber})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = editPaidAmount,
                        onValueChange = { editPaidAmount = it },
                        label = { Text("Amount Received (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editLateFee,
                        onValueChange = { editLateFee = it },
                        label = { Text("Late Fee / Fine (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Payment Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Payment Mode", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("UPI", "Cash", "Net Banking", "Cheque").forEach { mode ->
                            FilterChip(
                                selected = editMode.equals(mode, ignoreCase = true),
                                onClick = { editMode = mode },
                                label = { Text(mode, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes / Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmount = editPaidAmount.toDoubleOrNull() ?: payment.paidAmount
                        val newLateFee = editLateFee.toDoubleOrNull() ?: payment.lateFee

                        val updated = payment.copy(
                            paidAmount = newAmount,
                            lateFee = newLateFee,
                            paymentMode = editMode,
                            paymentDate = editDate,
                            notes = editNotes
                        )

                        viewModel.updatePayment(updated) {
                            Toast.makeText(context, "Payment updated & totals recalculated!", Toast.LENGTH_SHORT).show()
                        }
                        editingPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPayment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. DELETE PAYMENT DIALOG
    deletingPayment?.let { payment ->
        AlertDialog(
            onDismissRequest = { deletingPayment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Payment Record?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Text(
                    "Are you sure you want to delete payment receipt #${payment.receiptNumber} for ${payment.customerName} (₹${payment.paidAmount})?\n\nThis action will recalculate Policy Outstanding, Client Balance, and Dashboard Totals.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(payment) {
                            Toast.makeText(context, "Payment deleted & totals recalculated!", Toast.LENGTH_SHORT).show()
                        }
                        deletingPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPayment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            agentProfile = agentProfile,
            payments = payments,
            policies = policies,
            customers = customers,
            onDismiss = { showExportDialog = false }
        )
    }
}

// ==========================================
// ITEM COMPONENTS & HELPER FUNCTIONS
// ==========================================

@Composable
fun DashboardMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ModeBreakdownItem(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    amount: Double,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color))
            Text("₹${"%.0f".format(amount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface))
            Text("$count Txns", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
fun PaymentCardItem(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    customer: CustomerEntity?,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val totalReceived = payment.paidAmount + payment.lateFee
    val premiumAmount = policy?.premiumAmount ?: 0.0
    val remainingBalance = if (premiumAmount > payment.paidAmount) (premiumAmount - payment.paidAmount) else 0.0

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_card_${payment.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Customer Name & Amount Received
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Policy #: ${payment.policyNumber} • Rcpt #${payment.receiptNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customer?.mobile != null && customer.mobile.isNotBlank()) {
                        Text(
                            text = "Mobile: ${customer.mobile}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalBluePrimary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${"%.2f".format(totalReceived)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldGreenSecondary)
                    )
                    Surface(
                        color = RoyalBluePrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = payment.paymentMode,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary, fontSize = 10.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Details Grid: Premium, Received, Remaining, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Scheduled Premium", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(premiumAmount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }

                Column {
                    Text("Amount Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(payment.paidAmount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = EmeraldGreenSecondary))
                }

                Column {
                    Text("Remaining Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(remainingBalance)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = if (remainingBalance > 0) ErrorRed else EmeraldGreenSecondary))
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(payment.paymentDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            if (payment.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes: ${payment.notes}",
                        modifier = Modifier.padding( horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: View, Edit, Delete, Share Receipt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // View
                    IconButton(onClick = onView, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = "View Details", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                    }

                    // Edit
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Payment", tint = AccentOrange, modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Payment", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }

                // Share Receipt
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Receipt", modifier = Modifier.size(14.dp), tint = RoyalBluePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Receipt", style = MaterialTheme.typography.labelSmall.copy(color = RoyalBluePrimary, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (highlight) EmeraldGreenSecondary else MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

// Share Receipt Helper Intent
fun shareReceipt(context: Context, payment: PaymentEntity, policy: PolicyEntity?, customer: CustomerEntity?) {
    val shareText = """
        🧾 *LIC PREMIUM RECEIPT*
        ----------------------------------
        Receipt No: ${payment.receiptNumber}
        Date: ${payment.paymentDate}
        
        Customer: ${payment.customerName}
        Mobile: ${customer?.mobile ?: "N/A"}
        Policy No: ${payment.policyNumber}
        Plan: ${policy?.planName ?: "LIC Policy"}
        
        Scheduled Premium: ₹${"%.2f".format(policy?.premiumAmount ?: 0.0)}
        Amount Received: ₹${"%.2f".format(payment.paidAmount)}
        Late Fee / Fine: ₹${"%.2f".format(payment.lateFee)}
        Total Received: ₹${"%.2f".format(payment.paidAmount + payment.lateFee)}
        Payment Mode: ${payment.paymentMode}
        Notes: ${if (payment.notes.isNotBlank()) payment.notes else "None"}
        ----------------------------------
        Thank you for choosing LIC!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "LIC Premium Receipt #${payment.receiptNumber}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Premium Receipt"))
}

// ==========================================
// REPORT EXPORT & PRINT MODULE HELPERS
// ==========================================

fun generateReportPdf(
    context: Context,
    reportType: ReportType,
    filterOption: FilterOption,
    startDateStr: String,
    endDateStr: String,
    agentProfile: AgentProfileEntity?,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
): File {
    val pdfDoc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 Page
    val page = pdfDoc.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    val agentName = agentProfile?.agentName ?: "LIC Agent"
    val agencyCode = agentProfile?.agencyCode ?: "12345678"
    val branchName = agentProfile?.branchName ?: "LIC Main Branch"
    val reportDateStr = LocalDate.now().toString()
    val filterLabel = when (filterOption) {
        FilterOption.TODAY -> "Today ($reportDateStr)"
        FilterOption.YESTERDAY -> "Yesterday"
        FilterOption.THIS_WEEK -> "This Week"
        FilterOption.THIS_MONTH -> "This Month"
        FilterOption.THIS_YEAR -> "This Year"
        FilterOption.CUSTOM -> "Range: $startDateStr to $endDateStr"
        FilterOption.ALL -> "All Time"
    }

    // Header Background Accent (Royal Blue)
    paint.color = android.graphics.Color.parseColor("#1E3A8A")
    canvas.drawRect(0f, 0f, 595f, 95f, paint)

    // Title & Agent Profile Info Header
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 15f
    paint.isFakeBoldText = true
    canvas.drawText("LIC PREMIUM REMINDER PRO - ${reportType.title.uppercase()}", 25f, 35f, paint)

    paint.textSize = 9.5f
    paint.isFakeBoldText = false
    canvas.drawText("Agent Name: $agentName   |   Branch / Agency Code: $branchName ($agencyCode)", 25f, 55f, paint)
    canvas.drawText("Report Date: $reportDateStr   |   Filter Selected: $filterLabel", 25f, 75f, paint)

    var y = 115f

    // Summary Section Box (Grey Card)
    paint.color = android.graphics.Color.parseColor("#F3F4F6")
    canvas.drawRoundRect(25f, y, 570f, y + 65f, 10f, 10f, paint)

    paint.color = android.graphics.Color.parseColor("#1F2937")
    paint.textSize = 10.5f
    paint.isFakeBoldText = true
    canvas.drawText("EXECUTIVE SUMMARY (COLLECTION & OUTSTANDING)", 35f, y + 20f, paint)

    paint.textSize = 9f
    paint.isFakeBoldText = false

    val totalCollected = payments.sumOf { it.paidAmount + it.lateFee }
    val totalOutstanding = policies.sumOf { p ->
        getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
    }
    val totalCustomersCount = customers.size
    val totalPoliciesCount = policies.size

    canvas.drawText("Collection Summary: Total Collected = Rs.${"%.2f".format(totalCollected)}   |   Total Receipts = ${payments.size}", 35f, y + 40f, paint)
    canvas.drawText("Outstanding Summary: Total Balance Pending = Rs.${"%.2f".format(totalOutstanding)}   |   Clients = $totalCustomersCount   |   Policies = $totalPoliciesCount", 35f, y + 55f, paint)

    y += 85f

    // Table Header Bar (Accent Blue)
    paint.color = android.graphics.Color.parseColor("#2563EB")
    canvas.drawRect(25f, y, 570f, y + 22f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 8.5f
    paint.isFakeBoldText = true

    when (reportType) {
        ReportType.TODAYS_COLLECTION, ReportType.MONTHLY_COLLECTION, ReportType.PAYMENT_HISTORY -> {
            canvas.drawText("Rcpt #", 30f, y + 15f, paint)
            canvas.drawText("Date", 85f, y + 15f, paint)
            canvas.drawText("Customer Name", 150f, y + 15f, paint)
            canvas.drawText("Policy #", 290f, y + 15f, paint)
            canvas.drawText("Mode", 380f, y + 15f, paint)
            canvas.drawText("Paid Amt (Rs)", 445f, y + 15f, paint)
            canvas.drawText("Late Fee", 515f, y + 15f, paint)
        }
        ReportType.OUTSTANDING_REPORT -> {
            canvas.drawText("Policy #", 30f, y + 15f, paint)
            canvas.drawText("Plan", 110f, y + 15f, paint)
            canvas.drawText("Customer Details", 190f, y + 15f, paint)
            canvas.drawText("Mobile", 320f, y + 15f, paint)
            canvas.drawText("Premium", 400f, y + 15f, paint)
            canvas.drawText("Paid", 465f, y + 15f, paint)
            canvas.drawText("Outstanding", 515f, y + 15f, paint)
        }
        ReportType.DUE_CUSTOMERS -> {
            canvas.drawText("Cust ID", 30f, y + 15f, paint)
            canvas.drawText("Customer Name", 110f, y + 15f, paint)
            canvas.drawText("Mobile Number", 250f, y + 15f, paint)
            canvas.drawText("City", 360f, y + 15f, paint)
            canvas.drawText("Policies", 450f, y + 15f, paint)
            canvas.drawText("Outstanding (Rs)", 500f, y + 15f, paint)
        }
        ReportType.CUSTOMER_LIST -> {
            canvas.drawText("Cust ID", 30f, y + 15f, paint)
            canvas.drawText("Customer Name", 100f, y + 15f, paint)
            canvas.drawText("Mobile Number", 240f, y + 15f, paint)
            canvas.drawText("Email Address", 340f, y + 15f, paint)
            canvas.drawText("City / Location", 460f, y + 15f, paint)
        }
        ReportType.POLICY_LIST -> {
            canvas.drawText("Policy #", 30f, y + 15f, paint)
            canvas.drawText("Plan Name", 110f, y + 15f, paint)
            canvas.drawText("Customer Name", 200f, y + 15f, paint)
            canvas.drawText("Sum Assured", 330f, y + 15f, paint)
            canvas.drawText("Premium", 420f, y + 15f, paint)
            canvas.drawText("Status", 500f, y + 15f, paint)
        }
    }

    y += 28f
    paint.color = android.graphics.Color.parseColor("#374151")
    paint.isFakeBoldText = false
    paint.textSize = 8f

    // Rows Population
    when (reportType) {
        ReportType.TODAYS_COLLECTION, ReportType.MONTHLY_COLLECTION, ReportType.PAYMENT_HISTORY -> {
            payments.take(35).forEach { p ->
                if (y > 800f) return@forEach
                canvas.drawText(p.receiptNumber.take(10), 30f, y, paint)
                canvas.drawText(p.paymentDate.take(10), 85f, y, paint)
                canvas.drawText(p.customerName.take(22), 150f, y, paint)
                canvas.drawText(p.policyNumber.take(14), 290f, y, paint)
                canvas.drawText(p.paymentMode.take(10), 380f, y, paint)
                canvas.drawText("%.2f".format(p.paidAmount), 445f, y, paint)
                canvas.drawText("%.2f".format(p.lateFee), 515f, y, paint)
                y += 18f
            }
        }
        ReportType.OUTSTANDING_REPORT -> {
            policies.filter { p ->
                getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0
            }.take(35).forEach { p ->
                if (y > 800f) return@forEach
                val cust = customers.find { it.id == p.customerId }
                val paid = payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }.sumOf { it.paidAmount }
                val outstanding = getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })

                canvas.drawText(p.policyNumber.take(12), 30f, y, paint)
                canvas.drawText(p.planName.take(12), 110f, y, paint)
                canvas.drawText((cust?.name ?: p.customerId.toString()).take(20), 190f, y, paint)
                canvas.drawText((cust?.mobile ?: "N/A").take(12), 320f, y, paint)
                canvas.drawText("%.0f".format(p.premiumAmount), 400f, y, paint)
                canvas.drawText("%.0f".format(paid), 465f, y, paint)
                canvas.drawText("%.0f".format(outstanding), 515f, y, paint)
                y += 18f
            }
        }
        ReportType.DUE_CUSTOMERS -> {
            customers.filter { cust ->
                val custPolicies = policies.filter { it.customerId == cust.id }
                custPolicies.any { p ->
                    getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0 ||
                            p.status.equals("Due", ignoreCase = true) || p.status.equals("Overdue", ignoreCase = true)
                }
            }.take(35).forEach { cust ->
                if (y > 800f) return@forEach
                val custPolicies = policies.filter { it.customerId == cust.id }
                val custOutstanding = custPolicies.sumOf { p ->
                    getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
                }
                canvas.drawText(cust.id.toString().take(10), 30f, y, paint)
                canvas.drawText(cust.name.take(22), 110f, y, paint)
                canvas.drawText(cust.mobile.take(14), 250f, y, paint)
                canvas.drawText(cust.address.ifBlank { "N/A" }.take(12), 360f, y, paint)
                canvas.drawText("${custPolicies.size}", 450f, y, paint)
                canvas.drawText("%.2f".format(custOutstanding), 500f, y, paint)
                y += 18f
            }
        }
        ReportType.CUSTOMER_LIST -> {
            customers.take(35).forEach { cust ->
                if (y > 800f) return@forEach
                canvas.drawText(cust.id.toString().take(10), 30f, y, paint)
                canvas.drawText(cust.name.take(22), 100f, y, paint)
                canvas.drawText(cust.mobile.take(14), 240f, y, paint)
                canvas.drawText(cust.email.ifBlank { "N/A" }.take(18), 340f, y, paint)
                canvas.drawText(cust.address.ifBlank { "N/A" }.take(16), 460f, y, paint)
                y += 18f
            }
        }
        ReportType.POLICY_LIST -> {
            policies.take(35).forEach { p ->
                if (y > 800f) return@forEach
                val cust = customers.find { it.id == p.customerId }
                canvas.drawText(p.policyNumber.take(12), 30f, y, paint)
                canvas.drawText(p.planName.take(14), 110f, y, paint)
                canvas.drawText((cust?.name ?: p.customerId.toString()).take(20), 200f, y, paint)
                canvas.drawText("%.0f".format(p.sumAssured), 330f, y, paint)
                canvas.drawText("%.2f".format(p.premiumAmount), 420f, y, paint)
                canvas.drawText(p.status.take(12), 500f, y, paint)
                y += 18f
            }
        }
    }

    // Page Footer
    paint.color = android.graphics.Color.GRAY
    paint.textSize = 8f
    canvas.drawText("Generated by LIC Premium Reminder Pro  •  Advisor CRM System", 25f, 825f, paint)

    pdfDoc.finishPage(page)

    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LIC_${reportType.name}_${System.currentTimeMillis()}.pdf")
    pdfDoc.writeTo(FileOutputStream(file))
    pdfDoc.close()

    return file
}

fun generateReportExcel(
    context: Context,
    reportType: ReportType,
    filterOption: FilterOption,
    startDateStr: String,
    endDateStr: String,
    agentProfile: AgentProfileEntity?,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
): File {
    val csv = StringBuilder()
    val agentName = agentProfile?.agentName ?: "LIC Agent"
    val agencyCode = agentProfile?.agencyCode ?: "12345678"
    val branchName = agentProfile?.branchName ?: "LIC Main Branch"
    val reportDateStr = LocalDate.now().toString()

    csv.append("LIC PREMIUM REMINDER PRO - ${reportType.title.uppercase()}\n")
    csv.append("Agent Name:,$agentName,Agency Code:,$agencyCode,Branch:,$branchName,Generated Date:,$reportDateStr\n\n")

    when (reportType) {
        ReportType.TODAYS_COLLECTION, ReportType.MONTHLY_COLLECTION, ReportType.PAYMENT_HISTORY -> {
            csv.append("Receipt Number,Payment Date,Customer Name,Policy Number,Payment Mode,Paid Amount,Late Fee,Total Amount,Notes\n")
            payments.forEach { p ->
                val total = p.paidAmount + p.lateFee
                csv.append("\"${p.receiptNumber}\",\"${p.paymentDate}\",\"${p.customerName}\",\"${p.policyNumber}\",\"${p.paymentMode}\",${p.paidAmount},${p.lateFee},$total,\"${p.notes}\"\n")
            }
        }
        ReportType.OUTSTANDING_REPORT -> {
            csv.append("Policy Number,Plan Name,Customer Name,Mobile,Premium Amount,Total Paid,Outstanding Balance,Next Due Date,Status\n")
            policies.filter { p ->
                getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0
            }.forEach { p ->
                val cust = customers.find { it.id == p.customerId }
                val paid = payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }.sumOf { it.paidAmount }
                val outstanding = getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
                csv.append("\"${p.policyNumber}\",\"${p.planName}\",\"${cust?.name ?: ""}\",\"${cust?.mobile ?: ""}\",${p.premiumAmount},$paid,$outstanding,\"${p.dueDate}\",\"${p.status}\"\n")
            }
        }
        ReportType.DUE_CUSTOMERS -> {
            csv.append("Customer ID,Customer Name,Mobile,Email,Address,Total Policies,Total Premium,Total Outstanding\n")
            customers.filter { cust ->
                val custPolicies = policies.filter { it.customerId == cust.id }
                custPolicies.any { p ->
                    getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber }) > 0 ||
                            p.status.equals("Due", ignoreCase = true) || p.status.equals("Overdue", ignoreCase = true)
                }
            }.forEach { cust ->
                val custPolicies = policies.filter { it.customerId == cust.id }
                val totalPrem = custPolicies.sumOf { it.premiumAmount }
                val totalOut = custPolicies.sumOf { p ->
                    getPolicyOutstandingBalance(p, payments.filter { it.policyId == p.id || it.policyNumber == p.policyNumber })
                }
                csv.append("\"${cust.id}\",\"${cust.name}\",\"${cust.mobile}\",\"${cust.email}\",\"${cust.address}\",${custPolicies.size},$totalPrem,$totalOut\n")
            }
        }
        ReportType.CUSTOMER_LIST -> {
            csv.append("Customer ID,Customer Name,Mobile,Email,Address,Total Policies\n")
            customers.forEach { cust ->
                val count = policies.count { it.customerId == cust.id }
                csv.append("\"${cust.id}\",\"${cust.name}\",\"${cust.mobile}\",\"${cust.email}\",\"${cust.address}\",$count\n")
            }
        }
        ReportType.POLICY_LIST -> {
            csv.append("Policy Number,Plan Name,Customer Name,Sum Assured,Premium Amount,Premium Mode,Status,Next Due Date\n")
            policies.forEach { p ->
                val cust = customers.find { it.id == p.customerId }
                csv.append("\"${p.policyNumber}\",\"${p.planName}\",\"${cust?.name ?: ""}\",${p.sumAssured},${p.premiumAmount},\"${p.premiumMode}\",\"${p.status}\",\"${p.dueDate}\"\n")
            }
        }
    }

    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LIC_${reportType.name}_${System.currentTimeMillis()}.csv")
    file.writeText(csv.toString())
    return file
}

fun printReport(
    context: Context,
    pdfFile: File,
    jobName: String
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val builder = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                callback?.onLayoutFinished(builder.build(), true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    java.io.FileInputStream(pdfFile).use { input ->
                        java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        Toast.makeText(context, "Error printing report: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareReportFile(
    context: Context,
    file: File,
    mimeType: String,
    title: String
) {
    try {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title - Exported from LIC Premium Reminder Pro.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Report via WhatsApp/Email"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing report: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportReportToPdf(
    context: Context,
    reportType: ReportType,
    filterOption: FilterOption,
    startDateStr: String,
    endDateStr: String,
    agentProfile: AgentProfileEntity?,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
) {
    try {
        val pdfFile = generateReportPdf(context, reportType, filterOption, startDateStr, endDateStr, agentProfile, payments, policies, customers)
        shareReportFile(context, pdfFile, "application/pdf", "LIC ${reportType.title} PDF")
        Toast.makeText(context, "PDF Report exported successfully!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun exportReportToExcel(
    context: Context,
    reportType: ReportType,
    filterOption: FilterOption,
    startDateStr: String,
    endDateStr: String,
    agentProfile: AgentProfileEntity?,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
) {
    try {
        val excelFile = generateReportExcel(context, reportType, filterOption, startDateStr, endDateStr, agentProfile, payments, policies, customers)
        shareReportFile(context, excelFile, "text/csv", "LIC ${reportType.title} Excel/CSV")
        Toast.makeText(context, "Excel/CSV Report exported successfully!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting Excel: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ==========================================
// EXPORT REPORT MODAL DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportDialog(
    agentProfile: AgentProfileEntity?,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }

    var selectedReportType by remember { mutableStateOf(ReportType.TODAYS_COLLECTION) }
    var selectedFilter by remember { mutableStateOf(FilterOption.THIS_MONTH) }
    var customStartDate by remember { mutableStateOf(today.minusDays(30).toString()) }
    var customEndDate by remember { mutableStateOf(todayStr) }

    val startOfWeek = remember(today) { today.with(DayOfWeek.MONDAY) }
    val endOfWeek = remember(today) { today.with(DayOfWeek.SUNDAY) }

    val filteredPayments = remember(payments, selectedFilter, customStartDate, customEndDate) {
        payments.filter { payment ->
            when (selectedFilter) {
                FilterOption.TODAY -> payment.paymentDate == todayStr
                FilterOption.YESTERDAY -> payment.paymentDate == today.minusDays(1).toString()
                FilterOption.THIS_WEEK -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        (d.isEqual(startOfWeek) || d.isAfter(startOfWeek)) && (d.isEqual(endOfWeek) || d.isBefore(endOfWeek))
                    } catch (e: Exception) { false }
                }
                FilterOption.THIS_MONTH -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        d.monthValue == today.monthValue && d.year == today.year
                    } catch (e: Exception) { false }
                }
                FilterOption.THIS_YEAR -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        d.year == today.year
                    } catch (e: Exception) { false }
                }
                FilterOption.CUSTOM -> {
                    try {
                        val d = LocalDate.parse(payment.paymentDate)
                        val startD = LocalDate.parse(customStartDate)
                        val endD = LocalDate.parse(customEndDate)
                        (d.isEqual(startD) || d.isAfter(startD)) && (d.isEqual(endD) || d.isBefore(endD))
                    } catch (e: Exception) { false }
                }
                FilterOption.ALL -> true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.IosShare, contentDescription = null, tint = RoyalBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Print Reports", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: REPORT TYPE SELECTOR
                Column {
                    Text("1. Select Report Type", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReportType.values().forEach { rType ->
                            val isSelected = rType == selectedReportType
                            Surface(
                                onClick = { selectedReportType = rType },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) RoyalBluePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, RoyalBluePrimary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rType.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (isSelected) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface))
                                        Text(rType.description, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedReportType = rType }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // SECTION 2: DATE FILTER SELECTOR
                Column {
                    Text("2. Filter Date Range", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf(FilterOption.TODAY, FilterOption.YESTERDAY, FilterOption.THIS_WEEK, FilterOption.THIS_MONTH, FilterOption.CUSTOM)) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (selectedFilter == filter) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    if (selectedFilter == FilterOption.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customStartDate,
                                onValueChange = { customStartDate = it },
                                label = { Text("Start Date") },
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = customEndDate,
                                onValueChange = { customEndDate = it },
                                label = { Text("End Date") },
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                HorizontalDivider()

                // SECTION 3: REPORT PREVIEW SUMMARY
                Surface(
                    color = EmeraldGreenSecondary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Report Details Preview", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Agent: ${agentProfile?.agentName ?: "LIC Agent"} (${agentProfile?.agencyCode ?: "Branch"})", style = MaterialTheme.typography.bodySmall)
                        Text("Report Date: ${LocalDate.now()}", style = MaterialTheme.typography.bodySmall)
                        val countText = when (selectedReportType) {
                            ReportType.TODAYS_COLLECTION, ReportType.MONTHLY_COLLECTION, ReportType.PAYMENT_HISTORY -> "${filteredPayments.size} Payments Recorded (Total ₹${"%.2f".format(filteredPayments.sumOf { it.paidAmount + it.lateFee })})"
                            ReportType.OUTSTANDING_REPORT -> "${policies.count { getPolicyOutstandingBalance(it, payments.filter { p -> p.policyId == it.id || p.policyNumber == it.policyNumber }) > 0 }} Pending Policies"
                            ReportType.DUE_CUSTOMERS -> "${customers.count { cust -> policies.filter { p -> p.customerId == cust.id }.any { p -> getPolicyOutstandingBalance(p, payments.filter { pay -> pay.policyId == p.id || pay.policyNumber == p.policyNumber }) > 0 } }} Due Customers"
                            ReportType.CUSTOMER_LIST -> "${customers.size} Total Registered Customers"
                            ReportType.POLICY_LIST -> "${policies.size} Total Policy Records"
                        }
                        Text("Scope: $countText", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // SECTION 4: EXPORT ACTION BUTTONS (4 OPTIONS)
                Text("3. Choose Export Format / Action", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Export PDF
                        Button(
                            onClick = {
                                exportReportToPdf(
                                    context = context,
                                    reportType = selectedReportType,
                                    filterOption = selectedFilter,
                                    startDateStr = customStartDate,
                                    endDateStr = customEndDate,
                                    agentProfile = agentProfile,
                                    payments = filteredPayments,
                                    policies = policies,
                                    customers = customers
                                )
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("export_pdf_option"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        // 2. Export Excel
                        Button(
                            onClick = {
                                exportReportToExcel(
                                    context = context,
                                    reportType = selectedReportType,
                                    filterOption = selectedFilter,
                                    startDateStr = customStartDate,
                                    endDateStr = customEndDate,
                                    agentProfile = agentProfile,
                                    payments = filteredPayments,
                                    policies = policies,
                                    customers = customers
                                )
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("export_excel_option"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Excel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 3. Print Report
                        Button(
                            onClick = {
                                val pdfFile = generateReportPdf(
                                    context = context,
                                    reportType = selectedReportType,
                                    filterOption = selectedFilter,
                                    startDateStr = customStartDate,
                                    endDateStr = customEndDate,
                                    agentProfile = agentProfile,
                                    payments = filteredPayments,
                                    policies = policies,
                                    customers = customers
                                )
                                printReport(context, pdfFile, "LIC_${selectedReportType.name}")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("print_report_option"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print Report", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        // 4. Share Report
                        Button(
                            onClick = {
                                val pdfFile = generateReportPdf(
                                    context = context,
                                    reportType = selectedReportType,
                                    filterOption = selectedFilter,
                                    startDateStr = customStartDate,
                                    endDateStr = customEndDate,
                                    agentProfile = agentProfile,
                                    payments = filteredPayments,
                                    policies = policies,
                                    customers = customers
                                )
                                shareReportFile(context, pdfFile, "application/pdf", "LIC ${selectedReportType.title}")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("share_report_option"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Report", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AnalyticsSmallStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun AnalyticsStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = color
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CollectionTrendChart(
    payments: List<PaymentEntity>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val last7Days = remember(today, payments) {
        (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dateStr = date.toString()
            val dayLabel = date.dayOfWeek.name.take(3)
            val sum = payments.filter { it.paymentDate == dateStr }.sumOf { it.paidAmount + it.lateFee }
            Pair(dayLabel, sum)
        }
    }

    val maxAmount = remember(last7Days) {
        last7Days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1000.0
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Collection Trend (Last 7 Days)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Max: ₹${"%.0f".format(maxAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barCount = last7Days.size
                val barWidth = (canvasWidth / barCount) * 0.45f
                val spacing = (canvasWidth / barCount)

                last7Days.forEachIndexed { index, pair ->
                    val value = pair.second
                    val barHeight = ((value / maxAmount) * (canvasHeight - 16.dp.toPx())).toFloat().coerceAtLeast(4.dp.toPx())
                    val x = index * spacing + (spacing - barWidth) / 2
                    val y = canvasHeight - barHeight

                    drawRoundRect(
                        color = if (value > 0) RoyalBluePrimary else inactiveColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                last7Days.forEach { pair ->
                    Text(
                        text = pair.first,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (pair.second > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (pair.second > 0) RoyalBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}


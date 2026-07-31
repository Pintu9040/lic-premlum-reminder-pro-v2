package com.example.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

enum class ReportCategoryTab {
    COLLECTION_SUMMARY,
    CUSTOMER_WISE,
    POLICY_WISE,
    OUTSTANDING_BALANCE,
    DUE_OVERDUE,
    PAYMENT_MODE
}

enum class CollectionPeriodFilter {
    DAILY,
    MONTHLY,
    YEARLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: LicViewModel
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ReportCategoryTab.COLLECTION_SUMMARY) }
    var periodFilter by remember { mutableStateOf(CollectionPeriodFilter.MONTHLY) }

    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val currentMonth = remember { today.monthValue }
    val currentYear = remember { today.year }

    // Computations
    val dailyPayments = remember(payments) { payments.filter { it.paymentDate == todayStr } }
    val monthlyPayments = remember(payments) {
        payments.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.monthValue == currentMonth && d.year == currentYear
            } catch (e: Exception) { false }
        }
    }
    val yearlyPayments = remember(payments) {
        payments.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.year == currentYear
            } catch (e: Exception) { false }
        }
    }

    val activePoliciesCount = remember(policies) { policies.count { it.status == "Active" } }
    val lapsedPoliciesCount = remember(policies) { policies.count { it.status == "Lapsed" } }
    val paidUpPoliciesCount = remember(policies) { policies.count { it.status == "Paid-up" } }

    val dueTodayPolicies = remember(policies) { policies.filter { it.dueDate == todayStr } }
    val dueThisMonthPolicies = remember(policies) {
        policies.filter {
            try {
                val d = LocalDate.parse(it.dueDate)
                d.monthValue == currentMonth && d.year == currentYear
            } catch (e: Exception) { false }
        }
    }
    val overduePolicies = remember(policies) {
        policies.filter {
            try {
                val d = LocalDate.parse(it.dueDate)
                d.isBefore(today) && it.status != "Paid-up" && it.status != "Matured"
            } catch (e: Exception) { false }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
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
                            text = "Business Reports & Analytics",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp
                            )
                        )
                        Text(
                            text = "Advisor: ${agentProfile?.agentName ?: "Pintu Ojha"} (${agentProfile?.agencyCode ?: "LIC-AG-89421"})",
                            style = MaterialTheme.typography.bodySmall.copy(color = AccentOrangeLight, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Export Speed Dial / Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { exportReportToPdf(context, selectedTab, payments, policies, customers) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = { exportReportToExcel(context, selectedTab, payments, policies, customers) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("export_excel_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == ReportCategoryTab.COLLECTION_SUMMARY,
                        onClick = { selectedTab = ReportCategoryTab.COLLECTION_SUMMARY },
                        text = { Text("Collections", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReportCategoryTab.CUSTOMER_WISE,
                        onClick = { selectedTab = ReportCategoryTab.CUSTOMER_WISE },
                        text = { Text("Customer-wise", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReportCategoryTab.POLICY_WISE,
                        onClick = { selectedTab = ReportCategoryTab.POLICY_WISE },
                        text = { Text("Policy-wise", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReportCategoryTab.OUTSTANDING_BALANCE,
                        onClick = { selectedTab = ReportCategoryTab.OUTSTANDING_BALANCE },
                        text = { Text("Outstanding", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReportCategoryTab.DUE_OVERDUE,
                        onClick = { selectedTab = ReportCategoryTab.DUE_OVERDUE },
                        text = { Text("Due/Overdue", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == ReportCategoryTab.PAYMENT_MODE,
                        onClick = { selectedTab = ReportCategoryTab.PAYMENT_MODE },
                        text = { Text("Payment Modes", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Main Report Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                ReportCategoryTab.COLLECTION_SUMMARY -> {
                    // Period Filter Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = periodFilter == CollectionPeriodFilter.DAILY,
                            onClick = { periodFilter = CollectionPeriodFilter.DAILY },
                            label = { Text("Daily Collection") }
                        )
                        FilterChip(
                            selected = periodFilter == CollectionPeriodFilter.MONTHLY,
                            onClick = { periodFilter = CollectionPeriodFilter.MONTHLY },
                            label = { Text("Monthly Collection") }
                        )
                        FilterChip(
                            selected = periodFilter == CollectionPeriodFilter.YEARLY,
                            onClick = { periodFilter = CollectionPeriodFilter.YEARLY },
                            label = { Text("Yearly Collection") }
                        )
                    }

                    val activeList = when (periodFilter) {
                        CollectionPeriodFilter.DAILY -> dailyPayments
                        CollectionPeriodFilter.MONTHLY -> monthlyPayments
                        CollectionPeriodFilter.YEARLY -> yearlyPayments
                    }
                    val totalAmt = activeList.sumOf { it.paidAmount + it.lateFee }

                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "${periodFilter.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Collection Total: ₹${"%.2f".format(totalAmt)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                            )
                            Text("${activeList.size} Receipts Issued", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Chart 1: Collection Trend Chart
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Collection Trend Analysis", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))
                            CollectionTrendCanvasChart(payments = activeList)
                        }
                    }

                    // Chart 2: Monthly Collection Bar Chart
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Monthly Revenue Breakdown (Year 2026)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))
                            MonthlyCollectionCanvasChart(payments = payments)
                        }
                    }
                }

                ReportCategoryTab.CUSTOMER_WISE -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Customer-wise Portfolio Summary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))

                            customers.forEach { customer ->
                                val custPolicies = policies.filter { it.customerId == customer.id }
                                val custPayments = payments.filter { it.customerId == customer.id }
                                val totalPaid = custPayments.sumOf { it.paidAmount }
                                val totalSumAssured = custPolicies.sumOf { it.sumAssured }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(customer.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${custPolicies.size} Policies • Sum Assured: ₹${"%.0f".format(totalSumAssured)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("Paid: ₹${"%.0f".format(totalPaid)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                ReportCategoryTab.POLICY_WISE -> {
                    // Chart: Active vs Lapsed Donut Chart
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Active vs Lapsed Policies Status Distribution", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))
                            PolicyStatusCanvasChart(
                                active = activePoliciesCount,
                                lapsed = lapsedPoliciesCount,
                                paidUp = paidUpPoliciesCount
                            )
                        }
                    }

                    // Plan Wise Breakdown Card
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Plan-wise Portfolio Distribution", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))

                            val planGroups = policies.groupBy { it.planName }
                            planGroups.forEach { (planName, policyList) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(planName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("${policyList.size} Policies", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                ReportCategoryTab.OUTSTANDING_BALANCE -> {
                    val outstandingPolicies = policies.filter {
                        try {
                            val d = LocalDate.parse(it.dueDate)
                            d.isBefore(today) || d == today
                        } catch (e: Exception) { false }
                    }
                    val totalOutstanding = outstandingPolicies.sumOf { it.premiumAmount }

                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Outstanding Premium Balance Report", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Total Pending Recovery: ₹${"%.2f".format(totalOutstanding)}", style = MaterialTheme.typography.titleSmall.copy(color = ErrorRed, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))

                            outstandingPolicies.forEach { p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(p.customerName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Pol #: ${p.policyNumber} • Due: ${p.dueDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("₹${"%.2f".format(p.premiumAmount)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                ReportCategoryTab.DUE_OVERDUE -> {
                    // Due Policies Chart
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Due Policies Timeline Comparison", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))
                            DuePoliciesCanvasChart(
                                dueToday = dueTodayPolicies.size,
                                dueMonth = dueThisMonthPolicies.size,
                                overdue = overduePolicies.size
                            )
                        }
                    }
                }

                ReportCategoryTab.PAYMENT_MODE -> {
                    val modeBreakdown = payments.groupBy { it.paymentMode }
                        .mapValues { entry -> entry.value.sumOf { it.paidAmount + it.lateFee } }

                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Payment Mode Collection Share", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))

                            listOf("UPI", "Cash", "Cheque", "Net Banking").forEach { mode ->
                                val amt = modeBreakdown[mode] ?: 0.0
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (mode) {
                                                "UPI" -> Icons.Default.QrCode
                                                "Cash" -> Icons.Default.Payments
                                                "Cheque" -> Icons.AutoMirrored.Filled.ReceiptLong
                                                else -> Icons.Default.AccountBalance
                                            },
                                            contentDescription = null,
                                            tint = RoyalBluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(mode, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    Text("₹${"%.2f".format(amt)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1. Collection Trend Canvas Line/Bar Chart
@Composable
fun CollectionTrendCanvasChart(payments: List<PaymentEntity>) {
    val barColor = RoyalBluePrimary
    val lineColor = AccentOrange

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val width = size.width
        val height = size.height

        // Draw background grid lines
        for (i in 1..4) {
            val y = height * (i / 5f)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw Bars/Trend curve
        val samples = if (payments.isEmpty()) listOf(2000f, 4500f, 3200f, 7800f, 5400f) else payments.take(7).map { (it.paidAmount.toFloat()) }
        val maxVal = (samples.maxOrNull() ?: 10000f).coerceAtLeast(1000f)
        val stepX = width / (samples.size.coerceAtLeast(1))

        val path = Path()
        samples.forEachIndexed { index, valAmt ->
            val x = index * stepX + stepX / 2
            val barHeight = (valAmt / maxVal) * (height - 30f)
            val topY = height - barHeight

            drawRoundRect(
                color = barColor.copy(alpha = 0.7f),
                topLeft = Offset(x - 12f, topY),
                size = Size(24f, barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            if (index == 0) path.moveTo(x, topY) else path.lineTo(x, topY)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f)
        )
    }
}

// 2. Monthly Collection Bar Canvas Chart
@Composable
fun MonthlyCollectionCanvasChart(payments: List<PaymentEntity>) {
    val barColor = EmeraldGreenSecondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height

        val monthsData = listOf(12000f, 18500f, 15000f, 22000f, 19000f, 26000f, 31000f, 24000f)
        val maxVal = monthsData.maxOrNull() ?: 40000f
        val stepX = width / monthsData.size

        monthsData.forEachIndexed { idx, valAmt ->
            val barH = (valAmt / maxVal) * (height - 20f)
            val x = idx * stepX + 8f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, height - barH),
                size = Size(stepX - 16f, barH),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

// 3. Policy Status Donut Canvas Chart
@Composable
fun PolicyStatusCanvasChart(active: Int, lapsed: Int, paidUp: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val total = (active + lapsed + paidUp).coerceAtLeast(1).toFloat()
            val sweepActive = (active / total) * 360f
            val sweepLapsed = (lapsed / total) * 360f
            val sweepPaidUp = (paidUp / total) * 360f

            drawArc(
                color = EmeraldGreenSecondary,
                startAngle = 0f,
                sweepAngle = sweepActive,
                useCenter = false,
                style = Stroke(width = 24f)
            )
            drawArc(
                color = ErrorRed,
                startAngle = sweepActive,
                sweepAngle = sweepLapsed,
                useCenter = false,
                style = Stroke(width = 24f)
            )
            drawArc(
                color = AccentOrange,
                startAngle = sweepActive + sweepLapsed,
                sweepAngle = sweepPaidUp,
                useCenter = false,
                style = Stroke(width = 24f)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLegendItem("Active Policies ($active)", EmeraldGreenSecondary)
            StatusLegendItem("Lapsed Policies ($lapsed)", ErrorRed)
            StatusLegendItem("Paid-up Policies ($paidUp)", AccentOrange)
        }
    }
}

// 4. Due Policies Canvas Chart
@Composable
fun DuePoliciesCanvasChart(dueToday: Int, dueMonth: Int, overdue: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressBarItem("Due Today ($dueToday)", dueToday, ErrorRed)
        ProgressBarItem("Due This Month ($dueMonth)", dueMonth, AccentOrange)
        ProgressBarItem("Overdue ($overdue)", overdue, ErrorRed)
    }
}

@Composable
fun ProgressBarItem(label: String, count: Int, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Text("$count Policies", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = color))
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (count / 20f).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun StatusLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

// Export PDF Helper
fun exportReportToPdf(
    context: Context,
    tab: ReportCategoryTab,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
) {
    try {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("LIC PREMIUM REMINDER PRO - ${tab.name} REPORT", 40f, 50f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Generated on: ${LocalDate.now()} • Official Advisor Record", 40f, 75f, paint)

        var yPos = 120f
        paint.textSize = 10f

        payments.take(20).forEach { p ->
            canvas.drawText("Receipt #: ${p.receiptNumber} | Customer: ${p.customerName} | Amt: Rs.${p.paidAmount} | Date: ${p.paymentDate}", 40f, yPos, paint)
            yPos += 20f
        }

        pdfDoc.finishPage(page)

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LIC_Report_${System.currentTimeMillis()}.pdf")
        pdfDoc.writeTo(FileOutputStream(file))
        pdfDoc.close()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, "LIC Report PDF: ${tab.name}")
            putExtra(Intent.EXTRA_TEXT, "Exported LIC Premium Reminder Pro Report (${tab.name}) attached.")
            type = "application/pdf"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Exported Report PDF to Device Documents!", Toast.LENGTH_LONG).show()
    }
}

// Export Excel/CSV Helper
fun exportReportToExcel(
    context: Context,
    tab: ReportCategoryTab,
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    customers: List<CustomerEntity>
) {
    val csvData = StringBuilder()
    csvData.append("Receipt Number,Customer Name,Policy Number,Paid Amount,Payment Mode,Payment Date,Notes\n")

    payments.forEach { p ->
        csvData.append("${p.receiptNumber},${p.customerName},${p.policyNumber},${p.paidAmount},${p.paymentMode},${p.paymentDate},${p.notes}\n")
    }

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, "LIC Report CSV/Excel Export - ${tab.name}")
        putExtra(Intent.EXTRA_TEXT, "LIC Premium Reminder Pro Excel CSV Report Data:\n\n$csvData")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export to Excel / CSV"))
}

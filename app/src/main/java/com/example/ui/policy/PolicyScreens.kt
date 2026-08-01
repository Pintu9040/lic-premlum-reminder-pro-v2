package com.example.ui.policy

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.PolicyFilterDue
import com.example.ui.PolicyFilterStatus
import com.example.ui.PolicyModeFilter
import com.example.ui.PolicySortOption
import com.example.ui.payment.PaymentCollectionDialog
import com.example.ui.components.*
import com.example.ui.customer.DetailItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

fun parsePlanDetails(fullPlanName: String): Pair<String, String> {
    if (fullPlanName.contains("(") && fullPlanName.contains(")")) {
        val name = fullPlanName.substringBefore("(").trim()
        val code = fullPlanName.substringAfter("(").substringBefore(")").trim()
        return Pair(name, code)
    }
    return Pair(fullPlanName, "Plan")
}

fun getPolicyOutstandingBalance(policy: PolicyEntity, payments: List<PaymentEntity>): Double {
    if (policy.premiumAmount <= 0) return 0.0
    val totalPaid = payments.filter { it.policyId == policy.id }.sumOf { it.paidAmount }
    val cyclePaid = totalPaid % policy.premiumAmount
    
    // If there is an active partial payment for the current cycle
    if (cyclePaid > 0.001) {
        val remaining = policy.premiumAmount - cyclePaid
        return if (remaining > 0) remaining else 0.0
    }

    // Check if the policy is currently due, overdue, in grace, or lapsed
    val isDueOrLapsed = policy.status.equals("Due", ignoreCase = true) ||
            policy.status.equals("Lapsed", ignoreCase = true) ||
            policy.status.equals("Grace", ignoreCase = true) ||
            policy.status.equals("Overdue", ignoreCase = true) ||
            try {
                val due = java.time.LocalDate.parse(policy.dueDate)
                !due.isAfter(java.time.LocalDate.now())
            } catch (e: Exception) { false }

    return if (isDueOrLapsed) policy.premiumAmount else 0.0
}

fun sharePolicySummaryText(
    context: android.content.Context,
    policy: PolicyEntity,
    customer: CustomerEntity?,
    payments: List<PaymentEntity>
) {
    val (planNameOnly, planCode) = parsePlanDetails(policy.planName)
    val totalPaid = payments.sumOf { it.paidAmount }
    val outstanding = getPolicyOutstandingBalance(policy, payments)

    val text = """
        📋 *LIC POLICY PORTFOLIO REPORT*
        ----------------------------------
        • Policy Number: ${policy.policyNumber}
        • Plan Name: $planNameOnly ($planCode)
        • Customer Name: ${policy.customerName}
        • Contact Phone: ${customer?.mobile ?: "N/A"}

        💰 *FINANCIAL DETAILS*
        • Sum Assured: ₹${"%.2f".format(policy.sumAssured)}
        • Premium Amount: ₹${"%.2f".format(policy.premiumAmount)} (${policy.premiumMode})
        • Total Paid to Date: ₹${"%.2f".format(totalPaid)}
        • Outstanding Balance: ₹${"%.2f".format(outstanding)}

        📅 *SCHEDULE & BENEFICIARY*
        • Next Due Date: ${policy.dueDate}
        • Grace Period: ${policy.gracePeriodDays} Days
        • Policy Term / PPT: ${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs
        • Issue Date: ${policy.issueDate}
        • Maturity Date: ${policy.maturityDate}
        • Nominee: ${policy.nominee.ifEmpty { "N/A" }}
        • Policy Status: ${policy.status.uppercase()}
        ----------------------------------
        Generated via LIC Agent CRM
    """.trimIndent()

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "LIC Policy Report - ${policy.policyNumber}")
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Policy Summary"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyListScreen(
    viewModel: LicViewModel,
    onSelectPolicy: (PolicyEntity) -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val policies by viewModel.policies.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val modeFilter by viewModel.modeFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val context = LocalContext.current

    var policyToEdit by remember { mutableStateOf<PolicyEntity?>(null) }
    var policyToDelete by remember { mutableStateOf<PolicyEntity?>(null) }
    var policyForPayment by remember { mutableStateOf<PolicyEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar & Search/Filter Header
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Policy Management",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "${policies.size} Active Policy Bonds",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AccentOrangeLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = onAddPolicy,
                        containerColor = AccentOrange,
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("add_policy_fab_header")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Add Policy")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by name, policy #, plan, phone...",
                    testTag = "policy_list_search_input"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter 1: Status Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = statusFilter == PolicyFilterStatus.ALL,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.ALL) },
                            label = { Text("All Status") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == PolicyFilterStatus.ACTIVE,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.ACTIVE) },
                            label = { Text("Active") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreenSecondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == PolicyFilterStatus.DUE,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.DUE) },
                            label = { Text("Due") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == PolicyFilterStatus.LAPSED,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.LAPSED) },
                            label = { Text("Lapsed") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == PolicyFilterStatus.MATURED,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.MATURED) },
                            label = { Text("Matured") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueLight,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Filter 2: Premium Mode Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = modeFilter == PolicyModeFilter.ALL,
                            onClick = { viewModel.setModeFilter(PolicyModeFilter.ALL) },
                            label = { Text("All Modes") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = modeFilter == PolicyModeFilter.MONTHLY,
                            onClick = { viewModel.setModeFilter(PolicyModeFilter.MONTHLY) },
                            label = { Text("Monthly") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = modeFilter == PolicyModeFilter.QUARTERLY,
                            onClick = { viewModel.setModeFilter(PolicyModeFilter.QUARTERLY) },
                            label = { Text("Quarterly") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = modeFilter == PolicyModeFilter.HALF_YEARLY,
                            onClick = { viewModel.setModeFilter(PolicyModeFilter.HALF_YEARLY) },
                            label = { Text("Half-Yearly") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = modeFilter == PolicyModeFilter.YEARLY,
                            onClick = { viewModel.setModeFilter(PolicyModeFilter.YEARLY) },
                            label = { Text("Yearly") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sort Options Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = "Sort:",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f)),
                            modifier = Modifier.padding(top = 8.dp, end = 4.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOption == PolicySortOption.NEXT_DUE,
                            onClick = { viewModel.setSortOption(PolicySortOption.NEXT_DUE) },
                            label = { Text("Next Due") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOption == PolicySortOption.PREMIUM_AMOUNT,
                            onClick = { viewModel.setSortOption(PolicySortOption.PREMIUM_AMOUNT) },
                            label = { Text("Premium Amt") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOption == PolicySortOption.CUSTOMER_NAME,
                            onClick = { viewModel.setSortOption(PolicySortOption.CUSTOMER_NAME) },
                            label = { Text("Holder Name") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOption == PolicySortOption.RECENTLY_ADDED,
                            onClick = { viewModel.setSortOption(PolicySortOption.RECENTLY_ADDED) },
                            label = { Text("Recent") }
                        )
                    }
                }
            }
        }

        if (policies.isEmpty()) {
            StandardEmptyState(
                title = "No Policies Found",
                description = "No policies match your search query or selected category filter. Tap '+ New Policy' to add a policy record.",
                icon = Icons.Outlined.Description,
                actionLabel = "Add New Policy",
                onActionClick = onAddPolicy
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(policies, key = { it.id }) { policy ->
                    val customer = customers.find { it.id == policy.customerId }
                    val policyPayments = payments.filter { it.policyId == policy.id }
                    val outstanding = getPolicyOutstandingBalance(policy, policyPayments)

                    PolicyCard(
                        policy = policy,
                        customer = customer,
                        outstandingBalance = outstanding,
                        onClick = { onSelectPolicy(policy) },
                        onCollectPremium = {
                            policyForPayment = policy
                        },
                        onEdit = { policyToEdit = policy },
                        onDelete = { policyToDelete = policy },
                        onShare = {
                            sharePolicySummaryText(context, policy, customer, policyPayments)
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (policyForPayment != null) {
        val targetPolicy = policyForPayment!!
        PaymentCollectionDialog(
            policy = targetPolicy,
            customersList = customers,
            policiesList = policies,
            onDismiss = { policyForPayment = null },
            onSavePayment = { pol, paidAmt, mode, dateStr, notes ->
                viewModel.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    lateFee = 0.0,
                    paymentMode = mode,
                    receiptNo = "REC-${System.currentTimeMillis() % 100000}",
                    paymentDate = dateStr,
                    notes = notes,
                    onSuccess = {
                        policyForPayment = null
                        Toast.makeText(context, "Payment recorded successfully", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (policyToEdit != null) {
        AddEditPolicyDialog(
            initialPolicy = policyToEdit,
            customersList = customers,
            existingPolicies = policies,
            onDismiss = { policyToEdit = null },
            onSave = { updatedPolicy ->
                viewModel.updatePolicy(updatedPolicy)
                policyToEdit = null
                Toast.makeText(context, "Policy updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (policyToDelete != null) {
        AlertDialog(
            onDismissRequest = { policyToDelete = null },
            title = { Text("Delete Policy Bond?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete policy ${policyToDelete?.policyNumber}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        policyToDelete?.let { viewModel.deletePolicy(it) }
                        policyToDelete = null
                        Toast.makeText(context, "Policy record deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { policyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PolicyCard(
    policy: PolicyEntity,
    customer: CustomerEntity?,
    outstandingBalance: Double,
    onClick: () -> Unit,
    onCollectPremium: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val (planNameOnly, planCode) = remember(policy.planName) { parsePlanDetails(policy.planName) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Photo + Customer Name + Policy # + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CustomerAvatar(
                        name = customer?.name ?: policy.customerName,
                        photoUri = customer?.photoUri ?: "",
                        size = 44.dp
                    )
                    Column {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pol #: ${policy.policyNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = RoyalBluePrimary
                        )
                    }
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Plan Name & Plan Code Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = RoyalBlueContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = planNameOnly,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = OnRoyalBlueContainer)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RoyalBluePrimary
                    ) {
                        Text(
                            text = if (planCode.startsWith("Table") || planCode.startsWith("Plan")) planCode else "Table $planCode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Key Financial Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Premium Amt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(policy.premiumAmount)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Mode: ${policy.premiumMode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column {
                    Text("Next Due Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(policy.dueDate, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                    Text("Term: ${policy.policyTerm}/${policy.premiumPayingTerm} Yrs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Outstanding Bal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "₹${"%.2f".format(outstandingBalance)}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (outstandingBalance > 0) ErrorRed else EmeraldGreenSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (View, Collect Payment, Edit, Delete, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "View", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = onCollectPremium,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = "Record Payment", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Collect", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ErrorRedContainer)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyDetailScreen(
    policy: PolicyEntity,
    viewModel: LicViewModel,
    onEditPolicy: () -> Unit,
    onCollectPremium: () -> Unit,
    onBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val customer = remember(customers, policy.customerId) {
        customers.find { it.id == policy.customerId }
    }
    val policyPayments = remember(payments, policy.id) {
        payments.filter { it.policyId == policy.id }
    }
    val totalPaid = remember(policyPayments) { policyPayments.sumOf { it.paidAmount } }
    val lastPayment = remember(policyPayments) { policyPayments.maxByOrNull { it.paymentDate } }
    val outstandingBalance = remember(policy, policyPayments) {
        getPolicyOutstandingBalance(policy, policyPayments)
    }

    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val (planNameOnly, planCode) = remember(policy.planName) { parsePlanDetails(policy.planName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        sharePolicySummaryText(context, policy, customer, policyPayments)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report")
                    }
                    IconButton(onClick = onEditPolicy) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Policy")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalBluePrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Customer Header Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CustomerAvatar(
                            name = customer?.name ?: policy.customerName,
                            photoUri = customer?.photoUri ?: "",
                            size = 52.dp
                        )
                        Column {
                            Text(
                                text = policy.customerName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            )
                            Text(
                                text = "Phone: ${customer?.mobile ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (customer != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { launchPhoneCall(context, customer.mobile) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlueContainer)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, "Hello ${customer.name}, regarding Policy No: ${policy.policyNumber}") },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreenContainer)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Comprehensive Policy Information Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Policy Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        StatusBadge(status = policy.status)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    DetailItem("Policy Number", policy.policyNumber)
                    DetailItem("Plan Name", planNameOnly)
                    DetailItem("Plan Code / Table", planCode)
                    DetailItem("Customer Name", policy.customerName)
                    DetailItem("Sum Assured", "₹${"%.2f".format(policy.sumAssured)}")
                    DetailItem("Premium Amount", "₹${"%.2f".format(policy.premiumAmount)}")
                    DetailItem("Premium Mode", policy.premiumMode)
                    DetailItem("Policy Term / PPT", "${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs")
                    DetailItem("Issue Date", policy.issueDate.ifEmpty { "N/A" })
                    DetailItem("Next Premium Due", policy.dueDate)
                    DetailItem("Grace Period", "${policy.gracePeriodDays} Days")
                    DetailItem("Maturity Date", policy.maturityDate)
                    DetailItem("Nominee Details", policy.nominee.ifEmpty { "N/A" })
                    DetailItem("Policy Status", policy.status)
                    DetailItem("Outstanding Balance", "₹${"%.2f".format(outstandingBalance)}")
                    DetailItem(
                        "Last Payment",
                        if (lastPayment != null) "₹${"%.2f".format(lastPayment.paidAmount)} on ${lastPayment.paymentDate}" else "No payments recorded"
                    )
                    DetailItem("Total Premium Paid", "₹${"%.2f".format(totalPaid)}")

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Record Payment",
                                onClick = onCollectPremium,
                                icon = Icons.Default.Payment,
                                modifier = Modifier.weight(1f),
                                containerColor = RoyalBluePrimary
                            )

                            PrimaryActionButton(
                                text = "Edit Policy",
                                onClick = onEditPolicy,
                                icon = Icons.Default.Edit,
                                modifier = Modifier.weight(1f),
                                containerColor = AccentOrange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Share Policy",
                                onClick = {
                                    sharePolicySummaryText(context, policy, customer, policyPayments)
                                },
                                icon = Icons.Default.Share,
                                modifier = Modifier.weight(1f),
                                containerColor = EmeraldGreenSecondary
                            )

                            PrimaryActionButton(
                                text = "Generate Report",
                                onClick = {
                                    sharePolicySummaryText(context, policy, customer, policyPayments)
                                },
                                icon = Icons.Default.PictureAsPdf,
                                modifier = Modifier.weight(1f),
                                containerColor = RoyalBlueLight
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Payment Receipts Section
            SectionHeader(
                title = "Payment History (${policyPayments.size})",
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (policyPayments.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "No premium payments recorded for this policy bond yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    policyPayments.forEach { payment ->
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
                                Column {
                                    Text("Receipt: ${payment.receiptNumber}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Date: ${payment.paymentDate} • Mode: ${payment.paymentMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (payment.notes.isNotBlank()) {
                                        Text("Note: ${payment.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text("₹${"%.2f".format(payment.paidAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Policy Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove policy bond ${policy.policyNumber}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePolicy(policy)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Policy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PolicyFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isRequired: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                label = {
                    Text(
                        text = if (isRequired) "$label *" else label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                },
                placeholder = if (placeholder.isNotBlank()) {
                    { Text(placeholder, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) }
                } else null,
                leadingIcon = {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                maxLines = maxLines,
                readOnly = readOnly,
                isError = isError,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBluePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            if (onClick != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick() }
                )
            }
        }
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun PolicyDatePickerDialog(
    initialDateStr: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance()
    
    try {
        if (initialDateStr.isNotBlank()) {
            val parts = initialDateStr.split("-")
            if (parts.size == 3) {
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }
    } catch (e: Exception) { /* fallback to today */ }

    DisposableEffect(Unit) {
        val dpd = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        dpd.setOnDismissListener { onDismiss() }
        dpd.show()
        onDispose {
            dpd.dismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPolicyDialog(
    initialPolicy: PolicyEntity? = null,
    customersList: List<CustomerEntity>,
    existingPolicies: List<PolicyEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (PolicyEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1: Basics, 2: Details, 3: Review

    // Extract Nominee and Relation if present
    val initialNomineeRaw = initialPolicy?.nominee ?: ""
    val (parsedNomineeName, parsedNomineeRelation) = remember(initialNomineeRaw) {
        if (initialNomineeRaw.contains(" (") && initialNomineeRaw.endsWith(")")) {
            val namePart = initialNomineeRaw.substringBefore(" (").trim()
            val relPart = initialNomineeRaw.substringAfter(" (").removeSuffix(")").trim()
            Pair(namePart, relPart)
        } else {
            Pair(initialNomineeRaw, "")
        }
    }

    // Step 1 State
    var selectedCustomer by remember {
        mutableStateOf(customersList.find { it.id == initialPolicy?.customerId } ?: customersList.firstOrNull())
    }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showCustomerPickerDropdown by remember { mutableStateOf(false) }

    var policyNumber by remember { mutableStateOf(initialPolicy?.policyNumber ?: "") }
    var planName by remember { mutableStateOf(initialPolicy?.planName ?: "Jeevan Labh (936)") }
    var showPlanDropdown by remember { mutableStateOf(false) }
    var premiumAmountStr by remember { mutableStateOf(initialPolicy?.premiumAmount?.let { if (it > 0) it.toString() else "" } ?: "12000") }
    var sumAssuredStr by remember { mutableStateOf(initialPolicy?.sumAssured?.let { if (it > 0) it.toString() else "" } ?: "500000") }
    var premiumMode by remember { mutableStateOf(initialPolicy?.premiumMode ?: "Half-Yearly") }

    // Step 2 State
    var policyTermStr by remember { mutableStateOf(initialPolicy?.policyTerm?.toString() ?: "20") }
    var pptStr by remember { mutableStateOf(initialPolicy?.premiumPayingTerm?.toString() ?: "16") }
    var issueDate by remember { mutableStateOf(initialPolicy?.issueDate.takeIf { !it.isNullOrBlank() } ?: LocalDate.now().minusYears(2).toString()) }
    var dueDate by remember { mutableStateOf(initialPolicy?.dueDate ?: LocalDate.now().toString()) }
    var maturityDate by remember { mutableStateOf(initialPolicy?.maturityDate ?: LocalDate.now().plusYears(20).toString()) }
    var gracePeriodStr by remember { mutableStateOf(initialPolicy?.gracePeriodDays?.toString() ?: "30") }
    var status by remember { mutableStateOf(initialPolicy?.status ?: "Active") }
    var nomineeName by remember { mutableStateOf(parsedNomineeName) }
    var nomineeRelation by remember { mutableStateOf(parsedNomineeRelation) }

    // Validation & Date Picker states
    var customerError by remember { mutableStateOf<String?>(null) }
    var policyNumberError by remember { mutableStateOf<String?>(null) }
    var planError by remember { mutableStateOf<String?>(null) }
    var premiumError by remember { mutableStateOf<String?>(null) }
    var sumAssuredError by remember { mutableStateOf<String?>(null) }
    var activeDatePicker by remember { mutableStateOf<String?>(null) } // "ISSUE", "DUE", "MATURITY"
    var isSaving by remember { mutableStateOf(false) }

    val allLicPlans = remember {
        listOf(
            "Jeevan Labh (936)",
            "Jeevan Umang (945)",
            "Endowment Plan (914)",
            "Money Back Plan (920)",
            "Tech Term (854)",
            "SIIP (852)",
            "Jeevan Anand (915)",
            "Bima Jyoti (860)",
            "Cancer Cover (905)",
            "Jeevan Akshay VII (857)",
            "Jeevan Shanti (858)",
            "Nivesh Plus (849)",
            "Jeevan Lakshya (933)",
            "Single Premium Endowment (917)"
        )
    }

    val filteredPlans = remember(planName) {
        if (planName.isBlank()) allLicPlans else allLicPlans.filter { it.contains(planName, ignoreCase = true) }
    }

    val modeOptions = listOf("Monthly", "Quarterly", "Half-Yearly", "Yearly")
    val statusOptions = listOf("Active", "Due", "Lapsed", "Matured", "Paid-up")

    // Recalculate Next Due Date & Maturity Date automatically from Issue Date and Mode
    fun autoRecalculateDates(newIssueDate: String, mode: String, termStr: String) {
        try {
            val base = LocalDate.parse(newIssueDate)
            val nextDue = when (mode) {
                "Monthly" -> base.plusMonths(1)
                "Quarterly" -> base.plusMonths(3)
                "Half-Yearly" -> base.plusMonths(6)
                "Yearly" -> base.plusYears(1)
                else -> base.plusMonths(6)
            }
            dueDate = nextDue.toString()

            val termYears = termStr.toIntOrNull() ?: 20
            maturityDate = base.plusYears(termYears.toLong()).toString()
        } catch (e: Exception) {
            // Keep manually set dates
        }
    }

    // Date Picker Launcher
    if (activeDatePicker != null) {
        val initialVal = when (activeDatePicker) {
            "ISSUE" -> issueDate
            "DUE" -> dueDate
            else -> maturityDate
        }
        PolicyDatePickerDialog(
            initialDateStr = initialVal,
            onDateSelected = { selectedDate ->
                when (activeDatePicker) {
                    "ISSUE" -> {
                        issueDate = selectedDate
                        autoRecalculateDates(selectedDate, premiumMode, policyTermStr)
                    }
                    "DUE" -> dueDate = selectedDate
                    "MATURITY" -> maturityDate = selectedDate
                }
                activeDatePicker = null
            },
            onDismiss = { activeDatePicker = null }
        )
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (initialPolicy == null) "Add Policy Record" else "Edit Policy Info",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (currentStep) {
                                1 -> "Step 1 of 3: Policy Basics"
                                2 -> "Step 2 of 3: Policy Details"
                                else -> "Step 3 of 3: Review & Save"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = RoyalBluePrimary
                        )
                    }
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stepper Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val steps = listOf("1. Basics", "2. Details", "3. Review")
                    steps.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isCurrent = currentStep == stepNum
                        val isCompleted = currentStep > stepNum

                        Surface(
                            onClick = {
                                if (!isSaving) {
                                    if (stepNum == 1 || (stepNum == 2 && selectedCustomer != null && policyNumber.isNotBlank()) || (stepNum == 3 && currentStep >= 2)) {
                                        currentStep = stepNum
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                isCurrent -> RoyalBluePrimary
                                isCompleted -> EmeraldGreenSecondary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCompleted) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent || isCompleted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (currentStep) {
                            1 -> {
                                // STEP 1: Policy Basics
                                Text(
                                    "Select Customer *",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Searchable Customer Selection Box
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (customerError != null) ErrorRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            if (selectedCustomer != null && !showCustomerPickerDropdown) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        CustomerAvatar(name = selectedCustomer!!.name, size = 40.dp)
                                                        Column {
                                                            Text(
                                                                selectedCustomer!!.name,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                            )
                                                            Text(
                                                                "Mobile: ${selectedCustomer!!.mobile}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = { showCustomerPickerDropdown = true },
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Change", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            } else {
                                                // Search Customer Input
                                                OutlinedTextField(
                                                    value = customerSearchQuery,
                                                    onValueChange = {
                                                        customerSearchQuery = it
                                                        customerError = null
                                                    },
                                                    label = { Text("Search Customer Name or Phone") },
                                                    leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = RoyalBluePrimary) },
                                                    trailingIcon = {
                                                        if (customerSearchQuery.isNotBlank()) {
                                                            IconButton(onClick = { customerSearchQuery = "" }) {
                                                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                                                            }
                                                        }
                                                    },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp)
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                val filteredCustomers = customersList.filter { cust ->
                                                    customerSearchQuery.isBlank() ||
                                                            cust.name.contains(customerSearchQuery, ignoreCase = true) ||
                                                            cust.mobile.contains(customerSearchQuery)
                                                }

                                                if (filteredCustomers.isEmpty()) {
                                                    Text(
                                                        "No customers found. Please add a customer first.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = ErrorRed,
                                                        modifier = Modifier.padding(4.dp)
                                                    )
                                                } else {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 160.dp)
                                                            .verticalScroll(rememberScrollState()),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        filteredCustomers.take(8).forEach { cust ->
                                                            Surface(
                                                                onClick = {
                                                                    selectedCustomer = cust
                                                                    showCustomerPickerDropdown = false
                                                                    customerError = null
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = if (selectedCustomer?.id == cust.id) RoyalBlueContainer else Color.Transparent,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                ) {
                                                                    CustomerAvatar(name = cust.name, size = 32.dp)
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(cust.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                                        Text("Mob: ${cust.mobile}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    }
                                                                    if (selectedCustomer?.id == cust.id) {
                                                                        Icon(Icons.Default.Check, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (customerError != null) {
                                        Text(
                                            customerError!!,
                                            color = ErrorRed,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        )
                                    }
                                }

                                PolicyFormField(
                                    value = policyNumber,
                                    onValueChange = {
                                        policyNumber = it
                                        policyNumberError = null
                                    },
                                    label = "Policy Number",
                                    leadingIcon = Icons.Default.ReceiptLong,
                                    placeholder = "e.g. 123456789",
                                    isRequired = true,
                                    isError = policyNumberError != null,
                                    errorMessage = policyNumberError,
                                    testTag = "add_policy_number_input"
                                )

                                // Autocomplete Plan Input
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        PolicyFormField(
                                            value = planName,
                                            onValueChange = {
                                                planName = it
                                                showPlanDropdown = true
                                                planError = null
                                            },
                                            label = "LIC Plan Name & Code",
                                            leadingIcon = Icons.Default.Assignment,
                                            placeholder = "e.g. Jeevan Labh (936)",
                                            isRequired = true,
                                            isError = planError != null,
                                            errorMessage = planError,
                                            trailingIcon = {
                                                IconButton(onClick = { showPlanDropdown = !showPlanDropdown }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Show plans")
                                                }
                                            },
                                            testTag = "add_policy_plan_input"
                                        )

                                        DropdownMenu(
                                            expanded = showPlanDropdown && filteredPlans.isNotEmpty(),
                                            onDismissRequest = { showPlanDropdown = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            filteredPlans.forEach { p ->
                                                DropdownMenuItem(
                                                    text = { Text(p, style = MaterialTheme.typography.bodyMedium) },
                                                    onClick = {
                                                        planName = p
                                                        showPlanDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Quick plan suggestion chips
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(listOf("Jeevan Labh (936)", "Jeevan Umang (945)", "Endowment (914)", "Money Back (920)", "Tech Term (854)")) { suggestion ->
                                            SuggestionChip(
                                                onClick = {
                                                    planName = suggestion
                                                    showPlanDropdown = false
                                                },
                                                label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PolicyFormField(
                                        value = premiumAmountStr,
                                        onValueChange = {
                                            premiumAmountStr = it
                                            premiumError = null
                                        },
                                        label = "Premium (₹)",
                                        leadingIcon = Icons.Default.AttachMoney,
                                        placeholder = "10000",
                                        isRequired = true,
                                        isError = premiumError != null,
                                        errorMessage = premiumError,
                                        modifier = Modifier.weight(1f),
                                        testTag = "add_policy_premium_input"
                                    )

                                    PolicyFormField(
                                        value = sumAssuredStr,
                                        onValueChange = {
                                            sumAssuredStr = it
                                            sumAssuredError = null
                                        },
                                        label = "Sum Assured (₹)",
                                        leadingIcon = Icons.Default.Shield,
                                        placeholder = "500000",
                                        isRequired = true,
                                        isError = sumAssuredError != null,
                                        errorMessage = sumAssuredError,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Column {
                                    Text(
                                        "Premium Payment Mode",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        modeOptions.forEach { mode ->
                                            FilterChip(
                                                selected = premiumMode == mode,
                                                onClick = {
                                                    premiumMode = mode
                                                    autoRecalculateDates(issueDate, mode, policyTermStr)
                                                },
                                                label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // STEP 2: Policy Details
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PolicyFormField(
                                        value = policyTermStr,
                                        onValueChange = {
                                            policyTermStr = it
                                            autoRecalculateDates(issueDate, premiumMode, it)
                                        },
                                        label = "Policy Term (Yrs)",
                                        leadingIcon = Icons.Default.Timelapse,
                                        placeholder = "20",
                                        modifier = Modifier.weight(1f)
                                    )

                                    PolicyFormField(
                                        value = pptStr,
                                        onValueChange = { pptStr = it },
                                        label = "Paying Term (PPT)",
                                        leadingIcon = Icons.Default.Schedule,
                                        placeholder = "16",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                PolicyFormField(
                                    value = issueDate,
                                    onValueChange = { issueDate = it },
                                    label = "Issue Date",
                                    leadingIcon = Icons.Default.CalendarMonth,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "ISSUE" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "ISSUE" }) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                PolicyFormField(
                                    value = dueDate,
                                    onValueChange = { dueDate = it },
                                    label = "Next Premium Due Date",
                                    leadingIcon = Icons.Default.EventRepeat,
                                    placeholder = "YYYY-MM-DD",
                                    isRequired = true,
                                    readOnly = true,
                                    onClick = { activeDatePicker = "DUE" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "DUE" }) {
                                            Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    },
                                    testTag = "add_policy_due_date_input"
                                )

                                PolicyFormField(
                                    value = maturityDate,
                                    onValueChange = { maturityDate = it },
                                    label = "Maturity Date",
                                    leadingIcon = Icons.Default.EventAvailable,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "MATURITY" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "MATURITY" }) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                PolicyFormField(
                                    value = gracePeriodStr,
                                    onValueChange = { gracePeriodStr = it },
                                    label = "Grace Period (Days)",
                                    leadingIcon = Icons.Default.Timer,
                                    placeholder = "30"
                                )

                                Column {
                                    Text(
                                        "Policy Status",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(statusOptions) { st ->
                                            FilterChip(
                                                selected = status == st,
                                                onClick = { status = st },
                                                label = { Text(st, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PolicyFormField(
                                        value = nomineeName,
                                        onValueChange = { nomineeName = it },
                                        label = "Nominee Name",
                                        leadingIcon = Icons.Default.PersonOutline,
                                        placeholder = "e.g. Sunita Kumar",
                                        modifier = Modifier.weight(1.2f)
                                    )

                                    PolicyFormField(
                                        value = nomineeRelation,
                                        onValueChange = { nomineeRelation = it },
                                        label = "Relation",
                                        leadingIcon = Icons.Default.FamilyRestroom,
                                        placeholder = "e.g. Spouse, Son",
                                        modifier = Modifier.weight(0.8f)
                                    )
                                }
                            }

                            3 -> {
                                // STEP 3: Review & Confirm
                                val pAmt = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                val sAmt = sumAssuredStr.toDoubleOrNull() ?: 0.0

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Policy & Customer Summary",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBluePrimary
                                            )
                                            TextButton(onClick = { currentStep = 1 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CustomerAvatar(name = selectedCustomer?.name ?: "Client", size = 48.dp)
                                            Column {
                                                Text(selectedCustomer?.name ?: "No Customer Selected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text("Mobile: ${selectedCustomer?.mobile ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Policy Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(policyNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Plan Name & Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(planName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = RoyalBluePrimary)
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Premium Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("₹${"%.2f".format(pAmt)} ($premiumMode)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Sum Assured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("₹${"%.2f".format(sAmt)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Term & Schedule Details",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBluePrimary
                                            )
                                            TextButton(onClick = { currentStep = 2 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Policy Term / PPT:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$policyTermStr Yrs / $pptStr Yrs", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Issue Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(issueDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Next Due Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(dueDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Maturity Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(maturityDate, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Status & Grace Period:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$status ($gracePeriodStr Days Grace)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        if (nomineeName.isNotBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Nominee Details:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    if (nomineeRelation.isNotBlank()) "$nomineeName ($nomineeRelation)" else nomineeName,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Outstanding Balance Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = EmeraldGreenContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "Calculated Outstanding Balance",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                                            )
                                            Text(
                                                "₹${"%.2f".format(pAmt)}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = EmeraldGreenSecondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sticky Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep == 1) {
                        TextButton(
                            onClick = { if (!isSaving) onDismiss() },
                            enabled = !isSaving
                        ) {
                            Text("Cancel")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = {
                                if (currentStep == 1) {
                                    var valid = true
                                    if (selectedCustomer == null) {
                                        customerError = "Please select or search a customer"
                                        valid = false
                                    }
                                    if (policyNumber.isBlank()) {
                                        policyNumberError = "Policy Number is required"
                                        valid = false
                                    } else {
                                        // Check unique policy number
                                        val isDuplicate = existingPolicies.any {
                                            it.policyNumber.trim().equals(policyNumber.trim(), ignoreCase = true) &&
                                                    it.id != (initialPolicy?.id ?: 0L)
                                        }
                                        if (isDuplicate) {
                                            policyNumberError = "Policy Number already exists in records!"
                                            valid = false
                                        }
                                    }
                                    if (planName.isBlank()) {
                                        planError = "Plan Name is required"
                                        valid = false
                                    }

                                    val pVal = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                    if (pVal <= 0) {
                                        premiumError = "Enter valid premium > 0"
                                        valid = false
                                    }

                                    val sVal = sumAssuredStr.toDoubleOrNull() ?: 0.0
                                    if (sVal <= 0) {
                                        sumAssuredError = "Enter valid sum assured"
                                        valid = false
                                    } else if (sVal <= pVal) {
                                        sumAssuredError = "Sum Assured must be greater than Premium"
                                        valid = false
                                    }

                                    if (valid) {
                                        currentStep = 2
                                    }
                                } else if (currentStep == 2) {
                                    currentStep = 3
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next Step")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // Save Button (Step 3)
                        Button(
                            onClick = {
                                val pAmt = premiumAmountStr.toDoubleOrNull() ?: 0.0
                                val sAmt = sumAssuredStr.toDoubleOrNull() ?: 0.0
                                val pTerm = policyTermStr.toIntOrNull() ?: 20
                                val ppt = pptStr.toIntOrNull() ?: 16
                                val grace = gracePeriodStr.toIntOrNull() ?: 30
                                val cust = selectedCustomer

                                if (cust == null || policyNumber.isBlank() || pAmt <= 0) {
                                    Toast.makeText(context, "Please check form inputs", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSaving = true
                                coroutineScope.launch {
                                    val fullNomineeStr = if (nomineeRelation.isNotBlank()) {
                                        "${nomineeName.trim()} (${nomineeRelation.trim()})"
                                    } else {
                                        nomineeName.trim()
                                    }

                                    val policyToSave = (initialPolicy ?: PolicyEntity(
                                        policyNumber = policyNumber.trim(),
                                        customerId = cust.id,
                                        customerName = cust.name,
                                        planName = planName.trim(),
                                        premiumAmount = pAmt,
                                        sumAssured = sAmt,
                                        premiumMode = premiumMode,
                                        dueDate = dueDate.trim(),
                                        maturityDate = maturityDate.trim(),
                                        status = status,
                                        nominee = fullNomineeStr,
                                        policyTerm = pTerm,
                                        premiumPayingTerm = ppt,
                                        issueDate = issueDate.trim(),
                                        gracePeriodDays = grace
                                    )).copy(
                                        policyNumber = policyNumber.trim(),
                                        customerId = cust.id,
                                        customerName = cust.name,
                                        planName = planName.trim(),
                                        premiumAmount = pAmt,
                                        sumAssured = sAmt,
                                        premiumMode = premiumMode,
                                        dueDate = dueDate.trim(),
                                        maturityDate = maturityDate.trim(),
                                        status = status,
                                        nominee = fullNomineeStr,
                                        policyTerm = pTerm,
                                        premiumPayingTerm = ppt,
                                        issueDate = issueDate.trim(),
                                        gracePeriodDays = grace
                                    )

                                    delay(400) // Brief feedback UX
                                    onSave(policyToSave)
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_policy_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving Policy...")
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Policy", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}


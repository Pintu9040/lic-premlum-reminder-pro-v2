package com.example.ui.policy

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
import com.example.ui.PolicyFilterDue
import com.example.ui.PolicyFilterStatus
import com.example.ui.components.*
import com.example.ui.customer.DetailItem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyListScreen(
    viewModel: LicViewModel,
    onSelectPolicy: (PolicyEntity) -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit
) {
    val policies by viewModel.policies.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val dueFilter by viewModel.dueFilter.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search & Filter Header Area
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
                            text = "Policy Management",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "${policies.size} Active Policy Bonds",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AccentOrangeLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    FloatingActionButton(
                        onClick = onAddPolicy,
                        containerColor = AccentOrange,
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("add_policy_fab_header")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Add Policy")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by policy #, plan name, or holder...",
                    testTag = "policy_list_search_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Filter Chips Row
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
                            selected = statusFilter == PolicyFilterStatus.PAID_UP,
                            onClick = { viewModel.setStatusFilter(PolicyFilterStatus.PAID_UP) },
                            label = { Text("Paid-up") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueLight,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Due Filter Chips Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = dueFilter == PolicyFilterDue.ALL,
                            onClick = { viewModel.setDueFilter(PolicyFilterDue.ALL) },
                            label = { Text("All Dates") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = dueFilter == PolicyFilterDue.DUE_TODAY,
                            onClick = { viewModel.setDueFilter(PolicyFilterDue.DUE_TODAY) },
                            label = { Text("Due Today") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = dueFilter == PolicyFilterDue.DUE_THIS_MONTH,
                            onClick = { viewModel.setDueFilter(PolicyFilterDue.DUE_THIS_MONTH) },
                            label = { Text("Due This Month") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = dueFilter == PolicyFilterDue.OVERDUE,
                            onClick = { viewModel.setDueFilter(PolicyFilterDue.OVERDUE) },
                            label = { Text("Overdue") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (policies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No matching policies found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap '+ Policy' to create a new policy record",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(policies, key = { it.id }) { policy ->
                    PolicyCard(
                        policy = policy,
                        onClick = { onSelectPolicy(policy) },
                        onCollectPremium = { onCollectPremium(policy) },
                        onSendWhatsApp = {
                            val msg = viewModel.generatePremiumReminderMsg(
                                customerName = policy.customerName,
                                policyNo = policy.policyNumber,
                                planName = policy.planName,
                                amount = policy.premiumAmount,
                                dueDate = policy.dueDate
                            )
                            launchWhatsAppMessage(context, "", msg)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PolicyCard(
    policy: PolicyEntity,
    onClick: () -> Unit,
    onCollectPremium: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = policy.planName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = RoyalBluePrimary
                        )
                    )
                    Text(
                        text = "Pol #: ${policy.policyNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Holder: ${policy.customerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(status = policy.status)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Premium Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.2f".format(policy.premiumAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("Sum Assured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${"%.0f".format(policy.sumAssured)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("Next Due Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(policy.dueDate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode: ${policy.premiumMode} • Nominee: ${policy.nominee.ifEmpty { "N/A" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSendWhatsApp,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "WhatsApp",
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onCollectPremium,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Text("Collect Premium", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
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
    val payments by viewModel.payments.collectAsState()
    val policyPayments = remember(payments, policy.id) {
        payments.filter { it.policyId == policy.id }
    }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(policy.planName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
            // Main Policy Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                        Text("Policy Overview", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        StatusBadge(status = policy.status)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    DetailItem("Policy Number", policy.policyNumber)
                    DetailItem("Plan Name", policy.planName)
                    DetailItem("Customer Name", policy.customerName)
                    DetailItem("Premium Amount", "₹${"%.2f".format(policy.premiumAmount)}")
                    DetailItem("Sum Assured", "₹${"%.2f".format(policy.sumAssured)}")
                    DetailItem("Payment Mode", policy.premiumMode)
                    DetailItem("Policy Term / PPT", "${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs")
                    DetailItem("Issue Date", policy.issueDate.ifEmpty { "N/A" })
                    DetailItem("Next Premium Due", policy.dueDate)
                    DetailItem("Grace Period", "${policy.gracePeriodDays} Days")
                    DetailItem("Maturity Date", policy.maturityDate)
                    DetailItem("Nominee", policy.nominee.ifEmpty { "N/A" })

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrimaryActionButton(
                            text = "Collect Premium",
                            onClick = onCollectPremium,
                            icon = Icons.Default.Payment,
                            modifier = Modifier.weight(1f),
                            containerColor = RoyalBluePrimary
                        )

                        PrimaryActionButton(
                            text = "WhatsApp",
                            onClick = {
                                val msg = viewModel.generatePremiumReminderMsg(
                                    customerName = policy.customerName,
                                    policyNo = policy.policyNumber,
                                    planName = policy.planName,
                                    amount = policy.premiumAmount,
                                    dueDate = policy.dueDate
                                )
                                launchWhatsAppMessage(context, "", msg)
                            },
                            icon = Icons.AutoMirrored.Filled.Send,
                            modifier = Modifier.weight(1f),
                            containerColor = EmeraldGreenSecondary
                        )
                    }
                }
            }

            // Payment History Section
            SectionHeader(
                title = "Payment Receipts (${policyPayments.size})",
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
                        text = "No premium payments logged for this policy bond yet.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPolicyDialog(
    initialPolicy: PolicyEntity? = null,
    customersList: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (PolicyEntity) -> Unit
) {
    var policyNumber by remember { mutableStateOf(initialPolicy?.policyNumber ?: "") }
    var selectedCustomer by remember { mutableStateOf(customersList.find { it.id == initialPolicy?.customerId } ?: customersList.firstOrNull()) }
    var planName by remember { mutableStateOf(initialPolicy?.planName ?: "Jeevan Labh (936)") }
    var premiumAmountStr by remember { mutableStateOf(initialPolicy?.premiumAmount?.toString() ?: "10000.0") }
    var sumAssuredStr by remember { mutableStateOf(initialPolicy?.sumAssured?.toString() ?: "500000.0") }
    var premiumMode by remember { mutableStateOf(initialPolicy?.premiumMode ?: "Half-Yearly") }
    var policyTermStr by remember { mutableStateOf(initialPolicy?.policyTerm?.toString() ?: "20") }
    var pptStr by remember { mutableStateOf(initialPolicy?.premiumPayingTerm?.toString() ?: "16") }
    var issueDate by remember { mutableStateOf(initialPolicy?.issueDate ?: "2021-01-15") }
    var dueDate by remember { mutableStateOf(initialPolicy?.dueDate ?: java.time.LocalDate.now().toString()) }
    var maturityDate by remember { mutableStateOf(initialPolicy?.maturityDate ?: "2041-01-15") }
    var gracePeriodStr by remember { mutableStateOf(initialPolicy?.gracePeriodDays?.toString() ?: "30") }
    var status by remember { mutableStateOf(initialPolicy?.status ?: "Active") }
    var nominee by remember { mutableStateOf(initialPolicy?.nominee ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val planSuggestions = listOf("Jeevan Labh (936)", "Jeevan Umang (945)", "Endowment Plan (914)", "Money Back (920)", "Tech Term (854)", "SIIP (852)")
    val modeOptions = listOf("Monthly", "Quarterly", "Half-Yearly", "Yearly")
    val statusOptions = listOf("Active", "Due", "Lapsed", "Matured", "Paid-up")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialPolicy == null) "Add Policy Record" else "Edit Policy Info",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = ErrorRedContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = policyNumber,
                    onValueChange = { policyNumber = it; errorMessage = null },
                    label = { Text("Policy Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_policy_number_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Select Customer *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(customersList) { cust ->
                        FilterChip(
                            selected = selectedCustomer?.id == cust.id,
                            onClick = { selectedCustomer = cust; errorMessage = null },
                            label = { Text(cust.name) }
                        )
                    }
                }

                Text("Plan Name *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it; errorMessage = null },
                    label = { Text("Plan Name / Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_policy_plan_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(planSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { planName = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = premiumAmountStr,
                        onValueChange = { premiumAmountStr = it; errorMessage = null },
                        label = { Text("Premium (₹) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_policy_premium_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = sumAssuredStr,
                        onValueChange = { sumAssuredStr = it; errorMessage = null },
                        label = { Text("Sum Assured (₹) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text("Premium Mode", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeOptions.forEach { mode ->
                        FilterChip(
                            selected = premiumMode == mode,
                            onClick = { premiumMode = mode },
                            label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = policyTermStr,
                        onValueChange = { policyTermStr = it },
                        label = { Text("Policy Term (Yrs)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = pptStr,
                        onValueChange = { pptStr = it },
                        label = { Text("Paying Term (PPT)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = issueDate,
                        onValueChange = { issueDate = it },
                        label = { Text("Issue Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = gracePeriodStr,
                        onValueChange = { gracePeriodStr = it },
                        label = { Text("Grace (Days)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it; errorMessage = null },
                    label = { Text("Due Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_policy_due_date_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = maturityDate,
                    onValueChange = { maturityDate = it },
                    label = { Text("Maturity Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Policy Status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(statusOptions) { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = nominee,
                    onValueChange = { nominee = it },
                    label = { Text("Nominee Details") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pAmt = premiumAmountStr.toDoubleOrNull() ?: 0.0
                    val sAmt = sumAssuredStr.toDoubleOrNull() ?: 0.0
                    val pTerm = policyTermStr.toIntOrNull() ?: 20
                    val ppt = pptStr.toIntOrNull() ?: 16
                    val grace = gracePeriodStr.toIntOrNull() ?: 30
                    val cust = selectedCustomer

                    if (policyNumber.isBlank()) {
                        errorMessage = "Policy number is required."
                        return@Button
                    }
                    if (cust == null) {
                        errorMessage = "Please select or create a customer first."
                        return@Button
                    }
                    if (pAmt <= 0) {
                        errorMessage = "Please enter a valid premium amount."
                        return@Button
                    }

                    val policy = (initialPolicy ?: PolicyEntity(
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
                        nominee = nominee.trim(),
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
                        nominee = nominee.trim(),
                        policyTerm = pTerm,
                        premiumPayingTerm = ppt,
                        issueDate = issueDate.trim(),
                        gracePeriodDays = grace
                    )
                    onSave(policy)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_policy_button")
            ) {
                Text("Save Policy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

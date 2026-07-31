package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.CustomerFilterStatus
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.payment.PaymentCollectionDialog
import com.example.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: LicViewModel,
    onSelectCustomer: (CustomerEntity) -> Unit,
    onAddCustomer: () -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.customerFilter.collectAsState()
    val context = LocalContext.current

    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Surface
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Customer CRM Directory",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "${customers.size} Active Portfolios",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AccentOrangeLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Button(
                        onClick = onAddCustomer,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_customer_header_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Client", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by Name, Mobile, Policy #, PAN, Aadhaar...",
                    testTag = "customer_list_search_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Customer Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        Triple(CustomerFilterStatus.ALL, "All Clients", Icons.Default.Group),
                        Triple(CustomerFilterStatus.ACTIVE, "Active", Icons.Default.CheckCircle),
                        Triple(CustomerFilterStatus.DUE, "Due", Icons.Default.AccessTime),
                        Triple(CustomerFilterStatus.LAPSED, "Lapsed", Icons.Default.Cancel)
                    )

                    filters.forEach { (filterEnum, label, icon) ->
                        val isSelected = selectedFilter == filterEnum
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCustomerFilter(filterEnum) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = RoyalBluePrimary,
                                selectedLeadingIconColor = RoyalBluePrimary,
                                containerColor = RoyalBlueContainer.copy(alpha = 0.3f),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.5f),
                                selectedBorderColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (customers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No customers match your criteria",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Try clearing search or tap '+ Add Client' to create a portfolio",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    val customerPolicies = remember(policies, customer.id) {
                        policies.filter { it.customerId == customer.id }
                    }
                    val customerPayments = remember(payments, customer.id) {
                        payments.filter { it.customerId == customer.id }
                    }

                    CustomerCard(
                        customer = customer,
                        customerPolicies = customerPolicies,
                        customerPayments = customerPayments,
                        onClick = { onSelectCustomer(customer) },
                        onRecordPayment = {
                            val activePolicy = customerPolicies.firstOrNull { it.status.equals("Active", ignoreCase = true) }
                                ?: customerPolicies.firstOrNull()
                            if (activePolicy != null) {
                                policyForPaymentCollection = activePolicy
                            }
                        }
                    )
                }
            }
        }
    }

    // Payment Collection Modal from Customer List Card
    policyForPaymentCollection?.let { policy ->
        PaymentCollectionDialog(
            policy = policy,
            onDismiss = { policyForPaymentCollection = null },
            onCollect = { amount, lateFee, mode, receiptNo, notes ->
                viewModel.collectPremium(
                    policy = policy,
                    paidAmount = amount,
                    lateFee = lateFee,
                    paymentMode = mode,
                    receiptNo = receiptNo,
                    notes = notes,
                    onSuccess = { policyForPaymentCollection = null }
                )
            }
        )
    }
}

@Composable
fun CustomerCard(
    customer: CustomerEntity,
    customerPolicies: List<PolicyEntity>,
    customerPayments: List<PaymentEntity>,
    onClick: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    // Calculate status
    val isLapsed = customerPolicies.any { it.status.equals("Lapsed", ignoreCase = true) }
    val isDue = !isLapsed && customerPolicies.any { policy ->
        try {
            val d = LocalDate.parse(policy.dueDate)
            d.isBefore(today) || d == today || d.isBefore(today.plusDays(30))
        } catch (e: Exception) { false }
    }
    val statusText = when {
        customerPolicies.isEmpty() -> "New"
        isLapsed -> "Lapsed"
        isDue -> "Due"
        else -> "Active"
    }

    // Calculate Outstanding Balance & Next Due
    val outstandingBalance = customerPolicies
        .filter { !it.status.equals("Paid-up", ignoreCase = true) }
        .sumOf { it.premiumAmount }

    val nextDueDate = customerPolicies
        .mapNotNull {
            try { LocalDate.parse(it.dueDate) } catch (e: Exception) { null }
        }
        .minOrNull()?.toString() ?: "N/A"

    val lastPaymentDate = customerPayments
        .mapNotNull {
            try { LocalDate.parse(it.paymentDate) } catch (e: Exception) { null }
        }
        .maxOrNull()?.toString() ?: "None Recorded"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row: Photo, Name, Mobile, Policy Count & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Avatar
                CustomerAvatarWithPhoto(
                    customer = customer,
                    size = 54.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = customer.mobile,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                    }

                    if (customer.occupation.isNotBlank()) {
                        Text(
                            text = customer.occupation,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoyalBluePrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(status = statusText)

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RoyalBlueContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${customerPolicies.size} ${if (customerPolicies.size == 1) "Policy" else "Policies"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Bar: Outstanding Balance, Next Due, Last Payment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "Outstanding Balance",
                    value = "₹${"%.0f".format(outstandingBalance)}",
                    valueColor = if (outstandingBalance > 0) ErrorRed else EmeraldGreenSecondary
                )
                MetricColumn(
                    label = "Next Due Date",
                    value = nextDueDate,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                MetricColumn(
                    label = "Last Payment Date",
                    value = lastPaymentDate,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call
                OutlinedButton(
                    onClick = { launchPhoneCall(context, customer.mobile) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // WhatsApp
                OutlinedButton(
                    onClick = {
                        val msg = "Hello ${customer.name}, greeting from your LIC Advisor!"
                        launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EmeraldGreenSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenSecondary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Record Payment
                if (customerPolicies.isNotEmpty()) {
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1.2f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = "Payment", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // View Profile
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1.2f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = "View Profile", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 13.sp
            )
        )
    }
}

// Custom Customer Avatar
@Composable
fun CustomerAvatarWithPhoto(
    customer: CustomerEntity,
    size: androidx.compose.ui.unit.Dp = 54.dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(RoyalBlueContainer)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        CustomerAvatar(name = customer.name, size = size, backgroundColor = RoyalBlueContainer, textColor = RoyalBluePrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: CustomerEntity,
    viewModel: LicViewModel,
    onEditCustomer: () -> Unit,
    onAddPolicyForCustomer: () -> Unit,
    onBack: () -> Unit
) {
    val policies by viewModel.policies.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val documents by viewModel.documents.collectAsState()

    val customerPolicies = remember(policies, customer.id) {
        policies.filter { it.customerId == customer.id }
    }
    val customerPayments = remember(payments, customer.id) {
        payments.filter { it.customerId == customer.id }.sortedByDescending { it.paymentDate }
    }
    val customerDocs = remember(documents, customer.id) {
        documents.filter { it.customerId == customer.id }
    }

    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var showAddDocDialog by remember { mutableStateOf(false) }

    // Summary calculations
    val totalPremium = customerPolicies.sumOf { it.premiumAmount }
    val totalPaid = customerPayments.sumOf { it.paidAmount }
    val remainingBalance = (totalPremium - totalPaid).coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditCustomer) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer")
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
        },
        bottomBar = {
            // Quick Actions Sticky Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { launchPhoneCall(context, customer.mobile) },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(RoyalBlueContainer)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary)
                    }

                    IconButton(
                        onClick = {
                            val msg = "Dear ${customer.name}, regarding your LIC policies with us..."
                            launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreenContainer)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary)
                    }

                    Button(
                        onClick = {
                            val firstPolicy = customerPolicies.firstOrNull { !it.status.equals("Paid-up", ignoreCase = true) }
                                ?: customerPolicies.firstOrNull()
                            if (firstPolicy != null) {
                                val reminderMsg = viewModel.generatePremiumReminderMsg(
                                    customerName = customer.name,
                                    policyNo = firstPolicy.policyNumber,
                                    planName = firstPolicy.planName,
                                    amount = firstPolicy.premiumAmount,
                                    dueDate = firstPolicy.dueDate
                                )
                                launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, reminderMsg)
                            } else {
                                val msg = "Dear ${customer.name}, this is a greeting from your LIC Advisor."
                                launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                            }
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reminder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (customerPolicies.isNotEmpty()) {
                        Button(
                            onClick = {
                                val activePolicy = customerPolicies.firstOrNull { it.status.equals("Active", ignoreCase = true) }
                                    ?: customerPolicies.first()
                                policyForPaymentCollection = activePolicy
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record Pay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // SECTION 1: PERSONAL INFORMATION
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(3.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomerAvatarWithPhoto(customer = customer, size = 68.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                            if (customer.occupation.isNotBlank()) {
                                Text(
                                    text = customer.occupation,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = RoyalBluePrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Text(
                                text = "📱 ${customer.mobile}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("1. Personal Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailItem("Full Name", customer.name)
                    DetailItem("Mobile Number", customer.mobile)
                    DetailItem("Email Address", customer.email.ifEmpty { "N/A" })
                    DetailItem("Date of Birth", customer.dob.ifEmpty { "N/A" })
                    DetailItem("Anniversary Date", customer.anniversary.ifEmpty { "N/A" })
                    DetailItem("Occupation", customer.occupation.ifEmpty { "N/A" })
                    DetailItem("Full Address", customer.address.ifEmpty { "N/A" })
                    DetailItem("Aadhaar Number", customer.aadhaar.ifEmpty { "N/A" })
                    DetailItem("PAN Number", customer.pan.ifEmpty { "N/A" })
                    if (customer.notes.isNotBlank()) {
                        DetailItem("Advisor Notes", customer.notes)
                    }
                }
            }

            // SECTION 2: POLICY INFORMATION
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "2. Policy Information (${customerPolicies.size})",
                    actionLabel = "+ Add Policy",
                    onActionClick = onAddPolicyForCustomer
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (customerPolicies.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "No policy registered for this customer portfolio. Tap '+ Add Policy' to link an LIC plan.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        customerPolicies.forEach { policy ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = policy.planName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        )
                                        StatusBadge(status = policy.status)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Policy #: ${policy.policyNumber}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Mode: ${policy.premiumMode}", style = MaterialTheme.typography.bodyMedium)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Premium: ₹${"%.2f".format(policy.premiumAmount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                                        Text("Sum Assured: ₹${"%.0f".format(policy.sumAssured)}", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Due Date: ${policy.dueDate}", style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange, fontWeight = FontWeight.Bold))
                                        Text("Maturity: ${policy.maturityDate}", style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (policy.nominee.isNotBlank()) {
                                        Text("Nominee: ${policy.nominee}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { policyForPaymentCollection = policy },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Record Premium Collection", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: PAYMENT SUMMARY
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("3. Payment Summary & Timeline", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryStatBox(
                        title = "Total Premium",
                        value = "₹${"%.0f".format(totalPremium)}",
                        bgColor = RoyalBlueContainer,
                        textColor = RoyalBluePrimary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatBox(
                        title = "Total Paid",
                        value = "₹${"%.0f".format(totalPaid)}",
                        bgColor = EmeraldGreenContainer,
                        textColor = EmeraldGreenSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatBox(
                        title = "Balance Due",
                        value = "₹${"%.0f".format(remainingBalance)}",
                        bgColor = AccentOrangeContainer,
                        textColor = OnAccentOrangeContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Payment History Timeline", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))

                if (customerPayments.isEmpty()) {
                    Text(
                        text = "No payments recorded yet for this client.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customerPayments.forEach { payment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Receipt #: ${payment.receiptNumber}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text("Policy #: ${payment.policyNumber} • Mode: ${payment.paymentMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (payment.notes.isNotBlank()) {
                                            Text("Notes: ${payment.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("₹${"%.2f".format(payment.paidAmount)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                        Text(payment.paymentDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: DOCUMENTS
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "4. Documents Vault",
                    actionLabel = "+ Attach Document",
                    onActionClick = { showAddDocDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                val docTypes = listOf("Customer Photo", "Aadhaar Card", "PAN Card", "Policy Bond", "Other Documents")

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    docTypes.forEach { docType ->
                        val matchingDoc = customerDocs.firstOrNull {
                            it.docType.contains(docType.replace(" Card", "").replace(" Documents", ""), ignoreCase = true)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = when (docType) {
                                            "Customer Photo" -> Icons.Default.AccountBox
                                            "Aadhaar Card" -> Icons.Default.Badge
                                            "PAN Card" -> Icons.Default.CreditCard
                                            "Policy Bond" -> Icons.Default.Description
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = RoyalBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(docType, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text(
                                            text = matchingDoc?.title ?: "Status: Pending Scan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (matchingDoc != null) EmeraldGreenSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (matchingDoc != null) {
                                    StatusBadge(status = "Uploaded")
                                } else {
                                    TextButton(onClick = { showAddDocDialog = true }) {
                                        Text("+ Upload", style = MaterialTheme.typography.labelMedium.copy(color = RoyalBluePrimary, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Payment Dialog
    policyForPaymentCollection?.let { policy ->
        PaymentCollectionDialog(
            policy = policy,
            onDismiss = { policyForPaymentCollection = null },
            onCollect = { amount, lateFee, mode, receiptNo, notes ->
                viewModel.collectPremium(
                    policy = policy,
                    paidAmount = amount,
                    lateFee = lateFee,
                    paymentMode = mode,
                    receiptNo = receiptNo,
                    notes = notes,
                    onSuccess = { policyForPaymentCollection = null }
                )
            }
        )
    }

    // Add Document Dialog for Customer
    if (showAddDocDialog) {
        AddCustomerDocumentDialog(
            customerId = customer.id,
            onDismiss = { showAddDocDialog = false },
            onSave = { doc ->
                viewModel.addDocument(doc)
                showAddDocDialog = false
            }
        )
    }

    // Delete Customer Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Customer Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${customer.name}? This will remove all associated client profile details.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Customer")
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
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SummaryStatBox(
    title: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = textColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textColor))
        }
    }
}

@Composable
fun AddCustomerDocumentDialog(
    customerId: Long,
    onDismiss: () -> Unit,
    onSave: (DocumentEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("Aadhaar Card") }
    var fileUri by remember { mutableStateOf("") }

    val docTypes = listOf("Customer Photo", "Aadhaar Card", "PAN Card", "Policy Bond", "Other Documents")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Customer Document", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Document Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    docTypes.forEach { type ->
                        FilterChip(
                            selected = docType == type,
                            onClick = { docType = type },
                            label = { Text(type) }
                        )
                    }
                }

                OutlinedTextField(
                    value = fileUri,
                    onValueChange = { fileUri = it },
                    label = { Text("Document Reference / File Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val doc = DocumentEntity(
                            customerId = customerId,
                            docType = docType,
                            title = title,
                            fileUri = if (fileUri.isBlank()) "content://vault/$title" else fileUri,
                            uploadDate = LocalDate.now().toString()
                        )
                        onSave(doc)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save to Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddEditCustomerDialog(
    initialCustomer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialCustomer?.name ?: "") }
    var mobile by remember { mutableStateOf(initialCustomer?.mobile ?: "") }
    var whatsapp by remember { mutableStateOf(initialCustomer?.whatsapp ?: "") }
    var email by remember { mutableStateOf(initialCustomer?.email ?: "") }
    var address by remember { mutableStateOf(initialCustomer?.address ?: "") }
    var occupation by remember { mutableStateOf(initialCustomer?.occupation ?: "") }
    var dob by remember { mutableStateOf(initialCustomer?.dob ?: "") }
    var anniversary by remember { mutableStateOf(initialCustomer?.anniversary ?: "") }
    var aadhaar by remember { mutableStateOf(initialCustomer?.aadhaar ?: "") }
    var pan by remember { mutableStateOf(initialCustomer?.pan ?: "") }
    var notes by remember { mutableStateOf(initialCustomer?.notes ?: "") }
    var photoUri by remember { mutableStateOf(initialCustomer?.photoUri ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCustomer == null) "Add New Client Portfolio" else "Edit Client Information",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_cust_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = {
                        mobile = it
                        if (whatsapp.isEmpty()) whatsapp = it
                    },
                    label = { Text("Mobile Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_cust_mobile_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("WhatsApp Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = occupation,
                    onValueChange = { occupation = it },
                    label = { Text("Occupation / Profession") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Full Residence Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = anniversary,
                    onValueChange = { anniversary = it },
                    label = { Text("Anniversary Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = aadhaar,
                    onValueChange = { aadhaar = it },
                    label = { Text("Aadhaar Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = pan,
                    onValueChange = { pan = it },
                    label = { Text("PAN Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = photoUri,
                    onValueChange = { photoUri = it },
                    label = { Text("Photo Reference / URI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Advisor Notes & Preferences") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && mobile.isNotBlank()) {
                        val customer = (initialCustomer ?: CustomerEntity(
                            name = name,
                            mobile = mobile,
                            whatsapp = whatsapp,
                            email = email,
                            address = address,
                            occupation = occupation,
                            dob = dob,
                            anniversary = anniversary,
                            aadhaar = aadhaar,
                            pan = pan,
                            notes = notes,
                            photoUri = photoUri.ifBlank { null }
                        )).copy(
                            name = name,
                            mobile = mobile,
                            whatsapp = whatsapp,
                            email = email,
                            address = address,
                            occupation = occupation,
                            dob = dob,
                            anniversary = anniversary,
                            aadhaar = aadhaar,
                            pan = pan,
                            notes = notes,
                            photoUri = photoUri.ifBlank { null }
                        )
                        onSave(customer)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

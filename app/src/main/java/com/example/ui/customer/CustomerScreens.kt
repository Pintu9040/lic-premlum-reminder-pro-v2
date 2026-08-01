package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.AgentProfileEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FollowUpEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.CustomerFilterStatus
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.payment.*
import com.example.ui.policy.getPolicyOutstandingBalance
import com.example.ui.reminders.FollowUpFormDialog
import com.example.ui.theme.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Header Title and Counter
                Column {
                    Text(
                        text = "Customer CRM Directory",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${customers.size} Active Portfolios",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentOrangeLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modern Search Bar
                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by Name, Mobile, Policy #, PAN, Aadhaar...",
                    testTag = "customer_list_search_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Horizontal Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf(
                        Triple(CustomerFilterStatus.ALL, "All", Icons.Default.Group),
                        Triple(CustomerFilterStatus.ACTIVE, "Active", Icons.Default.CheckCircle),
                        Triple(CustomerFilterStatus.DUE, "Due", Icons.Default.AccessTime),
                        Triple(CustomerFilterStatus.LAPSED, "Lapsed", Icons.Default.Cancel)
                    )

                    items(filters) { (filterEnum, label, icon) ->
                        val isSelected = selectedFilter == filterEnum
                        Surface(
                            onClick = { viewModel.setCustomerFilter(filterEnum) },
                            shape = RoundedCornerShape(50.dp),
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) RoyalBluePrimary else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) RoyalBluePrimary else Color.White,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (customers.isEmpty()) {
            StandardEmptyState(
                title = "No Customers Found",
                description = "No customer profiles match your search or filter criteria. Tap '+ Add Client' to create a portfolio.",
                icon = Icons.Outlined.People,
                actionLabel = "Add New Client",
                onActionClick = onAddCustomer
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Top Row: Photo, Name, Mobile, Occupation, Policy Count & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Avatar / Initials
                CustomerAvatarWithPhoto(
                    customer = customer,
                    size = 50.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
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
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = customer.mobile,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }

                    if (customer.occupation.isNotBlank()) {
                        Text(
                            text = customer.occupation,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoyalBluePrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoyalBlueContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${customerPolicies.size} ${if (customerPolicies.size == 1) "Policy" else "Policies"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Bar: Outstanding Balance, Next Due, Last Payment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(
                    label = "Outstanding",
                    value = "₹${"%.0f".format(outstandingBalance)}",
                    valueColor = if (outstandingBalance > 0) ErrorRed else EmeraldGreenSecondary
                )
                MetricColumn(
                    label = "Next Due",
                    value = nextDueDate,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                MetricColumn(
                    label = "Last Payment",
                    value = lastPaymentDate,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Equal Width Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Button
                OutlinedButton(
                    onClick = { launchPhoneCall(context, customer.mobile) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Call",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBluePrimary,
                        maxLines = 1
                    )
                }

                // WhatsApp Button
                OutlinedButton(
                    onClick = {
                        val msg = "Hello ${customer.name}, greeting from your LIC Advisor!"
                        launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreenSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenSecondary),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "WhatsApp",
                        tint = EmeraldGreenSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "WhatsApp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreenSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Pay Button
                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    enabled = customerPolicies.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenSecondary,
                        disabledContainerColor = EmeraldGreenSecondary.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "Pay",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Pay",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // Profile Button
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Profile",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
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
        if (!customer.photoUri.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = customer.photoUri,
                contentDescription = customer.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            CustomerAvatar(name = customer.name, size = size, backgroundColor = RoyalBlueContainer, textColor = RoyalBluePrimary)
        }
    }
}

@Composable
fun CustomerStatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "active" -> EmeraldGreenContainer to EmeraldGreenSecondary
        "due", "grace" -> AccentOrangeContainer to OnAccentOrangeContainer
        "lapsed" -> ErrorRedContainer to ErrorRed
        else -> RoyalBlueContainer to RoyalBluePrimary
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun QuickMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .width(135.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val followUps by viewModel.followUps.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    val customerPolicies = remember(policies, customer.id) {
        policies.filter { it.customerId == customer.id }
    }
    val customerPayments = remember(payments, customer.id) {
        payments.filter { it.customerId == customer.id }.sortedByDescending { it.createdAt }
    }
    val customerDocs = remember(documents, customer.id) {
        documents.filter { it.customerId == customer.id }
    }
    val customerFollowUps = remember(followUps, customer.id) {
        followUps.filter { it.customerId == customer.id }.sortedBy { it.date }
    }

    val context = LocalContext.current
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Dialog states
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var viewingPolicyDetail by remember { mutableStateOf<PolicyEntity?>(null) }
    var editingPolicy by remember { mutableStateOf<PolicyEntity?>(null) }
    var deletingPolicy by remember { mutableStateOf<PolicyEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var viewingReceiptPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showAddDocDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var followUpToEdit by remember { mutableStateOf<FollowUpEntity?>(null) }

    // --- AUTOMATIC CRM SUMMARY COMPUTATIONS ---
    val totalPoliciesCount = customerPolicies.size
    val totalPremiumAmount = customerPolicies.fold(0.0) { acc, p -> acc + p.premiumAmount }
    val totalPaidAmount = customerPayments.fold(0.0) { acc, p -> acc + p.paidAmount }

    val totalOutstandingBalance = remember(customerPolicies, customerPayments) {
        customerPolicies.fold(0.0) { acc, policy ->
            val policyPayments = customerPayments.filter { it.policyId == policy.id }
            acc + getPolicyOutstandingBalance(policy, policyPayments)
        }
    }

    val todayStr = remember { LocalDate.now().toString() }
    val nextDueDate = remember(customerPolicies) {
        customerPolicies
            .mapNotNull { it.dueDate }
            .filter { it.isNotBlank() }
            .minOrNull() ?: "None"
    }

    val lastPayment = remember(customerPayments) {
        customerPayments.maxByOrNull { it.createdAt }
    }

    // Client Overall Status evaluated dynamically from policy statuses
    val overallStatus = remember(customerPolicies, totalOutstandingBalance) {
        when {
            customerPolicies.any { it.status.equals("Overdue", ignoreCase = true) } -> "Overdue"
            customerPolicies.any { it.status.equals("Lapsed", ignoreCase = true) } -> "Lapsed"
            customerPolicies.any { it.status.equals("Due", ignoreCase = true) || it.status.equals("Grace", ignoreCase = true) } || totalOutstandingBalance > 0 -> "Due"
            customerPolicies.isNotEmpty() -> "Active"
            else -> "Active"
        }
    }

    val customerRemindersCount = remember(customerPolicies, customerFollowUps, customer.dob, customer.anniversary) {
        val dueCount = customerPolicies.count { p ->
            val pPayments = customerPayments.filter { it.policyId == p.id }
            getPolicyOutstandingBalance(p, pPayments) > 0
        }
        val fuCount = customerFollowUps.count { !it.status.equals("Completed", ignoreCase = true) }
        val bdayCount = if (customer.dob.isNotBlank()) 1 else 0
        val anniCount = if (customer.anniversary.isNotBlank()) 1 else 0
        dueCount + fuCount + bdayCount + anniCount
    }

    val tabs = listOf(
        "Policies (${customerPolicies.size})",
        "Payment History (${customerPayments.size})",
        "Document Vault (${customerDocs.size})",
        "Reminders & Dues ($customerRemindersCount)",
        "Advisor Notes",
        "Personal Profile"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "360° Customer CRM • $totalPoliciesCount Policies",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onEditCustomer, modifier = Modifier.testTag("edit_customer_button")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            val shareSummary = buildString {
                                appendLine("LIC 360° CRM PORTFOLIO SUMMARY")
                                appendLine("Client Name: ${customer.name}")
                                appendLine("Mobile: ${customer.mobile}")
                                appendLine("WhatsApp: ${customer.whatsapp.ifEmpty { customer.mobile }}")
                                appendLine("Email: ${customer.email.ifEmpty { "N/A" }}")
                                appendLine("Total Policies: $totalPoliciesCount")
                                appendLine("Total Premium: ₹${"%.0f".format(totalPremiumAmount)}")
                                appendLine("Total Paid: ₹${"%.0f".format(totalPaidAmount)}")
                                appendLine("Outstanding Balance: ₹${"%.0f".format(totalOutstandingBalance)}")
                                appendLine("Next Due Date: $nextDueDate")
                                if (lastPayment != null) {
                                    appendLine("Last Payment: ₹${"%.0f".format(lastPayment.paidAmount)} on ${lastPayment.paymentDate}")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, shareSummary)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Customer Profile"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Profile", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = Color(0xFFFF8A80))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalBluePrimary)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val targetPolicy = customerPolicies.firstOrNull { it.status.equals("Active", ignoreCase = true) || it.status.equals("Due", ignoreCase = true) }
                                    ?: customerPolicies.firstOrNull()
                                policyForPaymentCollection = targetPolicy
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("record_payment_bottom_button")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Collect Payment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onAddPolicyForCustomer,
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("add_policy_bottom_button")
                        ) {
                            Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Policy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val firstDuePolicy = customerPolicies.firstOrNull { !it.status.equals("Paid-up", ignoreCase = true) }
                                    ?: customerPolicies.firstOrNull()
                                val reminderMsg = if (firstDuePolicy != null) {
                                    viewModel.generatePremiumReminderMsg(
                                        customerName = customer.name,
                                        policyNo = firstDuePolicy.policyNumber,
                                        planName = firstDuePolicy.planName,
                                        amount = firstDuePolicy.premiumAmount,
                                        dueDate = firstDuePolicy.dueDate
                                    )
                                } else {
                                    "Dear ${customer.name}, greetings from your LIC Advisor ${agentProfile?.agentName ?: ""}."
                                }
                                launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, reminderMsg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("whatsapp_reminder_bottom_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reminder", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchPhoneCall(context, customer.mobile) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                generateAndShareCustomerPortfolioPdf(context, customer, customerPolicies, customerPayments)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        ) {
            // ==========================================
            // SECTION 1: CUSTOMER HEADER
            // ==========================================
            Surface(
                color = RoyalBluePrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CustomerAvatarWithPhoto(customer = customer, size = 76.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = customer.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 20.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Status Chip
                                Surface(
                                    color = when (overallStatus) {
                                        "Overdue", "Lapsed" -> ErrorRed
                                        "Due" -> AccentOrange
                                        else -> EmeraldGreenSecondary
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = overallStatus,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }

                            if (customer.occupation.isNotBlank()) {
                                Text(
                                    text = "💼 ${customer.occupation}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f))
                                )
                            }
                            Text(
                                text = "📱 Mobile: +91 ${customer.mobile}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            if (customer.whatsapp.isNotBlank()) {
                                Text(
                                    text = "💬 WhatsApp: +91 ${customer.whatsapp}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f))
                                )
                            }
                            if (customer.email.isNotBlank()) {
                                Text(
                                    text = "📧 ${customer.email}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                                )
                            }
                            if (customer.address.isNotBlank()) {
                                Text(
                                    text = "🏠 ${customer.address}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { launchPhoneCall(context, customer.mobile) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Call", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                val msg = "Hello ${customer.name}, regarding your LIC policy requirements..."
                                launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                                Text("WhatsApp", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                launchSMS(context, customer.mobile, "Dear ${customer.name}, LIC Policy update from your advisor.")
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("SMS", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                val shareText = "LIC Client: ${customer.name}\nMobile: ${customer.mobile}\nPolicies: $totalPoliciesCount\nOutstanding: ₹${totalOutstandingBalance.toInt()}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Customer"))
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Share", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { onEditCustomer() }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Edit", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { showDeleteConfirm = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                                Text("Delete", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2: CUSTOMER SUMMARY CARDS
            // ==========================================
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    QuickMetricCard(
                        title = "Total Policies",
                        value = "$totalPoliciesCount",
                        icon = Icons.Default.Folder,
                        color = RoyalBluePrimary,
                        bgColor = RoyalBlueContainer
                    )
                }
                item {
                    QuickMetricCard(
                        title = "Total Premium",
                        value = "₹${"%.0f".format(totalPremiumAmount)}",
                        icon = Icons.Default.Payments,
                        color = RoyalBluePrimary,
                        bgColor = RoyalBlueContainer
                    )
                }
                item {
                    QuickMetricCard(
                        title = "Total Paid",
                        value = "₹${"%.0f".format(totalPaidAmount)}",
                        icon = Icons.Default.CheckCircle,
                        color = EmeraldGreenSecondary,
                        bgColor = EmeraldGreenContainer
                    )
                }
                item {
                    QuickMetricCard(
                        title = "Outstanding Balance",
                        value = "₹${"%.0f".format(totalOutstandingBalance)}",
                        icon = Icons.Default.Warning,
                        color = if (totalOutstandingBalance > 0) ErrorRed else EmeraldGreenSecondary,
                        bgColor = if (totalOutstandingBalance > 0) ErrorRedContainer else EmeraldGreenContainer
                    )
                }
                item {
                    QuickMetricCard(
                        title = "Next Due Date",
                        value = nextDueDate,
                        icon = Icons.Default.Event,
                        color = AccentOrange,
                        bgColor = AccentOrangeContainer
                    )
                }
                item {
                    QuickMetricCard(
                        title = "Last Payment Date",
                        value = lastPayment?.let { "₹${it.paidAmount.toInt()} (${it.paymentDate})" } ?: "None",
                        icon = Icons.Default.History,
                        color = EmeraldGreenSecondary,
                        bgColor = EmeraldGreenContainer
                    )
                }
            }

            // NAVIGATION TABS
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RoyalBluePrimary,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // TAB CONTENT AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> CustomerPoliciesTab(
                        policies = customerPolicies,
                        payments = customerPayments,
                        onAddPolicy = onAddPolicyForCustomer,
                        onViewDetail = { viewingPolicyDetail = it },
                        onRecordPayment = { policyForPaymentCollection = it },
                        onEditPolicy = { editingPolicy = it },
                        onDeletePolicy = { deletingPolicy = it }
                    )
                    1 -> CustomerPaymentHistoryTab(
                        payments = customerPayments,
                        policies = customerPolicies,
                        agentName = agentProfile?.agentName ?: "LIC Advisor",
                        agencyCode = agentProfile?.agencyCode ?: "",
                        branchName = agentProfile?.branchName ?: "",
                        onRecordPayment = {
                            val targetPolicy = customerPolicies.firstOrNull { it.status.equals("Active", ignoreCase = true) || it.status.equals("Due", ignoreCase = true) }
                                ?: customerPolicies.firstOrNull()
                            policyForPaymentCollection = targetPolicy
                        },
                        onViewReceipt = { viewingReceiptPayment = it },
                        onEditPayment = { editingPayment = it },
                        onDeletePayment = { deletingPayment = it }
                    )
                    2 -> CustomerDocumentsTab(
                        customer = customer,
                        documents = customerDocs,
                        onUploadClick = { showAddDocDialog = true },
                        onDeleteDoc = { viewModel.deleteDocument(it) }
                    )
                    3 -> CustomerRemindersTab(
                        customer = customer,
                        policies = customerPolicies,
                        payments = customerPayments,
                        followUps = customerFollowUps,
                        onCall = { launchPhoneCall(context, customer.mobile) },
                        onWhatsApp = { msg -> launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg) },
                        onCollectPayment = { policy -> policyForPaymentCollection = policy },
                        onNewFollowUp = {
                            followUpToEdit = null
                            showFollowUpDialog = true
                        },
                        onEditFollowUp = { fu ->
                            followUpToEdit = fu
                            showFollowUpDialog = true
                        },
                        onToggleFollowUp = { fu ->
                            val newStatus = if (fu.status.equals("Completed", ignoreCase = true)) "Pending" else "Completed"
                            viewModel.updateFollowUp(fu.copy(status = newStatus))
                        },
                        onDeleteFollowUp = { fu -> viewModel.deleteFollowUp(fu) }
                    )
                    4 -> CustomerNotesTab(
                        customer = customer,
                        onAddNoteClick = { showAddNoteDialog = true },
                        onUpdateNotes = { updatedNotes ->
                            viewModel.updateCustomer(customer.copy(notes = updatedNotes))
                        }
                    )
                    5 -> CustomerPersonalDetailsTab(
                        customer = customer,
                        onEditClick = onEditCustomer,
                        onDeleteClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }

    // DIALOGS

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

    // Edit Payment Dialog
    editingPayment?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            existingPayments = customerPayments,
            onDismiss = { editingPayment = null },
            onSave = { updatedPayment ->
                viewModel.updatePayment(updatedPayment)
                editingPayment = null
            }
        )
    }

    // Delete Payment Dialog
    deletingPayment?.let { payment ->
        DeletePaymentDialog(
            payment = payment,
            onDismiss = { deletingPayment = null },
            onConfirmDelete = {
                viewModel.deletePayment(payment)
                deletingPayment = null
            }
        )
    }

    // View Receipt Dialog
    viewingReceiptPayment?.let { payment ->
        ReceiptDialog(
            payment = payment,
            agentName = agentProfile?.agentName ?: "LIC Advisor",
            agencyCode = agentProfile?.agencyCode ?: "",
            branch = agentProfile?.branchName ?: "",
            onDismiss = { viewingReceiptPayment = null }
        )
    }

    // View Policy Detail Dialog
    viewingPolicyDetail?.let { policy ->
        PolicyDetailDialog(
            policy = policy,
            customer = customer,
            onDismiss = { viewingPolicyDetail = null },
            onRecordPayment = {
                viewingPolicyDetail = null
                policyForPaymentCollection = policy
            }
        )
    }

    // Edit Policy Dialog
    editingPolicy?.let { policy ->
        EditCustomerPolicyDialog(
            policy = policy,
            onDismiss = { editingPolicy = null },
            onSave = { updatedPolicy ->
                viewModel.updatePolicy(updatedPolicy)
                editingPolicy = null
            }
        )
    }

    // Delete Policy Confirmation
    deletingPolicy?.let { policy ->
        AlertDialog(
            onDismissRequest = { deletingPolicy = null },
            title = { Text("Delete Policy ${policy.policyNumber}?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete policy ${policy.policyNumber} (${policy.planName})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePolicy(policy)
                        deletingPolicy = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Policy")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPolicy = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Document Dialog
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

    // Add Advisor Note Dialog
    if (showAddNoteDialog) {
        AddCustomerNoteDialog(
            customer = customer,
            onDismiss = { showAddNoteDialog = false },
            onSave = { updatedNotes ->
                val updatedCust = customer.copy(notes = updatedNotes)
                viewModel.updateCustomer(updatedCust)
                showAddNoteDialog = false
            }
        )
    }

    // Add/Edit Follow-Up Dialog
    if (showFollowUpDialog) {
        FollowUpFormDialog(
            existingFollowUp = followUpToEdit,
            customers = listOf(customer),
            onDismiss = { showFollowUpDialog = false },
            onSave = { followUp ->
                val fuWithCust = followUp.copy(
                    customerId = customer.id,
                    customerName = customer.name,
                    customerMobile = customer.mobile
                )
                if (followUpToEdit != null) {
                    viewModel.updateFollowUp(fuWithCust)
                } else {
                    viewModel.addFollowUp(fuWithCust)
                }
                showFollowUpDialog = false
            }
        )
    }

    // Delete Customer Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Customer Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${customer.name}? This will remove all associated client profile details and records.") },
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

// ==========================================
// SUB-TAB COMPONENT 1: POLICIES
// ==========================================
@Composable
fun CustomerPoliciesTab(
    policies: List<PolicyEntity>,
    payments: List<PaymentEntity>,
    onAddPolicy: () -> Unit,
    onViewDetail: (PolicyEntity) -> Unit,
    onRecordPayment: (PolicyEntity) -> Unit,
    onEditPolicy: (PolicyEntity) -> Unit,
    onDeletePolicy: (PolicyEntity) -> Unit
) {
    if (policies.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Policy, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(56.dp))
                Text("No Policies Linked Yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "Tap below to add and link a new LIC policy for this client portfolio.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAddPolicy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Policy")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(policies, key = { it.id }) { policy ->
                val policyPayments = payments.filter { it.policyId == policy.id }
                val outstandingBal = getPolicyOutstandingBalance(policy, policyPayments)

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
                            Text("Issue Date: ${policy.issueDate.ifEmpty { "N/A" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Next Due: ${policy.dueDate}", style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange, fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Outstanding Bal:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "₹${"%.2f".format(outstandingBal)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (outstandingBal > 0) ErrorRed else EmeraldGreenSecondary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 4 Action Buttons: View, Edit, Collect Payment, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onViewDetail(policy) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("View", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { onEditPolicy(policy) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("Edit", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onRecordPayment(policy) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                                modifier = Modifier.weight(1.3f).height(38.dp)
                            ) {
                                Text("Collect Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onDeletePolicy(policy) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                modifier = Modifier.weight(0.9f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-TAB COMPONENT 2: PAYMENT HISTORY TIMELINE
// ==========================================
@Composable
fun CustomerPaymentHistoryTab(
    payments: List<PaymentEntity>,
    policies: List<PolicyEntity>,
    agentName: String,
    agencyCode: String = "",
    branchName: String = "",
    onRecordPayment: () -> Unit,
    onViewReceipt: (PaymentEntity) -> Unit,
    onEditPayment: (PaymentEntity) -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit
) {
    val context = LocalContext.current

    if (payments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(56.dp))
                Text("No Payment Records Yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "No premium payment history exists for this client portfolio. Record a payment to generate receipts.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRecordPayment,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Collect Payment")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(payments, key = { it.id }) { payment ->
                val matchingPolicy = policies.find { it.id == payment.policyId }
                val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, payments)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Receipt #: ${payment.receiptNumber}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Payment Date: ${payment.paymentDate}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                            Surface(
                                color = EmeraldGreenContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "₹${"%.2f".format(payment.paidAmount)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Policy #: ${payment.policyNumber}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("Payment Mode: ${payment.paymentMode}", style = MaterialTheme.typography.bodySmall)
                        }

                        if (matchingPolicy != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Premium Installment: ₹${"%.0f".format(matchingPolicy.premiumAmount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Remaining Bal: ₹${"%.0f".format(remainingBal)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (remainingBal > 0) ErrorRed else EmeraldGreenSecondary))
                            }
                        }

                        if (payment.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Notes: ${payment.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: View Receipt, Edit, Delete, Share Receipt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onViewReceipt(payment) }) {
                                Icon(Icons.Default.Receipt, contentDescription = "View Receipt", tint = RoyalBluePrimary)
                            }
                            IconButton(onClick = { onEditPayment(payment) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Payment", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeletePayment(payment) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Payment", tint = ErrorRed)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    val shareText = generateReceiptShareText(
                                        payment = payment,
                                        agentName = agentName,
                                        agencyCode = agencyCode,
                                        branch = branchName
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Premium Receipt"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share Receipt", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-TAB COMPONENT 3: DOCUMENT VAULT
// ==========================================
@Composable
fun CustomerDocumentsTab(
    customer: CustomerEntity,
    documents: List<DocumentEntity>,
    onUploadClick: () -> Unit,
    onDeleteDoc: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val requiredDocTypes = listOf(
        "Policy Bond",
        "Aadhaar Card",
        "PAN Card",
        "Customer Photo",
        "Nominee Documents",
        "Medical Reports",
        "KYC Documents",
        "Other Files"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Document Vault", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    Text("Policy bonds, KYC, Aadhaar, PAN & Nominee files", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                Button(
                    onClick = onUploadClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload File", fontSize = 12.sp)
                }
            }
        }

        items(requiredDocTypes) { docType ->
            val matchingDoc = documents.firstOrNull {
                it.docType.contains(docType.replace(" Card", "").replace(" Documents", "").replace(" Files", ""), ignoreCase = true)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                                "Medical Reports" -> Icons.Default.MedicalServices
                                "Nominee Documents" -> Icons.Default.FolderShared
                                "KYC Documents" -> Icons.Default.VerifiedUser
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(28.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val shareText = "Document: ${matchingDoc.title}\nClient: ${customer.name}\nReference: ${matchingDoc.fileUri.ifEmpty { "Uploaded locally" }}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Document"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = RoyalBluePrimary, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = { onDeleteDoc(matchingDoc) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onUploadClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Upload", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-TAB COMPONENT 4: REMINDERS & FOLLOW-UPS
// ==========================================
@Composable
fun CustomerRemindersTab(
    customer: CustomerEntity,
    policies: List<PolicyEntity>,
    payments: List<PaymentEntity>,
    followUps: List<FollowUpEntity>,
    onCall: () -> Unit,
    onWhatsApp: (String) -> Unit,
    onCollectPayment: (PolicyEntity) -> Unit,
    onNewFollowUp: () -> Unit,
    onEditFollowUp: (FollowUpEntity) -> Unit,
    onToggleFollowUp: (FollowUpEntity) -> Unit,
    onDeleteFollowUp: (FollowUpEntity) -> Unit
) {
    val duePolicies = remember(policies, payments) {
        policies.filter { policy ->
            val pPayments = payments.filter { it.policyId == policy.id }
            getPolicyOutstandingBalance(policy, pPayments) > 0
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Section: Upcoming Premium Dues
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upcoming Premium Dues (${duePolicies.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
            }
        }

        if (duePolicies.isEmpty()) {
            item {
                Surface(
                    color = EmeraldGreenContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 All policy premiums for ${customer.name} are up-to-date!",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                    )
                }
            }
        } else {
            items(duePolicies, key = { "due_${it.id}" }) { policy ->
                val policyPayments = payments.filter { it.policyId == policy.id }
                val outBal = getPolicyOutstandingBalance(policy, policyPayments)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(policy.planName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Policy #: ${policy.policyNumber}", style = MaterialTheme.typography.labelSmall)
                            }
                            Surface(color = AccentOrange, shape = RoundedCornerShape(8.dp)) {
                                Text("Due: ${policy.dueDate}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Outstanding: ₹${"%.2f".format(outBal)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed))

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(onClick = onCall, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(36.dp)) {
                                Text("Call", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onWhatsApp("Dear ${customer.name}, premium of ₹${policy.premiumAmount} for policy ${policy.policyNumber} is due on ${policy.dueDate}.")
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("WhatsApp", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onCollectPayment(policy) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                                modifier = Modifier.weight(1.2f).height(36.dp)
                            ) {
                                Text("Record Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Section: Personal Events (Birthday / Anniversary)
        if (customer.dob.isNotBlank() || customer.anniversary.isNotBlank()) {
            item {
                Text("Personal Celebration Events", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
            }

            if (customer.dob.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cake, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Birthday", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("DOB: ${customer.dob}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row {
                                IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = null, tint = RoyalBluePrimary) }
                                IconButton(onClick = { onWhatsApp("Happy Birthday ${customer.name}! Best wishes from your LIC Advisor.") }) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = EmeraldGreenSecondary)
                                }
                            }
                        }
                    }
                }
            }

            if (customer.anniversary.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Marriage Anniversary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Date: ${customer.anniversary}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row {
                                IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = null, tint = RoyalBluePrimary) }
                                IconButton(onClick = { onWhatsApp("Happy Marriage Anniversary ${customer.name}! Best wishes from your LIC Advisor.") }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = EmeraldGreenSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Scheduled Follow-ups
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scheduled Follow-ups (${followUps.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                Button(
                    onClick = onNewFollowUp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Follow-up", fontSize = 12.sp)
                }
            }
        }

        if (followUps.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No follow-up tasks scheduled for this client. Tap '+ New Follow-up' to create one.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(followUps, key = { "fu_${it.id}" }) { fu ->
                val isCompleted = fu.status.equals("Completed", ignoreCase = true)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isCompleted,
                                    onCheckedChange = { onToggleFollowUp(fu) }
                                )
                                Column {
                                    Text(
                                        text = "Date: ${fu.date} ${if (fu.time.isNotBlank()) "@ ${fu.time}" else ""}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            StatusBadge(status = fu.status)
                        }

                        if (fu.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Notes: ${fu.notes}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = "Call", tint = RoyalBluePrimary) }
                            IconButton(onClick = { onWhatsApp("Hello ${customer.name}, regarding our scheduled follow-up: ${fu.notes}") }) {
                                Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = EmeraldGreenSecondary)
                            }
                            IconButton(onClick = { onEditFollowUp(fu) }) { Icon(Icons.Default.Edit, contentDescription = "Reschedule", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { onDeleteFollowUp(fu) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed) }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-TAB COMPONENT 5: ADVISOR NOTES WITH TIMESTAMPS
// ==========================================
@Composable
fun CustomerNotesTab(
    customer: CustomerEntity,
    onAddNoteClick: () -> Unit,
    onUpdateNotes: (String) -> Unit
) {
    var editingRawNotes by remember { mutableStateOf(false) }
    var notesTextState by remember { mutableStateOf(customer.notes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Advisor Private Notes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                Text("Private remarks visible only to you as advisor", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }

            Button(
                onClick = onAddNoteClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Note", fontSize = 12.sp)
            }
        }

        if (editingRawNotes) {
            OutlinedTextField(
                value = notesTextState,
                onValueChange = { notesTextState = it },
                label = { Text("Edit Advisor Notes") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                shape = RoundedCornerShape(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    notesTextState = customer.notes
                    editingRawNotes = false
                }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onUpdateNotes(notesTextState)
                        editingRawNotes = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Save Notes")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recorded Remarks & Log", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                        Row {
                            IconButton(onClick = { editingRawNotes = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit All Notes", tint = RoyalBluePrimary)
                            }
                            if (customer.notes.isNotBlank()) {
                                IconButton(onClick = { onUpdateNotes("") }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear Notes", tint = ErrorRed)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (customer.notes.isBlank()) {
                        Text(
                            text = "No advisor notes recorded yet. Tap '+ Add Note' above to log client preferences, follow-up remarks, or meeting records.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Render timestamped paragraphs cleanly
                        val noteBlocks = customer.notes.split("\n\n").filter { it.isNotBlank() }
                        noteBlocks.forEachIndexed { index, block ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = block,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-TAB COMPONENT 6: PERSONAL PROFILE
// ==========================================
@Composable
fun CustomerPersonalDetailsTab(
    customer: CustomerEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Client Profile & KYC", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = RoyalBluePrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DetailItem("Full Name", customer.name)
                DetailItem("Mobile Number", customer.mobile)
                DetailItem("WhatsApp Number", customer.whatsapp.ifEmpty { customer.mobile })
                DetailItem("Email Address", customer.email.ifEmpty { "N/A" })
                DetailItem("Date of Birth", customer.dob.ifEmpty { "N/A" })
                DetailItem("Marriage Anniversary", customer.anniversary.ifEmpty { "N/A" })
                DetailItem("Occupation", customer.occupation.ifEmpty { "N/A" })
                DetailItem("Full Address", customer.address.ifEmpty { "N/A" })
                DetailItem("Aadhaar Number", customer.aadhaar.ifEmpty { "N/A" })
                DetailItem("PAN Number", customer.pan.ifEmpty { "N/A" })

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Details")
                    }

                    OutlinedButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Profile")
                    }
                }
            }
        }
    }
}

// HELPER DIALOGS FOR CUSTOMER PROFILE

@Composable
fun PolicyDetailDialog(
    policy: PolicyEntity,
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onRecordPayment: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${policy.planName} (${policy.policyNumber})", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailItem("Customer Name", customer.name)
                DetailItem("Policy Number", policy.policyNumber)
                DetailItem("Plan Name", policy.planName)
                DetailItem("Premium Amount", "₹${"%.2f".format(policy.premiumAmount)}")
                DetailItem("Premium Mode", policy.premiumMode)
                DetailItem("Sum Assured", "₹${"%.0f".format(policy.sumAssured)}")
                DetailItem("Policy Term / PPT", "${policy.policyTerm} Yrs / ${policy.premiumPayingTerm} Yrs")
                DetailItem("Issue Date", policy.issueDate.ifEmpty { "N/A" })
                DetailItem("Next Due Date", policy.dueDate)
                DetailItem("Maturity Date", policy.maturityDate)
                DetailItem("Nominee", policy.nominee.ifEmpty { "N/A" })
                DetailItem("Status", policy.status)
            }
        },
        confirmButton = {
            Button(
                onClick = onRecordPayment,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
            ) {
                Text("Record Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EditCustomerPolicyDialog(
    policy: PolicyEntity,
    onDismiss: () -> Unit,
    onSave: (PolicyEntity) -> Unit
) {
    var policyNumber by remember { mutableStateOf(policy.policyNumber) }
    var planName by remember { mutableStateOf(policy.planName) }
    var premiumAmount by remember { mutableStateOf(policy.premiumAmount.toString()) }
    var premiumMode by remember { mutableStateOf(policy.premiumMode) }
    var dueDate by remember { mutableStateOf(policy.dueDate) }
    var status by remember { mutableStateOf(policy.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Policy", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = policyNumber,
                    onValueChange = { policyNumber = it },
                    label = { Text("Policy Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    label = { Text("Plan Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = premiumAmount,
                    onValueChange = { premiumAmount = it },
                    label = { Text("Premium Amount (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Next Due Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = policy.copy(
                        policyNumber = policyNumber,
                        planName = planName,
                        premiumAmount = premiumAmount.toDoubleOrNull() ?: policy.premiumAmount,
                        dueDate = dueDate
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
            ) {
                Text("Save Changes")
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
fun AddCustomerNoteDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Advisor / Meeting Note", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Enter meeting notes or follow-up remarks...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timestamp = LocalDate.now().toString()
                    val newCombinedNotes = if (customer.notes.isBlank()) {
                        "[$timestamp] $noteText"
                    } else {
                        "${customer.notes}\n\n[$timestamp] $noteText"
                    }
                    onSave(newCombinedNotes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun generateAndShareCustomerPortfolioPdf(
    context: Context,
    customer: CustomerEntity,
    policies: List<PolicyEntity>,
    payments: List<PaymentEntity>
) {
    val totalPremium = policies.sumOf { it.premiumAmount }
    val totalPaid = payments.sumOf { it.paidAmount }
    val pending = (totalPremium - totalPaid).coerceAtLeast(0.0)

    val text = buildString {
        appendLine("==========================================")
        appendLine("       LIC COMMERCIAL CLIENT PORTFOLIO     ")
        appendLine("==========================================")
        appendLine("Customer Name  : ${customer.name}")
        appendLine("Mobile Number  : ${customer.mobile}")
        appendLine("Email          : ${customer.email.ifEmpty { "N/A" }}")
        appendLine("Occupation     : ${customer.occupation.ifEmpty { "N/A" }}")
        appendLine("Total Policies : ${policies.size}")
        appendLine("Total Premium  : ₹${"%.2f".format(totalPremium)}")
        appendLine("Total Paid     : ₹${"%.2f".format(totalPaid)}")
        appendLine("Pending Amount : ₹${"%.2f".format(pending)}")
        appendLine("------------------------------------------")
        appendLine("POLICY DETAILS:")
        policies.forEachIndexed { idx, pol ->
            appendLine("${idx + 1}. Policy #: ${pol.policyNumber} (${pol.planName})")
            appendLine("   Premium: ₹${pol.premiumAmount} | Mode: ${pol.premiumMode}")
            appendLine("   Due Date: ${pol.dueDate} | Status: ${pol.status}")
        }
        appendLine("==========================================")
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "LIC Client Portfolio - ${customer.name}")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Client Portfolio")
    context.startActivity(shareIntent)
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

// Helper component for clean form inputs
@Composable
fun FormInputField(
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
fun DatePickerDialogComponent(
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
    } catch (e: Exception) { /* default to today */ }

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

@Composable
fun PhotoSourceDialog(
    hasPhoto: Boolean,
    onCameraSelect: () -> Unit,
    onGallerySelect: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Upload Customer Photo",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Choose photo source:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Surface(
                    onClick = { onCameraSelect(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    color = RoyalBlueContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = RoyalBluePrimary)
                        Text("Take Photo with Camera", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = RoyalBluePrimary))
                    }
                }

                Surface(
                    onClick = { onGallerySelect(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Choose from Gallery", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                if (hasPhoto) {
                    Surface(
                        onClick = { onRemovePhoto(); onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRedContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                            Text("Remove Current Photo", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = ErrorRed))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private suspend fun uploadPhotoToFirebaseStorage(
    selectedUri: Uri?,
    selectedBitmap: Bitmap?
): String? {
    return try {
        val storage = FirebaseStorage.getInstance()
        val photoRef = storage.reference.child("customer_photos/${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        
        if (selectedBitmap != null) {
            val baos = ByteArrayOutputStream()
            selectedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val bytes = baos.toByteArray()
            photoRef.putBytes(bytes).await()
            photoRef.downloadUrl.await().toString()
        } else if (selectedUri != null) {
            photoRef.putFile(selectedUri).await()
            photoRef.downloadUrl.await().toString()
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("FirebaseStorage", "Image upload error/fallback: ${e.message}")
        selectedUri?.toString()
    }
}

@Composable
fun AddEditCustomerDialog(
    initialCustomer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1: Basic Info, 2: Personal Details, 3: Review

    // Form fields
    var name by remember { mutableStateOf(initialCustomer?.name ?: "") }
    var mobile by remember { mutableStateOf(initialCustomer?.mobile ?: "") }
    var whatsapp by remember { mutableStateOf(initialCustomer?.whatsapp ?: "") }
    var email by remember { mutableStateOf(initialCustomer?.email ?: "") }
    var occupation by remember { mutableStateOf(initialCustomer?.occupation ?: "") }
    var address by remember { mutableStateOf(initialCustomer?.address ?: "") }
    var dob by remember { mutableStateOf(initialCustomer?.dob ?: "") }
    var anniversary by remember { mutableStateOf(initialCustomer?.anniversary ?: "") }
    var aadhaar by remember { mutableStateOf(initialCustomer?.aadhaar ?: "") }
    var pan by remember { mutableStateOf(initialCustomer?.pan ?: "") }
    var notes by remember { mutableStateOf(initialCustomer?.notes ?: "") }
    var photoUri by remember { mutableStateOf(initialCustomer?.photoUri ?: "") }

    // Photo picker states
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Validation & Date Picker states
    var nameError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var aadhaarError by remember { mutableStateOf<String?>(null) }
    var panError by remember { mutableStateOf<String?>(null) }
    var activeDatePicker by remember { mutableStateOf<String?>(null) } // "DOB" or "ANNIVERSARY"
    var isSaving by remember { mutableStateOf(false) }

    // Activity Result Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            selectedBitmap = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            selectedImageUri = null
        }
    }

    // Date Picker Dialog
    if (activeDatePicker != null) {
        DatePickerDialogComponent(
            initialDateStr = if (activeDatePicker == "DOB") dob else anniversary,
            onDateSelected = { date ->
                if (activeDatePicker == "DOB") dob = date else anniversary = date
                activeDatePicker = null
            },
            onDismiss = { activeDatePicker = null }
        )
    }

    // Photo Choice Dialog
    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            hasPhoto = selectedImageUri != null || selectedBitmap != null || photoUri.isNotBlank(),
            onCameraSelect = { cameraLauncher.launch(null) },
            onGallerySelect = { galleryLauncher.launch("image/*") },
            onRemovePhoto = {
                selectedImageUri = null
                selectedBitmap = null
                photoUri = ""
            },
            onDismiss = { showPhotoSourceDialog = false }
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
                            text = if (initialCustomer == null) "Add New Client Portfolio" else "Edit Client Portfolio",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (currentStep) {
                                1 -> "Step 1 of 3: Basic Information"
                                2 -> "Step 2 of 3: Personal Details"
                                else -> "Step 3 of 3: Review & Confirm"
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
                    val steps = listOf("1. Basic", "2. Personal", "3. Review")
                    steps.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isCurrent = currentStep == stepNum
                        val isCompleted = currentStep > stepNum

                        Surface(
                            onClick = {
                                if (!isSaving) {
                                    if (stepNum == 1 || (stepNum == 2 && name.isNotBlank() && mobile.isNotBlank()) || (stepNum == 3 && currentStep >= 2)) {
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
                                // STEP 1: Basic Information
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Circular Avatar with Photo
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                            .border(2.5.dp, RoyalBluePrimary, CircleShape)
                                            .background(RoyalBlueContainer)
                                            .clickable { showPhotoSourceDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            selectedBitmap != null -> {
                                                Image(
                                                    bitmap = selectedBitmap!!.asImageBitmap(),
                                                    contentDescription = "Selected Photo",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            selectedImageUri != null -> {
                                                coil.compose.AsyncImage(
                                                    model = selectedImageUri,
                                                    contentDescription = "Selected Photo",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            photoUri.isNotBlank() -> {
                                                coil.compose.AsyncImage(
                                                    model = photoUri,
                                                    contentDescription = "Customer Photo",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            else -> {
                                                CustomerAvatar(
                                                    name = name.ifBlank { "Client" },
                                                    size = 96.dp,
                                                    backgroundColor = RoyalBlueContainer,
                                                    textColor = RoyalBluePrimary
                                                )
                                            }
                                        }

                                        // Camera Overlay Icon
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(RoyalBluePrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CameraAlt,
                                                contentDescription = "Upload Photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = { showPhotoSourceDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (selectedImageUri != null || selectedBitmap != null || photoUri.isNotBlank()) "Change Photo" else "Upload Photo",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                FormInputField(
                                    value = name,
                                    onValueChange = { name = it; nameError = null },
                                    label = "Full Name",
                                    leadingIcon = Icons.Default.Person,
                                    placeholder = "e.g. Rajesh Kumar",
                                    isRequired = true,
                                    isError = nameError != null,
                                    errorMessage = nameError,
                                    testTag = "add_cust_name_input"
                                )

                                FormInputField(
                                    value = mobile,
                                    onValueChange = {
                                        mobile = it
                                        mobileError = null
                                        if (whatsapp.isBlank()) whatsapp = it
                                    },
                                    label = "Mobile Phone Number",
                                    leadingIcon = Icons.Default.Phone,
                                    placeholder = "10-digit mobile number",
                                    isRequired = true,
                                    isError = mobileError != null,
                                    errorMessage = mobileError,
                                    testTag = "add_cust_mobile_input"
                                )

                                FormInputField(
                                    value = whatsapp,
                                    onValueChange = { whatsapp = it },
                                    label = "WhatsApp Number",
                                    leadingIcon = Icons.Default.Chat,
                                    placeholder = "WhatsApp contact number"
                                )

                                FormInputField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = "Email Address",
                                    leadingIcon = Icons.Default.Email,
                                    placeholder = "e.g. rajesh@gmail.com"
                                )

                                FormInputField(
                                    value = occupation,
                                    onValueChange = { occupation = it },
                                    label = "Occupation / Profession",
                                    leadingIcon = Icons.Default.Work,
                                    placeholder = "e.g. Software Engineer, Business"
                                )
                            }

                            2 -> {
                                // STEP 2: Personal Details
                                FormInputField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = "Full Residence Address",
                                    leadingIcon = Icons.Default.Home,
                                    placeholder = "House No., Street, City, Pincode",
                                    singleLine = false,
                                    maxLines = 3
                                )

                                FormInputField(
                                    value = dob,
                                    onValueChange = { dob = it },
                                    label = "Date of Birth",
                                    leadingIcon = Icons.Default.Cake,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "DOB" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "DOB" }) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                FormInputField(
                                    value = anniversary,
                                    onValueChange = { anniversary = it },
                                    label = "Anniversary Date",
                                    leadingIcon = Icons.Default.Favorite,
                                    placeholder = "YYYY-MM-DD",
                                    readOnly = true,
                                    onClick = { activeDatePicker = "ANNIVERSARY" },
                                    trailingIcon = {
                                        IconButton(onClick = { activeDatePicker = "ANNIVERSARY" }) {
                                            Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = RoyalBluePrimary)
                                        }
                                    }
                                )

                                FormInputField(
                                    value = aadhaar,
                                    onValueChange = {
                                        aadhaar = it
                                        aadhaarError = null
                                    },
                                    label = "Aadhaar Number",
                                    leadingIcon = Icons.Default.Badge,
                                    placeholder = "12-digit Aadhaar number",
                                    isError = aadhaarError != null,
                                    errorMessage = aadhaarError
                                )

                                FormInputField(
                                    value = pan,
                                    onValueChange = {
                                        pan = it.uppercase()
                                        panError = null
                                    },
                                    label = "PAN Number",
                                    leadingIcon = Icons.Default.CreditCard,
                                    placeholder = "10-character PAN (e.g. ABCDE1234F)",
                                    isError = panError != null,
                                    errorMessage = panError
                                )

                                FormInputField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = "Advisor Notes & Preferences",
                                    leadingIcon = Icons.Default.Notes,
                                    placeholder = "Key preferences, policy notes, best time to call...",
                                    singleLine = false,
                                    maxLines = 3
                                )
                            }

                            3 -> {
                                // STEP 3: Review
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
                                                "Basic Information",
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
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(CircleShape)
                                                    .background(RoyalBlueContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when {
                                                    selectedBitmap != null -> Image(selectedBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                                    selectedImageUri != null -> coil.compose.AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                                    photoUri.isNotBlank() -> coil.compose.AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                                    else -> CustomerAvatar(name = name, size = 54.dp)
                                                }
                                            }

                                            Column {
                                                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text("Mobile: $mobile", style = MaterialTheme.typography.bodySmall)
                                                if (whatsapp.isNotBlank()) Text("WhatsApp: $whatsapp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (email.isNotBlank()) Text("Email: $email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (occupation.isNotBlank()) Text("Occupation: $occupation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                "Personal Details",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBluePrimary
                                            )
                                            TextButton(onClick = { currentStep = 2 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                        }

                                        if (address.isNotBlank()) Text("Address: $address", style = MaterialTheme.typography.bodySmall)
                                        if (dob.isNotBlank()) Text("Date of Birth: $dob", style = MaterialTheme.typography.bodySmall)
                                        if (anniversary.isNotBlank()) Text("Anniversary: $anniversary", style = MaterialTheme.typography.bodySmall)
                                        if (aadhaar.isNotBlank()) Text("Aadhaar: $aadhaar", style = MaterialTheme.typography.bodySmall)
                                        if (pan.isNotBlank()) Text("PAN: $pan", style = MaterialTheme.typography.bodySmall)
                                        if (notes.isNotBlank()) Text("Notes: $notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (address.isBlank() && dob.isBlank() && anniversary.isBlank() && aadhaar.isBlank() && pan.isBlank() && notes.isBlank()) {
                                            Text("No additional personal details specified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
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
                                    if (name.isBlank()) {
                                        nameError = "Full Customer Name is required"
                                        valid = false
                                    }
                                    if (mobile.isBlank()) {
                                        mobileError = "Mobile Phone Number is required"
                                        valid = false
                                    } else if (mobile.length < 10) {
                                        mobileError = "Enter valid 10-digit mobile number"
                                        valid = false
                                    }
                                    if (valid) {
                                        currentStep = 2
                                    }
                                } else if (currentStep == 2) {
                                    var valid = true
                                    if (aadhaar.isNotBlank() && aadhaar.replace(" ", "").length != 12) {
                                        aadhaarError = "Aadhaar must be 12 digits"
                                        valid = false
                                    }
                                    if (pan.isNotBlank() && !pan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]{1}"))) {
                                        panError = "Invalid PAN format (e.g. ABCDE1234F)"
                                        valid = false
                                    }
                                    if (valid) {
                                        currentStep = 3
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                isSaving = true
                                coroutineScope.launch {
                                    val uploadedPhotoUrl = uploadPhotoToFirebaseStorage(selectedImageUri, selectedBitmap)
                                    val finalPhotoUri = uploadedPhotoUrl ?: photoUri.ifBlank { null }

                                    val customer = (initialCustomer ?: CustomerEntity(
                                        name = name.trim(),
                                        mobile = mobile.trim(),
                                        whatsapp = whatsapp.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        occupation = occupation.trim(),
                                        dob = dob.trim(),
                                        anniversary = anniversary.trim(),
                                        aadhaar = aadhaar.trim(),
                                        pan = pan.trim(),
                                        notes = notes.trim(),
                                        photoUri = finalPhotoUri
                                    )).copy(
                                        name = name.trim(),
                                        mobile = mobile.trim(),
                                        whatsapp = whatsapp.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        occupation = occupation.trim(),
                                        dob = dob.trim(),
                                        anniversary = anniversary.trim(),
                                        aadhaar = aadhaar.trim(),
                                        pan = pan.trim(),
                                        notes = notes.trim(),
                                        photoUri = finalPhotoUri
                                    )

                                    onSave(customer)
                                    Toast.makeText(context, "Client Portfolio Saved Successfully!", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading & Saving...")
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Customer Portfolio")
                            }
                        }
                    }
                }
            }
        }
    }
}

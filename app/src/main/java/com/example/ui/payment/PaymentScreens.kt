package com.example.ui.payment

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.PaymentDateFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCollectionDialog(
    policy: PolicyEntity,
    existingPayments: List<PaymentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCollect: (amount: Double, lateFee: Double, mode: String, receiptNo: String, notes: String) -> Unit
) {
    var amountStr by remember { mutableStateOf(policy.premiumAmount.toString()) }
    var lateFeeStr by remember { mutableStateOf("0") }
    var selectedMode by remember { mutableStateOf("UPI") }
    var receiptNo by remember {
        mutableStateOf("REC-${LocalDate.now().year}-${System.currentTimeMillis().toString().takeLast(4)}")
    }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("UPI", "Cash", "Bank Transfer", "Cheque")

    val paidAmount = amountStr.toDoubleOrNull() ?: 0.0
    val lateFee = lateFeeStr.toDoubleOrNull() ?: 0.0
    val totalAmount = paidAmount + lateFee

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = EmeraldGreenContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = null,
                        tint = EmeraldGreenSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Record Premium Collection",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Policy Summary Header
                Surface(
                    color = RoyalBlueContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = policy.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary
                            )
                        )
                        Text(
                            text = "${policy.planName} • Policy #${policy.policyNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Due Date: ${policy.dueDate}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = OnRoyalBlueContainer
                            )
                            Text(
                                text = "Mode: ${policy.premiumMode}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = OnRoyalBlueContainer
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        color = ErrorRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        errorMessage = null
                    },
                    label = { Text("Premium Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collect_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = lateFeeStr,
                    onValueChange = {
                        lateFeeStr = it
                        errorMessage = null
                    },
                    label = { Text("Late Fee / Fine (₹)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collect_late_fee_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Total Calculated Badge
                Surface(
                    color = EmeraldGreenContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Received Amount:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnEmeraldGreenContainer
                        )
                        Text(
                            text = "₹${"%.2f".format(totalAmount)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreenSecondary
                            )
                        )
                    }
                }

                Text(
                    "Payment Method *",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modeOptions.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = receiptNo,
                    onValueChange = {
                        receiptNo = it
                        errorMessage = null
                    },
                    label = { Text("Receipt Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Cheque No / Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: -1.0
                    val fee = lateFeeStr.toDoubleOrNull() ?: 0.0

                    if (amt <= 0) {
                        errorMessage = "Please enter a valid amount greater than ₹0."
                        return@Button
                    }
                    if (receiptNo.isBlank()) {
                        errorMessage = "Receipt number is required."
                        return@Button
                    }
                    val isDuplicate = existingPayments.any {
                        it.receiptNumber.trim().equals(receiptNo.trim(), ignoreCase = true)
                    }
                    if (isDuplicate) {
                        errorMessage = "Receipt number '$receiptNo' already exists. Please enter a unique receipt number."
                        return@Button
                    }

                    onCollect(amt, fee, selectedMode, receiptNo.trim(), notes.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Confirm Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPaymentDialog(
    payment: PaymentEntity,
    existingPayments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onSave: (PaymentEntity) -> Unit
) {
    var amountStr by remember { mutableStateOf(payment.paidAmount.toString()) }
    var lateFeeStr by remember { mutableStateOf(payment.lateFee.toString()) }
    var selectedMode by remember { mutableStateOf(payment.paymentMode) }
    var receiptNo by remember { mutableStateOf(payment.receiptNumber) }
    var paymentDate by remember { mutableStateOf(payment.paymentDate) }
    var notes by remember { mutableStateOf(payment.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("UPI", "Cash", "Bank Transfer", "Cheque")
    val paidAmount = amountStr.toDoubleOrNull() ?: 0.0
    val lateFee = lateFeeStr.toDoubleOrNull() ?: 0.0
    val totalAmount = paidAmount + lateFee

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Payment Record", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = RoyalBlueContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(payment.customerName, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                        Text("Policy #${payment.policyNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; errorMessage = null },
                    label = { Text("Premium Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = lateFeeStr,
                    onValueChange = { lateFeeStr = it; errorMessage = null },
                    label = { Text("Late Fee / Fine (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "Total: ₹${"%.2f".format(totalAmount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                )

                Text("Payment Mode", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeOptions.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode) }
                        )
                    }
                }

                OutlinedTextField(
                    value = receiptNo,
                    onValueChange = { receiptNo = it; errorMessage = null },
                    label = { Text("Receipt Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it; errorMessage = null },
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: -1.0
                    val fee = lateFeeStr.toDoubleOrNull() ?: 0.0

                    if (amt <= 0) {
                        errorMessage = "Amount must be greater than ₹0."
                        return@Button
                    }
                    if (receiptNo.isBlank()) {
                        errorMessage = "Receipt number cannot be empty."
                        return@Button
                    }
                    val isDuplicate = existingPayments.any {
                        it.id != payment.id && it.receiptNumber.trim().equals(receiptNo.trim(), ignoreCase = true)
                    }
                    if (isDuplicate) {
                        errorMessage = "Receipt number '$receiptNo' is already used."
                        return@Button
                    }

                    val updated = payment.copy(
                        paidAmount = amt,
                        lateFee = fee,
                        paymentMode = selectedMode,
                        receiptNumber = receiptNo.trim(),
                        paymentDate = paymentDate.trim(),
                        notes = notes.trim()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
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
fun DeletePaymentDialog(
    payment: PaymentEntity,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Payment Record", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Text(
                "Are you sure you want to delete payment receipt #${payment.receiptNumber} for ${payment.customerName} (₹${"%.2f".format(payment.paidAmount + payment.lateFee)})?\n\nThis action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete Record", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    viewModel: LicViewModel
) {
    val stats by viewModel.paymentStats.collectAsState()
    val filteredPayments by viewModel.filteredPayments.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val allPolicies by viewModel.policies.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    val searchQuery by viewModel.paymentSearchQuery.collectAsState()
    val selectedDateFilter by viewModel.paymentDateFilter.collectAsState()
    val selectedModeFilter by viewModel.paymentModeFilter.collectAsState()

    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var selectedPolicyForRecord by remember { mutableStateOf<PolicyEntity?>(null) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Banner
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
                            text = "Payment Management",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "LIC Premium Collections & Receipts",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            if (allPolicies.isNotEmpty()) {
                                selectedPolicyForRecord = allPolicies.first()
                                showRecordPaymentDialog = true
                            }
                        },
                        containerColor = AccentOrange,
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("record_payment_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Record Payment")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // DASHBOARD CARDS SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PAYMENT COLLECTION DASHBOARD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )

                    // Grid 1: Total Premium, Total Paid, Remaining Balance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaymentDashboardCard(
                            title = "Total Premium",
                            value = "₹${"%.0f".format(stats.totalPremium)}",
                            icon = Icons.Default.AccountBalanceWallet,
                            color = RoyalBluePrimary,
                            containerColor = RoyalBlueContainer,
                            modifier = Modifier.weight(1f)
                        )
                        PaymentDashboardCard(
                            title = "Total Paid",
                            value = "₹${"%.0f".format(stats.totalPaid)}",
                            icon = Icons.Default.CheckCircle,
                            color = EmeraldGreenSecondary,
                            containerColor = EmeraldGreenContainer,
                            modifier = Modifier.weight(1f)
                        )
                        PaymentDashboardCard(
                            title = "Remaining",
                            value = "₹${"%.0f".format(stats.remainingBalance)}",
                            icon = Icons.Default.PendingActions,
                            color = AccentOrange,
                            containerColor = AccentOrangeContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Grid 2: Outstanding, Today's Collection, Monthly Collection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaymentDashboardCard(
                            title = "Outstanding",
                            value = "₹${"%.0f".format(stats.outstandingAmount)}",
                            icon = Icons.Default.WarningAmber,
                            color = ErrorRed,
                            containerColor = ErrorRedContainer,
                            modifier = Modifier.weight(1f)
                        )
                        PaymentDashboardCard(
                            title = "Today's Collection",
                            value = "₹${"%.0f".format(stats.todayCollection)}",
                            icon = Icons.Default.Today,
                            color = EmeraldGreenSecondary,
                            containerColor = EmeraldGreenContainer,
                            modifier = Modifier.weight(1f)
                        )
                        PaymentDashboardCard(
                            title = "Monthly Collection",
                            value = "₹${"%.0f".format(stats.monthlyCollection)}",
                            icon = Icons.Default.DateRange,
                            color = RoyalBluePrimary,
                            containerColor = RoyalBlueContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Payment Progress Indicator
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Overall Payment Progress",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${"%.1f".format(stats.paymentProgressPercent)}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreenSecondary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (stats.paymentProgressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = EmeraldGreenSecondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Collected ₹${"%.0f".format(stats.totalPaid)} of total target ₹${"%.0f".format(stats.totalPremium)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // SEARCH & FILTERS SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setPaymentSearchQuery(it) },
                        placeholder = { Text("Search by Customer Name, Policy #, Receipt #") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setPaymentSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_search_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Date Filters
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Date Filter",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PaymentDateFilter.values()) { filter ->
                                val label = when (filter) {
                                    PaymentDateFilter.ALL -> "All Time"
                                    PaymentDateFilter.TODAY -> "Today"
                                    PaymentDateFilter.THIS_WEEK -> "This Week"
                                    PaymentDateFilter.THIS_MONTH -> "This Month"
                                }
                                FilterChip(
                                    selected = selectedDateFilter == filter,
                                    onClick = { viewModel.setPaymentDateFilter(filter) },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }

                    // Mode Filters
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Payment Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PaymentModeFilter.values()) { mode ->
                                val label = when (mode) {
                                    PaymentModeFilter.ALL -> "All Modes"
                                    PaymentModeFilter.CASH -> "Cash"
                                    PaymentModeFilter.UPI -> "UPI"
                                    PaymentModeFilter.BANK_TRANSFER -> "Bank Transfer"
                                    PaymentModeFilter.CHEQUE -> "Cheque"
                                }
                                FilterChip(
                                    selected = selectedModeFilter == mode,
                                    onClick = { viewModel.setPaymentModeFilter(mode) },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // TIMELINE HISTORY SECTION HEADER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PAYMENT HISTORY TIMELINE (${filteredPayments.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            if (filteredPayments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No payment records found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Try adjusting search or filters, or record a new payment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredPayments, key = { _, item -> item.id }) { index, payment ->
                    PaymentTimelineItem(
                        payment = payment,
                        isLast = index == filteredPayments.lastIndex,
                        onViewReceipt = { selectedPaymentForReceipt = payment },
                        onEdit = { editingPayment = payment },
                        onDelete = { deletingPayment = payment },
                        onShare = {
                            val shareText = generateReceiptShareText(
                                payment = payment,
                                agentName = agentProfile?.agentName ?: "LIC Agent",
                                agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
                                branch = agentProfile?.branchName ?: "LIC Branch"
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showRecordPaymentDialog && selectedPolicyForRecord != null) {
        PaymentCollectionDialog(
            policy = selectedPolicyForRecord!!,
            existingPayments = allPayments,
            onDismiss = { showRecordPaymentDialog = false },
            onCollect = { amount, lateFee, mode, receiptNo, notes ->
                viewModel.collectPremium(
                    policy = selectedPolicyForRecord!!,
                    paidAmount = amount,
                    lateFee = lateFee,
                    paymentMode = mode,
                    receiptNo = receiptNo,
                    notes = notes,
                    onSuccess = { showRecordPaymentDialog = false }
                )
            }
        )
    }

    editingPayment?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            existingPayments = allPayments,
            onDismiss = { editingPayment = null },
            onSave = { updated ->
                viewModel.updatePayment(updated) {
                    editingPayment = null
                }
            }
        )
    }

    deletingPayment?.let { payment ->
        DeletePaymentDialog(
            payment = payment,
            onDismiss = { deletingPayment = null },
            onConfirmDelete = {
                viewModel.deletePayment(payment) {
                    deletingPayment = null
                }
            }
        )
    }

    selectedPaymentForReceipt?.let { payment ->
        ReceiptDialog(
            payment = payment,
            agentName = agentProfile?.agentName ?: "Pintu Ojha",
            agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
            branch = agentProfile?.branchName ?: "Branch 883 (City Center)",
            onDismiss = { selectedPaymentForReceipt = null }
        )
    }
}

@Composable
fun PaymentDashboardCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PaymentTimelineItem(
    payment: PaymentEntity,
    isLast: Boolean,
    onViewReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val totalPaid = payment.paidAmount + payment.lateFee

    val (modeIcon, modeColor) = when (payment.paymentMode.uppercase()) {
        "UPI" -> Icons.Default.QrCodeScanner to RoyalBluePrimary
        "CASH" -> Icons.Default.Payments to EmeraldGreenSecondary
        "CHEQUE" -> Icons.AutoMirrored.Filled.ReceiptLong to AccentOrange
        else -> Icons.Default.AccountBalance to RoyalBlueLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Timeline Column (Node + Connecting Line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = modeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    modeIcon,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.padding(6.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(110.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Content Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .shadow(2.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Top Row: Customer Name & Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payment.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Policy #${payment.policyNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${"%.2f".format(totalPaid)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreenSecondary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = payment.paymentDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Breakdown & Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Tag
                    Surface(
                        color = modeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = payment.paymentMode,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = modeColor
                            )
                        )
                    }

                    // Receipt Tag
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Receipt: ${payment.receiptNumber}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (payment.lateFee > 0) {
                        Surface(
                            color = AccentOrangeContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "+Fine: ₹${"%.0f".format(payment.lateFee)}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnAccentOrangeContainer
                                )
                            )
                        }
                    }
                }

                if (payment.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Notes: ${payment.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onViewReceipt,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoyalBluePrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Receipt", style = MaterialTheme.typography.labelMedium.copy(color = RoyalBluePrimary, fontWeight = FontWeight.Bold))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptDialog(
    payment: PaymentEntity,
    agentName: String,
    agencyCode: String,
    branch: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalPaid = payment.paidAmount + payment.lateFee

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldGreenSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Official Premium Receipt", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        color = RoyalBluePrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "LIFE INSURANCE CORPORATION OF INDIA",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                "OFFICIAL AGENT PREMIUM COLLECTION RECEIPT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentOrangeLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Receipt No: ${payment.receiptNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Date: ${payment.paymentDate}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ReceiptDetailRow("Customer Name", payment.customerName)
                    ReceiptDetailRow("Policy Number", payment.policyNumber)
                    ReceiptDetailRow("Payment Mode", payment.paymentMode)
                    ReceiptDetailRow("Premium Amount", "₹${"%.2f".format(payment.paidAmount)}")
                    if (payment.lateFee > 0) {
                        ReceiptDetailRow("Late Fee / Fine", "₹${"%.2f".format(payment.lateFee)}")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ReceiptDetailRow("Total Paid Amount", "₹${"%.2f".format(totalPaid)}", isHighlight = true)

                    if (payment.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ReceiptDetailRow("Remarks / Ref", payment.notes)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Authorized Issuing Agent:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("$agentName (Code: $agencyCode)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(branch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareText = generateReceiptShareText(
                        payment = payment,
                        agentName = agentName,
                        agencyCode = agencyCode,
                        branch = branch
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Premium Receipt"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Receipt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ReceiptDetailRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isHighlight)
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
            else
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

fun generateReceiptShareText(
    payment: PaymentEntity,
    agentName: String,
    agencyCode: String,
    branch: String
): String {
    val totalPaid = payment.paidAmount + payment.lateFee
    val lateFeeText = if (payment.lateFee > 0) "• Late Fee: ₹${"%.2f".format(payment.lateFee)}\n" else ""
    val remarksText = if (payment.notes.isNotBlank()) "• Remarks: ${payment.notes}\n" else ""

    return "===================================\n" +
            "  LIC INDIA PREMIUM RECEIPT\n" +
            "===================================\n" +
            "• Receipt No: ${payment.receiptNumber}\n" +
            "• Date: ${payment.paymentDate}\n" +
            "• Customer Name: ${payment.customerName}\n" +
            "• Policy Number: ${payment.policyNumber}\n" +
            "• Payment Mode: ${payment.paymentMode}\n" +
            "• Premium Amount: ₹${"%.2f".format(payment.paidAmount)}\n" +
            lateFeeText +
            "• Total Paid: ₹${"%.2f".format(totalPaid)}\n" +
            remarksText +
            "-----------------------------------\n" +
            "Issued By: $agentName\n" +
            "Agency Code: $agencyCode\n" +
            "Branch: $branch\n" +
            "==================================="
}

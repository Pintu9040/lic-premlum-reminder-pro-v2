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
import com.example.ui.components.*
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
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.PaymentDateFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.theme.*
import java.time.LocalDate

/**
 * Calculates the remaining balance for a policy after a specific payment record in chronological order.
 */
fun getRemainingBalanceForPayment(
    payment: PaymentEntity,
    policy: PolicyEntity?,
    allPaymentsForPolicy: List<PaymentEntity>
): Double {
    if (policy == null || policy.premiumAmount <= 0) return 0.0
    val installment = policy.premiumAmount
    val sortedPayments = allPaymentsForPolicy
        .filter { it.policyId == payment.policyId }
        .sortedBy { it.createdAt }

    var cumulativePaid = 0.0
    var remainingBalance = installment

    for (p in sortedPayments) {
        cumulativePaid += p.paidAmount
        val completedCycles = (cumulativePaid / installment).toInt()
        val paidInCurrentCycle = cumulativePaid - (completedCycles * installment)

        remainingBalance = if (paidInCurrentCycle > 0) {
            (installment - paidInCurrentCycle).coerceAtLeast(0.0)
        } else {
            0.0
        }

        if (p.id == payment.id) {
            return remainingBalance
        }
    }
    return remainingBalance
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCollectionDialog(
    policy: PolicyEntity? = null,
    customersList: List<CustomerEntity> = emptyList(),
    policiesList: List<PolicyEntity> = emptyList(),
    existingPayments: List<PaymentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCollect: (amount: Double, lateFee: Double, mode: String, receiptNo: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onSavePayment: ((policy: PolicyEntity, paidAmount: Double, mode: String, date: String, notes: String) -> Unit)? = null
) {
    var customerSearchQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(
            if (policy != null) customersList.find { it.id == policy.customerId }
            else customersList.firstOrNull()
        )
    }

    val availablePolicies = remember(selectedCustomer, policiesList, policy) {
        if (selectedCustomer != null) {
            policiesList.filter { it.customerId == selectedCustomer!!.id }
        } else if (policy != null) {
            listOf(policy)
        } else {
            policiesList
        }
    }

    var selectedPolicy by remember {
        mutableStateOf<PolicyEntity?>(
            policy ?: availablePolicies.firstOrNull() ?: policiesList.firstOrNull()
        )
    }

    // Auto calculate previous payments for this policy
    val paymentsForPolicy = remember(selectedPolicy, existingPayments) {
        if (selectedPolicy != null) {
            existingPayments.filter { it.policyId == selectedPolicy!!.id }
        } else {
            emptyList()
        }
    }

    val totalPaidSoFar = remember(paymentsForPolicy) {
        paymentsForPolicy.sumOf { it.paidAmount }
    }

    val installmentAmount = selectedPolicy?.premiumAmount ?: 0.0
    val completedCycles = if (installmentAmount > 0) (totalPaidSoFar / installmentAmount).toInt() else 0
    val paidInCurrentCycle = if (installmentAmount > 0) totalPaidSoFar - (completedCycles * installmentAmount) else 0.0
    val currentRemainingBeforeNew = if (installmentAmount > 0) (installmentAmount - paidInCurrentCycle).coerceAtLeast(0.0) else 0.0

    var amountStr by remember {
        mutableStateOf(if (currentRemainingBeforeNew > 0) currentRemainingBeforeNew.toString() else installmentAmount.toString())
    }
    var paymentDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var selectedMode by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")

    val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
    val newRemainingBalance = (currentRemainingBeforeNew - enteredAmount).coerceAtLeast(0.0)
    val isCompletingCycle = enteredAmount >= currentRemainingBeforeNew && currentRemainingBeforeNew > 0

    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showPolicyDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Record Payment",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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

                // 1. CUSTOMER SEARCH & SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "1. Customer Search",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: customerSearchQuery,
                        onValueChange = {
                            customerSearchQuery = it
                            showCustomerDropdown = true
                            if (selectedCustomer != null && selectedCustomer?.name != it) {
                                selectedCustomer = null
                                selectedPolicy = null
                            }
                        },
                        placeholder = { Text("Search or select customer...") },
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showCustomerDropdown = !showCustomerDropdown }) {
                                Icon(
                                    if (showCustomerDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_customer_search"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showCustomerDropdown && customersList.isNotEmpty()) {
                        val filteredCustomers = customersList.filter {
                            it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                    it.mobile.contains(customerSearchQuery)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                filteredCustomers.forEach { cust ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedCustomer = cust
                                                customerSearchQuery = cust.name
                                                showCustomerDropdown = false
                                                val matchingPol = policiesList.firstOrNull { it.customerId == cust.id }
                                                selectedPolicy = matchingPol
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(cust.mobile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                // 2. POLICY SEARCH & SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "2. Policy Search",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = selectedPolicy?.let { "${it.planName} (#${it.policyNumber})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select policy...") },
                        leadingIcon = { Icon(Icons.Default.Policy, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPolicyDropdown = !showPolicyDropdown }) {
                                Icon(
                                    if (showPolicyDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_policy_search"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showPolicyDropdown && availablePolicies.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                availablePolicies.forEach { pol ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPolicy = pol
                                                showPolicyDropdown = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = AccentOrange)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("${pol.planName} • Policy #${pol.policyNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Premium: ₹${pol.premiumAmount} • Due: ${pol.dueDate}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                // 3. AUTO PREMIUM DUE SUMMARY CARD
                selectedPolicy?.let { pol ->
                    Surface(
                        color = RoyalBlueContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Premium Due (Auto):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("₹${"%.2f".format(installmentAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Paid In Current Cycle:", style = MaterialTheme.typography.bodySmall)
                                Text("₹${"%.2f".format(paidInCurrentCycle)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Balance Due:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("₹${"%.2f".format(currentRemainingBeforeNew)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = AccentOrange))
                            }
                        }
                    }
                }

                // 4. AMOUNT RECEIVED INPUT
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "4. Amount Received (₹)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            errorMessage = null
                        },
                        placeholder = { Text("Enter amount received...") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = amountStr == currentRemainingBeforeNew.toString(),
                            onClick = { amountStr = currentRemainingBeforeNew.toString() },
                            label = { Text("Full Balance (₹${"%.0f".format(currentRemainingBeforeNew)})", style = MaterialTheme.typography.labelSmall) }
                        )
                        if (currentRemainingBeforeNew > 1000) {
                            FilterChip(
                                selected = amountStr == (currentRemainingBeforeNew / 2).toString(),
                                onClick = { amountStr = (currentRemainingBeforeNew / 2).toString() },
                                label = { Text("50% (₹${"%.0f".format(currentRemainingBeforeNew / 2)})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // 5. REMAINING BALANCE (AUTO CALCULATED LIVE BADGE)
                Surface(
                    color = if (newRemainingBalance == 0.0) EmeraldGreenContainer else AccentOrangeContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remaining Balance (Auto):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (newRemainingBalance == 0.0) OnEmeraldGreenContainer else OnAccentOrangeContainer
                            )
                            Text(
                                text = "₹${"%.2f".format(newRemainingBalance)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (newRemainingBalance == 0.0) EmeraldGreenSecondary else AccentOrange
                                )
                            )
                        }
                        if (isCompletingCycle || newRemainingBalance == 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Installment Paid! Next due date will advance automatically.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = EmeraldGreenSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // 6. PAYMENT DATE
                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it; errorMessage = null },
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // 7. PAYMENT MODE (Cash / UPI / Bank / Cheque)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Payment Mode",
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
                                label = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 8. NOTES
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Cheque No / Reference") },
                    placeholder = { Text("Optional payment remarks...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPolicy == null) {
                        errorMessage = "Please select a valid customer and policy."
                        return@Button
                    }
                    if (enteredAmount <= 0) {
                        errorMessage = "Please enter a valid payment amount greater than ₹0."
                        return@Button
                    }

                    val generatedReceiptNo = "REC-${System.currentTimeMillis()}"

                    if (onSavePayment != null) {
                        onSavePayment(selectedPolicy!!, enteredAmount, selectedMode, paymentDate, notes)
                    } else {
                        onCollect(enteredAmount, 0.0, selectedMode, generatedReceiptNo, notes)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_payment_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Payment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {}
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
    var selectedMode by remember { mutableStateOf(payment.paymentMode) }
    var paymentDate by remember { mutableStateOf(payment.paymentDate) }
    var notes by remember { mutableStateOf(payment.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val modeOptions = listOf("Cash", "UPI", "Bank", "Cheque")
    val paidAmount = amountStr.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Payment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
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
                    label = { Text("Amount Paid (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it; errorMessage = null },
                    label = { Text("Payment Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Payment Mode", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeOptions.forEach { mode ->
                        FilterChip(
                            selected = selectedMode.equals(mode, ignoreCase = true),
                            onClick = { selectedMode = mode },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

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

                    if (amt <= 0) {
                        errorMessage = "Amount must be greater than ₹0."
                        return@Button
                    }

                    val updated = payment.copy(
                        paidAmount = amt,
                        paymentMode = selectedMode,
                        paymentDate = paymentDate.trim(),
                        notes = notes.trim()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
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
                "Are you sure you want to delete payment of ₹${"%.2f".format(payment.paidAmount)} made on ${payment.paymentDate} for ${payment.customerName}?\n\nThis will recalculate remaining balances and update the policy automatically.",
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
    val allCustomers by viewModel.customers.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    val searchQuery by viewModel.paymentSearchQuery.collectAsState()
    val selectedDateFilter by viewModel.paymentDateFilter.collectAsState()
    val selectedModeFilter by viewModel.paymentModeFilter.collectAsState()

    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }

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
                            text = "LIC Partial Payments & History",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        )
                    }

                    Button(
                        onClick = { showRecordPaymentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("record_payment_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Record Payment")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // DASHBOARD METRICS SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PAYMENT SUMMARY & REPORTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                            title = "Total Paid",
                            value = "₹${"%.0f".format(stats.totalPaid)}",
                            icon = Icons.Default.CheckCircle,
                            color = EmeraldGreenSecondary,
                            containerColor = EmeraldGreenContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SEARCH & FILTERS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setPaymentSearchQuery(it) },
                        placeholder = { Text("Search customer name, policy number, or notes...") },
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
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Date Filters
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

                    // Mode Filters
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PaymentModeFilter.values()) { mode ->
                            val label = when (mode) {
                                PaymentModeFilter.ALL -> "All Modes"
                                PaymentModeFilter.CASH -> "Cash"
                                PaymentModeFilter.UPI -> "UPI"
                                PaymentModeFilter.BANK_TRANSFER -> "Bank"
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

            // TIMELINE SECTION HEADER
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
                    StandardEmptyState(
                        title = "No Payments Found",
                        description = "No premium payment receipts match your search or date filter. Tap 'Record Payment' to enter a transaction.",
                        icon = Icons.Outlined.Payments,
                        actionLabel = "Record New Payment",
                        onActionClick = { showRecordPaymentDialog = true }
                    )
                }
            } else {
                itemsIndexed(filteredPayments, key = { _, item -> item.id }) { index, payment ->
                    val matchingPolicy = allPolicies.find { it.id == payment.policyId }
                    val remainingBal = getRemainingBalanceForPayment(payment, matchingPolicy, allPayments)

                    PaymentTimelineItem(
                        payment = payment,
                        remainingBalance = remainingBal,
                        isLast = index == filteredPayments.lastIndex,
                        onViewReceipt = { selectedPaymentForReceipt = payment },
                        onEdit = { editingPayment = payment },
                        onDelete = { deletingPayment = payment },
                        onShare = {
                            val shareText = generateReceiptShareText(
                                payment = payment,
                                agentName = agentProfile?.agentName ?: "LIC Agent",
                                agencyCode = agentProfile?.agencyCode ?: "",
                                branch = agentProfile?.branchName ?: ""
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Premium Receipt"))
                        }
                    )
                }
            }
        }
    }

    // RECORD PAYMENT DIALOG
    if (showRecordPaymentDialog) {
        PaymentCollectionDialog(
            customersList = allCustomers,
            policiesList = allPolicies,
            existingPayments = allPayments,
            onDismiss = { showRecordPaymentDialog = false },
            onSavePayment = { pol, paidAmt, mode, date, notes ->
                viewModel.collectPremium(
                    policy = pol,
                    paidAmount = paidAmt,
                    paymentMode = mode,
                    paymentDate = date,
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
            agentName = agentProfile?.agentName ?: "LIC Agent",
            agencyCode = agentProfile?.agencyCode ?: "LIC-AGENT-89421",
            branch = agentProfile?.branchName ?: "LIC Branch",
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
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
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
    remainingBalance: Double,
    isLast: Boolean,
    onViewReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {}
) {
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
        // Node + Connecting Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = modeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    modeIcon,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.padding(5.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(115.dp)
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
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Customer Name & Paid Amount
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
                            text = "Paid: ₹${"%.2f".format(payment.paidAmount)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreenSecondary,
                                fontSize = 16.sp
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

                // Mode Tag & Remaining Balance Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = modeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = payment.paymentMode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = modeColor
                            )
                        )
                    }

                    Surface(
                        color = if (remainingBalance == 0.0) EmeraldGreenContainer else AccentOrangeContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (remainingBalance == 0.0) "Remaining Balance: ₹0 (Paid)" else "Remaining Balance: ₹${"%.2f".format(remainingBalance)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (remainingBalance == 0.0) OnEmeraldGreenContainer else OnAccentOrangeContainer
                            )
                        )
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

                // Actions: View, Edit, Delete, Share
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
                        Text("View", style = MaterialTheme.typography.labelMedium.copy(color = RoyalBluePrimary, fontWeight = FontWeight.Bold))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }

                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                        }

                        TextButton(
                            onClick = onShare,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = EmeraldGreenSecondary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Share", style = MaterialTheme.typography.labelSmall)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Payment Receipt", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
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
                                "PREMIUM COLLECTION RECEIPT",
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
                            "Date: ${payment.paymentDate}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Mode: ${payment.paymentMode}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ReceiptDetailRow("Customer Name", payment.customerName)
                    ReceiptDetailRow("Policy Number", payment.policyNumber)
                    ReceiptDetailRow("Amount Paid", "₹${"%.2f".format(payment.paidAmount)}", isHighlight = true)

                    if (payment.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ReceiptDetailRow("Notes", payment.notes)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Authorized LIC Agent:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("$agentName ($agencyCode)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
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
                    context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Receipt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
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
    val remarksText = if (payment.notes.isNotBlank()) "• Notes: ${payment.notes}\n" else ""

    return "===================================\n" +
            "  LIC PREMIUM COLLECTION RECEIPT\n" +
            "===================================\n" +
            "• Date: ${payment.paymentDate}\n" +
            "• Customer Name: ${payment.customerName}\n" +
            "• Policy Number: ${payment.policyNumber}\n" +
            "• Payment Mode: ${payment.paymentMode}\n" +
            "• Amount Paid: ₹${"%.2f".format(payment.paidAmount)}\n" +
            remarksText +
            "-----------------------------------\n" +
            "Issued By: $agentName ($agencyCode)\n" +
            "Branch: $branch\n" +
            "==================================="
}

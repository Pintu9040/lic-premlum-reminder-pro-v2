package com.example.ui.payment

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.local.AppSettingsManager
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.theme.*
import com.example.util.PaymentAllocationEngine
import com.example.util.QrCodeGenerator
import com.example.util.SecurityUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

fun isValidUpiVpa(vpa: String): Boolean {
    val clean = vpa.trim()
    if (clean.isBlank() || !clean.contains("@") || clean.length < 5 || clean.contains(" ")) return false
    val parts = clean.split("@")
    if (parts.size != 2) return false
    return parts[0].isNotBlank() && parts[1].isNotBlank()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentQrScreen(
    viewModel: LicViewModel,
    initialPolicy: PolicyEntity? = null,
    initialCustomer: CustomerEntity? = null,
    initialAmount: Double = 0.0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()

    DisposableEffect(Unit) {
        SecurityUtils.setSecureFlag(context, true)
        onDispose {
            SecurityUtils.setSecureFlag(context, false)
        }
    }

    // Load initial settings from AppSettingsManager
    val appSettings = remember { AppSettingsManager.getSettings(context) }

    // Active Customer & Policy Selections
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(initialCustomer) }
    var selectedPolicy by remember { mutableStateOf<PolicyEntity?>(initialPolicy) }

    // Auto-resolve selections
    LaunchedEffect(customers, policies, initialPolicy, initialCustomer) {
        if (selectedCustomer == null && initialCustomer != null) {
            selectedCustomer = initialCustomer
        }
        if (selectedPolicy == null && initialPolicy != null) {
            selectedPolicy = initialPolicy
        }
        if (selectedCustomer == null && selectedPolicy != null) {
            selectedCustomer = customers.find { it.id == selectedPolicy?.customerId }
        }
        if (selectedCustomer == null && customers.isNotEmpty()) {
            selectedCustomer = customers.first()
        }
        if (selectedPolicy == null && selectedCustomer != null) {
            val custPolicies = policies.filter { it.customerId == selectedCustomer?.id }
            if (custPolicies.isNotEmpty()) {
                selectedPolicy = custPolicies.first()
            }
        }
    }

    // Editable Account Holder Name state (Persisted in AppSettingsManager)
    var accountHolderNameInput by remember {
        mutableStateOf(appSettings.accountHolderName.ifBlank { "GEETANJALI SUTAR" })
    }

    // Editable Agent UPI VPA state (Persisted in AppSettingsManager)
    var upiVpaInput by remember {
        mutableStateOf(appSettings.upiVpaId.ifBlank { "895412036@lic" })
    }

    // Calculate Actual Due Amount from Policy & Payments
    val policyPayments = remember(payments, selectedPolicy) {
        val polId = selectedPolicy?.id ?: -1L
        val polNum = selectedPolicy?.policyNumber ?: ""
        payments.filter { it.policyId == polId || (polNum.isNotBlank() && it.policyNumber.equals(polNum, ignoreCase = true)) }
            .sortedByDescending { it.paymentDate }
    }

    val dueSummary = remember(selectedPolicy, policyPayments) {
        if (selectedPolicy != null) {
            PaymentAllocationEngine.calculateCurrentDueSummary(selectedPolicy!!, policyPayments)
        } else {
            null
        }
    }

    val actualDueAmount: Double = remember(dueSummary, initialAmount, selectedPolicy) {
        val calcDue = dueSummary?.outstanding ?: 0.0
        if (calcDue > 0) calcDue
        else if (initialAmount > 0) initialAmount
        else (selectedPolicy?.premiumAmount ?: 24000.0)
    }

    val totalPaidForPolicy = dueSummary?.totalPaid ?: policyPayments.sumOf { it.paidAmount }

    // Editable Payment Amount State
    var paymentAmountInput by remember { mutableStateOf("") }

    // Keep Payment Amount default synced with Actual Due Amount when policy changes
    LaunchedEffect(actualDueAmount, selectedPolicy) {
        paymentAmountInput = "%.0f".format(actualDueAmount)
    }

    // Validations
    val parsedPaymentAmount = paymentAmountInput.trim().toDoubleOrNull()
    val paymentAmountError: String? = when {
        paymentAmountInput.isBlank() -> "Payment amount is required"
        parsedPaymentAmount == null -> "Enter a valid numeric amount"
        parsedPaymentAmount <= 0.0 -> "Payment amount must be greater than ₹0"
        parsedPaymentAmount > actualDueAmount -> "Cannot exceed Due Amount (₹${"%.0f".format(actualDueAmount)})"
        else -> null
    }

    val accountHolderError: String? = if (accountHolderNameInput.isBlank()) {
        "Account Holder Name is required"
    } else null

    val upiVpaError: String? = if (!isValidUpiVpa(upiVpaInput)) {
        "Invalid UPI VPA (e.g. name@upi)"
    } else null

    // Single Source of Truth for Effective Payment Amount
    val effectivePaymentAmount: Double = if (parsedPaymentAmount != null && parsedPaymentAmount > 0.0 && parsedPaymentAmount <= actualDueAmount) {
        parsedPaymentAmount
    } else {
        actualDueAmount
    }

    val formattedPaymentAmount = "%.2f".format(effectivePaymentAmount)

    // Derived Display Strings
    val pNo = selectedPolicy?.policyNumber ?: "663214789"
    val planName = selectedPolicy?.planName ?: "Endowment Plan (914)"
    val cName = selectedCustomer?.name ?: selectedPolicy?.customerName ?: "Amitabh Gupta"

    // Cleaned Account Holder & VPA
    val cleanAccountHolder = accountHolderNameInput.trim().ifBlank { "GEETANJALI SUTAR" }
    val cleanUpiVpa = upiVpaInput.trim().ifBlank { "895412036@lic" }

    // Dynamic UPI Link
    val encodedNote = URLEncoder.encode("LIC Premium Policy $pNo ($cName)", "UTF-8")
    val upiPayLink = "upi://pay?pa=$cleanUpiVpa&pn=${URLEncoder.encode(cleanAccountHolder, "UTF-8")}&am=$formattedPaymentAmount&tn=$encodedNote&cu=INR"

    // Dynamic QR Card Bitmap
    val qrCardBitmap = remember(cleanAccountHolder, cleanUpiVpa, formattedPaymentAmount, pNo, cName) {
        QrCodeGenerator.createBrandedQrCardBitmap(
            accountHolderName = cleanAccountHolder,
            upiId = cleanUpiVpa,
            amount = formattedPaymentAmount,
            policyNumber = pNo,
            customerName = cName
        )
    }

    // Full-screen QR Scanner state
    var showScannerScreen by remember { mutableStateOf(false) }

    // Manual Payment Form state
    var showManualForm by remember { mutableStateOf(false) }
    var manualPayerName by remember { mutableStateOf("") }
    var manualPayerUpiId by remember { mutableStateOf("") }
    var manualUtr by remember { mutableStateOf("") }
    var manualAmount by remember { mutableStateOf("") }
    var manualPaymentMode by remember { mutableStateOf("UPI") }
    var manualNotes by remember { mutableStateOf("") }
    var manualDate by remember { mutableStateOf(LocalDate.now().toString()) }

    LaunchedEffect(effectivePaymentAmount) {
        if (manualAmount.isBlank()) {
            manualAmount = "%.0f".format(effectivePaymentAmount)
        }
    }

    // Dropdown expansion states
    var customerDropdownExpanded by remember { mutableStateOf(false) }
    var policyDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Payment & QR",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "$cName • Policy #$pNo",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("payment_qr_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showScannerScreen = true }, modifier = Modifier.testTag("open_qr_scanner")) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalBluePrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            // ==============================================================
            // 1. CUSTOMER & POLICY SELECTION CARD
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary)
                        Text(
                            text = "Customer & Policy Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // Customer Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Customer Name") },
                            trailingIcon = {
                                IconButton(onClick = { customerDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Customer")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { customerDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp)
                        )

                        DropdownMenu(
                            expanded = customerDropdownExpanded,
                            onDismissRequest = { customerDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            if (customers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Amitabh Gupta (Default Client)") },
                                    onClick = { customerDropdownExpanded = false }
                                )
                            } else {
                                customers.forEach { cust ->
                                    DropdownMenuItem(
                                        text = { Text("${cust.name} (${cust.mobile})") },
                                        onClick = {
                                            selectedCustomer = cust
                                            val custPolicies = policies.filter { it.customerId == cust.id }
                                            selectedPolicy = custPolicies.firstOrNull()
                                            customerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Policy Selector Dropdown
                    val availablePolicies = remember(selectedCustomer, policies) {
                        if (selectedCustomer != null) {
                            policies.filter { it.customerId == selectedCustomer?.id }
                        } else {
                            policies
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = "Policy #$pNo — $planName",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Policy Number & Plan") },
                            trailingIcon = {
                                IconButton(onClick = { policyDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Policy")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { policyDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp)
                        )

                        DropdownMenu(
                            expanded = policyDropdownExpanded,
                            onDismissRequest = { policyDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            if (availablePolicies.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Policy #663214789 — Endowment Plan (914)") },
                                    onClick = { policyDropdownExpanded = false }
                                )
                            } else {
                                availablePolicies.forEach { pol ->
                                    DropdownMenuItem(
                                        text = { Text("Policy #${pol.policyNumber} - ${pol.planName} (₹${pol.premiumAmount.toInt()})") },
                                        onClick = {
                                            selectedPolicy = pol
                                            policyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // 2. DUE AMOUNT (READ-ONLY) & PAYMENT AMOUNT (EDITABLE)
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldGreenSecondary)
                        Text(
                            text = "Amount Breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // READ-ONLY ACTUAL DUE AMOUNT DISPLAY
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Actual Due Amount (Read-Only)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Due Amount: ₹${"%,.2f".format(actualDueAmount)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Surface(
                                color = RoyalBluePrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Official Outstanding",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RoyalBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // EDITABLE PAYMENT AMOUNT FIELD
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = paymentAmountInput,
                            onValueChange = { newValue ->
                                // Filter out invalid characters
                                val filtered = newValue.filter { it.isDigit() || it == '.' }
                                paymentAmountInput = filtered
                            },
                            label = { Text("Payment Amount (₹)*") },
                            placeholder = { Text("e.g. 10000") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (paymentAmountInput.isNotBlank()) {
                                    IconButton(onClick = { paymentAmountInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear amount")
                                    }
                                }
                            },
                            isError = paymentAmountError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (paymentAmountError != null) {
                            Text(
                                text = paymentAmountError,
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    // RESET TO DUE AMOUNT BUTTON
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                paymentAmountInput = "%.0f".format(actualDueAmount)
                                Toast.makeText(context, "Payment Amount reset to Due Amount (₹${"%.0f".format(actualDueAmount)})", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset to Due Amount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 3. ACCOUNT HOLDER NAME & AGENT UPI VPA (EDITABLE & PERSISTED)
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = AccentOrange)
                        Text(
                            text = "Payee Account & VPA Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // EDITABLE ACCOUNT HOLDER NAME
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = accountHolderNameInput,
                            onValueChange = { newName ->
                                accountHolderNameInput = newName
                                if (newName.isNotBlank()) {
                                    AppSettingsManager.savePaymentAccountHolder(context, newName)
                                }
                            },
                            label = { Text("Account Holder / Payee Name*") },
                            placeholder = { Text("e.g. GEETANJALI SUTAR") },
                            leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isError = accountHolderError != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (accountHolderError != null) {
                            Text(
                                text = accountHolderError,
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    // EDITABLE AGENT UPI VPA
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = upiVpaInput,
                            onValueChange = { newVpa ->
                                upiVpaInput = newVpa
                                if (isValidUpiVpa(newVpa)) {
                                    AppSettingsManager.savePaymentUpiVpa(context, newVpa)
                                }
                            },
                            label = { Text("Agent UPI VPA*") },
                            placeholder = { Text("e.g. 895412036@lic") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isError = upiVpaError != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (upiVpaError != null) {
                            Text(
                                text = upiVpaError,
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 4. DYNAMIC QR CODE DISPLAY CARD
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // Dark Navy
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DYNAMIC UPI PAYMENT QR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentOrangeLight,
                            letterSpacing = 1.2.sp
                        )
                    )

                    // QR Image Canvas Container
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (upiVpaError != null || accountHolderError != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Fix VPA/Name to generate QR",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.DarkGray, fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Image(
                                bitmap = qrCardBitmap.asImageBitmap(),
                                contentDescription = "Auto Generated Dynamic UPI Payment QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Text(
                        text = "Scan with Google Pay / PhonePe / Paytm / BHIM",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Current Amount & VPA Banner
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "₹${"%,.0f".format(effectivePaymentAmount)}",
                                style = TextStyle(
                                    color = EmeraldGreenSecondary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "•",
                                color = Color.Gray
                            )
                            Text(
                                text = "VPA: $cleanUpiVpa",
                                style = TextStyle(
                                    color = AccentOrangeLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 5. QUICK ACTIONS BAR
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Actions:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Row 1: Copy VPA | Copy Link | Save QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UPI VPA", cleanUpiVpa))
                            Toast.makeText(context, "UPI VPA copied: $cleanUpiVpa", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy VPA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Payment Link", upiPayLink))
                            Toast.makeText(context, "Payment link copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val uri = QrCodeGenerator.saveQrBitmapToGallery(context, qrCardBitmap, "LIC_Payment_QR_$pNo")
                            if (uri != null) {
                                Toast.makeText(context, "QR image saved to Pictures/LIC_QR", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save QR image", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Row 2: Share QR | Share Payment Link | Pay UPI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cacheUri = QrCodeGenerator.saveQrBitmapToCache(context, qrCardBitmap)
                            if (cacheUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, cacheUri)
                                    putExtra(Intent.EXTRA_TEXT, "LIC Premium Payment QR for $cName (Policy #$pNo)\nPayment Amount: ₹${"%.0f".format(effectivePaymentAmount)}\nPayment Link: $upiPayLink")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Payment QR"))
                            } else {
                                Toast.makeText(context, "Error generating QR for sharing", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val shareMessage = "LIC Premium Payment\n\n" +
                                    "Customer Name: $cName\n" +
                                    "Policy Number: $pNo\n" +
                                    "Due Amount: ₹${"%.0f".format(actualDueAmount)}\n" +
                                    "Payment Amount: ₹${"%.0f".format(effectivePaymentAmount)}\n" +
                                    "Payee: $cleanAccountHolder\n" +
                                    "UPI VPA: $cleanUpiVpa\n\n" +
                                    "Payment Link:\n$upiPayLink"

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Payment Link"))
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // WhatsApp Green
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Payment Link", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            try {
                                val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiPayLink))
                                val chooser = Intent.createChooser(upiIntent, "Pay with UPI App")
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI Payment App found on this device", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay UPI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Row 3: Scan QR Code Button
                Button(
                    onClick = { showScannerScreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Any QR Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 6. PAYMENT STATUS & MANUAL RECORDING SECTION
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (actualDueAmount <= 0) Icons.Default.CheckCircle else Icons.Default.Pending,
                                contentDescription = null,
                                tint = if (actualDueAmount <= 0) EmeraldGreenSecondary else AccentOrange
                            )
                            Text(
                                text = "PAYMENT STATUS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        Surface(
                            color = if (actualDueAmount <= 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (actualDueAmount <= 0) "Paid / Clear" else "Waiting for Payment",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (actualDueAmount <= 0) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Actual Due Amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.2f".format(actualDueAmount)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = if (actualDueAmount > 0) ErrorRed else EmeraldGreenSecondary))
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Recorded Paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.2f".format(totalPaidForPolicy)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "No automated payment gateway attached. Please record payment manually once verified.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Gateway", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showManualForm = !showManualForm },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showManualForm) "Hide Form" else "Record Payment Manually", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==============================================================
            // 7. MANUAL PAYMENT RECORDING FORM (EXPANDABLE)
            // ==============================================================
            AnimatedVisibility(
                visible = showManualForm,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(1.dp, EmeraldGreenSecondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = EmeraldGreenSecondary)
                            Text("Record Payment Confirmation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualAmount,
                                onValueChange = { manualAmount = it },
                                label = { Text("Amount Received (₹)*") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = manualDate,
                                onValueChange = { manualDate = it },
                                label = { Text("Payment Date") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualPayerName,
                                onValueChange = { manualPayerName = it },
                                label = { Text("Payer Name") },
                                placeholder = { Text("e.g. Rajesh Sharma") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = manualPayerUpiId,
                                onValueChange = { manualPayerUpiId = it },
                                label = { Text("Payer UPI ID") },
                                placeholder = { Text("e.g. rajesh@upi") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualUtr,
                                onValueChange = { manualUtr = it },
                                label = { Text("UTR / Transaction ID") },
                                placeholder = { Text("e.g. 408912345678") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            var modeExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = manualPaymentMode,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Payment Mode") },
                                    trailingIcon = {
                                        IconButton(onClick = { modeExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { modeExpanded = true },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                DropdownMenu(
                                    expanded = modeExpanded,
                                    onDismissRequest = { modeExpanded = false }
                                ) {
                                    listOf("UPI", "Cash", "Bank Transfer", "Cheque").forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode) },
                                            onClick = {
                                                manualPaymentMode = mode
                                                modeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = manualNotes,
                            onValueChange = { manualNotes = it },
                            label = { Text("Notes / Reference") },
                            placeholder = { Text("Optional notes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val amt = manualAmount.toDoubleOrNull() ?: 0.0
                                val pol = selectedPolicy
                                if (pol == null) {
                                    Toast.makeText(context, "Please select a valid policy first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (amt <= 0) {
                                    Toast.makeText(context, "Please enter a valid payment amount", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val currentTime = try {
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                                } catch (e: Exception) { "" }

                                viewModel.collectPremium(
                                    policy = pol,
                                    paidAmount = amt,
                                    paymentMode = manualPaymentMode,
                                    paymentDate = manualDate,
                                    notes = manualNotes,
                                    payerName = manualPayerName.trim(),
                                    payerUpiId = manualPayerUpiId.trim(),
                                    utrNumber = manualUtr.trim(),
                                    verificationType = "Manually Recorded",
                                    paymentTime = currentTime,
                                    onSuccess = {
                                        showManualForm = false
                                        manualPayerName = ""
                                        manualPayerUpiId = ""
                                        manualUtr = ""
                                        manualNotes = ""
                                        Toast.makeText(context, "✓ Payment recorded successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✓ Confirm & Record Payment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 8. PAYMENT TRANSACTION HISTORY SECTION
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            text = "PAYMENT TRANSACTIONS (${policyPayments.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Text(
                            text = "Policy #$pNo",
                            style = MaterialTheme.typography.labelMedium.copy(color = RoyalBluePrimary)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (policyPayments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No payment transactions recorded for this policy yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    } else {
                        policyPayments.forEach { payment ->
                            PaymentTransactionItemCard(
                                payment = payment,
                                agentName = cleanAccountHolder,
                                onShareReceipt = {
                                    val shareText = "LIC Premium Receipt\n" +
                                            "Customer: ${payment.customerName}\n" +
                                            "Policy: ${payment.policyNumber}\n" +
                                            "Amount Paid: ₹${payment.paidAmount}\n" +
                                            "Mode: ${payment.paymentMode}\n" +
                                            "Date: ${payment.paymentDate}\n" +
                                            "Receipt No: ${payment.receiptNumber}\n" +
                                            "Payer: ${if (payment.payerName.isNotBlank()) payment.payerName else "Not available"}\n" +
                                            "UTR: ${if (payment.utrNumber.isNotBlank()) payment.utrNumber else "Not available"}\n" +
                                            "Verification: ${payment.verificationType}"

                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Transaction Receipt"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Full-screen QR Scanner Screen Dialog
    if (showScannerScreen) {
        FullScreenQrScannerDialog(
            onDismiss = { showScannerScreen = false },
            onVpaScanned = { scannedVpa ->
                upiVpaInput = scannedVpa
                AppSettingsManager.savePaymentUpiVpa(context, scannedVpa)
                Toast.makeText(context, "UPI VPA updated to: $scannedVpa", Toast.LENGTH_SHORT).show()
                showScannerScreen = false
            }
        )
    }
}

// ============================================================================
// FULL-SCREEN QR SCANNER COMPOSABLE
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenQrScannerDialog(
    onDismiss: () -> Unit,
    onVpaScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var scannedResultText by remember { mutableStateOf<String?>(null) }
    var isScanned by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan QR Code", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                cameraRef?.let { camera ->
                                    if (camera.cameraInfo.hasFlashUnit()) {
                                        isTorchEnabled = !isTorchEnabled
                                        camera.cameraControl.enableTorch(isTorchEnabled)
                                    } else {
                                        Toast.makeText(context, "Flash not available on this device", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Torch",
                                tint = if (isTorchEnabled) Color.Yellow else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val executor = Executors.newSingleThreadExecutor()
                                    imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { resultText ->
                                        if (!isScanned) {
                                            isScanned = true
                                            scannedResultText = resultText
                                        }
                                    })

                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraRef = camera
                                } catch (e: Exception) {
                                    // Camera binding error handled gracefully
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    )

                    // Target Scanning Frame Overlay
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(3.dp, Color(0xFF22C55E), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusWeak,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                        )
                    }

                    Text(
                        text = "Align QR code inside the frame to scan",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Parsed Decoded Result Sheet
                    if (scannedResultText != null) {
                        val rawText = scannedResultText!!
                        val parsedUpi = remember(rawText) { parseUpiUri(rawText) }

                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary)
                                    Text(
                                        text = if (parsedUpi != null) "UPI QR Code Scanned" else "QR Code Decoded",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                if (parsedUpi != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("UPI VPA: ${parsedUpi.vpa.ifBlank { "Not specified" }}", fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                                            if (parsedUpi.payeeName.isNotBlank()) Text("Payee Name: ${parsedUpi.payeeName}")
                                            if (parsedUpi.amount.isNotBlank()) Text("Amount: ₹${parsedUpi.amount}", fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                                            if (parsedUpi.note.isNotBlank()) Text("Note: ${parsedUpi.note}", fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = rawText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (parsedUpi != null && parsedUpi.vpa.isNotBlank()) {
                                        Button(
                                            onClick = { onVpaScanned(parsedUpi.vpa) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary)
                                        ) {
                                            Text("Use Scanned VPA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Scanned QR", rawText))
                                            Toast.makeText(context, "Scanned QR text copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Copy Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scannedResultText = null
                                            isScanned = false
                                        },
                                        modifier = Modifier.weight(0.8f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Scan Again", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Camera Permission Denied UI
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(56.dp))
                            Text("Camera Permission Required", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "To scan QR codes with your device camera, please grant camera permission.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant Permission", fontWeight = FontWeight.Bold)
                            }

                            TextButton(onClick = onDismiss) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper Data & Function for Parsing UPI URIs
data class ParsedUpiData(
    val vpa: String,
    val payeeName: String,
    val amount: String,
    val currency: String,
    val note: String
)

fun parseUpiUri(uriString: String): ParsedUpiData? {
    if (!uriString.startsWith("upi://pay", ignoreCase = true)) return null
    return try {
        val uri = Uri.parse(uriString)
        val vpa = uri.getQueryParameter("pa") ?: ""
        val payeeName = uri.getQueryParameter("pn")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        val amount = uri.getQueryParameter("am") ?: ""
        val currency = uri.getQueryParameter("cu") ?: "INR"
        val note = uri.getQueryParameter("tn")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        ParsedUpiData(vpa, payeeName, amount, currency, note)
    } catch (e: Exception) {
        null
    }
}

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader()
    private var isScanned = false

    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val buffer = mediaImage.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height

            val source = PlanarYUVLuminanceSource(
                bytes, width, height, 0, 0, width, height, false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(binaryBitmap)
                if (!isScanned && result != null && result.text.isNotBlank()) {
                    isScanned = true
                    onQrCodeScanned(result.text)
                }
            } catch (_: Exception) {
                // No QR code detected in this frame
            }
        }
        imageProxy.close()
    }
}

@Composable
fun PaymentTransactionItemCard(
    payment: PaymentEntity,
    agentName: String,
    onShareReceipt: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Receipt #${payment.receiptNumber}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RoyalBluePrimary
                        )
                    )
                    Text(
                        text = "Date: ${payment.paymentDate} ${payment.paymentTime}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Text(
                    text = "₹${"%,.2f".format(payment.paidAmount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreenSecondary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mode: ${payment.paymentMode} • ${payment.verificationType}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    if (payment.payerName.isNotBlank()) {
                        Text(
                            text = "Payer: ${payment.payerName} ${if (payment.payerUpiId.isNotBlank()) "(${payment.payerUpiId})" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                    if (payment.utrNumber.isNotBlank()) {
                        Text(
                            text = "UTR: ${payment.utrNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange)
                        )
                    }
                }

                IconButton(
                    onClick = onShareReceipt,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Receipt",
                        tint = RoyalBluePrimary
                    )
                }
            }
        }
    }
}


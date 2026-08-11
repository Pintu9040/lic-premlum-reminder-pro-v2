package com.example.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.theme.*
import com.example.util.PaymentAllocationEngine
import com.example.util.QrCodeGenerator
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        com.example.util.SecurityUtils.setSecureFlag(context, true)
        onDispose {
            com.example.util.SecurityUtils.setSecureFlag(context, false)
        }
    }

    // Active Selections
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(initialCustomer) }
    var selectedPolicy by remember { mutableStateOf<PolicyEntity?>(initialPolicy) }

    // Auto-resolve initial selections
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

    // Agent Details
    var editableUpiVpa by remember { mutableStateOf("895412036@lic") }
    val accountHolderName: String = remember(agentProfile) {
        val name = agentProfile?.agentName
        if (!name.isNullOrBlank()) name else "Pintu Ojha"
    }
    val upiVpaId: String = editableUpiVpa.ifBlank { "895412036@lic" }

    // Policy Payments & Outstanding Calculation via PaymentAllocationEngine
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

    val outstandingAmount = dueSummary?.outstanding ?: initialAmount.let { if (it > 0) it else (selectedPolicy?.premiumAmount ?: 0.0) }
    val totalPaidForPolicy = dueSummary?.totalPaid ?: policyPayments.sumOf { it.paidAmount }

    // Payment Amount state for QR Code
    var customAmountText by remember { mutableStateOf("") }

    LaunchedEffect(outstandingAmount, selectedPolicy) {
        if (customAmountText.isBlank() || customAmountText.toDoubleOrNull() == 0.0) {
            customAmountText = if (outstandingAmount > 0) {
                "%.0f".format(outstandingAmount)
            } else {
                "%.0f".format(selectedPolicy?.premiumAmount ?: 5000.0)
            }
        }
    }

    val currentAmount = customAmountText.toDoubleOrNull() ?: outstandingAmount

    // UPI Link Generation
    val pNo = selectedPolicy?.policyNumber ?: "LIC-POL"
    val cName = selectedCustomer?.name ?: selectedPolicy?.customerName ?: "Valued Customer"
    val formattedAmount = "%.2f".format(currentAmount)
    val encodedNote = URLEncoder.encode("LIC Premium Policy $pNo ($cName)", "UTF-8")
    val upiPayLink = "upi://pay?pa=$upiVpaId&pn=${URLEncoder.encode(accountHolderName, "UTF-8")}&am=$formattedAmount&tn=$encodedNote&cu=INR"

    // QR Card Bitmap Generation
    val qrCardBitmap = remember(accountHolderName, upiVpaId, formattedAmount, pNo, cName) {
        QrCodeGenerator.createBrandedQrCardBitmap(
            accountHolderName = accountHolderName,
            upiId = upiVpaId,
            amount = formattedAmount,
            policyNumber = pNo,
            customerName = cName
        )
    }

    // Manual Payment Recording State
    var showManualForm by remember { mutableStateOf(false) }
    var manualPayerName by remember { mutableStateOf("") }
    var manualPayerUpiId by remember { mutableStateOf("") }
    var manualUtr by remember { mutableStateOf("") }
    var manualAmount by remember { mutableStateOf("") }
    var manualPaymentMode by remember { mutableStateOf("UPI") }
    var manualNotes by remember { mutableStateOf("") }
    var manualDate by remember { mutableStateOf(LocalDate.now().toString()) }

    LaunchedEffect(outstandingAmount) {
        if (manualAmount.isBlank()) {
            manualAmount = if (outstandingAmount > 0) "%.0f".format(outstandingAmount) else "%.0f".format(selectedPolicy?.premiumAmount ?: 5000.0)
        }
    }

    // Dropdown Expansion States
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
                        if (selectedCustomer != null || selectedPolicy != null) {
                            Text(
                                text = "${cName} • Policy #${pNo}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
            // 1. CUSTOMER & POLICY SELECTION / DETAILS CARD
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
                            text = "Customer & Payment Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // Customer Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: "Select Customer",
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
                            value = selectedPolicy?.let { "${it.planName} (${it.policyNumber})" } ?: "Select Policy",
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

                    // Editable Premium Amount & Account Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customAmountText,
                            onValueChange = { customAmountText = it },
                            label = { Text("Premium Amount (₹)") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = editableUpiVpa,
                            onValueChange = { editableUpiVpa = it },
                            label = { Text("Agent UPI VPA") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RoyalBlueContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account Holder: $accountHolderName",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = RoyalBluePrimary)
                        )
                        Text(
                            text = "VPA: $upiVpaId",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = AccentOrange)
                        )
                    }
                }
            }

            // ==============================================================
            // 2. LARGE SCANNABLE QR CODE SECTION
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // Dark Navy Theme
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SCAN & PAY VIA UPI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentOrangeLight,
                            letterSpacing = 1.2.sp
                        )
                    )

                    // Large QR Container Canvas
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrCardBitmap.asImageBitmap(),
                            contentDescription = "Auto Generated UPI Payment QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
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
                                text = "₹$formattedAmount",
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
                                text = "VPA: $upiVpaId",
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
            // 3. QUICK ACTIONS BAR
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Quick Actions:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Row 1: Copy VPA, Copy Link, Save QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UPI VPA", upiVpaId))
                            Toast.makeText(context, "UPI VPA copied: $upiVpaId", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Payment Link copied to clipboard!", Toast.LENGTH_SHORT).show()
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

                // Row 2: Share QR, WhatsApp, Pay UPI
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
                                    putExtra(Intent.EXTRA_TEXT, "LIC Premium Payment QR for $cName (Policy #$pNo). Amount: ₹$formattedAmount\nPay Link: $upiPayLink")
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
                            val mobile = selectedCustomer?.whatsapp?.ifBlank { selectedCustomer?.mobile } ?: selectedCustomer?.mobile ?: ""
                            val cleanMobile = mobile.replace(Regex("[^0-9]"), "")
                            val formattedPhone = if (cleanMobile.length == 10) "91$cleanMobile" else cleanMobile

                            val msg = "Dear $cName,\n\nYour LIC Premium of ₹$formattedAmount for Policy #$pNo is due.\n\nUPI VPA: $upiVpaId\nPayment Link: $upiPayLink\n\nPlease reply with transaction details once paid."
                            val encodedMsg = URLEncoder.encode(msg, "UTF-8")

                            try {
                                val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedMsg"))
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Payment Link"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==============================================================
            // 4. PAYMENT STATUS SECTION
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
                                imageVector = if (outstandingAmount <= 0) Icons.Default.CheckCircle else Icons.Default.Pending,
                                contentDescription = null,
                                tint = if (outstandingAmount <= 0) EmeraldGreenSecondary else AccentOrange
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
                            color = if (outstandingAmount <= 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (outstandingAmount <= 0) "Paid / Clear" else "Waiting for Payment",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (outstandingAmount <= 0) Color(0xFF2E7D32) else Color(0xFFE65100)
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
                            Text("Outstanding Amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%.2f".format(outstandingAmount)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = if (outstandingAmount > 0) ErrorRed else EmeraldGreenSecondary))
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Recorded Paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%.2f".format(totalPaidForPolicy)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
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
            // 5. MANUAL PAYMENT RECORDING FORM (EXPANDABLE)
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
                                label = { Text("Payer Name (Actual)") },
                                placeholder = { Text("e.g. Rajesh Sharma") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = manualPayerUpiId,
                                onValueChange = { manualPayerUpiId = it },
                                label = { Text("Payer UPI ID (Actual)") },
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

                            // Payment Mode Dropdown Choice
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
                            label = { Text("Notes / Receipt Reference") },
                            placeholder = { Text("Optional payment notes") },
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
            // 6. PAYMENT TRANSACTION HISTORY SECTION
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
                                agentName = accountHolderName,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldGreenSecondary.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreenSecondary,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = "₹${"%.2f".format(payment.paidAmount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary)
                    )
                }

                Surface(
                    color = if (payment.verificationType == "Verified") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = payment.verificationType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (payment.verificationType == "Verified") Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Date: ${payment.paymentDate}${if (payment.paymentTime.isNotBlank()) " at ${payment.paymentTime}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Mode: ${payment.paymentMode}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = RoyalBluePrimary
                )
            }

            // Payer & UTR details — Display actual or "Not available" strictly as requested
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Payer Name: ${if (payment.payerName.isNotBlank()) payment.payerName else "Not available"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Payer UPI: ${if (payment.payerUpiId.isNotBlank()) payment.payerUpiId else "Not available"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "UTR / Trans ID: ${if (payment.utrNumber.isNotBlank()) payment.utrNumber else "Not available"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Receipt No: ${payment.receiptNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onShareReceipt,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Receipt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

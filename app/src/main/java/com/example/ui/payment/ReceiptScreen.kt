package com.example.ui.payment

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PaymentEntity
import kotlinx.coroutines.launch

// Royal Blue Dark Theme Palette
private val DarkBackground = Color(0xFF0B1120)
private val DarkCardSurface = Color(0xFF1E293B)
private val DarkCardSurfaceVariant = Color(0xFF0F172A)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val EmeraldGreen = Color(0xFF10B981)
private val EmeraldGreenContainer = Color(0xFF064E3B)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val BorderSlate = Color(0xFF334155)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    payment: PaymentEntity? = null,
    customerName: String? = null,
    policyNumber: String? = null,
    planName: String? = null,
    agentName: String = "Rajesh Sharma (Agent Code: 089421A)",
    agencyCode: String = "LIC-089421",
    branch: String = "Branch 883, Gurgaon",
    onDismiss: () -> Unit = {},
    onBack: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showPrintPreview by remember { mutableStateOf(false) }

    // Screen Entrance Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Default Fallback Payment Data if null
    val displayPayment = remember(payment) {
        payment ?: PaymentEntity(
            id = 1001L,
            policyId = 867452901L,
            policyNumber = policyNumber ?: "867452901",
            customerId = 101L,
            customerName = customerName ?: "Rajesh Kumar Sharma",
            paidAmount = 25050.00,
            lateFee = 0.00,
            paymentDate = "2026-08-03",
            paymentMode = "UPI (Google Pay)",
            receiptNumber = "LIC-2026-894210",
            notes = "Official premium receipt. Eligible for Tax rebate under Section 80C."
        )
    }

    val displayCustomerName = customerName ?: displayPayment.customerName
    val displayPolicyNumber = policyNumber ?: displayPayment.policyNumber
    val displayPlanName = planName ?: "Jeevan Umang (Plan 945)"
    val mobileNumber = "+91 98765 43210"
    val formattedDate = "03 Aug 2026, 04:30 PM"
    val nextDueDate = "03 Aug 2027"
    val premiumMode = "Yearly"
    val baseAmount = displayPayment.paidAmount - displayPayment.lateFee
    val lateFee = displayPayment.lateFee

    // If Print Preview is open, show the full-screen Print Preview
    if (showPrintPreview) {
        PrintPreviewScreen(
            payment = displayPayment,
            customerName = displayCustomerName,
            policyNumber = displayPolicyNumber,
            planName = displayPlanName,
            agentName = agentName,
            branch = branch,
            onClose = { showPrintPreview = false }
        )
        return
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Premium Receipt",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "Official e-Receipt #${displayPayment.receiptNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Sharing receipt with customer...")
                            }
                        },
                        modifier = Modifier.testTag("top_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextWhite
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("top_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TextWhite
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkCardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Email Receipt", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Receipt sent via email")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Receipt No.", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RoyalBlueLight) },
                                onClick = {
                                    showMenu = false
                                    clipboardManager.setText(AnnotatedString(displayPayment.receiptNumber))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Receipt number copied")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Help & Support", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = TextMuted) },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(context, "LIC Support: 022-68276827", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            // BOTTOM STICKY ACTION BAR: Download PDF, Share, Print (56dp height, equal width weight(1f))
            Surface(
                color = DarkBackground,
                border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Download PDF Button (Equal Width weight(1f), height 56dp)
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Generating PDF Receipt...")
                                    val reportData = com.example.pdf.PdfReportData(
                                        reportType = com.example.pdf.ReportType.PREMIUM_RECEIPT,
                                        payment = displayPayment,
                                        agentProfile = com.example.data.local.AgentProfileEntity(
                                            agentName = agentName.substringBefore(" ("),
                                            agencyCode = agencyCode,
                                            branchName = branch
                                        )
                                    )
                                    val res = com.example.pdf.PdfReportGenerator.generatePdfReport(context, reportData)
                                    res.onSuccess { file ->
                                        snackbarHostState.showSnackbar("PDF Receipt Saved: ${file.name}")
                                        com.example.pdf.PdfReportGenerator.openPdf(context, file)
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar("PDF Generation Failed: ${err.message}")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, RoyalBlueLight),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("download_pdf_button"),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCardSurfaceVariant
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Download PDF",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Download PDF",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = RoyalBlueLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // 2. Share Button (Equal Width weight(1f), height 56dp)
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Sharing Receipt details...")
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("share_receipt_button"),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCardSurfaceVariant
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = TextWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        // 3. Print Button (Equal Width weight(1f), height 56dp -> Opens Print Preview)
                        Button(
                            onClick = {
                                showPrintPreview = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .shadow(12.dp, shape = RoundedCornerShape(16.dp), ambientColor = RoyalBlueGlow, spotColor = RoyalBlueGlow)
                                .testTag("print_receipt_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(RoyalBluePrimary, RoyalBlueLight)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Print",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Print",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = tween(400)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ==================== 1. RECEIPT BANNER / HEADER CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Decorative Background Gradient Accent
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(RoyalBluePrimary, RoyalBlueLight, EmeraldGreen)
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "RECEIPT NUMBER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(displayPayment.receiptNumber))
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Receipt number copied")
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = displayPayment.receiptNumber,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Receipt Number",
                                            tint = RoyalBlueLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // ENHANCED PROMINENT PAID STATUS BADGE
                                Surface(
                                    color = EmeraldGreenContainer,
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.5.dp, EmeraldGreen)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PAID",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = EmeraldGreen,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(18.dp))

                            // PROMINENT AMOUNT DISPLAY (30sp BOLD)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TOTAL AMOUNT RECEIVED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "₹${String.format("%,.2f", displayPayment.paidAmount)}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 30.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EventAvailable,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Collected on $formattedDate",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 2. RECEIPT CARD (POLICY & CUSTOMER DETAILS) ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBluePrimary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Policy & Customer Details",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReceiptDetailRow("Customer Name", displayCustomerName, isBold = true)
                            ReceiptDetailRow("Mobile Number", mobileNumber)
                            ReceiptDetailRow("Policy Number", displayPolicyNumber, isHighlight = true)
                            ReceiptDetailRow("Plan Name", displayPlanName)
                            ReceiptDetailRow("Premium Mode", premiumMode)
                            ReceiptDetailRow("Payment Date", displayPayment.paymentDate)
                            ReceiptDetailRow("Next Due Date", nextDueDate, valueColor = EmeraldGreen)
                            ReceiptDetailRow("Collected By", agentName)
                            ReceiptDetailRow("LIC Branch", branch)
                        }
                    }
                }

                // ==================== 3. QR CODE PLACEHOLDER CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBluePrimary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Scan & Verify Receipt",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = "LIC Digital Security Verification",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // QR Code Vector Visual Placeholder Box
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "QR Code Placeholder",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(130.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "LIC e-SEAL VERIFIED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RoyalBluePrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Scan this QR code with any camera or scanner app to verify receipt authenticity.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = DarkCardSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text(
                                text = "HASH: 8F9B-402A-8942-LIC2026",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // ==================== 4. TRANSACTION SUMMARY CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Transaction Summary",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReceiptDetailRow("Base Premium Amount", "₹${String.format("%,.2f", baseAmount)}")
                            ReceiptDetailRow("Late Fee / Interest", if (lateFee > 0) "₹${String.format("%,.2f", lateFee)}" else "₹0.00")

                            // PAYMENT MODE AS MATERIAL 3 ASSIST CHIP
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Payment Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                )

                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            text = displayPayment.paymentMode,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextWhite,
                                                fontSize = 12.sp
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = RoyalBlueLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = DarkCardSurfaceVariant,
                                        labelColor = TextWhite
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        borderColor = RoyalBlueLight.copy(alpha = 0.5f),
                                        enabled = true
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Paid",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = "₹${String.format("%,.2f", displayPayment.paidAmount)}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // ==================== 5. NOTES & REMARKS CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = RoyalBlueLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notes & Legal Disclaimer",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (displayPayment.notes.isNotEmpty()) displayPayment.notes else "Official electronic receipt issued by LIC Premium Reminder Pro. Valid for income tax deduction under Section 80C of the Income Tax Act.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// PrintPreviewScreen implementation is located in PrintPreviewScreen.kt

@Composable
private fun ReceiptDetailRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isHighlight: Boolean = false,
    isBadge: Boolean = false,
    valueColor: Color = TextWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextMuted,
                fontSize = 13.sp
            )
        )

        if (isBadge) {
            Surface(
                color = DarkCardSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = RoyalBlueLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isHighlight) RoyalBlueLight else valueColor,
                    fontWeight = if (isBold || isHighlight) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package com.example.ui.payment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PaymentEntity
import kotlinx.coroutines.launch
import java.io.FileOutputStream

// Dark App Bar Theme & Neutral Outer Canvas
private val DarkTopBarBg = Color(0xFF0F172A)
private val DarkSlateCard = Color(0xFF1E293B)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val OuterCanvasBg = Color(0xFFCBD5E1) // Neutral light grey background for A5 paper
private val PaperWhite = Color(0xFFFFFFFF)
private val PrintTextDark = Color(0xFF0F172A)
private val PrintTextMuted = Color(0xFF475569)
private val PrintBorderGray = Color(0xFF94A3B8)
private val PrintTableBg = Color(0xFFF1F5F9)
private val StampGreen = Color(0xFF15803D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    payment: PaymentEntity? = null,
    customerName: String? = null,
    policyNumber: String? = null,
    planName: String? = null,
    agentName: String = "Rajesh Sharma (Code: 089421A)",
    agencyCode: String = "LIC-089421",
    branch: String = "Branch 883, Gurgaon • Northern Zone",
    onClose: () -> Unit = {},
    onBack: () -> Unit = onClose
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fallback payment entity if not provided
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
            paymentMode = "UPI (Google Pay / Ref: 62890145)",
            receiptNumber = "LIC-2026-894210",
            notes = "Official premium receipt. Eligible for Tax rebate under Section 80C."
        )
    }

    val displayCustomerName = customerName ?: displayPayment.customerName
    val displayPolicyNumber = policyNumber ?: displayPayment.policyNumber
    val displayPlanName = planName ?: "Jeevan Umang (Plan 945)"
    val mobileNumber = "+91 98765 43210"
    val customerAddress = "Plot 42, Sector 14, Gurgaon, HR - 122001"
    val uinNumber = "512N312V02"
    val sumAssured = "₹ 10,000,000.00"
    val formattedDate = "03 Aug 2026, 04:30 PM"
    val nextDueDate = "03 Aug 2027"
    val premiumMode = "Yearly"
    val baseAmount = displayPayment.paidAmount - displayPayment.lateFee
    val lateFee = displayPayment.lateFee

    // Pinch-to-zoom & pan interactive gesture states
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = OuterCanvasBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkTopBarBg,
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
                            text = "Print Preview",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "A5 Portrait • 148 × 210 mm",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Sharing A5 Receipt PDF...")
                            }
                        },
                        modifier = Modifier.testTag("preview_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Downloading printable A5 PDF...")
                            }
                        },
                        modifier = Modifier.testTag("preview_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Download PDF",
                            tint = RoyalBlueLight
                        )
                    }
                    IconButton(
                        onClick = {
                            launchAndroidPrintFramework(
                                context = context,
                                receiptNo = displayPayment.receiptNumber,
                                customerName = displayCustomerName,
                                amount = displayPayment.paidAmount.toString(),
                                onSuccess = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Print job sent to system printer")
                                    }
                                }
                            )
                        },
                        modifier = Modifier.testTag("preview_top_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkTopBarBg,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            // STICKY BOTTOM PRINT NOW ACTION BAR
            Surface(
                color = DarkTopBarBg,
                shadowElevation = 16.dp,
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
                    Button(
                        onClick = {
                            launchAndroidPrintFramework(
                                context = context,
                                receiptNo = displayPayment.receiptNumber,
                                customerName = displayCustomerName,
                                amount = displayPayment.paidAmount.toString(),
                                onSuccess = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Sending receipt to system printer...")
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(12.dp, shape = RoundedCornerShape(16.dp), spotColor = RoyalBlueGlow)
                            .testTag("print_now_button"),
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
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Print Now",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // HELPER INSTRUCTION CHIP FOR ZOOM & PAN GESTURES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlateCard)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = RoyalBlueLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pinch to zoom • Double tap to fit",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    )
                }

                if (scale != 1f) {
                    TextButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Reset (${(scale * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoyalBlueLight,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // MAIN PAN & ZOOM VIEWPORT FOR THE A5 PAPER
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 1.6f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 3.0f)
                            if (scale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // A5 PORTRAIT PAPER SHEET (148mm × 210mm)
                Surface(
                    color = PaperWhite,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, PrintBorderGray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // ==================== 1. A5 HEADER ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LIFE INSURANCE CORPORATION OF INDIA",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrintTextDark,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Text(
                                    text = "BHARATIYA JEEVAN BIMA NIGAM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrintTextMuted,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = branch,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = PrintTextDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // LIC BADGE LOGO
                            Surface(
                                color = RoyalBluePrimary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "LIC",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Text(
                                        text = "INDIA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = PrintTextDark, thickness = 1.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // ==================== 2. RECEIPT TITLE & STAMP ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "OFFICIAL PREMIUM RECEIPT",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrintTextDark,
                                        fontSize = 13.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = "Valid for Tax Benefit u/s 80C",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PrintTextMuted,
                                        fontSize = 10.sp
                                    )
                                )
                            }

                            // STAMP: PAID - ACKNOWLEDGED
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(2.dp, StampGreen)
                            ) {
                                Text(
                                    text = "PAID - ACKNOWLEDGED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = StampGreen,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ==================== 3. RECEIPT & CUSTOMER DETAILS BOX ====================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrintTableBg, shape = RoundedCornerShape(4.dp))
                                .border(1.dp, PrintBorderGray, shape = RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PrintInfoGridRow("Receipt No:", displayPayment.receiptNumber, "Date & Time:", formattedDate)
                            PrintInfoGridRow("Customer Name:", displayCustomerName, "Mobile No:", mobileNumber)
                            PrintInfoGridRow("Address:", customerAddress, "Branch Code:", agencyCode)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ==================== 4. POLICY SPECIFICATIONS ====================
                        Text(
                            text = "POLICY SPECIFICATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PrintTextDark,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PrintBorderGray, shape = RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PrintInfoGridRow("Policy Number:", displayPolicyNumber, "Plan Name:", displayPlanName)
                            PrintInfoGridRow("UIN Number:", uinNumber, "Premium Mode:", premiumMode)
                            PrintInfoGridRow("Sum Assured:", sumAssured, "Next Due Date:", nextDueDate)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ==================== 5. PAYMENT BREAKDOWN TABLE ====================
                        Text(
                            text = "PAYMENT BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PrintTextDark,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PrintTextDark, shape = RoundedCornerShape(4.dp))
                        ) {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE2E8F0))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Particulars / Description",
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrintTextDark,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "Amount (₹)",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrintTextDark,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            HorizontalDivider(color = PrintTextDark, thickness = 1.dp)

                            // Table Rows
                            PrintTableRow("Base Installment Premium", String.format("%,.2f", baseAmount))
                            PrintTableRow("Late Fee / Interest / Fine", String.format("%,.2f", lateFee))
                            PrintTableRow("GST / Taxes (18% Included)", "₹ 0.00")

                            HorizontalDivider(color = PrintTextDark, thickness = 1.dp)

                            // Highlighted Total Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PrintTextDark)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL AMOUNT PAID",
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Text(
                                    text = "₹${String.format("%,.2f", displayPayment.paidAmount)}",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Payment Mode: ${displayPayment.paymentMode}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrintTextDark,
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ==================== 6. VERIFICATION & SIGNATURE AREA ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // QR CODE VERIFICATION BOX
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "QR Verification",
                                    tint = PrintTextDark,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = "Scan to Verify Receipt",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PrintTextMuted,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "VERIFY: LIC-8F9B-2026",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PrintTextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp
                                    )
                                )
                            }

                            // AGENT & CUSTOMER SIGNATURE LINES
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = agentName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrintTextDark,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(1.dp)
                                        .background(PrintTextDark)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Authorized Servicing Agent Stamp & Sign",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PrintTextMuted,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = PrintBorderGray)
                        Spacer(modifier = Modifier.height(6.dp))

                        // FOOTER DISCLAIMER
                        Text(
                            text = "Note: Subject to realization of Cheque / Electronic Funds Transfer. Computer generated e-Receipt valid under Income Tax Act 1961 Section 80C. Thank you for choosing Life Insurance Corporation of India.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrintTextMuted,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// HELPER COMPONENTS FOR PRINT PREVIEW A5 PAPER
@Composable
private fun PrintInfoGridRow(
    label1: String, val1: String,
    label2: String, val2: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = label1,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PrintTextMuted,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = val1,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrintTextDark,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = label2,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PrintTextMuted,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = val2,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrintTextDark,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PrintTableRow(particulars: String, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = particulars,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall.copy(
                color = PrintTextDark,
                fontSize = 11.sp
            )
        )
        Text(
            text = amount,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall.copy(
                color = PrintTextDark,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        )
    }
}

// ANDROID PRINT MANAGER FRAMEWORK LAUNCHER
private fun launchAndroidPrintFramework(
    context: Context,
    receiptNo: String,
    customerName: String,
    amount: String,
    onSuccess: () -> Unit
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager != null) {
            val jobName = "LIC_Receipt_$receiptNo"
            val printAdapter = object : PrintDocumentAdapter() {
                private var pdfDocument: PdfDocument? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    pdfDocument = PdfDocument()
                    val builder = PrintDocumentInfo.Builder("LIC_Receipt_$receiptNo.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                    val info = builder.build()
                    callback?.onLayoutFinished(info, newAttributes != oldAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(420, 595, 1).create() // A5 size in points
                        val page = pdfDocument?.startPage(pageInfo)
                        page?.let {
                            val canvas: Canvas = it.canvas
                            val paint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 12f
                                isAntiAlias = true
                            }

                            // Render text on PDF canvas
                            paint.textSize = 16f
                            paint.isFakeBoldText = true
                            canvas.drawText("LIFE INSURANCE CORPORATION OF INDIA", 30f, 40f, paint)

                            paint.textSize = 10f
                            paint.isFakeBoldText = false
                            canvas.drawText("OFFICIAL PREMIUM RECEIPT #$receiptNo", 30f, 60f, paint)
                            canvas.drawLine(30f, 70f, 390f, 70f, paint)

                            paint.textSize = 11f
                            canvas.drawText("Customer: $customerName", 30f, 95f, paint)
                            canvas.drawText("Amount Received: Rs. $amount", 30f, 115f, paint)
                            canvas.drawText("Status: PAID - LIC ACKNOWLEDGED", 30f, 135f, paint)
                            canvas.drawText("Date: 03 Aug 2026", 30f, 155f, paint)

                            canvas.drawText("Thank you for choosing LIC of India.", 30f, 190f, paint)

                            pdfDocument?.finishPage(it)
                        }

                        destination?.fileDescriptor?.let { fd ->
                            FileOutputStream(fd).use { out ->
                                pdfDocument?.writeTo(out)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDocument?.close()
                    }
                }
            }

            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()

            printManager.print(jobName, printAdapter, attributes)
            onSuccess()
        } else {
            Toast.makeText(context, "System Print Service unavailable", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Print launched: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

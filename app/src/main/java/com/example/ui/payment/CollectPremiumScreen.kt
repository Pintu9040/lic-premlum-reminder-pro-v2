package com.example.ui.payment

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPremiumScreen(
    policy: PolicyEntity? = null,
    customer: CustomerEntity? = null,
    onNavigateBack: () -> Unit = {},
    onSavePayment: ((PolicyEntity, Double, String, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fallback/Mock details if policy/customer not explicitly passed
    val displayCustomerName = policy?.customerName ?: customer?.name ?: "Rajesh Kumar"
    val displayPolicyNumber = policy?.policyNumber ?: "POL-9842105"
    val displayPlanName = policy?.planName ?: "Jeevan Umang (Plan 945)"
    val defaultAmount = policy?.premiumAmount ?: 24500.0
    val displayDueDate = policy?.dueDate ?: "15 Aug 2026"
    val displayStatus = policy?.status ?: "Due Today"

    // Initials for Customer Avatar
    val initials = displayCustomerName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "RK" }

    // Form State
    var amountReceived by remember { mutableStateOf(defaultAmount.toInt().toString()) }
    var paymentDate by remember {
        mutableStateOf(
            try {
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
            } catch (e: Exception) {
                "03 Aug 2026"
            }
        )
    }
    var selectedPaymentMode by remember { mutableStateOf("UPI") } // Cash, UPI, Bank, Cheque
    var receiptNumber by remember { mutableStateOf("REC-" + System.currentTimeMillis().toString().takeLast(6)) }
    var remarks by remember { mutableStateOf("Annual premium collected in full") }
    var isSaving by remember { mutableStateOf(false) }
    var isCollected by remember { mutableStateOf(false) }

    val paymentModes = listOf(
        PaymentModeOption("Cash", Icons.Default.Payments),
        PaymentModeOption("UPI", Icons.Default.QrCodeScanner),
        PaymentModeOption("Bank", Icons.Default.AccountBalance),
        PaymentModeOption("Cheque", Icons.AutoMirrored.Filled.ReceiptLong)
    )

    // Dark theme banking colors
    val darkBg = NeutralBgDark // 0xFF0F172A
    val darkCardSurface = NeutralSurfaceDark // 0xFF1E293B
    val darkBorder = NeutralBorderDark // 0xFF334155
    val textPrimary = Color.White
    val textSecondary = Color(0xFF94A3B8)
    val emeraldGreen = Color(0xFF34C759)
    val alertRed = Color(0xFFEF4444)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = AccentOrangeLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Collect Premium",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.15.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("collect_premium_back_button")
                    ) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = darkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(darkBg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // -------------------------------------------------------------
            // 1. CUSTOMER SUMMARY CARD (Round 20dp, Banking Quality)
            // -------------------------------------------------------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardSurface),
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = RoyalBluePrimary.copy(alpha = 0.3f)
                    )
                    .testTag("customer_summary_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row: Avatar, Customer Name, Policy Number & Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Customer Avatar with gradient ring
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                        )
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Column {
                                Text(
                                    text = displayCustomerName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = AccentOrangeLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = displayPolicyNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = AccentOrangeLight,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }

                        // Payment Status Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCollected) emeraldGreen.copy(alpha = 0.15f) else AccentOrangeContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isCollected) emeraldGreen else AccentOrange.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isCollected) "Paid" else displayStatus,
                                color = if (isCollected) emeraldGreen else AccentOrangeLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = darkBorder)

                    // Plan & Due Date Details Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Plan Name",
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = displayPlanName,
                                fontSize = 13.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Next Due Date",
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Due Date Highlight Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentOrangeContainer.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = AccentOrangeLight,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = displayDueDate,
                                        color = AccentOrangeLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Amounts Grid Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(darkBg)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Premium Amount Highlight
                        Column {
                            Text(
                                text = "Premium Amount",
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = emeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "₹ ${"%,.2f".format(defaultAmount)}",
                                    fontSize = 16.sp,
                                    color = emeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Outstanding Amount (Highlighted in Red when due)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Outstanding Amount",
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCollected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isCollected) emeraldGreen else alertRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isCollected) "₹ 0.00" else "₹ ${"%,.2f".format(defaultAmount)}",
                                    fontSize = 16.sp,
                                    color = if (isCollected) emeraldGreen else alertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. PREMIUM MATERIAL 3 SUCCESS CARD (Shown after Collection)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = isCollected,
                enter = slideInVertically(initialOffsetY = { -20 }) + fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val scaleCheck by animateFloatAsState(
                    targetValue = if (isCollected) 1f else 0.5f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "scaleCheck"
                )

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15803D)),
                    border = BorderStroke(1.dp, emeraldGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), spotColor = emeraldGreen)
                        .testTag("premium_collected_success_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // White Circular Check Badge with spring animation
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(52.dp)
                                .scale(scaleCheck)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Premium collected successfully",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            val parsedAmount = amountReceived.toDoubleOrNull() ?: defaultAmount
                            Text(
                                text = "₹ ${"%,.2f".format(parsedAmount)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Receipt #$receiptNumber • Mode: $selectedPaymentMode",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. COLLECTION FORM CARD (Round 20dp with Focus Animations)
            // -------------------------------------------------------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardSurface),
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                    .testTag("collection_form_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RoyalBlueLight)
                        )
                        Text(
                            text = "Collection Details",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }

                    // Amount Received Field with Animated Focus & Icon
                    AnimatedBankingTextField(
                        value = amountReceived,
                        onValueChange = { amountReceived = it },
                        label = "Amount Received (₹)",
                        leadingIcon = Icons.Default.Payments,
                        keyboardType = KeyboardType.Number,
                        testTagStr = "amount_received_input"
                    )

                    // Payment Date Field with Animated Focus & Icon
                    AnimatedBankingTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it },
                        label = "Payment Date",
                        leadingIcon = Icons.Default.CalendarToday,
                        trailingIcon = {
                            Icon(Icons.Default.Event, contentDescription = null, tint = AccentOrangeLight)
                        },
                        testTagStr = "payment_date_input"
                    )

                    // Payment Mode Selector using Material 3 Filter Chips with Icons
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Payment Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paymentModes.forEach { mode ->
                                val isSelected = selectedPaymentMode == mode.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPaymentMode = mode.name },
                                    label = {
                                        Text(
                                            text = mode.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else mode.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBluePrimary,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = darkBg,
                                        labelColor = textSecondary,
                                        iconColor = textSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = darkBorder,
                                        selectedBorderColor = RoyalBlueLight,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.5.dp
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("payment_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Receipt Number Field with Animated Focus & Icon
                    AnimatedBankingTextField(
                        value = receiptNumber,
                        onValueChange = { receiptNumber = it },
                        label = "Receipt Number (Auto Generated)",
                        leadingIcon = Icons.AutoMirrored.Filled.ReceiptLong,
                        trailingIcon = {
                            IconButton(onClick = {
                                receiptNumber = "REC-" + System.currentTimeMillis().toString().takeLast(6)
                            }) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Regenerate", tint = RoyalBlueLight)
                            }
                        },
                        testTagStr = "receipt_number_input"
                    )

                    // Remarks Field with Animated Focus & Icon
                    AnimatedBankingTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = "Remarks (Optional)",
                        leadingIcon = Icons.Default.EditNote,
                        singleLine = false,
                        maxLines = 3,
                        testTagStr = "remarks_input"
                    )
                }
            }

            // -------------------------------------------------------------
            // 4. BOTTOM ACTIONS (Royal Blue Gradient Button & Outlined Button)
            // -------------------------------------------------------------
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Primary Action: Collect Premium (Full-width Gradient Button)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = RoyalBluePrimary)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(RoyalBluePrimary, RoyalBlueLight)
                            )
                        )
                        .clickable(enabled = !isSaving) {
                            val parsedAmount = amountReceived.toDoubleOrNull() ?: defaultAmount
                            isSaving = true

                            // Invoke optional callback if provided
                            policy?.let { pol ->
                                onSavePayment?.invoke(pol, parsedAmount, selectedPaymentMode, paymentDate, remarks)
                            }

                            coroutineScope.launch {
                                isCollected = true
                                snackbarHostState.showSnackbar(
                                    message = "Premium of ₹${"%,.2f".format(parsedAmount)} collected successfully! Receipt #$receiptNumber",
                                    duration = SnackbarDuration.Short
                                )
                                delay(1200)
                                isSaving = false
                                onNavigateBack()
                            }
                        }
                        .testTag("save_collection_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Collect Premium",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }

                // Secondary Action: Generate Receipt (Outlined Premium Button)
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Receipt #$receiptNumber generated and ready for print/share.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    border = BorderStroke(1.5.dp, RoyalBlueLight),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = darkCardSurface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_receipt_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = RoyalBlueLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate Receipt",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoyalBlueLight,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedBankingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    testTagStr: String
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) RoyalBlueLight else NeutralBorderDark,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) RoyalBlueLight else Color(0xFF94A3B8),
        animationSpec = tween(durationMillis = 200),
        label = "iconTint"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = if (isFocused) RoyalBlueLight else Color(0xFF94A3B8)) },
        leadingIcon = {
            Icon(imageVector = leadingIcon, contentDescription = null, tint = iconTint)
        },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RoyalBlueLight,
            unfocusedBorderColor = NeutralBorderDark,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = NeutralBgDark,
            unfocusedContainerColor = NeutralBgDark
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag(testTagStr)
    )
}

private data class PaymentModeOption(val name: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun CollectPremiumScreenPreview() {
    LICReminderProTheme(darkTheme = true) {
        CollectPremiumScreen()
    }
}

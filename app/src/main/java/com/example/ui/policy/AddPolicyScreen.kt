package com.example.ui.policy

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPolicyScreen(
    policy: PolicyEntity? = null,
    customersList: List<CustomerEntity> = emptyList(),
    existingPolicies: List<PolicyEntity> = emptyList(),
    onNavigateBack: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onSavePolicy: ((PolicyEntity) -> Unit)? = null,
    onSave: ((PolicyEntity) -> Unit)? = null,
    onAddNewCustomer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fallback Mock Customers if none provided
    val availableCustomers = if (customersList.isNotEmpty()) {
        customersList
    } else {
        listOf(
            CustomerEntity(id = 101, name = "Rajesh Kumar", mobile = "+91 98765 43210", email = "rajesh@example.com"),
            CustomerEntity(id = 102, name = "Priya Sharma", mobile = "+91 91234 56789", email = "priya@example.com"),
            CustomerEntity(id = 103, name = "Amit Singh", mobile = "+91 99887 76655", email = "amit@example.com"),
            CustomerEntity(id = 104, name = "Sunita Verma", mobile = "+91 97654 32109", email = "sunita@example.com")
        )
    }

    // Pre-selected customer or default
    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(
            availableCustomers.find { it.id == policy?.customerId }
                ?: availableCustomers.find { it.name.equals(policy?.customerName, ignoreCase = true) }
                ?: availableCustomers.firstOrNull()
        )
    }

    var customerSearchQuery by remember { mutableStateOf("") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }

    val filteredCustomers = remember(customerSearchQuery, availableCustomers) {
        if (customerSearchQuery.isBlank()) {
            availableCustomers
        } else {
            availableCustomers.filter {
                it.name.contains(customerSearchQuery, ignoreCase = true) ||
                        it.mobile.contains(customerSearchQuery)
            }
        }
    }

    // Policy Details Form States
    var planName by remember { mutableStateOf(policy?.planName ?: "Jeevan Umang (945)") }
    var policyNumber by remember { mutableStateOf(policy?.policyNumber ?: "POL-" + System.currentTimeMillis().toString().takeLast(7)) }
    var premiumAmountStr by remember { mutableStateOf(policy?.premiumAmount?.toInt()?.toString() ?: "24500") }
    var selectedPremiumMode by remember { mutableStateOf(policy?.premiumMode ?: "Yearly") } // Yearly, Half-Yearly, Quarterly, Monthly

    // Generate Policy Number Animation state
    var isGeneratingNumber by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isGeneratingNumber) 360f else 0f,
        animationSpec = tween(durationMillis = 400),
        finishedListener = { isGeneratingNumber = false },
        label = "rotationAngle"
    )

    val todayStr = try {
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: Exception) {
        "15 Aug 2026"
    }

    var startDateStr by remember { mutableStateOf(policy?.issueDate.takeIf { !it.isNullOrBlank() } ?: todayStr) }
    var policyTermStr by remember { mutableStateOf(policy?.policyTerm?.toString() ?: "20") }
    var sumAssuredStr by remember { mutableStateOf(policy?.sumAssured?.toInt()?.toString() ?: "500000") }
    var notesStr by remember { mutableStateOf(policy?.nominee.takeIf { !it.isNullOrBlank() } ?: "Nominee: Family Member") }

    // Auto-calculated Next Due Date based on Premium Mode
    val calculatedNextDueDate = remember(startDateStr, selectedPremiumMode) {
        try {
            val monthsToAdd = when (selectedPremiumMode) {
                "Monthly" -> 1L
                "Quarterly" -> 3L
                "Half-Yearly" -> 6L
                else -> 12L // Yearly
            }
            LocalDate.now().plusMonths(monthsToAdd).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        } catch (e: Exception) {
            "15 Aug 2027"
        }
    }

    var isSaving by remember { mutableStateOf(false) }

    val premiumModes = listOf(
        PremiumModeChip("Yearly", Icons.Default.CalendarToday),
        PremiumModeChip("Half-Yearly", Icons.Default.DateRange),
        PremiumModeChip("Quarterly", Icons.Default.EventRepeat),
        PremiumModeChip("Monthly", Icons.Default.Update)
    )

    // Dark Banking Theme Colors
    val darkBg = NeutralBgDark // 0xFF0F172A
    val darkCardSurface = NeutralSurfaceDark // 0xFF1E293B
    val darkBorder = NeutralBorderDark // 0xFF334155
    val textPrimary = Color.White
    val textSecondary = Color(0xFF94A3B8)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PostAdd,
                                contentDescription = null,
                                tint = AccentOrangeLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (policy == null) "Add Policy" else "Edit Policy",
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
                        onClick = {
                            onNavigateBack()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("add_policy_back_button")
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
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -------------------------------------------------------------
            // SECTION 1: CUSTOMER SELECTION CARD (20dp Rounded Corners)
            // -------------------------------------------------------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardSurface),
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
                    .testTag("add_policy_customer_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(RoyalBlueLight)
                            )
                            Text(
                                text = "1. Customer Selection",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                        }

                        // Add New Customer Shortcut
                        FloatingActionButton(
                            onClick = {
                                onAddNewCustomer?.invoke()
                                Toast.makeText(context, "Opening Add Customer...", Toast.LENGTH_SHORT).show()
                            },
                            shape = CircleShape,
                            containerColor = Color(0xFFFF7A00),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("add_new_customer_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add New Customer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Currently Selected Customer Display Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = darkBg,
                        border = BorderStroke(1.dp, RoyalBlueLight.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clickable { isCustomerDropdownExpanded = !isCustomerDropdownExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentCust = selectedCustomer
                            if (currentCust != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Customer Gradient Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentCust.name.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = currentCust.name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = currentCust.mobile,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = textSecondary,
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(darkBorder),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonSearch,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "No Client Selected",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        )
                                        Text(
                                            text = "Tap to search or select a client",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBluePrimary.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCustomerDropdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Select Customer",
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Customer Selection Dropdown
                    AnimatedVisibility(
                        visible = isCustomerDropdownExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(darkBg)
                                .border(1.dp, darkBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search Customer Field - M3 OutlinedTextField, 56dp height, clean borders
                            OutlinedTextField(
                                value = customerSearchQuery,
                                onValueChange = { customerSearchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Search customer name or mobile...",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = AccentOrangeLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RoyalBlueLight,
                                    unfocusedBorderColor = darkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = darkCardSurface,
                                    unfocusedContainerColor = darkCardSurface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("search_customer_field")
                            )

                            // List of Filtered Customers OR Clean Empty State
                            if (filteredCustomers.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = darkCardSurface,
                                    border = BorderStroke(1.dp, darkBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonSearch,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = "No customer found",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp
                                            )
                                        )
                                        Text(
                                            text = "Please add a client first.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            } else {
                                Column {
                                    filteredCustomers.forEachIndexed { index, cust ->
                                        val isSelected = cust.id == selectedCustomer?.id

                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = darkBorder.copy(alpha = 0.5f),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) RoyalBluePrimary.copy(alpha = 0.35f) else Color.Transparent,
                                            border = if (isSelected) BorderStroke(1.dp, RoyalBlueLight) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedCustomer = cust
                                                    isCustomerDropdownExpanded = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = cust.name,
                                                        color = if (isSelected) RoyalBlueLight else textPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(text = cust.mobile, color = textSecondary, fontSize = 12.sp)
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = RoyalBlueLight,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: POLICY DETAILS CARD (20dp Rounded Corners)
            // -------------------------------------------------------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardSurface),
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
                    .testTag("add_policy_details_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RoyalBlueLight)
                        )
                        Text(
                            text = "2. Policy Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                    }

                    // Plan Name Field
                    AnimatedPolicyTextField(
                        value = planName,
                        onValueChange = { planName = it },
                        label = "Plan Name",
                        leadingIconVector = Icons.Default.Description,
                        singleLine = true,
                        maxLines = 1,
                        testTagStr = "policy_plan_name_input"
                    )

                    // LIC Plan Chips (Equal width & height, horizontal scrollable, smooth selection animation)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Popular LIC Plans",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )

                        val popularLicPlans = listOf(
                            "Jeevan Umang (945)",
                            "Jeevan Labh (936)",
                            "Endowment (914)",
                            "Money Back (920)",
                            "Tech Term (854)",
                            "Ananda (915)",
                            "Bima Jyoti (860)"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            popularLicPlans.forEach { plan ->
                                val isSelected = planName == plan
                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (isSelected) RoyalBluePrimary else darkBg,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "planBg"
                                )
                                val animatedBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) RoyalBlueLight else darkBorder,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "planBorder"
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = animatedBgColor,
                                    border = BorderStroke(1.dp, animatedBorderColor),
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { planName = plan }
                                        .testTag("lic_plan_chip_${plan.lowercase().replace(" ", "_")}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = plan,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else textSecondary,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Policy Number Field with Copy Icon + Snackbar & Rotating Auto-generate
                    AnimatedPolicyTextField(
                        value = policyNumber,
                        onValueChange = { policyNumber = it },
                        label = "Policy Number",
                        leadingIconVector = Icons.Default.Pin,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                // Copy Icon with Material 3 Snackbar
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(policyNumber))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Policy Number Copied",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Policy Number",
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Auto-generate Icon with rotation animation
                                IconButton(
                                    onClick = {
                                        isGeneratingNumber = true
                                        policyNumber = "POL-" + System.currentTimeMillis().toString().takeLast(7)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "Generate",
                                        tint = RoyalBlueLight,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(rotationAngle)
                                    )
                                }
                            }
                        },
                        testTagStr = "policy_number_input"
                    )

                    // Premium Amount & Sum Assured - Equal Width, Height & Vertical Center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedPolicyTextField(
                                value = premiumAmountStr,
                                onValueChange = { premiumAmountStr = it },
                                label = "Premium Amount (₹)",
                                customLeadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 10.dp, end = 2.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(RoyalBluePrimary.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "₹",
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                keyboardType = KeyboardType.Number,
                                testTagStr = "policy_premium_amount_input"
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedPolicyTextField(
                                value = sumAssuredStr,
                                onValueChange = { sumAssuredStr = it },
                                label = "Sum Assured (₹)",
                                customLeadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = RoyalBlueLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                keyboardType = KeyboardType.Number,
                                testTagStr = "sum_assured_input"
                            )
                        }
                    }

                    // Premium Payment Mode (Horizontally Scrollable, Same width 115.dp & height 48.dp, no clipped text)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Premium Payment Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            premiumModes.forEach { mode ->
                                val isSelected = selectedPremiumMode == mode.name

                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (isSelected) RoyalBluePrimary else darkBg,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "modeBg"
                                )
                                val animatedBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) RoyalBlueLight else darkBorder,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "modeBorder"
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = animatedBgColor,
                                    border = BorderStroke(1.dp, animatedBorderColor),
                                    modifier = Modifier
                                        .width(115.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedPremiumMode = mode.name }
                                        .testTag("premium_mode_${mode.name.lowercase().replace("-", "_")}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else mode.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else textSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = mode.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else textSecondary,
                                                fontSize = 12.5.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Policy Start Date & Policy Term (Years)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedPolicyTextField(
                                value = startDateStr,
                                onValueChange = { startDateStr = it },
                                label = "Start Date",
                                leadingIconVector = Icons.Default.CalendarToday,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = "Select Date",
                                        tint = AccentOrangeLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                testTagStr = "policy_start_date_input"
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedPolicyTextField(
                                value = policyTermStr,
                                onValueChange = { policyTermStr = it },
                                label = "Term (Years)",
                                leadingIconVector = Icons.Default.Timer,
                                keyboardType = KeyboardType.Number,
                                testTagStr = "policy_term_input"
                            )
                        }
                    }

                    // Next Due Date Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF059669).copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Next Due Date",
                                        fontSize = 11.sp,
                                        color = Color(0xFFA7F3D0),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = calculatedNextDueDate,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF059669).copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = selectedPremiumMode,
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 3: NOTES & NOMINEE CARD (20dp Rounded Corners)
            // -------------------------------------------------------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardSurface),
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
                    .testTag("add_policy_notes_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RoyalBlueLight)
                        )
                        Text(
                            text = "3. Notes / Nominee (Optional)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                    }

                    AnimatedPolicyTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = "Nominee / Remarks",
                        leadingIconVector = Icons.Default.EditNote,
                        singleLine = false,
                        maxLines = 3,
                        testTagStr = "policy_notes_input"
                    )
                }
            }

            // -------------------------------------------------------------
            // BOTTOM BUTTONS (Cancel & Next Step / Save aligned horizontally, 160dp Next button)
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button on the left taking remaining space
                OutlinedButton(
                    onClick = {
                        onNavigateBack()
                        onDismiss()
                    },
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = darkCardSurface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("cancel_policy_button")
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                }

                // Save Policy / Next Step Button on the right with FIXED WIDTH 160.dp
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(52.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = RoyalBlueLight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(RoyalBluePrimary, RoyalBlueLight)
                            )
                        )
                        .clickable(enabled = !isSaving) {
                            if (policyNumber.isBlank()) {
                                Toast.makeText(context, "Please enter a valid Policy Number", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            val parsedAmount = premiumAmountStr.toDoubleOrNull() ?: 24500.0
                            val parsedSum = sumAssuredStr.toDoubleOrNull() ?: 500000.0
                            val parsedTerm = policyTermStr.toIntOrNull() ?: 20

                            val newPolicy = PolicyEntity(
                                id = policy?.id ?: 0,
                                policyNumber = policyNumber,
                                customerId = selectedCustomer?.id ?: 101,
                                customerName = selectedCustomer?.name ?: "Client",
                                planName = planName,
                                premiumAmount = parsedAmount,
                                sumAssured = parsedSum,
                                premiumMode = selectedPremiumMode,
                                dueDate = calculatedNextDueDate,
                                maturityDate = "15 Aug 2046",
                                status = "Active",
                                nominee = notesStr,
                                policyTerm = parsedTerm,
                                premiumPayingTerm = (parsedTerm - 4).coerceAtLeast(10),
                                issueDate = startDateStr
                            )

                            isSaving = true
                            coroutineScope.launch {
                                onSavePolicy?.invoke(newPolicy)
                                onSave?.invoke(newPolicy)
                                snackbarHostState.showSnackbar(
                                    message = "Policy #${newPolicy.policyNumber} saved successfully!",
                                    duration = SnackbarDuration.Short
                                )
                                delay(1000)
                                isSaving = false
                                onNavigateBack()
                                onDismiss()
                            }
                        }
                        .testTag("save_policy_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (policy == null) "Save Policy" else "Update Policy",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    letterSpacing = 0.1.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedPolicyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIconVector: ImageVector? = null,
    customLeadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    testTagStr: String
) {
    var isFocused by remember { mutableStateOf(false) }

    val iconTint by animateColorAsState(
        targetValue = if (isFocused) RoyalBlueLight else Color(0xFF94A3B8),
        animationSpec = tween(durationMillis = 200),
        label = "iconTint"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                color = if (isFocused) RoyalBlueLight else Color(0xFF94A3B8),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        textStyle = LocalTextStyle.current.copy(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = Color.White
        ),
        leadingIcon = {
            if (customLeadingIcon != null) {
                customLeadingIcon()
            } else if (leadingIconVector != null) {
                Icon(imageVector = leadingIconVector, contentDescription = null, tint = iconTint)
            }
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
            .heightIn(min = 56.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .testTag(testTagStr)
    )
}

private data class PremiumModeChip(val name: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun AddPolicyScreenPreview() {
    LICReminderProTheme(darkTheme = true) {
        AddPolicyScreen()
    }
}

package com.example.ui.customer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerScreen(
    customer: CustomerEntity? = null,
    onNavigateBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onSaveCustomer: ((CustomerEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Animation state for cards
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cardsVisible = true
    }

    // Form Field States
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var mobile by remember { mutableStateOf(customer?.mobile ?: "") }
    var sameAsMobile by remember { mutableStateOf(customer?.whatsapp?.isNotEmpty() != true || customer?.whatsapp == customer?.mobile) }
    var whatsapp by remember { mutableStateOf(customer?.whatsapp ?: "") }
    var dob by remember { mutableStateOf(customer?.dob ?: "") }
    var gender by remember { mutableStateOf("Male") }
    var occupation by remember { mutableStateOf(customer?.occupation ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }

    // Address States
    var village by remember { mutableStateOf(customer?.address ?: "") }
    var postOffice by remember { mutableStateOf("") }
    var policeStation by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }

    // Nominee States
    var nomineeName by remember { mutableStateOf("") }
    var nomineeRelation by remember { mutableStateOf("Spouse") }
    var nomineeMobile by remember { mutableStateOf("") }

    // Notes State
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    // Validation Errors
    var nameError by remember { mutableStateOf(false) }
    var mobileError by remember { mutableStateOf(false) }

    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }

    // Sync WhatsApp if Same As Mobile is checked
    LaunchedEffect(mobile, sameAsMobile) {
        if (sameAsMobile) {
            whatsapp = mobile
        }
    }

    // Dark Theme Palette
    val darkBg = Color(0xFF0F172A)
    val darkSurface = Color(0xFF1E293B)
    val darkBorder = Color(0xFF334155)
    val royalBluePrimary = Color(0xFF1D4ED8)
    val royalBlueLight = Color(0xFF3B82F6)
    val royalBlueDark = Color(0xFF1E3A8A)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)
    val accentOrange = Color(0xFFFF7A00)

    Scaffold(
        containerColor = darkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (customer != null) "Edit Customer" else "Add Customer",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onDismiss?.invoke()
                            onNavigateBack?.invoke()
                        },
                        modifier = Modifier.testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = royalBlueDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = darkSurface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, darkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = {
                            onDismiss?.invoke()
                            onNavigateBack?.invoke()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, darkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Save Customer Button
                    Button(
                        onClick = {
                            var hasError = false
                            if (name.isBlank()) {
                                nameError = true
                                hasError = true
                            }
                            if (mobile.isBlank()) {
                                mobileError = true
                                hasError = true
                            }

                            if (!hasError) {
                                // Compose Address
                                val fullAddress = listOf(
                                    village.takeIf { it.isNotBlank() },
                                    postOffice.takeIf { it.isNotBlank() }?.let { "P.O: $it" },
                                    policeStation.takeIf { it.isNotBlank() }?.let { "P.S: $it" },
                                    district.takeIf { it.isNotBlank() }?.let { "Dist: $it" },
                                    stateName.takeIf { it.isNotBlank() },
                                    pincode.takeIf { it.isNotBlank() }?.let { "PIN: $it" }
                                ).filterNotNull().joinToString(", ")

                                // Compose Notes with Nominee
                                val fullNotes = buildString {
                                    if (notes.isNotBlank()) append(notes)
                                    if (nomineeName.isNotBlank() || nomineeMobile.isNotBlank()) {
                                        if (isNotEmpty()) append("\n\n")
                                        append("Nominee: $nomineeName")
                                        if (nomineeRelation.isNotBlank()) append(" ($nomineeRelation)")
                                        if (nomineeMobile.isNotBlank()) append(" - Mob: $nomineeMobile")
                                    }
                                }

                                val finalCustomer = CustomerEntity(
                                    id = customer?.id ?: 0,
                                    name = name.trim(),
                                    mobile = mobile.trim(),
                                    whatsapp = if (sameAsMobile) mobile.trim() else whatsapp.trim(),
                                    email = email.trim(),
                                    address = fullAddress,
                                    dob = dob.trim(),
                                    occupation = occupation.trim(),
                                    notes = fullNotes
                                )

                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Customer added successfully",
                                        duration = SnackbarDuration.Short
                                    )
                                    onSaveCustomer?.invoke(finalCustomer)
                                    onDismiss?.invoke()
                                    onNavigateBack?.invoke()
                                }
                            } else {
                                Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = royalBluePrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("save_customer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Customer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(darkBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = cardsVisible,
                    enter = fadeIn(animationSpec = tween(350)) + slideInVertically(
                        initialOffsetY = { 30 },
                        animationSpec = tween(350)
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // ==================== 1. CUSTOMER INFORMATION CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = darkSurface),
                    border = BorderStroke(1.dp, darkBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(royalBluePrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = royalBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Customer Information",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Basic details & contact info",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = darkBorder, thickness = 1.dp)

                        // Full Name *
                        CustomDarkTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (it.isNotBlank()) nameError = false
                            },
                            label = "Full Name *",
                            leadingIcon = Icons.Default.Person,
                            isError = nameError,
                            errorMessage = if (nameError) "Full Name is required" else null,
                            testTagStr = "customer_name_input"
                        )

                        // Mobile Number *
                        CustomDarkTextField(
                            value = mobile,
                            onValueChange = {
                                mobile = it
                                if (it.isNotBlank()) mobileError = false
                            },
                            label = "Mobile Number *",
                            leadingIcon = Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone,
                            isError = mobileError,
                            errorMessage = if (mobileError) "Mobile Number is required" else null,
                            testTagStr = "customer_mobile_input"
                        )

                        // WhatsApp Number Toggle Row (Same 56dp Height as Inputs)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = darkBg,
                            border = BorderStroke(1.dp, darkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = accentOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "WhatsApp same as Mobile",
                                        fontSize = 14.sp,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Switch(
                                    checked = sameAsMobile,
                                    onCheckedChange = { sameAsMobile = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentOrange,
                                        uncheckedThumbColor = textSecondary,
                                        uncheckedTrackColor = darkBorder
                                    ),
                                    modifier = Modifier.testTag("whatsapp_same_as_mobile_toggle")
                                )
                            }
                        }

                        // WhatsApp Number (Editable if not sameAsMobile)
                        if (!sameAsMobile) {
                            CustomDarkTextField(
                                value = whatsapp,
                                onValueChange = { whatsapp = it },
                                label = "WhatsApp Number",
                                leadingIcon = Icons.Default.Chat,
                                keyboardType = KeyboardType.Phone,
                                testTagStr = "customer_whatsapp_input"
                            )
                        }

                        // Date of Birth (DatePicker)
                        CustomDarkTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = "Date of Birth (YYYY-MM-DD)",
                            leadingIcon = Icons.Default.Cake,
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Pick Date",
                                        tint = royalBlueLight,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            testTagStr = "customer_dob_input"
                        )

                        // Gender Choice Chips (Equal Height & Width)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Gender",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf("Male", "Female", "Other").forEach { g ->
                                    val isSelected = gender == g
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) royalBluePrimary else darkBg,
                                        border = BorderStroke(1.dp, if (isSelected) royalBlueLight else darkBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { gender = g }
                                            .testTag("gender_chip_${g.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = when (g) {
                                                    "Male" -> Icons.Default.Male
                                                    "Female" -> Icons.Default.Female
                                                    else -> Icons.Default.Transgender
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = g,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Occupation
                        CustomDarkTextField(
                            value = occupation,
                            onValueChange = { occupation = it },
                            label = "Occupation",
                            leadingIcon = Icons.Default.Work,
                            placeholder = "e.g. Business, Salaried, Doctor",
                            testTagStr = "customer_occupation_input"
                        )

                        // Email
                        CustomDarkTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address (Optional)",
                            leadingIcon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            placeholder = "e.g. client@example.com",
                            testTagStr = "customer_email_input"
                        )
                    }
                }

                // ==================== 2. ADDRESS CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = darkSurface),
                    border = BorderStroke(1.dp, darkBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(royalBluePrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HomeWork,
                                    contentDescription = null,
                                    tint = royalBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Address Details",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Residential address information",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = darkBorder, thickness = 1.dp)

                        CustomDarkTextField(
                            value = village,
                            onValueChange = { village = it },
                            label = "Village / Area / Street",
                            leadingIcon = Icons.Default.Home,
                            testTagStr = "address_village_input"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CustomDarkTextField(
                                    value = postOffice,
                                    onValueChange = { postOffice = it },
                                    label = "Post Office",
                                    leadingIcon = Icons.Default.LocalPostOffice,
                                    testTagStr = "address_post_office_input"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CustomDarkTextField(
                                    value = policeStation,
                                    onValueChange = { policeStation = it },
                                    label = "Police Station",
                                    leadingIcon = Icons.Default.LocalPolice,
                                    testTagStr = "address_police_station_input"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CustomDarkTextField(
                                    value = district,
                                    onValueChange = { district = it },
                                    label = "District",
                                    leadingIcon = Icons.Default.LocationCity,
                                    testTagStr = "address_district_input"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CustomDarkTextField(
                                    value = stateName,
                                    onValueChange = { stateName = it },
                                    label = "State",
                                    leadingIcon = Icons.Default.Map,
                                    testTagStr = "address_state_input"
                                )
                            }
                        }

                        CustomDarkTextField(
                            value = pincode,
                            onValueChange = { pincode = it },
                            label = "PIN Code",
                            leadingIcon = Icons.Default.PinDrop,
                            keyboardType = KeyboardType.Number,
                            testTagStr = "address_pincode_input"
                        )
                    }
                }

                // ==================== 3. NOMINEE CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = darkSurface),
                    border = BorderStroke(1.dp, darkBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(royalBluePrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FamilyRestroom,
                                    contentDescription = null,
                                    tint = royalBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Nominee Details",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Beneficiary information",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = darkBorder, thickness = 1.dp)

                        CustomDarkTextField(
                            value = nomineeName,
                            onValueChange = { nomineeName = it },
                            label = "Nominee Name",
                            leadingIcon = Icons.Default.PersonOutline,
                            testTagStr = "nominee_name_input"
                        )

                        // Relationship Choice Chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Relationship",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Spouse", "Son", "Daughter", "Mother", "Father", "Other").forEach { rel ->
                                    val isSelected = nomineeRelation == rel
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) royalBluePrimary else darkBg,
                                        border = BorderStroke(1.dp, if (isSelected) royalBlueLight else darkBorder),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { nomineeRelation = rel }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rel,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CustomDarkTextField(
                            value = nomineeMobile,
                            onValueChange = { nomineeMobile = it },
                            label = "Nominee Mobile",
                            leadingIcon = Icons.Default.PhoneIphone,
                            keyboardType = KeyboardType.Phone,
                            testTagStr = "nominee_mobile_input"
                        )
                    }
                }

                // ==================== 4. NOTES CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = darkSurface),
                    border = BorderStroke(1.dp, darkBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(royalBluePrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NoteAlt,
                                    contentDescription = null,
                                    tint = royalBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Notes & Remarks",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Additional agent comments",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = darkBorder, thickness = 1.dp)

                        CustomDarkTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Notes (Optional)",
                            leadingIcon = Icons.Default.EditNote,
                            singleLine = false,
                            minLines = 3,
                            placeholder = "Enter any special requirements or meeting notes...",
                            testTagStr = "customer_notes_input"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
}

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = millis
                            }
                            val year = cal.get(Calendar.YEAR)
                            val month = cal.get(Calendar.MONTH) + 1
                            val day = cal.get(Calendar.DAY_OF_MONTH)
                            dob = String.format("%04d-%02d-%02d", year, month, day)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = darkSurface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = darkSurface,
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = textSecondary,
                    subheadContentColor = Color.White,
                    yearContentColor = Color.White,
                    currentYearContentColor = royalBlueLight,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = royalBluePrimary,
                    dayContentColor = Color.White,
                    disabledDayContentColor = textSecondary.copy(alpha = 0.4f),
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = royalBluePrimary,
                    todayContentColor = accentOrange,
                    todayDateBorderColor = accentOrange
                )
            )
        }
    }
}

@Composable
private fun CustomDarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    testTagStr: String = ""
) {
    val darkSurface = Color(0xFF0F172A)
    val darkBorder = Color(0xFF334155)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)
    val royalBlueLight = Color(0xFF3B82F6)

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 14.sp) },
            placeholder = {
                if (placeholder.isNotEmpty()) {
                    Text(placeholder, color = textSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isError) Color(0xFFEF4444) else royalBlueLight,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = trailingIcon,
            isError = isError,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkSurface,
                unfocusedContainerColor = darkSurface,
                disabledContainerColor = darkSurface,
                errorContainerColor = darkSurface,
                focusedBorderColor = royalBlueLight,
                unfocusedBorderColor = darkBorder,
                errorBorderColor = Color(0xFFEF4444),
                focusedLabelColor = royalBlueLight,
                unfocusedLabelColor = textSecondary,
                errorLabelColor = Color(0xFFEF4444),
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                cursorColor = royalBlueLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (singleLine) Modifier.height(56.dp)
                    else Modifier.heightIn(min = 100.dp)
                )
                .testTag(testTagStr)
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

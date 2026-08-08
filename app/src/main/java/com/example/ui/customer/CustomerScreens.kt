package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
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
import com.example.util.NoMatchingRecordsEmptyState
import com.example.util.SearchFilterEngine
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush

enum class CustomerSortOption(val label: String) {
    NAME_AZ("Name A–Z"),
    RECENTLY_ADDED("Recently Added"),
    LATEST_POLICY("Latest Policy"),
    DUE_DATE("Due Date")
}

enum class CustomerFilterTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DUE_TODAY("Due Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue")
}

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
    val context = LocalContext.current

    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf(CustomerFilterTab.ALL) }
    var selectedSortOption by remember { mutableStateOf(CustomerSortOption.NAME_AZ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val selectedSearchFilters by viewModel.selectedSearchFilters.collectAsState()
    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }

    // Filter & Sort Customer List
    val filteredCustomers = remember(customers, policies, searchQuery, selectedFilterTab, selectedSortOption) {
        val list = customers.filter { customer ->
            val custPolicies = policies.filter { it.customerId == customer.id }

            val custPayments = payments.filter { it.customerId == customer.id }

            // Global Multi-Keyword Search across Customer Name, Mobile, WhatsApp, Policy #, Plan Name, Nominee, Receipt #
            val matchesQuery = SearchFilterEngine.matchesQuery(
                query = searchQuery,
                fields = listOf(
                    customer.name,
                    customer.mobile,
                    customer.whatsapp,
                    customer.email,
                    customer.aadhaar,
                    customer.pan,
                    customer.occupation
                ) + custPolicies.flatMap { listOf(it.policyNumber, it.planName, it.nominee) }
                  + custPayments.map { it.receiptNumber }
            )

            // Filter logic
            val isOverdue = custPolicies.any { policy ->
                try {
                    val d = LocalDate.parse(policy.dueDate)
                    d.isBefore(today) && !policy.status.equals("Paid-up", ignoreCase = true)
                } catch (e: Exception) { false }
            }
            val isDueToday = custPolicies.any { policy ->
                try {
                    val d = LocalDate.parse(policy.dueDate)
                    d.isEqual(today) && !policy.status.equals("Paid-up", ignoreCase = true)
                } catch (e: Exception) { false }
            }
            val isUpcoming = custPolicies.any { policy ->
                try {
                    val d = LocalDate.parse(policy.dueDate)
                    d.isAfter(today) && d.isBefore(today.plusDays(30)) && !policy.status.equals("Paid-up", ignoreCase = true)
                } catch (e: Exception) { false }
            }
            val isActive = custPolicies.isNotEmpty() && !isOverdue && !isDueToday

            val matchesFilter = when (selectedFilterTab) {
                CustomerFilterTab.ALL -> true
                CustomerFilterTab.ACTIVE -> isActive
                CustomerFilterTab.DUE_TODAY -> isDueToday
                CustomerFilterTab.UPCOMING -> isUpcoming
                CustomerFilterTab.OVERDUE -> isOverdue
            }

            matchesQuery && matchesFilter
        }

        // Apply Sorting
        when (selectedSortOption) {
            CustomerSortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
            CustomerSortOption.RECENTLY_ADDED -> list.sortedByDescending { it.id }
            CustomerSortOption.LATEST_POLICY -> list.sortedByDescending { cust ->
                policies.filter { it.customerId == cust.id }.maxOfOrNull { it.id } ?: 0L
            }
            CustomerSortOption.DUE_DATE -> list.sortedBy { cust ->
                policies.filter { it.customerId == cust.id }
                    .mapNotNull { try { LocalDate.parse(it.dueDate) } catch (e: Exception) { null } }
                    .minOrNull() ?: LocalDate.MAX
            }
        }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refreshData { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP APP BAR / HEADER SECTION (Royal Blue Header with 20dp section spacing & padding)
            Surface(
                color = RoyalBluePrimary,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title & Subtitle + Sort Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Clients Directory",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    letterSpacing = 0.15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Manage your LIC customer portfolio",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // Sort Action Button
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Clients",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                CustomerSortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (selectedSortOption == option) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = RoyalBluePrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                } else {
                                                    Spacer(modifier = Modifier.width(24.dp))
                                                }
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (selectedSortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedSortOption = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. CUSTOMER COUNT PREMIUM SUMMARY CARD (20dp radius)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.25f),
                                                    Color.White.copy(alpha = 0.15f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "👥 Total Clients",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "${filteredCustomers.size} Customers",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                            }

                            if (selectedFilterTab != CustomerFilterTab.ALL) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AccentOrangeLight,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = selectedFilterTab.label,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. SEARCH SECTION (56dp height SearchBar + Single Filter Icon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    text = "Search customer, mobile, policy...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 13.5.sp
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("clients_directory_search_input")
                        )

                        // Single Filter Button at the end
                        Surface(
                            onClick = { showFilterBottomSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedSearchFilters.isNotEmpty() || selectedFilterTab != CustomerFilterTab.ALL) AccentOrangeLight else Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("clients_directory_filter_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 4. FILTER CHIPS ROW (Equal height, equal padding, horizontal scrolling, selected chip gradient + glow effect)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(CustomerFilterTab.values()) { filterTab ->
                            val isSelected = selectedFilterTab == filterTab

                            val animatedBgColor by animateColorAsState(
                                targetValue = if (isSelected) RoyalBluePrimary else Color.White.copy(alpha = 0.15f),
                                animationSpec = tween(durationMillis = 200),
                                label = "chipBg"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                animationSpec = tween(durationMillis = 200),
                                label = "chipBorder"
                            )

                            Surface(
                                onClick = { selectedFilterTab = filterTab },
                                shape = RoundedCornerShape(20.dp),
                                color = animatedBgColor,
                                border = BorderStroke(1.dp, animatedBorderColor),
                                shadowElevation = if (isSelected) 6.dp else 0.dp,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("filter_chip_${filterTab.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (isSelected) {
                                                Modifier.background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(RoyalBluePrimary, Color(0xFF2563EB))
                                                    )
                                                )
                                            } else Modifier
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filterTab.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MAIN CONTENT AREA (20dp spacing, smooth fade animations)
            when {
                isLoading -> {
                    CustomerSkeletonLoader()
                }

                isError -> {
                    CustomerErrorCard(onRetry = {
                        isError = false
                        viewModel.triggerSync()
                    })
                }

                customers.isEmpty() -> {
                    CustomerEmptyState(onAddFirstCustomer = onAddCustomer)
                }

                filteredCustomers.isEmpty() -> {
                    NoMatchingRecordsEmptyState(
                        query = searchQuery,
                        onResetFilters = {
                            viewModel.clearAllFilters()
                            selectedFilterTab = CustomerFilterTab.ALL
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val customerPolicies = remember(policies, customer.id) {
                                policies.filter { it.customerId == customer.id }
                            }
                            val customerPayments = remember(payments, customer.id) {
                                payments.filter { it.customerId == customer.id }
                            }

                            SwipeableCustomerItem(
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
                                },
                                onEditCustomer = { customerToEdit = customer },
                                onDeleteCustomer = { customerToDelete = customer }
                            )
                        }
                    }
                }
            }
        }

        // 7. FLOATING ACTION BUTTON (Material 3 Extended FAB, 56dp height, hidden when no customers exist)
        AnimatedVisibility(
            visible = customers.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = onAddCustomer,
                shape = RoundedCornerShape(28.dp),
                containerColor = RoyalBluePrimary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("add_customer_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Customer",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Add Customer",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }

    // Payment Collection Modal
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

    // Customer Edit Dialog
    customerToEdit?.let { customer ->
        AddEditCustomerDialog(
            initialCustomer = customer,
            onDismiss = { customerToEdit = null },
            onSave = { updated ->
                viewModel.updateCustomer(updated)
                customerToEdit = null
                Toast.makeText(context, "Customer details updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    customerToDelete?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
            title = { Text("Move to Trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove ${customer.name} from your customer directory? Linked policies will remain preserved in repository.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        customerToDelete = null
                        Toast.makeText(context, "${customer.name} removed.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Customer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { customerToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFilterBottomSheet) {
        SearchFilterBottomSheet(
            initialFilters = selectedSearchFilters,
            onApply = { viewModel.setSearchFilters(it) },
            onReset = { viewModel.resetSearchFilters() },
            onDismiss = { showFilterBottomSheet = false }
        )
    }
        }
    }
}

// SWIPEABLE CUSTOMER CONTAINER
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCustomerItem(
    customer: CustomerEntity,
    customerPolicies: List<PolicyEntity>,
    customerPayments: List<PaymentEntity>,
    onClick: () -> Unit,
    onRecordPayment: () -> Unit,
    onEditCustomer: () -> Unit,
    onDeleteCustomer: () -> Unit
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe Right -> launch Call
                    launchPhoneCall(context, customer.mobile)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe Left -> Delete Customer Confirmation
                    onDeleteCustomer()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> RoyalBluePrimary
                            SwipeToDismissBoxValue.EndToStart -> ErrorRed
                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .clickable { launchPhoneCall(context, customer.mobile) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldGreenSecondary)
                                .clickable {
                                    val msg = "Hello ${customer.name}, greeting from your LIC Advisor!"
                                    launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .clickable { onEditCustomer() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable { onDeleteCustomer() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Trash", tint = ErrorRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trash", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        content = {
            CustomerCard(
                customer = customer,
                customerPolicies = customerPolicies,
                customerPayments = customerPayments,
                onClick = onClick,
                onRecordPayment = onRecordPayment
            )
        }
    )
}

// PREMIUM MATERIAL 3 CUSTOMER CARD
@Composable
fun CustomerCard(
    customer: CustomerEntity,
    customerPolicies: List<PolicyEntity>,
    customerPayments: List<PaymentEntity>,
    onClick: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    // Calculate Status Badge
    val isOverdue = customerPolicies.any { policy ->
        try {
            val d = LocalDate.parse(policy.dueDate)
            d.isBefore(today) && !policy.status.equals("Paid-up", ignoreCase = true)
        } catch (e: Exception) { false }
    }
    val isUpcomingDue = !isOverdue && customerPolicies.any { policy ->
        try {
            val d = LocalDate.parse(policy.dueDate)
            (d.isEqual(today) || (d.isAfter(today) && d.isBefore(today.plusDays(30)))) &&
                    !policy.status.equals("Paid-up", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    val statusBadgeText = when {
        isOverdue -> "Overdue"
        isUpcomingDue -> "Upcoming Due"
        else -> "Active"
    }

    val (badgeBgColor, badgeTextColor) = when {
        isOverdue -> ErrorRedContainer to ErrorRed
        isUpcomingDue -> AccentOrangeContainer to OnAccentOrangeContainer
        else -> EmeraldGreenContainer to EmeraldGreenSecondary
    }

    // Outstanding Amount
    val outstandingAmount = customerPolicies
        .filter { !it.status.equals("Paid-up", ignoreCase = true) }
        .sumOf { it.premiumAmount }

    // Next Due Date
    val nextDueDate = customerPolicies
        .mapNotNull {
            try { LocalDate.parse(it.dueDate) } catch (e: Exception) { null }
        }
        .minOrNull()?.toString() ?: "N/A"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HEADER ROW: Avatar, Name, Mobile, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomerAvatarWithPhoto(
                    customer = customer,
                    size = 52.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

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

                    Spacer(modifier = Modifier.height(3.dp))

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
                }

                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // METRICS ROW: Total Policies, Outstanding, Next Due
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricColumn(
                        label = "Total Policies",
                        value = "${customerPolicies.size} ${if (customerPolicies.size == 1) "Policy" else "Policies"}",
                        valueColor = RoyalBluePrimary
                    )

                    MetricColumn(
                        label = "Outstanding",
                        value = "₹${"%.0f".format(outstandingAmount)}",
                        valueColor = if (outstandingAmount > 0) ErrorRed else EmeraldGreenSecondary
                    )

                    MetricColumn(
                        label = "Next Due",
                        value = nextDueDate,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // QUICK ACTIONS ROW: Call, WhatsApp, View Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Quick Action
                OutlinedButton(
                    onClick = { launchPhoneCall(context, customer.mobile) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Call",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBluePrimary
                    )
                }

                // WhatsApp Quick Action
                OutlinedButton(
                    onClick = {
                        val msg = "Hello ${customer.name}, greeting from your LIC Advisor!"
                        launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreenSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenSecondary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "WhatsApp",
                        tint = EmeraldGreenSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "WhatsApp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreenSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // View Profile Quick Action
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View Profile",
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

// SKELETON LOADER
@Composable
fun CustomerSkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Gray.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

// ERROR STATE CARD
@Composable
fun CustomerErrorCard(
    errorMessage: String = "Failed to load customer records.",
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorRedContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = ErrorRed,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Unable to Display Customers",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// EMPTY STATE (Centered Vertically, Large Icon, Clean M3 Styling)
@Composable
fun CustomerEmptyState(
    onAddFirstCustomer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = RoyalBlueContainer,
                border = BorderStroke(2.dp, RoyalBluePrimary.copy(alpha = 0.3f)),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No Clients Yet",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add your first client to start managing LIC policies.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddFirstCustomer,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("add_first_customer_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ Add First Customer",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                )
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

    // Expand / Collapse state for 5 Sections
    var isPersonalExpanded by remember { mutableStateOf(true) }
    var isPoliciesExpanded by remember { mutableStateOf(true) }
    var isPaymentsExpanded by remember { mutableStateOf(true) }
    var isDocumentsExpanded by remember { mutableStateOf(true) }
    var isNotesExpanded by remember { mutableStateOf(true) }

    // More dropdown menu
    var showMoreMenu by remember { mutableStateOf(false) }

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

    // CRM Metrics
    val totalPoliciesCount = customerPolicies.size
    val activePoliciesCount = customerPolicies.count { it.status.equals("Active", ignoreCase = true) }
    val duePremiumAmount = customerPolicies.filter {
        !it.status.equals("Paid-up", ignoreCase = true) && !it.status.equals("Matured", ignoreCase = true)
    }.sumOf { it.premiumAmount }

    val totalOutstandingBalance = remember(customerPolicies, customerPayments) {
        customerPolicies.fold(0.0) { acc, policy ->
            val policyPayments = customerPayments.filter { it.policyId == policy.id }
            acc + getPolicyOutstandingBalance(policy, policyPayments)
        }
    }

    val nomineeName = customerPolicies.mapNotNull { it.nominee.takeIf { n -> n.isNotBlank() } }.firstOrNull() ?: "Nominee Specified in Policy"

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
                            text = "360° Customer Profile • $totalPoliciesCount Policies",
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
                    IconButton(onClick = {
                        val shareSummary = buildString {
                            appendLine("LIC CUSTOMER PROFILE - 360° CRM")
                            appendLine("Name: ${customer.name}")
                            appendLine("Mobile: ${customer.mobile}")
                            appendLine("Total Policies: $totalPoliciesCount")
                            appendLine("Active Policies: $activePoliciesCount")
                            appendLine("Due Premium: ₹${"%.0f".format(duePremiumAmount)}")
                            appendLine("Outstanding Balance: ₹${"%.0f".format(totalOutstandingBalance)}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, shareSummary)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Customer Profile"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Profile") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBluePrimary) },
                            onClick = {
                                showMoreMenu = false
                                onEditCustomer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive Client") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = AccentOrange) },
                            onClick = {
                                showMoreMenu = false
                                Toast.makeText(context, "${customer.name} moved to archive.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Customer", color = ErrorRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                            onClick = {
                                showMoreMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalBluePrimary)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PROFILE HEADER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RoyalBluePrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Large Circular Customer Photo
                            CustomerAvatarWithPhoto(customer = customer, size = 80.dp)
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

                                    // Status Badge (Active / Inactive)
                                    Surface(
                                        color = if (activePoliciesCount > 0 || customerPolicies.isEmpty()) EmeraldGreenSecondary else Color.Gray,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (activePoliciesCount > 0 || customerPolicies.isEmpty()) "Active" else "Inactive",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+91 ${customer.mobile}",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                if (customer.occupation.isNotBlank()) {
                                    Text(
                                        text = "💼 ${customer.occupation}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Call & WhatsApp Header Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { launchPhoneCall(context, customer.mobile) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RoyalBluePrimary)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    val msg = "Hello ${customer.name}, greetings from your LIC Advisor!"
                                    launchWhatsAppMessage(context, customer.whatsapp.ifEmpty { customer.mobile }, msg)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 2. SUMMARY CARDS (Total Policies, Active Policies, Due Premium, Outstanding Amount)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Portfolio Summary",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Policies
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = RoyalBlueContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("$totalPoliciesCount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                                Text("Total Policies", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }

                        // Active Policies
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = EmeraldGreenContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("$activePoliciesCount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                Text("Active Policies", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Due Premium
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AccentOrangeContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("₹${"%.0f".format(duePremiumAmount)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 18.sp))
                                Text("Due Premium", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }

                        // Outstanding Amount
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = if (totalOutstandingBalance > 0) ErrorRedContainer else EmeraldGreenContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    imageVector = if (totalOutstandingBalance > 0) Icons.Default.Warning else Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (totalOutstandingBalance > 0) ErrorRed else EmeraldGreenSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "₹${"%.0f".format(totalOutstandingBalance)}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalOutstandingBalance > 0) ErrorRed else EmeraldGreenSecondary,
                                        fontSize = 18.sp
                                    )
                                )
                                Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }
                    }
                }
            }

            // 3. QUICK ACTIONS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Quick Actions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Add Policy
                            Button(
                                onClick = onAddPolicyForCustomer,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Policy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Collect Premium
                            Button(
                                onClick = {
                                    val targetPolicy = customerPolicies.firstOrNull { it.status.equals("Active", ignoreCase = true) || it.status.equals("Due", ignoreCase = true) }
                                        ?: customerPolicies.firstOrNull()
                                    policyForPaymentCollection = targetPolicy
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Collect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Send WhatsApp Reminder
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
                                modifier = Modifier.weight(1.1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WA Reminder", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            // Call Customer
                            OutlinedButton(
                                onClick = { launchPhoneCall(context, customer.mobile) },
                                modifier = Modifier.weight(0.9f).height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                            }
                        }
                    }
                }
            }

            // SECTION 1: PERSONAL INFORMATION (Expandable Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPersonalExpanded = !isPersonalExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("1. Personal Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            IconButton(onClick = { isPersonalExpanded = !isPersonalExpanded }) {
                                Icon(
                                    imageVector = if (isPersonalExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section"
                                )
                            }
                        }

                        if (isPersonalExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            DetailItem("Date of Birth (DOB)", customer.dob.ifEmpty { "N/A" })
                            DetailItem("Marriage Anniversary", customer.anniversary.ifEmpty { "N/A" })
                            DetailItem("Residential Address", customer.address.ifEmpty { "N/A" })
                            DetailItem("Occupation", customer.occupation.ifEmpty { "N/A" })
                            DetailItem("Nominee Name", nomineeName)
                            DetailItem("Nominee Relationship", "Family / Primary Nominee")
                            DetailItem("Aadhaar Number", customer.aadhaar.ifEmpty { "N/A" })
                            DetailItem("PAN Number", customer.pan.ifEmpty { "N/A" })

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onEditCustomer) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Personal Details")
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: POLICIES (Expandable Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPoliciesExpanded = !isPoliciesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("2. Policies (${customerPolicies.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            IconButton(onClick = { isPoliciesExpanded = !isPoliciesExpanded }) {
                                Icon(
                                    imageVector = if (isPoliciesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section"
                                )
                            }
                        }

                        if (isPoliciesExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (customerPolicies.isEmpty()) {
                                Text("No linked policies found. Tap below to add policy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    customerPolicies.forEach { policy ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(policy.planName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                    CustomerStatusBadge(status = policy.status)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Policy #: ${policy.policyNumber}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                                    Text("Premium: ₹${"%.0f".format(policy.premiumAmount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Due Date: ${policy.dueDate}", style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange, fontWeight = FontWeight.Bold))
                                                    Text("Mode: ${policy.premiumMode}", style = MaterialTheme.typography.labelSmall)
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewingPolicyDetail = policy },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f).height(34.dp),
                                                        contentPadding = PaddingValues(2.dp)
                                                    ) {
                                                        Text("View", fontSize = 10.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { editingPolicy = policy },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f).height(34.dp),
                                                        contentPadding = PaddingValues(2.dp)
                                                    ) {
                                                        Text("Edit", fontSize = 10.sp)
                                                    }
                                                    Button(
                                                        onClick = { policyForPaymentCollection = policy },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenSecondary),
                                                        modifier = Modifier.weight(1.2f).height(34.dp),
                                                        contentPadding = PaddingValues(2.dp)
                                                    ) {
                                                        Text("Collect", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onAddPolicyForCustomer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add New Policy")
                            }
                        }
                    }
                }
            }

            // SECTION 3: PAYMENT HISTORY (Expandable Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPaymentsExpanded = !isPaymentsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("3. Payment History (${customerPayments.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            IconButton(onClick = { isPaymentsExpanded = !isPaymentsExpanded }) {
                                Icon(
                                    imageVector = if (isPaymentsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section"
                                )
                            }
                        }

                        if (isPaymentsExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (customerPayments.isEmpty()) {
                                Text("No payment transactions recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                com.example.ui.payment.CustomerPaymentHistoryDataTable(
                                    payments = customerPayments,
                                    policies = customerPolicies,
                                    customerName = customer.name,
                                    onEdit = { editingPayment = it },
                                    onDelete = { deletingPayment = it },
                                    onReceipt = { viewingReceiptPayment = it }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 4: DOCUMENTS (Aadhaar, PAN, Policy Documents, Nominee Documents)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDocumentsExpanded = !isDocumentsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("4. Documents Vault (${customerDocs.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            IconButton(onClick = { isDocumentsExpanded = !isDocumentsExpanded }) {
                                Icon(
                                    imageVector = if (isDocumentsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section"
                                )
                            }
                        }

                        if (isDocumentsExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            val requiredDocTypes = listOf("Aadhaar Card", "PAN Card", "Policy Documents", "Nominee Documents")

                            requiredDocTypes.forEach { docType ->
                                val matchingDoc = customerDocs.firstOrNull {
                                    it.docType.contains(docType.replace(" Card", "").replace(" Documents", ""), ignoreCase = true)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = when (docType) {
                                                "Aadhaar Card" -> Icons.Default.Badge
                                                "PAN Card" -> Icons.Default.CreditCard
                                                "Policy Documents" -> Icons.Default.Description
                                                else -> Icons.Default.FolderShared
                                            },
                                            contentDescription = null,
                                            tint = RoyalBluePrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(docType, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = matchingDoc?.title ?: "Status: Pending Scan",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (matchingDoc != null) EmeraldGreenSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (matchingDoc != null) {
                                        Row {
                                            IconButton(onClick = {
                                                val shareText = "Document: ${matchingDoc.title}\nClient: ${customer.name}"
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share Document"))
                                            }) {
                                                Icon(Icons.Default.Share, contentDescription = "Share", tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { viewModel.deleteDocument(matchingDoc) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showAddDocDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("+ Upload", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showAddDocDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload New Document")
                            }
                        }
                    }
                }
            }

            // SECTION 5: NOTES (Agent Notes)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isNotesExpanded = !isNotesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("5. Agent Notes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary))
                            }
                            IconButton(onClick = { isNotesExpanded = !isNotesExpanded }) {
                                Icon(
                                    imageVector = if (isNotesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section"
                                )
                            }
                        }

                        if (isNotesExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (customer.notes.isBlank()) {
                                Text(
                                    text = "No agent notes recorded yet. Tap below to add client notes or follow-up remarks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = customer.notes,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showAddNoteDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add / Edit Agent Notes")
                            }
                        }
                    }
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
                                    // Circular Avatar with Clean Photo & Premium Border
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                            .border(2.5.dp, RoyalBluePrimary, CircleShape)
                                            .background(RoyalBlueContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Crossfade(
                                            targetState = Triple(selectedBitmap, selectedImageUri, photoUri),
                                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
                                            label = "ClientPhotoCrossfade"
                                        ) { (bitmap, uri, photoUrl) ->
                                            when {
                                                bitmap != null -> {
                                                    Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = "Selected Photo",
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                                uri != null -> {
                                                    coil.compose.AsyncImage(
                                                        model = uri,
                                                        contentDescription = "Selected Photo",
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                                photoUrl.isNotBlank() -> {
                                                    coil.compose.AsyncImage(
                                                        model = photoUrl,
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

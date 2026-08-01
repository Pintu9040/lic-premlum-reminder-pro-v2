package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.auth.*
import com.example.ui.customer.*
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.documents.DocumentListScreen
import com.example.ui.payment.PaymentCollectionDialog
import com.example.ui.payment.PaymentHistoryScreen
import com.example.ui.policy.*
import com.example.ui.reminders.ReminderListScreen
import com.example.ui.reports.ReportScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.LICReminderProTheme
import com.example.ui.theme.RoyalBluePrimary
import com.example.ui.theme.RoyalBlueContainer
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.ErrorRed

enum class AppNavigationTab {
    DASHBOARD,
    CUSTOMERS,
    POLICIES,
    PAYMENTS,
    REMINDERS,
    REPORTS,
    DOCUMENTS,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    private val licViewModel: LicViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "FirebaseApp init error: ${e.localizedMessage}")
        }
        enableEdgeToEdge()

        setContent {
            val agentProfile by licViewModel.agentProfile.collectAsState()
            val isDark = when (agentProfile?.themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            var isPinUnlocked by remember { mutableStateOf(false) }
            val pinCode = agentProfile?.pinCode ?: ""

            LICReminderProTheme(darkTheme = isDark) {
                val authState by authViewModel.authState.collectAsState()

                if (pinCode.isNotBlank() && !isPinUnlocked && authState is AuthState.LoggedIn) {
                    PinLockScreen(
                        correctPin = pinCode,
                        onUnlocked = { isPinUnlocked = true }
                    )
                } else {
                    when (authState) {
                        is AuthState.LoggedOut -> {
                            var authScreen by remember { mutableStateOf("login") }
                            when (authScreen) {
                                "login" -> LoginScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToRegister = { authScreen = "register" },
                                    onNavigateToForgotPassword = { authScreen = "forgot" }
                                )
                                "register" -> RegisterScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToLogin = { authScreen = "login" }
                                )
                                "forgot" -> ForgotPasswordScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToLogin = { authScreen = "login" }
                                )
                            }
                        }
                        is AuthState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator(color = RoyalBluePrimary)
                            }
                        }
                        is AuthState.LoggedIn -> {
                            MainAppContent(
                                licViewModel = licViewModel,
                                authViewModel = authViewModel
                            )
                        }
                        is AuthState.Error -> {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onNavigateToRegister = { },
                                onNavigateToForgotPassword = { }
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class ScreenDestination {
    object Dashboard : ScreenDestination()
    object Customers : ScreenDestination()
    data class CustomerDetail(val customer: CustomerEntity) : ScreenDestination()
    object Policies : ScreenDestination()
    data class PolicyDetail(val policy: PolicyEntity) : ScreenDestination()
    object Reminders : ScreenDestination()
    object Payments : ScreenDestination()
    object Reports : ScreenDestination()
    object Documents : ScreenDestination()
    object Settings : ScreenDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    licViewModel: LicViewModel,
    authViewModel: AuthViewModel
) {
    val backStack = remember { mutableStateListOf<ScreenDestination>(ScreenDestination.Dashboard) }
    val currentDestination = backStack.lastOrNull() ?: ScreenDestination.Dashboard

    fun navigateTo(destination: ScreenDestination) {
        if (currentDestination == destination) return
        if (destination == ScreenDestination.Dashboard) {
            backStack.clear()
            backStack.add(ScreenDestination.Dashboard)
        } else {
            backStack.add(destination)
        }
    }

    fun handleBackPress() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.clear()
            backStack.add(ScreenDestination.Dashboard)
        }
    }

    BackHandler(enabled = currentDestination != ScreenDestination.Dashboard) {
        handleBackPress()
    }

    val currentTab = when (currentDestination) {
        ScreenDestination.Dashboard -> AppNavigationTab.DASHBOARD
        ScreenDestination.Customers, is ScreenDestination.CustomerDetail -> AppNavigationTab.CUSTOMERS
        ScreenDestination.Policies, is ScreenDestination.PolicyDetail -> AppNavigationTab.POLICIES
        ScreenDestination.Reminders -> AppNavigationTab.REMINDERS
        ScreenDestination.Payments -> AppNavigationTab.PAYMENTS
        ScreenDestination.Reports -> AppNavigationTab.REPORTS
        ScreenDestination.Documents -> AppNavigationTab.DOCUMENTS
        ScreenDestination.Settings -> AppNavigationTab.SETTINGS
    }

    // Dialog States
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var showAddPolicyDialog by remember { mutableStateOf(false) }
    var policyToEdit by remember { mutableStateOf<PolicyEntity?>(null) }

    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }

    val context = LocalContext.current
    val customers by licViewModel.customers.collectAsState()
    val policies by licViewModel.policies.collectAsState()

    Scaffold(
        topBar = {
            if (currentDestination != ScreenDestination.Dashboard &&
                currentDestination !is ScreenDestination.CustomerDetail &&
                currentDestination !is ScreenDestination.PolicyDetail
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentDestination) {
                                is ScreenDestination.Customers -> "Clients Directory"
                                is ScreenDestination.Policies -> "Policy Portfolio"
                                is ScreenDestination.Reminders -> "Reminders & Dues"
                                is ScreenDestination.Payments -> "Payment History"
                                is ScreenDestination.Reports -> "Reports & Analytics"
                                is ScreenDestination.Documents -> "Document Locker"
                                is ScreenDestination.Settings -> "Profile & Settings"
                                else -> ""
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { handleBackPress() },
                            modifier = Modifier.testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RoyalBluePrimary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = RoyalBluePrimary,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.DASHBOARD,
                    onClick = { navigateTo(ScreenDestination.Dashboard) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("nav_tab_dashboard"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.75f),
                        unselectedTextColor = Color.White.copy(alpha = 0.75f)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.CUSTOMERS,
                    onClick = { navigateTo(ScreenDestination.Customers) },
                    icon = { Icon(Icons.Default.People, contentDescription = "Customers") },
                    label = { Text("Clients") },
                    modifier = Modifier.testTag("nav_tab_customers"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.75f),
                        unselectedTextColor = Color.White.copy(alpha = 0.75f)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.POLICIES,
                    onClick = { navigateTo(ScreenDestination.Policies) },
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "Policies") },
                    label = { Text("Policies") },
                    modifier = Modifier.testTag("nav_tab_policies"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.75f),
                        unselectedTextColor = Color.White.copy(alpha = 0.75f)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.REMINDERS,
                    onClick = { navigateTo(ScreenDestination.Reminders) },
                    icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Reminders") },
                    label = { Text("Reminders") },
                    modifier = Modifier.testTag("nav_tab_reminders"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.75f),
                        unselectedTextColor = Color.White.copy(alpha = 0.75f)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.SETTINGS ||
                            currentTab == AppNavigationTab.REPORTS ||
                            currentTab == AppNavigationTab.DOCUMENTS ||
                            currentTab == AppNavigationTab.PAYMENTS,
                    onClick = { navigateTo(ScreenDestination.Settings) },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
                    label = { Text("More") },
                    modifier = Modifier.testTag("nav_tab_more"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = AccentOrange,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.75f),
                        unselectedTextColor = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (currentDestination is ScreenDestination.Customers) {
                FloatingActionButton(
                    onClick = { showAddCustomerDialog = true },
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_customer")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
                }
            } else if (currentDestination is ScreenDestination.Policies) {
                FloatingActionButton(
                    onClick = { showAddPolicyDialog = true },
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_policy")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "Add Policy")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Secondary top tab navigation when on Settings or More options
            if (currentTab == AppNavigationTab.SETTINGS ||
                currentTab == AppNavigationTab.REPORTS ||
                currentTab == AppNavigationTab.DOCUMENTS ||
                currentTab == AppNavigationTab.PAYMENTS
            ) {
                ScrollableTabRow(
                    selectedTabIndex = when (currentTab) {
                        AppNavigationTab.PAYMENTS -> 0
                        AppNavigationTab.REPORTS -> 1
                        AppNavigationTab.DOCUMENTS -> 2
                        else -> 3
                    },
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = currentTab == AppNavigationTab.PAYMENTS,
                        onClick = { navigateTo(ScreenDestination.Payments) },
                        text = { Text("Payments", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.REPORTS,
                        onClick = { navigateTo(ScreenDestination.Reports) },
                        text = { Text("Reports", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.DOCUMENTS,
                        onClick = { navigateTo(ScreenDestination.Documents) },
                        text = { Text("Documents", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.SETTINGS,
                        onClick = { navigateTo(ScreenDestination.Settings) },
                        text = { Text("Profile & Settings", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentDestination) {
                    is ScreenDestination.Dashboard -> {
                        DashboardScreen(
                            viewModel = licViewModel,
                            onNavigateToCustomers = { navigateTo(ScreenDestination.Customers) },
                            onNavigateToPolicies = { navigateTo(ScreenDestination.Policies) },
                            onNavigateToReminders = { navigateTo(ScreenDestination.Reminders) },
                            onNavigateToPayments = { navigateTo(ScreenDestination.Payments) },
                            onNavigateToReports = { navigateTo(ScreenDestination.Reports) },
                            onAddCustomer = { showAddCustomerDialog = true },
                            onAddPolicy = { showAddPolicyDialog = true },
                            onCollectPremium = { policyForPaymentCollection = it }
                        )
                    }

                    is ScreenDestination.Customers -> {
                        CustomerListScreen(
                            viewModel = licViewModel,
                            onSelectCustomer = { navigateTo(ScreenDestination.CustomerDetail(it)) },
                            onAddCustomer = { showAddCustomerDialog = true }
                        )
                    }

                    is ScreenDestination.CustomerDetail -> {
                        val cust = (currentDestination as ScreenDestination.CustomerDetail).customer
                        val activeCustomer = customers.find { it.id == cust.id } ?: cust
                        CustomerDetailScreen(
                            customer = activeCustomer,
                            viewModel = licViewModel,
                            onEditCustomer = { customerToEdit = activeCustomer },
                            onAddPolicyForCustomer = { showAddPolicyDialog = true },
                            onBack = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.Policies -> {
                        PolicyListScreen(
                            viewModel = licViewModel,
                            onSelectPolicy = { navigateTo(ScreenDestination.PolicyDetail(it)) },
                            onAddPolicy = { showAddPolicyDialog = true },
                            onCollectPremium = { policyForPaymentCollection = it }
                        )
                    }

                    is ScreenDestination.PolicyDetail -> {
                        val pol = (currentDestination as ScreenDestination.PolicyDetail).policy
                        val policiesList by licViewModel.policies.collectAsState()
                        val activePolicy = policiesList.find { it.id == pol.id } ?: pol
                        PolicyDetailScreen(
                            policy = activePolicy,
                            viewModel = licViewModel,
                            onEditPolicy = { policyToEdit = activePolicy },
                            onCollectPremium = { policyForPaymentCollection = activePolicy },
                            onBack = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.Reminders -> {
                        ReminderListScreen(
                            viewModel = licViewModel,
                            onCollectPremium = { policyForPaymentCollection = it },
                            onViewCustomerProfile = { cust -> navigateTo(ScreenDestination.CustomerDetail(cust)) },
                            onViewPolicyDetail = { pol -> navigateTo(ScreenDestination.PolicyDetail(pol)) },
                            onBack = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.Payments -> {
                        PaymentHistoryScreen(viewModel = licViewModel)
                    }

                    is ScreenDestination.Reports -> {
                        ReportScreen(viewModel = licViewModel)
                    }

                    is ScreenDestination.Documents -> {
                        DocumentListScreen(viewModel = licViewModel)
                    }

                    is ScreenDestination.Settings -> {
                        SettingsScreen(
                            viewModel = licViewModel,
                            onLogout = { authViewModel.logout() }
                        )
                    }
                }
            }
        }
    }

    // Customer Add/Edit Dialog
    if (showAddCustomerDialog || customerToEdit != null) {
        AddEditCustomerDialog(
            initialCustomer = customerToEdit,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { customer ->
                if (customerToEdit != null) {
                    licViewModel.updateCustomer(customer)
                } else {
                    licViewModel.addCustomer(customer)
                }
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    // Policy Add/Edit Dialog
    if (showAddPolicyDialog || policyToEdit != null) {
        AddEditPolicyDialog(
            initialPolicy = policyToEdit,
            customersList = customers,
            existingPolicies = policies,
            onDismiss = {
                showAddPolicyDialog = false
                policyToEdit = null
            },
            onSave = { policy ->
                if (policyToEdit != null) {
                    licViewModel.updatePolicy(policy)
                    android.widget.Toast.makeText(context, "Policy updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    licViewModel.addPolicy(policy)
                    android.widget.Toast.makeText(context, "Policy added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                }
                showAddPolicyDialog = false
                policyToEdit = null
            }
        )
    }

    // Premium Collection Dialog
    policyForPaymentCollection?.let { policy ->
        PaymentCollectionDialog(
            policy = policy,
            onDismiss = { policyForPaymentCollection = null },
            onCollect = { amount, lateFee, mode, receiptNo, notes ->
                licViewModel.collectPremium(
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
}

@Composable
fun PinLockScreen(
    correctPin: String,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoyalBluePrimary),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoyalBlueContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(32.dp))
                    }
                }

                Text(
                    text = "LIC Security Lock",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Enter 4-Digit Security Passcode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 4) {
                            enteredPin = it
                            errorMsg = ""
                        }
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(180.dp)
                )

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = {
                        if (enteredPin == correctPin) {
                            onUnlocked()
                        } else {
                            errorMsg = "Incorrect Security PIN. Please retry."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Vault", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

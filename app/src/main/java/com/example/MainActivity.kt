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
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel
import com.example.ui.auth.*
import com.example.ui.calendar.CalendarScreen
import com.example.ui.customer.*
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.documents.DocumentListScreen
import com.example.ui.documents.DocumentVaultScreen
import com.example.ui.payment.PaymentCollectionDialog
import com.example.ui.payment.PaymentHistoryScreen
import com.example.ui.payment.PrintPreviewScreen
import com.example.ui.payment.ReceiptScreen
import com.example.ui.payment.RecordPaymentScreen
import com.example.ui.policy.*
import com.example.ui.reminders.ReminderCenterScreen
import com.example.ui.reminders.ReminderListScreen
import com.example.ui.reports.ReportScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.LICReminderProTheme
import com.example.ui.theme.RoyalBluePrimary
import com.example.ui.theme.RoyalBlueContainer
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.ErrorRed

import com.example.util.BiometricAuthManager
import com.example.util.SecurityUtils

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

class MainActivity : androidx.fragment.app.FragmentActivity() {
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
            val rawPinCode = agentProfile?.pinCode ?: ""

            fun isPinValid(pin: String?): Boolean {
                if (pin.isNullOrBlank()) return false
                val trimmed = pin.trim()
                return trimmed.length == 4 && trimmed.all { it.isDigit() }
            }

            val hasValidPin = isPinValid(rawPinCode)

            LaunchedEffect(rawPinCode) {
                if (rawPinCode.isNotBlank() && !hasValidPin) {
                    agentProfile?.let { prof ->
                        licViewModel.saveAgentProfile(prof.copy(pinCode = ""))
                    }
                }
            }

            LICReminderProTheme(darkTheme = isDark) {
                val authState by authViewModel.authState.collectAsState()

                if (hasValidPin && !isPinUnlocked && authState is AuthState.LoggedIn) {
                    PinLockScreen(
                        correctPin = rawPinCode.trim(),
                        agentProfile = agentProfile,
                        onUnlocked = { isPinUnlocked = true },
                        onResetPin = { newPinHash ->
                            agentProfile?.let { prof ->
                                licViewModel.saveAgentProfile(prof.copy(pinCode = newPinHash))
                            }
                            isPinUnlocked = true
                        }
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
    object Calendar : ScreenDestination()
    object RecordPayment : ScreenDestination()
    object Payments : ScreenDestination()
    data class CustomerPaymentHistory(val customer: CustomerEntity) : ScreenDestination()
    object Reports : ScreenDestination()
    object Documents : ScreenDestination()
    object Settings : ScreenDestination()
    object AddPolicy : ScreenDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    licViewModel: LicViewModel,
    authViewModel: AuthViewModel
) {
    val customersList by licViewModel.customers.collectAsState()
    val initialScreen = ScreenDestination.Dashboard
    val backStack = remember { mutableStateListOf<ScreenDestination>(initialScreen) }
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
        ScreenDestination.Customers, is ScreenDestination.CustomerDetail, is ScreenDestination.CustomerPaymentHistory -> AppNavigationTab.CUSTOMERS
        ScreenDestination.Policies, is ScreenDestination.PolicyDetail, ScreenDestination.AddPolicy -> AppNavigationTab.POLICIES
        ScreenDestination.Reminders, ScreenDestination.Calendar -> AppNavigationTab.REMINDERS
        ScreenDestination.RecordPayment, ScreenDestination.Payments -> AppNavigationTab.PAYMENTS
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
                currentDestination !is ScreenDestination.CustomerPaymentHistory &&
                currentDestination !is ScreenDestination.PolicyDetail &&
                currentDestination != ScreenDestination.Payments &&
                currentDestination != ScreenDestination.RecordPayment &&
                currentDestination != ScreenDestination.AddPolicy &&
                currentDestination != ScreenDestination.Reports
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
            if (currentDestination != ScreenDestination.AddPolicy) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 8.dp
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RoyalBluePrimary,
                        selectedTextColor = RoyalBluePrimary,
                        indicatorColor = RoyalBlueContainer,
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    )

                    NavigationBarItem(
                        selected = currentTab == AppNavigationTab.DASHBOARD,
                        onClick = { navigateTo(ScreenDestination.Dashboard) },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                        label = { Text("Home", fontWeight = if (currentTab == AppNavigationTab.DASHBOARD) FontWeight.Bold else FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_dashboard"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentTab == AppNavigationTab.CUSTOMERS,
                        onClick = { navigateTo(ScreenDestination.Customers) },
                        icon = { Icon(Icons.Default.People, contentDescription = "Customers") },
                        label = { Text("Clients", fontWeight = if (currentTab == AppNavigationTab.CUSTOMERS) FontWeight.Bold else FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_customers"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentTab == AppNavigationTab.POLICIES,
                        onClick = { navigateTo(ScreenDestination.Policies) },
                        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "Policies") },
                        label = { Text("Policies", fontWeight = if (currentTab == AppNavigationTab.POLICIES) FontWeight.Bold else FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_policies"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentTab == AppNavigationTab.REMINDERS,
                        onClick = { navigateTo(ScreenDestination.Reminders) },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Reminders") },
                        label = { Text("Reminders", fontWeight = if (currentTab == AppNavigationTab.REMINDERS) FontWeight.Bold else FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_reminders"),
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentTab == AppNavigationTab.SETTINGS ||
                                currentTab == AppNavigationTab.REPORTS ||
                                currentTab == AppNavigationTab.DOCUMENTS ||
                                currentTab == AppNavigationTab.PAYMENTS,
                        onClick = { navigateTo(ScreenDestination.Settings) },
                        icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
                        label = { Text("More", fontWeight = if (currentTab == AppNavigationTab.SETTINGS) FontWeight.Bold else FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_more"),
                        colors = navItemColors
                    )
                }
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
                currentTab == AppNavigationTab.DOCUMENTS
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
                            onNavigateToCalendar = { navigateTo(ScreenDestination.Calendar) },
                            onNavigateToPayments = { navigateTo(ScreenDestination.Payments) },
                            onNavigateToRecordPayment = { navigateTo(ScreenDestination.RecordPayment) },
                            onNavigateToReports = { navigateTo(ScreenDestination.Reports) },
                            onNavigateToDocuments = { navigateTo(ScreenDestination.Documents) },
                            onNavigateToSettings = { navigateTo(ScreenDestination.Settings) },
                            onAddCustomer = { showAddCustomerDialog = true },
                            onAddPolicy = { showAddPolicyDialog = true },
                            onCollectPremium = { policyForPaymentCollection = it },
                            onNavigateToCustomerPaymentHistory = { selectedCustomer ->
                                navigateTo(ScreenDestination.CustomerPaymentHistory(selectedCustomer))
                            }
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
                        CustomerProfileScreen(
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
                        ReminderCenterScreen(
                            viewModel = licViewModel,
                            onBack = { handleBackPress() },
                            onCollectPremium = { policyForPaymentCollection = it },
                            onViewPolicyDetail = { polNum ->
                                val matchingPolicy = licViewModel.policies.value.find { it.policyNumber.equals(polNum, ignoreCase = true) }
                                if (matchingPolicy != null) {
                                    navigateTo(ScreenDestination.PolicyDetail(matchingPolicy))
                                }
                            }
                        )
                    }

                    is ScreenDestination.Calendar -> {
                        CalendarScreen(
                            onBackClick = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.RecordPayment -> {
                        RecordPaymentScreen(
                            viewModel = licViewModel,
                            onBack = { handleBackPress() },
                            onPaymentSaved = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.Payments -> {
                        PaymentHistoryScreen(
                            viewModel = licViewModel,
                            onBack = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.CustomerPaymentHistory -> {
                        val cust = (currentDestination as ScreenDestination.CustomerPaymentHistory).customer
                        PaymentHistoryScreen(
                            viewModel = licViewModel,
                            initialCustomer = cust,
                            onBack = { handleBackPress() }
                        )
                    }

                    is ScreenDestination.Reports -> {
                        ReportScreen(
                            viewModel = licViewModel,
                            onNavigateToPayments = { navigateTo(ScreenDestination.Payments) },
                            onNavigateToReports = { navigateTo(ScreenDestination.Reports) },
                            onNavigateToDocuments = { navigateTo(ScreenDestination.Documents) },
                            onNavigateToSettings = { navigateTo(ScreenDestination.Settings) },
                            onNavigateToHome = { navigateTo(ScreenDestination.Dashboard) },
                            onNavigateToCustomers = { navigateTo(ScreenDestination.Customers) },
                            onNavigateToPolicies = { navigateTo(ScreenDestination.Policies) },
                            onNavigateToReminders = { navigateTo(ScreenDestination.Reminders) },
                            onNavigateToCustomerDetail = { custId ->
                                val cust = customers.find { it.id == custId.toLong() }
                                    ?: com.example.data.local.CustomerEntity(id = custId.toLong(), name = "Client #$custId", mobile = "", email = "")
                                navigateTo(ScreenDestination.CustomerDetail(cust))
                            }
                        )
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

                    is ScreenDestination.AddPolicy -> {
                        DashboardScreen(
                            viewModel = licViewModel,
                            onNavigateToCustomers = { navigateTo(ScreenDestination.Customers) },
                            onNavigateToPolicies = { navigateTo(ScreenDestination.Policies) },
                            onNavigateToReminders = { navigateTo(ScreenDestination.Reminders) },
                            onNavigateToCalendar = { navigateTo(ScreenDestination.Calendar) },
                            onNavigateToPayments = { navigateTo(ScreenDestination.Payments) },
                            onNavigateToRecordPayment = { navigateTo(ScreenDestination.RecordPayment) },
                            onNavigateToReports = { navigateTo(ScreenDestination.Reports) },
                            onAddCustomer = { showAddCustomerDialog = true },
                            onAddPolicy = { showAddPolicyDialog = true },
                            onCollectPremium = { policyForPaymentCollection = it }
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
                    if (currentDestination !is ScreenDestination.Customers) {
                        navigateTo(ScreenDestination.Customers)
                    }
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
    agentProfile: com.example.data.local.AgentProfileEntity?,
    onUnlocked: () -> Unit,
    onResetPin: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }
    var isAuthenticatingBiometric by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Secure window against screenshots on Security Lock screen
    DisposableEffect(Unit) {
        SecurityUtils.setSecureFlag(context, true)
        onDispose {
            SecurityUtils.setSecureFlag(context, false)
        }
    }

    val isBiometricAvailable = remember(context) { BiometricAuthManager.isBiometricAvailable(context) }
    val isBiometricEnabled = remember(context) { SecurityUtils.isBiometricEnabled(context) }

    fun triggerBiometricAuthentication() {
        if (!isBiometricAvailable) {
            errorMsg = BiometricAuthManager.getBiometricStatusMessage(context)
            return
        }

        isAuthenticatingBiometric = true
        errorMsg = ""
        successMsg = "Authenticating biometrics..."

        BiometricAuthManager.showBiometricPrompt(
            context = context,
            title = "LIC Vault Security",
            subtitle = "Scan fingerprint or face unlock to access",
            negativeButtonText = "Use Security PIN",
            onSuccess = {
                isAuthenticatingBiometric = false
                successMsg = "Biometric Verification Succeeded!"
                onUnlocked()
            },
            onError = { err ->
                isAuthenticatingBiometric = false
                successMsg = ""
                if (err != "PIN_FALLBACK" && err != "Cancelled by user") {
                    errorMsg = err
                }
            }
        )
    }

    // Auto-launch biometric prompt if biometric login is enabled
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && isBiometricAvailable) {
            triggerBiometricAuthentication()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoyalBluePrimary),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoyalBlueContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isAuthenticatingBiometric) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = RoyalBluePrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security Lock",
                                tint = RoyalBluePrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "LIC Security Lock",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter 4-Digit Security Passcode or Scan Fingerprint",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { newValue ->
                        if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                            enteredPin = newValue
                            errorMsg = ""
                            successMsg = ""
                        }
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBluePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .width(180.dp)
                        .testTag("pin_lock_input_field")
                )

                if (successMsg.isNotBlank()) {
                    Text(
                        text = successMsg,
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (errorMsg.isNotBlank()) {
                    Text(
                        text = errorMsg,
                        color = ErrorRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val cleanEntered = enteredPin.trim()
                        if (SecurityUtils.isPinValid(cleanEntered, correctPin)) {
                            onUnlocked()
                        } else {
                            errorMsg = "Incorrect Security PIN. Please retry."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("unlock_vault_button")
                ) {
                    Text("Unlock Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Biometric Fingerprint Button
                OutlinedButton(
                    onClick = { triggerBiometricAuthentication() },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoyalBluePrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoyalBluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Fingerprint Unlock",
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Fingerprint / Face", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.testTag("forgot_pin_button")
                ) {
                    Text(
                        text = "Forgot PIN?",
                        color = RoyalBluePrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showForgotDialog) {
        var recoveryStage by remember { mutableStateOf(1) } // 1: Verify Answer/Email, 2: Set New PIN
        var recoveryAnswerInput by remember { mutableStateOf("") }
        var newPinInput by remember { mutableStateOf("") }
        var confirmNewPinInput by remember { mutableStateOf("") }

        var recoveryError by remember { mutableStateOf("") }
        var recoverySuccessMsg by remember { mutableStateOf("") }
        var isSendingEmail by remember { mutableStateOf(false) }

        // Secure screen against screenshots during Emergency Recovery
        DisposableEffect(Unit) {
            SecurityUtils.setSecureFlag(context, true)
            onDispose {
                SecurityUtils.setSecureFlag(context, false)
            }
        }

        val (isLockedOut, remainingMinutes) = remember(showForgotDialog, recoveryError) {
            SecurityUtils.checkLockoutStatus(context)
        }

        val recoveryQuestion = remember(context) { SecurityUtils.getRecoveryQuestion(context) }
        val recoveryEmail = remember(context) {
            val stored = SecurityUtils.getRecoveryEmail(context)
            if (stored.isNotBlank()) stored else (agentProfile?.email ?: "")
        }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = RoyalBluePrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (recoveryStage == 1) "Emergency PIN Recovery" else "Create New 4-Digit PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLockedOut) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Emergency recovery locked due to 5 failed attempts. Please try again in $remainingMinutes minute(s).",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (recoveryStage == 1) {
                        Text(
                            text = "Answer your Emergency Recovery Question to create a new passcode:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Question Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = recoveryQuestion,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = recoveryAnswerInput,
                            onValueChange = {
                                recoveryAnswerInput = it
                                recoveryError = ""
                                recoverySuccessMsg = ""
                            },
                            label = { Text("Your Recovery Answer") },
                            singleLine = true,
                            enabled = !isLockedOut,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_answer_input")
                        )

                        // Optional Firebase Email Reset Option
                        if (recoveryEmail.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Send Reset Link via Email",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = recoveryEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSendingEmail) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = RoyalBluePrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            isSendingEmail = true
                                            recoveryError = ""
                                            recoverySuccessMsg = ""
                                            SecurityUtils.sendPasswordResetEmail(recoveryEmail) { success, msg ->
                                                isSendingEmail = false
                                                if (success) {
                                                    recoverySuccessMsg = msg ?: "Reset email sent successfully!"
                                                } else {
                                                    recoveryError = msg ?: "Failed to send reset email."
                                                }
                                            }
                                        },
                                        enabled = !isLockedOut,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("send_recovery_email_button")
                                    ) {
                                        Text("Send Email", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Stage 2: Create New PIN
                        Text(
                            text = "Enter a new 4-digit passcode for your LIC Vault:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    newPinInput = it
                                    recoveryError = ""
                                }
                            },
                            label = { Text("New 4-Digit PIN") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_pin_recovery_input")
                        )

                        OutlinedTextField(
                            value = confirmNewPinInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    confirmNewPinInput = it
                                    recoveryError = ""
                                }
                            },
                            label = { Text("Confirm New 4-Digit PIN") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_new_pin_recovery_input")
                        )
                    }

                    if (recoverySuccessMsg.isNotBlank()) {
                        Text(
                            text = recoverySuccessMsg,
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (recoveryError.isNotBlank()) {
                        Text(
                            text = recoveryError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (recoveryStage == 1) {
                    Button(
                        onClick = {
                            val cleanAnswer = recoveryAnswerInput.trim()
                            if (cleanAnswer.isBlank()) {
                                recoveryError = "Please enter your Recovery Answer."
                                return@Button
                            }

                            val fallbacks = listOfNotNull(
                                agentProfile?.agencyCode,
                                agentProfile?.email,
                                agentProfile?.mobile,
                                agentProfile?.agentName
                            )

                            val isValid = SecurityUtils.verifyRecoveryAnswer(context, cleanAnswer, fallbacks)

                            if (isValid) {
                                SecurityUtils.resetFailedAttempts(context)
                                recoveryError = ""
                                recoverySuccessMsg = ""
                                recoveryStage = 2
                            } else {
                                val remaining = SecurityUtils.recordFailedAttempt(context)
                                if (remaining > 0) {
                                    recoveryError = "Incorrect Recovery Answer. $remaining attempt(s) remaining."
                                } else {
                                    recoveryError = "Too many failed attempts. Emergency recovery is locked for 30 minutes."
                                }
                            }
                        },
                        enabled = !isLockedOut,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("verify_recovery_answer_button")
                    ) {
                        Text("Verify Answer", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val newPin = newPinInput.trim()
                            val confirmPin = confirmNewPinInput.trim()

                            if (newPin.length != 4) {
                                recoveryError = "PIN must be exactly 4 digits."
                                return@Button
                            }
                            if (newPin != confirmPin) {
                                recoveryError = "PINs do not match."
                                return@Button
                            }

                            val hashedPin = SecurityUtils.hashPin(newPin)
                            showForgotDialog = false
                            onResetPin(hashedPin)
                            android.widget.Toast.makeText(
                                context,
                                "New 4-Digit Security PIN saved successfully!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_new_pin_recovery_button")
                    ) {
                        Text("Save New PIN & Unlock", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotDialog = false },
                    modifier = Modifier.testTag("cancel_recovery_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

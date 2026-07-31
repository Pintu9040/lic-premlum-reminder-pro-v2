package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    licViewModel: LicViewModel,
    authViewModel: AuthViewModel
) {
    var currentTab by remember { mutableStateOf(AppNavigationTab.DASHBOARD) }

    // Navigation Sub-states
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedPolicy by remember { mutableStateOf<PolicyEntity?>(null) }

    // Dialog States
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var showAddPolicyDialog by remember { mutableStateOf(false) }
    var policyToEdit by remember { mutableStateOf<PolicyEntity?>(null) }

    var policyForPaymentCollection by remember { mutableStateOf<PolicyEntity?>(null) }

    val customers by licViewModel.customers.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = RoyalBluePrimary,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentTab == AppNavigationTab.DASHBOARD,
                    onClick = {
                        currentTab = AppNavigationTab.DASHBOARD
                        selectedCustomer = null
                        selectedPolicy = null
                    },
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
                    onClick = {
                        currentTab = AppNavigationTab.CUSTOMERS
                        selectedCustomer = null
                        selectedPolicy = null
                    },
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
                    onClick = {
                        currentTab = AppNavigationTab.POLICIES
                        selectedCustomer = null
                        selectedPolicy = null
                    },
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
                    onClick = {
                        currentTab = AppNavigationTab.REMINDERS
                        selectedCustomer = null
                        selectedPolicy = null
                    },
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
                    onClick = {
                        currentTab = AppNavigationTab.SETTINGS
                        selectedCustomer = null
                        selectedPolicy = null
                    },
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
            if (currentTab == AppNavigationTab.CUSTOMERS && selectedCustomer == null) {
                FloatingActionButton(
                    onClick = { showAddCustomerDialog = true },
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_customer")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
                }
            } else if (currentTab == AppNavigationTab.POLICIES && selectedPolicy == null) {
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
                        onClick = { currentTab = AppNavigationTab.PAYMENTS },
                        text = { Text("Payments", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.REPORTS,
                        onClick = { currentTab = AppNavigationTab.REPORTS },
                        text = { Text("Reports", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.DOCUMENTS,
                        onClick = { currentTab = AppNavigationTab.DOCUMENTS },
                        text = { Text("Documents", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == AppNavigationTab.SETTINGS,
                        onClick = { currentTab = AppNavigationTab.SETTINGS },
                        text = { Text("Profile & Settings", color = Color.White, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    AppNavigationTab.DASHBOARD -> {
                        DashboardScreen(
                            viewModel = licViewModel,
                            onNavigateToCustomers = { currentTab = AppNavigationTab.CUSTOMERS },
                            onNavigateToPolicies = { currentTab = AppNavigationTab.POLICIES },
                            onNavigateToReminders = { currentTab = AppNavigationTab.REMINDERS },
                            onNavigateToPayments = { currentTab = AppNavigationTab.PAYMENTS },
                            onNavigateToReports = { currentTab = AppNavigationTab.REPORTS },
                            onAddCustomer = { showAddCustomerDialog = true },
                            onAddPolicy = { showAddPolicyDialog = true },
                            onCollectPremium = { policyForPaymentCollection = it }
                        )
                    }

                    AppNavigationTab.CUSTOMERS -> {
                        val customersList by licViewModel.customers.collectAsState()
                        val activeCustomer = customersList.find { it.id == selectedCustomer?.id } ?: selectedCustomer
                        if (activeCustomer != null) {
                            CustomerDetailScreen(
                                customer = activeCustomer,
                                viewModel = licViewModel,
                                onEditCustomer = { customerToEdit = activeCustomer },
                                onAddPolicyForCustomer = { showAddPolicyDialog = true },
                                onBack = { selectedCustomer = null }
                            )
                        } else {
                            CustomerListScreen(
                                viewModel = licViewModel,
                                onSelectCustomer = { selectedCustomer = it },
                                onAddCustomer = { showAddCustomerDialog = true }
                            )
                        }
                    }

                    AppNavigationTab.POLICIES -> {
                        val activePolicy = selectedPolicy
                        if (activePolicy != null) {
                            PolicyDetailScreen(
                                policy = activePolicy,
                                viewModel = licViewModel,
                                onEditPolicy = { policyToEdit = activePolicy },
                                onCollectPremium = { policyForPaymentCollection = activePolicy },
                                onBack = { selectedPolicy = null }
                            )
                        } else {
                            PolicyListScreen(
                                viewModel = licViewModel,
                                onSelectPolicy = { selectedPolicy = it },
                                onAddPolicy = { showAddPolicyDialog = true },
                                onCollectPremium = { policyForPaymentCollection = it }
                            )
                        }
                    }

                    AppNavigationTab.REMINDERS -> {
                        ReminderListScreen(
                            viewModel = licViewModel,
                            onCollectPremium = { policyForPaymentCollection = it }
                        )
                    }

                    AppNavigationTab.PAYMENTS -> {
                        PaymentHistoryScreen(viewModel = licViewModel)
                    }

                    AppNavigationTab.REPORTS -> {
                        ReportScreen(viewModel = licViewModel)
                    }

                    AppNavigationTab.DOCUMENTS -> {
                        DocumentListScreen(viewModel = licViewModel)
                    }

                    AppNavigationTab.SETTINGS -> {
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
            onDismiss = {
                showAddPolicyDialog = false
                policyToEdit = null
            },
            onSave = { policy ->
                if (policyToEdit != null) {
                    licViewModel.updatePolicy(policy)
                } else {
                    licViewModel.addPolicy(policy)
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

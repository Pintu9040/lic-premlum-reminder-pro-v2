package com.example.ui.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.LicViewModel

@Composable
fun ReportsScreen(
    viewModel: LicViewModel? = null,
    onNavigateToPayments: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCustomers: () -> Unit = {},
    onNavigateToPolicies: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToCustomerDetail: ((Int) -> Unit)? = null
) {
    ReportScreen(
        viewModel = viewModel,
        onNavigateToPayments = onNavigateToPayments,
        onNavigateToReports = onNavigateToReports,
        onNavigateToDocuments = onNavigateToDocuments,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToHome = onNavigateToHome,
        onNavigateToCustomers = onNavigateToCustomers,
        onNavigateToPolicies = onNavigateToPolicies,
        onNavigateToReminders = onNavigateToReminders,
        onNavigateToCustomerDetail = onNavigateToCustomerDetail
    )
}

@Preview(showBackground = true, name = "Reports & Analytics - Royal Blue Theme")
@Composable
fun ReportsScreenPreview() {
    ReportsScreen()
}


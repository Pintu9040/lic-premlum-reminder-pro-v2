package com.example.ui.payment

import androidx.compose.runtime.Composable
import com.example.ui.LicViewModel

@Composable
fun PaymentDashboardScreen(
    viewModel: LicViewModel,
    onBack: (() -> Unit)? = null
) {
    PaymentHistoryScreen(
        viewModel = viewModel,
        onBack = onBack
    )
}

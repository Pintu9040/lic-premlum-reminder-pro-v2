package com.example.ui.reminders

import androidx.compose.runtime.Composable
import com.example.data.local.CustomerEntity
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel

@Composable
fun ReminderScreen(
    viewModel: LicViewModel,
    onCollectPremium: (PolicyEntity) -> Unit = {},
    onViewCustomerProfile: (CustomerEntity) -> Unit = {},
    onViewPolicyDetail: (PolicyEntity) -> Unit = {},
    onBack: () -> Unit = {}
) {
    ReminderListScreen(
        viewModel = viewModel,
        onCollectPremium = onCollectPremium,
        onViewCustomerProfile = onViewCustomerProfile,
        onViewPolicyDetail = onViewPolicyDetail,
        onBack = onBack
    )
}

package com.example.ui.policy

import androidx.compose.runtime.Composable
import com.example.data.local.PolicyEntity
import com.example.ui.LicViewModel

@Composable
fun PolicyListScreenDelegate(
    viewModel: LicViewModel,
    onSelectPolicy: (PolicyEntity) -> Unit,
    onAddPolicy: () -> Unit,
    onCollectPremium: (PolicyEntity) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    PolicyListScreen(
        viewModel = viewModel,
        onSelectPolicy = onSelectPolicy,
        onAddPolicy = onAddPolicy,
        onCollectPremium = onCollectPremium,
        onBack = onBack
    )
}

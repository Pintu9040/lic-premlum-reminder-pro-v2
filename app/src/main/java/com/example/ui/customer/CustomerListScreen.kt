package com.example.ui.customer

import androidx.compose.runtime.Composable
import com.example.data.local.CustomerEntity
import com.example.ui.LicViewModel

@Composable
fun CustomerListScreenDelegate(
    viewModel: LicViewModel,
    onSelectCustomer: (CustomerEntity) -> Unit,
    onAddCustomer: () -> Unit
) {
    CustomerListScreen(
        viewModel = viewModel,
        onSelectCustomer = onSelectCustomer,
        onAddCustomer = onAddCustomer
    )
}

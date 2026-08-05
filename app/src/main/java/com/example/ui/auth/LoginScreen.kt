package com.example.ui.auth

import androidx.compose.runtime.Composable

@Composable
fun LoginScreenDelegate(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    LoginScreen(
        authViewModel = authViewModel,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToForgotPassword = onNavigateToForgotPassword
    )
}

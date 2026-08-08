package com.example.ui.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf("agent@licreminderpro.com") }
    var password by remember { mutableStateOf("lic12345") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    val authState by authViewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Trigger subtle entrance animations on load
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateIn = true
    }

    // Dynamic greeting based on current time of day
    val timeOfDayGreeting = remember {
        val hour = try { LocalTime.now().hour } catch (e: Exception) { 9 }
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Formatted date string (e.g. "Monday, 3 August 2026")
    val formattedTodayDate = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
        } catch (e: Exception) {
            "Monday, 3 August 2026"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RoyalBlueDark,
                        Color(0xFF1E3A8A),
                        RoyalBlueDark,
                        Color(0xFF0F172A)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Abstract Background Light Effects (Subtle glowing orbs)
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-50).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentOrange.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-120).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            RoyalBlueLight.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------
            // 1. HEADER SECTION (PREMIUM 3D SHIELD LOGO & GREETING)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    // Dynamic greeting chip with subtle metallic border
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = AccentOrangeLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$timeOfDayGreeting, Agent",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.sp
                                )
                            )
                        }
                    }

                    // 3D Layered Enterprise Shield Logo
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .shadow(16.dp, CircleShape, spotColor = AccentOrange)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        AccentOrange,
                                        AccentOrangeLight,
                                        RoyalBluePrimary
                                    )
                                )
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            RoyalBlueDark,
                                            Color(0xFF0F172A)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color(0xFFEFF6FF)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "LIC CRM 3D Shield Logo",
                                    tint = RoyalBluePrimary,
                                    modifier = Modifier.size(46.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 1.dp, y = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "LIC Agent Daily Work CRM",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 22.sp,
                            letterSpacing = 0.4.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formattedTodayDate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentOrangeLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            letterSpacing = 0.2.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // -------------------------------------------------------------
            // 2. UPGRADED LOGIN CARD (WHITE CARD WITH SOFT ELEVATION)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 100)) + slideInVertically(
                    initialOffsetY = { 60 },
                    animationSpec = tween(600, delayMillis = 100, easing = FastOutSlowInEasing)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(26.dp)
                    ) {
                        Text(
                            text = "Welcome Back",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = NeutralTextPrimaryLight,
                                letterSpacing = (-0.3).sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sign in to manage your LIC business.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = NeutralTextSecondaryLight,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // Error Banner
                        if (authState is AuthState.Error) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                color = ErrorRedContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = (authState as AuthState.Error).message,
                                        color = ErrorRed,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Email / Agent ID Field (Material 3 Outlined Style)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = {
                                Text(
                                    "Email Address / Agent ID",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                            },
                            placeholder = {
                                Text(
                                    "agent@licreminderpro.com",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        color = NeutralTextSecondaryLight.copy(alpha = 0.7f)
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = RoyalBluePrimary
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                color = NeutralTextPrimaryLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = RoyalBluePrimary,
                                unfocusedBorderColor = NeutralBorderLight,
                                focusedLabelColor = RoyalBluePrimary,
                                unfocusedLabelColor = NeutralTextSecondaryLight,
                                focusedPlaceholderColor = NeutralTextSecondaryLight.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = NeutralTextSecondaryLight.copy(alpha = 0.7f),
                                focusedTextColor = NeutralTextPrimaryLight,
                                unfocusedTextColor = NeutralTextPrimaryLight
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    "Password",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                            },
                            placeholder = {
                                Text(
                                    "••••••••",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        color = NeutralTextSecondaryLight.copy(alpha = 0.7f)
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = RoyalBluePrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = NeutralTextSecondaryLight
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboardController?.hide()
                                authViewModel.login(email, password)
                            }),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                color = NeutralTextPrimaryLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = RoyalBluePrimary,
                                unfocusedBorderColor = NeutralBorderLight,
                                focusedLabelColor = RoyalBluePrimary,
                                unfocusedLabelColor = NeutralTextSecondaryLight,
                                focusedPlaceholderColor = NeutralTextSecondaryLight.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = NeutralTextSecondaryLight.copy(alpha = 0.7f),
                                focusedTextColor = NeutralTextPrimaryLight,
                                unfocusedTextColor = NeutralTextPrimaryLight
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Remember Me & Forgot Password Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { rememberMe = !rememberMe }
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RoyalBluePrimary,
                                        uncheckedColor = NeutralTextSecondaryLight
                                    )
                                )
                                Text(
                                    text = "Remember Me",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = NeutralTextPrimaryLight,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }

                            TextButton(
                                onClick = onNavigateToForgotPassword,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBluePrimary,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Large Gradient Sign In Button with Soft Glow
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                authViewModel.login(email, password)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("login_submit_button")
                                .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                RoyalBluePrimary,
                                                RoyalBlueLight
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Sign In",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.5.sp,
                                                letterSpacing = 0.3.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google Sign-In Button
                        OutlinedButton(
                            onClick = {
                                keyboardController?.hide()
                                authViewModel.loginWithGoogleToken("sample_google_token_" + System.currentTimeMillis())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_google_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF4285F4)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google Sign In",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign In with Google",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Guest Mode Button
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                authViewModel.loginAnonymously()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_guest_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonOutline,
                                    contentDescription = "Guest Login",
                                    tint = NeutralTextSecondaryLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Continue as Guest Agent",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = NeutralTextSecondaryLight,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Register Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "New LIC Agent? ",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NeutralTextSecondaryLight,
                                    fontSize = 13.sp
                                )
                            )
                            TextButton(
                                onClick = onNavigateToRegister,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Register Here",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentOrange,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -------------------------------------------------------------
            // 3. FOOTER (SECURITY BADGE & PRIVACY POLICY)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 200))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Secured with 256-bit Enterprise SSL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v2.4.0 • Privacy Policy • Terms of Service",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var agencyCode by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LicNavyPrimary, LicNavySecondary, MaterialTheme.colorScheme.background)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Brand Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LicGoldAccent)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Register Agent",
                    tint = LicNavyPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Register LIC Agent",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Create Cloud Account & Agent CRM Portfolio",
                style = MaterialTheme.typography.bodyMedium.copy(color = LicGoldAccent),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    if (authState is AuthState.Error) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = (authState as AuthState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Agent Full Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address *") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_email_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = agencyCode,
                        onValueChange = { agencyCode = it },
                        label = { Text("Agent ID / Agency Code (e.g. LIC-89421)") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_agency_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Branch Office (e.g. Branch 883 City Center)") },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_branch_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_mobile_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (Min 6 chars) *") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Password Visibility"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            authViewModel.register(name, email, agencyCode, branchName, mobile, password)
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_password_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            authViewModel.register(name, email, agencyCode, branchName, mobile, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("register_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LicNavyPrimary)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                "Create Cloud Account",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = onNavigateToLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Already have an account? Sign In")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val successMsg by authViewModel.forgotPasswordSuccess.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LicNavyPrimary, LicNavySecondary, MaterialTheme.colorScheme.background)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = LicNavyPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Reset Password",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Enter your registered email to receive a password reset link",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (successMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = successMsg!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Registered Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            authViewModel.resetPassword(email)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            authViewModel.resetPassword(email)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LicNavyPrimary)
                    ) {
                        Text("Send Password Reset Link")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = {
                        authViewModel.clearForgotPasswordSuccess()
                        onNavigateToLogin()
                    }) {
                        Text("Back to Sign In")
                    }
                }
            }
        }
    }
}


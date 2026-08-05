package com.example.ui.reminders

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Royal Blue Dark Theme Colors for SendingProgressScreen
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentAmber = Color(0xFFF59E0B)

enum class CustomerSendStatus {
    SENDING,
    SENT,
    FAILED
}

data class CustomerProgressItem(
    val id: Int,
    val name: String,
    val policyNumber: String,
    val mobileNumber: String,
    val status: CustomerSendStatus,
    val avatarInitials: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendingProgressScreen(
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    // Demo data & state for UI demonstration
    var progressStep by remember { mutableIntStateOf(6) } // Default 6 out of 12 (50%)
    val totalCustomers = 12

    // Auto-advance loop for smooth UI demonstration
    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            progressStep = if (progressStep >= totalCustomers) 1 else progressStep + 1
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressStep / totalCustomers.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progressAnimation"
    )

    val currentPercentage = (animatedProgress * 100).toInt()

    val customersList = remember {
        listOf(
            CustomerProgressItem(1, "Rahul Kumar", "847291038", "+91 98765 43210", CustomerSendStatus.SENT, "RK"),
            CustomerProgressItem(2, "Anita Das", "918237465", "+91 98123 45678", CustomerSendStatus.SENT, "AD"),
            CustomerProgressItem(3, "Rajesh Sharma", "736451928", "+91 97654 32109", CustomerSendStatus.SENDING, "RS"),
            CustomerProgressItem(4, "Suresh Patel", "654321987", "+91 96543 21098", CustomerSendStatus.FAILED, "SP"),
            CustomerProgressItem(5, "Priya Verma", "543216879", "+91 95432 10987", CustomerSendStatus.SENT, "PV"),
            CustomerProgressItem(6, "Amit Sahoo", "432156789", "+91 94321 09876", CustomerSendStatus.SENT, "AS"),
            CustomerProgressItem(7, "Meenakshi S.", "321456987", "+91 93210 98765", CustomerSendStatus.SENT, "MS"),
            CustomerProgressItem(8, "Sunil Verma", "214365879", "+91 92109 87654", CustomerSendStatus.SENT, "SV"),
            CustomerProgressItem(9, "Kavita Rao", "123456789", "+91 91098 76543", CustomerSendStatus.SENT, "KR"),
            CustomerProgressItem(10, "Deepak Gupta", "987654321", "+91 90987 65432", CustomerSendStatus.SENT, "DG"),
            CustomerProgressItem(11, "Vikramjit Singh", "876543210", "+91 89876 54321", CustomerSendStatus.SENT, "VS"),
            CustomerProgressItem(12, "Priya Mukherji", "765432109", "+91 88765 43210", CustomerSendStatus.SENT, "PM")
        )
    }

    val currentCustomer = customersList.getOrElse((progressStep - 1).coerceIn(0, customersList.size - 1)) {
        customersList[0]
    }

    // Counts based on progress
    val completedCount = progressStep
    val remainingCount = (totalCustomers - progressStep).coerceAtLeast(0)
    val successCount = (progressStep - 1).coerceAtLeast(0)
    val failedCount = if (progressStep >= 4) 1 else 0

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextWhite,
                    navigationIconContentColor = TextWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Sending Premium Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Please wait...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 9. Bottom Sticky Button: Cancel Sending
            Surface(
                color = DarkBg,
                border = BorderStroke(1.dp, CardBorder),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = onCancelClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cancel Sending",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 3. Center Progress Section: 72dp Circular Progress Indicator & Large progress text (24sp SemiBold)
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        // 72dp Circular Progress Indicator
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(72.dp),
                            color = RoyalBlueLight,
                            strokeWidth = 8.dp,
                            trackColor = CardBorder
                        )

                        // Center percentage text
                        Text(
                            text = "$currentPercentage%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Large Progress Text (24sp SemiBold)
                    AnimatedContent(
                        targetState = progressStep,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "progressTextAnim"
                    ) { targetStep ->
                        Text(
                            text = "$targetStep / $totalCustomers Customers Sent",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Percentages indicator Row: 25% | 50% | 75% | 100%
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(25, 50, 75, 100).forEach { pct ->
                            val isReached = currentPercentage >= pct
                            Surface(
                                color = if (isReached) RoyalBluePrimary.copy(alpha = 0.3f) else DarkBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isReached) RoyalBlueLight else CardBorder)
                            ) {
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isReached) RoyalBlueLight else TextMuted,
                                        fontWeight = if (isReached) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Current Customer Card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "CURRENTLY PROCESSING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = currentCustomer,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) + slideInVertically { it / 2 } togetherWith
                                    fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 2 }
                        },
                        label = "customerCardAnim"
                    ) { customer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                color = RoyalBluePrimary.copy(alpha = 0.25f),
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, RoyalBlueLight.copy(alpha = 0.6f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = customer.avatarInitials,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = RoyalBlueLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Customer Details
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Customer Name (18sp SemiBold)
                                Text(
                                    text = customer.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Policy: ${customer.policyNumber}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextMuted,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                    )
                                    Text(
                                        text = customer.mobileNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextMuted,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Status Badge
                            val (badgeBg, badgeText, badgeIcon) = when (customer.status) {
                                CustomerSendStatus.SENDING -> Triple(
                                    AccentAmber.copy(alpha = 0.15f),
                                    "Sending...",
                                    Icons.Default.HourglassTop
                                )
                                CustomerSendStatus.SENT -> Triple(
                                    AccentGreen.copy(alpha = 0.15f),
                                    "Sent ✓",
                                    Icons.Default.Check
                                )
                                CustomerSendStatus.FAILED -> Triple(
                                    AccentRed.copy(alpha = 0.15f),
                                    "Failed ✕",
                                    Icons.Default.Close
                                )
                            }

                            val badgeColor = when (customer.status) {
                                CustomerSendStatus.SENDING -> AccentAmber
                                CustomerSendStatus.SENT -> AccentGreen
                                CustomerSendStatus.FAILED -> AccentRed
                            }

                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Progress Summary Card & 6. Linear Progress Bar below the card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PROGRESS SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryMetricItem("Total", "$totalCustomers", TextWhite)
                        SummaryMetricItem("Completed", "$completedCount", RoyalBlueLight)
                        SummaryMetricItem("Remaining", "$remainingCount", AccentAmber)
                        SummaryMetricItem("Success", "$successCount", AccentGreen)
                        SummaryMetricItem("Failed", "$failedCount", AccentRed)
                    }

                    HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))

                    // 6. Linear Progress Bar below the card
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Overall Progress",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                            )
                            Text(
                                text = "$currentPercentage%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = RoyalBlueLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = RoyalBlueLight,
                            trackColor = DarkBg
                        )
                    }
                }
            }

            // 7. ETA Card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RoyalBluePrimary.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = "Timer",
                                tint = RoyalBlueLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Estimated Time Remaining:",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                        Text(
                            text = "~${remainingCount * 3} seconds",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }

            // 8. Live Status List
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "LIVE STATUS LIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        customersList.take(6).forEachIndexed { idx, item ->
                            val itemNum = idx + 1
                            val (statusIcon, statusColor, statusLabel) = when {
                                itemNum < progressStep -> Triple(Icons.Default.Check, AccentGreen, "Sent")
                                itemNum == progressStep -> Triple(Icons.Default.HourglassTop, AccentAmber, "Sending...")
                                itemNum == 4 && progressStep >= 4 -> Triple(Icons.Default.Close, AccentRed, "Failed")
                                else -> Triple(Icons.Outlined.Schedule, TextMuted, "Pending")
                            }

                            Surface(
                                color = DarkBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (itemNum <= progressStep) TextWhite else TextMuted,
                                                fontWeight = if (itemNum == progressStep) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SendingProgressScreenPreview() {
    SendingProgressScreen()
}

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.SearchFilterOption

// Primary Large Action Button (Minimum 56dp height, 16-20dp rounded corners)
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    testTag: String = "primary_action_btn"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

// Secondary Action Button (Outlined / Light fill)
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String = "secondary_action_btn"
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
        }
    }
}

// Reusable Production-Quality Search Bar with High-Contrast Dark Theme UI, Glow & Animations
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarComponent(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Search customers, policy no, plan...",
    onFilterClick: (() -> Unit)? = null,
    testTag: String = "search_bar_input",
    selectedFilters: Set<SearchFilterOption> = emptySet(),
    onApplyFilters: ((Set<SearchFilterOption>) -> Unit)? = null,
    onResetFilters: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var localShowBottomSheet by remember { mutableStateOf(false) }

    // Smooth focus / unfocus animations
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF3B82F6) else Color(0xFF334155),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "SearchBarBorderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "SearchBarBorderWidth"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) 10.dp else 2.dp,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "SearchBarShadow"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selectedFilters.isNotEmpty()) Color(0xFF3B82F6) else if (isFocused) Color(0xFF3B82F6) else Color(0xFF94A3B8),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "SearchBarIconTint"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp) // 20dp horizontal padding
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) // 60dp search field height
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(28.dp), // 28dp rounded corners
                    spotColor = Color(0xFF2563EB), // Subtle Royal Blue glow on focus
                    ambientColor = Color(0xFF3B82F6)
                )
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(28.dp)
                ),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E293B) // Dark theme surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Search Icon (24dp)
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) Color(0xFF3B82F6) else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Input & Placeholder Box
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = TextStyle(
                                color = Color(0xFFBFC7D5),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            }
                            .testTag(testTag),
                        textStyle = TextStyle(
                            color = Color(0xFFFFFFFF),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color(0xFF2563EB)),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                innerTextField()
                            }
                        }
                    )
                }

                // Clear (X) icon & Filter icon vertically centered
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (onFilterClick != null) {
                                onFilterClick.invoke()
                            } else {
                                localShowBottomSheet = true
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_filter_icon_button")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            if (selectedFilters.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF3B82F6), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (localShowBottomSheet && onApplyFilters != null) {
        SearchFilterBottomSheet(
            initialFilters = selectedFilters,
            onApply = { filters ->
                onApplyFilters(filters)
                localShowBottomSheet = false
            },
            onReset = {
                onResetFilters?.invoke()
                localShowBottomSheet = false
            },
            onDismiss = {
                localShowBottomSheet = false
            }
        )
    }
}

// Material 3 Search Filter Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBottomSheet(
    initialFilters: Set<SearchFilterOption>,
    onApply: (Set<SearchFilterOption>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedOptions by remember { mutableStateOf(initialFilters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color(0xFFF8FAFC),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF475569))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter Options",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Filter Customer List",
                        style = TextStyle(
                            color = Color(0xFFF8FAFC),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (selectedOptions.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF2563EB).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6))
                    ) {
                        Text(
                            text = "${selectedOptions.size} Selected",
                            color = Color(0xFF3B82F6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterCategorySection(
                    title = "DUE DATE",
                    options = listOf(
                        SearchFilterOption.TODAY_DUE,
                        SearchFilterOption.TOMORROW_DUE,
                        SearchFilterOption.THIS_WEEK,
                        SearchFilterOption.THIS_MONTH,
                        SearchFilterOption.UPCOMING,
                        SearchFilterOption.OVERDUE
                    ),
                    selectedOptions = selectedOptions,
                    onToggleOption = { option ->
                        selectedOptions = if (selectedOptions.contains(option)) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                    }
                )

                FilterCategorySection(
                    title = "PAYMENT STATUS",
                    options = listOf(
                        SearchFilterOption.PAID,
                        SearchFilterOption.UNPAID
                    ),
                    selectedOptions = selectedOptions,
                    onToggleOption = { option ->
                        selectedOptions = if (selectedOptions.contains(option)) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                    }
                )

                FilterCategorySection(
                    title = "PREMIUM MODE",
                    options = listOf(
                        SearchFilterOption.HALF_YEARLY,
                        SearchFilterOption.QUARTERLY,
                        SearchFilterOption.MONTHLY,
                        SearchFilterOption.YEARLY
                    ),
                    selectedOptions = selectedOptions,
                    onToggleOption = { option ->
                        selectedOptions = if (selectedOptions.contains(option)) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedOptions = emptySet()
                        onReset()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("filter_reset_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }

                Button(
                    onClick = {
                        onApply(selectedOptions)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("filter_apply_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Apply Filters",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterCategorySection(
    title: String,
    options: List<SearchFilterOption>,
    selectedOptions: Set<SearchFilterOption>,
    onToggleOption: (SearchFilterOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = TextStyle(
                color = Color(0xFF3B82F6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )

        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { option ->
                    val isChecked = selectedOptions.contains(option)
                    Surface(
                        onClick = { onToggleOption(option) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_option_${option.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChecked) Color(0xFF2563EB).copy(alpha = 0.15f) else Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isChecked) Color(0xFF3B82F6) else Color(0xFF334155)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleOption(option) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF2563EB),
                                    uncheckedColor = Color(0xFF64748B),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                style = TextStyle(
                                    color = if (isChecked) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Animated Counter Text Component for Stat Cards
@Composable
fun AnimatedStatNumber(
    value: String,
    style: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val numericValue = remember(value) {
        value.replace("₹", "").replace(",", "").trim().toDoubleOrNull()
    }

    if (numericValue != null) {
        val isCurrency = value.contains("₹")
        var targetValue by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(numericValue) {
            targetValue = numericValue.toFloat()
        }
        val animatedVal by animateFloatAsState(
            targetValue = targetValue,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "StatNumberAnimation"
        )
        val formattedText = if (isCurrency) {
            "₹${"%.0f".format(animatedVal)}"
        } else {
            "%.0f".format(animatedVal)
        }
        Text(
            text = formattedText,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 2 })
                    .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 2 })
            },
            label = "StatTextAnimation"
        ) { targetText ->
            Text(
                text = targetText,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Stat Card with 18dp rounded corners, subtle shadow, status colors & animated counter typography
@Composable
fun DashboardStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "stat_card"
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .testTag(testTag)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTintColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedStatNumber(
                value = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Status Badge Chip (Active, Lapsed, Paid-Up, Pending, Due Today)
@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "active", "paid" -> Pair(EmeraldGreenContainer, OnEmeraldGreenContainer)
        "due", "due today", "pending", "grace" -> Pair(AccentOrangeContainer, OnAccentOrangeContainer)
        "lapsed", "overdue" -> Pair(ErrorRedContainer, ErrorRed)
        "matured", "paid-up", "paidup" -> Pair(RoyalBlueContainer, OnRoyalBlueContainer)
        else -> Pair(AccentOrangeContainer, OnAccentOrangeContainer)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            )
        )
    }
}

// Customer Avatar Photo Component with Initials Fallback
@Composable
fun CustomerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    size: Dp = 50.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "C" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri.isNotBlank()) {
            coil.compose.AsyncImage(
                model = photoUri,
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = initials.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontSize = (size.value * 0.4).sp
                )
            )
        }
    }
}

// Quick Call & WhatsApp Utility Buttons
@Composable
fun ContactActionRow(
    mobile: String,
    customerName: String = "",
    policyNumber: String = "",
    reminderText: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Phone Call Button
        IconButton(
            onClick = { launchPhoneCall(context, mobile) },
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(RoyalBlueContainer)
                .testTag("action_call_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Call $customerName",
                tint = RoyalBluePrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // WhatsApp Button
        IconButton(
            onClick = { launchWhatsAppMessage(context, mobile, reminderText) },
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(EmeraldGreenContainer)
                .testTag("action_whatsapp_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "WhatsApp $customerName",
                tint = EmeraldGreenSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Section Header with optional action label
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

// Standardized M3 Empty State Component
@Composable
fun StandardEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.FolderOff,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    testTag: String = "standard_empty_state"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (actionLabel != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onActionClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// Standardized M3 Error State Component with Retry
@Composable
fun StandardErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    testTag: String = "standard_error_state"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Standardized M3 Loading Indicator
@Composable
fun StandardLoadingIndicator(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
    testTag: String = "standard_loading_indicator"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

// Standardized Loading Overlay for Async Saving/Syncing/Uploading Operations
@Composable
fun StandardLoadingOverlay(
    isLoading: Boolean,
    message: String = "Processing...",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

// Standardized M3 Input Text Field Component
@Composable
fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    testTag: String = "standard_text_field"
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(testTag),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = trailingIcon,
            isError = isError,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error
            )
        )
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// Quick Helper Intent launcher functions
fun launchPhoneCall(context: Context, phoneNumber: String) {
    if (phoneNumber.isBlank()) {
        Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun launchWhatsAppMessage(context: Context, phoneNumber: String, message: String) {
    com.example.whatsapp.WhatsAppAutomation.sendWhatsAppReminder(
        context = context,
        phoneNumber = phoneNumber,
        message = message
    )
}

fun launchSMS(context: Context, phoneNumber: String, message: String = "") {
    if (phoneNumber.isBlank()) {
        Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            if (message.isNotBlank()) {
                putExtra("sms_body", message)
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to send SMS: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

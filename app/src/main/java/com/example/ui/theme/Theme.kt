package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlueLight,
    onPrimary = Color.White,
    primaryContainer = RoyalBluePrimary,
    onPrimaryContainer = RoyalBlueContainer,
    secondary = EmeraldGreenLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldGreenSecondary,
    onSecondaryContainer = EmeraldGreenContainer,
    tertiary = AccentOrangeLight,
    onTertiary = Color.White,
    tertiaryContainer = AccentOrange,
    onTertiaryContainer = AccentOrangeContainer,
    background = NeutralBgDark,
    onBackground = NeutralTextPrimaryDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralTextPrimaryDark,
    surfaceVariant = NeutralBorderDark,
    onSurfaceVariant = NeutralTextSecondaryDark,
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = ErrorRed,
    onErrorContainer = ErrorRedContainer
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBluePrimary,
    onPrimary = Color.White,
    primaryContainer = RoyalBlueContainer,
    onPrimaryContainer = OnRoyalBlueContainer,
    secondary = EmeraldGreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldGreenContainer,
    onSecondaryContainer = OnEmeraldGreenContainer,
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = AccentOrangeContainer,
    onTertiaryContainer = OnAccentOrangeContainer,
    background = NeutralBgLight,
    onBackground = NeutralTextPrimaryLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralTextPrimaryLight,
    surfaceVariant = NeutralBorderLight,
    onSurfaceVariant = NeutralTextSecondaryLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = ErrorRed
)

@Composable
fun LICReminderProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent LIC branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

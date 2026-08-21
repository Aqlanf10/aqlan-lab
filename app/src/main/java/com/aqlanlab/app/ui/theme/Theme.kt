package com.aqlanlab.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = PolishPrimaryDark,
  onPrimary = PolishOnPrimaryDark,
  primaryContainer = PolishPrimaryContainerDark,
  onPrimaryContainer = PolishOnPrimaryContainerDark,
  secondary = PolishSecondaryDark,
  onSecondary = PolishOnSecondaryDark,
  secondaryContainer = PolishSecondaryContainerDark,
  onSecondaryContainer = PolishOnSecondaryContainerDark,
  tertiary = PolishTertiaryDark,
  onTertiary = PolishOnTertiaryDark,
  tertiaryContainer = PolishTertiaryContainerDark,
  onTertiaryContainer = PolishOnTertiaryContainerDark,
  background = PolishBackgroundDark,
  surface = PolishSurfaceDark,
  surfaceVariant = PolishSurfaceVariantDark,
  outline = PolishOutlineDark,
  outlineVariant = PolishOutlineDark.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
  primary = PolishPrimary,
  onPrimary = PolishOnPrimary,
  primaryContainer = PolishPrimaryContainer,
  onPrimaryContainer = PolishOnPrimaryContainer,
  secondary = PolishSecondary,
  onSecondary = PolishOnSecondary,
  secondaryContainer = PolishSecondaryContainer,
  onSecondaryContainer = PolishOnSecondaryContainer,
  tertiary = PolishTertiary,
  onTertiary = PolishOnTertiary,
  tertiaryContainer = PolishTertiaryContainer,
  onTertiaryContainer = PolishOnTertiaryContainer,
  background = PolishBackgroundLight,
  surface = PolishSurfaceLight,
  surfaceVariant = PolishSurfaceVariantLight,
  outline = PolishOutlineLight,
  outlineVariant = PolishOutlineVariantLight
)

// Backward compatibility alias
val DentalPrimary = PolishPrimary

@Composable
fun DentalLabTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
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

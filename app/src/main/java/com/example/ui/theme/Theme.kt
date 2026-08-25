package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val title: String, val description: String) {
    SYSTEM("System Default", "Follows device system settings"),
    LIGHT("Light Theme", "Crisp light background with ocean cobalt accents"),
    DARK("Dark Theme", "Deep dark canvas easy on the eyes")
}

private val DarkColorScheme = darkColorScheme(
  primary = CleanPrimaryDark,
  onPrimary = CleanOnPrimaryDark,
  primaryContainer = CleanPrimaryContainerDark,
  onPrimaryContainer = CleanOnPrimaryContainerDark,
  secondary = CleanSecondaryDark,
  onSecondary = CleanOnSecondaryDark,
  secondaryContainer = CleanSecondaryContainerDark,
  onSecondaryContainer = CleanOnSecondaryContainerDark,
  tertiary = CleanTertiaryDark,
  onTertiary = CleanOnTertiaryDark,
  tertiaryContainer = CleanTertiaryContainerDark,
  onTertiaryContainer = CleanOnTertiaryContainerDark,
  background = CleanBgDark,
  onBackground = CleanOnSurfaceDark,
  surface = CleanSurfaceDark,
  onSurface = CleanOnSurfaceDark,
  surfaceVariant = CleanSurfaceVariantDark,
  onSurfaceVariant = CleanOnSurfaceVariantDark,
  outline = CleanOutlineDark,
  outlineVariant = CleanOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
  primary = CleanPrimaryLight,
  onPrimary = CleanOnPrimaryLight,
  primaryContainer = CleanPrimaryContainerLight,
  onPrimaryContainer = CleanOnPrimaryContainerLight,
  secondary = CleanSecondaryLight,
  onSecondary = CleanOnSecondaryLight,
  secondaryContainer = CleanSecondaryContainerLight,
  onSecondaryContainer = CleanOnSecondaryContainerLight,
  tertiary = CleanTertiaryLight,
  onTertiary = CleanOnTertiaryLight,
  tertiaryContainer = CleanTertiaryContainerLight,
  onTertiaryContainer = CleanOnTertiaryContainerLight,
  background = CleanBgLight,
  onBackground = CleanOnSurfaceLight,
  surface = CleanSurfaceLight,
  onSurface = CleanOnSurfaceLight,
  surfaceVariant = CleanSurfaceVariantLight,
  onSurfaceVariant = CleanOnSurfaceVariantLight,
  outline = CleanOutlineLight,
  outlineVariant = CleanOutlineVariantLight
)

@Composable
fun MyApplicationTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val darkTheme = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }

  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}


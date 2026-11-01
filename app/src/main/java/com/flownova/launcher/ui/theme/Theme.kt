package com.flownova.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.flownova.launcher.model.LauncherSettings
import com.flownova.launcher.model.ThemeMode

@Composable
fun FlowNovaTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val primaryPreset = Color(settings.palettePreset.primaryHex)
    val containerPreset = Color(settings.palettePreset.containerHex)

    val colorScheme = when {
        settings.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> darkColorScheme(
            primary = primaryPreset,
            secondaryContainer = containerPreset,
            onSecondaryContainer = primaryPreset,
            background = Color(0xFF0A0E17),
            surface = Color(0xFF121826),
            surfaceVariant = Color(0xFF1E2638)
        )
        else -> lightColorScheme(
            primary = containerPreset,
            secondaryContainer = primaryPreset.copy(alpha = 0.25f),
            onSecondaryContainer = containerPreset
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

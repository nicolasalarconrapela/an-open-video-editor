package io.github.devhyper.openvideoeditor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore

// We prioritize Dark Theme for the "Ultra-Minimalist 2026" look
private val DarkColorScheme = darkColorScheme(
    primary = Primary2026,
    secondary = Secondary2026,
    tertiary = Tertiary2026,
    background = Background2026,
    surface = Surface2026,
    onPrimary = OnPrimary2026,
    onSecondary = OnPrimary2026,
    onTertiary = OnPrimary2026,
    onBackground = OnBackground2026,
    onSurface = OnSurface2026,
    surfaceVariant = GlassDark, // Use for cards/glass
)

// Light theme is also high-contrast minimalist, but inverted
private val LightColorScheme = lightColorScheme(
    primary = Primary2026,
    secondary = Secondary2026,
    tertiary = Tertiary2026,
    background = White,
    surface = Color(0xFFF5F5F5),
    onPrimary = AbsoluteBlack,
    onSecondary = AbsoluteBlack,
    onTertiary = AbsoluteBlack,
    onBackground = AbsoluteBlack,
    onSurface = AbsoluteBlack
)

@Composable
fun OpenVideoEditorTheme(
    forceDarkTheme: Boolean = true, // Defaulting to Dark for the intended 2026 vibe
    forceBlackStatusBar: Boolean = false,
    dynamicColor: Boolean = false, // Disable dynamic color to enforce the branding
    content: @Composable () -> Unit
) {
    val dataStore = SettingsDataStore(LocalContext.current)
    val theme by dataStore.getThemeAsync().collectAsState(dataStore.getThemeBlocking())

    // Logic to determine dark theme preference
    val darkTheme = if (forceDarkTheme) {
        true
    } else {
        when (theme) {
            "Light" -> false
            "Dark" -> true
            else -> isSystemInDarkTheme()
        }
    }

    // Select color scheme
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make status bar transparent for edge-to-edge content
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            // Adjust icons visibility based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

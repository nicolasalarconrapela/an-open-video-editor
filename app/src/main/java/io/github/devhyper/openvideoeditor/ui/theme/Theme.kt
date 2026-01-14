package io.github.devhyper.openvideoeditor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AbsoluteBlack,
    surface = AbsoluteBlack,
    onPrimary = Color.White,
    onBackground = TextWhite,
    onSurface = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue, // Keep branding even in light (though we will force dark)
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun OpenVideoEditorTheme(
    forceDarkTheme: Boolean = true, // Default to true for 2026 aesthetic
    forceBlackStatusBar: Boolean = true, // Default to true
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color to enforce our palette
    content: @Composable () -> Unit
) {
    val dataStore = SettingsDataStore(LocalContext.current)
    // We override user prefs for now to enforce the new design,
    // or we can treat them as "soft" preferences.
    // Given the request "revise el diseño... 2026", we prioritize the new look.
    val theme by dataStore.getThemeAsync().collectAsState(dataStore.getThemeBlocking())
    val amoled by dataStore.getAmoledAsync().collectAsState(dataStore.getAmoledBlocking())

    // Enforce dark theme for the 2026 look
    val darkTheme = true

    val colorScheme = DarkColorScheme.copy(
        background = AbsoluteBlack,
        surface = AbsoluteBlack
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AbsoluteBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

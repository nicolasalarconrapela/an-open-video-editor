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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
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

val LocalUiCascadingEffect = staticCompositionLocalOf { false }

@Composable
fun OpenVideoEditorTheme(
    forceDarkTheme: Boolean = false,
    forceBlackStatusBar: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dataStore = SettingsDataStore(LocalContext.current)
    val theme by dataStore.getThemeAsync().collectAsState(dataStore.getThemeBlocking())
    val amoled by dataStore.getAmoledAsync().collectAsState(dataStore.getAmoledBlocking())
    val uiCascadingEffect by dataStore.getUiCascadingEffectAsync()
        .collectAsState(dataStore.getUiCascadingEffectBlocking())

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (theme) {
        "Light" -> false
        "Dark" -> true
        else -> systemDark
    } || forceDarkTheme

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(LocalContext.current)
            } else {
                dynamicLightColorScheme(LocalContext.current)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme && amoled) {
        baseScheme.copy(
            background = AbsoluteBlack,
            surface = AbsoluteBlack
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarColor = if (forceBlackStatusBar && darkTheme && amoled) {
                AbsoluteBlack
            } else {
                colorScheme.background
            }
            window.statusBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalUiCascadingEffect provides uiCascadingEffect) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

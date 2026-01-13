package io.github.devhyper.openvideoeditor.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.misc.DropdownSetting
import io.github.devhyper.openvideoeditor.misc.SwitchSetting
import io.github.devhyper.openvideoeditor.misc.move
import io.github.devhyper.openvideoeditor.ui.theme.GlassDark
import io.github.devhyper.openvideoeditor.ui.theme.GlassWhite
import io.github.devhyper.openvideoeditor.ui.theme.OpenVideoEditorTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context) }
    OpenVideoEditorTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                // Custom Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { activity.finish() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassWhite)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme Setting
                    item {
                        val theme = dataStore.getThemeBlocking()
                        val options = mutableListOf("System", "Light", "Dark")
                        options.move(theme, 0)

                        SettingsCard(title = stringResource(R.string.theme)) {
                            DropdownSetting(
                                name = "", // Hide label inside
                                options = options.toImmutableList(),
                                onSelectionChanged = {
                                    if (it != theme) {
                                        scope.launch { dataStore.setTheme(it) }
                                    }
                                }
                            )
                        }
                    }

                    // Switches
                    item {
                        val useLegacyFilePicker = dataStore.getLegacyFilePickerBlocking()
                        SettingsCard {
                            SwitchSetting(
                                name = stringResource(R.string.use_legacy_file_picker),
                                startChecked = useLegacyFilePicker,
                                onCheckChanged = {
                                    if (it != useLegacyFilePicker) {
                                        scope.launch { dataStore.setLegacyFilePicker(it) }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        val useUiCascadingEffect = dataStore.getUiCascadingEffectBlocking()
                        SettingsCard {
                            SwitchSetting(
                                name = stringResource(R.string.use_ui_cascading_effect),
                                startChecked = useUiCascadingEffect,
                                onCheckChanged = {
                                    if (it != useUiCascadingEffect) {
                                        scope.launch { dataStore.setUiCascadingEffect(it) }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        val useAmoled = dataStore.getAmoledBlocking()
                        SettingsCard {
                            SwitchSetting(
                                name = stringResource(R.string.amoled_dark_theme),
                                startChecked = useAmoled,
                                onCheckChanged = {
                                    if (it != useAmoled) {
                                        scope.launch { dataStore.setAmoled(it) }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        val proxyEnabled = dataStore.getProxyEnabledBlocking()
                        SettingsCard {
                            SwitchSetting(
                                name = stringResource(R.string.use_proxy_media),
                                startChecked = proxyEnabled,
                                onCheckChanged = {
                                    if (it != proxyEnabled) {
                                        scope.launch { dataStore.setProxyEnabled(it) }
                                    }
                                }
                            )
                        }
                    }

                    // Proxy Quality
                    item {
                        val proxyQuality = dataStore.getProxyQualityBlocking()
                        val proxyOptions = listOf(
                            ProxyQualityOption("low", stringResource(R.string.proxy_quality_low)),
                            ProxyQualityOption("medium", stringResource(R.string.proxy_quality_medium)),
                            ProxyQualityOption("high", stringResource(R.string.proxy_quality_high))
                        )
                        val selectedOption = proxyOptions.firstOrNull { it.key == proxyQuality } ?: proxyOptions[1]
                        val options = proxyOptions.map { it.label }.toMutableList()
                        options.move(selectedOption.label, 0)

                        SettingsCard(title = stringResource(R.string.proxy_quality)) {
                            DropdownSetting(
                                name = "",
                                options = options.toImmutableList(),
                                onSelectionChanged = { label ->
                                    val selectedKey = proxyOptions.first { it.label == label }.key
                                    if (selectedKey != proxyQuality) {
                                        scope.launch { dataStore.setProxyQuality(selectedKey) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = GlassDark,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            content()
        }
    }
}

private data class ProxyQualityOption(
    val key: String,
    val label: String,
)

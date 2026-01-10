package io.github.devhyper.openvideoeditor.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.floatPreferencesKey

class SettingsDataStore(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val THEME = stringPreferencesKey("theme")
        val LEGACY_FILE_PICKER = booleanPreferencesKey("legacy_file_picker")
        val UI_CASCADING_EFFECT = booleanPreferencesKey("ui_cascading_effect")
        val AMOLED_DARK_THEME = booleanPreferencesKey("amoled_dark_theme")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_QUALITY = stringPreferencesKey("proxy_quality")
        val EXPORT_STATE = stringPreferencesKey("export_state")
        val EXPORT_PROGRESS = floatPreferencesKey("export_progress")
        val EXPORT_OUTPUT_PATH = stringPreferencesKey("export_output_path")
        val EXPORT_WORK_ID = stringPreferencesKey("export_work_id")
        val EXPORT_ERROR = stringPreferencesKey("export_error")
    }

    fun getThemeBlocking(): String {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[THEME] ?: "System"
        }
    }

    fun getThemeAsync(): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[THEME] ?: "System"
            }
    }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME] = value
        }
    }

    fun getAmoledBlocking(): Boolean {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[AMOLED_DARK_THEME] ?: false
        }
    }

    fun getAmoledAsync(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[AMOLED_DARK_THEME] ?: false
            }
    }

    suspend fun setAmoled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AMOLED_DARK_THEME] = value
        }
    }

    fun getLegacyFilePickerBlocking(): Boolean {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[LEGACY_FILE_PICKER] ?: false
        }
    }

    suspend fun setLegacyFilePicker(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LEGACY_FILE_PICKER] = value
        }
    }

    fun getUiCascadingEffectBlocking(): Boolean {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[UI_CASCADING_EFFECT] ?: false
        }
    }

    fun getUiCascadingEffectAsync(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[UI_CASCADING_EFFECT] ?: false
            }
    }

    suspend fun setUiCascadingEffect(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UI_CASCADING_EFFECT] = value
        }
    }

    fun getProxyEnabledBlocking(): Boolean {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[PROXY_ENABLED] ?: false
        }
    }

    suspend fun setProxyEnabled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PROXY_ENABLED] = value
        }
    }

    fun getProxyQualityBlocking(): String {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            preferences[PROXY_QUALITY] ?: "medium"
        }
    }

    suspend fun setProxyQuality(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PROXY_QUALITY] = value
        }
    }

    fun getExportStateAsync(): Flow<io.github.devhyper.openvideoeditor.videoeditor.ExportState> {
        return context.dataStore.data.map { preferences ->
            io.github.devhyper.openvideoeditor.videoeditor.ExportState.fromValue(preferences[EXPORT_STATE])
        }
    }

    fun getExportStateBlocking(): io.github.devhyper.openvideoeditor.videoeditor.ExportState {
        return runBlocking {
            val preferences = context.dataStore.data.first()
            io.github.devhyper.openvideoeditor.videoeditor.ExportState.fromValue(preferences[EXPORT_STATE])
        }
    }

    fun getExportProgressAsync(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[EXPORT_PROGRESS] ?: 0F
        }
    }

    fun getExportOutputPathAsync(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[EXPORT_OUTPUT_PATH] ?: ""
        }
    }

    fun getExportErrorAsync(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[EXPORT_ERROR]
        }
    }

    suspend fun setExportState(state: io.github.devhyper.openvideoeditor.videoeditor.ExportState) {
        context.dataStore.edit { preferences ->
            preferences[EXPORT_STATE] = state.value
        }
    }

    suspend fun setExportProgress(progress: Float) {
        context.dataStore.edit { preferences ->
            preferences[EXPORT_PROGRESS] = progress
        }
    }

    suspend fun setExportOutputPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[EXPORT_OUTPUT_PATH] = path
        }
    }

    suspend fun setExportWorkId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) {
                preferences.remove(EXPORT_WORK_ID)
            } else {
                preferences[EXPORT_WORK_ID] = id
            }
        }
    }

    suspend fun setExportError(error: String?) {
        context.dataStore.edit { preferences ->
            if (error == null) {
                preferences.remove(EXPORT_ERROR)
            } else {
                preferences[EXPORT_ERROR] = error
            }
        }
    }

    suspend fun clearExportState() {
        context.dataStore.edit { preferences ->
            preferences[EXPORT_STATE] = io.github.devhyper.openvideoeditor.videoeditor.ExportState.IDLE.value
            preferences[EXPORT_PROGRESS] = 0F
            preferences.remove(EXPORT_OUTPUT_PATH)
            preferences.remove(EXPORT_WORK_ID)
            preferences.remove(EXPORT_ERROR)
        }
    }

}

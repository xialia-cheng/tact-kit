package com.xialia.tactkit.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.tactKitDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "tactkit_settings")

enum class ThemeMode(val label: String) {
  SYSTEM("跟随系统"),
  LIGHT("浅色模式"),
  DARK("深色模式"),
}

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val dynamicColor: Boolean = true,
  val liquidGlassPowerSave: Boolean = false,
  val pinnedToolIds: List<String> = DEFAULT_PINNED_TOOL_IDS,
) {
  companion object {
    const val MAX_PINNED_TOOLS = 4
    val DEFAULT_PINNED_TOOL_IDS =
      listOf("quick_note", "quick_checklist", "quick_look", "pdf_toolkit")
  }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

  constructor(context: Context) : this(context.tactKitDataStore)

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val LIQUID_GLASS_POWER_SAVE = booleanPreferencesKey("liquid_glass_power_save")
    val PINNED_TOOL_IDS = stringPreferencesKey("pinned_tool_ids")
  }

  val preferencesFlow: Flow<UserPreferences> =
    dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode =
          ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
        val dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true
        val powerSave = prefs[Keys.LIQUID_GLASS_POWER_SAVE] ?: false
        val rawPinned = prefs[Keys.PINNED_TOOL_IDS]
        val pinnedIds =
          if (rawPinned == null) {
            UserPreferences.DEFAULT_PINNED_TOOL_IDS
          } else if (rawPinned.isBlank()) {
            emptyList()
          } else {
            rawPinned
              .split(",")
              .map { it.trim() }
              .filter { it.isNotEmpty() }
              .distinct()
              .take(UserPreferences.MAX_PINNED_TOOLS)
          }

        UserPreferences(
          themeMode = themeMode,
          dynamicColor = dynamicColor,
          liquidGlassPowerSave = powerSave,
          pinnedToolIds = pinnedIds,
        )
      }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setDynamicColor(enabled: Boolean) {
    dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
  }

  suspend fun setLiquidGlassPowerSave(enabled: Boolean) {
    dataStore.edit { it[Keys.LIQUID_GLASS_POWER_SAVE] = enabled }
  }

  suspend fun togglePinnedTool(toolId: String) {
    dataStore.edit { prefs ->
      val currentRaw = prefs[Keys.PINNED_TOOL_IDS]
      val current =
        if (currentRaw == null) {
          UserPreferences.DEFAULT_PINNED_TOOL_IDS.toMutableList()
        } else {
          currentRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        }

      if (toolId in current) {
        current.remove(toolId)
      } else {
        if (current.size >= UserPreferences.MAX_PINNED_TOOLS) {
          current.removeAt(0)
        }
        current.add(toolId)
      }
      prefs[Keys.PINNED_TOOL_IDS] = current.joinToString(",")
    }
  }
}

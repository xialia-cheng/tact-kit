package com.xialia.tactkit.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xialia.tactkit.security.AndroidKeyStoreCipher
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
  val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
  val aiModel: String = DEFAULT_AI_MODEL,
  val hasAiApiKey: Boolean = false,
) {
  companion object {
    const val MAX_PINNED_TOOLS = 4
    const val DEFAULT_AI_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_AI_MODEL = "gpt-4o-mini"
    val DEFAULT_PINNED_TOOL_IDS =
      listOf("pdf_toolkit", "image_privacy", "ai_hub", "quick_note")
  }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

  constructor(context: Context) : this(context.tactKitDataStore)

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val LIQUID_GLASS_POWER_SAVE = booleanPreferencesKey("liquid_glass_power_save")
    val PINNED_TOOL_IDS = stringPreferencesKey("pinned_tool_ids")
    val AI_BASE_URL = stringPreferencesKey("ai_base_url")
    val AI_MODEL = stringPreferencesKey("ai_model")
    val AI_API_KEY_ENC = stringPreferencesKey("ai_api_key_encrypted")
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
          aiBaseUrl =
            prefs[Keys.AI_BASE_URL]?.takeIf { it.isNotBlank() }
              ?: UserPreferences.DEFAULT_AI_BASE_URL,
          aiModel =
            prefs[Keys.AI_MODEL]?.takeIf { it.isNotBlank() }
              ?: UserPreferences.DEFAULT_AI_MODEL,
          hasAiApiKey = !prefs[Keys.AI_API_KEY_ENC].isNullOrBlank(),
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

  suspend fun saveAiSettings(baseUrl: String, model: String, plainApiKey: String?) {
    val cleanUrl = baseUrl.trim().ifEmpty { UserPreferences.DEFAULT_AI_BASE_URL }
    val cleanModel = model.trim().ifEmpty { UserPreferences.DEFAULT_AI_MODEL }
    val encryptedKey =
      if (!plainApiKey.isNullOrBlank()) {
        AndroidKeyStoreCipher.encrypt(plainApiKey.trim())
      } else {
        null
      }

    dataStore.edit { prefs ->
      prefs[Keys.AI_BASE_URL] = cleanUrl
      prefs[Keys.AI_MODEL] = cleanModel
      if (encryptedKey != null) {
        prefs[Keys.AI_API_KEY_ENC] = encryptedKey
      }
    }
  }

  suspend fun clearAiApiKey() {
    dataStore.edit { prefs -> prefs.remove(Keys.AI_API_KEY_ENC) }
  }

  suspend fun getDecryptedAiApiKey(): String {
    val encrypted = dataStore.data.first()[Keys.AI_API_KEY_ENC].orEmpty()
    if (encrypted.isBlank()) return ""
    return AndroidKeyStoreCipher.decrypt(encrypted)
  }
}

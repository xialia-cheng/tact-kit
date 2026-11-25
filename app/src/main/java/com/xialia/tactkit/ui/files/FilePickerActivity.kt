package com.xialia.tactkit.ui.files

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.ui.theme.TactKitTheme
import com.xialia.tactkit.ui.theme.isTactKitDarkTheme
import kotlinx.coroutines.launch

class FilePickerActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true

    setContent {
      val settingsRepo = remember { SettingsRepository(applicationContext) }
      val preferences by settingsRepo.preferencesFlow.collectAsStateWithLifecycle(UserPreferences())
      val isDark = isTactKitDarkTheme(preferences.themeMode)
      val scope = rememberCoroutineScope()

      LaunchedEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle =
            if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
          navigationBarStyle =
            if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
      }

      TactKitTheme(
        themeMode = preferences.themeMode,
        dynamicColor = preferences.dynamicColor,
      ) {
        FileManagerWorkspace(
          preferences = preferences,
          isPickerMode = true,
          allowMultiplePick = allowMultiple,
          onTogglePinnedDir = { path ->
            scope.launch { settingsRepo.togglePinnedDir(path) }
          },
          onAddLinkedSafTreeUri = { uriStr ->
            scope.launch { settingsRepo.addLinkedSafTreeUri(uriStr) }
          },
          onLaunchTool = { _, _ -> },
          onConfirmPick = { entries -> returnPickedEntries(entries, allowMultiple) },
          onNavigateBack = {
            setResult(RESULT_CANCELED)
            finish()
          },
        )
      }
    }
  }

  private fun returnPickedEntries(entries: List<ManagedFileEntry>, allowMultiple: Boolean) {
    val resolved: List<Pair<ManagedFileEntry, Uri>> =
      entries.mapNotNull { entry ->
        val uri = FileManagerEngine.toShareableUri(this, entry) ?: return@mapNotNull null
        entry to uri
      }
    if (resolved.isEmpty()) {
      Toast.makeText(this, "未能生成可共享的文件 URI", Toast.LENGTH_SHORT).show()
      return
    }

    val resultIntent = Intent().apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    if (resolved.size == 1 && !allowMultiple) {
      val (entry, uri) = resolved.first()
      resultIntent.data = uri
      resultIntent.clipData = ClipData.newRawUri(entry.name, uri)
    } else if (resolved.size == 1) {
      val (entry, uri) = resolved.first()
      resultIntent.data = uri
      resultIntent.clipData = ClipData.newRawUri(entry.name, uri)
    } else {
      val clip = ClipData.newRawUri(resolved.first().first.name, resolved.first().second)
      resolved.drop(1).forEach { (_, uri) ->
        clip.addItem(ClipData.Item(uri))
      }
      resultIntent.clipData = clip
      resultIntent.data = resolved.first().second
    }

    setResult(RESULT_OK, resultIntent)
    finish()
  }
}

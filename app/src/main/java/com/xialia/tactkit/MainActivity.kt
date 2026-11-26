package com.xialia.tactkit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.navigation.TactKitNavHost
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.TactKitTheme
import com.xialia.tactkit.ui.theme.isTactKitDarkTheme

/**
 * Allows opt-in screens (such as the Persistent Counter) to capture hardware volume +/- keys
 * without altering normal audio volume behavior on any other screen.
 */
object HardwareVolumeKeyDispatcher {
  var onVolumeKey: ((delta: Int) -> Unit)? = null
}

class MainActivity : ComponentActivity() {

  private var pendingWidgetToolId by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingWidgetToolId = intent?.getStringExtra(EXTRA_OPEN_TOOL_ID)
    setContent {
      val homeViewModel: HomeViewModel = viewModel()
      val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
      val isDarkTheme = isTactKitDarkTheme(uiState.preferences.themeMode)
      var crashLogText by remember { mutableStateOf(TactKitApplication.readLastCrash(this@MainActivity)) }

      DisposableEffect(isDarkTheme) {
        enableEdgeToEdge(
          statusBarStyle =
            if (isDarkTheme) {
              SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
              SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
          navigationBarStyle =
            if (isDarkTheme) {
              SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
              SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        onDispose {}
      }

      TactKitTheme(
        themeMode = uiState.preferences.themeMode,
        dynamicColor = uiState.preferences.dynamicColor,
        appThemeStyle = uiState.preferences.appThemeStyle,
      ) {
        TactKitNavHost(
          uiState = uiState,
          viewModel = homeViewModel,
          pendingToolId = pendingWidgetToolId,
          onConsumedPendingTool = { pendingWidgetToolId = null },
        )

        crashLogText?.let { log ->
          AlertDialog(
            onDismissRequest = {},
            title = { Text("检测到上次异常退出") },
            text = {
              Text(
                text = log,
                fontFamily = TactKitMono,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
              )
            },
            confirmButton = {
              Button(
                onClick = {
                  val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                  cb?.setPrimaryClip(ClipData.newPlainText("TactKit Crash", log))
                  Toast.makeText(this@MainActivity, "已复制崩溃日志", Toast.LENGTH_SHORT).show()
                },
              ) {
                Text("一键复制")
              }
            },
            dismissButton = {
              TextButton(
                onClick = {
                  TactKitApplication.clearLastCrash(this@MainActivity)
                  crashLogText = null
                },
              ) {
                Text("清除")
              }
            },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingWidgetToolId = intent.getStringExtra(EXTRA_OPEN_TOOL_ID)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val handler = HardwareVolumeKeyDispatcher.onVolumeKey
    if (handler != null) {
      when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
          handler(1)
          return true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
          handler(-1)
          return true
        }
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  companion object {
    const val EXTRA_OPEN_TOOL_ID = "extra_open_tool_id"
  }
}

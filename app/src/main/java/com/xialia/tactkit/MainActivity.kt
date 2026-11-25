package com.xialia.tactkit

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.navigation.TactKitNavHost
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
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val homeViewModel: HomeViewModel = viewModel()
      val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
      val isDarkTheme = isTactKitDarkTheme(uiState.preferences.themeMode)

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
      ) {
        TactKitNavHost(uiState = uiState, viewModel = homeViewModel)
      }
    }
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
}

package com.xialia.tactkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.navigation.TactKitNavHost
import com.xialia.tactkit.ui.theme.TactKitTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val homeViewModel: HomeViewModel = viewModel()
      val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

      TactKitTheme(
        themeMode = uiState.preferences.themeMode,
        dynamicColor = uiState.preferences.dynamicColor,
      ) {
        TactKitNavHost(uiState = uiState, viewModel = homeViewModel)
      }
    }
  }
}

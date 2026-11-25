package com.xialia.tactkit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xialia.tactkit.ui.home.HomeScreen
import com.xialia.tactkit.ui.home.HomeUiState
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SettingsRoute

@Composable
fun TactKitNavHost(
  uiState: HomeUiState,
  viewModel: HomeViewModel,
) {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
  ) {
    composable<HomeRoute> {
      HomeScreen(
        uiState = uiState,
        onSearchQueryChange = viewModel::setSearchQuery,
        onFilterChange = viewModel::setActiveFilter,
        onCreateChecklist = viewModel::createChecklist,
        onToggleChecklistItem = viewModel::toggleChecklistItem,
        onAddChecklistItem = viewModel::addChecklistItem,
        onDeleteChecklistItem = viewModel::deleteChecklistItem,
        onCreateNote = viewModel::createNote,
        onUpdateNote = viewModel::updateNote,
        onTogglePinCard = viewModel::togglePinCard,
        onDeleteCard = viewModel::deleteCard,
        onImportLocalFile = viewModel::importLocalFile,
        onTogglePinTool = viewModel::togglePinnedTool,
        onClearStatusMessage = viewModel::clearStatusMessage,
        onNavigateToSettings = { navController.navigate(SettingsRoute) },
      )
    }

    composable<SettingsRoute> {
      SettingsScreen(
        preferences = uiState.preferences,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onLiquidGlassPowerSaveChange = viewModel::setLiquidGlassPowerSave,
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}

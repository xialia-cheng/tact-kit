package com.xialia.tactkit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.xialia.tactkit.ui.ai.AiHubScreen
import com.xialia.tactkit.ui.archive.ArchiveWorkshopScreen
import com.xialia.tactkit.ui.daily.DailyToolsScreen
import com.xialia.tactkit.ui.daily.QrStudioScreen
import com.xialia.tactkit.ui.data.DataWorkshopScreen
import com.xialia.tactkit.ui.dev.CryptoVaultScreen
import com.xialia.tactkit.ui.dev.DevToolsScreen
import com.xialia.tactkit.ui.dev.ElectronicsScreen
import com.xialia.tactkit.ui.dev.HashWorkshopScreen
import com.xialia.tactkit.ui.dev.LanShareScreen
import com.xialia.tactkit.ui.home.HomeScreen
import com.xialia.tactkit.ui.home.HomeUiState
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.image.ImageToolkitScreen
import com.xialia.tactkit.ui.media.MediaWorkshopScreen
import com.xialia.tactkit.ui.office.DocxEditorScreen
import com.xialia.tactkit.ui.pdf.PdfAnnotateScreen
import com.xialia.tactkit.ui.pdf.PdfToolkitScreen
import com.xialia.tactkit.ui.sensor.CompassLevelScreen
import com.xialia.tactkit.ui.sensor.MetalDetectorScreen
import com.xialia.tactkit.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SettingsRoute

@Serializable data class PdfToolkitRoute(val initialUri: String? = null)

@Serializable data class PdfAnnotateRoute(val initialUri: String? = null)

@Serializable data class ImageToolkitRoute(val initialUri: String? = null, val initialTab: String? = null)

@Serializable data class MediaWorkshopRoute(val initialUri: String? = null)

@Serializable data class AiHubRoute(val initialMode: String = "VISION", val initialImageUri: String? = null)

@Serializable data class DocxEditorRoute(val initialUri: String? = null)

@Serializable data object DataWorkshopRoute

@Serializable data class ArchiveWorkshopRoute(val initialUri: String? = null)

@Serializable data object HashWorkshopRoute

@Serializable data object CryptoVaultRoute

@Serializable data object DevToolsRoute

@Serializable data object ElectronicsRoute

@Serializable data object LanShareRoute

@Serializable data object CompassLevelRoute

@Serializable data object MetalDetectorRoute

@Serializable data class DailyToolsRoute(val initialTab: String? = null)

@Serializable data object QrStudioRoute

@Composable
fun TactKitNavHost(
  uiState: HomeUiState,
  viewModel: HomeViewModel,
) {
  val navController = rememberNavController()
  val audioPlaybackState by viewModel.audioPlaybackState.collectAsStateWithLifecycle()

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    enterTransition = {
      slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(260)) +
        fadeIn(animationSpec = tween(220))
    },
    exitTransition = {
      fadeOut(animationSpec = tween(180))
    },
    popEnterTransition = {
      scaleIn(initialScale = 0.92f, animationSpec = tween(260)) +
        fadeIn(animationSpec = tween(220))
    },
    popExitTransition = {
      slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) +
        fadeOut(animationSpec = tween(200))
    },
  ) {
    composable<HomeRoute> {
      HomeScreen(
        uiState = uiState,
        audioPlaybackState = audioPlaybackState,
        voiceMemoManager = viewModel.voiceMemoManager,
        onSearchQueryChange = viewModel::setSearchQuery,
        onFilterChange = viewModel::setActiveFilter,
        onCreateChecklist = viewModel::createChecklist,
        onToggleChecklistItem = viewModel::toggleChecklistItem,
        onAddChecklistItem = viewModel::addChecklistItem,
        onDeleteChecklistItem = viewModel::deleteChecklistItem,
        onCreateNote = viewModel::createNote,
        onUpdateNote = viewModel::updateNote,
        onCreateVoiceMemo = viewModel::createVoiceMemo,
        onToggleVoiceMemoPlay = viewModel::toggleVoiceMemoPlayback,
        onSeekVoiceMemo = viewModel::seekVoiceMemo,
        onTogglePinCard = viewModel::togglePinCard,
        onDeleteCard = viewModel::deleteCard,
        onImportLocalFile = viewModel::importLocalFile,
        onTogglePinTool = viewModel::togglePinnedTool,
        onClearStatusMessage = viewModel::clearStatusMessage,
        onNavigateToSettings = { navController.navigate(SettingsRoute) },
        onOpenPdfToolkit = { uri -> navController.navigate(PdfToolkitRoute(initialUri = uri)) },
        onOpenPdfAnnotate = { uri -> navController.navigate(PdfAnnotateRoute(initialUri = uri)) },
        onOpenImageToolkit = { uri -> navController.navigate(ImageToolkitRoute(initialUri = uri)) },
        onOpenMediaWorkshop = { uri -> navController.navigate(MediaWorkshopRoute(initialUri = uri)) },
        onOpenAiHub = { mode -> navController.navigate(AiHubRoute(initialMode = mode)) },
        onOpenDocxEditor = { uri -> navController.navigate(DocxEditorRoute(initialUri = uri)) },
        onOpenDataWorkshop = { navController.navigate(DataWorkshopRoute) },
        onOpenArchiveWorkshop = { uri -> navController.navigate(ArchiveWorkshopRoute(initialUri = uri)) },
        onOpenHashWorkshop = { navController.navigate(HashWorkshopRoute) },
        onOpenCryptoVault = { navController.navigate(CryptoVaultRoute) },
        onOpenDevTools = { navController.navigate(DevToolsRoute) },
        onOpenElectronics = { navController.navigate(ElectronicsRoute) },
        onOpenLanShare = { navController.navigate(LanShareRoute) },
        onOpenCompassLevel = { navController.navigate(CompassLevelRoute) },
        onOpenMetalDetector = { navController.navigate(MetalDetectorRoute) },
        onOpenDailyTools = { tab -> navController.navigate(DailyToolsRoute(initialTab = tab)) },
        onOpenQrStudio = { navController.navigate(QrStudioRoute) },
      )
    }

    composable<SettingsRoute> {
      SettingsScreen(
        preferences = uiState.preferences,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onLiquidGlassPowerSaveChange = viewModel::setLiquidGlassPowerSave,
        onSaveAiSettings = viewModel::saveAiSettings,
        onClearAiApiKey = viewModel::clearAiApiKey,
        onGetDecryptedAiApiKey = viewModel::getDecryptedAiApiKey,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<PdfToolkitRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<PdfToolkitRoute>()
      PdfToolkitScreen(
        initialUriString = route.initialUri,
        onOpenAnnotate = { uriStr -> navController.navigate(PdfAnnotateRoute(initialUri = uriStr)) },
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<PdfAnnotateRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<PdfAnnotateRoute>()
      PdfAnnotateScreen(
        initialUriString = route.initialUri,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<ImageToolkitRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<ImageToolkitRoute>()
      ImageToolkitScreen(
        initialUriString = route.initialUri,
        initialTabName = route.initialTab,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<MediaWorkshopRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<MediaWorkshopRoute>()
      MediaWorkshopScreen(
        initialUriString = route.initialUri,
        onOpenImageToolkit = { uri, tab ->
          navController.navigate(ImageToolkitRoute(initialUri = uri, initialTab = tab.name))
        },
        onOpenAiVision = { uri ->
          navController.navigate(AiHubRoute(initialMode = "VISION", initialImageUri = uri))
        },
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<AiHubRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<AiHubRoute>()
      AiHubScreen(
        initialModeName = route.initialMode,
        initialImageUriString = route.initialImageUri,
        preferences = uiState.preferences,
        onSaveNoteCard = viewModel::createNote,
        onSaveChecklistCard = viewModel::createChecklist,
        onNavigateToSettings = { navController.navigate(SettingsRoute) },
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<DocxEditorRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<DocxEditorRoute>()
      DocxEditorScreen(
        initialUriString = route.initialUri,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<DataWorkshopRoute> {
      DataWorkshopScreen(
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<ArchiveWorkshopRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<ArchiveWorkshopRoute>()
      ArchiveWorkshopScreen(
        initialUriString = route.initialUri,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<HashWorkshopRoute> {
      HashWorkshopScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<CryptoVaultRoute> {
      CryptoVaultScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<DevToolsRoute> {
      DevToolsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<ElectronicsRoute> {
      ElectronicsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<LanShareRoute> {
      LanShareScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<CompassLevelRoute> {
      CompassLevelScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<MetalDetectorRoute> {
      MetalDetectorScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<DailyToolsRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<DailyToolsRoute>()
      DailyToolsScreen(
        initialTabName = route.initialTab,
        preferences = uiState.preferences,
        onStepCounter = viewModel::stepCounter,
        onResetCounter = viewModel::resetCounter,
        onSetCounterStep = viewModel::setCounterStep,
        onSetCounterLabel = viewModel::setCounterLabel,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<QrStudioRoute> {
      QrStudioScreen(onNavigateBack = { navController.popBackStack() })
    }
  }
}

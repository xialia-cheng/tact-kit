package com.xialia.tactkit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.xialia.tactkit.ui.ai.AiHubScreen
import com.xialia.tactkit.ui.archive.ArchiveWorkshopScreen
import com.xialia.tactkit.ui.daily.DailyToolsScreen
import com.xialia.tactkit.ui.daily.LedClockScreen
import com.xialia.tactkit.ui.daily.QrStudioScreen
import com.xialia.tactkit.ui.data.DataWorkshopScreen
import com.xialia.tactkit.ui.dev.CryptoVaultScreen
import com.xialia.tactkit.ui.dev.DevToolsScreen
import com.xialia.tactkit.ui.dev.ElectronicsScreen
import com.xialia.tactkit.ui.dev.HashWorkshopScreen
import com.xialia.tactkit.ui.dev.LanShareScreen
import com.xialia.tactkit.ui.files.FileManagerScreen
import com.xialia.tactkit.ui.files.TactKitToolLaunch
import com.xialia.tactkit.ui.home.HomeScreen
import com.xialia.tactkit.ui.home.HomeUiState
import com.xialia.tactkit.ui.home.HomeViewModel
import com.xialia.tactkit.ui.image.ImageToolkitScreen
import com.xialia.tactkit.ui.media.MediaWorkshopScreen
import com.xialia.tactkit.ui.network.DnsSslScreen
import com.xialia.tactkit.ui.network.NetDiagnosticsScreen
import com.xialia.tactkit.ui.network.WifiChannelScreen
import com.xialia.tactkit.ui.office.DocxEditorScreen
import com.xialia.tactkit.ui.pdf.PdfAnnotateScreen
import com.xialia.tactkit.ui.pdf.PdfToolkitScreen
import com.xialia.tactkit.ui.scanner.DocScannerScreen
import com.xialia.tactkit.ui.sensor.BatteryHardwareScreen
import com.xialia.tactkit.ui.sensor.CompassLevelScreen
import com.xialia.tactkit.ui.sensor.DecibelMeterScreen
import com.xialia.tactkit.ui.sensor.LightLuxScreen
import com.xialia.tactkit.ui.sensor.MetalDetectorScreen
import com.xialia.tactkit.ui.sensor.MorseTorchScreen
import com.xialia.tactkit.ui.sensor.PhysicalRulerScreen
import com.xialia.tactkit.ui.settings.HelpTipsScreen
import com.xialia.tactkit.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SettingsRoute

@Serializable data object HelpTipsRoute

@Serializable data class PdfToolkitRoute(val initialUri: String? = null)

@Serializable data class PdfAnnotateRoute(val initialUri: String? = null)

@Serializable data class ImageToolkitRoute(val initialUri: String? = null, val initialTab: String? = null)

@Serializable data class MediaWorkshopRoute(val initialUri: String? = null)

@Serializable data class AiHubRoute(
  val initialMode: String = "VISION",
  val initialImageUri: String? = null,
  val initialTextUri: String? = null,
)

@Serializable data class DocxEditorRoute(val initialUri: String? = null)

@Serializable data class DataWorkshopRoute(val initialUri: String? = null)

@Serializable data class ArchiveWorkshopRoute(val initialUri: String? = null)

@Serializable data class HashWorkshopRoute(val initialUri: String? = null)

@Serializable data class CryptoVaultRoute(val initialUri: String? = null)

@Serializable data object DevToolsRoute

@Serializable data object ElectronicsRoute

@Serializable data class LanShareRoute(val initialUri: String? = null)

@Serializable data object FileManagerRoute

@Serializable data object CompassLevelRoute

@Serializable data object MetalDetectorRoute

@Serializable data class DailyToolsRoute(val initialTab: String? = null)

@Serializable data object QrStudioRoute

@Serializable data object DocScannerRoute

@Serializable data object DecibelMeterRoute

@Serializable data object LightLuxRoute

@Serializable data object PhysicalRulerRoute

@Serializable data object MorseTorchRoute

@Serializable data object BatteryHardwareRoute

@Serializable data object NetDiagnosticsRoute

@Serializable data object DnsSslRoute

@Serializable data object WifiChannelRoute

@Serializable data object LedClockRoute

@Composable
fun TactKitNavHost(
  uiState: HomeUiState,
  viewModel: HomeViewModel,
  pendingToolId: String? = null,
  onConsumedPendingTool: () -> Unit = {},
) {
  val navController = rememberNavController()
  val audioPlaybackState by viewModel.audioPlaybackState.collectAsStateWithLifecycle()
  val onboardingState by viewModel.onboardingUiState.collectAsStateWithLifecycle()
  var pendingHomeDialog by remember { mutableStateOf<String?>(null) }

  fun launchToolById(toolId: String) {
    when (toolId) {
      "doc_scanner" -> navController.navigate(DocScannerRoute)
      "pdf_toolkit" -> navController.navigate(PdfToolkitRoute())
      "pdf_annotate" -> navController.navigate(PdfAnnotateRoute())
      "qr_studio" -> navController.navigate(QrStudioRoute)
      "image_privacy" -> navController.navigate(ImageToolkitRoute())
      "image_long" -> navController.navigate(ImageToolkitRoute(initialTab = "STITCH_PDF"))
      "lan_share" -> navController.navigate(LanShareRoute())
      "file_manager" -> navController.navigate(FileManagerRoute)
      "net_diag" -> navController.navigate(NetDiagnosticsRoute)
      "dns_ssl" -> navController.navigate(DnsSslRoute)
      "wifi_channel" -> navController.navigate(WifiChannelRoute)
      "decibel_meter" -> navController.navigate(DecibelMeterRoute)
      "light_lux" -> navController.navigate(LightLuxRoute)
      "physical_ruler" -> navController.navigate(PhysicalRulerRoute)
      "morse_torch" -> navController.navigate(MorseTorchRoute)
      "battery_hardware" -> navController.navigate(BatteryHardwareRoute)
      "led_clock" -> navController.navigate(LedClockRoute)
      "docx_editor" -> navController.navigate(DocxEditorRoute())
      "ai_hub" -> navController.navigate(AiHubRoute())
      "media_extractor" -> navController.navigate(MediaWorkshopRoute())
      "archive_box" -> navController.navigate(ArchiveWorkshopRoute())
      "data_workshop" -> navController.navigate(DataWorkshopRoute())
      "hash_workshop" -> navController.navigate(HashWorkshopRoute())
      "crypto_vault" -> navController.navigate(CryptoVaultRoute())
      "electronics_calc" -> navController.navigate(ElectronicsRoute)
      "quick_note", "quick_checklist", "voice_memo" -> {
        navController.popBackStack(HomeRoute, inclusive = false)
        pendingHomeDialog = toolId
      }
    }
  }

  LaunchedEffect(pendingToolId) {
    val toolId = pendingToolId ?: return@LaunchedEffect
    launchToolById(toolId)
    onConsumedPendingTool()
  }

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
        onDismissAvailableUpdate = viewModel::dismissAvailableUpdate,
        onboardingState = onboardingState,
        onStartOnboarding = viewModel::startOnboardingTutorial,
        onAdvanceOnboarding = viewModel::advanceOnboardingStep,
        onSkipOnboarding = viewModel::skipOnboardingForever,
        onNavigateToSettings = { navController.navigate(SettingsRoute) },
        onOpenPdfToolkit = { uri -> navController.navigate(PdfToolkitRoute(initialUri = uri)) },
        onOpenPdfAnnotate = { uri -> navController.navigate(PdfAnnotateRoute(initialUri = uri)) },
        onOpenImageToolkit = { uri -> navController.navigate(ImageToolkitRoute(initialUri = uri)) },
        onOpenMediaWorkshop = { uri -> navController.navigate(MediaWorkshopRoute(initialUri = uri)) },
        onOpenAiHub = { mode -> navController.navigate(AiHubRoute(initialMode = mode)) },
        onOpenDocxEditor = { uri -> navController.navigate(DocxEditorRoute(initialUri = uri)) },
        onOpenDataWorkshop = { navController.navigate(DataWorkshopRoute()) },
        onOpenArchiveWorkshop = { uri -> navController.navigate(ArchiveWorkshopRoute(initialUri = uri)) },
        onOpenHashWorkshop = { navController.navigate(HashWorkshopRoute()) },
        onOpenCryptoVault = { navController.navigate(CryptoVaultRoute()) },
        onOpenDevTools = { navController.navigate(DevToolsRoute) },
        onOpenElectronics = { navController.navigate(ElectronicsRoute) },
        onOpenLanShare = { navController.navigate(LanShareRoute()) },
        onOpenCompassLevel = { navController.navigate(CompassLevelRoute) },
        onOpenMetalDetector = { navController.navigate(MetalDetectorRoute) },
        onOpenDailyTools = { tab -> navController.navigate(DailyToolsRoute(initialTab = tab)) },
        onOpenQrStudio = { navController.navigate(QrStudioRoute) },
        onOpenDocScanner = { navController.navigate(DocScannerRoute) },
        onOpenImageLongPdf = { navController.navigate(ImageToolkitRoute(initialTab = "STITCH_PDF")) },
        onOpenDecibelMeter = { navController.navigate(DecibelMeterRoute) },
        onOpenLightLux = { navController.navigate(LightLuxRoute) },
        onOpenPhysicalRuler = { navController.navigate(PhysicalRulerRoute) },
        onOpenMorseTorch = { navController.navigate(MorseTorchRoute) },
        onOpenBatteryHardware = { navController.navigate(BatteryHardwareRoute) },
        onOpenNetDiagnostics = { navController.navigate(NetDiagnosticsRoute) },
        onOpenDnsSsl = { navController.navigate(DnsSslRoute) },
        onOpenWifiChannel = { navController.navigate(WifiChannelRoute) },
        onOpenLedClock = { navController.navigate(LedClockRoute) },
        onOpenFileManager = { navController.navigate(FileManagerRoute) },
        pendingHomeDialog = pendingHomeDialog,
        onConsumedHomeDialog = { pendingHomeDialog = null },
      )
    }

    composable<SettingsRoute> {
      SettingsScreen(
        preferences = uiState.preferences,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onLiquidGlassPowerSaveChange = viewModel::setLiquidGlassPowerSave,
        onAutoCheckUpdatesChange = viewModel::setAutoCheckUpdates,
        onSaveAiSettings = viewModel::saveAiSettings,
        onClearAiApiKey = viewModel::clearAiApiKey,
        onGetDecryptedAiApiKey = viewModel::getDecryptedAiApiKey,
        onOpenHelpTips = { navController.navigate(HelpTipsRoute) },
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<HelpTipsRoute> {
      HelpTipsScreen(
        onLaunchTool = { toolId -> launchToolById(toolId) },
        onReplayOnboarding = {
          viewModel.replayOnboardingFromHelp()
          navController.popBackStack(HomeRoute, inclusive = false)
        },
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
        initialTextUriString = route.initialTextUri,
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

    composable<DataWorkshopRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<DataWorkshopRoute>()
      DataWorkshopScreen(
        initialUriString = route.initialUri,
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

    composable<HashWorkshopRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<HashWorkshopRoute>()
      HashWorkshopScreen(
        initialUriString = route.initialUri,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<CryptoVaultRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<CryptoVaultRoute>()
      CryptoVaultScreen(
        initialUriString = route.initialUri,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<DevToolsRoute> {
      DevToolsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<ElectronicsRoute> {
      ElectronicsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<LanShareRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<LanShareRoute>()
      LanShareScreen(
        initialUriString = route.initialUri,
        onSaveNoteCard = viewModel::createNote,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<FileManagerRoute> {
      FileManagerScreen(
        preferences = uiState.preferences,
        onTogglePinnedDir = viewModel::togglePinnedDir,
        onAddLinkedSafTreeUri = viewModel::addLinkedSafTreeUri,
        onLaunchTool = { uriStr, launch ->
          when (launch) {
            TactKitToolLaunch.PDF_TOOLKIT -> navController.navigate(PdfToolkitRoute(initialUri = uriStr))
            TactKitToolLaunch.PDF_ANNOTATE -> navController.navigate(PdfAnnotateRoute(initialUri = uriStr))
            TactKitToolLaunch.DOCX_EDITOR -> navController.navigate(DocxEditorRoute(initialUri = uriStr))
            TactKitToolLaunch.DATA_WORKSHOP -> navController.navigate(DataWorkshopRoute(initialUri = uriStr))
            TactKitToolLaunch.ARCHIVE_WORKSHOP -> navController.navigate(ArchiveWorkshopRoute(initialUri = uriStr))
            TactKitToolLaunch.IMAGE_TOOLKIT -> navController.navigate(ImageToolkitRoute(initialUri = uriStr))
            TactKitToolLaunch.MEDIA_WORKSHOP -> navController.navigate(MediaWorkshopRoute(initialUri = uriStr))
            TactKitToolLaunch.AI_VISION ->
              navController.navigate(AiHubRoute(initialMode = "VISION", initialImageUri = uriStr))
            TactKitToolLaunch.AI_TEXT_ORGANIZE ->
              navController.navigate(AiHubRoute(initialMode = "CHECKLIST", initialTextUri = uriStr))
            TactKitToolLaunch.HASH_WORKSHOP -> navController.navigate(HashWorkshopRoute(initialUri = uriStr))
            TactKitToolLaunch.CRYPTO_VAULT -> navController.navigate(CryptoVaultRoute(initialUri = uriStr))
            TactKitToolLaunch.LAN_SHARE -> navController.navigate(LanShareRoute(initialUri = uriStr))
            TactKitToolLaunch.QUICK_LOOK -> Unit
          }
        },
        onNavigateBack = { navController.popBackStack() },
      )
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

    composable<DocScannerRoute> {
      DocScannerScreen(
        onRegisterPdfFile = viewModel::registerGeneratedFileCard,
        onOpenQrStudio = { navController.navigate(QrStudioRoute) },
        onOpenPdfToolkit = { uri -> navController.navigate(PdfToolkitRoute(initialUri = uri)) },
        onOpenPdfAnnotate = { uri -> navController.navigate(PdfAnnotateRoute(initialUri = uri)) },
        onOpenLanShare = { uri -> navController.navigate(LanShareRoute(initialUri = uri)) },
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable<DecibelMeterRoute> {
      DecibelMeterScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<LightLuxRoute> {
      LightLuxScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<PhysicalRulerRoute> {
      PhysicalRulerScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<MorseTorchRoute> {
      MorseTorchScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<BatteryHardwareRoute> {
      BatteryHardwareScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<NetDiagnosticsRoute> {
      NetDiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<DnsSslRoute> {
      DnsSslScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<WifiChannelRoute> {
      WifiChannelScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable<LedClockRoute> {
      LedClockScreen(onNavigateBack = { navController.popBackStack() })
    }
  }
}

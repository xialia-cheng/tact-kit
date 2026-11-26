package com.xialia.tactkit.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.R
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.domain.ToolItem
import com.xialia.tactkit.engine.AudioPlaybackState
import com.xialia.tactkit.engine.VoiceMemoManager
import com.xialia.tactkit.ui.components.LiquidGlassToolBallOverlay
import com.xialia.tactkit.ui.components.NewVersionUpdateDialog
import com.xialia.tactkit.ui.components.OnboardingStep
import com.xialia.tactkit.ui.components.OnboardingWelcomeDialog
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.RollingNumberText
import com.xialia.tactkit.ui.components.SpotlightOnboardingOverlay
import com.xialia.tactkit.ui.components.TactileCard
import com.xialia.tactkit.ui.components.VoiceMemoDialog
import com.xialia.tactkit.ui.components.WorkbenchCardView
import com.xialia.tactkit.ui.components.tactilePress
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.ui.theme.OreUiButton
import com.xialia.tactkit.ui.theme.OreUiSegmentedTabRow
import com.xialia.tactkit.ui.theme.OreUiTone
import com.xialia.tactkit.ui.theme.TactKitCard
import com.xialia.tactkit.ui.theme.TactKitChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.mcGrassButtonBox
import com.xialia.tactkit.ui.theme.mcPixelBox
import com.xialia.tactkit.ui.theme.minecraftPixelBevel
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.AppHaptics
import com.xialia.tactkit.util.rememberAppHaptics

private data class QuickStripEntry(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: HomeUiState,
  audioPlaybackState: AudioPlaybackState,
  voiceMemoManager: VoiceMemoManager,
  onSearchQueryChange: (String) -> Unit,
  onFilterChange: (CardFilter) -> Unit,
  onCreateChecklist: (String, List<String>) -> Unit,
  onToggleChecklistItem: (Long, String) -> Unit,
  onAddChecklistItem: (Long, String) -> Unit,
  onDeleteChecklistItem: (Long, String) -> Unit,
  onCreateNote: (String, String) -> Unit,
  onUpdateNote: (Long, String, String) -> Unit,
  onCreateVoiceMemo: (String, String, Long, String) -> Unit,
  onToggleVoiceMemoPlay: (WorkbenchCard.VoiceMemo) -> Unit,
  onSeekVoiceMemo: (Long, Long) -> Unit,
  onTogglePinCard: (Long) -> Unit,
  onDeleteCard: (Long) -> Unit,
  onImportLocalFile: (Uri) -> Unit,
  onTogglePinTool: (String) -> Unit,
  onClearStatusMessage: () -> Unit,
  onDismissAvailableUpdate: () -> Unit = {},
  onboardingState: OnboardingUiState = OnboardingUiState(),
  onStartOnboarding: () -> Unit = {},
  onAdvanceOnboarding: () -> Unit = {},
  onSkipOnboarding: () -> Unit = {},
  onNavigateToSettings: () -> Unit,
  onOpenPdfToolkit: (String?) -> Unit,
  onOpenPdfAnnotate: (String?) -> Unit,
  onOpenImageToolkit: (String?) -> Unit,
  onOpenMediaWorkshop: (String?) -> Unit,
  onOpenAiHub: (String) -> Unit,
  onOpenDocxEditor: (String?) -> Unit,
  onOpenDataWorkshop: () -> Unit,
  onOpenArchiveWorkshop: (String?) -> Unit,
  onOpenHashWorkshop: () -> Unit,
  onOpenCryptoVault: () -> Unit,
  onOpenDevTools: () -> Unit,
  onOpenElectronics: () -> Unit,
  onOpenLanShare: () -> Unit,
  onOpenCompassLevel: () -> Unit,
  onOpenMetalDetector: () -> Unit,
  onOpenDailyTools: (String?) -> Unit,
  onOpenQrStudio: () -> Unit,
  onOpenDocScanner: () -> Unit,
  onOpenImageLongPdf: () -> Unit,
  onOpenDecibelMeter: () -> Unit,
  onOpenLightLux: () -> Unit,
  onOpenPhysicalRuler: () -> Unit,
  onOpenMorseTorch: () -> Unit,
  onOpenBatteryHardware: () -> Unit,
  onOpenNetDiagnostics: () -> Unit,
  onOpenDnsSsl: () -> Unit,
  onOpenWifiChannel: () -> Unit,
  onOpenLedClock: () -> Unit,
  onOpenFileManager: () -> Unit,
  pendingHomeDialog: String? = null,
  onConsumedHomeDialog: () -> Unit = {},
) {
  val context = LocalContext.current
  val view = LocalView.current
  val haptics = rememberAppHaptics()
  val snackbarHostState = remember { SnackbarHostState() }

  var isSearchBarVisible by remember { mutableStateOf(false) }
  var showCreateChecklistDialog by remember { mutableStateOf(false) }
  var editingNote by remember { mutableStateOf<WorkbenchCard.Note?>(null) }
  var showCreateNoteDialog by remember { mutableStateOf(false) }
  var showVoiceMemoDialog by remember { mutableStateOf(false) }

  val targetBoundsMap = remember { mutableStateMapOf<OnboardingStep, Rect>() }

  LaunchedEffect(pendingHomeDialog) {
    when (pendingHomeDialog) {
      "quick_note" -> {
        showCreateNoteDialog = true
        onConsumedHomeDialog()
      }
      "quick_checklist" -> {
        showCreateChecklistDialog = true
        onConsumedHomeDialog()
      }
      "voice_memo" -> {
        showVoiceMemoDialog = true
        onConsumedHomeDialog()
      }
    }
  }

  val safImportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        haptics.confirm()
        onImportLocalFile(uri)
      }
    }

  val quickLookLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        haptics.tick()
        val intent =
          Intent(context, QuickLookActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        context.startActivity(intent)
      }
    }

  LaunchedEffect(uiState.statusMessage) {
    uiState.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      onClearStatusMessage()
    }
  }

  val bodySurfaceBg = MaterialTheme.colorScheme.surfaceContainerLowest
  val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f
  val themeStyle = com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current
  val isMinecraft = themeStyle == com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
  val topBarSurfaceBg =
    if (isMinecraft) (if (isDark) Color(0xFF1C1D20) else Color(0xFFE6E8EB))
    else MaterialTheme.colorScheme.surfaceContainer
  val backdrop = rememberLayerBackdrop { drawRect(bodySurfaceBg) }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .let { if (isMinecraft) it else it.layerBackdrop(backdrop) }
          .background(bodySurfaceBg),
    ) {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = topBarSurfaceBg,
                  scrolledContainerColor = topBarSurfaceBg,
                ),
              title = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "简工坊",
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Surface(
                    color = if (isDark) Color(0xFF2D3240) else Color(0xFF1C1F27),
                    shape = if (isMinecraft) androidx.compose.ui.graphics.RectangleShape else ContinuousCapsule(),
                  ) {
                    Text(
                      text = "v${BuildConfig.VERSION_NAME}",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = Color.White,
                      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                  }
                  if (isMinecraft) {
                    Text(
                      text = "Local-First!",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFFFD83D),
                      modifier = Modifier.graphicsLayer { rotationZ = -8f },
                    )
                  } else {
                    Text(
                      text = "✨",
                      fontSize = 15.sp,
                      color = Color(0xFFD99B26),
                    )
                  }
                }
              },
            actions = {
              IconButton(
                onClick = {
                  AppHaptics.click(view)
                  isSearchBarVisible = !isSearchBarVisible
                  if (!isSearchBarVisible) onSearchQueryChange("")
                },
                modifier = Modifier.testTag("home_search_toggle"),
              ) {
                Icon(
                  imageVector =
                    if (isSearchBarVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                  contentDescription = stringResource(R.string.cd_search),
                )
              }
              IconButton(
                onClick = {
                  AppHaptics.click(view)
                  onNavigateToSettings()
                },
                modifier =
                  Modifier.onGloballyPositioned { coords ->
                      targetBoundsMap[OnboardingStep.SETTINGS_ENTRY] = coords.boundsInRoot()
                    }
                    .testTag("settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Settings,
                  contentDescription = stringResource(R.string.cd_settings),
                )
              }
            },
            )
            if (isMinecraft) {
              com.xialia.tactkit.ui.theme.McGrassDirtTrim()
            }
          }
        },
      ) { innerPadding ->
        Column(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
          if (uiState.isImportingFile) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }

          AnimatedVisibility(visible = isSearchBarVisible) {
            OutlinedTextField(
              value = uiState.searchQuery,
              onValueChange = onSearchQueryChange,
              placeholder = { Text("搜索标题、清单项、便签、语音或文件名...") },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
              trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                  IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                      Icons.Rounded.Close,
                      contentDescription = stringResource(R.string.cd_close),
                    )
                  }
                }
              },
              singleLine = true,
              shape = themeShape(16.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
                  .testTag("home_search_input"),
            )
          }

          val openChecklistCreator = {
            haptics.tick()
            showCreateChecklistDialog = true
          }
          val openNoteCreator = {
            haptics.tick()
            showCreateNoteDialog = true
          }
          val openVoiceMemoCreator = {
            haptics.tick()
            showVoiceMemoDialog = true
          }
          val openFileImporter = {
            haptics.tick()
            safImportLauncher.launch(arrayOf("*/*"))
          }
          val openRecentFile: (WorkbenchCard.RecentFile) -> Unit = { fileCard ->
            val uriStr = fileCard.fileUri
            if (uriStr != null) {
              val lowerName = fileCard.fileName.lowercase()
              when {
                fileCard.mimeType == "application/pdf" -> onOpenPdfToolkit(uriStr)
                fileCard.mimeType.startsWith("image/") -> onOpenImageToolkit(uriStr)
                fileCard.mimeType.startsWith("video/") || fileCard.mimeType.startsWith("audio/") ->
                  onOpenMediaWorkshop(uriStr)
                lowerName.endsWith(".docx") ||
                  fileCard.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                  onOpenDocxEditor(uriStr)
                lowerName.endsWith(".zip") ||
                  lowerName.endsWith(".7z") ||
                  lowerName.endsWith(".tar") ||
                  lowerName.endsWith(".tar.gz") ||
                  lowerName.endsWith(".tgz") ||
                  lowerName.endsWith(".tar.xz") ||
                  lowerName.endsWith(".txz") ||
                  lowerName.endsWith(".gz") ||
                  lowerName.endsWith(".xz") ->
                  onOpenArchiveWorkshop(uriStr)
                else -> {
                  val intent =
                    Intent(context, QuickLookActivity::class.java).apply {
                      action = Intent.ACTION_VIEW
                      data = Uri.parse(uriStr)
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                  context.startActivity(intent)
                }
              }
            }
          }

          val quickStripEntries =
            remember {
              listOf(
                QuickStripEntry("strip_files", "文件管理", Icons.Rounded.FolderOpen) {
                  haptics.tick()
                  onOpenFileManager()
                },
                QuickStripEntry("strip_scan", "文档扫描", Icons.Rounded.DocumentScanner) {
                  haptics.tick()
                  onOpenDocScanner()
                },
                QuickStripEntry("strip_pdf", "PDF 工具", Icons.Rounded.PictureAsPdf) {
                  haptics.tick()
                  onOpenPdfToolkit(null)
                },
                QuickStripEntry("strip_img", "图片隐私", Icons.Rounded.HideImage) {
                  haptics.tick()
                  onOpenImageToolkit(null)
                },
                QuickStripEntry("strip_qr", "二维码", Icons.Rounded.QrCode2) {
                  haptics.tick()
                  onOpenQrStudio()
                },
                QuickStripEntry("strip_lan", "局域网快传", Icons.Rounded.WifiTethering) {
                  haptics.tick()
                  onOpenLanShare()
                },
                QuickStripEntry("strip_net", "网络体检", Icons.Rounded.Lan) {
                  haptics.tick()
                  onOpenNetDiagnostics()
                },
                QuickStripEntry("strip_db", "分贝计", Icons.Rounded.GraphicEq) {
                  haptics.tick()
                  onOpenDecibelMeter()
                },
                QuickStripEntry("strip_hw", "电池硬件", Icons.Rounded.BatteryChargingFull) {
                  haptics.tick()
                  onOpenBatteryHardware()
                },
                QuickStripEntry("strip_hash", "哈希校验", Icons.Rounded.Fingerprint) {
                  haptics.tick()
                  onOpenHashWorkshop()
                },
                QuickStripEntry("strip_daily", "日常工具", Icons.Rounded.Calculate) {
                  haptics.tick()
                  onOpenDailyTools(null)
                },
                QuickStripEntry("strip_compass", "指南针", Icons.Rounded.Explore) {
                  haptics.tick()
                  onOpenCompassLevel()
                },
                QuickStripEntry("strip_docx", "Docx 文档", Icons.Rounded.Description) {
                  haptics.tick()
                  onOpenDocxEditor(null)
                },
                QuickStripEntry("strip_data", "数据工坊", Icons.Rounded.TableChart) {
                  haptics.tick()
                  onOpenDataWorkshop()
                },
                QuickStripEntry("strip_archive", "压缩包", Icons.Rounded.Archive) {
                  haptics.tick()
                  onOpenArchiveWorkshop(null)
                },
                QuickStripEntry("strip_ai", "AI 提取", Icons.Rounded.AutoAwesome) {
                  haptics.tick()
                  onOpenAiHub("VISION")
                },
                QuickStripEntry("strip_note", "新建便签", Icons.Rounded.EditNote) {
                  openNoteCreator()
                },
                QuickStripEntry("strip_voice", "语音备忘", Icons.Rounded.Mic) {
                  openVoiceMemoCreator()
                },
                QuickStripEntry("strip_media", "音视频", Icons.Rounded.AudioFile) {
                  haptics.tick()
                  onOpenMediaWorkshop(null)
                },
              )
            }

          BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 640.dp

            if (isWide) {
              LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding =
                  PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                item(span = { GridItemSpan(2) }, key = "quick_strip") {
                  QuickActionStrip(
                    entries = quickStripEntries,
                    modifier =
                      Modifier.onGloballyPositioned { coords ->
                        targetBoundsMap[OnboardingStep.QUICK_STRIP] = coords.boundsInRoot()
                      },
                  )
                }
                item(span = { GridItemSpan(2) }, key = "overview_header") {
                  WorkbenchOverviewSection(
                    uiState = uiState,
                    onFilterChange = {
                      AppHaptics.click(view)
                      onFilterChange(it)
                    },
                    onOpenRecentFile = openRecentFile,
                    onOpenFileManager = {
                      AppHaptics.click(view)
                      onOpenFileManager()
                    },
                    onOpenDocScanner = onOpenDocScanner,
                    onOpenLanShare = onOpenLanShare,
                    onOpenAiHub = { onOpenAiHub("VISION") },
                    onQuickNote = openNoteCreator,
                    onDashboardPositioned = { rect ->
                      targetBoundsMap[OnboardingStep.DASHBOARD] = rect
                    },
                    onFileManagerButtonPositioned = { rect ->
                      targetBoundsMap[OnboardingStep.FILE_MANAGER] = rect
                    },
                  )
                }

                if (uiState.cards.isEmpty()) {
                  item(span = { GridItemSpan(2) }, key = "empty_actions") {
                    EmptyQuickActionsSection(
                      hasSearchOrFilter =
                        uiState.searchQuery.isNotBlank() || uiState.activeFilter != CardFilter.ALL,
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
                      onCreateVoiceMemo = openVoiceMemoCreator,
                      onImportFile = openFileImporter,
                      onResetFilter = {
                        haptics.tick()
                        onSearchQueryChange("")
                        onFilterChange(CardFilter.ALL)
                      },
                    )
                  }
                } else {
                  items(uiState.cards, key = { it.id }) { card ->
                    WorkbenchCardView(
                      card = card,
                      audioPlaybackState = audioPlaybackState,
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenRecentFile = openRecentFile,
                      onToggleVoiceMemoPlay = onToggleVoiceMemoPlay,
                      onSeekVoiceMemo = onSeekVoiceMemo,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
                    )
                  }
                  item(span = { GridItemSpan(2) }, key = "footer_quick_actions") {
                    QuickCreateListSection(
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
                      onCreateVoiceMemo = openVoiceMemoCreator,
                      onImportFile = openFileImporter,
                    )
                  }
                }
              }
            } else {
              LazyColumn(
                contentPadding =
                  PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                item(key = "quick_strip") {
                  QuickActionStrip(
                    entries = quickStripEntries,
                    modifier =
                      Modifier.onGloballyPositioned { coords ->
                        targetBoundsMap[OnboardingStep.QUICK_STRIP] = coords.boundsInRoot()
                      },
                  )
                }
                item(key = "overview_header") {
                  WorkbenchOverviewSection(
                    uiState = uiState,
                    onFilterChange = {
                      AppHaptics.click(view)
                      onFilterChange(it)
                    },
                    onOpenRecentFile = openRecentFile,
                    onOpenFileManager = {
                      AppHaptics.click(view)
                      onOpenFileManager()
                    },
                    onOpenDocScanner = onOpenDocScanner,
                    onOpenLanShare = onOpenLanShare,
                    onOpenAiHub = { onOpenAiHub("VISION") },
                    onQuickNote = openNoteCreator,
                    onDashboardPositioned = { rect ->
                      targetBoundsMap[OnboardingStep.DASHBOARD] = rect
                    },
                    onFileManagerButtonPositioned = { rect ->
                      targetBoundsMap[OnboardingStep.FILE_MANAGER] = rect
                    },
                  )
                }

                if (uiState.cards.isEmpty()) {
                  item(key = "empty_actions") {
                    EmptyQuickActionsSection(
                      hasSearchOrFilter =
                        uiState.searchQuery.isNotBlank() || uiState.activeFilter != CardFilter.ALL,
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
                      onCreateVoiceMemo = openVoiceMemoCreator,
                      onImportFile = openFileImporter,
                      onResetFilter = {
                        haptics.tick()
                        onSearchQueryChange("")
                        onFilterChange(CardFilter.ALL)
                      },
                    )
                  }
                } else {
                  items(uiState.cards, key = { it.id }) { card ->
                    WorkbenchCardView(
                      card = card,
                      audioPlaybackState = audioPlaybackState,
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenRecentFile = openRecentFile,
                      onToggleVoiceMemoPlay = onToggleVoiceMemoPlay,
                      onSeekVoiceMemo = onSeekVoiceMemo,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
                    )
                  }
                  item(key = "footer_quick_actions") {
                    QuickCreateListSection(
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
                      onCreateVoiceMemo = openVoiceMemoCreator,
                      onImportFile = openFileImporter,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Floating Liquid Glass Tool Ball & Drawer
    LiquidGlassToolBallOverlay(
      backdrop = backdrop,
      isPowerSaveMode = uiState.preferences.liquidGlassPowerSave,
      pinnedToolIds = uiState.preferences.pinnedToolIds,
      ballModifier =
        Modifier.onGloballyPositioned { coords ->
          targetBoundsMap[OnboardingStep.TOOL_BALL] = coords.boundsInRoot()
        },
      onLaunchTool = { tool: ToolItem ->
        when (tool.id) {
          "quick_note" -> showCreateNoteDialog = true
          "quick_checklist" -> showCreateChecklistDialog = true
          "voice_memo" -> showVoiceMemoDialog = true
          "doc_scanner" -> onOpenDocScanner()
          "quick_look" -> quickLookLauncher.launch(arrayOf("*/*"))
          "ai_hub" -> onOpenAiHub("VISION")
          "docx_editor" -> onOpenDocxEditor(null)
          "data_workshop" -> onOpenDataWorkshop()
          "pdf_toolkit" -> onOpenPdfToolkit(null)
          "pdf_annotate" -> onOpenPdfAnnotate(null)
          "image_privacy" -> onOpenImageToolkit(null)
          "image_long" -> onOpenImageLongPdf()
          "media_extractor" -> onOpenMediaWorkshop(null)
          "led_clock" -> onOpenLedClock()
          "file_manager" -> onOpenFileManager()
          "archive_box" -> onOpenArchiveWorkshop(null)
          "lan_share" -> onOpenLanShare()
          "net_diag" -> onOpenNetDiagnostics()
          "dns_ssl" -> onOpenDnsSsl()
          "wifi_channel" -> onOpenWifiChannel()
          "hash_workshop" -> onOpenHashWorkshop()
          "crypto_vault" -> onOpenCryptoVault()
          "codec_hash" -> onOpenDevTools()
          "electronics_calc" -> onOpenElectronics()
          "level_compass" -> onOpenCompassLevel()
          "metal_detector" -> onOpenMetalDetector()
          "decibel_meter" -> onOpenDecibelMeter()
          "light_lux" -> onOpenLightLux()
          "physical_ruler" -> onOpenPhysicalRuler()
          "morse_torch" -> onOpenMorseTorch()
          "battery_hardware" -> onOpenBatteryHardware()
          "unit_calculator" -> onOpenDailyTools(null)
          "qr_studio" -> onOpenQrStudio()
        }
      },
      onTogglePinTool = onTogglePinTool,
    )

    // Interactive Spotlight Onboarding Overlay (5 steps on real HomeScreen)
    onboardingState.activeStep?.let { step ->
      SpotlightOnboardingOverlay(
        currentStep = step,
        targetBoundsMap = targetBoundsMap,
        onAdvanceStep = onAdvanceOnboarding,
        onSkipTutorial = onSkipOnboarding,
      )
    }
  }

  if (onboardingState.showWelcomeDialog && onboardingState.activeStep == null) {
    OnboardingWelcomeDialog(
      onSkipForever = onSkipOnboarding,
      onStartTutorial = onStartOnboarding,
    )
  } else if (onboardingState.activeStep == null) {
    uiState.availableUpdate?.let { releaseInfo ->
      NewVersionUpdateDialog(
        releaseInfo = releaseInfo,
        currentVersionName = BuildConfig.VERSION_NAME,
        onDismiss = onDismissAvailableUpdate,
      )
    }
  }

  if (showCreateChecklistDialog) {
    CreateChecklistDialog(
      onDismiss = { showCreateChecklistDialog = false },
      onConfirm = { title, items ->
        haptics.confirm()
        onCreateChecklist(title, items)
        showCreateChecklistDialog = false
      },
    )
  }

  if (showCreateNoteDialog || editingNote != null) {
    NoteEditorDialog(
      initialTitle = editingNote?.title.orEmpty(),
      initialContent = editingNote?.markdownContent.orEmpty(),
      isEditing = editingNote != null,
      onDismiss = {
        showCreateNoteDialog = false
        editingNote = null
      },
      onSave = { title, content ->
        haptics.confirm()
        val current = editingNote
        if (current != null) {
          onUpdateNote(current.id, title, content)
        } else {
          onCreateNote(title, content)
        }
        showCreateNoteDialog = false
        editingNote = null
      },
    )
  }

  if (showVoiceMemoDialog) {
    VoiceMemoDialog(
      voiceMemoManager = voiceMemoManager,
      onDismiss = { showVoiceMemoDialog = false },
      onSaveMemo = { title, filePath, durationMs, note ->
        onCreateVoiceMemo(title, filePath, durationMs, note)
        showVoiceMemoDialog = false
      },
    )
  }
}

@Composable
private fun QuickActionStrip(
  entries: List<QuickStripEntry>,
  modifier: Modifier = Modifier,
) {
  val view = LocalView.current
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth().testTag("quick_action_strip"),
  ) {
    items(entries, key = { it.id }) { entry ->
      val interaction = remember { MutableInteractionSource() }
      val isMc = com.xialia.tactkit.ui.theme.isMcPixelTheme()
      val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f
      if (isMc) {
        val isAi = entry.id.contains("ai", ignoreCase = true) || entry.label.contains("AI", ignoreCase = true)
        val tone =
          when {
            isAi -> OreUiTone.PURPLE
            isDark -> OreUiTone.SLATE_DARK
            else -> OreUiTone.SLATE_LIGHT
          }
        OreUiButton(
          onClick = {
            AppHaptics.click(view)
            entry.onClick()
          },
          tone = tone,
          interactionSource = interaction,
          modifier = Modifier.testTag(entry.id),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
          ) {
            Icon(
              imageVector = entry.icon,
              contentDescription = null,
              tint = if (isAi) Color.White else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = entry.label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      } else {
        TactKitChip(
          onClick = {
            AppHaptics.click(view)
            entry.onClick()
          },
          interactionSource = interaction,
          corner = 18.dp,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier =
            Modifier.tactilePress(
                interactionSource = interaction,
                baseCorner = 18.dp,
                pressedCorner = 24.dp,
                enableHapticOnPress = false,
              )
              .testTag(entry.id),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
          ) {
            Icon(
              imageVector = entry.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = entry.label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WorkbenchOverviewSection(
  uiState: HomeUiState,
  onFilterChange: (CardFilter) -> Unit,
  onOpenRecentFile: (WorkbenchCard.RecentFile) -> Unit,
  onOpenFileManager: () -> Unit,
  onOpenDocScanner: () -> Unit = {},
  onOpenLanShare: () -> Unit = {},
  onOpenAiHub: () -> Unit = {},
  onQuickNote: () -> Unit = {},
  onDashboardPositioned: (Rect) -> Unit = {},
  onFileManagerButtonPositioned: (Rect) -> Unit = {},
) {
  val themeStyle = com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current
  val isDark = MaterialTheme.colorScheme.background.red < 0.2f
  val isMinecraft = themeStyle == com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
  val cardCorner =
    when (themeStyle) {
      com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL -> 0.dp
      com.xialia.tactkit.data.datastore.AppThemeStyle.PRECISION_LAB -> 16.dp
      else -> 22.dp
    }
  val cardBorder =
    com.xialia.tactkit.ui.theme.themeCardBorder(
      themeStyle,
      isDark,
      MaterialTheme.colorScheme.outlineVariant,
    )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (themeStyle == com.xialia.tactkit.data.datastore.AppThemeStyle.GLACIER_BENTO) {
      // 2-column asymmetric Bento dashboard (left tall FileManager, right stacked PDF/Scanner + LAN Share, bottom AI + QuickNote)
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
          Modifier.fillMaxWidth()
            .onGloballyPositioned { coords -> onDashboardPositioned(coords.boundsInRoot()) }
            .testTag("workbench_overview_card"),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
          TactileCard(
            onClick = onOpenFileManager,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            baseCorner = 22.dp,
            pressedCorner = 26.dp,
            modifier =
              Modifier.weight(1f)
                .fillMaxSize()
                .onGloballyPositioned { coords -> onFileManagerButtonPositioned(coords.boundsInRoot()) },
          ) {
            Column(
              verticalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
              )
              Spacer(modifier = Modifier.height(16.dp))
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "文件管理",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "共 ${uiState.dashboardStats.totalItemsCount} 项文档 · 全盘直览",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
              }
            }
          }

          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            TactileCard(
              onClick = onOpenDocScanner,
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              baseCorner = 20.dp,
              pressedCorner = 24.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.DocumentScanner,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("PDF 与扫描", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Text("透视拉直 · 多页", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            TactileCard(
              onClick = onOpenLanShare,
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              baseCorner = 20.dp,
              pressedCorner = 24.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.WifiTethering,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("局域网快传", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Text("同网扫码互传", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          TactileCard(
            onClick = onOpenAiHub,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            baseCorner = 20.dp,
            pressedCorner = 24.dp,
            modifier = Modifier.weight(1f),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(14.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("AI 中枢", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("识图 · 摘要", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          TactileCard(
            onClick = onQuickNote,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            baseCorner = 20.dp,
            pressedCorner = 24.dp,
            modifier = Modifier.weight(1f),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(14.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("随手记", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${uiState.totalNotes} 便签 · ${uiState.totalChecklists} 清单", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    } else {
      // Borderless tonal overview card (CLASSIC_DEFAULT and non-Bento themes)
      Surface(
        shape = themeShape(cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = cardBorder,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
          Modifier.fillMaxWidth()
            .let {
              if (isMinecraft) it.minecraftPixelBevel(isDark = isDark)
              else it
            }
            .onGloballyPositioned { coords -> onDashboardPositioned(coords.boundsInRoot()) }
            .testTag("workbench_overview_card"),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.padding(16.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "我的工作台",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = if (isMinecraft) RoundedCornerShape(0.dp) else ContinuousCapsule(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Shield,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = stringResource(R.string.local_first_badge),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          // Flat unboxed stats row
          Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
          ) {
            OverviewStatTile(
              count = uiState.totalChecklists,
              label = "清单",
              modifier = Modifier.weight(1f),
            )
            OverviewStatTile(
              count = uiState.totalNotes,
              label = "便签",
              modifier = Modifier.weight(1f),
            )
            OverviewStatTile(
              count = uiState.totalVoiceMemos,
              label = "语音",
              modifier = Modifier.weight(1f),
            )
            OverviewStatTile(
              count = uiState.totalFiles,
              label = "文件",
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      DocumentDashboardCard(
        stats = uiState.dashboardStats,
        onOpenRecentFile = onOpenRecentFile,
        onOpenFileManager = onOpenFileManager,
        onFileManagerButtonPositioned = onFileManagerButtonPositioned,
      )
    }

    // Aligned borderless tonal filter tabs
    if (isMinecraft) {
      OreUiSegmentedTabRow(
        items = CardFilter.entries.map { it.label to (uiState.activeFilter == it) },
        onSelect = { idx -> onFilterChange(CardFilter.entries[idx]) },
      )
    } else {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        CardFilter.entries.forEach { filter ->
          val selected = uiState.activeFilter == filter
          val filterInteraction = remember { MutableInteractionSource() }
          Surface(
            onClick = { onFilterChange(filter) },
            interactionSource = filterInteraction,
            shape = RoundedCornerShape(14.dp),
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier =
              Modifier.weight(1f)
                .tactilePress(
                  interactionSource = filterInteraction,
                  baseCorner = 14.dp,
                  pressedCorner = 22.dp,
                  enableHapticOnPress = false,
                )
                .testTag("filter_chip_${filter.name}"),
          ) {
            Text(
              text = filter.label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(vertical = 10.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun OverviewStatTile(
  count: Int,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
  ) {
    RollingNumberText(
      text = "$count",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EmptyQuickActionsSection(
  hasSearchOrFilter: Boolean,
  onCreateChecklist: () -> Unit,
  onCreateNote: () -> Unit,
  onCreateVoiceMemo: () -> Unit,
  onImportFile: () -> Unit,
  onResetFilter: () -> Unit,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("empty_workbench_card"),
  ) {
    if (hasSearchOrFilter) {
      TactKitCard(
        corner = 22.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(16.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "没有匹配的桌面内容",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "尝试清除搜索关键词或查看全部分类",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onResetFilter) { Text("查看全部") }
        }
      }
    } else {
      QuickCreateListSection(
        onCreateChecklist = onCreateChecklist,
        onCreateNote = onCreateNote,
        onCreateVoiceMemo = onCreateVoiceMemo,
        onImportFile = onImportFile,
      )
    }
  }
}

@Composable
private fun QuickCreateListSection(
  onCreateChecklist: () -> Unit,
  onCreateNote: () -> Unit,
  onCreateVoiceMemo: () -> Unit,
  onImportFile: () -> Unit,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = "快速开始",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
    ImageToolboxActionCard(
      icon = Icons.Rounded.AddTask,
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      title = "新建清单",
      subtitle = "创建可勾选的日常任务或发版检查项",
      onClick = onCreateChecklist,
      testTag = "create_checklist_button",
    )
    ImageToolboxActionCard(
      icon = Icons.Rounded.EditNote,
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      title = "新建便签",
      subtitle = "支持 Markdown 标题、表格、任务列表与代码块渲染",
      onClick = onCreateNote,
      testTag = "create_note_button",
    )
    ImageToolboxActionCard(
      icon = Icons.Rounded.Mic,
      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      title = "录制语音备忘",
      subtitle = "原生 AAC 录音并直接在桌面卡片中回放",
      onClick = onCreateVoiceMemo,
      testTag = "create_voice_memo_button",
    )
    ImageToolboxActionCard(
      icon = Icons.Rounded.FolderOpen,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
      title = "导入本地文件",
      subtitle = "选取 PDF、图片、音视频或文档直达对应工坊",
      onClick = onImportFile,
      testTag = "import_local_file_button",
    )
  }
}

@Composable
private fun ImageToolboxActionCard(
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  TactileCard(
    onClick = onClick,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    baseCorner = 22.dp,
    pressedCorner = 26.dp,
    pressedScale = 0.95f,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(14.dp),
    ) {
      PetalIconBadge(
        icon = icon,
        size = 44.dp,
        iconSize = 21.dp,
        petals = 8,
        containerColor = containerColor,
        contentColor = contentColor,
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
      )
    }
  }
}

@Composable
private fun CreateChecklistDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, List<String>) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var rawItems by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("新建可勾选清单") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("清单标题") },
          placeholder = { Text("例如：发版前检查项") },
          singleLine = true,
          shape = themeShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_checklist_title"),
        )
        OutlinedTextField(
          value = rawItems,
          onValueChange = { rawItems = it },
          label = { Text("待办条目（每行一条）") },
          placeholder = { Text("校验 SHA-256\n导出 release APK\n备份配置文件") },
          minLines = 4,
          maxLines = 8,
          shape = themeShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_checklist_items"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val lines = rawItems.lines().map { it.trim() }.filter { it.isNotEmpty() }
          onConfirm(title, lines)
        },
        modifier = Modifier.testTag("dialog_checklist_save"),
      ) {
        Text("创建")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
private fun NoteEditorDialog(
  initialTitle: String,
  initialContent: String,
  isEditing: Boolean,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var title by remember(initialTitle) { mutableStateOf(initialTitle) }
  var content by remember(initialContent) { mutableStateOf(initialContent) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isEditing) "编辑 Markdown / 代码便签" else "新建 Markdown / 代码便签") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("便签标题") },
          placeholder = { Text("例如：Gradle 防冲突备忘") },
          singleLine = true,
          shape = themeShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_note_title"),
        )
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text("Markdown / 代码片段") },
          placeholder = { Text("# 标题\n- [x] 支持 GFM 表格与任务列表\n```kotlin\nprintln(\"TactKit\")\n```") },
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
          minLines = 6,
          maxLines = 12,
          shape = themeShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_note_content"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(title, content) },
        modifier = Modifier.testTag("dialog_note_save"),
      ) {
        Text("保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
private fun DocumentDashboardCard(
  stats: DocumentDashboardStats,
  onOpenRecentFile: (WorkbenchCard.RecentFile) -> Unit,
  onOpenFileManager: () -> Unit,
  onFileManagerButtonPositioned: (Rect) -> Unit = {},
) {
  val themeStyle = com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current
  val isDark = MaterialTheme.colorScheme.background.red < 0.2f
  val isMinecraft = themeStyle == com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
  val cardCorner =
    when (themeStyle) {
      com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL -> 0.dp
      com.xialia.tactkit.data.datastore.AppThemeStyle.PRECISION_LAB -> 16.dp
      else -> 22.dp
    }
  val cardBorder =
    com.xialia.tactkit.ui.theme.themeCardBorder(
      themeStyle,
      isDark,
      MaterialTheme.colorScheme.outlineVariant,
    )

  Surface(
    shape = if (isMinecraft) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(cardCorner),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = cardBorder,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier =
      Modifier.fillMaxWidth()
        .let {
          if (isMinecraft) it.minecraftPixelBevel(isDark = isDark)
          else it
        }
        .testTag("document_dashboard_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "文档统计仪表盘",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "累计管理 ${stats.totalItemsCount} 项 · 本周 +${stats.thisWeekAddedCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isMinecraft) {
          OreUiButton(
            onClick = onOpenFileManager,
            tone = OreUiTone.GREEN,
            modifier =
              Modifier.onGloballyPositioned { coords ->
                  onFileManagerButtonPositioned(coords.boundsInRoot())
                }
                .testTag("dashboard_open_file_manager"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "文件管理",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
              )
            }
          }
        } else {
          Surface(
            onClick = onOpenFileManager,
            shape = ContinuousCapsule(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier =
              Modifier.onGloballyPositioned { coords ->
                  onFileManagerButtonPositioned(coords.boundsInRoot())
                }
                .testTag("dashboard_open_file_manager"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "文件管理",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }
      }

      val totalCat = (stats.pdfCount + stats.docTextCount + stats.imageCount + stats.mediaArchiveCount).coerceAtLeast(1)
      val pdfColor = MaterialTheme.colorScheme.primary
      val docColor = MaterialTheme.colorScheme.secondary
      val imgColor = MaterialTheme.colorScheme.tertiary
      val mediaColor = MaterialTheme.colorScheme.outlineVariant

      if (isMinecraft) {
        val ratio = if (stats.totalItemsCount == 0) 0f else (stats.pdfCount + stats.docTextCount + stats.imageCount).toFloat() / totalCat.toFloat()
        com.xialia.tactkit.ui.theme.McXpBar(progress = ratio)
      } else {
        Row(
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          modifier = Modifier.fillMaxWidth().height(8.dp),
        ) {
          val barSegmentShape = themeShape(999.dp)
          if (stats.totalItemsCount == 0) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh, barSegmentShape),
            )
          } else {
            if (stats.pdfCount > 0) {
              Box(
                modifier =
                  Modifier.weight(stats.pdfCount.toFloat() / totalCat)
                    .height(8.dp)
                    .background(pdfColor, barSegmentShape),
              )
            }
            if (stats.docTextCount > 0) {
              Box(
                modifier =
                  Modifier.weight(stats.docTextCount.toFloat() / totalCat)
                    .height(8.dp)
                    .background(docColor, barSegmentShape),
              )
            }
            if (stats.imageCount > 0) {
              Box(
                modifier =
                  Modifier.weight(stats.imageCount.toFloat() / totalCat)
                    .height(8.dp)
                    .background(imgColor, barSegmentShape),
              )
            }
            if (stats.mediaArchiveCount > 0) {
              Box(
                modifier =
                  Modifier.weight(stats.mediaArchiveCount.toFloat() / totalCat)
                    .height(8.dp)
                    .background(mediaColor, barSegmentShape),
              )
            }
          }
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        DashboardLegendItem("PDF ${stats.pdfCount}", pdfColor)
        DashboardLegendItem("文档 ${stats.docTextCount}", docColor)
        DashboardLegendItem("图片 ${stats.imageCount}", imgColor)
        DashboardLegendItem("音视频 ${stats.mediaArchiveCount}", mediaColor)
      }

      if (stats.recentFiles.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "最近处理文件",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          stats.recentFiles.forEach { file ->
            Surface(
              onClick = { onOpenRecentFile(file) },
              shape = if (isMinecraft) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier =
                Modifier.fillMaxWidth()
                  .let { if (isMinecraft) it.minecraftPixelBevel(isDark = isDark) else it }
                  .testTag("dashboard_recent_${file.id}"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              ) {
                Text(
                  text = file.fileName,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = com.xialia.tactkit.util.FileInspectionHelper.formatBytes(file.fileSizeBytes),
                  fontFamily = TactKitMono,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DashboardLegendItem(label: String, dotColor: Color) {
  val isMinecraft =
    com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current ==
      com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier =
        Modifier.size(8.dp)
          .background(
            dotColor,
            if (isMinecraft) androidx.compose.ui.graphics.RectangleShape else ContinuousCapsule(),
          ),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      softWrap = false,
    )
  }
}

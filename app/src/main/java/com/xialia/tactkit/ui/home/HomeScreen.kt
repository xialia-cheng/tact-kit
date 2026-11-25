package com.xialia.tactkit.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.xialia.tactkit.R
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.domain.ToolItem
import com.xialia.tactkit.ui.components.LiquidGlassToolBallOverlay
import com.xialia.tactkit.ui.components.WorkbenchCardView
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: HomeUiState,
  onSearchQueryChange: (String) -> Unit,
  onFilterChange: (CardFilter) -> Unit,
  onCreateChecklist: (String, List<String>) -> Unit,
  onToggleChecklistItem: (Long, String) -> Unit,
  onAddChecklistItem: (Long, String) -> Unit,
  onDeleteChecklistItem: (Long, String) -> Unit,
  onCreateNote: (String, String) -> Unit,
  onUpdateNote: (Long, String, String) -> Unit,
  onTogglePinCard: (Long) -> Unit,
  onDeleteCard: (Long) -> Unit,
  onImportLocalFile: (Uri) -> Unit,
  onTogglePinTool: (String) -> Unit,
  onClearStatusMessage: () -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val snackbarHostState = remember { SnackbarHostState() }

  var isSearchBarVisible by remember { mutableStateOf(false) }
  var showCreateChecklistDialog by remember { mutableStateOf(false) }
  var editingNote by remember { mutableStateOf<WorkbenchCard.Note?>(null) }
  var showCreateNoteDialog by remember { mutableStateOf(false) }

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

  val backgroundColor = MaterialTheme.colorScheme.background
  val backdrop = rememberLayerBackdrop { drawRect(backgroundColor) }

  Box(modifier = Modifier.fillMaxSize()) {
    // Captured layer backdrop for Liquid Glass Tool Ball
    Box(
      modifier =
        Modifier.fillMaxSize()
          .layerBackdrop(backdrop)
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                  MaterialTheme.colorScheme.background,
                  MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f),
                )
            )
          )
    ) {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "简工坊 · TactKit",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = ContinuousCapsule(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Shield,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = stringResource(R.string.local_first_badge),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
            },
            actions = {
              IconButton(
                onClick = {
                  haptics.tick()
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
                  haptics.tick()
                  onNavigateToSettings()
                },
                modifier = Modifier.testTag("settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Settings,
                  contentDescription = stringResource(R.string.cd_settings),
                )
              }
            },
          )
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
              placeholder = { Text("筛选标题、待办内容、Markdown 或文件名...") },
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
              shape = ContinuousRoundedRectangle(18.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp)
                  .testTag("home_search_input"),
            )
          }

          // Quick Creation + Filter Strip
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            item(key = "action_checklist") {
              FilledTonalButton(
                onClick = {
                  haptics.tick()
                  showCreateChecklistDialog = true
                },
                shape = ContinuousCapsule(),
                modifier = Modifier.testTag("create_checklist_button"),
              ) {
                Icon(
                  Icons.Rounded.AddTask,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("新建清单")
              }
            }
            item(key = "action_note") {
              FilledTonalButton(
                onClick = {
                  haptics.tick()
                  showCreateNoteDialog = true
                },
                shape = ContinuousCapsule(),
                modifier = Modifier.testTag("create_note_button"),
              ) {
                Icon(
                  Icons.Rounded.EditNote,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("新建便签")
              }
            }
            item(key = "action_import") {
              FilledTonalButton(
                onClick = {
                  haptics.tick()
                  safImportLauncher.launch(arrayOf("*/*"))
                },
                shape = ContinuousCapsule(),
                modifier = Modifier.testTag("import_local_file_button"),
              ) {
                Icon(
                  Icons.Rounded.FolderOpen,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("导入本地文件")
              }
            }
          }

          // Filter Chips
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(CardFilter.entries) { filter ->
              FilterChip(
                selected = uiState.activeFilter == filter,
                onClick = {
                  haptics.tick()
                  onFilterChange(filter)
                },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("filter_chip_${filter.name}"),
              )
            }
          }

          // Hybrid Card Stream or Empty State
          if (uiState.cards.isEmpty()) {
            EmptyWorkbenchState(
              hasSearchOrFilter =
                uiState.searchQuery.isNotBlank() || uiState.activeFilter != CardFilter.ALL,
              onCreateChecklist = {
                haptics.tick()
                showCreateChecklistDialog = true
              },
              onCreateNote = {
                haptics.tick()
                showCreateNoteDialog = true
              },
              onImportFile = {
                haptics.tick()
                safImportLauncher.launch(arrayOf("*/*"))
              },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          } else {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
              val isWide = maxWidth >= 640.dp
              val openQuickLook: (WorkbenchCard.RecentFile) -> Unit = { fileCard ->
                val uriStr = fileCard.fileUri
                if (uriStr != null) {
                  val intent =
                    Intent(context, QuickLookActivity::class.java).apply {
                      action = Intent.ACTION_VIEW
                      data = Uri.parse(uriStr)
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                  context.startActivity(intent)
                }
              }

              if (isWide) {
                LazyVerticalGrid(
                  columns = GridCells.Fixed(2),
                  contentPadding =
                    PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.fillMaxSize(),
                ) {
                  items(uiState.cards, key = { it.id }) { card ->
                    WorkbenchCardView(
                      card = card,
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenFileQuickLook = openQuickLook,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
                    )
                  }
                }
              } else {
                LazyColumn(
                  contentPadding =
                    PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.fillMaxSize(),
                ) {
                  items(uiState.cards, key = { it.id }) { card ->
                    WorkbenchCardView(
                      card = card,
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenFileQuickLook = openQuickLook,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
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
      onLaunchTool = { tool: ToolItem ->
        when (tool.id) {
          "quick_note" -> showCreateNoteDialog = true
          "quick_checklist" -> showCreateChecklistDialog = true
          "quick_look" -> quickLookLauncher.launch(arrayOf("*/*"))
        }
      },
      onTogglePinTool = onTogglePinTool,
    )
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
}

@Composable
private fun EmptyWorkbenchState(
  hasSearchOrFilter: Boolean,
  onCreateChecklist: () -> Unit,
  onCreateNote: () -> Unit,
  onImportFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
  ) {
    Surface(
      shape = ContinuousRoundedRectangle(26.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      border =
        BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("empty_workbench_card"),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
      ) {
        if (!hasSearchOrFilter) {
          Image(
            painter = painterResource(id = R.drawable.img_workbench_empty),
            contentDescription = stringResource(R.string.cd_empty_workbench),
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .height(168.dp)
                .clip(ContinuousRoundedRectangle(18.dp)),
          )
          Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
          text = if (hasSearchOrFilter) "没有匹配的桌面卡片" else "你的个人本地工作台已就绪",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            if (hasSearchOrFilter) {
              "试试更换搜索词或切换回「全部」筛选。"
            } else {
              "所有清单、Markdown 便签与本地文件均保存在设备 Room 数据库中，零上传、零追踪。点击下方按钮或右下角液态玻璃工具球开始使用。"
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasSearchOrFilter) {
          Spacer(modifier = Modifier.height(18.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Button(
              onClick = onCreateChecklist,
              shape = ContinuousCapsule(),
              modifier = Modifier.weight(1f),
            ) {
              Text("创建清单")
            }
            FilledTonalButton(
              onClick = onCreateNote,
              shape = ContinuousCapsule(),
              modifier = Modifier.weight(1f),
            ) {
              Text("记一条便签")
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          TextButton(onClick = onImportFile) {
            Text("通过系统文件选择器 (SAF) 导入本地文件")
          }
        }
      }
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
          shape = ContinuousRoundedRectangle(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_checklist_title"),
        )
        OutlinedTextField(
          value = rawItems,
          onValueChange = { rawItems = it },
          label = { Text("待办条目（每行一条）") },
          placeholder = { Text("校验 SHA-256\n导出 release APK\n备份配置文件") },
          minLines = 4,
          maxLines = 8,
          shape = ContinuousRoundedRectangle(14.dp),
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
          shape = ContinuousRoundedRectangle(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_note_title"),
        )
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text("Markdown / 代码片段") },
          placeholder = { Text("# 标题\n- 支持等宽预览与快速编辑") },
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
          minLines = 6,
          maxLines = 12,
          shape = ContinuousRoundedRectangle(14.dp),
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

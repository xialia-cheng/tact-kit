package com.xialia.tactkit.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.xialia.tactkit.BuildConfig
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

  val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLow
  val backdrop = rememberLayerBackdrop { drawRect(surfaceBg) }

  Box(modifier = Modifier.fillMaxSize()) {
    // Clean flat M3 Expressive surface layer captured for Liquid Glass
    Box(
      modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(surfaceBg),
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
                  text = "简工坊",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = ContinuousCapsule(),
                ) {
                  Text(
                    text = "v${BuildConfig.VERSION_NAME} ✨",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                  )
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
              placeholder = { Text("搜索标题、清单项、便签或文件名...") },
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
              shape = RoundedCornerShape(16.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp)
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
          val openFileImporter = {
            haptics.tick()
            safImportLauncher.launch(arrayOf("*/*"))
          }
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

          BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 640.dp

            if (isWide) {
              LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding =
                  PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                item(span = { GridItemSpan(2) }, key = "overview_header") {
                  WorkbenchOverviewSection(
                    uiState = uiState,
                    onFilterChange = {
                      haptics.tick()
                      onFilterChange(it)
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
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenFileQuickLook = openQuickLook,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
                    )
                  }
                  item(span = { GridItemSpan(2) }, key = "footer_quick_actions") {
                    QuickCreateListSection(
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
                      onImportFile = openFileImporter,
                    )
                  }
                }
              }
            } else {
              LazyColumn(
                contentPadding =
                  PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                item(key = "overview_header") {
                  WorkbenchOverviewSection(
                    uiState = uiState,
                    onFilterChange = {
                      haptics.tick()
                      onFilterChange(it)
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
                      onToggleChecklistItem = onToggleChecklistItem,
                      onAddChecklistItem = onAddChecklistItem,
                      onDeleteChecklistItem = onDeleteChecklistItem,
                      onEditNote = { editingNote = it },
                      onOpenFileQuickLook = openQuickLook,
                      onTogglePin = onTogglePinCard,
                      onDeleteCard = onDeleteCard,
                    )
                  }
                  item(key = "footer_quick_actions") {
                    QuickCreateListSection(
                      onCreateChecklist = openChecklistCreator,
                      onCreateNote = openNoteCreator,
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
private fun WorkbenchOverviewSection(
  uiState: HomeUiState,
  onFilterChange: (CardFilter) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Overview Card
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      border =
        BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
      modifier = Modifier.fillMaxWidth().testTag("workbench_overview_card"),
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
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = ContinuousCapsule(),
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

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            count = uiState.totalFiles,
            label = "文件",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Aligned Filter Tabs (flush with 16.dp list margin)
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      CardFilter.entries.forEach { filter ->
        val selected = uiState.activeFilter == filter
        Surface(
          onClick = { onFilterChange(filter) },
          shape = RoundedCornerShape(14.dp),
          color =
            if (selected) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surface
            },
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (selected) {
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                },
            ),
          modifier = Modifier.weight(1f).testTag("filter_chip_${filter.name}"),
        ) {
          Text(
            text = filter.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color =
              if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 10.dp),
          )
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
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
      Text(
        text = "$count",
        style = MaterialTheme.typography.titleLarge,
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
}

@Composable
private fun EmptyQuickActionsSection(
  hasSearchOrFilter: Boolean,
  onCreateChecklist: () -> Unit,
  onCreateNote: () -> Unit,
  onImportFile: () -> Unit,
  onResetFilter: () -> Unit,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("empty_workbench_card"),
  ) {
    if (hasSearchOrFilter) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
          BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
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
        onImportFile = onImportFile,
      )
    }
  }
}

@Composable
private fun QuickCreateListSection(
  onCreateChecklist: () -> Unit,
  onCreateNote: () -> Unit,
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
      subtitle = "记录纯文本、Markdown 备忘或代码片段",
      onClick = onCreateNote,
      testTag = "create_note_button",
    )
    ImageToolboxActionCard(
      icon = Icons.Rounded.FolderOpen,
      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      title = "导入本地文件",
      subtitle = "从设备选取文档或图片并生成本地摘要指纹",
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
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
          )
        }
      }
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
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("dialog_checklist_title"),
        )
        OutlinedTextField(
          value = rawItems,
          onValueChange = { rawItems = it },
          label = { Text("待办条目（每行一条）") },
          placeholder = { Text("校验 SHA-256\n导出 release APK\n备份配置文件") },
          minLines = 4,
          maxLines = 8,
          shape = RoundedCornerShape(14.dp),
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
          shape = RoundedCornerShape(14.dp),
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
          shape = RoundedCornerShape(14.dp),
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

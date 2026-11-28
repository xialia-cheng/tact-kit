package com.xialia.tactkit.ui.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.FileCacheRepository
import com.xialia.tactkit.engine.DirectoryListing
import com.xialia.tactkit.engine.DirectorySortBy
import com.xialia.tactkit.engine.FileCategory
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.engine.MessengerDirHint
import com.xialia.tactkit.engine.SpaceScanResult
import com.xialia.tactkit.engine.StorageOverview
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.ui.theme.OreUiSegmentedTabRow
import com.xialia.tactkit.ui.theme.isMcPixelTheme
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FileManagerMode(val label: String) {
  RECENT("最近文件"),
  TREE("目录树"),
  CLEANUP("空间清理"),
}

fun hasAllFilesAccessPermission(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
  } else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
  }
}

@Composable
fun FileManagerScreen(
  preferences: UserPreferences,
  onTogglePinnedDir: (String) -> Unit,
  onAddLinkedSafTreeUri: (String) -> Unit,
  onLaunchTool: (uriString: String, launch: TactKitToolLaunch) -> Unit,
  onPreviewImage: ((uriString: String, title: String, sharedKey: String) -> Unit)? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  PredictiveBackContainer(onBack = onNavigateBack) {
    FileManagerWorkspace(
      preferences = preferences,
      isPickerMode = false,
      allowMultiplePick = false,
      onTogglePinnedDir = onTogglePinnedDir,
      onAddLinkedSafTreeUri = onAddLinkedSafTreeUri,
      onLaunchTool = { entry, launch ->
        val uri = FileManagerEngine.toShareableUri(context, entry)
        if (uri == null) {
          Toast.makeText(context, "无法读取文件路径", Toast.LENGTH_SHORT).show()
        } else if (launch == TactKitToolLaunch.QUICK_LOOK) {
          val intent =
            Intent(context, QuickLookActivity::class.java).apply {
              action = Intent.ACTION_VIEW
              data = uri
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
          context.startActivity(intent)
        } else {
          onLaunchTool(uri.toString(), launch)
        }
      },
      onPreviewImage = onPreviewImage,
      onConfirmPick = {},
      onNavigateBack = onNavigateBack,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerWorkspace(
  preferences: UserPreferences,
  isPickerMode: Boolean,
  allowMultiplePick: Boolean,
  onTogglePinnedDir: (String) -> Unit,
  onAddLinkedSafTreeUri: (String) -> Unit,
  onLaunchTool: (ManagedFileEntry, TactKitToolLaunch) -> Unit,
  onConfirmPick: (List<ManagedFileEntry>) -> Unit,
  onNavigateBack: () -> Unit,
  onPreviewImage: ((uriString: String, title: String, sharedKey: String) -> Unit)? = null,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current

  val rootDir = remember { FileManagerEngine.defaultRootDir() }
  var hasPermission by remember { mutableStateOf(hasAllFilesAccessPermission(context)) }
  var refreshTick by remember { mutableIntStateOf(0) }

  var mode by remember { mutableStateOf(FileManagerMode.RECENT) }
  var isSearchVisible by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var searchResults by remember { mutableStateOf<List<ManagedFileEntry>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }

  // Recent tab state (backed by application-scoped FileCacheRepository for 0ms re-entry)
  var showHidden by remember { mutableStateOf(false) }
  val initialRecentKey =
    remember {
      FileCacheRepository.RecentCacheKey(
        hasPermission = hasPermission,
        linkedSafTreeUris = preferences.linkedSafTreeUris,
        includeHidden = showHidden,
      )
    }
  var recentFiles by remember {
    mutableStateOf(
      FileCacheRepository.getRecentIfFresh(initialRecentKey) ?: FileCacheRepository.peekRecent()
    )
  }
  var isScanningRecent by remember { mutableStateOf(false) }
  var recentScanJob by remember { mutableStateOf<Job?>(null) }
  var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }

  // Directory tree state
  var currentDir by remember { mutableStateOf(rootDir) }
  var directoryListing by remember {
    mutableStateOf(DirectoryListing(rootDir, "", emptyList(), emptyList()))
  }
  var smartNoiseReduction by remember { mutableStateOf(true) }
  var isGridMode by remember { mutableStateOf(false) }
  var sortBy by remember { mutableStateOf(DirectorySortBy.NAME) }

  // Cleanup tab state
  var spaceScanResult by remember { mutableStateOf<SpaceScanResult?>(null) }
  var isScanningSpace by remember { mutableStateOf(false) }
  var spaceScanJob by remember { mutableStateOf<Job?>(null) }
  var cleanupCheckedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var showCleanupDeleteDialog by remember { mutableStateOf(false) }

  // Selection & Dialogs
  var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var inspectedEntry by remember { mutableStateOf<ManagedFileEntry?>(null) }
  var showNewFolderDialog by remember { mutableStateOf(false) }
  var renameTargets by remember { mutableStateOf<List<ManagedFileEntry>?>(null) }
  var packZipTargets by remember { mutableStateOf<List<ManagedFileEntry>?>(null) }
  var deleteTargets by remember { mutableStateOf<List<ManagedFileEntry>?>(null) }
  var transferTargets by remember { mutableStateOf<Pair<Boolean, List<ManagedFileEntry>>?>(null) } // isMove to entries

  val initialStorageSnapshot = remember { FileCacheRepository.getStorageSnapshot() }
  var storageOverview by remember {
    mutableStateOf(initialStorageSnapshot.first ?: StorageOverview(0L, 0L, 0L))
  }
  var messengerDirs by remember { mutableStateOf(initialStorageSnapshot.second.orEmpty()) }

  val legacyPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      hasPermission = hasAllFilesAccessPermission(context)
      FileCacheRepository.markDirty()
      refreshTick++
    }

  val safTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      if (treeUri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onAddLinkedSafTreeUri(treeUri.toString())
        haptics.confirm()
        FileCacheRepository.markDirty()
        refreshTick++
      }
    }

  DisposableEffect(lifecycleOwner) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          val nowGranted = hasAllFilesAccessPermission(context)
          if (nowGranted != hasPermission) {
            hasPermission = nowGranted
            FileCacheRepository.markDirty()
          }
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(hasPermission, refreshTick, preferences.linkedSafTreeUris, showHidden) {
    val currentCacheKey =
      FileCacheRepository.RecentCacheKey(
        hasPermission = hasPermission,
        linkedSafTreeUris = preferences.linkedSafTreeUris,
        includeHidden = showHidden,
      )
    if (hasPermission && (!FileCacheRepository.isStorageSnapshotFresh() || refreshTick > 0)) {
      val (overview, dirs) =
        withContext(Dispatchers.IO) {
          FileManagerEngine.readStorageOverview(rootDir) to FileManagerEngine.detectMessengerDirs(rootDir)
        }
      FileCacheRepository.updateStorageSnapshot(overview, dirs)
      storageOverview = overview
      messengerDirs = dirs
    }
    recentScanJob?.cancel()
    if (hasPermission || preferences.linkedSafTreeUris.isNotEmpty()) {
      val cachedRecent =
        if (refreshTick == 0) FileCacheRepository.getRecentIfFresh(currentCacheKey) else null
      if (cachedRecent != null) {
        recentFiles = cachedRecent
        isScanningRecent = false
      } else {
        if (recentFiles.isEmpty()) {
          val stale = FileCacheRepository.peekRecent()
          if (stale.isNotEmpty()) {
            recentFiles = stale
          }
        }
        val job =
          scope.launch {
            isScanningRecent = true
            try {
              val scanned =
                FileManagerEngine.scanRecentFiles(
                  context = context,
                  rootDir = rootDir,
                  linkedSafTreeUris = preferences.linkedSafTreeUris,
                  limit = 700,
                  includeHidden = showHidden,
                )
              FileCacheRepository.updateRecent(currentCacheKey, scanned)
              recentFiles = scanned
            } finally {
              isScanningRecent = false
            }
          }
        recentScanJob = job
      }
    } else {
      recentFiles = emptyList()
      isScanningRecent = false
      FileCacheRepository.clear()
    }
  }

  LaunchedEffect(hasPermission, currentDir, showHidden, smartNoiseReduction, sortBy, refreshTick) {
    if (hasPermission) {
      directoryListing =
        FileManagerEngine.listDirectory(
          dir = currentDir,
          rootDir = rootDir,
          showHidden = showHidden,
          sortBy = sortBy,
          smartNoiseReduction = smartNoiseReduction,
        )
    }
  }

  LaunchedEffect(searchQuery, refreshTick, hasPermission, preferences.linkedSafTreeUris) {
    val q = searchQuery.trim()
    if (q.isEmpty()) {
      searchResults = emptyList()
      isSearching = false
    } else {
      isSearching = true
      delay(160)
      searchResults =
        FileManagerEngine.searchFiles(
          context = context,
          query = q,
          rootDir = rootDir,
          linkedSafTreeUris = preferences.linkedSafTreeUris,
          includeHidden = showHidden,
        )
      isSearching = false
    }
  }

  BackHandler(enabled = selectedIds.isNotEmpty() || (mode == FileManagerMode.TREE && currentDir.absolutePath != rootDir.absolutePath)) {
    when {
      selectedIds.isNotEmpty() -> selectedIds = emptySet()
      mode == FileManagerMode.TREE && currentDir.absolutePath != rootDir.absolutePath -> {
        currentDir = currentDir.parentFile ?: rootDir
      }
    }
  }

  fun openSystemStoragePermissionSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val intents =
        listOf(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
          },
          Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
          },
        )
      for (candidate in intents) {
        val launched = runCatching { context.startActivity(candidate) }.isSuccess
        if (launched) break
      }
    } else {
      legacyPermLauncher.launch(
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
      )
    }
  }

  fun jumpToPathInTree(relativePath: String) {
    val target = File(rootDir, relativePath)
    if (target.exists() && target.isDirectory) {
      currentDir = target
      mode = FileManagerMode.TREE
      searchQuery = ""
      selectedIds = emptySet()
    } else {
      Toast.makeText(context, "目录尚未创建：/$relativePath", Toast.LENGTH_SHORT).show()
    }
  }

  fun locateEntryDirectory(entry: ManagedFileEntry) {
    val file = entry.absolutePath?.let(::File) ?: return
    val parent = if (file.isDirectory) file else file.parentFile ?: rootDir
    if (parent.exists() && parent.isDirectory) {
      currentDir = parent
      mode = FileManagerMode.TREE
      searchQuery = ""
    }
  }

  fun shareEntriesSystem(entries: List<ManagedFileEntry>) {
    val uris = entries.mapNotNull { FileManagerEngine.toShareableUri(context, it) }
    if (uris.isEmpty()) return
    val intent =
      if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
          type = entries.first().mimeType.ifBlank { "*/*" }
          putExtra(Intent.EXTRA_STREAM, uris.first())
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "*/*"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      }
    context.startActivity(Intent.createChooser(intent, "分享文件"))
  }

  fun handleEntryClick(entry: ManagedFileEntry) {
    if (selectedIds.isNotEmpty()) {
      selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
      haptics.tick()
      return
    }
    if (entry.isDirectory && entry.absolutePath != null) {
      currentDir = File(entry.absolutePath)
      haptics.tick()
      return
    }
    if (isPickerMode) {
      if (allowMultiplePick) {
        selectedIds = selectedIds + entry.id
        haptics.tick()
      } else {
        onConfirmPick(listOf(entry))
      }
    } else {
      inspectedEntry = entry
      haptics.tick()
    }
  }

  fun previewImageEntry(entry: ManagedFileEntry) {
    if (selectedIds.isNotEmpty() || isPickerMode) {
      handleEntryClick(entry)
      return
    }
    val uri = FileManagerEngine.toShareableUri(context, entry)
    if (uri != null && onPreviewImage != null) {
      haptics.tick()
      onPreviewImage(uri.toString(), entry.name, "shared_img_fm_${entry.id}")
    } else {
      handleEntryClick(entry)
    }
  }

  fun handleEntryLongClick(entry: ManagedFileEntry) {
    if (entry.isSafBacked) {
      inspectedEntry = entry
      return
    }
    haptics.confirm()
    selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
  }

  val visibleEntriesForSelection: List<ManagedFileEntry> =
    when {
      searchQuery.isNotBlank() -> searchResults
      mode == FileManagerMode.RECENT -> recentFiles
      mode == FileManagerMode.TREE -> directoryListing.activeEntries
      else -> emptyList()
    }

  val selectedEntries =
    remember(selectedIds, recentFiles, directoryListing, searchResults) {
      val map = LinkedHashMap<String, ManagedFileEntry>()
      recentFiles.forEach { map[it.id] = it }
      directoryListing.activeEntries.forEach { map[it.id] = it }
      searchResults.forEach { map[it.id] = it }
      selectedIds.mapNotNull { map[it] }
    }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          ),
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
          }
        },
        title = {
          Text(
            text =
              if (selectedIds.isNotEmpty()) "已选 ${selectedIds.size} 项"
              else if (isPickerMode) "选择文件 · 简工坊"
              else "文件管理",
            fontWeight = FontWeight.Bold,
            modifier =
              if (selectedIds.isEmpty() && !isPickerMode) Modifier.tactKitToolHeroTarget("file_manager")
              else Modifier,
          )
        },
        actions = {
          if (selectedIds.isNotEmpty()) {
            IconButton(
              onClick = {
                val allSelectable = visibleEntriesForSelection.filter { !it.isSafBacked }.map { it.id }.toSet()
                selectedIds = if (selectedIds.containsAll(allSelectable)) emptySet() else allSelectable
              },
            ) {
              Icon(Icons.Rounded.SelectAll, contentDescription = "全选")
            }
            IconButton(onClick = { selectedIds = emptySet() }) {
              Icon(Icons.Rounded.Close, contentDescription = "退出多选")
            }
          } else {
            IconButton(
              onClick = {
                haptics.tick()
                isSearchVisible = !isSearchVisible
                if (!isSearchVisible) searchQuery = ""
              },
              modifier = Modifier.testTag("file_manager_search_toggle"),
            ) {
              Icon(
                imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = "搜索文件",
              )
            }
            IconButton(
              onClick = {
                haptics.tick()
                FileCacheRepository.markDirty()
                refreshTick++
              },
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
          }
        },
      )
    },
    bottomBar = {
      if (isPickerMode && allowMultiplePick && selectedEntries.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Text("已选 ${selectedEntries.size} 个文件", style = MaterialTheme.typography.titleSmall)
            Button(onClick = { onConfirmPick(selectedEntries.filter { !it.isDirectory }) }) {
              Text("确认返回")
            }
          }
        }
      } else if (!isPickerMode && selectedEntries.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("batch_action_bar"),
          ) {
            item(key = "batch_copy", contentType = "batch_action") {
              BatchBarAction("复制", Icons.Rounded.ContentCopy) {
                transferTargets = false to selectedEntries
              }
            }
            item(key = "batch_move", contentType = "batch_action") {
              BatchBarAction("移动", Icons.AutoMirrored.Rounded.DriveFileMove) {
                transferTargets = true to selectedEntries
              }
            }
            item(key = "batch_rename", contentType = "batch_action") {
              BatchBarAction("重命名", Icons.Rounded.DriveFileRenameOutline) {
                renameTargets = selectedEntries
              }
            }
            item(key = "batch_zip", contentType = "batch_action") {
              BatchBarAction("打包 ZIP", Icons.Rounded.FolderZip) {
                packZipTargets = selectedEntries
              }
            }
            item(key = "batch_share", contentType = "batch_action") {
              BatchBarAction("分享", Icons.Rounded.Share) {
                shareEntriesSystem(selectedEntries.filter { !it.isDirectory })
              }
            }
            item(key = "batch_delete", contentType = "batch_action") {
              BatchBarAction("删除", Icons.Rounded.DeleteOutline) {
                deleteTargets = selectedEntries
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      if (!hasPermission && preferences.linkedSafTreeUris.isEmpty()) {
        StoragePermissionOnboardingCard(
          onGrantPermission = { openSystemStoragePermissionSettings() },
          onLinkSafTree = { safTreeLauncher.launch(null) },
        )
        return@Column
      }

      // Single-line search bar expanded only when user taps TopAppBar search icon
      AnimatedVisibility(visible = isSearchVisible) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("全局实时搜索文件名或后缀（如 .jks、.pem、apk）...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
              }
            }
          },
          singleLine = true,
          shape = themeShape(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .testTag("file_manager_search"),
        )
      }

      val scrollableHeader: (@Composable () -> Unit)? =
        if (hasPermission) {
          {
            StorageOverviewAndQuickLinks(
              storage = storageOverview,
              pinnedDirs = preferences.pinnedDirs,
              messengerDirs = messengerDirs,
              linkedSafCount = preferences.linkedSafTreeUris.size,
              onOpenPinnedPath = { jumpToPathInTree(it) },
              onLinkMessengerSaf = { safTreeLauncher.launch(null) },
            )
          }
        } else {
          null
        }

      if (searchQuery.isNotBlank()) {
        Text(
          text =
            if (isSearching) "正在搜索..."
            else "跨目录找到 ${searchResults.size} 个匹配项",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          items(
            items = searchResults,
            key = { "search_${it.id}" },
            contentType = { if (it.isDirectory) "dir" else "file" },
          ) { entry ->
            val canPreview = !isPickerMode && selectedIds.isEmpty() && entry.category == FileCategory.IMAGES && onPreviewImage != null
            ManagedFileRow(
              entry = entry,
              isSelected = entry.id in selectedIds,
              selectionMode = selectedIds.isNotEmpty(),
              showLocateButton = !isPickerMode && !entry.isSafBacked,
              showRelativePath = true,
              sharedElementKey = if (entry.category == FileCategory.IMAGES) "shared_img_fm_${entry.id}" else null,
              onThumbClick = if (canPreview) ({ previewImageEntry(entry) }) else null,
              onClick = { handleEntryClick(entry) },
              onLongClick = { handleEntryLongClick(entry) },
              onLocateClick = { locateEntryDirectory(entry) },
            )
          }
        }
      } else {
        // 3 single-line top mode capsules (pinned under TopAppBar)
        val visibleModes =
          if (isPickerMode) listOf(FileManagerMode.RECENT, FileManagerMode.TREE)
          else FileManagerMode.entries
        if (isMcPixelTheme()) {
          OreUiSegmentedTabRow(
            items = visibleModes.map { it.label to (mode == it) },
            onSelect = { idx ->
              mode = visibleModes[idx]
              selectedIds = emptySet()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        } else {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(
              items = visibleModes,
              key = { it.name },
              contentType = { "mode_chip" },
            ) { m ->
              val icon =
                when (m) {
                  FileManagerMode.RECENT -> Icons.Rounded.History
                  FileManagerMode.TREE -> Icons.Rounded.FolderCopy
                  FileManagerMode.CLEANUP -> Icons.Rounded.CleaningServices
                }
              TactileModeChip(
                selected = mode == m,
                label = m.label,
                icon = icon,
                onClick = {
                  mode = m
                  selectedIds = emptySet()
                },
                modifier = Modifier.testTag("file_mode_${m.name}"),
              )
            }
          }
        }

        when (mode) {
          FileManagerMode.RECENT -> {
            RecentFilesTabContent(
              allRecentFiles = recentFiles,
              selectedCategory = selectedCategory,
              isScanning = isScanningRecent,
              isPickerMode = isPickerMode,
              selectedIds = selectedIds,
              headerContent = scrollableHeader,
              onCancelScan = {
                recentScanJob?.cancel()
                isScanningRecent = false
              },
              onSelectCategory = { selectedCategory = it },
              onEntryClick = ::handleEntryClick,
              onEntryLongClick = ::handleEntryLongClick,
              onLocateDirectory = ::locateEntryDirectory,
              onImagePreviewClick = if (onPreviewImage != null) ::previewImageEntry else null,
            )
          }

          FileManagerMode.TREE -> {
            DirectoryTreeTabContent(
              rootDir = rootDir,
              currentDir = currentDir,
              activeEntries = directoryListing.activeEntries,
              collapsedNoiseDirs = directoryListing.collapsedNoiseDirs,
              showHidden = showHidden,
              smartNoiseReduction = smartNoiseReduction,
              isGridMode = isGridMode,
              sortBy = sortBy,
              selectedIds = selectedIds,
              isPickerMode = isPickerMode,
              headerContent = scrollableHeader,
              onNavigateToDir = { dir ->
                currentDir = dir
                selectedIds = emptySet()
              },
              onToggleHidden = { showHidden = !showHidden },
              onToggleNoiseReduction = { smartNoiseReduction = !smartNoiseReduction },
              onToggleGridMode = { isGridMode = !isGridMode },
              onChangeSort = { sortBy = it },
              onCreateFolder = { showNewFolderDialog = true },
              onEntryClick = ::handleEntryClick,
              onEntryLongClick = ::handleEntryLongClick,
              onImagePreviewClick = if (onPreviewImage != null) ::previewImageEntry else null,
            )
          }

          FileManagerMode.CLEANUP -> {
            SpaceCleanupTabContent(
              scanResult = spaceScanResult,
              isScanning = isScanningSpace,
              checkedIds = cleanupCheckedIds,
              headerContent = scrollableHeader,
              onTriggerScan = {
                spaceScanJob?.cancel()
                spaceScanJob =
                  scope.launch {
                    isScanningSpace = true
                    cleanupCheckedIds = emptySet()
                    try {
                      spaceScanResult = FileManagerEngine.scanStorageWaste(rootDir)
                    } finally {
                      isScanningSpace = false
                    }
                  }
              },
              onCancelScan = {
                spaceScanJob?.cancel()
                isScanningSpace = false
              },
              onToggleChecked = { entry ->
                cleanupCheckedIds =
                  if (entry.id in cleanupCheckedIds) cleanupCheckedIds - entry.id
                  else cleanupCheckedIds + entry.id
              },
              onDeleteChecked = { showCleanupDeleteDialog = true },
            )
          }
        }
      }
    }
  }

  // Action Sheet
  inspectedEntry?.let { entry ->
    val isPinned =
      preferences.pinnedDirs.any { it.equals(entry.relativePath, ignoreCase = true) }
    FileActionSheet(
      entry = entry,
      showLocateOption = mode != FileManagerMode.TREE || searchQuery.isNotBlank(),
      isPinnedDir = isPinned,
      onDismiss = { inspectedEntry = null },
      onLaunchTool = onLaunchTool,
      onLocateInTree = ::locateEntryDirectory,
      onTogglePinDir = { target -> onTogglePinnedDir(target.relativePath) },
      onSystemShare = { target -> shareEntriesSystem(listOf(target)) },
      onPackZip = { target -> packZipTargets = listOf(target) },
      onRename = { target -> renameTargets = listOf(target) },
      onDelete = { target -> deleteTargets = listOf(target) },
    )
  }

  if (showNewFolderDialog) {
    NewFolderDialog(
      onDismiss = { showNewFolderDialog = false },
      onConfirm = { name ->
        scope.launch {
          FileManagerEngine.createFolder(currentDir, name)
            .onSuccess {
              Toast.makeText(context, "已创建文件夹：$name", Toast.LENGTH_SHORT).show()
              refreshTick++
            }
            .onFailure {
              Toast.makeText(context, it.message ?: "创建失败", Toast.LENGTH_SHORT).show()
            }
          showNewFolderDialog = false
        }
      },
    )
  }

  renameTargets?.let { targets ->
    BatchRenameDialog(
      entries = targets,
      onDismiss = { renameTargets = null },
      onConfirm = { rule ->
        scope.launch {
          FileManagerEngine.batchRename(targets, rule)
            .onSuccess { cnt ->
              Toast.makeText(context, "已重命名 $cnt 项", Toast.LENGTH_SHORT).show()
              selectedIds = emptySet()
              refreshTick++
            }
            .onFailure {
              Toast.makeText(context, it.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
            }
          renameTargets = null
        }
      },
    )
  }

  packZipTargets?.let { targets ->
    val defaultZip =
      if (targets.size == 1) "${targets.first().name.substringBeforeLast('.')}.zip"
      else "TactKit_${targets.size}_items.zip"
    PackZipDialog(
      itemCount = targets.size,
      defaultName = defaultZip,
      onDismiss = { packZipTargets = null },
      onConfirm = { zipFileName, password ->
        scope.launch {
          val targetDir = if (mode == FileManagerMode.TREE) currentDir else File(rootDir, "Download").apply { mkdirs() }
          val outFile = File(targetDir, zipFileName)
          FileManagerEngine.packEntriesToZip(targets, outFile, password)
            .onSuccess {
              Toast.makeText(context, "已打包至：${it.name}", Toast.LENGTH_SHORT).show()
              selectedIds = emptySet()
              refreshTick++
            }
            .onFailure {
              Toast.makeText(context, it.message ?: "打包失败", Toast.LENGTH_SHORT).show()
            }
          packZipTargets = null
        }
      },
    )
  }

  transferTargets?.let { (isMove, targets) ->
    DirectoryTargetPickerDialog(
      title = if (isMove) "移动到目标目录" else "复制到目标目录",
      rootDir = rootDir,
      initialDir = currentDir,
      onDismiss = { transferTargets = null },
      onConfirmTarget = { destDir ->
        scope.launch {
          val result =
            if (isMove) FileManagerEngine.moveEntries(targets, destDir)
            else FileManagerEngine.copyEntries(targets, destDir)
          result
            .onSuccess { cnt ->
              Toast.makeText(context, "${if (isMove) "已移动" else "已复制"} $cnt 项", Toast.LENGTH_SHORT).show()
              selectedIds = emptySet()
              refreshTick++
            }
            .onFailure {
              Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
          transferTargets = null
        }
      },
    )
  }

  deleteTargets?.let { targets ->
    ConfirmDeleteDialog(
      count = targets.size,
      sampleName = targets.firstOrNull()?.name.orEmpty(),
      onDismiss = { deleteTargets = null },
      onConfirm = {
        scope.launch {
          FileManagerEngine.deleteEntries(context, targets)
            .onSuccess { cnt ->
              Toast.makeText(context, "已删除 $cnt 项", Toast.LENGTH_SHORT).show()
              selectedIds = emptySet()
              refreshTick++
            }
            .onFailure {
              Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show()
            }
          deleteTargets = null
        }
      },
    )
  }

  if (showCleanupDeleteDialog) {
    val allTargets =
      (spaceScanResult?.largeFilesOver50Mb.orEmpty() + spaceScanResult?.emptyDirectories.orEmpty())
        .filter { it.id in cleanupCheckedIds }
    ConfirmDeleteDialog(
      count = allTargets.size,
      sampleName = allTargets.firstOrNull()?.name.orEmpty(),
      onDismiss = { showCleanupDeleteDialog = false },
      onConfirm = {
        scope.launch {
          FileManagerEngine.deleteEntries(context, allTargets)
            .onSuccess { cnt ->
              Toast.makeText(context, "已清理 $cnt 项", Toast.LENGTH_SHORT).show()
              cleanupCheckedIds = emptySet()
              isScanningSpace = true
              spaceScanResult = FileManagerEngine.scanStorageWaste(rootDir)
              isScanningSpace = false
              refreshTick++
            }
          showCleanupDeleteDialog = false
        }
      },
    )
  }
}

@Composable
private fun BatchBarAction(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = themeShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

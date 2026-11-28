package com.xialia.tactkit.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.xialia.tactkit.engine.DirectorySortBy
import com.xialia.tactkit.engine.FileCategory
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.engine.MessengerDirHint
import com.xialia.tactkit.engine.SpaceScanResult
import com.xialia.tactkit.engine.StorageOverview
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import com.xialia.tactkit.ui.components.TactKitThemedProgressBar
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.theme.TactKitCard
import com.xialia.tactkit.ui.theme.TactKitChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.isMcPixelTheme
import com.xialia.tactkit.ui.theme.mcPixelBox
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.FileInspectionHelper
import java.io.File

@Composable
fun StoragePermissionOnboardingCard(
  onGrantPermission: () -> Unit,
  onLinkSafTree: () -> Unit,
) {
  TactKitCard(
    corner = 22.dp,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("all_files_onboarding_card"),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(24.dp),
    ) {
      PetalIconBadge(
        icon = Icons.Rounded.SdStorage,
        size = 64.dp,
        iconSize = 30.dp,
        petals = 8,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        text = "开启全盘文件管理权限",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "简工坊纯本地读取手机目录（直接遍历物理 File，不依赖系统媒体库），需要「所有文件访问权限」才能实时索引 .jks、.pem、.apk、代码与 QQ/微信下载文件。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TactileActionButton(
        onClick = onGrantPermission,
        modifier = Modifier.fillMaxWidth().testTag("grant_all_files_button"),
      ) {
        Text("前往系统设置开启", fontWeight = FontWeight.SemiBold)
      }
      TextButton(onClick = onLinkSafTree) {
        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("或仅通过 SAF 授权指定 QQ/微信目录")
      }
    }
  }
}

@Composable
fun StorageOverviewAndQuickLinks(
  storage: StorageOverview,
  pinnedDirs: List<String>,
  messengerDirs: List<MessengerDirHint>,
  linkedSafCount: Int,
  onOpenPinnedPath: (String) -> Unit,
  onLinkMessengerSaf: () -> Unit,
) {
  val isMinecraft =
    com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current ==
      com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
  val filteredMessengerDirs =
    remember(messengerDirs, pinnedDirs) {
      messengerDirs.filter { hint -> pinnedDirs.none { it.equals(hint.relativePath, ignoreCase = true) } }
    }
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    if (storage.totalBytes > 0L) {
      val progress =
        (storage.usedBytes.toFloat() / storage.totalBytes.toFloat()).coerceIn(0f, 1f)
      Surface(
        shape = if (isMinecraft) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "内部存储空间",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "已用 ${FileInspectionHelper.formatBytes(storage.usedBytes)} / ${FileInspectionHelper.formatBytes(storage.totalBytes)}",
              fontFamily = TactKitMono,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TactKitThemedProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(pinnedDirs, key = { "pin_$it" }, contentType = { "pinned_dir_chip" }) { relPath ->
        val label = relPath.substringAfterLast('/').ifBlank { relPath }
        TactKitChip(
          onClick = { onOpenPinnedPath(relPath) },
          corner = 999.dp,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.PushPin,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }

      items(
        filteredMessengerDirs,
        key = { "msg_${it.relativePath}" },
        contentType = { "messenger_dir_chip" },
      ) { hint ->
        TactKitChip(
          onClick = { onOpenPinnedPath(hint.relativePath) },
          corner = 999.dp,
          containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.FolderSpecial,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${hint.label} (${hint.fileCountEstimate})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }

      item(key = "link_saf_btn", contentType = "link_saf_btn") {
        TactKitChip(
          onClick = onLinkMessengerSaf,
          corner = 999.dp,
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
          modifier = Modifier.testTag("link_qq_wechat_dir"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Link,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text =
                if (linkedSafCount > 0) "关联 QQ/微信目录 ($linkedSafCount)"
                else "关联 QQ/微信目录",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
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
fun RecentFilesTabContent(
  allRecentFiles: List<ManagedFileEntry>,
  selectedCategory: FileCategory?,
  isScanning: Boolean,
  isPickerMode: Boolean,
  selectedIds: Set<String>,
  headerContent: (@Composable () -> Unit)? = null,
  onCancelScan: () -> Unit = {},
  onSelectCategory: (FileCategory?) -> Unit,
  onEntryClick: (ManagedFileEntry) -> Unit,
  onEntryLongClick: (ManagedFileEntry) -> Unit,
  onLocateDirectory: (ManagedFileEntry) -> Unit,
  onImagePreviewClick: ((ManagedFileEntry) -> Unit)? = null,
) {
  val countsByCategory =
    remember(allRecentFiles) {
      allRecentFiles.groupingBy { it.category }.eachCount()
    }
  val filteredFiles =
    remember(allRecentFiles, selectedCategory) {
      if (selectedCategory == null) allRecentFiles
      else allRecentFiles.filter { it.category == selectedCategory }
    }
  val filterCategories =
    remember { FileCategory.entries.filter { it != FileCategory.OTHER } }

  Column(modifier = Modifier.fillMaxSize()) {
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
      item(key = "cat_all", contentType = "cat_chip") {
        FilterChip(
          selected = selectedCategory == null,
          onClick = { onSelectCategory(null) },
          label = { Text("全部 (${allRecentFiles.size})", maxLines = 1, softWrap = false) },
        )
      }
      items(filterCategories, key = { it.name }, contentType = { "cat_chip" }) { cat ->
        val count = countsByCategory[cat] ?: 0
        FilterChip(
          selected = selectedCategory == cat,
          onClick = { onSelectCategory(if (selectedCategory == cat) null else cat) },
          label = { Text("${cat.label} ($count)", maxLines = 1, softWrap = false) },
        )
      }
    }

    if (isScanning) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
      ) {
        TactKitBusyStatusBar(
          message = "正在扫描索引本地文件...",
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onCancelScan,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text("取消", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    if (isScanning && allRecentFiles.isEmpty()) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          TactKitMorphLoadingIndicator(size = 48.dp)
          Spacer(modifier = Modifier.height(12.dp))
          Text("正在深度遍历存储文件...", style = MaterialTheme.typography.bodyMedium)
        }
      }
    } else if (filteredFiles.isEmpty()) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        if (headerContent != null) {
          item(key = "scrollable_header", contentType = "header") { headerContent() }
        }
        item(key = "empty_msg", contentType = "empty_msg") {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
          ) {
            Text(
              text = "当前筛选下暂无文件",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else if (selectedCategory == FileCategory.IMAGES) {
      // 3-column square large-image grid (~110dp+ per tile)
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("recent_images_grid"),
      ) {
        if (headerContent != null) {
          item(span = { GridItemSpan(3) }, key = "scrollable_header", contentType = "header") {
            Box(modifier = Modifier.padding(bottom = 4.dp)) { headerContent() }
          }
        }
        items(filteredFiles, key = { it.id }, contentType = { "media_grid_tile" }) { entry ->
          val previewHandler =
            if (!isPickerMode && selectedIds.isEmpty() && entry.category == FileCategory.IMAGES) {
              onImagePreviewClick
            } else {
              null
            }
          SquareMediaGridTile(
            entry = entry,
            isSelected = entry.id in selectedIds,
            selectionMode = selectedIds.isNotEmpty(),
            sharedElementKey = if (entry.category == FileCategory.IMAGES) "shared_img_fm_${entry.id}" else null,
            onClick = { if (previewHandler != null) previewHandler(entry) else onEntryClick(entry) },
            onLongClick = { onEntryLongClick(entry) },
          )
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("recent_files_list"),
      ) {
        if (headerContent != null) {
          item(key = "scrollable_header", contentType = "header") {
            Box(modifier = Modifier.padding(bottom = 4.dp)) { headerContent() }
          }
        }
        items(
          items = filteredFiles,
          key = { it.id },
          contentType = { if (it.isDirectory) "dir" else "file" },
        ) { entry ->
          val previewHandler =
            if (!isPickerMode && selectedIds.isEmpty() && entry.category == FileCategory.IMAGES) {
              onImagePreviewClick
            } else {
              null
            }
          ManagedFileRow(
            entry = entry,
            isSelected = entry.id in selectedIds,
            selectionMode = selectedIds.isNotEmpty(),
            showLocateButton = !isPickerMode && !entry.isSafBacked,
            showRelativePath = false,
            sharedElementKey = if (entry.category == FileCategory.IMAGES) "shared_img_fm_${entry.id}" else null,
            onThumbClick = if (previewHandler != null) ({ previewHandler(entry) }) else null,
            onClick = { onEntryClick(entry) },
            onLongClick = { onEntryLongClick(entry) },
            onLocateClick = { onLocateDirectory(entry) },
          )
        }
      }
    }
  }
}

@Composable
fun DirectoryTreeTabContent(
  rootDir: File,
  currentDir: File,
  activeEntries: List<ManagedFileEntry>,
  collapsedNoiseDirs: List<ManagedFileEntry>,
  showHidden: Boolean,
  smartNoiseReduction: Boolean,
  isGridMode: Boolean,
  sortBy: DirectorySortBy,
  selectedIds: Set<String>,
  isPickerMode: Boolean,
  headerContent: (@Composable () -> Unit)? = null,
  onNavigateToDir: (File) -> Unit,
  onToggleHidden: () -> Unit,
  onToggleNoiseReduction: () -> Unit,
  onToggleGridMode: () -> Unit,
  onChangeSort: (DirectorySortBy) -> Unit,
  onCreateFolder: () -> Unit,
  onEntryClick: (ManagedFileEntry) -> Unit,
  onEntryLongClick: (ManagedFileEntry) -> Unit,
  onImagePreviewClick: ((ManagedFileEntry) -> Unit)? = null,
) {
  var showSortMenu by remember { mutableStateOf(false) }
  var expandNoiseDirs by remember { mutableStateOf(false) }

  val breadcrumbs =
    remember(currentDir.absolutePath, rootDir.absolutePath) {
      val rel = FileManagerEngine.toRelativePath(rootDir, currentDir)
      val segments = if (rel.isBlank()) emptyList() else rel.split('/')
      val list = mutableListOf("内部存储" to rootDir)
      var acc = rootDir
      segments.forEach { seg ->
        acc = File(acc, seg)
        list.add(seg to acc)
      }
      list
    }

  Column(modifier = Modifier.fillMaxSize()) {
    // Breadcrumbs
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
      items(
        count = breadcrumbs.size,
        key = { idx -> breadcrumbs[idx].second.absolutePath },
        contentType = { "breadcrumb" },
      ) { idx ->
        val (label, target) = breadcrumbs[idx]
        if (idx > 0) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
        TactKitChip(
          onClick = { onNavigateToDir(target) },
          corner = 10.dp,
          containerColor =
            if (idx == breadcrumbs.lastIndex) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (idx == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Medium,
            color =
              if (idx == breadcrumbs.lastIndex) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }

    // Toolbar controls
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
      Box {
        FilterChip(
          selected = false,
          onClick = { showSortMenu = true },
          leadingIcon = { Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(sortBy.label, maxLines = 1, softWrap = false) },
        )
        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
          DirectorySortBy.entries.forEach { option ->
            DropdownMenuItem(
              text = { Text(option.label) },
              onClick = {
                showSortMenu = false
                onChangeSort(option)
              },
            )
          }
        }
      }

      FilterChip(
        selected = smartNoiseReduction,
        onClick = onToggleNoiseReduction,
        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text("精简目录", maxLines = 1, softWrap = false) },
      )

      Spacer(modifier = Modifier.weight(1f))

      IconButton(onClick = onToggleHidden) {
        Icon(
          imageVector = if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
          contentDescription = "显示或隐藏隐藏文件",
        )
      }
      IconButton(onClick = onToggleGridMode) {
        Icon(
          imageVector = if (isGridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
          contentDescription = "切换列表或网格",
        )
      }
      if (!isPickerMode) {
        IconButton(onClick = onCreateFolder) {
          Icon(
            imageVector = Icons.Rounded.CreateNewFolder,
            contentDescription = "新建文件夹",
          )
        }
      }
    }

    val combinedEntries =
      remember(activeEntries, collapsedNoiseDirs, expandNoiseDirs) {
        if (expandNoiseDirs) activeEntries + collapsedNoiseDirs else activeEntries
      }

    if (combinedEntries.isEmpty() && collapsedNoiseDirs.isEmpty()) {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        if (headerContent != null) {
          item(key = "scrollable_header", contentType = "header") { headerContent() }
        }
        item(key = "empty_dir", contentType = "empty_msg") {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
            Text(
              text = "当前目录为空",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else if (isGridMode) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        if (headerContent != null) {
          item(span = { GridItemSpan(3) }, key = "scrollable_header", contentType = "header") {
            Box(modifier = Modifier.padding(bottom = 4.dp)) { headerContent() }
          }
        }
        items(
          items = combinedEntries,
          key = { it.id },
          contentType = { entry ->
            when {
              entry.isDirectory -> "dir_grid"
              entry.category == FileCategory.IMAGES || entry.category == FileCategory.AUDIO_VIDEO -> "media_grid"
              else -> "file_grid"
            }
          },
        ) { entry ->
          val previewHandler =
            if (!isPickerMode && selectedIds.isEmpty() && entry.category == FileCategory.IMAGES) {
              onImagePreviewClick
            } else {
              null
            }
          ManagedFileGridCard(
            entry = entry,
            isSelected = entry.id in selectedIds,
            selectionMode = selectedIds.isNotEmpty(),
            sharedElementKey = if (entry.category == FileCategory.IMAGES) "shared_img_fm_${entry.id}" else null,
            onClick = { if (previewHandler != null) previewHandler(entry) else onEntryClick(entry) },
            onLongClick = { onEntryLongClick(entry) },
          )
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("directory_tree_list"),
      ) {
        if (headerContent != null) {
          item(key = "scrollable_header", contentType = "header") {
            Box(modifier = Modifier.padding(bottom = 4.dp)) { headerContent() }
          }
        }
        items(
          items = activeEntries,
          key = { it.id },
          contentType = { if (it.isDirectory) "dir" else "file" },
        ) { entry ->
          val previewHandler =
            if (!isPickerMode && selectedIds.isEmpty() && entry.category == FileCategory.IMAGES) {
              onImagePreviewClick
            } else {
              null
            }
          ManagedFileRow(
            entry = entry,
            isSelected = entry.id in selectedIds,
            selectionMode = selectedIds.isNotEmpty(),
            showLocateButton = false,
            showRelativePath = false,
            sharedElementKey = if (entry.category == FileCategory.IMAGES) "shared_img_fm_${entry.id}" else null,
            onThumbClick = if (previewHandler != null) ({ previewHandler(entry) }) else null,
            onClick = { onEntryClick(entry) },
            onLongClick = { onEntryLongClick(entry) },
            onLocateClick = {},
          )
        }

        if (collapsedNoiseDirs.isNotEmpty()) {
          item(key = "noise_toggle_banner", contentType = "noise_banner") {
            TactKitCard(
              onClick = { expandNoiseDirs = !expandNoiseDirs },
              corner = 14.dp,
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth().testTag("collapsed_noise_banner"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              ) {
                Text(
                  text =
                    if (expandNoiseDirs) "收起 ${collapsedNoiseDirs.size} 个空目录 / 系统冷门目录"
                    else "已精简折叠 ${collapsedNoiseDirs.size} 个空目录与系统冷门目录",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = if (expandNoiseDirs) "收起" else "展开",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
          if (expandNoiseDirs) {
            items(
              items = collapsedNoiseDirs,
              key = { "noise_${it.id}" },
              contentType = { "noise_dir" },
            ) { entry ->
              ManagedFileRow(
                entry = entry,
                isSelected = entry.id in selectedIds,
                selectionMode = selectedIds.isNotEmpty(),
                showLocateButton = false,
                showRelativePath = false,
                onClick = { onEntryClick(entry) },
                onLongClick = { onEntryLongClick(entry) },
                onLocateClick = {},
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun SpaceCleanupTabContent(
  scanResult: SpaceScanResult?,
  isScanning: Boolean,
  checkedIds: Set<String>,
  headerContent: (@Composable () -> Unit)? = null,
  onTriggerScan: () -> Unit,
  onCancelScan: () -> Unit = {},
  onToggleChecked: (ManagedFileEntry) -> Unit,
  onDeleteChecked: () -> Unit,
) {
  var minLargeMb by remember { mutableStateOf(50) }
  var includeEmptyDirs by remember { mutableStateOf(true) }

  val filteredLarge =
    remember(scanResult, minLargeMb) {
      val minBytes = minLargeMb.toLong() * 1024L * 1024L
      scanResult?.largeFilesOver50Mb?.filter { it.sizeBytes >= minBytes }.orEmpty()
    }
  val emptyDirs = remember(scanResult) { scanResult?.emptyDirectories.orEmpty() }

  val allVisibleEntries =
    remember(filteredLarge, emptyDirs, includeEmptyDirs) {
      if (includeEmptyDirs) filteredLarge + emptyDirs else filteredLarge
    }
  val checkedEntries =
    remember(allVisibleEntries, checkedIds) { allVisibleEntries.filter { it.id in checkedIds } }
  val reclaimableBytes = remember(checkedEntries) { checkedEntries.sumOf { it.sizeBytes } }

  Column(modifier = Modifier.fillMaxSize()) {
    TactKitCard(
      corner = 20.dp,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(14.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "大文件与冗余空目录扫描",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                if (scanResult == null) "一键识别 >50MB / >100MB 大文件与空文件夹"
                else "已扫描 ${scanResult.scannedFileCount} 个文件，发现 ${scanResult.largeFilesOver50Mb.size} 个大文件 · ${scanResult.emptyDirectories.size} 个空目录",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TactileActionButton(
            onClick = onTriggerScan,
            enabled = !isScanning,
          ) {
            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isScanning) "扫描中..." else if (scanResult == null) "开始扫描" else "重新扫描")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = minLargeMb == 50,
            onClick = { minLargeMb = 50 },
            label = { Text(">50 MB") },
          )
          FilterChip(
            selected = minLargeMb == 100,
            onClick = { minLargeMb = 100 },
            label = { Text(">100 MB") },
          )
          FilterChip(
            selected = includeEmptyDirs,
            onClick = { includeEmptyDirs = !includeEmptyDirs },
            label = { Text("空目录 (${emptyDirs.size})") },
          )
        }
      }
    }

    if (isScanning) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
      ) {
        TactKitBusyStatusBar(
          message = "正在扫描大文件、重复文件与冗余目录...",
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onCancelScan,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text("取消", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
      if (headerContent != null) {
        item(key = "scrollable_header", contentType = "header") {
          Box(modifier = Modifier.padding(bottom = 4.dp)) { headerContent() }
        }
      }
      items(
        items = allVisibleEntries,
        key = { "clean_${it.id}" },
        contentType = { if (it.isDirectory) "clean_dir" else "clean_file" },
      ) { entry ->
        TactKitCard(
          onClick = { onToggleChecked(entry) },
          corner = 16.dp,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            Checkbox(
              checked = entry.id in checkedIds,
              onCheckedChange = { onToggleChecked(entry) },
            )
            FileEntryThumbnailOrBadge(entry = entry, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = entry.relativePath,
                fontFamily = TactKitMono,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text =
                if (entry.isDirectory) "空目录"
                else FileInspectionHelper.formatBytes(entry.sizeBytes),
              fontFamily = TactKitMono,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }

    if (checkedEntries.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
          Text(
            text = "已选 ${checkedEntries.size} 项 · 可释放 ${FileInspectionHelper.formatBytes(reclaimableBytes)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Button(
            onClick = onDeleteChecked,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("一键释放")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManagedFileRow(
  entry: ManagedFileEntry,
  isSelected: Boolean,
  selectionMode: Boolean,
  showLocateButton: Boolean,
  showRelativePath: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onLocateClick: () -> Unit,
  sharedElementKey: String? = null,
  onThumbClick: (() -> Unit)? = null,
) {
  val isMediaThumb =
    !entry.isDirectory &&
      (entry.category == FileCategory.IMAGES || entry.category == FileCategory.AUDIO_VIDEO)
  val thumbSize = if (isMediaThumb) 68.dp else 48.dp

  TactKitCard(
    corner = 18.dp,
    containerColor =
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.fillMaxWidth()
        .clip(themeShape(18.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .testTag("file_row_${entry.name}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      if (selectionMode) {
        Icon(
          imageVector =
            if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
          contentDescription = null,
          tint =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
      FileEntryThumbnailOrBadge(
        entry = entry,
        size = thumbSize,
        sharedElementKey = sharedElementKey,
        modifier = if (onThumbClick != null) Modifier.clickable(onClick = onThumbClick) else Modifier,
      )
      Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = entry.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (!entry.isDirectory && entry.extension.isNotBlank()) {
            FileExtensionPill(entry.extension)
          }
          Text(
            text =
              if (entry.isDirectory) "${entry.childCount} 项"
              else FileInspectionHelper.formatBytes(entry.sizeBytes),
            fontFamily = TactKitMono,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
          )
          Text(
            text = "· ${FileManagerEngine.formatRelativeTime(entry.lastModifiedMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
          )
        }
        // Dedicated compact line for source label so it never truncates
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          TactKitChip(
            corner = 6.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
          ) {
            Text(
              text = entry.sourceLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
          if (showRelativePath && entry.relativePath.isNotBlank()) {
            Text(
              text = entry.relativePath,
              fontFamily = TactKitMono,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      if (showLocateButton && !selectionMode) {
        IconButton(onClick = onLocateClick) {
          Icon(
            imageVector = Icons.Rounded.MyLocation,
            contentDescription = "定位到目录",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareMediaGridTile(
  entry: ManagedFileEntry,
  isSelected: Boolean,
  selectionMode: Boolean,
  sharedElementKey: String? = null,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val isMc = isMcPixelTheme()
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .aspectRatio(1f)
        .clip(themeShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .then(if (isMc) Modifier.mcPixelBox() else Modifier)
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
  ) {
    FileEntryThumbnailOrBadge(
      entry = entry,
      size = androidx.compose.ui.unit.Dp.Unspecified,
      sharedElementKey = sharedElementKey,
      modifier = Modifier.fillMaxSize(),
    )
    if (selectionMode) {
      Icon(
        imageVector =
          if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = null,
        tint =
          if (isSelected) MaterialTheme.colorScheme.primary
          else androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
      )
    } else if (entry.extension.isNotBlank()) {
      TactKitChip(
        corner = 6.dp,
        containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.52f),
        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
      ) {
        Text(
          text = entry.extension.uppercase(),
          fontFamily = TactKitMono,
          style = MaterialTheme.typography.labelSmall,
          color = androidx.compose.ui.graphics.Color.White,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManagedFileGridCard(
  entry: ManagedFileEntry,
  isSelected: Boolean,
  selectionMode: Boolean,
  sharedElementKey: String? = null,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  if (!entry.isDirectory &&
    (entry.category == FileCategory.IMAGES || entry.category == FileCategory.AUDIO_VIDEO)
  ) {
    SquareMediaGridTile(
      entry = entry,
      isSelected = isSelected,
      selectionMode = selectionMode,
      sharedElementKey = sharedElementKey,
      onClick = onClick,
      onLongClick = onLongClick,
    )
    return
  }

  TactKitCard(
    corner = 18.dp,
    containerColor =
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.fillMaxWidth()
        .clip(themeShape(18.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        FileEntryThumbnailOrBadge(entry = entry, size = 44.dp)
        if (selectionMode) {
          Icon(
            imageVector =
              if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else if (!entry.isDirectory && entry.extension.isNotBlank()) {
          FileExtensionPill(entry.extension)
        }
      }
      Text(
        text = entry.name,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text =
          if (entry.isDirectory) "${entry.childCount} 项"
          else FileInspectionHelper.formatBytes(entry.sizeBytes),
        fontFamily = TactKitMono,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

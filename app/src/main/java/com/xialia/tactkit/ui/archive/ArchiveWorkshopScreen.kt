package com.xialia.tactkit.ui.archive

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.ArchiveEngine
import com.xialia.tactkit.engine.ArchiveEntryNode
import com.xialia.tactkit.engine.ArchiveInspection
import com.xialia.tactkit.engine.PackArchiveFormat
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

private enum class ArchiveTab(val label: String) {
  BROWSE("浏览与解压"),
  PACK("打包与加密"),
}

private data class StagedPackItem(
  val uri: Uri,
  val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveWorkshopScreen(
  initialUriString: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var activeTab by remember { mutableStateOf(ArchiveTab.BROWSE) }
  var isBusy by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  // Browse / Extract state
  var archiveUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var inspection by remember { mutableStateOf<ArchiveInspection?>(null) }
  var extractPassword by remember { mutableStateOf("") }
  var showExtractPwd by remember { mutableStateOf(false) }
  var entrySearch by remember { mutableStateOf("") }
  var pendingSingleEntry by remember { mutableStateOf<ArchiveEntryNode?>(null) }

  // Pack state
  val packItems = remember { mutableStateListOf<StagedPackItem>() }
  var packFormat by remember { mutableStateOf(PackArchiveFormat.ZIP) }
  var packPassword by remember { mutableStateOf("") }
  var showPackPwd by remember { mutableStateOf(false) }

  fun loadArchive(uri: Uri) {
    archiveUri = uri
    scope.launch {
      isBusy = true
      errorText = null
      runCatching { ArchiveEngine.inspectArchive(context, uri) }
        .onSuccess {
          haptics.confirm()
          inspection = it
        }
        .onFailure { err ->
          inspection = null
          errorText = err.message ?: "无法解析该压缩文件"
        }
      isBusy = false
    }
  }

  val pickArchiveLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        haptics.tick()
        loadArchive(uri)
      }
    }

  val extractAllTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      val src = archiveUri ?: return@rememberLauncherForActivityResult
      if (treeUri != null) {
        scope.launch {
          isBusy = true
          errorText = null
          ArchiveEngine.extractAllToDirectory(
            context = context,
            archiveUri = src,
            targetTreeUri = treeUri,
            password = extractPassword.takeIf { it.isNotEmpty() },
          ).onSuccess { count ->
            haptics.confirm()
            Toast.makeText(context, "已成功解压 $count 个文件", Toast.LENGTH_SHORT).show()
          }.onFailure { err ->
            errorText = "解压失败：${err.message ?: "密码错误或文件损坏"}"
          }
          isBusy = false
        }
      }
    }

  val extractSingleTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      val src = archiveUri ?: return@rememberLauncherForActivityResult
      val node = pendingSingleEntry ?: return@rememberLauncherForActivityResult
      if (treeUri != null) {
        scope.launch {
          isBusy = true
          errorText = null
          ArchiveEngine.extractSingleEntryToDirectory(
            context = context,
            archiveUri = src,
            entryPath = node.path,
            targetTreeUri = treeUri,
            password = extractPassword.takeIf { it.isNotEmpty() },
          ).onSuccess { leafName ->
            haptics.confirm()
            Toast.makeText(context, "已提取：$leafName", Toast.LENGTH_SHORT).show()
          }.onFailure { err ->
            errorText = "提取失败：${err.message ?: "密码错误或条目损坏"}"
          }
          pendingSingleEntry = null
          isBusy = false
        }
      }
    }

  val pickPackFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        haptics.tick()
        uris.forEach { uri ->
          if (packItems.none { it.uri == uri }) {
            val name = resolveDisplayName(context, uri)
            packItems.add(StagedPackItem(uri, name))
          }
        }
      }
    }

  val exportArchiveLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(packFormat.mime)) { outUri ->
      if (outUri != null) {
        scope.launch {
          isBusy = true
          errorText = null
          ArchiveEngine.packFilesToArchive(
            context = context,
            inputUris = packItems.map { it.uri },
            outputUri = outUri,
            format = packFormat,
            zipPassword =
              if (packFormat == PackArchiveFormat.ZIP) packPassword.takeIf { it.isNotEmpty() }
              else null,
          ).onSuccess { count ->
            haptics.confirm()
            Toast.makeText(context, "已打包 $count 个文件为 ${packFormat.label}", Toast.LENGTH_SHORT).show()
          }.onFailure { err ->
            errorText = "打包失败：${err.message ?: "未知错误"}"
          }
          isBusy = false
        }
      }
    }

  androidx.compose.runtime.LaunchedEffect(initialUriString) {
    val uri = initialUriString?.let { Uri.parse(it) } ?: return@LaunchedEffect
    loadArchive(uri)
  }

  PredictiveBackContainer(onBack = onNavigateBack) {
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
          title = {
            Text(
              text = "压缩包工坊",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("archive_workshop"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("archive_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (isBusy) {
          TactKitBusyStatusBar(
            message = "正在解析与解压压缩包内容...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
          )
        }

        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          item(key = "archive_tabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(ArchiveTab.entries, key = { it.name }) { tab ->
                FilterChip(
                  selected = activeTab == tab,
                  onClick = {
                    haptics.tick()
                    activeTab = tab
                    errorText = null
                  },
                  label = { Text(tab.label, maxLines = 1, softWrap = false) },
                  modifier = Modifier.testTag("archive_tab_${tab.name}"),
                )
              }
            }
          }

          errorText?.let { msg ->
            item(key = "archive_error") {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
          }

          if (activeTab == ArchiveTab.BROWSE) {
            item(key = "archive_pick_card") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text(
                    text = "支持 ZIP / 7Z / TAR / TAR.GZ / TAR.XZ 离线浏览与流式解压",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Button(
                    enabled = !isBusy,
                    onClick = {
                      haptics.tick()
                      pickArchiveLauncher.launch(arrayOf("*/*"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("archive_pick_btn"),
                  ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (inspection == null) "选择压缩包" else "更换压缩包")
                  }
                }
              }
            }

            inspection?.let { info ->
              item(key = "archive_summary_card") {
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = info.fileName,
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "${info.archiveType.label} · ${info.entries.size} 项" +
                            if (info.requiresPassword) " · 已加密" else "",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }

                    if (info.requiresPassword) {
                      OutlinedTextField(
                        value = extractPassword,
                        onValueChange = { extractPassword = it },
                        label = { Text("解压密码") },
                        singleLine = true,
                        visualTransformation =
                          if (showExtractPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                          IconButton(onClick = { showExtractPwd = !showExtractPwd }) {
                            Icon(
                              if (showExtractPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                              contentDescription = "切换密码显示",
                            )
                          }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("archive_extract_password"),
                      )
                    }

                    OutlinedTextField(
                      value = entrySearch,
                      onValueChange = { entrySearch = it },
                      placeholder = { Text("搜索包内文件名或路径...") },
                      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth(),
                    )

                    FilledTonalButton(
                      enabled = !isBusy && info.entries.isNotEmpty(),
                      onClick = {
                        haptics.tick()
                        extractAllTreeLauncher.launch(null)
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("archive_extract_all_btn"),
                    ) {
                      Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("全部解压到所选目录")
                    }
                  }
                }
              }

              val filtered =
                info.entries.filter {
                  entrySearch.isBlank() || it.path.contains(entrySearch.trim(), ignoreCase = true)
                }

              items(filtered, key = { it.path }) { node ->
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                  ) {
                    Icon(
                      imageVector =
                        if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Text(
                          text = node.path,
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Medium,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false),
                        )
                        if (node.isEncrypted) {
                          Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "已加密",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                          )
                        }
                      }
                      if (!node.isDirectory && node.sizeBytes >= 0L) {
                        Text(
                          text = FileInspectionHelper.formatBytes(node.sizeBytes),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    if (!node.isDirectory) {
                      FilledTonalButton(
                        enabled = !isBusy,
                        onClick = {
                          haptics.tick()
                          pendingSingleEntry = node
                          extractSingleTreeLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                      ) {
                        Text("提取", maxLines = 1, softWrap = false)
                      }
                    }
                  }
                }
              }
            }
          } else {
            item(key = "archive_pack_controls") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text("选择输出归档格式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PackArchiveFormat.entries, key = { it.name }) { fmt ->
                      FilterChip(
                        selected = packFormat == fmt,
                        onClick = {
                          haptics.tick()
                          packFormat = fmt
                        },
                        label = { Text(fmt.label, maxLines = 1, softWrap = false) },
                        modifier = Modifier.testTag("archive_pack_fmt_${fmt.name}"),
                      )
                    }
                  }

                  if (packFormat == PackArchiveFormat.ZIP) {
                    OutlinedTextField(
                      value = packPassword,
                      onValueChange = { packPassword = it },
                      label = { Text("ZIP AES-256 加密密码（留空则不加密）") },
                      singleLine = true,
                      visualTransformation =
                        if (showPackPwd) VisualTransformation.None else PasswordVisualTransformation(),
                      trailingIcon = {
                        IconButton(onClick = { showPackPwd = !showPackPwd }) {
                          Icon(
                            if (showPackPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "切换密码显示",
                          )
                        }
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("archive_pack_password"),
                    )
                  }

                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    FilledTonalButton(
                      enabled = !isBusy,
                      onClick = {
                        haptics.tick()
                        pickPackFilesLauncher.launch(arrayOf("*/*"))
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f).testTag("archive_add_pack_files_btn"),
                    ) {
                      Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("添加文件 (${packItems.size})", maxLines = 1, softWrap = false)
                    }

                    Button(
                      enabled = !isBusy && packItems.isNotEmpty(),
                      onClick = {
                        haptics.tick()
                        exportArchiveLauncher.launch(
                          "TactKit_Archive_${System.currentTimeMillis()}.${packFormat.ext}"
                        )
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f).testTag("archive_pack_export_btn"),
                    ) {
                      Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("打包导出", maxLines = 1, softWrap = false)
                    }
                  }
                }
              }
            }

            itemsIndexed(
              items = packItems,
              key = { idx, item -> "pack_${idx}_${item.uri}" },
              contentType = { _, _ -> "staged_pack_item" },
            ) { idx, item ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                  Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    onClick = {
                      haptics.tick()
                      packItems.removeAt(idx)
                    }
                  ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "移除文件")
                  }
                }
              }
            }
          }

          item { Spacer(modifier = Modifier.height(16.dp)) }
        }
      }
    }
  }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
  return runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    }
  }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件"
}

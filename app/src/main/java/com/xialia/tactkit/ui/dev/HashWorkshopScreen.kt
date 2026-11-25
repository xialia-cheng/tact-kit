package com.xialia.tactkit.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.FileHashResult
import com.xialia.tactkit.engine.HashAlgorithm
import com.xialia.tactkit.engine.HashAndCryptoEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HashTab(val label: String) {
  FILE_HASH("计算"),
  TEXT_HASH("文本哈希"),
  COMPARE("对比"),
  BATCH_COMPARE("批量比较"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashWorkshopScreen(
  initialUriString: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var activeTab by remember { mutableStateOf(HashTab.FILE_HASH) }
  var selectedAlgo by remember { mutableStateOf(HashAlgorithm.SHA_256) }
  var isBusy by remember { mutableStateOf(false) }

  // Tab 1: File Hash (all 6 algorithms)
  var pickedFileName by remember { mutableStateOf<String?>(null) }
  var pickedFileSize by remember { mutableStateOf(0L) }
  var allFileHashes by remember { mutableStateOf<Map<HashAlgorithm, String>>(emptyMap()) }

  // Tab 2: Text Hash
  var textInput by remember { mutableStateOf("") }

  // Tab 3: Pair Compare
  var compareLeft by remember { mutableStateOf("") }
  var compareRight by remember { mutableStateOf("") }

  // Tab 4: Batch Compare
  var batchResults by remember { mutableStateOf<List<FileHashResult>>(emptyList()) }

  LaunchedEffect(initialUriString) {
    val raw = initialUriString ?: return@LaunchedEffect
    isBusy = true
    runCatching {
      val uri = Uri.parse(raw)
      val (name, reportedSize) = FileInspectionHelper.queryNameAndSize(context, uri)
      val hashes =
        withContext(Dispatchers.IO) {
          HashAndCryptoEngine.hashAllAlgorithms {
            context.contentResolver.openInputStream(uri)
              ?: throw IllegalStateException("无法打开文件")
          }
        }
      val actualSize =
        if (reportedSize > 0L) reportedSize
        else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
      Triple(name, actualSize, hashes)
    }.onSuccess { (name, size, hashes) ->
      pickedFileName = name
      pickedFileSize = size
      allFileHashes = hashes
    }.onFailure { err ->
      Toast.makeText(context, "计算失败：${err.message}", Toast.LENGTH_SHORT).show()
    }
    isBusy = false
  }

  val singleFilePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          isBusy = true
          runCatching {
            val (name, reportedSize) = FileInspectionHelper.queryNameAndSize(context, uri)
            val hashes =
              withContext(Dispatchers.IO) {
                HashAndCryptoEngine.hashAllAlgorithms {
                  context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法打开文件")
                }
              }
            val actualSize =
              if (reportedSize > 0L) reportedSize
              else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
            Triple(name, actualSize, hashes)
          }.onSuccess { (name, size, hashes) ->
            haptics.confirm()
            pickedFileName = name
            pickedFileSize = size
            allFileHashes = hashes
          }.onFailure { err ->
            Toast.makeText(context, "计算失败：${err.message}", Toast.LENGTH_SHORT).show()
          }
          isBusy = false
        }
      }
    }

  val batchFilesPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        scope.launch {
          isBusy = true
          val results =
            withContext(Dispatchers.IO) {
              uris.mapNotNull { uri ->
                runCatching {
                  val (name, _) = FileInspectionHelper.queryNameAndSize(context, uri)
                  HashAndCryptoEngine.hashUri(context, uri, name, selectedAlgo)
                }.getOrNull()
              }
            }
          haptics.confirm()
          batchResults = results
          isBusy = false
        }
      }
    }

  val exportReportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { outUri ->
      if (outUri != null && batchResults.isNotEmpty()) {
        scope.launch {
          val report = HashAndCryptoEngine.formatBatchReport(batchResults)
          runCatching {
            withContext(Dispatchers.IO) {
              context.contentResolver.openOutputStream(outUri)?.use { os ->
                os.write(report.toByteArray(Charsets.UTF_8))
              }
            }
          }.onSuccess {
            haptics.confirm()
            Toast.makeText(context, "已导出校验报告", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

  val compareFilePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          isBusy = true
          runCatching {
            withContext(Dispatchers.IO) {
              val (name, _) = FileInspectionHelper.queryNameAndSize(context, uri)
              HashAndCryptoEngine.hashUri(context, uri, name, selectedAlgo)
            }
          }.onSuccess { res ->
            haptics.confirm()
            if (compareLeft.isBlank()) {
              compareLeft = res.digestHex
            } else {
              compareRight = res.digestHex
            }
          }
          isBusy = false
        }
      }
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
          title = { Text("校验和与哈希", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("hash_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          item(key = "hash_tabs") {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              items(HashTab.entries, key = { it.name }) { tab ->
                TactileModeChip(
                  selected = activeTab == tab,
                  label = tab.label,
                  onClick = { activeTab = tab },
                  modifier = Modifier.testTag("hash_tab_${tab.name}"),
                )
              }
            }
          }

          if (activeTab == HashTab.COMPARE || activeTab == HashTab.BATCH_COMPARE) {
            item(key = "algo_selector") {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HashAlgorithm.entries, key = { it.name }) { algo ->
                  FilterChip(
                    selected = selectedAlgo == algo,
                    onClick = {
                      haptics.tick()
                      selectedAlgo = algo
                    },
                    label = { Text(algo.label, maxLines = 1, softWrap = false) },
                  )
                }
              }
            }
          }

          when (activeTab) {
            HashTab.FILE_HASH -> {
              item(key = "file_hash_header") {
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      PetalIconBadge(icon = Icons.Rounded.Fingerprint, size = 48.dp)
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = pickedFileName ?: "流式多算法文件校验",
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text =
                            if (pickedFileName != null) {
                              "文件大小：${FileInspectionHelper.formatBytes(pickedFileSize)} · 一次读取生成 6 种摘要"
                            } else {
                              "支持大文件流式计算 MD5 与 SHA 全家族摘要"
                            },
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }

                    TactileActionButton(
                      onClick = { singleFilePicker.launch(arrayOf("*/*")) },
                      modifier = Modifier.fillMaxWidth().testTag("hash_pick_file_btn"),
                    ) {
                      Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(if (pickedFileName == null) "选择文件计算校验和" else "更换文件")
                    }
                  }
                }
              }

              items(HashAlgorithm.entries, key = { "file_${it.name}" }) { algo ->
                val hex = allFileHashes[algo]
                DigestResultCard(
                  label = algo.label,
                  digest = hex ?: "等待选择文件...",
                  enabled = hex != null,
                  onCopy = {
                    if (hex != null) copyText(context, algo.label, hex)
                  },
                )
              }
            }

            HashTab.TEXT_HASH -> {
              item(key = "text_hash_input") {
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    OutlinedTextField(
                      value = textInput,
                      onValueChange = { textInput = it },
                      label = { Text("输入文本内容 (UTF-8)") },
                      placeholder = { Text("实时生成 MD5、SHA-1、SHA-224、SHA-256、SHA-384、SHA-512...") },
                      minLines = 3,
                      maxLines = 6,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("hash_text_input"),
                    )
                  }
                }
              }

              items(HashAlgorithm.entries, key = { "txt_${it.name}" }) { algo ->
                val digest = remember(textInput, algo) { HashAndCryptoEngine.hashText(textInput, algo) }
                DigestResultCard(
                  label = algo.label,
                  digest = digest,
                  enabled = true,
                  onCopy = { copyText(context, algo.label, digest) },
                )
              }
            }

            HashTab.COMPARE -> {
              item(key = "compare_card") {
                val comparison =
                  remember(compareLeft, compareRight) {
                    HashAndCryptoEngine.compareHashes(compareLeft, compareRight)
                  }
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    OutlinedTextField(
                      value = compareLeft,
                      onValueChange = { compareLeft = it },
                      label = { Text("参考摘要 A / 官方校验值") },
                      textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                      minLines = 2,
                      maxLines = 4,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("hash_compare_left"),
                    )
                    OutlinedTextField(
                      value = compareRight,
                      onValueChange = { compareRight = it },
                      label = { Text("待比对摘要 B") },
                      textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                      minLines = 2,
                      maxLines = 4,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("hash_compare_right"),
                    )

                    TactileActionButton(
                      onClick = { compareFilePicker.launch(arrayOf("*/*")) },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Icon(Icons.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("选取文件填入 (${selectedAlgo.label})")
                    }

                    if (comparison.leftNormalized.isNotEmpty() && comparison.rightNormalized.isNotEmpty()) {
                      Surface(
                        color =
                          if (comparison.matches) {
                            MaterialTheme.colorScheme.primaryContainer
                          } else {
                            MaterialTheme.colorScheme.errorContainer
                          },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("hash_compare_result"),
                      ) {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(10.dp),
                          modifier = Modifier.padding(14.dp),
                        ) {
                          Icon(
                            imageVector =
                              if (comparison.matches) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint =
                              if (comparison.matches) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                              } else {
                                MaterialTheme.colorScheme.onErrorContainer
                              },
                          )
                          Text(
                            text =
                              if (comparison.matches) "校验一致 · 摘要完全匹配"
                              else "校验不一致 · 两段摘要存在差异",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                              if (comparison.matches) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                              } else {
                                MaterialTheme.colorScheme.onErrorContainer
                              },
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            HashTab.BATCH_COMPARE -> {
              item(key = "batch_actions") {
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
                      text = "批量文件校验 (${selectedAlgo.label})",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      TactileActionButton(
                        onClick = { batchFilesPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).testTag("hash_batch_pick_btn"),
                      ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("多选文件")
                      }
                      TactileActionButton(
                        onClick = { exportReportLauncher.launch("checksum_${selectedAlgo.name.lowercase()}.txt") },
                        enabled = batchResults.isNotEmpty(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                      ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导出报告")
                      }
                    }
                  }
                }
              }

              val groupedDuplicates = batchResults.groupBy { it.digestHex }
              items(batchResults, key = { "${it.fileName}_${it.byteSize}_${it.digestHex}" }) { item ->
                val dupCount = groupedDuplicates[item.digestHex]?.size ?: 1
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color =
                    if (dupCount > 1) {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                      MaterialTheme.colorScheme.surfaceContainerLow
                    },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text =
                          if (dupCount > 1) "${item.fileName} · 同校验重复组 ($dupCount)"
                          else item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${FileInspectionHelper.formatBytes(item.byteSize)} · ${item.digestHex}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TactKitMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    IconButton(onClick = { copyText(context, item.fileName, item.digestHex) }) {
                      Icon(Icons.Rounded.ContentCopy, contentDescription = "复制摘要")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DigestResultCard(
  label: String,
  digest: String,
  enabled: Boolean,
  onCopy: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = digest,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = TactKitMono,
          color =
            if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
      }
      IconButton(onClick = onCopy, enabled = enabled) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制 $label")
      }
    }
  }
}

private fun copyText(context: Context, label: String, text: String) {
  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  cm.setPrimaryClip(ClipData.newPlainText(label, text))
  Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
}

package com.xialia.tactkit.ui.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.CsvTableData
import com.xialia.tactkit.engine.DataFormat
import com.xialia.tactkit.engine.DataFormatEngine
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DataWorkshopTab(val label: String) {
  CONVERTER("格式转换与校验"),
  CSV_TABLE("CSV 表格编辑"),
}

private const val SAMPLE_JSON = """[
  {"name": "简工坊", "version": "0.3.0", "localOnly": true},
  {"name": "Quick Look", "version": "0.3.0", "localOnly": true}
]"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataWorkshopScreen(
  initialUriString: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var activeTab by remember { mutableStateOf(DataWorkshopTab.CONVERTER) }
  var sourceFormat by remember { mutableStateOf(DataFormat.JSON) }
  var targetFormat by remember { mutableStateOf(DataFormat.YAML) }
  var prettyOutput by remember { mutableStateOf(true) }
  var inputText by remember { mutableStateOf(SAMPLE_JSON) }
  var outputText by remember { mutableStateOf("") }
  var convertError by remember { mutableStateOf<String?>(null) }

  // Interactive CSV table state
  val csvHeaders = remember { mutableStateListOf("名称", "版本", "备注") }
  val csvRows = remember {
    mutableStateListOf(
      mutableStateListOf("简工坊", "0.3.0", "本地优先"),
      mutableStateListOf("Quick Look", "0.3.0", "悬浮速览"),
    )
  }

  val validation = remember(inputText, sourceFormat) { DataFormatEngine.validate(inputText, sourceFormat) }

  fun syncTextToCsvTable() {
    val rawCsv =
      if (sourceFormat == DataFormat.CSV) inputText
      else DataFormatEngine.convert(inputText, sourceFormat, DataFormat.CSV, true).getOrNull().orEmpty()
    if (rawCsv.isNotBlank()) {
      val parsed = DataFormatEngine.parseCsvTable(rawCsv)
      csvHeaders.clear()
      csvHeaders.addAll(parsed.headers)
      csvRows.clear()
      parsed.rows.forEach { row ->
        val mutableRow = mutableStateListOf<String>()
        mutableRow.addAll(row)
        csvRows.add(mutableRow)
      }
    }
  }

  fun currentCsvString(): String {
    return DataFormatEngine.serializeCsvTable(
      CsvTableData(headers = csvHeaders.toList(), rows = csvRows.map { it.toList() })
    )
  }

  LaunchedEffect(initialUriString) {
    val raw = initialUriString ?: return@LaunchedEffect
    runCatching {
      val uri = android.net.Uri.parse(raw)
      withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use {
          it.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("无法读取文件内容")
      }
    }.onSuccess { content ->
      inputText = content
      val trimmed = content.trimStart()
      sourceFormat =
        when {
          trimmed.startsWith("{") || trimmed.startsWith("[") -> DataFormat.JSON
          trimmed.startsWith("<") -> DataFormat.XML
          content.contains(",") && !content.contains(": ") -> DataFormat.CSV
          else -> DataFormat.YAML
        }
      if (sourceFormat == DataFormat.CSV) syncTextToCsvTable()
    }.onFailure { err ->
      Toast.makeText(context, "打开失败：${err.message}", Toast.LENGTH_SHORT).show()
    }
  }

  val openFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
              } ?: error("无法读取文件内容")
            }
          }.onSuccess { content ->
            haptics.confirm()
            inputText = content
            val trimmed = content.trimStart()
            sourceFormat =
              when {
                trimmed.startsWith("{") || trimmed.startsWith("[") -> DataFormat.JSON
                trimmed.startsWith("<") -> DataFormat.XML
                content.contains(",") && !content.contains(": ") -> DataFormat.CSV
                else -> DataFormat.YAML
              }
            if (sourceFormat == DataFormat.CSV) syncTextToCsvTable()
          }.onFailure { err ->
            Toast.makeText(context, "打开失败：${err.message}", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

  val exportConvertedLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { outUri ->
      if (outUri != null) {
        val textToWrite =
          if (activeTab == DataWorkshopTab.CSV_TABLE) currentCsvString()
          else outputText.ifBlank { inputText }
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              context.contentResolver.openOutputStream(outUri, "wt")?.use { os ->
                os.write(textToWrite.toByteArray(Charsets.UTF_8))
                os.flush()
              } ?: error("无法写入目标文件")
            }
          }.onSuccess {
            com.xialia.tactkit.data.repository.FileCacheRepository.markDirty()
            haptics.confirm()
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
          }.onFailure { err ->
            Toast.makeText(context, "导出失败：${err.message}", Toast.LENGTH_SHORT).show()
          }
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
          title = {
            Text(
              "结构化数据工坊",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("data_workshop"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("data_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                openFileLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "*/*"))
              },
              modifier = Modifier.testTag("data_open_file_action"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = "导入数据文件")
            }
          },
        )
      },
    ) { innerPadding ->
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        item(key = "data_tabs") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DataWorkshopTab.entries, key = { it.name }) { tab ->
              FilterChip(
                selected = activeTab == tab,
                onClick = {
                  haptics.tick()
                  if (tab == DataWorkshopTab.CSV_TABLE) {
                    syncTextToCsvTable()
                  }
                  activeTab = tab
                },
                label = { Text(tab.label, maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("data_tab_${tab.name}"),
              )
            }
          }
        }

        if (activeTab == DataWorkshopTab.CONVERTER) {
          item(key = "data_converter_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text("源格式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(DataFormat.entries, key = { "src_${it.name}" }) { fmt ->
                    FilterChip(
                      selected = sourceFormat == fmt,
                      onClick = {
                        haptics.tick()
                        sourceFormat = fmt
                      },
                      label = { Text(fmt.label, maxLines = 1, softWrap = false) },
                      modifier = Modifier.testTag("data_src_${fmt.name}"),
                    )
                  }
                }

                OutlinedTextField(
                  value = inputText,
                  onValueChange = { inputText = it },
                  label = { Text("输入 ${sourceFormat.label} 数据") },
                  textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                  minLines = 6,
                  maxLines = 12,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("data_input_field"),
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector =
                      if (validation.isValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint =
                      if (validation.isValid) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = validation.message,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (validation.isValid) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error,
                  )
                }

                Text("目标格式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(DataFormat.entries, key = { "dst_${it.name}" }) { fmt ->
                    FilterChip(
                      selected = targetFormat == fmt,
                      onClick = {
                        haptics.tick()
                        targetFormat = fmt
                      },
                      label = { Text(fmt.label, maxLines = 1, softWrap = false) },
                      modifier = Modifier.testTag("data_dst_${fmt.name}"),
                    )
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  FilterChip(
                    selected = prettyOutput,
                    onClick = { prettyOutput = true },
                    label = { Text("美化缩进", maxLines = 1, softWrap = false) },
                  )
                  FilterChip(
                    selected = !prettyOutput,
                    onClick = { prettyOutput = false },
                    label = { Text("紧凑压缩", maxLines = 1, softWrap = false) },
                  )
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Button(
                    onClick = {
                      haptics.tick()
                      DataFormatEngine.convert(inputText, sourceFormat, targetFormat, prettyOutput)
                        .onSuccess {
                          haptics.confirm()
                          outputText = it
                          convertError = null
                        }
                        .onFailure { err ->
                          convertError = err.message ?: "转换失败"
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("data_convert_btn"),
                  ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("立即转换", maxLines = 1, softWrap = false)
                  }

                  FilledTonalButton(
                    onClick = {
                      haptics.tick()
                      DataFormatEngine.formatOrMinify(inputText, sourceFormat, prettyOutput)
                        .onSuccess {
                          haptics.confirm()
                          inputText = it
                          convertError = null
                        }
                        .onFailure { err ->
                          convertError = err.message ?: "格式化失败"
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("data_format_btn"),
                  ) {
                    Text(if (prettyOutput) "美化原文" else "压缩原文", maxLines = 1, softWrap = false)
                  }
                }
              }
            }
          }

          convertError?.let { err ->
            item(key = "data_convert_error") {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = err,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
          }

          if (outputText.isNotEmpty()) {
            item(key = "data_output_card") {
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
                    text = "输出 (${targetFormat.label})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  OutlinedTextField(
                    value = outputText,
                    onValueChange = { outputText = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    minLines = 6,
                    maxLines = 14,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("data_output_field"),
                  )
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    FilledTonalButton(
                      onClick = {
                        haptics.confirm()
                        copyToClipboard(context, outputText)
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("复制结果", maxLines = 1, softWrap = false)
                    }
                    Button(
                      onClick = {
                        haptics.tick()
                        exportConvertedLauncher.launch("TactKit_Data.${targetFormat.ext}")
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("导出文件", maxLines = 1, softWrap = false)
                    }
                  }
                }
              }
            }
          }
        } else {
          item(key = "csv_table_card") {
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
                  Icon(Icons.Rounded.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text("CSV 表格编辑器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  FilledTonalButton(
                    onClick = {
                      haptics.tick()
                      csvRows.add(
                        mutableStateListOf<String>().apply {
                          repeat(csvHeaders.size) { add("") }
                        }
                      )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("csv_add_row_btn"),
                  ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增行", maxLines = 1, softWrap = false)
                  }
                  FilledTonalButton(
                    onClick = {
                      haptics.tick()
                      csvHeaders.add("列${csvHeaders.size + 1}")
                      csvRows.forEach { it.add("") }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("csv_add_col_btn"),
                  ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增列", maxLines = 1, softWrap = false)
                  }
                }

                Column(
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                  // Header row
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    csvHeaders.forEachIndexed { colIdx, header ->
                      OutlinedTextField(
                        value = header,
                        onValueChange = { csvHeaders[colIdx] = it },
                        label = { Text("表头 ${colIdx + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(128.dp),
                      )
                    }
                    Spacer(modifier = Modifier.width(36.dp))
                  }

                  // Data rows
                  csvRows.forEachIndexed { rowIdx, row ->
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      for (colIdx in csvHeaders.indices) {
                        val cell = row.getOrElse(colIdx) { "" }
                        OutlinedTextField(
                          value = cell,
                          onValueChange = { next ->
                            while (row.size <= colIdx) row.add("")
                            row[colIdx] = next
                          },
                          singleLine = true,
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.width(128.dp),
                        )
                      }
                      IconButton(
                        onClick = {
                          if (csvRows.size > 1) csvRows.removeAt(rowIdx)
                        },
                        modifier = Modifier.size(36.dp),
                      ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除行", modifier = Modifier.size(18.dp))
                      }
                    }
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  OutlinedButton(
                    onClick = {
                      haptics.confirm()
                      val csvStr = currentCsvString()
                      inputText = csvStr
                      sourceFormat = DataFormat.CSV
                      copyToClipboard(context, csvStr)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制 CSV", maxLines = 1, softWrap = false)
                  }
                  Button(
                    onClick = {
                      haptics.tick()
                      exportConvertedLauncher.launch("TactKit_Table.csv")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出 .csv", maxLines = 1, softWrap = false)
                  }
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

private fun copyToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  clipboard?.setPrimaryClip(ClipData.newPlainText("TactKit Data", text))
  Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

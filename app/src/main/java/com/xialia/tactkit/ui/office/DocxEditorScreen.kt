package com.xialia.tactkit.ui.office

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Save
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
import com.xialia.tactkit.engine.DocxDocumentData
import com.xialia.tactkit.engine.DocxEngine
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.MarkdownRenderer
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

private const val DOCX_MIME =
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

private enum class DocxViewMode(val label: String) {
  PREVIEW("阅读预览"),
  EDIT("段落编辑"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxEditorScreen(
  initialUriString: String?,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var docData by remember { mutableStateOf<DocxDocumentData?>(null) }
  var isBlankNewDoc by remember { mutableStateOf(false) }
  val editableParagraphs = remember { mutableStateListOf<String>() }
  var viewMode by remember { mutableStateOf(DocxViewMode.PREVIEW) }
  var isBusy by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val openDocxLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        haptics.tick()
        isBlankNewDoc = false
        currentUri = uri
      }
    }

  val saveDocxLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { outUri ->
      if (outUri != null) {
        scope.launch {
          isBusy = true
          errorText = null
          runCatching {
            DocxEngine.saveEditedDocx(
              context = context,
              sourceUri = if (isBlankNewDoc) null else currentUri,
              outputUri = outUri,
              editedParagraphs = editableParagraphs.toList(),
            )
          }.onSuccess {
            haptics.confirm()
            Toast.makeText(context, "已导出 .docx 文档", Toast.LENGTH_SHORT).show()
          }.onFailure { err ->
            errorText = "导出 .docx 失败：${err.message ?: "未知错误"}"
          }
          isBusy = false
        }
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
    errorText = null
    runCatching { DocxEngine.readDocx(context, uri) }
      .onSuccess { data ->
        docData = data
        editableParagraphs.clear()
        if (data.paragraphs.isEmpty()) {
          editableParagraphs.add("")
        } else {
          editableParagraphs.addAll(data.paragraphs.map { it.text })
        }
      }
      .onFailure { err ->
        errorText = "解析 .docx 失败：${err.message ?: "文件可能已损坏或非 OOXML 格式"}"
      }
    isBusy = false
  }

  if (currentUri == null && !isBlankNewDoc) {
    ExpressiveEmptyFileScreen(
      title = "Docx 文档工坊",
      icon = Icons.Rounded.Description,
      onPickFile = {
        haptics.tick()
        openDocxLauncher.launch(arrayOf(DOCX_MIME, "application/octet-stream", "*/*"))
      },
      onNavigateBack = onNavigateBack,
      fabText = "打开 .docx 文件",
      secondaryActionText = "新建空白 .docx 文档",
      onSecondaryAction = {
        haptics.confirm()
        isBlankNewDoc = true
        docData = DocxDocumentData(
          title = "新建文档.docx",
          paragraphs = emptyList(),
          tableCount = 0,
          fullText = "",
        )
        editableParagraphs.clear()
        editableParagraphs.add("文档标题")
        editableParagraphs.add("在这里开始编写正文内容...")
        viewMode = DocxViewMode.EDIT
      },
      heroTargetRoute = "docx_editor",
    )
    return
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
              text = docData?.title ?: "Docx 文档工坊",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier = Modifier.tactKitToolHeroTarget("docx_editor"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("docx_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                openDocxLauncher.launch(arrayOf(DOCX_MIME, "application/octet-stream", "*/*"))
              },
              modifier = Modifier.testTag("docx_open_action"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = "打开其他 .docx")
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
            message = "正在解析与导出 .docx 文档...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
          )
        }

        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          errorText?.let { msg ->
            item(key = "docx_error") {
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

          item(key = "docx_controls") {
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
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  FilterChip(
                    selected = viewMode == DocxViewMode.PREVIEW,
                    onClick = {
                      haptics.tick()
                      viewMode = DocxViewMode.PREVIEW
                    },
                    leadingIcon = {
                      Icon(Icons.Rounded.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(DocxViewMode.PREVIEW.label, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("docx_tab_preview"),
                  )
                  FilterChip(
                    selected = viewMode == DocxViewMode.EDIT,
                    onClick = {
                      haptics.tick()
                      viewMode = DocxViewMode.EDIT
                    },
                    leadingIcon = {
                      Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(DocxViewMode.EDIT.label, maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("docx_tab_edit"),
                  )
                  Spacer(modifier = Modifier.weight(1f))
                  Text(
                    text = "${editableParagraphs.size} 段" +
                      ((docData?.tableCount ?: 0).takeIf { it > 0 }?.let { " · $it 表" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Button(
                    enabled = !isBusy,
                    onClick = {
                      haptics.tick()
                      val baseName =
                        (docData?.title ?: "TactKit_Doc.docx")
                          .removeSuffix(".docx")
                          .ifBlank { "TactKit_Doc" }
                      saveDocxLauncher.launch("${baseName}_edited.docx")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("docx_save_btn"),
                  ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("另存为 .docx", maxLines = 1, softWrap = false)
                  }
                  if (viewMode == DocxViewMode.EDIT) {
                    FilledTonalButton(
                      onClick = {
                        haptics.tick()
                        editableParagraphs.add("")
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.testTag("docx_add_paragraph_btn"),
                    ) {
                      Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("新增段落", maxLines = 1, softWrap = false)
                    }
                  }
                }
              }
            }
          }

          if (viewMode == DocxViewMode.PREVIEW) {
            item(key = "docx_preview_body") {
              val previewText =
                remember(editableParagraphs.toList(), docData) {
                  val liveParagraphText =
                    editableParagraphs
                      .mapIndexed { idx, p ->
                        val isHeading = docData?.paragraphs?.getOrNull(idx)?.isHeading == true
                        if (p.isBlank()) ""
                        else if (isHeading) "## $p"
                        else p
                      }
                      .filter { it.isNotEmpty() }
                      .joinToString("\n\n")
                  liveParagraphText.ifBlank { docData?.fullText.orEmpty() }
                    .ifBlank { "（文档当前为空，可切换至「段落编辑」输入内容）" }
                }

              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("docx_preview_card"),
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  MarkdownRenderer(markdown = previewText, compact = false)
                }
              }
            }
          } else {
            itemsIndexed(editableParagraphs) { index, text ->
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.padding(12.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    val styleHint = docData?.paragraphs?.getOrNull(index)?.styleName
                    Text(
                      text =
                        if (!styleHint.isNullOrBlank()) "第 ${index + 1} 段 · $styleHint"
                        else "第 ${index + 1} 段",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold,
                    )
                    if (editableParagraphs.size > 1) {
                      IconButton(
                        onClick = {
                          haptics.tick()
                          editableParagraphs.removeAt(index)
                        },
                        modifier = Modifier.size(36.dp),
                      ) {
                        Icon(
                          Icons.Rounded.DeleteOutline,
                          contentDescription = "删除该段",
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }
                  OutlinedTextField(
                    value = text,
                    onValueChange = { editableParagraphs[index] = it },
                    minLines = 2,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("docx_para_input_$index"),
                  )
                }
              }
            }

            item(key = "docx_bottom_add") {
              OutlinedButton(
                onClick = {
                  haptics.tick()
                  editableParagraphs.add("")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("在末尾添加新段落")
              }
              Spacer(modifier = Modifier.height(16.dp))
            }
          }
        }
      }
    }
  }
}

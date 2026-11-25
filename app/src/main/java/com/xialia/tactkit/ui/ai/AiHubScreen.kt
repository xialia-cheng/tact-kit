package com.xialia.tactkit.ui.ai

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.data.repository.ChecklistDraft
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.ui.components.MarkdownRenderer
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

enum class AiHubMode(val label: String) {
  VISION("识图转 Markdown"),
  PDF_QA("PDF 摘要问答"),
  CHECKLIST("整理为清单"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
  initialModeName: String,
  initialImageUriString: String? = null,
  preferences: UserPreferences,
  onSaveNoteCard: (title: String, markdown: String) -> Unit,
  onSaveChecklistCard: (title: String, items: List<String>) -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  val aiRepo = remember { AiHubRepository() }
  val settingsRepo = remember { SettingsRepository(context.applicationContext) }

  var mode by remember(initialModeName) {
    mutableStateOf(AiHubMode.entries.firstOrNull { it.name == initialModeName } ?: AiHubMode.VISION)
  }
  var isBusy by remember { mutableStateOf(false) }

  // Vision state
  var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var visionPrompt by remember { mutableStateOf("") }
  var aiMarkdownResult by remember { mutableStateOf("") }

  LaunchedEffect(initialImageUriString) {
    val raw = initialImageUriString ?: return@LaunchedEffect
    val parsed = android.net.Uri.parse(raw)
    selectedBitmap = ImageEngine.loadOrientedBitmap(context, parsed, maxDimension = 1400)
  }

  // PDF QA state
  var extractedPdfText by remember { mutableStateOf("") }
  var pdfQuestion by remember { mutableStateOf("") }

  // Checklist state
  var messyTextInput by remember { mutableStateOf("") }
  var checklistDraft by remember { mutableStateOf<ChecklistDraft?>(null) }

  val pickImageLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          selectedBitmap = ImageEngine.loadOrientedBitmap(context, uri, maxDimension = 1400)
        }
      }
    }

  val pickPdfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          isBusy = true
          runCatching { PdfEngine.extractText(context, uri) }
            .onSuccess { text ->
              extractedPdfText = text
              if (text.isBlank()) {
                Toast.makeText(context, "未提取到文本（可能是纯图片 PDF）", Toast.LENGTH_SHORT).show()
              }
            }
            .onFailure {
              Toast.makeText(context, "读取 PDF 失败：${it.message}", Toast.LENGTH_SHORT).show()
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
          title = { Text("多模态 ✨ AI 中枢", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(onClick = onNavigateToSettings) {
              Icon(Icons.Rounded.Settings, contentDescription = "AI 中枢设置")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (!preferences.hasAiApiKey) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(16.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("尚未配置 BYOK API Key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  "密钥将通过硬件 Keystore 加密存放在本机。",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              Button(onClick = onNavigateToSettings) { Text("去配置") }
            }
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          AiHubMode.entries.forEach { tab ->
            FilterChip(
              selected = mode == tab,
              onClick = {
                haptics.tick()
                mode = tab
              },
              label = { Text(tab.label) },
              modifier = Modifier.testTag("ai_tab_${tab.name}"),
            )
          }
        }

        when (mode) {
          AiHubMode.VISION -> {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                FilledTonalButton(
                  onClick = {
                    pickImageLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (selectedBitmap == null) "选择图片" else "重新选图")
                }

                selectedBitmap?.let { bmp ->
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "待识别图片",
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                  )
                }

                OutlinedTextField(
                  value = visionPrompt,
                  onValueChange = { visionPrompt = it },
                  label = { Text("提取要求（可选，默认转 Markdown / LaTeX / 表格）") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth(),
                )

                Button(
                  enabled = !isBusy && selectedBitmap != null && preferences.hasAiApiKey,
                  onClick = {
                    val bmp = selectedBitmap ?: return@Button
                    scope.launch {
                      isBusy = true
                      val res =
                        runCatching {
                          val key = settingsRepo.getDecryptedAiApiKey()
                          aiRepo.imageToMarkdown(
                            preferences.aiBaseUrl,
                            preferences.aiModel,
                            key,
                            bmp,
                            visionPrompt,
                          ).getOrThrow()
                        }
                      isBusy = false
                      res.onSuccess {
                        haptics.confirm()
                        aiMarkdownResult = it
                      }.onFailure {
                        Toast.makeText(context, "AI 识图失败：${it.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("ai_vision_run_btn"),
                ) {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("识图转 Markdown")
                }
              }
            }
          }

          AiHubMode.PDF_QA -> {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                FilledTonalButton(
                  onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (extractedPdfText.isBlank()) "选择 PDF 文档" else "已提取 ${extractedPdfText.length} 字 · 更换 PDF")
                }

                OutlinedTextField(
                  value = pdfQuestion,
                  onValueChange = { pdfQuestion = it },
                  label = { Text("提问内容（留空则自动生成全文摘要）") },
                  minLines = 2,
                  maxLines = 4,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth(),
                )

                Button(
                  enabled = !isBusy && extractedPdfText.isNotBlank() && preferences.hasAiApiKey,
                  onClick = {
                    scope.launch {
                      isBusy = true
                      val res =
                        runCatching {
                          val key = settingsRepo.getDecryptedAiApiKey()
                          aiRepo.summarizeOrAskPdf(
                            preferences.aiBaseUrl,
                            preferences.aiModel,
                            key,
                            extractedPdfText,
                            pdfQuestion,
                          ).getOrThrow()
                        }
                      isBusy = false
                      res.onSuccess {
                        haptics.confirm()
                        aiMarkdownResult = it
                      }.onFailure {
                        Toast.makeText(context, "PDF 分析失败：${it.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("ai_pdf_run_btn"),
                ) {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (pdfQuestion.isBlank()) "生成 PDF 结构化摘要" else "提问该 PDF")
                }
              }
            }
          }

          AiHubMode.CHECKLIST -> {
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
                  value = messyTextInput,
                  onValueChange = { messyTextInput = it },
                  label = { Text("粘贴语音转写或杂乱文本") },
                  placeholder = { Text("例如：明天记得买咖啡豆、把周报发给产品组、晚上八点把项目打 release 包...") },
                  minLines = 4,
                  maxLines = 8,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("ai_checklist_input"),
                )

                Button(
                  enabled = !isBusy && messyTextInput.isNotBlank() && preferences.hasAiApiKey,
                  onClick = {
                    scope.launch {
                      isBusy = true
                      val res =
                        runCatching {
                          val key = settingsRepo.getDecryptedAiApiKey()
                          aiRepo.organizeTextToChecklist(
                            preferences.aiBaseUrl,
                            preferences.aiModel,
                            key,
                            messyTextInput,
                          ).getOrThrow()
                        }
                      isBusy = false
                      res.onSuccess { draft ->
                        haptics.confirm()
                        checklistDraft = draft
                      }.onFailure {
                        Toast.makeText(context, "整理失败：${it.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("ai_checklist_run_btn"),
                ) {
                  Icon(Icons.Rounded.Checklist, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("智能整理为可勾选清单")
                }
              }
            }

            checklistDraft?.let { draft ->
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text(draft.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  draft.items.forEach { item ->
                    Text("• $item", style = MaterialTheme.typography.bodyMedium)
                  }
                  Button(
                    onClick = {
                      haptics.confirm()
                      onSaveChecklistCard(draft.title, draft.items)
                      Toast.makeText(context, "已存入桌面清单卡片", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("一键存为桌面清单")
                  }
                }
              }
            }
          }
        }

        if (mode != AiHubMode.CHECKLIST && aiMarkdownResult.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Text("AI 生成结果 (Markdown)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              MarkdownRenderer(markdown = aiMarkdownResult)
              Button(
                onClick = {
                  haptics.confirm()
                  val title = if (mode == AiHubMode.VISION) "AI 识图笔记" else "AI PDF 摘要"
                  onSaveNoteCard(title, aiMarkdownResult)
                  Toast.makeText(context, "已存为桌面便签卡片", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("一键存为桌面便签")
              }
            }
          }
        }
      }
    }
  }
}

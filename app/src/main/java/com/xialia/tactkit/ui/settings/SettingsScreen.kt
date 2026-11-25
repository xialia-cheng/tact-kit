package com.xialia.tactkit.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.data.repository.ReleaseInfo
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

private data class AppVersionInfo(
  val versionName: String,
  val versionCode: Long,
)

private data class AiProviderPreset(
  val id: String,
  val label: String,
  val baseUrl: String,
  val model: String,
  val note: String,
)

private val AI_PROVIDER_PRESETS =
  listOf(
    AiProviderPreset(
      id = "deepseek",
      label = "DeepSeek",
      baseUrl = "https://api.deepseek.com",
      model = "deepseek-chat",
      note = "DeepSeek 官方接口支持「PDF 问答」与「整理清单」（仅文本模型；若需「识图提取」请改用支持视觉的模型）",
    ),
    AiProviderPreset(
      id = "openai",
      label = "OpenAI",
      baseUrl = "https://api.openai.com/v1",
      model = "gpt-4o-mini",
      note = "OpenAI gpt-4o-mini 同时支持文本、清单整理与多模态识图",
    ),
    AiProviderPreset(
      id = "siliconflow",
      label = "硅基流动",
      baseUrl = "https://api.siliconflow.cn/v1",
      model = "Qwen/Qwen2.5-72B-Instruct",
      note = "硅基流动（如需识图可填写 Qwen/Qwen2.5-VL-72B-Instruct 等视觉模型）",
    ),
    AiProviderPreset(
      id = "ollama",
      label = "Ollama 本地",
      baseUrl = "http://127.0.0.1:11434/v1",
      model = "qwen2.5:7b",
      note = "本地 Ollama 服务，API Key 可填任意占位字符（如 ollama）",
    ),
  )

private const val AUTHOR_NAME = "xialia cheng"
private const val AUTHOR_EMAIL = "zly080701@gmail.com"
private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 xialia cheng

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onLiquidGlassPowerSaveChange: (Boolean) -> Unit,
  onSaveAiSettings: suspend (baseUrl: String, model: String, plainApiKey: String?) -> Result<Unit>,
  onClearAiApiKey: () -> Unit,
  onGetDecryptedAiApiKey: suspend () -> String,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()
  val aiRepo = remember { AiHubRepository() }
  val updateRepo = remember { AppUpdateRepository() }

  val appVersionInfo = remember(context) { readAppVersionInfo(context) }
  val appIconBitmap = remember(context) { loadAppIconBitmap(context) }

  var baseUrl by remember(preferences.aiBaseUrl) { mutableStateOf(preferences.aiBaseUrl) }
  var modelName by remember(preferences.aiModel) { mutableStateOf(preferences.aiModel) }
  var apiKeyInput by remember { mutableStateOf("") }
  var showKeyPlaintext by remember { mutableStateOf(false) }
  var isTestingAi by remember { mutableStateOf(false) }
  var aiTestResult by remember { mutableStateOf<String?>(null) }

  var helpExpanded by remember { mutableStateOf(false) }
  var privacyExpanded by remember { mutableStateOf(true) }
  var licenseExpanded by remember { mutableStateOf(false) }

  var isCheckingUpdate by remember { mutableStateOf(false) }
  var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
  var updateError by remember { mutableStateOf<String?>(null) }

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
          title = { Text("我的与设置", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("settings_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回桌面",
              )
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        ) {
          // 1. About App & Author Header Card
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().testTag("settings_about_card"),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(18.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                if (appIconBitmap != null) {
                  Image(
                    bitmap = appIconBitmap,
                    contentDescription = "简工坊应用图标",
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)),
                  )
                } else {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(58.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Shield,
                      contentDescription = "简工坊应用图标",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(14.dp),
                    )
                  }
                }

                Column(
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = "简工坊 · TactKit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(999.dp),
                  ) {
                    Text(
                      text = "v${appVersionInfo.versionName} · Build ${appVersionInfo.versionCode}",
                      style = MaterialTheme.typography.labelMedium,
                      fontFamily = TactKitMono,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                  }
                }
              }

              Text(
                text = "个人定制、本地优先、零数据收集的全能工具与文档工作台。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Person,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "作者：$AUTHOR_NAME",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                )
              }

              // Email row: tap to mailto + copy button
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      haptics.tick()
                      openEmailClient(context)
                    }
                    .testTag("settings_author_email_row"),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "作者邮箱",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = AUTHOR_EMAIL,
                      style = MaterialTheme.typography.bodySmall,
                      fontFamily = TactKitMono,
                    )
                  }
                  IconButton(
                    onClick = {
                      haptics.tick()
                      copyToClipboard(context, "Author Email", AUTHOR_EMAIL)
                    },
                    modifier = Modifier.testTag("settings_copy_email_btn"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.ContentCopy,
                      contentDescription = "复制作者邮箱",
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }

              // GitHub repo row: tap to open browser
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      haptics.tick()
                      openBrowserUrl(context, AppUpdateRepository.REPO_URL)
                    }
                    .testTag("settings_github_repo_row"),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "开源仓库 (GitHub)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = AppUpdateRepository.REPO_URL,
                      style = MaterialTheme.typography.bodySmall,
                      fontFamily = TactKitMono,
                    )
                  }
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "打开 GitHub 仓库",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          // 2. Appearance Card
          SettingsSectionCard(
            title = "外观与主题",
            icon = Icons.Rounded.Palette,
          ) {
            Text(
              text = "主题模式",
              style = MaterialTheme.typography.titleSmall,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = preferences.themeMode == mode,
                  onClick = {
                    haptics.tick()
                    onThemeModeChange(mode)
                  },
                  label = { Text(mode.label) },
                  modifier = Modifier.testTag("theme_mode_${mode.name}"),
                )
              }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text(
                    text = "Dynamic Color 动态取色",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = "跟随 Android 12+ 系统壁纸生成专属莫奈配色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = preferences.dynamicColor,
                  onCheckedChange = {
                    haptics.tick()
                    onDynamicColorChange(it)
                  },
                  modifier = Modifier.testTag("dynamic_color_switch"),
                )
              }
            }
          }

          // 3. Liquid Glass Power Save Card
          SettingsSectionCard(
            title = "渲染与省电",
            icon = Icons.Rounded.BatterySaver,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "液态玻璃省电模式",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      "开启后关闭实时 AGSL 着色器折射渲染，改用 Material 3 半透明高对比卡片"
                    } else {
                      "当前系统低于 Android 13 (API 33)，已自动使用 Material 3 半透明兼容降级方案"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = preferences.liquidGlassPowerSave,
                onCheckedChange = {
                  haptics.confirm()
                  onLiquidGlassPowerSaveChange(it)
                },
                modifier = Modifier.testTag("liquid_glass_power_save_switch"),
              )
            }
          }

          // 4. BYOK AI Hub Card
          SettingsSectionCard(
            title = "AI 中枢设置 (BYOK)",
            icon = Icons.Rounded.AutoAwesome,
          ) {
            Text(
              text = "支持 OpenAI 兼容接口（可点下方预设一键填入）。API Key 仅通过硬件 AndroidKeyStore AES-GCM 加密保存在本地。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              items(AI_PROVIDER_PRESETS, key = { it.id }) { preset ->
                val selected =
                  baseUrl.trim().trimEnd('/').equals(preset.baseUrl.trimEnd('/'), ignoreCase = true) &&
                    modelName.trim() == preset.model
                FilterChip(
                  selected = selected,
                  onClick = {
                    haptics.tick()
                    baseUrl = preset.baseUrl
                    modelName = preset.model
                    aiTestResult = preset.note
                  },
                  label = { Text(preset.label, maxLines = 1, softWrap = false) },
                  modifier = Modifier.testTag("ai_preset_${preset.id}"),
                )
              }
            }

            OutlinedTextField(
              value = baseUrl,
              onValueChange = { baseUrl = it },
              label = { Text("Base URL") },
              placeholder = { Text(UserPreferences.DEFAULT_AI_BASE_URL) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("ai_base_url_input"),
            )

            Text(
              text = "实际请求地址：${AiHubRepository.normalizeChatCompletionsEndpoint(baseUrl)}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = TactKitMono,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = modelName,
              onValueChange = { modelName = it },
              label = { Text("模型名称") },
              placeholder = { Text(UserPreferences.DEFAULT_AI_MODEL) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("ai_model_input"),
            )

            OutlinedTextField(
              value = apiKeyInput,
              onValueChange = { apiKeyInput = it },
              label = {
                Text(
                  if (preferences.hasAiApiKey) "API Key（已加密保存，留空则保持不变）"
                  else "API Key（sk-...）"
                )
              },
              singleLine = true,
              visualTransformation =
                if (showKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { showKeyPlaintext = !showKeyPlaintext }) {
                  Icon(
                    imageVector =
                      if (showKeyPlaintext) Icons.Rounded.VisibilityOff
                      else Icons.Rounded.Visibility,
                    contentDescription = "切换明文显示",
                  )
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("ai_api_key_input"),
            )

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Button(
                onClick = {
                  scope.launch {
                    val result =
                      onSaveAiSettings(baseUrl, modelName, apiKeyInput.takeIf { it.isNotBlank() })
                    result
                      .onSuccess {
                        haptics.confirm()
                        apiKeyInput = ""
                        aiTestResult =
                          "已加密保存配置（请求地址：${AiHubRepository.normalizeChatCompletionsEndpoint(baseUrl)}）"
                      }
                      .onFailure { err ->
                        aiTestResult = "保存失败：${err.message ?: "硬件密钥库不可用"}"
                      }
                  }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("ai_save_settings_btn"),
              ) {
                Text("保存配置")
              }

              FilledTonalButton(
                enabled = !isTestingAi && (apiKeyInput.isNotBlank() || preferences.hasAiApiKey),
                onClick = {
                  scope.launch {
                    isTestingAi = true
                    aiTestResult = "正在测试连通性..."
                    val keyResult =
                      if (apiKeyInput.isNotBlank()) {
                        Result.success(apiKeyInput.trim())
                      } else {
                        runCatching { onGetDecryptedAiApiKey() }
                      }
                    keyResult
                      .onSuccess { resolvedKey ->
                        aiRepo
                          .testConnection(baseUrl, modelName, resolvedKey)
                          .onSuccess {
                            haptics.confirm()
                            aiTestResult = it
                          }
                          .onFailure {
                            aiTestResult = "测试失败：${it.message ?: "网络异常"}"
                          }
                      }
                      .onFailure { err ->
                        aiTestResult = "读取本地密钥失败：${err.message ?: "请重新输入并保存 API Key"}"
                      }
                    isTestingAi = false
                  }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("ai_test_conn_btn"),
              ) {
                Text(if (isTestingAi) "测试中..." else "测试连通性")
              }
            }

            if (preferences.hasAiApiKey) {
              OutlinedButton(
                onClick = {
                  haptics.tick()
                  onClearAiApiKey()
                  aiTestResult = "已清除密钥"
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_clear_key_btn"),
              ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("清除本地已保存的 API Key")
              }
            }

            aiTestResult?.let { resultMsg ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = resultMsg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          }

          // 5. Usage Help & Permissions Guide (Expandable)
          ExpandableSettingsCard(
            title = "使用帮助与权限指南",
            subtitle = "SAF 零全盘权限、按需麦克风与 Quick Look 速览",
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            expanded = helpExpanded,
            onToggle = {
              haptics.tick()
              helpExpanded = !helpExpanded
            },
            testTag = "settings_help_card",
          ) {
            HelpBullet(
              title = "文件授权（SAF 系统文档选择器）",
              detail = "简工坊不申请 READ_EXTERNAL_STORAGE 或全盘读写权限。每次导入图片、PDF 或音视频，均通过系统 Photo Picker 与 SAF 仅授权所选文件或导出目录。",
            )
            HelpBullet(
              title = "麦克风录音权限（按需动态申请）",
              detail = "仅在您主动点击「语音备忘录 -> 开始录音」时向系统请求 RECORD_AUDIO 权限；日常浏览卡片、处理文档与图片无需任何麦克风权限。",
            )
            HelpBullet(
              title = "Quick Look 悬浮速览窗",
              detail = "在微信、浏览器或文件管理器中点击「分享」，选择「简工坊 · Quick Look」即可在不离开当前应用的情况下速览 Markdown、文本或一键导入工作台。",
            )
          }

          // 6. Privacy & GitHub Update Check (Expandable)
          ExpandableSettingsCard(
            title = "隐私说明与检查更新",
            subtitle = "本地优先 · 零数据收集 · GitHub Releases 更新检查",
            icon = Icons.Rounded.Shield,
            expanded = privacyExpanded,
            onToggle = {
              haptics.tick()
              privacyExpanded = !privacyExpanded
            },
            testTag = "settings_privacy_update_card",
          ) {
            Text(
              text =
                "本地优先 · 零数据收集 · 无统计埋点与广告 SDK。您的便签、清单、录音及文件处理均完全留存在本地数据库与私有目录；BYOK API Key 经硬件 AndroidKeyStore AES-GCM 加密保管，仅在您发起 AI 请求或主动点击「检查更新」时才会发生出站网络请求。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "检查更新",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("settings_update_version_pill"),
              ) {
                Text(
                  text = "v${appVersionInfo.versionName}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = TactKitMono,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text =
                if (releaseInfo == null) {
                  "当前版本：v${appVersionInfo.versionName} (Build ${appVersionInfo.versionCode}) · 尚未检查"
                } else if (releaseInfo?.isUpdateAvailable == true) {
                  "当前版本：v${appVersionInfo.versionName} · 检测到新版本 ${releaseInfo?.tagName}"
                } else {
                  "当前版本：v${appVersionInfo.versionName} · 已是最新版本"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
              enabled = !isCheckingUpdate,
              onClick = {
                scope.launch {
                  haptics.tick()
                  isCheckingUpdate = true
                  updateError = null
                  updateRepo.fetchLatestRelease(appVersionInfo.versionName)
                    .onSuccess { info ->
                      haptics.confirm()
                      releaseInfo = info
                    }
                    .onFailure { err ->
                      updateError = err.message ?: "无法连接 GitHub Releases"
                    }
                  isCheckingUpdate = false
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_check_update_btn"),
            ) {
              if (isCheckingUpdate) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在查询 GitHub Releases...")
              } else {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("检查更新")
              }
            }

            releaseInfo?.let { info ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.padding(12.dp),
                ) {
                  Text(
                    text =
                      if (info.isUpdateAvailable) "发现新版本：${info.title} (${info.tagName})"
                      else "当前 v${appVersionInfo.versionName} 已是最新版本（远端 ${info.tagName}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (info.isUpdateAvailable) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurface,
                  )
                  if (info.body.isNotBlank()) {
                    Text(
                      text = info.body,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (info.isUpdateAvailable) {
                    Button(
                      onClick = {
                        haptics.tick()
                        openBrowserUrl(context, info.htmlUrl)
                      },
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.fillMaxWidth().testTag("settings_download_update_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("前往 GitHub 下载安装包")
                    }
                  }
                }
              }
            }

            updateError?.let { msg ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          }

          // 7. MIT License (Expandable)
          ExpandableSettingsCard(
            title = "开源协议 (MIT License)",
            subtitle = "MIT License · Copyright (c) 2026 xialia cheng",
            icon = Icons.Rounded.Gavel,
            expanded = licenseExpanded,
            onToggle = {
              haptics.tick()
              licenseExpanded = !licenseExpanded
            },
            testTag = "settings_license_card",
          ) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = MIT_LICENSE_TEXT,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = TactKitMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(18.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      content()
    }
  }
}

@Composable
private fun ExpandableSettingsCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  expanded: Boolean,
  onToggle: () -> Unit,
  testTag: String,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(18.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (expanded) "收起" else "展开",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          content()
        }
      }
    }
  }
}

@Composable
private fun HelpBullet(title: String, detail: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = detail,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun readAppVersionInfo(context: Context): AppVersionInfo =
  runCatching {
    val pkgInfo =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          context.packageName,
          PackageManager.PackageInfoFlags.of(0L),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
      }
    val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
    val versionCode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
      }
    AppVersionInfo(versionName = versionName, versionCode = versionCode)
  }.getOrElse {
    AppVersionInfo(
      versionName = BuildConfig.VERSION_NAME,
      versionCode = BuildConfig.VERSION_CODE.toLong(),
    )
  }

private fun loadAppIconBitmap(context: Context): ImageBitmap? =
  runCatching {
    context.packageManager
      .getApplicationIcon(context.packageName)
      .toBitmap(width = 144, height = 144)
      .asImageBitmap()
  }.getOrNull()

private fun openEmailClient(context: Context) {
  val intent =
    Intent(Intent.ACTION_SENDTO).apply {
      data = Uri.parse("mailto:$AUTHOR_EMAIL")
      putExtra(Intent.EXTRA_SUBJECT, "简工坊 (TactKit) 反馈")
    }
  runCatching {
    context.startActivity(Intent.createChooser(intent, "联系作者"))
  }.onFailure {
    copyToClipboard(context, "Author Email", AUTHOR_EMAIL)
  }
}

private fun openBrowserUrl(context: Context, url: String) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
  runCatching { context.startActivity(intent) }.onFailure {
    Toast.makeText(context, "未找到可用的浏览器应用", Toast.LENGTH_SHORT).show()
  }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
  Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
}

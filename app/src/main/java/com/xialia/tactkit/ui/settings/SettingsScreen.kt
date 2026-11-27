package com.xialia.tactkit.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.data.repository.ReleaseInfo
import com.xialia.tactkit.ui.components.GeminiSparkleLoader
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import com.xialia.tactkit.ui.components.TactileSwitch
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.components.tactilePress
import com.xialia.tactkit.ui.theme.TactKitCard
import com.xialia.tactkit.ui.theme.TactKitChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.AppHaptics
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
  onAutoCheckUpdatesChange: (Boolean) -> Unit = {},
  onSaveAiSettings: suspend (baseUrl: String, model: String, plainApiKey: String?) -> Result<Unit>,
  onClearAiApiKey: () -> Unit,
  onGetDecryptedAiApiKey: suspend () -> String,
  onOpenHelpTips: () -> Unit = {},
  onOpenThemeGallery: () -> Unit = {},
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

  var appearanceExpanded by remember { mutableStateOf(false) }
  var powerSaveExpanded by remember { mutableStateOf(false) }
  var aiExpanded by remember { mutableStateOf(false) }
  var storageExpanded by remember { mutableStateOf(false) }
  var privacyExpanded by remember { mutableStateOf(false) }
  var licenseExpanded by remember { mutableStateOf(false) }

  var isCheckingUpdate by remember { mutableStateOf(false) }
  var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
  var updateError by remember { mutableStateOf<String?>(null) }

  var hasAllFilesAccess by remember { mutableStateOf(checkAllFilesGranted(context)) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasAllFilesAccess = checkAllFilesGranted(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val legacyStorageLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      hasAllFilesAccess = checkAllFilesGranted(context)
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
              text = "我的与设置",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("settings"),
            )
          },
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
          TactKitCard(
            corner = 22.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    modifier = Modifier.size(58.dp).clip(themeShape(16.dp)),
                  )
                } else {
                  Surface(
                    shape = themeShape(16.dp),
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
                    shape = themeShape(999.dp),
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
                shape = themeShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(themeShape(14.dp))
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
                shape = themeShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(themeShape(14.dp))
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
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          // 1.5 Help & Tips Entry Card
          val helpInteraction = remember { MutableInteractionSource() }
          TactKitCard(
            onClick = {
              haptics.tick()
              onOpenHelpTips()
            },
            interactionSource = helpInteraction,
            corner = 22.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier =
              Modifier.fillMaxWidth()
                .tactilePress(
                  interactionSource = helpInteraction,
                  pressedScale = 0.98f,
                  baseCorner = 22.dp,
                  pressedCorner = 26.dp,
                )
                .testTag("settings_help_tips_entry"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth().padding(18.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "帮助和提示",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "全功能图文指南、权限排查、关键词搜索与重播新手引导",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "进入帮助和提示",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          // 2. Appearance Card (Expandable, default collapsed)
          val appearanceSummary =
            buildString {
              append(preferences.appThemeStyle.label)
              append(" · ")
              append(preferences.themeMode.label)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                append(if (preferences.dynamicColor) " · 动态取色" else "")
              }
            }
          ExpandableSettingsCard(
            title = "外观与主题",
            subtitle = appearanceSummary,
            icon = Icons.Rounded.Palette,
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
            testTag = "settings_appearance_card",
          ) {
            com.xialia.tactkit.ui.components.TactileCard(
              onClick = onOpenThemeGallery,
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              baseCorner = 16.dp,
              pressedCorner = 22.dp,
              modifier = Modifier.fillMaxWidth().testTag("settings_theme_gallery_entry"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Palette,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "主题风格画廊",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "当前：${preferences.appThemeStyle.label}（点击切换 5 套风格）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                  contentDescription = "进入主题风格画廊",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Text(
              text = "主题模式",
              style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              items(ThemeMode.entries, key = { it.name }) { mode ->
                FilterChip(
                  selected = preferences.themeMode == mode,
                  onClick = {
                    haptics.tick()
                    onThemeModeChange(mode)
                  },
                  label = { Text(mode.label, maxLines = 1, softWrap = false) },
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
                TactileSwitch(
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

          // 3. Liquid Glass Power Save Card (Expandable, default collapsed)
          val powerSaveSummary =
            if (preferences.liquidGlassPowerSave) {
              "省电模式已开启 · M3 半透明卡片"
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              "实时液态玻璃折射已开启"
            } else {
              "已自动启用 M3 半透明高对比渲染"
            }
          ExpandableSettingsCard(
            title = "渲染与省电",
            subtitle = powerSaveSummary,
            icon = Icons.Rounded.BatterySaver,
            expanded = powerSaveExpanded,
            onToggle = { powerSaveExpanded = !powerSaveExpanded },
            testTag = "settings_power_save_card",
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
                      "开启后关闭实时液态玻璃折射效果，改用 Material 3 半透明高对比卡片"
                    } else {
                      "当前系统版本已自动切换至 Material 3 半透明高对比渲染方案"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TactileSwitch(
                checked = preferences.liquidGlassPowerSave,
                onCheckedChange = {
                  haptics.confirm()
                  onLiquidGlassPowerSaveChange(it)
                },
                modifier = Modifier.testTag("liquid_glass_power_save_switch"),
              )
            }
          }

          // 4. AI Hub Card (Expandable, default collapsed)
          val aiProviderLabel =
            AI_PROVIDER_PRESETS.firstOrNull {
              preferences.aiBaseUrl.trim().trimEnd('/').equals(it.baseUrl.trimEnd('/'), ignoreCase = true)
            }?.label ?: "自定义接口"
          val aiSummary =
            if (preferences.hasAiApiKey) "$aiProviderLabel · 已加密保存 Key"
            else "$aiProviderLabel · 未配置 Key"
          ExpandableSettingsCard(
            title = "AI 中枢设置",
            subtitle = aiSummary,
            icon = Icons.Rounded.AutoAwesome,
            expanded = aiExpanded,
            onToggle = { aiExpanded = !aiExpanded },
            testTag = "settings_ai_card",
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
              shape = themeShape(14.dp),
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
              shape = themeShape(14.dp),
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
              shape = themeShape(14.dp),
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
                shape = themeShape(14.dp),
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
                shape = themeShape(14.dp),
                modifier = Modifier.weight(1f).testTag("ai_test_conn_btn"),
              ) {
                if (isTestingAi) {
                  GeminiSparkleLoader(size = 18.dp)
                  Spacer(modifier = Modifier.width(6.dp))
                }
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
                shape = themeShape(14.dp),
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
                shape = themeShape(12.dp),
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

          // 5. All-Files Access & Permissions Diagnostic Card (Expandable, default collapsed)
          val storageSummary =
            if (hasAllFilesAccess) "所有文件权限已授权"
            else "未授权 · 仅使用 SAF 单文件/目录模式"
          ExpandableSettingsCard(
            title = "所有文件访问权限",
            subtitle = storageSummary,
            icon = Icons.Rounded.FolderShared,
            expanded = storageExpanded,
            onToggle = { storageExpanded = !storageExpanded },
            testTag = "settings_storage_card",
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "全盘文档管理状态",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                      "可选授权。开启后可直接浏览本地目录中的 PDF、文档与压缩包。"
                    } else {
                      "Android 10 及以下使用传统存储读写权限。"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                color =
                  if (hasAllFilesAccess) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = themeShape(999.dp),
                modifier = Modifier.testTag("settings_all_files_status_pill"),
              ) {
                Text(
                  text = if (hasAllFilesAccess) "已授权" else "未授权",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color =
                    if (hasAllFilesAccess) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
              }
            }

            FilledTonalButton(
              onClick = {
                haptics.tick()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                  val appIntent =
                    Intent(
                      Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                      Uri.fromParts("package", context.packageName, null),
                    )
                  val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                  runCatching { context.startActivity(appIntent) }
                    .onFailure {
                      runCatching { context.startActivity(fallbackIntent) }.onFailure {
                        Toast.makeText(context, "请在系统设置 -> 应用权限中授予文件管理权限", Toast.LENGTH_LONG).show()
                      }
                    }
                } else {
                  legacyStorageLauncher.launch(
                    arrayOf(
                      Manifest.permission.READ_EXTERNAL_STORAGE,
                      Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                  )
                }
              },
              shape = themeShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_all_files_grant_btn"),
            ) {
              Text(if (hasAllFilesAccess) "管理文件访问权限" else "前往授权所有文件访问权限")
            }
          }

          // 6. Update & Privacy (Expandable, default collapsed)
          val updatePrivacySummary =
            when {
              releaseInfo?.isUpdateAvailable == true -> "发现新版本 ${releaseInfo?.tagName} · 零数据收集"
              preferences.autoCheckUpdates -> "启动自动检查已开启 · 零数据收集"
              else -> "手动检查更新 · 零数据收集"
            }
          ExpandableSettingsCard(
            title = "更新与隐私",
            subtitle = updatePrivacySummary,
            icon = Icons.Rounded.Shield,
            expanded = privacyExpanded,
            onToggle = { privacyExpanded = !privacyExpanded },
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
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "启动时自动检查更新",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "冷启动双路并发竞速检测新版本，失败时静默不打扰",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TactileSwitch(
                checked = preferences.autoCheckUpdates,
                onCheckedChange = {
                  haptics.tick()
                  onAutoCheckUpdatesChange(it)
                },
                modifier = Modifier.testTag("settings_auto_update_switch"),
              )
            }

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
                shape = themeShape(999.dp),
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

            var showUpdateDialog by remember { mutableStateOf(false) }

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
                      if (info.isUpdateAvailable) {
                        showUpdateDialog = true
                      }
                    }
                    .onFailure { err ->
                      updateError = err.message ?: "无法连接 GitHub Releases"
                    }
                  isCheckingUpdate = false
                }
              },
              shape = themeShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_check_update_btn"),
            ) {
              if (isCheckingUpdate) {
                TactKitMorphLoadingIndicator(
                  size = 18.dp,
                  color = LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在双路竞速查询 Releases...")
              } else {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("检查更新")
              }
            }

            if (showUpdateDialog) {
              releaseInfo?.takeIf { it.isUpdateAvailable }?.let { info ->
                com.xialia.tactkit.ui.components.NewVersionUpdateDialog(
                  releaseInfo = info,
                  currentVersionName = appVersionInfo.versionName,
                  onDismiss = { showUpdateDialog = false },
                )
              }
            }

            releaseInfo?.let { info ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = themeShape(14.dp),
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
                        showUpdateDialog = true
                      },
                      shape = themeShape(12.dp),
                      modifier = Modifier.fillMaxWidth().testTag("settings_download_update_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("选择下载线路 (官方 / 镜像)")
                    }
                  }
                }
              }
            }

            updateError?.let { msg ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = themeShape(12.dp),
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
              shape = themeShape(14.dp),
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
private fun ExpandableSettingsCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  expanded: Boolean,
  onToggle: () -> Unit,
  testTag: String,
  content: @Composable () -> Unit,
) {
  val view = LocalView.current
  val interactionSource = remember { MutableInteractionSource() }
  TactKitCard(
    corner = 22.dp,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.fillMaxWidth()
        .tactilePress(
          interactionSource = interactionSource,
          pressedScale = 0.98f,
          baseCorner = 22.dp,
          pressedCorner = 26.dp,
        )
        .testTag(testTag),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .clickable(
              interactionSource = interactionSource,
              indication = ripple(),
            ) {
              AppHaptics.click(view)
              onToggle()
            }
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

      AnimatedVisibility(
        visible = expanded,
        enter = com.xialia.tactkit.ui.components.tactileExpandVertically(),
        exit = com.xialia.tactkit.ui.components.tactileShrinkVertically(),
      ) {
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

private fun checkAllFilesGranted(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
  } else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
  }
}

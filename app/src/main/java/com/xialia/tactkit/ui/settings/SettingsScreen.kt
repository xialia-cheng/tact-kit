package com.xialia.tactkit.ui.settings

import android.os.Build
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onLiquidGlassPowerSaveChange: (Boolean) -> Unit,
  onSaveAiSettings: (baseUrl: String, model: String, plainApiKey: String?) -> Unit,
  onClearAiApiKey: () -> Unit,
  onGetDecryptedAiApiKey: suspend () -> String,
  onNavigateBack: () -> Unit,
) {
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()
  val aiRepo = remember { AiHubRepository() }

  var baseUrl by remember(preferences.aiBaseUrl) { mutableStateOf(preferences.aiBaseUrl) }
  var modelName by remember(preferences.aiModel) { mutableStateOf(preferences.aiModel) }
  var apiKeyInput by remember { mutableStateOf("") }
  var showKeyPlaintext by remember { mutableStateOf(false) }
  var isTestingAi by remember { mutableStateOf(false) }
  var aiTestResult by remember { mutableStateOf<String?>(null) }

  PredictiveBackContainer(onBack = onNavigateBack) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        LargeTopAppBar(
          colors =
            TopAppBarDefaults.largeTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
          title = { Text("设置 · Settings", fontWeight = FontWeight.Bold) },
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
          // BYOK AI Hub Card
          SettingsSectionCard(
            title = "AI 中枢设置 (BYOK)",
            icon = Icons.Rounded.AutoAwesome,
          ) {
            Text(
              text = "自备 OpenAI 兼容接口（如 OpenAI / DeepSeek / Ollama）。API Key 通过硬件 AndroidKeyStore AES-GCM 加密存放本地，零中转。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = baseUrl,
              onValueChange = { baseUrl = it },
              label = { Text("Base URL") },
              placeholder = { Text(UserPreferences.DEFAULT_AI_BASE_URL) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("ai_base_url_input"),
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
                  haptics.confirm()
                  onSaveAiSettings(baseUrl, modelName, apiKeyInput.takeIf { it.isNotBlank() })
                  apiKeyInput = ""
                  aiTestResult = "已加密保存配置"
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
                    val key =
                      if (apiKeyInput.isNotBlank()) apiKeyInput.trim()
                      else runCatching { onGetDecryptedAiApiKey() }.getOrDefault("")
                    aiRepo.testConnection(baseUrl, modelName, key)
                      .onSuccess {
                        haptics.confirm()
                        aiTestResult = it
                      }
                      .onFailure {
                        aiTestResult = "测试失败：${it.message ?: "网络异常"}"
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

          // Appearance Card
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

          // Liquid Glass Power Save Card
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
                      "开启后关闭实时 AGSL 着色器折射渲染，改用 Material 3 半透明高对比卡片（自动保存至 DataStore）"
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

          // About TactKit Card
          SettingsSectionCard(
            title = "关于简工坊",
            icon = Icons.Rounded.Info,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "本地优先 · 零数据收集",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text =
                "简工坊 (TactKit) 不包含任何遥测、广告 SDK 或后台服务器。所有便签、清单、录音、文件处理与加密密钥均严格留存在你的设备本地。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(12.dp),
              ) {
                Text(
                  text = "版本：v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                  style = MaterialTheme.typography.labelMedium,
                )
                Text(
                  text = "包名：${BuildConfig.APPLICATION_ID}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "开源协议：MIT License",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "作者：xialia cheng <zly080701@gmail.com>",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
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

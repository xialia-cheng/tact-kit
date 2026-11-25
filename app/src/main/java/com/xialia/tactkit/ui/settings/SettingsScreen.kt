package com.xialia.tactkit.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.util.rememberAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onLiquidGlassPowerSaveChange: (Boolean) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val haptics = rememberAppHaptics()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        colors =
          androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
        title = { Text("设置 · Settings") },
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
      ) {
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
              "简工坊 (TactKit) 不包含任何遥测、广告 SDK 或云端后端。所有便签、清单、文件指纹与偏好设置均严格留存在你的设备本地。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
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

@Composable
private fun SettingsSectionCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
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
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      content()
    }
  }
}

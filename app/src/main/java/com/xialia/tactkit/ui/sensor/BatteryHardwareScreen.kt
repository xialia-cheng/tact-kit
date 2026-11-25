package com.xialia.tactkit.ui.sensor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.engine.SystemHardwareEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHardwareScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var snapshot by remember { mutableStateOf(SystemHardwareEngine.captureSnapshot(context)) }

  LaunchedEffect(context) {
    while (true) {
      snapshot = SystemHardwareEngine.captureSnapshot(context)
      delay(1500L)
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
          title = { Text("电池功率与硬件信息", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("battery_hw_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                snapshot = SystemHardwareEngine.captureSnapshot(context)
              },
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          item(key = "battery_power_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("battery_power_card"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.BatteryChargingFull, size = 46.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "实时电池与充放电功率",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "${snapshot.chargeSourceLabel} · 健康状况${snapshot.batteryHealthLabel}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = "${snapshot.batteryLevelPct}%",
                    fontFamily = TactKitMono,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                LinearProgressIndicator(
                  progress = { (snapshot.batteryLevelPct / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth(),
                )

                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  HardwareMetricItem(
                    label = "实时功率",
                    value = String.format(Locale.US, "%.2f W", snapshot.watts),
                  )
                  HardwareMetricItem(
                    label = "电流",
                    value = String.format(Locale.US, "%.0f mA", snapshot.currentMa),
                  )
                  HardwareMetricItem(
                    label = "电压",
                    value = "${snapshot.voltageMv} mV",
                  )
                  HardwareMetricItem(
                    label = "温度",
                    value = String.format(Locale.US, "%.1f °C", snapshot.temperatureC),
                  )
                }
              }
            }
          }

          item(key = "screen_specs_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("屏幕显示参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                HardwareInfoRow("当前刷新率", String.format(Locale.US, "%.0f Hz", snapshot.refreshRateHz))
                HardwareInfoRow("物理分辨率", snapshot.resolutionLabel)
                HardwareInfoRow("像素密度", "${snapshot.densityDpi} DPI")
              }
            }
          }

          item(key = "soc_ram_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("处理器与内存规格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                val usedRam = (snapshot.totalRamMb - snapshot.availRamMb).coerceAtLeast(0L)
                val ramProgress =
                  if (snapshot.totalRamMb > 0L) (usedRam.toFloat() / snapshot.totalRamMb).coerceIn(0f, 1f)
                  else 0f
                LinearProgressIndicator(progress = { ramProgress }, modifier = Modifier.fillMaxWidth())
                HardwareInfoRow("运行内存 (可用/总计)", "${snapshot.availRamMb} MB / ${snapshot.totalRamMb} MB")
                HardwareInfoRow("CPU 核心数", "${snapshot.cpuCores} 核")
                HardwareInfoRow("指令集架构", snapshot.abiLabel)
                HardwareInfoRow("设备型号", snapshot.deviceModel)
                HardwareInfoRow("系统版本", snapshot.androidRelease)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HardwareMetricItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    com.xialia.tactkit.ui.components.RollingNumberText(
      text = value,
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HardwareInfoRow(label: String, value: String) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
  }
}

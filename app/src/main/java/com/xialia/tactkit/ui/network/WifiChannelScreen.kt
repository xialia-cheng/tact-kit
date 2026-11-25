package com.xialia.tactkit.ui.network

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xialia.tactkit.engine.NetworkDiagEngine
import com.xialia.tactkit.engine.WifiChannelScanEntry
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiChannelScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var hasLocPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var selectedBand by remember { mutableStateOf("2.4 GHz") }
  var scanEntries by remember { mutableStateOf<List<WifiChannelScanEntry>>(emptyList()) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasLocPermission = granted
      if (granted) {
        scanEntries = NetworkDiagEngine.readWifiChannelScan(context)
      }
    }

  LaunchedEffect(hasLocPermission) {
    if (hasLocPermission) {
      scanEntries = NetworkDiagEngine.readWifiChannelScan(context)
    }
  }

  val filteredEntries = remember(scanEntries, selectedBand) {
    scanEntries.filter { it.band == selectedBand }
  }

  val candidateChannels =
    remember(selectedBand) {
      if (selectedBand == "2.4 GHz") (1..13).toList()
      else listOf(36, 40, 44, 48, 52, 56, 60, 64, 149, 153, 157, 161, 165)
    }

  val congestionByChannel =
    remember(filteredEntries, candidateChannels) {
      candidateChannels.associateWith { ch -> filteredEntries.count { it.channel == ch } }
    }

  val recommendedChannels =
    remember(congestionByChannel, selectedBand) {
      val preferred = if (selectedBand == "2.4 GHz") listOf(1, 6, 11) else candidateChannels
      preferred.sortedBy { ch -> congestionByChannel[ch] ?: 0 }.take(3)
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
          title = { Text("Wi-Fi 信道分析", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("wifi_channel_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                if (hasLocPermission) {
                  scanEntries = NetworkDiagEngine.readWifiChannelScan(context)
                } else {
                  permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
              },
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "重新扫描")
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
          if (!hasLocPermission) {
            item(key = "wifi_loc_perm") {
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
                    PetalIconBadge(icon = Icons.Rounded.WifiFind, size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                      Text("需要精确位置权限读取周围信道", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                      Text("Android 系统要求位置权限才可获取附近 Wi-Fi AP 列表，数据仅在本地展示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  TactileActionButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.fillMaxWidth().testTag("wifi_grant_loc_button"),
                  ) {
                    Text("授予位置权限并扫描", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          item(key = "band_tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("2.4 GHz", "5 GHz").forEach { band ->
                TactileModeChip(
                  selected = selectedBand == band,
                  label = band,
                  onClick = { selectedBand = band },
                  modifier = Modifier.testTag("wifi_band_$band"),
                )
              }
            }
          }

          item(key = "channel_chart_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("wifi_chart_card"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text("$selectedBand 信道拥挤度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text(
                    text = "推荐信道：${recommendedChannels.joinToString(" / ")}",
                    fontFamily = TactKitMono,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                val barColor = MaterialTheme.colorScheme.primary
                val busyColor = MaterialTheme.colorScheme.error
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                  val count = candidateChannels.size.coerceAtLeast(1)
                  val slotW = size.width / count
                  val barW = (slotW * 0.55f).coerceAtLeast(6.dp.toPx())
                  val maxAp = (congestionByChannel.values.maxOrNull() ?: 1).coerceAtLeast(3).toFloat()

                  candidateChannels.forEachIndexed { idx, ch ->
                    val apCount = congestionByChannel[ch] ?: 0
                    val x = idx * slotW + (slotW - barW) * 0.5f
                    drawRoundRect(
                      color = trackColor,
                      topLeft = Offset(x, 8.dp.toPx()),
                      size = Size(barW, size.height - 16.dp.toPx()),
                      cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                    if (apCount > 0) {
                      val h = ((apCount / maxAp) * (size.height - 16.dp.toPx())).coerceAtLeast(10.dp.toPx())
                      drawRoundRect(
                        color = if (apCount >= 3) busyColor else barColor,
                        topLeft = Offset(x, size.height - 8.dp.toPx() - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                      )
                    }
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text("共发现 ${filteredEntries.size} 个信号源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("注：系统对主动扫描频率有限流", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          items(filteredEntries, key = { "${it.bssid}_${it.frequencyMhz}" }) { entry ->
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(entry.ssid, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = "信道 ${entry.channel} · ${entry.frequencyMhz} MHz · ${entry.bssid}",
                    fontFamily = TactKitMono,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                  Text(
                    text = "${entry.rssiDbm} dBm",
                    fontFamily = TactKitMono,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

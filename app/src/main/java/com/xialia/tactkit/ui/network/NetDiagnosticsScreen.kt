package com.xialia.tactkit.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.NetworkDiagEngine
import com.xialia.tactkit.engine.PortProbeResult
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetDiagnosticsScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var lanInfo by remember { mutableStateOf(NetworkDiagEngine.readLanDiagInfo(context)) }
  var targetHost by remember { mutableStateOf(lanInfo.gatewayIp.takeIf { it != "—" } ?: "1.1.1.1") }
  var pingPortText by remember { mutableStateOf("80") }
  var isPinging by remember { mutableStateOf(false) }
  var pingJob by remember { mutableStateOf<Job?>(null) }
  val pingHistory = remember { mutableStateListOf<Long?>() }

  var isScanningPorts by remember { mutableStateOf(false) }
  var portResults by remember { mutableStateOf<List<PortProbeResult>>(emptyList()) }

  DisposableEffect(Unit) {
    onDispose { pingJob?.cancel() }
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
          title = { Text("局域网体检与 Ping", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("net_diag_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                lanInfo = NetworkDiagEngine.readLanDiagInfo(context)
              },
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "刷新网络状态")
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
          item(key = "lan_info_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("lan_info_card"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.Lan, size = 44.dp)
                  Column {
                    Text(lanInfo.transportLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    lanInfo.wifiSsid?.let { ssid ->
                      Text("Wi-Fi：$ssid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
                NetDetailRow("本机 IPv4", lanInfo.localIps.joinToString(", ").ifBlank { "未获取" })
                NetDetailRow("默认网关", lanInfo.gatewayIp)
                NetDetailRow("DNS 服务器", lanInfo.dnsServers.joinToString(", ").ifBlank { "默认系统 DNS" })
                lanInfo.wifiRssiDbm?.let { rssi ->
                  NetDetailRow(
                    "信号与链路",
                    "$rssi dBm · ${lanInfo.wifiLinkSpeedMbps ?: 0} Mbps · ${lanInfo.wifiBandLabel ?: ""}",
                  )
                }
              }
            }
          }

          item(key = "ping_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text("连续延迟测试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  val presets =
                    listOfNotNull(
                      lanInfo.gatewayIp.takeIf { it != "—" }?.let { it to "网关" },
                      "1.1.1.1" to "1.1.1.1",
                      "223.5.5.5" to "阿里 DNS",
                      "github.com" to "GitHub",
                    )
                  items(presets, key = { it.first }) { (host, label) ->
                    FilterChip(
                      selected = targetHost == host,
                      onClick = { targetHost = host },
                      label = { Text(label) },
                    )
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  OutlinedTextField(
                    value = targetHost,
                    onValueChange = { targetHost = it },
                    label = { Text("目标地址 / 域名") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("net_ping_host_input"),
                  )
                  OutlinedTextField(
                    value = pingPortText,
                    onValueChange = { pingPortText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(88.dp),
                  )
                }

                val waveColor = MaterialTheme.colorScheme.primary
                val errColor = MaterialTheme.colorScheme.error
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                  drawLine(gridColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f))
                  if (pingHistory.isNotEmpty()) {
                    val maxLat = (pingHistory.filterNotNull().maxOrNull() ?: 100L).coerceAtLeast(60L).toFloat()
                    val stepX = size.width / (pingHistory.size - 1).coerceAtLeast(1)
                    val path = Path()
                    var started = false
                    pingHistory.forEachIndexed { idx, ms ->
                      val x = idx * stepX
                      if (ms != null) {
                        val norm = (ms / maxLat).coerceIn(0f, 1f)
                        val y = size.height - norm * (size.height - 16.dp.toPx()) - 8.dp.toPx()
                        if (!started) {
                          path.moveTo(x, y)
                          started = true
                        } else {
                          path.lineTo(x, y)
                        }
                        drawCircle(waveColor, radius = 3.dp.toPx(), center = Offset(x, y))
                      } else {
                        started = false
                        drawCircle(errColor, radius = 3.5.dp.toPx(), center = Offset(x, size.height - 8.dp.toPx()))
                      }
                    }
                    drawPath(path, waveColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                  }
                }

                if (pingHistory.isNotEmpty()) {
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pingHistory.takeLast(8).reversed()) { ms ->
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                          if (ms == null) MaterialTheme.colorScheme.errorContainer
                          else MaterialTheme.colorScheme.primaryContainer,
                      ) {
                        Text(
                          text = ms?.let { "${it}ms" } ?: "超时",
                          fontFamily = TactKitMono,
                          style = MaterialTheme.typography.labelSmall,
                          color =
                            if (ms == null) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                      }
                    }
                  }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                  TactileActionButton(
                    onClick = {
                      if (isPinging) {
                        pingJob?.cancel()
                        isPinging = false
                      } else {
                        isPinging = true
                        pingHistory.clear()
                        val port = pingPortText.toIntOrNull()?.coerceIn(1, 65535) ?: 80
                        pingJob =
                          scope.launch {
                            repeat(20) {
                              val rtt = NetworkDiagEngine.probeLatencyMs(targetHost, port)
                              pingHistory.add(rtt)
                              if (pingHistory.size > 24) pingHistory.removeAt(0)
                              delay(750L)
                            }
                            isPinging = false
                          }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("net_ping_toggle_button"),
                  ) {
                    Icon(
                      imageVector = if (isPinging) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPinging) "停止 Ping" else "开始连续 Ping", fontWeight = FontWeight.SemiBold)
                  }

                  TactileActionButton(
                    enabled = !isScanningPorts,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                      scope.launch {
                        isScanningPorts = true
                        portResults = NetworkDiagEngine.probePorts(targetHost)
                        isScanningPorts = false
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("net_port_scan_button"),
                  ) {
                    Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("常见端口探测", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          if (isScanningPorts || portResults.isNotEmpty()) {
            item(key = "port_scan_results") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("port_scan_results_card"),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text("常用端口状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  if (isScanningPorts) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                  }
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    portResults.forEach { res ->
                      Surface(
                        shape = RoundedCornerShape(14.dp),
                        color =
                          if (res.isOpen) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceContainer,
                      ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                          Text(
                            text = "${res.port} · ${res.serviceHint}",
                            fontFamily = TactKitMono,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                              if (res.isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                          Text(
                            text = if (res.isOpen) "开放 (${res.latencyMs}ms)" else "关闭/超时",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                              if (res.isOpen) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
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
      }
    }
  }
}

@Composable
private fun NetDetailRow(label: String, value: String) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontFamily = TactKitMono, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
  }
}

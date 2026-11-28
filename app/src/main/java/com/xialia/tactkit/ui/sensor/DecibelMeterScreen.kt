package com.xialia.tactkit.ui.sensor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.IntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xialia.tactkit.engine.DecibelMonitor
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val DB_RING_CAP = 64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecibelMeterScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()
  val monitor = remember { DecibelMonitor() }

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var isRunning by remember { mutableStateOf(false) }
  val currentDbState = remember { mutableFloatStateOf(0f) }
  val minDbState = remember { mutableFloatStateOf(999f) }
  val maxDbState = remember { mutableFloatStateOf(0f) }
  val sumDbState = remember { mutableFloatStateOf(0f) }
  val sampleCountState = remember { mutableIntStateOf(0) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  // Fixed pre-allocated ring buffer + Path
  val ringBuffer = remember { FloatArray(DB_RING_CAP) }
  val ringHeadState = remember { mutableIntStateOf(0) }
  val ringCountState = remember { mutableIntStateOf(0) }
  val wavePath = remember { Path() }

  fun startMonitoring() {
    errorMsg = null
    isRunning = true
    monitor.start(
      scope = scope,
      onSample = { db ->
        scope.launch(Dispatchers.Main) {
          val prev = currentDbState.floatValue
          val smoothed = if (prev <= 0f) db else prev * 0.65f + db * 0.35f
          currentDbState.floatValue = smoothed
          if (smoothed < minDbState.floatValue) minDbState.floatValue = smoothed
          if (smoothed > maxDbState.floatValue) maxDbState.floatValue = smoothed
          sumDbState.floatValue += smoothed
          sampleCountState.intValue += 1

          val head = ringHeadState.intValue
          ringBuffer[head] = smoothed
          ringHeadState.intValue = (head + 1) % DB_RING_CAP
          if (ringCountState.intValue < DB_RING_CAP) {
            ringCountState.intValue += 1
          }
        }
      },
      onError = { msg ->
        scope.launch(Dispatchers.Main) {
          errorMsg = msg
          isRunning = false
        }
      },
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted) {
        startMonitoring()
      }
    }

  DisposableEffect(hasMicPermission) {
    if (hasMicPermission) {
      startMonitoring()
    }
    onDispose {
      monitor.stop()
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
              "分贝噪音计",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("decibel_meter"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("decibel_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
          if (!hasMicPermission) {
            item(key = "mic_permission_card") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.padding(18.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    PetalIconBadge(icon = Icons.Rounded.Mic, size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "需要麦克风采样权限",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = "仅在本地实时计算声压分贝，不保存也不上传任何录音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  TactileActionButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth().testTag("decibel_grant_mic"),
                  ) {
                    Text("授予麦克风权限", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          errorMsg?.let { msg ->
            item(key = "decibel_error") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = msg,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }

          item(key = "decibel_main_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("decibel_main_card"),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp),
              ) {
                DecibelReadoutSection(
                  currentDbState = currentDbState,
                  minDbState = minDbState,
                  maxDbState = maxDbState,
                  sumDbState = sumDbState,
                  sampleCountState = sampleCountState,
                  isRunning = isRunning,
                )

                val waveColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val waveStroke =
                  remember(density) {
                    Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round)
                  }
                Canvas(
                  modifier = Modifier.fillMaxWidth().height(120.dp),
                ) {
                  val midY = size.height * 0.5f
                  drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                  val count = ringCountState.intValue
                  val head = ringHeadState.intValue
                  if (count >= 2) {
                    wavePath.rewind()
                    val stepX = size.width / (count - 1).coerceAtLeast(1)
                    val startIdx = if (count == DB_RING_CAP) head else 0
                    val padY = 8.dp.toPx()
                    val availH = size.height - 16.dp.toPx()
                    for (i in 0 until count) {
                      val value = ringBuffer[(startIdx + i) % DB_RING_CAP]
                      val norm = ((value - 20f) / 100f).coerceIn(0f, 1f)
                      val x = i * stepX
                      val y = size.height - norm * availH - padY
                      if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    drawPath(
                      path = wavePath,
                      color = waveColor,
                      style = waveStroke,
                    )
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  TactileActionButton(
                    onClick = {
                      if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      } else if (isRunning) {
                        monitor.stop()
                        isRunning = false
                      } else {
                        startMonitoring()
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("decibel_toggle_button"),
                  ) {
                    Icon(
                      imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRunning) "暂停测量" else "开始测量", fontWeight = FontWeight.SemiBold)
                  }
                  FilledTonalButton(
                    onClick = {
                      haptics.tick()
                      minDbState.floatValue = 999f
                      maxDbState.floatValue = 0f
                      sumDbState.floatValue = 0f
                      sampleCountState.intValue = 0
                      ringHeadState.intValue = 0
                      ringCountState.intValue = 0
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp).testTag("decibel_reset_button"),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清零")
                  }
                }
              }
            }
          }

          item(key = "decibel_reference_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text("常见环境声级参考", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                DecibelGuideRow("20 – 35 dB", "深夜卧室、图书馆阅读区")
                DecibelGuideRow("40 – 55 dB", "安静办公室、轻声交谈")
                DecibelGuideRow("60 – 75 dB", "正常通话、商超或城市车流")
                DecibelGuideRow("85 dB+", "长期暴露可能损伤听力，建议佩戴降噪耳机")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DecibelReadoutSection(
  currentDbState: FloatState,
  minDbState: FloatState,
  maxDbState: FloatState,
  sumDbState: FloatState,
  sampleCountState: IntState,
  isRunning: Boolean,
) {
  val currentDb = currentDbState.floatValue
  val minDb = minDbState.floatValue
  val maxDb = maxDbState.floatValue
  val sampleCount = sampleCountState.intValue
  val avgDb = if (sampleCount > 0) sumDbState.floatValue / sampleCount else 0f
  val envLabel =
    when {
      !isRunning && sampleCount == 0 -> "等待采样"
      currentDb < 35f -> "极静环境（图书馆 / 深夜）"
      currentDb < 50f -> "安静室内（住宅 / 轻声交谈）"
      currentDb < 68f -> "日常办公（正常对话）"
      currentDb < 82f -> "偏高嘈杂（街道 / 餐厅）"
      else -> "高分贝噪音（建议注意听力防护）"
    }

  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.Center,
  ) {
    Text(
      text = if (sampleCount == 0) "--.-" else String.format(Locale.US, "%.1f", currentDb),
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp, fontFeatureSettings = "tnum"),
      fontWeight = FontWeight.Bold,
      color =
        if (currentDb >= 82f) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = "dB",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 10.dp),
    )
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Text(
      text = envLabel,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }

  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = Modifier.fillMaxWidth(),
  ) {
    StatReadingTile("最小", if (sampleCount == 0) "--" else String.format(Locale.US, "%.1f", minDb))
    StatReadingTile("平均", if (sampleCount == 0) "--" else String.format(Locale.US, "%.1f", avgDb))
    StatReadingTile("峰值", if (sampleCount == 0) "--" else String.format(Locale.US, "%.1f", maxDb))
  }
}

@Composable
private fun StatReadingTile(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DecibelGuideRow(range: String, desc: String) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = range,
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.width(86.dp),
    )
    Text(
      text = desc,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

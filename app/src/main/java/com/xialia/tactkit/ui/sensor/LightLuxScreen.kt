package com.xialia.tactkit.ui.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlin.math.log10

private const val LUX_RING_CAP = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightLuxScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var hasLightSensor by remember { mutableStateOf(true) }
  val currentLuxState = remember { mutableFloatStateOf(0f) }
  val minLuxState = remember { mutableFloatStateOf(Float.MAX_VALUE) }
  val maxLuxState = remember { mutableFloatStateOf(0f) }
  val hasSampleState = remember { mutableStateOf(false) }

  // Fixed pre-allocated ring buffer + Path
  val ringBuffer = remember { FloatArray(LUX_RING_CAP) }
  val ringHeadState = remember { mutableIntStateOf(0) }
  val ringCountState = remember { mutableIntStateOf(0) }
  val wavePath = remember { Path() }

  DisposableEffect(context) {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    if (sm == null) {
      hasLightSensor = false
      return@DisposableEffect onDispose {}
    }
    val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
    hasLightSensor = lightSensor != null
    val listener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          val lux = event.values.firstOrNull()?.coerceAtLeast(0f) ?: return
          currentLuxState.floatValue = lux
          hasSampleState.value = true
          if (lux < minLuxState.floatValue) minLuxState.floatValue = lux
          if (lux > maxLuxState.floatValue) maxLuxState.floatValue = lux

          val head = ringHeadState.intValue
          ringBuffer[head] = lux
          ringHeadState.intValue = (head + 1) % LUX_RING_CAP
          if (ringCountState.intValue < LUX_RING_CAP) {
            ringCountState.intValue += 1
          }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
      }
    if (lightSensor != null) {
      sm.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_GAME)
    }
    onDispose { sm.unregisterListener(listener) }
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
          title = { Text("光线照度计", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("light_lux_back_button"),
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
          if (!hasLightSensor) {
            item(key = "no_light_sensor") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "当前设备未检测到环境光传感器 (TYPE_LIGHT)，无法读取真实照度。",
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }

          item(key = "lux_gauge_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("light_lux_card"),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp),
              ) {
                PetalIconBadge(icon = Icons.Rounded.LightMode, size = 50.dp)
                LightLuxReadout(
                  currentLuxState = currentLuxState,
                  minLuxState = minLuxState,
                  maxLuxState = maxLuxState,
                  hasSample = hasSampleState.value,
                )

                val waveColor = MaterialTheme.colorScheme.primary
                val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                  drawLine(lineColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f))
                  val count = ringCountState.intValue
                  val head = ringHeadState.intValue
                  if (count >= 2) {
                    wavePath.reset()
                    val stepX = size.width / (count - 1).coerceAtLeast(1)
                    val startIdx = if (count == LUX_RING_CAP) head else 0
                    for (i in 0 until count) {
                      val lux = ringBuffer[(startIdx + i) % LUX_RING_CAP]
                      // Log scale maps 1 lx .. 20000 lx cleanly
                      val norm = (log10(lux + 1f) / 4.3f).coerceIn(0f, 1f)
                      val x = i * stepX
                      val y = size.height - norm * (size.height - 16.dp.toPx()) - 8.dp.toPx()
                      if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    drawPath(
                      path = wavePath,
                      color = waveColor,
                      style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                  }
                }

                FilledTonalButton(
                  onClick = {
                    haptics.tick()
                    minLuxState.floatValue = currentLuxState.floatValue
                    maxLuxState.floatValue = currentLuxState.floatValue
                    ringHeadState.intValue = 0
                    ringCountState.intValue = 0
                  },
                  shape = RoundedCornerShape(16.dp),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("重置极值记录")
                }
              }
            }
          }

          item(key = "lux_standard_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text("照度场景参考值", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LuxGuideRow("50 – 100 lx", "客厅夜间休闲照明")
                LuxGuideRow("300 – 500 lx", "书桌阅读、写字与常规办公推荐照度")
                LuxGuideRow("750 – 1500 lx", "精细绘图、手作与摄影补光")
                LuxGuideRow("10000+ lx", "户外阴天或直射日光")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LightLuxReadout(
  currentLuxState: FloatState,
  minLuxState: FloatState,
  maxLuxState: FloatState,
  hasSample: Boolean,
) {
  val currentLux = currentLuxState.floatValue
  val minLux = minLuxState.floatValue
  val maxLux = maxLuxState.floatValue
  val sceneAdvice =
    when {
      !hasSample -> "等待光线传感器读数"
      currentLux < 30f -> "昏暗环境（夜间休息或暗室）"
      currentLux < 150f -> "柔和室内光（建议适当开灯阅读）"
      currentLux < 500f -> "标准阅读 / 办公照度（舒适护眼）"
      currentLux < 2500f -> "明亮窗边或高照度工作台"
      else -> "强烈户外日照环境"
    }

  Row(verticalAlignment = Alignment.Bottom) {
    Text(
      text = if (!hasSample) "--" else String.format(Locale.US, "%.0f", currentLux),
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, fontFeatureSettings = "tnum"),
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = "lx",
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
      text = sceneAdvice,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }

  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (!hasSample) "--" else String.format(Locale.US, "%.0f lx", minLux),
        fontFamily = TactKitMono,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
      )
      Text("最低照度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (!hasSample) "--" else String.format(Locale.US, "%.0f lx", maxLux),
        fontFamily = TactKitMono,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
      )
      Text("最高照度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun LuxGuideRow(range: String, desc: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = range,
      fontFamily = TactKitMono,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.width(105.dp),
    )
    Text(
      text = desc,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

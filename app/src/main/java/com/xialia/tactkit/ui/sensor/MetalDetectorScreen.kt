package com.xialia.tactkit.ui.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetalDetectorScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var hasMagnetometer by remember { mutableStateOf(true) }
  var magnitudeUt by remember { mutableFloatStateOf(0f) }
  var baselineUt by remember { mutableFloatStateOf(48f) }
  var thresholdDelta by remember { mutableFloatStateOf(20f) }
  var hapticEnabled by remember { mutableStateOf(true) }
  val history = remember { mutableStateListOf<Float>() }

  DisposableEffect(context, hapticEnabled, thresholdDelta, baselineUt) {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    hasMagnetometer = mag != null
    var lastHapticMs = 0L

    val listener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          val x = event.values[0]
          val y = event.values[1]
          val z = event.values[2]
          val total = sqrt(x * x + y * y + z * z)
          magnitudeUt = total
          history.add(total)
          if (history.size > 80) {
            history.removeAt(0)
          }

          val delta = abs(total - baselineUt)
          if (hapticEnabled && delta >= thresholdDelta) {
            val now = System.currentTimeMillis()
            val intervalMs = (420L - (delta * 3f).toLong()).coerceIn(90L, 420L)
            if (now - lastHapticMs >= intervalMs) {
              lastHapticMs = now
              if (delta >= thresholdDelta * 2f) haptics.confirm() else haptics.tick()
            }
          }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
      }

    if (mag != null) {
      sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
    }
    onDispose {
      sm.unregisterListener(listener)
    }
  }

  val deltaUt = abs(magnitudeUt - baselineUt)
  val isTriggered = deltaUt >= thresholdDelta

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
          title = { Text("磁力计金属探测", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("metal_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding).widthIn(max = 600.dp),
      ) {
        if (!hasMagnetometer) {
          item(key = "no_mag_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "当前设备缺少地磁传感器 (TYPE_MAGNETIC_FIELD)，无法实时探测磁场变化。",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }

        item(key = "metal_readout_card") {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color =
              if (isTriggered) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.padding(18.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                PetalIconBadge(icon = Icons.Rounded.Sensors, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = String.format(Locale.US, "%.1f μT", magnitudeUt),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TactKitMono,
                    modifier = Modifier.testTag("metal_ut_readout"),
                  )
                  Text(
                    text =
                      if (isTriggered) {
                        String.format(Locale.US, "检测到磁场异常 (+%.1f μT)", deltaUt)
                      } else {
                        String.format(Locale.US, "基准 %.1f μT · 偏差 %.1f μT", baselineUt, deltaUt)
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              val waveColor =
                if (isTriggered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
              val gridColor = MaterialTheme.colorScheme.outlineVariant
              Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                drawLine(
                  color = gridColor.copy(alpha = 0.5f),
                  start = Offset(0f, size.height / 2f),
                  end = Offset(size.width, size.height / 2f),
                  strokeWidth = 2f,
                )
                if (history.size >= 2) {
                  val path = Path()
                  val minV = (baselineUt - 60f).coerceAtLeast(0f)
                  val maxV = (baselineUt + 120f).coerceAtLeast(minV + 60f)
                  val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                  history.forEachIndexed { idx, v ->
                    val norm = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)
                    val x = idx * stepX
                    val y = size.height * (1f - norm)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                  }
                  drawPath(
                    path = path,
                    color = waveColor,
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                  )
                }
              }

              TactileActionButton(
                onClick = {
                  haptics.confirm()
                  if (magnitudeUt > 1f) baselineUt = magnitudeUt
                },
                modifier = Modifier.fillMaxWidth().testTag("metal_calibrate_btn"),
              ) {
                Text("以当前环境磁强校准基准")
              }
            }
          }
        }

        item(key = "metal_settings_card") {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Text(
                text = "触发阈值：±${thresholdDelta.toInt()} μT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Slider(
                value = thresholdDelta,
                onValueChange = { thresholdDelta = it },
                valueRange = 5f..80f,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("接近异常时渐进触感震动", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it })
              }
            }
          }
        }
      }
    }
  }
}

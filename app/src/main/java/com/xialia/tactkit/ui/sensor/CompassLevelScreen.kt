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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Straighten
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class CompassLevelTab(val label: String) {
  COMPASS("指南针"),
  LEVEL("双轴水平仪"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassLevelScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var activeTab by remember { mutableStateOf(CompassLevelTab.COMPASS) }
  var azimuthDeg by remember { mutableFloatStateOf(0f) }
  var pitchDeg by remember { mutableFloatStateOf(0f) }
  var rollDeg by remember { mutableFloatStateOf(0f) }
  var pitchOffset by remember { mutableFloatStateOf(0f) }
  var rollOffset by remember { mutableFloatStateOf(0f) }
  var hasSensors by remember { mutableStateOf(true) }
  var sensorAccuracyLabel by remember { mutableStateOf("标准") }

  DisposableEffect(context) {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    hasSensors = accel != null && mag != null

    val accelReading = FloatArray(3)
    val magReading = FloatArray(3)
    var hasAccel = false
    var hasMag = false
    val rotationMatrix = FloatArray(9)
    val orientation = FloatArray(3)
    var lastLevelSnap = false

    val listener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          val alpha = 0.18f
          when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
              for (i in 0..2) {
                accelReading[i] = accelReading[i] + alpha * (event.values[i] - accelReading[i])
              }
              hasAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
              for (i in 0..2) {
                magReading[i] = magReading[i] + alpha * (event.values[i] - magReading[i])
              }
              hasMag = true
            }
          }
          if (hasAccel && hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magReading)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            val az = ((Math.toDegrees(orientation[0].toDouble()).toFloat()) + 360f) % 360f
            val pt = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val rl = Math.toDegrees(orientation[2].toDouble()).toFloat()
            azimuthDeg = az
            pitchDeg = pt
            rollDeg = rl
            val adjP = pt - pitchOffset
            val adjR = rl - rollOffset
            val isLevelNow = abs(adjP) < 1.0f && abs(adjR) < 1.0f
            if (isLevelNow && !lastLevelSnap) {
              haptics.confirm()
            }
            lastLevelSnap = isLevelNow
          }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
          if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            sensorAccuracyLabel =
              when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "高精度"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "中等精度"
                else -> "建议做 8 字形晃动校准"
              }
          }
        }
      }

    if (accel != null) sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
    if (mag != null) sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)

    onDispose {
      sm.unregisterListener(listener)
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
          title = { Text("指南针与水平仪", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("compass_back_button"),
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
        item(key = "compass_tabs") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CompassLevelTab.entries, key = { it.name }) { tab ->
              TactileModeChip(
                selected = activeTab == tab,
                label = tab.label,
                onClick = { activeTab = tab },
                modifier = Modifier.testTag("compass_tab_${tab.name}"),
              )
            }
          }
        }

        if (!hasSensors) {
          item(key = "no_sensor_warning") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "当前设备缺少加速度计或磁力计硬件传感器，无法读取真实方向与倾角。",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }

        if (activeTab == CompassLevelTab.COMPASS) {
          item(key = "compass_dial") {
            val primaryColor = MaterialTheme.colorScheme.primary
            val errorColor = MaterialTheme.colorScheme.error
            val ringColor = MaterialTheme.colorScheme.outlineVariant
            val onSurface = MaterialTheme.colorScheme.onSurface

            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.Explore, size = 44.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${azimuthDeg.toInt()}° · ${cardinalDirection(azimuthDeg)}",
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Bold,
                      fontFamily = TactKitMono,
                      modifier = Modifier.testTag("compass_heading_text"),
                    )
                    Text(
                      text = "地磁状态：$sensorAccuracyLabel",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                  val cx = size.width / 2f
                  val cy = size.height / 2f
                  val radius = minOf(size.width, size.height) * 0.42f

                  drawCircle(
                    color = ringColor.copy(alpha = 0.5f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 4f),
                  )

                  // Fixed top pointer
                  drawLine(
                    color = primaryColor,
                    start = Offset(cx, cy - radius - 18f),
                    end = Offset(cx, cy - radius + 8f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                  )

                  rotate(degrees = -azimuthDeg, pivot = Offset(cx, cy)) {
                    for (deg in 0 until 360 step 15) {
                      val rad = Math.toRadians((deg - 90).toDouble())
                      val isMajor = deg % 90 == 0
                      val inner = radius * if (isMajor) 0.78f else 0.88f
                      val outer = radius * 0.96f
                      drawLine(
                        color = if (deg == 0) errorColor else onSurface.copy(alpha = if (isMajor) 0.85f else 0.4f),
                        start = Offset(cx + (inner * cos(rad)).toFloat(), cy + (inner * sin(rad)).toFloat()),
                        end = Offset(cx + (outer * cos(rad)).toFloat(), cy + (outer * sin(rad)).toFloat()),
                        strokeWidth = if (isMajor) 6f else 3f,
                        cap = StrokeCap.Round,
                      )
                    }
                    // North red needle
                    drawLine(
                      color = errorColor,
                      start = Offset(cx, cy),
                      end = Offset(cx, cy - radius * 0.65f),
                      strokeWidth = 10f,
                      cap = StrokeCap.Round,
                    )
                    // South needle
                    drawLine(
                      color = onSurface.copy(alpha = 0.55f),
                      start = Offset(cx, cy),
                      end = Offset(cx, cy + radius * 0.55f),
                      strokeWidth = 8f,
                      cap = StrokeCap.Round,
                    )
                  }
                  drawCircle(color = primaryColor, radius = 12f, center = Offset(cx, cy))
                }
              }
            }
          }
        } else {
          item(key = "bubble_level_card") {
            val adjPitch = pitchDeg - pitchOffset
            val adjRoll = rollDeg - rollOffset
            val isLevel = abs(adjPitch) < 1.0f && abs(adjRoll) < 1.0f
            val bubbleColor =
              if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            val ringColor = MaterialTheme.colorScheme.outlineVariant

            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.Straighten, size = 44.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = if (isLevel) "已处于水平状态" else "双轴倾角测量中",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text =
                        String.format(
                          Locale.US,
                          "横滚 X: %+.1f°  ·  俯仰 Y: %+.1f°",
                          adjRoll,
                          adjPitch,
                        ),
                      style = MaterialTheme.typography.bodySmall,
                      fontFamily = TactKitMono,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.testTag("level_angles_text"),
                    )
                  }
                }

                Canvas(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                  val cx = size.width / 2f
                  val cy = size.height / 2f
                  val outerR = minOf(size.width, size.height) * 0.42f

                  drawCircle(
                    color = ringColor.copy(alpha = 0.5f),
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 4f),
                  )
                  drawCircle(
                    color = ringColor.copy(alpha = 0.6f),
                    radius = outerR * 0.28f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f),
                  )
                  drawLine(
                    color = ringColor.copy(alpha = 0.4f),
                    start = Offset(cx - outerR, cy),
                    end = Offset(cx + outerR, cy),
                    strokeWidth = 2f,
                  )
                  drawLine(
                    color = ringColor.copy(alpha = 0.4f),
                    start = Offset(cx, cy - outerR),
                    end = Offset(cx, cy + outerR),
                    strokeWidth = 2f,
                  )

                  val maxTilt = 35f
                  val normX = (adjRoll / maxTilt).coerceIn(-1f, 1f)
                  val normY = (adjPitch / maxTilt).coerceIn(-1f, 1f)
                  val bubbleCenter =
                    Offset(
                      cx + normX * outerR * 0.72f,
                      cy + normY * outerR * 0.72f,
                    )
                  drawCircle(
                    color = bubbleColor.copy(alpha = 0.85f),
                    radius = outerR * 0.22f,
                    center = bubbleCenter,
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TactileActionButton(
                    onClick = {
                      haptics.confirm()
                      pitchOffset = pitchDeg
                      rollOffset = rollDeg
                    },
                    modifier = Modifier.weight(1f).testTag("level_zero_btn"),
                  ) {
                    Text("设为相对零点")
                  }
                  TactileActionButton(
                    onClick = {
                      haptics.tick()
                      pitchOffset = 0f
                      rollOffset = 0f
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                  ) {
                    Text("恢复绝对水平")
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

private fun cardinalDirection(deg: Float): String {
  val dirs = listOf("北 N", "东北 NE", "东 E", "东南 SE", "南 S", "西南 SW", "西 W", "西北 NW")
  val index = (((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
  return dirs[index]
}

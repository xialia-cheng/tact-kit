package com.xialia.tactkit.ui.sensor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalRulerScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val textMeasurer = rememberTextMeasurer()

  val metrics = context.resources.displayMetrics
  val rawYdpi = metrics.ydpi.takeIf { it in 80f..1200f } ?: (metrics.densityDpi.toFloat().coerceAtLeast(160f))
  var calibrationFactor by remember { mutableFloatStateOf(1f) }
  var showInchesOnRight by remember { mutableStateOf(true) }

  var cursorAY by remember { mutableFloatStateOf(180f) }
  var cursorBY by remember { mutableFloatStateOf(520f) }
  var activeCursor by remember { mutableIntStateOf(-1) }

  val pxPerMm = (rawYdpi / 25.4f) * calibrationFactor
  val pxPerInch = rawYdpi * calibrationFactor
  val deltaPx = abs(cursorBY - cursorAY)
  val deltaMm = deltaPx / pxPerMm
  val deltaCm = deltaMm / 10f
  val deltaInch = deltaPx / pxPerInch

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
          title = { Text("屏幕物理直尺", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("ruler_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                calibrationFactor = 1f
                cursorAY = 160f
                cursorBY = 520f
              },
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "重置游标")
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        Row(
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          val tickColor = MaterialTheme.colorScheme.onSurface
          val subtleTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
          val cursorColor = MaterialTheme.colorScheme.primary
          val bandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)

          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                  detectDragGestures(
                    onDragStart = { offset ->
                      activeCursor =
                        if (abs(offset.y - cursorAY) <= abs(offset.y - cursorBY)) 0 else 1
                    },
                    onDragEnd = { activeCursor = -1 },
                    onDragCancel = { activeCursor = -1 },
                  ) { change, dragAmount ->
                    change.consume()
                    val maxH = size.height.toFloat().coerceAtLeast(100f)
                    if (activeCursor == 0) {
                      cursorAY = (cursorAY + dragAmount.y).coerceIn(24f, maxH - 24f)
                    } else {
                      cursorBY = (cursorBY + dragAmount.y).coerceIn(24f, maxH - 24f)
                    }
                  }
                }
                .testTag("physical_ruler_canvas"),
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val topY = minOf(cursorAY, cursorBY)
              val bottomY = maxOf(cursorAY, cursorBY)
              drawRect(
                color = bandColor,
                topLeft = Offset(0f, topY),
                size = androidx.compose.ui.geometry.Size(size.width, (bottomY - topY).coerceAtLeast(1f)),
              )

              // Left edge: metric millimeter & centimeter ticks (exact ydpi scaling)
              val maxMm = (size.height / pxPerMm).toInt()
              for (mm in 0..maxMm) {
                val y = mm * pxPerMm
                val tickLen =
                  when {
                    mm % 10 == 0 -> 36.dp.toPx()
                    mm % 5 == 0 -> 24.dp.toPx()
                    else -> 13.dp.toPx()
                  }
                val color = if (mm % 5 == 0) tickColor else subtleTickColor
                drawLine(
                  color = color,
                  start = Offset(0f, y),
                  end = Offset(tickLen, y),
                  strokeWidth = if (mm % 10 == 0) 2.dp.toPx() else 1.dp.toPx(),
                )
                if (mm % 10 == 0) {
                  drawLabel(
                    textMeasurer = textMeasurer,
                    text = "${mm / 10} cm",
                    topLeft = Offset(tickLen + 6.dp.toPx(), (y - 9.dp.toPx()).coerceAtLeast(0f)),
                    color = tickColor,
                  )
                }
              }

              // Right edge: imperial 1/8 inch ticks
              if (showInchesOnRight) {
                val maxEighths = ((size.height / pxPerInch) * 8f).toInt()
                for (e in 0..maxEighths) {
                  val y = (e / 8f) * pxPerInch
                  val tickLen =
                    when {
                      e % 8 == 0 -> 34.dp.toPx()
                      e % 4 == 0 -> 24.dp.toPx()
                      e % 2 == 0 -> 16.dp.toPx()
                      else -> 10.dp.toPx()
                    }
                  drawLine(
                    color = if (e % 4 == 0) tickColor else subtleTickColor,
                    start = Offset(size.width, y),
                    end = Offset(size.width - tickLen, y),
                    strokeWidth = if (e % 8 == 0) 2.dp.toPx() else 1.dp.toPx(),
                  )
                  if (e % 8 == 0 && e > 0) {
                    drawLabel(
                      textMeasurer = textMeasurer,
                      text = "${e / 8} in",
                      topLeft = Offset(size.width - tickLen - 34.dp.toPx(), y - 9.dp.toPx()),
                      color = tickColor,
                    )
                  }
                }
              }

              // Draggable measurement cursors
              listOf(cursorAY, cursorBY).forEach { cy ->
                drawLine(
                  color = cursorColor,
                  start = Offset(0f, cy),
                  end = Offset(size.width, cy),
                  strokeWidth = 2.5.dp.toPx(),
                  cap = StrokeCap.Round,
                )
                drawCircle(
                  color = cursorColor,
                  radius = 10.dp.toPx(),
                  center = Offset(size.width * 0.5f, cy),
                )
              }
            }
          }

          Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(172.dp).padding(end = 14.dp, top = 16.dp, bottom = 16.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("ruler_readout_card"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Text("双游标间距", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = String.format(Locale.US, "%.1f mm", deltaMm),
                  fontFamily = TactKitMono,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = String.format(Locale.US, "%.2f cm", deltaCm),
                  fontFamily = TactKitMono,
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = String.format(Locale.US, "%.2f in", deltaInch),
                  fontFamily = TactKitMono,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "屏幕物理密度：${rawYdpi.toInt()} PPI",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Text("微调校准", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                  text = String.format(Locale.US, "%.2fx", calibrationFactor),
                  fontFamily = TactKitMono,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                  value = calibrationFactor,
                  onValueChange = { calibrationFactor = it },
                  valueRange = 0.94f..1.06f,
                )
                TactileModeChip(
                  selected = showInchesOnRight,
                  label = if (showInchesOnRight) "显示英寸刻度" else "仅公制厘米",
                  onClick = { showInchesOnRight = !showInchesOnRight },
                )
                Text(
                  text = "上下拖动蓝色游标线即可测量物体长度，支持对照实体直尺微调比例。",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
  textMeasurer: TextMeasurer,
  text: String,
  topLeft: Offset,
  color: androidx.compose.ui.graphics.Color,
) {
  drawText(
    textMeasurer = textMeasurer,
    text = text,
    topLeft = topLeft,
    style = TextStyle(color = color, fontSize = 11.sp, fontFamily = TactKitMono),
  )
}

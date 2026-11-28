package com.xialia.tactkit.ui.components

import android.graphics.LinearGradient
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as FrameworkPaint
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.ui.theme.LocalAppThemeStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Theme-adaptive linear progress indicator with zero per-frame allocations inside Canvas.
 * All Path, Matrix, Paint, and Stroke objects are pre-allocated in `remember` and reused via `rewind()`.
 */
@Composable
fun TactKitThemedProgressBar(
  progress: Float,
  modifier: Modifier = Modifier,
  animateProgress: Boolean = true,
) {
  val style = LocalAppThemeStyle.current
  val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f
  val density = LocalDensity.current

  val clampedTarget = progress.coerceIn(0f, 1f)
  val animatedProgressState: State<Float> =
    if (animateProgress) {
      animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = tween(durationMillis = 110, easing = LinearEasing),
        label = "themed_progress_val",
      )
    } else {
      remember(clampedTarget) { androidx.compose.runtime.mutableFloatStateOf(clampedTarget) }
    }

  val primaryColor = MaterialTheme.colorScheme.primary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val trackBaseColor = MaterialTheme.colorScheme.surfaceContainerHighest

  when (style) {
    AppThemeStyle.CLASSIC_DEFAULT -> {
      val waveTransition = rememberInfiniteTransition(label = "classic_wave_progress")
      val wavePhaseState =
        waveTransition.animateFloat(
          initialValue = 0f,
          targetValue = (2.0 * PI).toFloat(),
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 1200, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "wave_phase",
        )
      val wavePath = remember { Path() }
      val waveStroke =
        remember(density) {
          Stroke(
            width = with(density) { 3.5.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          )
        }
      val trackStrokeWidth = remember(density) { with(density) { 3.5.dp.toPx() } }

      Canvas(modifier = Modifier.fillMaxWidth().height(12.dp).then(modifier)) {
        val p = animatedProgressState.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val centerY = h * 0.5f
        val dotRadius = 2.2.dp.toPx()
        val usableEnd = w - dotRadius * 2f
        val activeEndX = (usableEnd * p).coerceIn(0f, usableEnd)
        val gap = 5.dp.toPx()

        if (activeEndX > 2f) {
          wavePath.rewind()
          val wavelengthPx = 22.dp.toPx().coerceAtLeast(12f)
          val amplitudePx = (h * 0.28f).coerceAtMost(3.dp.toPx())
          val stepPx = 3.dp.toPx().coerceAtLeast(2f)
          val phase = wavePhaseState.value
          var x = 0f
          var first = true
          while (x < activeEndX) {
            val theta = (x / wavelengthPx) * (2f * PI.toFloat()) - phase
            val y = centerY + sin(theta) * amplitudePx
            if (first) {
              wavePath.moveTo(x, y)
              first = false
            } else {
              wavePath.lineTo(x, y)
            }
            x += stepPx
          }
          val endTheta = (activeEndX / wavelengthPx) * (2f * PI.toFloat()) - phase
          wavePath.lineTo(activeEndX, centerY + sin(endTheta) * amplitudePx)
          drawPath(path = wavePath, color = primaryColor, style = waveStroke)
        }

        val trackStartX = if (activeEndX > 0f) (activeEndX + gap).coerceAtMost(w) else 0f
        if (trackStartX < usableEnd - dotRadius) {
          drawLine(
            color = trackBaseColor,
            start = Offset(trackStartX, centerY),
            end = Offset(usableEnd - dotRadius, centerY),
            strokeWidth = trackStrokeWidth,
            cap = StrokeCap.Round,
          )
        }

        // End stop dot indicator (Android 16 / M3 Expressive)
        drawCircle(
          color = primaryColor,
          radius = dotRadius,
          center = Offset(w - dotRadius, centerY),
        )
      }
    }

    AppThemeStyle.GLACIER_BENTO -> {
      val shimmerTransition = rememberInfiniteTransition(label = "glacier_progress_shimmer")
      val shimmerFractionState =
        shimmerTransition.animateFloat(
          initialValue = -0.35f,
          targetValue = 1.35f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 1500, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "glacier_shimmer_shift",
        )
      val glacierFillColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
      val glacierGlowColor = if (isDark) Color(0xFFE0F2FE) else Color(0xFFBAE6FD)
      val glacierTrackBg =
        if (isDark) Color(0xFF1E293B).copy(alpha = 0.72f) else Color(0xFFE0F2FE).copy(alpha = 0.75f)
      val glacierBorderColor =
        if (isDark) Color(0xFF7DD3FC).copy(alpha = 0.40f) else Color(0xFFFFFFFF).copy(alpha = 0.90f)
      val shimmerColorsArgb =
        remember(glacierGlowColor) {
          intArrayOf(
            Color.Transparent.toArgb(),
            glacierGlowColor.copy(alpha = 0.78f).toArgb(),
            Color.Transparent.toArgb(),
          )
        }
      val shimmerPositions = remember { floatArrayOf(0f, 0.5f, 1f) }
      val shaderMatrix = remember { AndroidMatrix() }
      val frameworkPaint =
        remember {
          FrameworkPaint(FrameworkPaint.ANTI_ALIAS_FLAG).apply {
            setStyle(FrameworkPaint.Style.FILL)
          }
        }
      val shaderHolder =
        remember(shimmerColorsArgb) {
          arrayOfNulls<LinearGradient>(1)
        }
      val borderStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }

      Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).then(modifier)) {
        val p = animatedProgressState.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val radius = h * 0.5f
        val corner = CornerRadius(radius, radius)

        drawRoundRect(
          color = glacierTrackBg,
          topLeft = Offset.Zero,
          size = size,
          cornerRadius = corner,
        )

        val fillW = (w * p).coerceIn(0f, w)
        if (fillW > 1f) {
          val drawW = fillW.coerceAtLeast(h.coerceAtMost(w))
          drawRoundRect(
            color = glacierFillColor,
            topLeft = Offset.Zero,
            size = Size(drawW, h),
            cornerRadius = corner,
          )

          var shader = shaderHolder[0]
          val spanW = (w * 0.42f).coerceAtLeast(48f)
          if (shader == null) {
            shader =
              LinearGradient(
                0f,
                0f,
                spanW,
                h,
                shimmerColorsArgb,
                shimmerPositions,
                Shader.TileMode.CLAMP,
              )
            shaderHolder[0] = shader
            frameworkPaint.shader = shader
          }
          shaderMatrix.reset()
          shaderMatrix.setTranslate(w * shimmerFractionState.value - spanW * 0.5f, 0f)
          shader.setLocalMatrix(shaderMatrix)
          drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(0f, 0f, drawW, h, radius, radius, frameworkPaint)
          }
        }

        drawRoundRect(
          color = glacierBorderColor,
          topLeft = Offset.Zero,
          size = size,
          cornerRadius = corner,
          style = borderStroke,
        )
      }
    }

    AppThemeStyle.SWISS_CRAFT -> {
      val cobaltColor = primaryColor
      val signalOrangeColor = tertiaryColor
      val swissTrackBg = if (isDark) Color(0xFF22252C) else Color(0xFFE2DFD5)
      val tickColor = if (isDark) Color(0xFF94A3B8).copy(alpha = 0.5f) else Color(0xFF5A5D65).copy(alpha = 0.45f)

      Canvas(modifier = Modifier.fillMaxWidth().height(12.dp).then(modifier)) {
        val p = animatedProgressState.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val barTop = h * 0.22f
        val barH = h * 0.56f
        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        drawRoundRect(
          color = swissTrackBg,
          topLeft = Offset(0f, barTop),
          size = Size(w, barH),
          cornerRadius = corner,
        )

        val fillW = (w * p).coerceIn(0f, w)
        if (fillW > 0.5f) {
          drawRoundRect(
            color = cobaltColor,
            topLeft = Offset(0f, barTop),
            size = Size(fillW, barH),
            cornerRadius = corner,
          )
        }

        // Precision vertical scale ticks (20 divisions)
        val divisions = 20
        val majorStroke = 1.2.dp.toPx()
        val minorStroke = 0.8.dp.toPx()
        for (i in 1 until divisions) {
          val tx = (w / divisions) * i
          val isMajor = i % 5 == 0
          val tickTop = if (isMajor) 0f else barTop
          val tickBottom = if (isMajor) h else barTop + barH
          drawLine(
            color = tickColor,
            start = Offset(tx, tickTop),
            end = Offset(tx, tickBottom),
            strokeWidth = if (isMajor) majorStroke else minorStroke,
          )
        }

        // Signal-orange precision cursor marker
        val cursorW = 2.5.dp.toPx()
        val cursorX = fillW.coerceIn(cursorW * 0.5f, (w - cursorW * 0.5f).coerceAtLeast(cursorW * 0.5f))
        drawRoundRect(
          color = signalOrangeColor,
          topLeft = Offset(cursorX - cursorW * 0.5f, 0f),
          size = Size(cursorW, h),
          cornerRadius = CornerRadius(cursorW * 0.5f, cursorW * 0.5f),
        )
      }
    }

    AppThemeStyle.PRECISION_LAB -> {
      val pulseTransition = rememberInfiniteTransition(label = "lab_pulse_progress")
      val scanHeadState =
        pulseTransition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 900, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "lab_scan_head",
        )
      val tealBase = if (isDark) Color(0xFF2DD4BF) else Color(0xFF1B6B5E)
      val tealBright = if (isDark) Color(0xFF99F6E4) else Color(0xFF0F766E)
      val cellEmpty = if (isDark) Color(0xFF1C2824) else Color(0xFFD0DDD6)

      Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).then(modifier)) {
        val p = animatedProgressState.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val segments = 24
        val gap = 2.dp.toPx()
        val totalGaps = gap * (segments - 1)
        val cellW = ((w - totalGaps) / segments).coerceAtLeast(2f)
        val activeSegments = (p * segments).roundToInt().coerceIn(if (p > 0f) 1 else 0, segments)
        val scanIdx = scanHeadState.value * activeSegments.coerceAtLeast(1)
        val cellCorner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

        for (i in 0 until segments) {
          val x = i * (cellW + gap)
          val isFilled = i < activeSegments
          val color =
            if (!isFilled) {
              cellEmpty
            } else {
              val dist = abs(i - scanIdx)
              if (dist < 2.2f && activeSegments > 0) {
                val boost = (1f - dist / 2.2f).coerceIn(0f, 1f)
                androidx.compose.ui.graphics.lerp(tealBase, tealBright, boost)
              } else {
                tealBase
              }
            }
          drawRoundRect(
            color = color,
            topLeft = Offset(x, 0f),
            size = Size(cellW, h),
            cornerRadius = cellCorner,
          )
        }
      }
    }

    AppThemeStyle.MINECRAFT_PIXEL -> {
      val mcOutline = Color(0xFF1E1E1F)
      val mcGroove = Color(0xFF253022)
      val mcFill = Color(0xFF63C548)
      val mcHighlight = Color(0xFF84E06A)

      Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).then(modifier)) {
        val p = animatedProgressState.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val borderPx = 1.dp.toPx()
        val innerH = (h - borderPx * 2f).coerceAtLeast(1f)
        drawRect(color = mcOutline, topLeft = Offset.Zero, size = size)

        val segments = 10
        val filledSegments = (p * segments).toInt().coerceIn(0, segments)
        val availW = (w - borderPx * (segments + 1)).coerceAtLeast(segments.toFloat())
        val cellW = availW / segments
        val hiPx = 1.dp.toPx().coerceAtMost(innerH * 0.35f)

        for (i in 0 until segments) {
          val cx = borderPx + i * (cellW + borderPx)
          val filled = i < filledSegments || (p > 0f && i == 0 && filledSegments == 0)
          drawRect(
            color = if (filled) mcFill else mcGroove,
            topLeft = Offset(cx, borderPx),
            size = Size(cellW, innerH),
          )
          if (filled) {
            drawRect(
              color = mcHighlight,
              topLeft = Offset(cx, borderPx),
              size = Size(cellW, hiPx),
            )
          }
        }
      }
    }
  }
}

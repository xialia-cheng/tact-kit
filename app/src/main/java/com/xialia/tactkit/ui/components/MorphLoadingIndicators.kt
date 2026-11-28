package com.xialia.tactkit.ui.components

import android.graphics.LinearGradient
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as FrameworkPaint
import android.graphics.Path as AndroidPath
import android.graphics.Shader
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.ui.theme.LocalAppThemeStyle
import com.xialia.tactkit.ui.theme.TactKitCard
import com.xialia.tactkit.ui.theme.themeShape
import kotlin.math.floor

private object MorphShapeBank {
  private val pentagon =
    RoundedPolygon(
      numVertices = 5,
      radius = 1f,
      rounding = CornerRounding(0.28f),
    ).normalized()

  private val clover =
    RoundedPolygon.star(
      numVerticesPerRadius = 4,
      radius = 1f,
      innerRadius = 0.56f,
      rounding = CornerRounding(0.36f),
      innerRounding = CornerRounding(0.30f),
    ).normalized()

  private val circle =
    RoundedPolygon.circle(
      numVertices = 8,
    ).normalized()

  private val starburst =
    RoundedPolygon.star(
      numVerticesPerRadius = 8,
      radius = 1f,
      innerRadius = 0.72f,
      rounding = CornerRounding(0.20f),
      innerRounding = CornerRounding(0.20f),
    ).normalized()

  private val roundedSquare =
    RoundedPolygon.rectangle(
      width = 1.65f,
      height = 1.65f,
      rounding = CornerRounding(0.34f),
    ).normalized()

  val expressiveShapes = listOf(pentagon, clover, circle, starburst, roundedSquare)

  val expressiveMorphs =
    List(expressiveShapes.size) { index ->
      Morph(
        start = expressiveShapes[index],
        end = expressiveShapes[(index + 1) % expressiveShapes.size],
      )
    }

  val geminiSparklePolygon =
    RoundedPolygon.star(
      numVerticesPerRadius = 4,
      radius = 1f,
      innerRadius = 0.22f,
      rounding = CornerRounding(0.10f),
      innerRounding = CornerRounding(0.45f),
    ).normalized()
}

private val ExpressiveMorphEasing = CubicBezierEasing(0.34f, 1.38f, 0.64f, 1f)

/**
 * Material 3 Expressive Morphing Loading Indicator:
 * Reads animated State<Float> exclusively inside Canvas DrawScope (zero per-frame recompositions)
 * and reuses pre-allocated AndroidPath/AndroidMatrix/Stroke via `rewind()`.
 */
@Composable
fun TactKitMorphLoadingIndicator(
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  color: Color = MaterialTheme.colorScheme.primary,
  containerColor: Color = Color.Transparent,
) {
  val isMinecraft = LocalAppThemeStyle.current == AppThemeStyle.MINECRAFT_PIXEL
  val density = LocalDensity.current
  val transition = rememberInfiniteTransition(label = "tactkit_morph_loader")

  val rawPhaseState =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 5f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "morph_phase",
    )

  val spinAngleState =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 4200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "morph_spin",
    )

  val androidPath = remember { AndroidPath() }
  val composePath: Path = remember(androidPath) { androidPath.asComposePath() }
  val matrix = remember { AndroidMatrix() }
  val mcOutlineColor = Color(0xFF1E1E1F)
  val mcStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }

  if (isMinecraft) {
    val mcPulseState =
      transition.animateFloat(
        initialValue = 0.80f,
        targetValue = 1.04f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "mc_pixel_pulse",
      )
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier.size(size),
    ) {
      Canvas(modifier = Modifier.size(size)) {
        val blockSize = this.size.minDimension * 0.68f
        val mcPulse = mcPulseState.value
        val rectSize = Size(blockSize, blockSize)
        val topLeft = Offset((this.size.width - blockSize) / 2f, (this.size.height - blockSize) / 2f)
        rotate(degrees = spinAngleState.value) {
          scale(scaleX = mcPulse, scaleY = mcPulse) {
            drawRect(color = color, topLeft = topLeft, size = rectSize)
            drawRect(color = mcOutlineColor, topLeft = topLeft, size = rectSize, style = mcStroke)
          }
        }
      }
    }
    return
  }

  val pillShape = remember { RoundedCornerShape(999.dp) }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(size)
        .let { base ->
          if (containerColor.alpha > 0f) {
            base.clip(pillShape).background(containerColor)
          } else {
            base
          }
        },
  ) {
    Canvas(modifier = Modifier.size(size * 0.86f)) {
      val rawPhase = rawPhaseState.value
      val morphIdx = floor(rawPhase).toInt().coerceIn(0, MorphShapeBank.expressiveMorphs.lastIndex)
      val localProgress = (rawPhase - floor(rawPhase)).coerceIn(0f, 1f)
      val activeSpan = (localProgress / 0.78f).coerceIn(0f, 1f)
      val easedProgress = ExpressiveMorphEasing.transform(activeSpan)
      val morph = MorphShapeBank.expressiveMorphs[morphIdx]

      androidPath.rewind()
      morph.toPath(progress = easedProgress, path = androidPath)
      val drawExtent = this.size.minDimension * 0.88f
      val padX = (this.size.width - drawExtent) / 2f
      val padY = (this.size.height - drawExtent) / 2f
      matrix.reset()
      matrix.setScale(drawExtent, drawExtent)
      matrix.postTranslate(padX, padY)
      androidPath.transform(matrix)

      val stepRotation = (morphIdx + easedProgress) * 18f
      rotate(degrees = (spinAngleState.value + stepRotation) % 360f) {
        drawPath(path = composePath, color = color)
      }
    }
  }
}

/**
 * Gemini-inspired AI Sparkle Loader:
 * 4-point curved diamond star with zero per-frame allocations (pre-allocated LinearGradient, Matrix,
 * Paint, and AndroidPath) and zero per-frame recompositions.
 */
@Composable
fun GeminiSparkleLoader(
  modifier: Modifier = Modifier,
  size: Dp = 32.dp,
) {
  val isMinecraft = LocalAppThemeStyle.current == AppThemeStyle.MINECRAFT_PIXEL
  val transition = rememberInfiniteTransition(label = "gemini_sparkle_loader")

  val spinState =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2800, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "gemini_spin",
    )

  val breatheState =
    transition.animateFloat(
      initialValue = 0.82f,
      targetValue = 1.08f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "gemini_breathe",
    )

  val shimmerShiftState =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "gemini_shimmer",
    )

  val androidPath = remember { AndroidPath() }
  val matrix = remember { AndroidMatrix() }
  val shaderMatrix = remember { AndroidMatrix() }
  val frameworkPaint =
    remember {
      FrameworkPaint(FrameworkPaint.ANTI_ALIAS_FLAG).apply { style = FrameworkPaint.Style.FILL }
    }
  val gradientColors =
    remember(isMinecraft) {
      if (isMinecraft) {
        intArrayOf(
          Color(0xFF7445D6).toArgb(),
          Color(0xFF38BDF8).toArgb(),
          Color(0xFF9F6BF5).toArgb(),
        )
      } else {
        intArrayOf(
          Color(0xFF38BDF8).toArgb(),
          Color(0xFF60A5FA).toArgb(),
          Color(0xFF818CF8).toArgb(),
          Color(0xFFA855F7).toArgb(),
          Color(0xFF38BDF8).toArgb(),
        )
      }
    }
  val gradientPositions =
    remember(gradientColors) {
      FloatArray(gradientColors.size) { idx ->
        idx.toFloat() / (gradientColors.size - 1).coerceAtLeast(1)
      }
    }
  val shaderHolder = remember(gradientColors) { arrayOfNulls<LinearGradient>(1) }

  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val minSide = this.size.minDimension
    if (w <= 0f || h <= 0f) return@Canvas

    var shader = shaderHolder[0]
    if (shader == null) {
      shader =
        LinearGradient(
          0f,
          0f,
          w * 1.2f,
          h,
          gradientColors,
          gradientPositions,
          Shader.TileMode.CLAMP,
        )
      shaderHolder[0] = shader
      frameworkPaint.shader = shader
    }

    val shimmerShift = shimmerShiftState.value
    shaderMatrix.reset()
    shaderMatrix.setTranslate(w * (shimmerShift - 0.5f), 0f)
    shader.setLocalMatrix(shaderMatrix)

    androidPath.rewind()
    MorphShapeBank.geminiSparklePolygon.toPath(androidPath)
    val mainExtent = minSide * 0.78f
    matrix.reset()
    matrix.setScale(mainExtent, mainExtent)
    matrix.postTranslate((w - mainExtent) / 2f, (h - mainExtent) / 2f)
    androidPath.transform(matrix)

    val spin = spinState.value
    val breathe = breatheState.value

    rotate(degrees = spin) {
      scale(scaleX = breathe, scaleY = breathe) {
        drawIntoCanvas { canvas ->
          canvas.nativeCanvas.drawPath(androidPath, frameworkPaint)
        }
      }
    }

    val accentRadius = (minSide * 0.11f * (1.78f - breathe)).coerceAtLeast(1f)
    rotate(degrees = -spin * 0.5f) {
      drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawCircle(w * 0.82f, h * 0.20f, accentRadius, frameworkPaint)
      }
    }
  }
}

/**
 * Unified expressive busy status bar replacing indeterminate LinearProgressIndicators across workshops.
 */
@Composable
fun TactKitBusyStatusBar(
  message: String = "正在处理...",
  modifier: Modifier = Modifier,
) {
  val isMinecraft = LocalAppThemeStyle.current == AppThemeStyle.MINECRAFT_PIXEL
  Surface(
    shape = themeShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isMinecraft) 0.18f else 0.35f),
    modifier =
      modifier
        .fillMaxWidth()
        .let { base ->
          if (isMinecraft) base.border(1.dp, Color(0xFF1E1E1F), RectangleShape) else base
        },
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      TactKitMorphLoadingIndicator(
        size = 24.dp,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * Gemini-styled AI Hub busy banner for vision / PDF QA / checklist generation requests.
 */
@Composable
fun TactKitAiBusyBanner(
  message: String = "Gemini / AI 中枢正在解析多模态内容...",
  modifier: Modifier = Modifier,
) {
  TactKitCard(
    corner = 18.dp,
    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      GeminiSparkleLoader(size = 28.dp)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "✨ AI 中枢推理中",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

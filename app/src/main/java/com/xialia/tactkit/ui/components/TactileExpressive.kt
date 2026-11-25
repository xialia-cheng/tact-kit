package com.xialia.tactkit.ui.components

import android.graphics.Matrix as AndroidMatrix
import android.graphics.Path as AndroidPath
import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.xialia.tactkit.util.AppHaptics
import com.xialia.tactkit.util.rememberAppHaptics

private val TactilePressSpringFloat = spring<Float>(dampingRatio = 0.7f, stiffness = 420f)
private val TactilePressSpringDp = spring<Dp>(dampingRatio = 0.7f, stiffness = 420f)
private val TactileExpandSpringIntSize = spring<IntSize>(dampingRatio = 0.78f, stiffness = 420f)
private val TactileExpandSpringFloat = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)

fun tactileExpandVertically(): EnterTransition =
  expandVertically(animationSpec = TactileExpandSpringIntSize) +
    fadeIn(animationSpec = TactileExpandSpringFloat)

fun tactileShrinkVertically(): ExitTransition =
  shrinkVertically(animationSpec = TactileExpandSpringIntSize) +
    fadeOut(animationSpec = TactileExpandSpringFloat)

@Composable
fun RollingNumberText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
) {
  AnimatedContent(
    targetState = text,
    transitionSpec = {
      ContentTransform(
        targetContentEnter =
          slideInVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)) { it / 2 } +
            fadeIn(animationSpec = spring(stiffness = 420f)),
        initialContentExit =
          slideOutVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)) { -it / 2 } +
            fadeOut(animationSpec = spring(stiffness = 420f)),
      )
    },
    label = "rolling_number_text",
    modifier = modifier,
  ) { value ->
    Text(
      text = value,
      style = style,
      color = color,
      fontWeight = fontWeight,
      fontFamily = fontFamily,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/**
 * Pure Compose zero-bloat M3 Expressive "Q-bounce" tactile press modifier:
 * observes MutableInteractionSource.collectIsPressedAsState(), drives spring(0.7f, 420f) scaling (1f -> 0.95f),
 * dynamic corner radius morphing, and CLOCK_TICK micro-haptics.
 */
fun Modifier.tactilePress(
  interactionSource: MutableInteractionSource,
  pressedScale: Float = 0.95f,
  baseCorner: Dp = 18.dp,
  pressedCorner: Dp = 26.dp,
  enableHapticOnPress: Boolean = true,
): Modifier = composed {
  val isPressed by interactionSource.collectIsPressedAsState()
  val view = LocalView.current

  LaunchedEffect(isPressed) {
    if (isPressed && enableHapticOnPress) {
      AppHaptics.tick(view)
    }
  }

  val scale by animateFloatAsState(
    targetValue = if (isPressed) pressedScale else 1f,
    animationSpec = TactilePressSpringFloat,
    label = "tactile_scale",
  )

  val cornerRadius by animateDpAsState(
    targetValue = if (isPressed) pressedCorner else baseCorner,
    animationSpec = TactilePressSpringDp,
    label = "tactile_corner",
  )

  this.graphicsLayer {
    scaleX = scale
    scaleY = scale
    shape = RoundedCornerShape(cornerRadius)
    clip = true
  }
}

/**
 * Combined Q-bounce tactile press + clickable + SoundEffectConstants.CLICK + AppHaptics.tick(view).
 */
fun Modifier.tactileClickable(
  enabled: Boolean = true,
  pressedScale: Float = 0.95f,
  baseCorner: Dp = 18.dp,
  pressedCorner: Dp = 26.dp,
  role: Role? = Role.Button,
  onClick: () -> Unit,
): Modifier = composed {
  val interactionSource = remember { MutableInteractionSource() }
  val view = LocalView.current
  this
    .tactilePress(
      interactionSource = interactionSource,
      pressedScale = if (enabled) pressedScale else 1f,
      baseCorner = baseCorner,
      pressedCorner = pressedCorner,
      enableHapticOnPress = false,
    )
    .clickable(
      interactionSource = interactionSource,
      indication = ripple(),
      enabled = enabled,
      role = role,
      onClick = {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        AppHaptics.tick(view)
        onClick()
      },
    )
}

/**
 * M3 Expressive bouncy card container with spring(0.7f, 420f) scale + corner morphing + ripple + sound + haptics.
 */
@Composable
fun TactileCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  baseCorner: Dp = 20.dp,
  pressedCorner: Dp = 26.dp,
  pressedScale: Float = 0.95f,
  border: BorderStroke? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val view = LocalView.current

  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) pressedScale else 1f,
    animationSpec = TactilePressSpringFloat,
    label = "tactile_card_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed && enabled) pressedCorner else baseCorner,
    animationSpec = TactilePressSpringDp,
    label = "tactile_card_corner",
  )

  Surface(
    shape = RoundedCornerShape(corner),
    color = containerColor,
    contentColor = contentColor,
    border = border,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier =
      modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          enabled = enabled,
          role = Role.Button,
          onClick = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            AppHaptics.tick(view)
            onClick()
          },
        ),
  ) {
    androidx.compose.foundation.layout.Column(content = content)
  }
}

/**
 * M3 Expressive single-line tactile mode chip (1f -> 0.94f spring bounce).
 */
@Composable
fun TactileModeChip(
  selected: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val view = LocalView.current

  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1f,
    animationSpec = TactilePressSpringFloat,
    label = "chip_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed) 22.dp else if (selected) 16.dp else 14.dp,
    animationSpec = TactilePressSpringDp,
    label = "chip_corner",
  )

  Surface(
    shape = RoundedCornerShape(corner),
    color =
      if (selected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    contentColor =
      if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    modifier =
      modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          role = Role.Tab,
          onClick = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            AppHaptics.tick(view)
            onClick()
          },
        ),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

/**
 * M3 Expressive bouncy primary action button (scale 1f -> 0.95f, dynamic corner morph, sound + haptics).
 */
@Composable
fun TactileActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  containerColor: Color = MaterialTheme.colorScheme.primary,
  contentColor: Color = MaterialTheme.colorScheme.onPrimary,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  content: @Composable RowScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val view = LocalView.current

  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) 0.95f else 1f,
    animationSpec = TactilePressSpringFloat,
    label = "btn_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed && enabled) 26.dp else 16.dp,
    animationSpec = TactilePressSpringDp,
    label = "btn_corner",
  )

  Surface(
    shape = RoundedCornerShape(corner),
    color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    modifier =
      modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          enabled = enabled,
          role = Role.Button,
          onClick = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            AppHaptics.tick(view)
            onClick()
          },
        ),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
      modifier = Modifier.padding(contentPadding),
      content = content,
    )
  }
}

/**
 * RoundedPolygon-backed scalloped petal badge (4 or 8 petals) using androidx.graphics:graphics-shapes.
 */
class PetalPolygonShape(
  private val petals: Int = 8,
  private val innerRadiusRatio: Float = 0.78f,
) : Shape {
  private val polygon: RoundedPolygon =
    RoundedPolygon.star(
      numVerticesPerRadius = petals.coerceAtLeast(4),
      radius = 1f,
      innerRadius = innerRadiusRatio,
      rounding = CornerRounding(radius = 0.28f),
      innerRounding = CornerRounding(radius = 0.22f),
    )

  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: androidx.compose.ui.unit.Density,
  ): Outline {
    val androidPath = AndroidPath()
    polygon.toPath(androidPath)
    val matrix = AndroidMatrix()
    val bounds = FloatArray(4)
    polygon.calculateBounds(bounds)
    val bw = (bounds[2] - bounds[0]).coerceAtLeast(0.001f)
    val bh = (bounds[3] - bounds[1]).coerceAtLeast(0.001f)
    matrix.setTranslate(-bounds[0], -bounds[1])
    matrix.postScale(size.width / bw, size.height / bh)
    androidPath.transform(matrix)
    return Outline.Generic(androidPath.asComposePath())
  }
}

@Composable
fun PetalIconBadge(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  iconSize: Dp = size * 0.48f,
  petals: Int = 8,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
  val shape = remember(petals) { PetalPolygonShape(petals = petals) }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(size)
        .background(color = containerColor, shape = shape),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(iconSize),
    )
  }
}

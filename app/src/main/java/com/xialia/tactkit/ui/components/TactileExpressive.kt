package com.xialia.tactkit.ui.components

import android.graphics.Matrix as AndroidMatrix
import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.xialia.tactkit.util.rememberAppHaptics

/**
 * Pure Compose zero-bloat M3 Expressive "Q-bounce" tactile press modifier:
 * observes MutableInteractionSource.collectIsPressedAsState(), drives spring scaling (1f -> 0.94f),
 * dynamic corner radius morphing, and CLOCK_TICK micro-haptics.
 */
fun Modifier.tactilePress(
  interactionSource: MutableInteractionSource,
  pressedScale: Float = 0.94f,
  baseCorner: Dp = 18.dp,
  pressedCorner: Dp = 26.dp,
  enableHapticOnPress: Boolean = true,
): Modifier = composed {
  val isPressed by interactionSource.collectIsPressedAsState()
  val haptics = rememberAppHaptics()

  LaunchedEffect(isPressed) {
    if (isPressed && enableHapticOnPress) {
      haptics.tick()
    }
  }

  val scale by animateFloatAsState(
    targetValue = if (isPressed) pressedScale else 1f,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    label = "tactile_scale",
  )

  val cornerRadius by animateDpAsState(
    targetValue = if (isPressed) pressedCorner else baseCorner,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
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
 * M3 Expressive bouncy card container with spring scale + corner morphing + ripple + CLOCK_TICK haptics.
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
  val haptics = rememberAppHaptics()

  LaunchedEffect(isPressed) {
    if (isPressed && enabled) {
      haptics.tick()
    }
  }

  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) pressedScale else 1f,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    label = "tactile_card_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed && enabled) pressedCorner else baseCorner,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
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
          onClick = onClick,
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
  val haptics = rememberAppHaptics()

  LaunchedEffect(isPressed) {
    if (isPressed) haptics.tick()
  }

  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.94f else 1f,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    label = "chip_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed) 22.dp else if (selected) 16.dp else 14.dp,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
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
          onClick = onClick,
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
 * M3 Expressive bouncy primary action button (scale 1f -> 0.94f, dynamic corner morph).
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
  val haptics = rememberAppHaptics()

  LaunchedEffect(isPressed) {
    if (isPressed && enabled) haptics.tick()
  }

  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) 0.94f else 1f,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    label = "btn_scale",
  )

  val corner by animateDpAsState(
    targetValue = if (isPressed && enabled) 26.dp else 16.dp,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
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
          onClick = onClick,
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

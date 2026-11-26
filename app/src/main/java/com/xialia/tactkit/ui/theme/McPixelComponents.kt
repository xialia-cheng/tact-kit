package com.xialia.tactkit.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.datastore.AppThemeStyle

/**
 * Soft Minecraft pixel bevel:
 * Outer 1.5.dp dark slate gray (#3A3D42) border + subtle inner highlight/shadow.
 * Inverts highlight/shadow when pressed = true for physical block-press feel.
 */
fun Modifier.mcPixelBox(
  pressed: Boolean = false,
  isDark: Boolean = false,
): Modifier =
  drawMcBevel(
    outerColor = if (isDark) Color(0xFF454952) else Color(0xFF3A3D42),
    hiColor = if (isDark) Color(0xFF3C4048) else Color(0xFFF8F9F8),
    loColor = if (isDark) Color(0xFF1A1C20) else Color(0xFFC2C6C2),
    pressed = pressed,
  )

fun Modifier.mcGrassButtonBox(
  pressed: Boolean = false,
): Modifier =
  drawMcBevel(
    outerColor = Color(0xFF3A3D42),
    hiColor = Color(0xFF6BA04F),
    loColor = Color(0xFF345B22),
    pressed = pressed,
  )

fun Modifier.mcInventorySlotBox(): Modifier =
  drawMcBevel(
    outerColor = Color.Transparent,
    hiColor = Color(0xFF9EA39E),
    loColor = Color(0xFFF4F5F4),
    pressed = false,
  )

private fun Modifier.drawMcBevel(
  outerColor: Color,
  hiColor: Color,
  loColor: Color,
  pressed: Boolean,
): Modifier =
  this.drawBehind {
    val borderPx = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    if (w <= borderPx * 2f || h <= borderPx * 2f) return@drawBehind

    val topLeftColor = if (pressed) loColor else hiColor
    val bottomRightColor = if (pressed) hiColor else loColor

    val inset = if (outerColor.alpha > 0f) borderPx else 0f
    // Inner top highlight
    drawRect(
      color = topLeftColor,
      topLeft = Offset(inset, inset),
      size = Size((w - inset * 2f).coerceAtLeast(0f), borderPx),
    )
    // Inner left highlight
    drawRect(
      color = topLeftColor,
      topLeft = Offset(inset, inset),
      size = Size(borderPx, (h - inset * 2f).coerceAtLeast(0f)),
    )
    // Inner bottom shadow
    drawRect(
      color = bottomRightColor,
      topLeft = Offset(inset, h - inset - borderPx),
      size = Size((w - inset * 2f).coerceAtLeast(0f), borderPx),
    )
    // Inner right shadow
    drawRect(
      color = bottomRightColor,
      topLeft = Offset(w - inset - borderPx, inset),
      size = Size(borderPx, (h - inset * 2f).coerceAtLeast(0f)),
    )

    if (outerColor.alpha > 0f) {
      drawRect(outerColor, Offset(0f, 0f), Size(w, borderPx))
      drawRect(outerColor, Offset(0f, h - borderPx), Size(w, borderPx))
      drawRect(outerColor, Offset(0f, 0f), Size(borderPx, h))
      drawRect(outerColor, Offset(w - borderPx, 0f), Size(borderPx, h))
    }
  }

fun Modifier.minecraftPixelBevel(
  pressed: Boolean = false,
  isDark: Boolean = false,
): Modifier = mcPixelBox(pressed = pressed, isDark = isDark)

@Composable
fun McXpBar(
  progress: Float,
  modifier: Modifier = Modifier,
  segments: Int = 12,
) {
  val clamped = progress.coerceIn(0f, 1f)
  val filledSegments = (clamped * segments).toInt().coerceIn(0, segments)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(10.dp)
        .background(Color(0xFF3A3D42), RectangleShape)
        .padding(1.5.dp),
  ) {
    for (i in 0 until segments) {
      val filled = i < filledSegments || (clamped > 0f && i == 0 && filledSegments == 0)
      Box(
        modifier =
          Modifier.weight(1f)
            .height(7.dp)
            .padding(horizontal = 0.5.dp)
            .background(
              if (filled) Color(0xFF5EA83B) else Color(0xFF2B3328),
              RectangleShape,
            ),
      )
    }
  }
}

@Composable
fun McGrassDirtTrim(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(2.dp)
          .background(Color(0xFF4A7C32), RectangleShape),
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(2.dp)
          .background(Color(0xFF7D5D43), RectangleShape),
    )
  }
}

@Composable
fun isMcPixelTheme(): Boolean = LocalAppThemeStyle.current == AppThemeStyle.MINECRAFT_PIXEL

@Composable
fun themeShape(corner: Dp): Shape =
  if (isMcPixelTheme()) RectangleShape else RoundedCornerShape(corner)

@Composable
fun TactKitCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  corner: Dp = 18.dp,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  contentColor: Color = contentColorFor(containerColor),
  border: BorderStroke? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val style = LocalAppThemeStyle.current
  val isMc = style == AppThemeStyle.MINECRAFT_PIXEL
  val isDark = isSystemInDarkTheme()
  if (isMc) {
    val pressed by interactionSource.collectIsPressedAsState()
    val clickModifier =
      if (onClick != null) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
      } else {
        Modifier
      }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      Box(
        modifier =
          modifier
            .clip(RectangleShape)
            .background(containerColor, RectangleShape)
            .mcPixelBox(pressed = pressed && onClick != null, isDark = isDark)
            .then(clickModifier),
        content = content,
      )
    }
  } else {
    val resolvedBorder = border ?: themeCardBorder(style, isDark, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
      Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        border = resolvedBorder,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
      ) {
        Box(content = content)
      }
    } else {
      Surface(
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        border = resolvedBorder,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
      ) {
        Box(content = content)
      }
    }
  }
}

@Composable
fun TactKitChip(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  corner: Dp = 999.dp,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  contentColor: Color = contentColorFor(containerColor),
  border: BorderStroke? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val isMc = isMcPixelTheme()
  val isDark = isSystemInDarkTheme()
  if (isMc) {
    val pressed by interactionSource.collectIsPressedAsState()
    val clickModifier =
      if (onClick != null) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
      } else {
        Modifier
      }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      Box(
        modifier =
          modifier
            .clip(RectangleShape)
            .background(containerColor, RectangleShape)
            .mcPixelBox(pressed = pressed && onClick != null, isDark = isDark)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
        content = content,
      )
    }
  } else {
    if (onClick != null) {
      Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
      ) {
        Box(contentAlignment = Alignment.Center, content = content)
      }
    } else {
      Surface(
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
      ) {
        Box(contentAlignment = Alignment.Center, content = content)
      }
    }
  }
}

@Composable
fun TactKitThumbnailBox(
  modifier: Modifier = Modifier,
  corner: Dp = 14.dp,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  content: @Composable BoxScope.() -> Unit,
) {
  val isMc = isMcPixelTheme()
  if (isMc) {
    Box(
      modifier =
        modifier
          .clip(RectangleShape)
          .background(containerColor, RectangleShape)
          .mcInventorySlotBox(),
      contentAlignment = Alignment.Center,
      content = content,
    )
  } else {
    Box(
      modifier =
        modifier
          .clip(RoundedCornerShape(corner))
          .background(containerColor, RoundedCornerShape(corner)),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}


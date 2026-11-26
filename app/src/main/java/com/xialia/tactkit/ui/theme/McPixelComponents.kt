package com.xialia.tactkit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Classic 3-layer Minecraft pixel bevel:
 * Outer 2.dp black/deep-charcoal border + inner 2.dp top-left highlight + inner 2.dp bottom-right shadow.
 * Inverts highlight/shadow when pressed = true for physical block-press feel.
 */
fun Modifier.mcPixelBox(
  pressed: Boolean = false,
  isDark: Boolean = false,
): Modifier =
  drawMcBevel(
    outerColor = if (isDark) Color(0xFF121215) else Color(0xFF1E1E1E),
    hiColor = if (isDark) Color(0xFF484852) else Color.White,
    loColor = if (isDark) Color(0xFF19191D) else Color(0xFF555555),
    pressed = pressed,
  )

fun Modifier.mcGrassButtonBox(
  pressed: Boolean = false,
): Modifier =
  drawMcBevel(
    outerColor = Color(0xFF1E1E1E),
    hiColor = Color(0xFF7CE352),
    loColor = Color(0xFF245217),
    pressed = pressed,
  )

fun Modifier.mcInventorySlotBox(): Modifier =
  drawMcBevel(
    outerColor = Color.Transparent,
    hiColor = Color(0xFF373737),
    loColor = Color.White,
    pressed = false,
  )

private fun Modifier.drawMcBevel(
  outerColor: Color,
  hiColor: Color,
  loColor: Color,
  pressed: Boolean,
): Modifier =
  this.drawBehind {
    val borderPx = 2.dp.toPx()
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
        .background(Color(0xFF1E1E1E), RectangleShape)
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
              if (filled) Color(0xFF80FF20) else Color(0xFF1B2418),
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
          .background(Color(0xFF5B8731), RectangleShape),
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(2.dp)
          .background(Color(0xFF866043), RectangleShape),
    )
  }
}

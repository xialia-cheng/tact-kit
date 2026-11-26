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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

enum class OreUiTone {
  SLATE_LIGHT,
  SLATE_DARK,
  GREEN,
  PURPLE,
}

/**
 * Standard Minecraft Bedrock OreUI 3D bevel:
 * - Outer 1.dp thin outline (#1E1E1F or #141518 in dark mode)
 * - Inner top 1.5.dp highlight
 * - Inner bottom 3.dp thick bevel (narrows to 1.dp when pressed; no thick side shadows)
 */
fun Modifier.oreUiBevel(
  outerColor: Color = Color(0xFF1E1E1F),
  topHighlight: Color = Color.White,
  bottomShadow: Color = Color(0xFF8E9095),
  pressed: Boolean = false,
): Modifier =
  this.drawBehind {
    val outerPx = 1.dp.toPx()
    val topPx = 1.5.dp.toPx()
    val bottomPx = (if (pressed) 1.dp else 3.dp).toPx()
    val w = size.width
    val h = size.height
    if (w <= outerPx * 2f || h <= outerPx * 2f) return@drawBehind

    val inset = if (outerColor.alpha > 0f) outerPx else 0f
    val innerW = (w - inset * 2f).coerceAtLeast(0f)

    // Top inner highlight (1.5.dp)
    if (topHighlight.alpha > 0f) {
      drawRect(
        color = topHighlight,
        topLeft = Offset(inset, inset),
        size = Size(innerW, topPx),
      )
    }

    // Bottom inner thick bevel (3.dp -> 1.dp when pressed)
    if (bottomShadow.alpha > 0f) {
      drawRect(
        color = bottomShadow,
        topLeft = Offset(inset, (h - inset - bottomPx).coerceAtLeast(inset)),
        size = Size(innerW, bottomPx),
      )
    }

    // Outer 1.dp thin outline
    if (outerColor.alpha > 0f) {
      drawRect(outerColor, Offset(0f, 0f), Size(w, outerPx))
      drawRect(outerColor, Offset(0f, h - outerPx), Size(w, outerPx))
      drawRect(outerColor, Offset(0f, 0f), Size(outerPx, h))
      drawRect(outerColor, Offset(w - outerPx, 0f), Size(outerPx, h))
    }
  }

fun Modifier.mcPixelBox(
  pressed: Boolean = false,
  isDark: Boolean = false,
): Modifier =
  oreUiBevel(
    outerColor = if (isDark) Color(0xFF141518) else Color(0xFF1E1E1F),
    topHighlight = if (isDark) Color(0xFF3E4148) else Color.White,
    bottomShadow = if (isDark) Color(0xFF141518) else Color(0xFF8E9095),
    pressed = pressed,
  )

fun Modifier.mcGrassButtonBox(
  pressed: Boolean = false,
): Modifier =
  oreUiBevel(
    outerColor = Color(0xFF1E1E1F),
    topHighlight = Color(0xFF63C548),
    bottomShadow = Color(0xFF255917),
    pressed = pressed,
  )

fun Modifier.mcPurpleButtonBox(
  pressed: Boolean = false,
): Modifier =
  oreUiBevel(
    outerColor = Color(0xFF1E1E1F),
    topHighlight = Color(0xFF9F6BF5),
    bottomShadow = Color(0xFF4D279B),
    pressed = pressed,
  )

fun Modifier.mcInventorySlotBox(
  isDark: Boolean = false,
): Modifier =
  this.border(
    width = 1.dp,
    color = if (isDark) Color(0xFF141518) else Color(0xFF1E1E1F),
    shape = RectangleShape,
  )

fun Modifier.minecraftPixelBevel(
  pressed: Boolean = false,
  isDark: Boolean = false,
): Modifier = mcPixelBox(pressed = pressed, isDark = isDark)

@Composable
fun OreUiSurface(
  modifier: Modifier = Modifier,
  tone: OreUiTone = if (isSystemInDarkTheme()) OreUiTone.SLATE_DARK else OreUiTone.SLATE_LIGHT,
  onClick: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  content: @Composable BoxScope.() -> Unit,
) {
  val pressed by interactionSource.collectIsPressedAsState()
  val (bg, fg, outer, hi, lo) = oreUiPalette(tone)
  val clickMod =
    if (onClick != null) {
      Modifier.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      )
    } else {
      Modifier
    }
  CompositionLocalProvider(LocalContentColor provides fg) {
    Box(
      modifier =
        modifier
          .graphicsLayer {
            if (onClick != null && pressed) {
              translationY = 2.dp.toPx()
            }
          }
          .background(bg, RectangleShape)
          .oreUiBevel(
            outerColor = outer,
            topHighlight = hi,
            bottomShadow = lo,
            pressed = onClick != null && pressed,
          )
          .then(clickMod),
      content = content,
    )
  }
}

@Composable
fun OreUiButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tone: OreUiTone = OreUiTone.GREEN,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  content: @Composable BoxScope.() -> Unit,
) {
  val pressed by interactionSource.collectIsPressedAsState()
  val (baseBg, baseFg, outer, hi, lo) = oreUiPalette(tone)
  val bg = if (enabled) baseBg else Color(0xFF8E9095)
  val fg = if (enabled) baseFg else Color(0xFFE6E8EB)
  CompositionLocalProvider(LocalContentColor provides fg) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        modifier
          .graphicsLayer {
            if (enabled && pressed) {
              translationY = 2.dp.toPx()
            }
          }
          .background(bg, RectangleShape)
          .oreUiBevel(
            outerColor = outer,
            topHighlight = if (enabled) hi else Color(0xFFB0B2B7),
            bottomShadow = if (enabled) lo else Color(0xFF6A6C70),
            pressed = enabled && pressed,
          )
          .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
          ),
      content = content,
    )
  }
}

private data class OreUiPalette(
  val bg: Color,
  val fg: Color,
  val outer: Color,
  val topHighlight: Color,
  val bottomShadow: Color,
)

private fun oreUiPalette(tone: OreUiTone): OreUiPalette =
  when (tone) {
    OreUiTone.SLATE_LIGHT ->
      OreUiPalette(
        bg = Color(0xFFD0D1D4),
        fg = Color(0xFF1E1E1F),
        outer = Color(0xFF1E1E1F),
        topHighlight = Color.White,
        bottomShadow = Color(0xFF8E9095),
      )
    OreUiTone.SLATE_DARK ->
      OreUiPalette(
        bg = Color(0xFF2B2D32),
        fg = Color(0xFFECEEF0),
        outer = Color(0xFF141518),
        topHighlight = Color(0xFF3E4148),
        bottomShadow = Color(0xFF141518),
      )
    OreUiTone.GREEN ->
      OreUiPalette(
        bg = Color(0xFF3C8527),
        fg = Color.White,
        outer = Color(0xFF1E1E1F),
        topHighlight = Color(0xFF63C548),
        bottomShadow = Color(0xFF255917),
      )
    OreUiTone.PURPLE ->
      OreUiPalette(
        bg = Color(0xFF7445D6),
        fg = Color.White,
        outer = Color(0xFF1E1E1F),
        topHighlight = Color(0xFF9F6BF5),
        bottomShadow = Color(0xFF4D279B),
      )
  }

/**
 * 7050.jpg style connected dark slate tab bar (#313236 / #48494E with 1.dp #1E1E1F dividers
 * and centered 2.dp pure white bottom indicator bar on selected tab).
 */
@Composable
fun OreUiSegmentedTabRow(
  items: List<Pair<String, Boolean>>,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(38.dp)
        .background(Color(0xFF313236), RectangleShape)
        .border(1.dp, Color(0xFF1E1E1F), RectangleShape),
  ) {
    items.forEachIndexed { index, (label, selected) ->
      if (index > 0) {
        Box(
          modifier =
            Modifier.width(1.dp)
              .fillMaxHeight()
              .background(Color(0xFF1E1E1F), RectangleShape),
        )
      }
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .background(
              if (selected) Color(0xFF48494E) else Color(0xFF313236),
              RectangleShape,
            )
            .clickable { onSelect(index) },
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          color = if (selected) Color.White else Color(0xFFD0D1D4),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
          Box(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth(0.56f)
                .height(2.dp)
                .background(Color.White, RectangleShape),
          )
        }
      }
    }
  }
}

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
        .height(9.dp)
        .background(Color(0xFF1E1E1F), RectangleShape)
        .padding(1.dp),
  ) {
    for (i in 0 until segments) {
      val filled = i < filledSegments || (clamped > 0f && i == 0 && filledSegments == 0)
      Box(
        modifier =
          Modifier.weight(1f)
            .height(7.dp)
            .padding(horizontal = 0.5.dp)
            .background(
              if (filled) Color(0xFF63C548) else Color(0xFF253022),
              RectangleShape,
            ),
      )
    }
  }
}

@Composable
fun McGrassDirtTrim(modifier: Modifier = Modifier) {
  val isDark = isSystemInDarkTheme()
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(if (isDark) Color(0xFF141518) else Color(0xFFA0A2A6), RectangleShape),
  )
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


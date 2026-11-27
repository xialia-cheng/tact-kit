package com.xialia.tactkit.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.xialia.tactkit.ui.components.TactileSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileCard
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.isTactKitDarkTheme
import com.xialia.tactkit.ui.theme.minecraftPixelBevel
import com.xialia.tactkit.util.rememberAppHaptics

private data class ThemePreviewPalette(
  val bg: Color,
  val card: Color,
  val primary: Color,
  val accent: Color,
  val text: Color,
  val corner: Dp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
  preferences: UserPreferences,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onAppThemeStyleChange: (AppThemeStyle) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val haptics = rememberAppHaptics()
  val isDark = isTactKitDarkTheme(preferences.themeMode)

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
          title = {
            Text(
              "主题风格画廊",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("theme_gallery"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("theme_gallery_back_btn"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          contentPadding = PaddingValues(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
        ) {
          item(span = { GridItemSpan(2) }, key = "theme_mode_controls") {
            Surface(
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text(
                  text = "明暗模式",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  ThemeMode.entries.forEach { mode ->
                    TactileModeChip(
                      selected = preferences.themeMode == mode,
                      label = mode.label,
                      onClick = { onThemeModeChange(mode) },
                      modifier = Modifier.weight(1f).testTag("gallery_mode_${mode.name}"),
                    )
                  }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                      Text(
                        text = "Dynamic Color 动态取色",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                      )
                      Text(
                        text =
                          if (preferences.appThemeStyle == AppThemeStyle.CLASSIC_DEFAULT) {
                            "跟随 Android 12+ 系统壁纸生成专属莫奈配色"
                          } else {
                            "当前主题优先应用专属色调（切回「默认经典」后生效）"
                          },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    TactileSwitch(
                      checked = preferences.dynamicColor,
                      onCheckedChange = {
                        haptics.tick()
                        onDynamicColorChange(it)
                      },
                      modifier = Modifier.testTag("gallery_dynamic_color_switch"),
                    )
                  }
                }
              }
            }
          }

          items(AppThemeStyle.entries, key = { it.name }) { style ->
            val selected = preferences.appThemeStyle == style
            ThemeStylePreviewCard(
              style = style,
              selected = selected,
              isDark = isDark,
              onClick = {
                haptics.confirm()
                onAppThemeStyleChange(style)
              },
            )
          }

          item(span = { GridItemSpan(2) }, key = "gallery_footer_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ThemeStylePreviewCard(
  style: AppThemeStyle,
  selected: Boolean,
  isDark: Boolean,
  onClick: () -> Unit,
) {
  val palette = previewPaletteFor(style, isDark)
  val border =
    if (selected) {
      BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
      BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

  TactileCard(
    onClick = onClick,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    border = border,
    baseCorner = 20.dp,
    pressedCorner = 26.dp,
    modifier = Modifier.fillMaxWidth().testTag("theme_style_card_${style.name}"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(12.dp),
    ) {
      // Mini phone desktop thumbnail
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(if (style == AppThemeStyle.MINECRAFT_PIXEL) 0.dp else 14.dp))
            .background(palette.bg)
            .padding(10.dp),
      ) {
        if (style == AppThemeStyle.GLACIER_BENTO) {
          // Asymmetric Bento mini preview
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxSize()
                  .clip(RoundedCornerShape(palette.corner))
                  .background(palette.primary.copy(alpha = 0.22f))
                  .border(
                    width = 1.dp,
                    brush =
                      Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f))
                      ),
                    shape = RoundedCornerShape(palette.corner),
                  )
                  .padding(6.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(16.dp, 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primary)
              )
            }
            Column(
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(palette.corner))
                    .background(palette.card)
                    .border(
                      width = 1.dp,
                      brush =
                        Brush.verticalGradient(
                          listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f))
                        ),
                      shape = RoundedCornerShape(palette.corner),
                    )
              )
              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(palette.corner))
                    .background(palette.card)
                    .border(
                      width = 1.dp,
                      brush =
                        Brush.verticalGradient(
                          listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f))
                        ),
                      shape = RoundedCornerShape(palette.corner),
                    )
              )
            }
          }
        } else {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            // Top bar pill
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Box(
                modifier =
                  Modifier.size(34.dp, 8.dp)
                    .clip(RoundedCornerShape(palette.corner))
                    .background(palette.text.copy(alpha = 0.78f))
              )
              Box(
                modifier =
                  Modifier.size(18.dp, 8.dp)
                    .clip(RoundedCornerShape(palette.corner))
                    .background(palette.primary)
              )
            }

            // Main mini card
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .weight(1f)
                  .clip(RoundedCornerShape(palette.corner))
                  .background(palette.card)
                  .let {
                    when (style) {
                      AppThemeStyle.MINECRAFT_PIXEL -> it.minecraftPixelBevel(isDark = isDark)
                      AppThemeStyle.SWISS_CRAFT, AppThemeStyle.PRECISION_LAB ->
                        it.border(1.dp, palette.text.copy(alpha = 0.18f), RoundedCornerShape(palette.corner))
                      else -> it
                    }
                  }
                  .padding(8.dp),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                  modifier =
                    Modifier.fillMaxWidth(0.65f)
                      .height(7.dp)
                      .clip(RoundedCornerShape(palette.corner))
                      .background(palette.primary)
                )
                Box(
                  modifier =
                    Modifier.fillMaxWidth(0.9f)
                      .height(5.dp)
                      .clip(RoundedCornerShape(palette.corner))
                      .background(palette.text.copy(alpha = 0.25f))
                )
              }
            }

            // Bottom floating ball indicator
            Row(
              horizontalArrangement = Arrangement.End,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Box(
                modifier =
                  Modifier.size(18.dp)
                    .clip(RoundedCornerShape(if (style == AppThemeStyle.MINECRAFT_PIXEL) 0.dp else 6.dp))
                    .background(palette.accent)
                    .let {
                      if (style == AppThemeStyle.MINECRAFT_PIXEL) it.minecraftPixelBevel(isDark = isDark)
                      else it
                    }
              )
            }
          }
        }

        // Animated checkmark badge
        androidx.compose.animation.AnimatedVisibility(
          visible = selected,
          enter = scaleIn() + fadeIn(),
          exit = scaleOut() + fadeOut(),
          modifier = Modifier.align(Alignment.TopEnd),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = style.label,
          style = MaterialTheme.typography.titleSmall,
          fontFamily = if (style == AppThemeStyle.MINECRAFT_PIXEL) FontFamily.Monospace else null,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (selected || style == AppThemeStyle.CLASSIC_DEFAULT) {
          Spacer(modifier = Modifier.width(4.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Text(
              text = if (selected) "当前" else "默认",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }

      Text(
        text = style.tagline,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun previewPaletteFor(style: AppThemeStyle, isDark: Boolean): ThemePreviewPalette =
  when (style) {
    AppThemeStyle.CLASSIC_DEFAULT ->
      if (isDark) {
        ThemePreviewPalette(Color(0xFF11131C), Color(0xFF1B1E2B), Color(0xFF8C9EFF), Color(0xFF5C6BC0), Color(0xFFE6E8F0), 8.dp)
      } else {
        ThemePreviewPalette(Color(0xFFF6F7FB), Color(0xFFFFFFFF), Color(0xFF4F5BD5), Color(0xFF7986CB), Color(0xFF1A1C24), 8.dp)
      }
    AppThemeStyle.GLACIER_BENTO ->
      if (isDark) {
        ThemePreviewPalette(Color(0xFF0A0E17), Color(0xFF111827), Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFFF1F5F9), 8.dp)
      } else {
        ThemePreviewPalette(Color(0xFFF7F9FC), Color(0xFFFFFFFF), Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF0F172A), 8.dp)
      }
    AppThemeStyle.SWISS_CRAFT ->
      if (isDark) {
        ThemePreviewPalette(Color(0xFF121316), Color(0xFF1A1C21), Color(0xFF6B93F5), Color(0xFFF47B56), Color(0xFFF1F0EC), 6.dp)
      } else {
        ThemePreviewPalette(Color(0xFFF6F5F1), Color(0xFFFFFFFF), Color(0xFF2B59C3), Color(0xFFD95D39), Color(0xFF18191C), 6.dp)
      }
    AppThemeStyle.PRECISION_LAB ->
      if (isDark) {
        ThemePreviewPalette(Color(0xFF0E1412), Color(0xFF151E1B), Color(0xFF2DD4BF), Color(0xFF34D399), Color(0xFFE9F2EE), 5.dp)
      } else {
        ThemePreviewPalette(Color(0xFFF3F6F4), Color(0xFFFFFFFF), Color(0xFF1B6B5E), Color(0xFF0F766E), Color(0xFF111C18), 5.dp)
      }
    AppThemeStyle.MINECRAFT_PIXEL ->
      if (isDark) {
        ThemePreviewPalette(Color(0xFF1C1D20), Color(0xFF2B2D32), Color(0xFF3C8527), Color(0xFF7445D6), Color(0xFFECEEF0), 0.dp)
      } else {
        ThemePreviewPalette(Color(0xFFE6E8EB), Color(0xFFD0D1D4), Color(0xFF3C8527), Color(0xFF7445D6), Color(0xFF1E1E1F), 0.dp)
      }
  }

package com.xialia.tactkit.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.xialia.tactkit.R
import com.xialia.tactkit.domain.ToolCatalog
import com.xialia.tactkit.domain.ToolCategory
import com.xialia.tactkit.domain.ToolItem
import com.xialia.tactkit.ui.theme.minecraftPixelBevel
import com.xialia.tactkit.util.AppHaptics
import com.xialia.tactkit.util.rememberAppHaptics

/**
 * Compact 56.dp rounded-square (16.dp) Liquid Glass Tool Ball & 28.dp top-corner Bottom Sheet Drawer.
 */
@Composable
fun LiquidGlassToolBallOverlay(
  backdrop: Backdrop,
  isPowerSaveMode: Boolean,
  pinnedToolIds: List<String>,
  onLaunchTool: (ToolItem) -> Unit,
  onTogglePinTool: (String) -> Unit,
  modifier: Modifier = Modifier,
  ballModifier: Modifier = Modifier,
) {
  val haptics = rememberAppHaptics()
  val view = LocalView.current
  var isExpanded by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }

  val useLiquidShader = !isPowerSaveMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f

  val expandedCategories = remember {
    mutableStateMapOf<ToolCategory, Boolean>().apply {
      ToolCategory.entries.forEach { put(it, it == ToolCategory.DOCUMENT_OFFICE) }
    }
  }

  if (isExpanded) {
    BackHandler {
      haptics.tick()
      isExpanded = false
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
      exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              haptics.tick()
              isExpanded = false
            }
      )
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter =
        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
          slideInVertically(initialOffsetY = { it / 3 }),
      exit =
        fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
          slideOutVertically(targetOffsetY = { it / 3 }),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
      val glassTint =
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (isDark) 0.84f else 0.82f)
      val fallbackTint = MaterialTheme.colorScheme.surfaceContainerLowest
      val highlightBorder =
        if (isDark) {
          Color.White.copy(alpha = 0.14f)
        } else {
          Color.White.copy(alpha = 0.72f)
        }

      val sheetModifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxWidth()
          .heightIn(max = 640.dp)
          .glassSurfaceModifier(
            useLiquidShader = useLiquidShader,
            backdrop = backdrop,
            shape = sheetShape,
            glassTint = glassTint,
            fallbackTint = fallbackTint,
            borderColor = highlightBorder,
          )
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { /* Consume taps inside sheet */ }
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 14.dp)
          .testTag("tool_drawer_panel")

      Column(modifier = sheetModifier) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.width(36.dp)
                .height(4.dp)
                .clip(ContinuousCapsule())
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
          )
        }

        // Clean Header: only title "全部工具" + close button (zero technical jargon subtitle)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "全部工具",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          IconButton(
            onClick = {
              haptics.tick()
              isExpanded = false
            },
            modifier = Modifier.testTag("close_tool_drawer_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_close),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Compact 50.dp single-line search bar
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
              contentAlignment = Alignment.CenterStart,
              modifier = Modifier.weight(1f),
            ) {
              if (searchQuery.isEmpty()) {
                Text(
                  text = "搜索工具...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle =
                  MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("tool_drawer_search_input"),
              )
            }
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_close),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }

        val pinnedTools =
          remember(pinnedToolIds) { pinnedToolIds.mapNotNull { ToolCatalog.findById(it) } }

        // Only show pinned tools strip when user actually pinned >= 1 tool (no empty placeholder, no cap)
        if (pinnedTools.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp),
            modifier = Modifier.fillMaxWidth().testTag("pinned_tools_row"),
          ) {
            items(pinnedTools, key = { "pinned_${it.id}" }) { tool ->
              PinnedToolSlotChip(
                tool = tool,
                onClick = {
                  haptics.tick()
                  isExpanded = false
                  onLaunchTool(tool)
                },
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val normalizedQuery = searchQuery.trim().lowercase()
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f, fill = false),
        ) {
          ToolCategory.entries.forEach { category ->
            val toolsInCategory =
              ToolCatalog.allTools.filter { tool ->
                tool.category == category &&
                  (normalizedQuery.isEmpty() ||
                    tool.name.lowercase().contains(normalizedQuery) ||
                    tool.subtitle.lowercase().contains(normalizedQuery) ||
                    category.title.lowercase().contains(normalizedQuery))
              }
            if (toolsInCategory.isNotEmpty()) {
              item(key = category.name) {
                val isCatExpanded =
                  if (normalizedQuery.isNotEmpty()) true else (expandedCategories[category] ?: true)
                CategoryToolCard(
                  category = category,
                  tools = toolsInCategory,
                  expanded = isCatExpanded,
                  pinnedIds = pinnedToolIds,
                  onToggleExpand = {
                    haptics.tick()
                    expandedCategories[category] = !isCatExpanded
                  },
                  onToolClick = { tool ->
                    haptics.tick()
                    isExpanded = false
                    onLaunchTool(tool)
                  },
                  onTogglePin = { toolId ->
                    haptics.confirm()
                    onTogglePinTool(toolId)
                  },
                )
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = !isExpanded,
      enter = fadeIn() + scaleIn(initialScale = 0.85f),
      exit = fadeOut() + scaleOut(targetScale = 0.85f),
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(end = 16.dp, bottom = 16.dp),
    ) {
      val themeStyle = com.xialia.tactkit.ui.theme.LocalAppThemeStyle.current
      val isMinecraft = themeStyle == com.xialia.tactkit.data.datastore.AppThemeStyle.MINECRAFT_PIXEL
      val orbCorner = if (isMinecraft) 0.dp else 16.dp
      val orbShape = RoundedCornerShape(orbCorner)
      val ballTint =
        when (themeStyle) {
          com.xialia.tactkit.data.datastore.AppThemeStyle.GLACIER_BENTO ->
            Color(0xFF38BDF8).copy(alpha = if (isDark) 0.28f else 0.26f)
          else ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.56f else 0.62f)
        }
      val fallbackBallTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
      val highlightBorder =
        when (themeStyle) {
          com.xialia.tactkit.data.datastore.AppThemeStyle.GLACIER_BENTO ->
            Color(0xFFBAE6FD).copy(alpha = if (isDark) 0.45f else 0.92f)
          else ->
            if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.72f)
        }

      val orbInteraction = remember { MutableInteractionSource() }
      val orbPressed by orbInteraction.collectIsPressedAsState()
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          ballModifier
            .size(56.dp)
            .tactilePress(
              interactionSource = orbInteraction,
              pressedScale = 0.90f,
              baseCorner = orbCorner,
              pressedCorner = if (isMinecraft) 0.dp else 24.dp,
              enableHapticOnPress = false,
            )
            .glassSurfaceModifier(
              useLiquidShader = useLiquidShader && !isMinecraft,
              backdrop = backdrop,
              shape = orbShape,
              glassTint = ballTint,
              fallbackTint = fallbackBallTint,
              borderColor = highlightBorder,
            )
            .let {
              if (isMinecraft) {
                it.minecraftPixelBevel(
                  pressed = orbPressed,
                  isDark = isDark,
                )
              } else {
                it
              }
            }
            .clickable(
              interactionSource = orbInteraction,
              indication = null,
            ) {
              AppHaptics.click(view)
              isExpanded = true
            }
            .testTag("liquid_glass_tool_ball"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Widgets,
          contentDescription = stringResource(R.string.cd_tool_ball),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

@Composable
private fun PinnedToolSlotChip(
  tool: ToolItem,
  onClick: () -> Unit,
) {
  val chipInteraction = remember { MutableInteractionSource() }
  Surface(
    onClick = onClick,
    interactionSource = chipInteraction,
    shape = ContinuousCapsule(),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier =
      Modifier.tactilePress(
          interactionSource = chipInteraction,
          baseCorner = 24.dp,
          pressedCorner = 28.dp,
          enableHapticOnPress = false,
        )
        .testTag("pinned_tool_${tool.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Icon(
        imageVector = tool.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = tool.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CategoryToolCard(
  category: ToolCategory,
  tools: List<ToolItem>,
  expanded: Boolean,
  pinnedIds: List<String>,
  onToggleExpand: () -> Unit,
  onToolClick: (ToolItem) -> Unit,
  onTogglePin: (String) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("category_header_${category.name}"),
      ) {
        Icon(
          imageVector = category.icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = category.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
          shape = ContinuousCapsule(),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = "${tools.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (expanded) "收起分类" else "展开分类",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = expanded,
        enter = tactileExpandVertically(),
        exit = tactileShrinkVertically(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 8.dp),
        ) {
          tools.forEach { tool ->
            val isPinned = tool.id in pinnedIds
            TactileCard(
              onClick = { onToolClick(tool) },
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              baseCorner = 16.dp,
              pressedCorner = 24.dp,
              pressedScale = 0.96f,
              modifier = Modifier.fillMaxWidth().testTag("tool_item_${tool.id}"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  modifier = Modifier.size(42.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = tool.icon,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(21.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                IconButton(
                  onClick = { onTogglePin(tool.id) },
                  modifier = Modifier.testTag("pin_tool_${tool.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.cd_pin),
                    tint =
                      if (isPinned) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                      },
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun Modifier.glassSurfaceModifier(
  useLiquidShader: Boolean,
  backdrop: Backdrop,
  shape: Shape,
  glassTint: Color,
  fallbackTint: Color,
  borderColor: Color,
): Modifier {
  return if (useLiquidShader) {
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
          vibrancy()
          blur(14.dp.toPx())
          lens(16.dp.toPx(), 28.dp.toPx())
        },
        onDrawSurface = { drawRect(glassTint) },
      )
      .border(width = 1.dp, color = borderColor, shape = shape)
  } else {
    this.clip(shape)
      .background(fallbackTint, shape)
      .border(width = 1.dp, color = borderColor, shape = shape)
  }
}

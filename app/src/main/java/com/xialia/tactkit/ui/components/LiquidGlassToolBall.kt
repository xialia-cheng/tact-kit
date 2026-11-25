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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
) {
  val haptics = rememberAppHaptics()
  var isExpanded by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var upcomingToolDialog by remember { mutableStateOf<ToolItem?>(null) }

  val useLiquidShader = !isPowerSaveMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f

  val expandedCategories = remember {
    mutableStateMapOf<ToolCategory, Boolean>().apply {
      ToolCategory.entries.forEach { put(it, true) }
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
          .heightIn(max = 620.dp)
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
          modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
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

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "工坊工具抽屉",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                if (useLiquidShader) "AGSL 实时折射液态玻璃 · 9 大工具就绪"
                else "Material 3 省电半透明模式 · 9 大工具就绪",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
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

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("搜索 PDF、图片隐私、AI、语音备忘...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close))
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().testTag("tool_drawer_search_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val pinnedTools =
          remember(pinnedToolIds) { pinnedToolIds.mapNotNull { ToolCatalog.findById(it) } }

        Text(
          text = "固定快捷槽位 (${pinnedTools.size}/4)",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (pinnedTools.isEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "点击下方工具卡片的图钉图标，即可固定到最多 4 个快捷槽位。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp),
          ) {
            items(pinnedTools, key = { "pinned_${it.id}" }) { tool ->
              PinnedToolSlotChip(
                tool = tool,
                onClick = {
                  haptics.tick()
                  if (tool.isAvailable) {
                    isExpanded = false
                    onLaunchTool(tool)
                  } else {
                    upcomingToolDialog = tool
                  }
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
                    if (tool.isAvailable) {
                      isExpanded = false
                      onLaunchTool(tool)
                    } else {
                      upcomingToolDialog = tool
                    }
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
      val orbShape = RoundedCornerShape(16.dp)
      val ballTint =
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.56f else 0.62f)
      val fallbackBallTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
      val highlightBorder =
        if (isDark) {
          Color.White.copy(alpha = 0.24f)
        } else {
          Color.White.copy(alpha = 0.72f)
        }

      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(56.dp)
            .glassSurfaceModifier(
              useLiquidShader = useLiquidShader,
              backdrop = backdrop,
              shape = orbShape,
              glassTint = ballTint,
              fallbackTint = fallbackBallTint,
              borderColor = highlightBorder,
            )
            .clickable {
              haptics.tick()
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

  upcomingToolDialog?.let { tool ->
    AlertDialog(
      onDismissRequest = { upcomingToolDialog = null },
      icon = {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      title = { Text("${tool.name} · 即将上线") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = tool.subtitle,
            style = MaterialTheme.typography.bodyMedium,
          )
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(
              text = tool.roadmapNote.ifEmpty { "离线引擎建设中，保持零数据收集与本地执行。" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            haptics.tick()
            upcomingToolDialog = null
          },
          modifier = Modifier.testTag("upcoming_dialog_ok"),
        ) {
          Text("知道了")
        }
      },
    )
  }
}

@Composable
private fun PinnedToolSlotChip(
  tool: ToolItem,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color =
      if (tool.isAvailable) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    modifier = Modifier.testTag("pinned_tool_${tool.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      Icon(
        imageVector = tool.icon,
        contentDescription = null,
        tint =
          if (tool.isAvailable) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = tool.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
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
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (expanded) "收起分类" else "展开分类",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 8.dp),
        ) {
          tools.forEach { tool ->
            val isPinned = tool.id in pinnedIds
            Surface(
              onClick = { onToolClick(tool) },
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("tool_item_${tool.id}"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (tool.isAvailable) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                  modifier = Modifier.size(44.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = tool.icon,
                      contentDescription = null,
                      tint =
                        if (tool.isAvailable) {
                          MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        },
                      modifier = Modifier.size(22.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = tool.name,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (!tool.isAvailable) {
                      Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = ContinuousCapsule(),
                      ) {
                        Text(
                          text = "即将上线",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
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

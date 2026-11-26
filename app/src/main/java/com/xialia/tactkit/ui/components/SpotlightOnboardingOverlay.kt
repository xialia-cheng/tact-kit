package com.xialia.tactkit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.AppHaptics
import kotlin.math.roundToInt

enum class OnboardingStep(
  val stepNumber: Int,
  val title: String,
  val description: String,
) {
  DASHBOARD(
    stepNumber = 1,
    title = "工作台与文档统计",
    description = "这里集中展示您的本地清单、Markdown 便签、语音备忘与各类文档概览，所有数据 100% 本地存储。",
  ),
  FILE_MANAGER(
    stepNumber = 2,
    title = "全盘文件管理直达",
    description = "点击进入文件管理：支持全盘最近时间流、QQ/微信目录持久关联，以及在其他 App 选文件时直接调用「简工坊 · 全盘文件」。",
  ),
  QUICK_STRIP(
    stepNumber = 3,
    title = "高频工具快捷条",
    description = "横向滑动直达文档扫描、加密 PDF 原地弹窗解锁、图片隐私打码、AI 识图及分享即预览 Quick Look。",
  ),
  TOOL_BALL(
    stepNumber = 4,
    title = "液态玻璃悬浮球",
    description = "点击悬浮球展开全部 5 大分类工具抽屉，支持工具无限置顶与高速双向局域网快传。",
  ),
  SETTINGS_ENTRY(
    stepNumber = 5,
    title = "设置与帮助中心",
    description = "其余压缩、校验和、传感器、电子与网络等小工具及冷门辅助功能，点这里进入「我的与设置 → 帮助和提示」即可查阅完整教程。",
  ),
}

@Composable
fun OnboardingWelcomeDialog(
  onSkipForever: () -> Unit,
  onStartTutorial: () -> Unit,
) {
  val view = LocalView.current
  AlertDialog(
    onDismissRequest = {},
    properties =
      androidx.compose.ui.window.DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
      ),
    icon = {
      PetalIconBadge(
        icon = Icons.Rounded.AutoAwesome,
        size = 54.dp,
        iconSize = 26.dp,
      )
    },
    title = {
      Text(
        text = "欢迎使用简工坊 (TactKit)",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text = "本地优先、零数据收集的全能效率与文档工作台。是否花 15 秒通过聚光灯引导快速认识 5 个核心区域？",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          AppHaptics.click(view)
          onStartTutorial()
        },
        modifier = Modifier.testTag("onboarding_start_btn"),
      ) {
        Text("开始教程")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          AppHaptics.click(view)
          onSkipForever()
        },
        modifier = Modifier.testTag("onboarding_skip_forever_btn"),
      ) {
        Text("不再显示")
      }
    },
  )
}

@Composable
fun SpotlightOnboardingOverlay(
  currentStep: OnboardingStep,
  targetBoundsMap: Map<OnboardingStep, Rect>,
  onAdvanceStep: () -> Unit,
  onSkipTutorial: () -> Unit,
) {
  val view = LocalView.current
  val density = LocalDensity.current

  BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("spotlight_onboarding_overlay")) {
    val rootWidthPx = with(density) { maxWidth.toPx() }
    val rootHeightPx = with(density) { maxHeight.toPx() }
    val padPx = with(density) { 6.dp.toPx() }
    val cornerPx = with(density) { 20.dp.toPx() }

    val fallbackRect =
      Rect(
        left = rootWidthPx * 0.2f,
        top = rootHeightPx * 0.35f,
        right = rootWidthPx * 0.8f,
        bottom = rootHeightPx * 0.48f,
      )
    val rawTarget = targetBoundsMap[currentStep] ?: fallbackRect
    val paddedTarget =
      Rect(
        left = (rawTarget.left - padPx).coerceAtLeast(0f),
        top = (rawTarget.top - padPx).coerceAtLeast(0f),
        right = (rawTarget.right + padPx).coerceAtMost(rootWidthPx),
        bottom = (rawTarget.bottom + padPx).coerceAtMost(rootHeightPx),
      )

    val animatedTarget by
      animateRectAsState(
        targetValue = paddedTarget,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "spotlight_rect",
      )

    val highlightRingColor = MaterialTheme.colorScheme.primary

    // Dark scrim with BlendMode.Clear cutout + tap interception outside highlighted target
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .pointerInput(currentStep, paddedTarget) {
            detectTapGestures { offset ->
              if (paddedTarget.contains(offset)) {
                AppHaptics.click(view)
                onAdvanceStep()
              }
              // Otherwise consume silently: block unrelated areas
            }
          },
    ) {
      drawRect(color = Color.Black.copy(alpha = 0.72f))
      drawRoundRect(
        color = Color.Transparent,
        topLeft = Offset(animatedTarget.left, animatedTarget.top),
        size = Size(animatedTarget.width, animatedTarget.height),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        blendMode = BlendMode.Clear,
      )
      drawRoundRect(
        color = highlightRingColor.copy(alpha = 0.9f),
        topLeft = Offset(animatedTarget.left, animatedTarget.top),
        size = Size(animatedTarget.width, animatedTarget.height),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = 3.dp.toPx()),
      )
    }

    // Bubble card placed above or below the highlighted region
    val placeAbove = animatedTarget.center.y > rootHeightPx * 0.55f
    val bubbleMarginPx = with(density) { 14.dp.toPx() }

    val bubbleYOffsetPx =
      if (placeAbove) {
        (animatedTarget.top - with(density) { 235.dp.toPx() } - bubbleMarginPx)
          .coerceAtLeast(with(density) { 72.dp.toPx() })
      } else {
        (animatedTarget.bottom + bubbleMarginPx)
          .coerceAtMost(rootHeightPx - with(density) { 255.dp.toPx() })
      }

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxWidth()
          .offset { IntOffset(0, bubbleYOffsetPx.roundToInt()) }
          .padding(horizontal = 20.dp)
          .navigationBarsPadding(),
    ) {
      AnimatedContent(
        targetState = currentStep,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_bubble",
      ) { step ->
        Surface(
          shape = themeShape(22.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLowest,
          tonalElevation = 4.dp,
          shadowElevation = 8.dp,
          modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(18.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Surface(
                shape = themeShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = "${step.stepNumber} / ${OnboardingStep.entries.size}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.TouchApp,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = if (step == OnboardingStep.SETTINGS_ENTRY) "点击高亮区域完成 →" else "点击高亮区域继续 →",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            Text(
              text = step.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = step.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Start,
              modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
              TextButton(
                onClick = {
                  AppHaptics.click(view)
                  onSkipTutorial()
                },
                modifier = Modifier.testTag("spotlight_skip_btn"),
              ) {
                Text(
                  text = "跳过（不再显示）",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

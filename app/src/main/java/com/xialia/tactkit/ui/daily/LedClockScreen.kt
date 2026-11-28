package com.xialia.tactkit.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class LedClockMode(val label: String) {
  MARQUEE("手持弹幕灯牌"),
  DESK_CLOCK("极简桌面时钟"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedClockScreen(
  onNavigateBack: () -> Unit,
) {
  val haptics = rememberAppHaptics()

  var mode by remember { mutableStateOf(LedClockMode.MARQUEE) }
  var bannerText by remember { mutableStateOf("欢迎使用简工坊 TactKit ✨") }
  var speedPxPerFrame by remember { mutableFloatStateOf(4f) }
  var fontSizeSp by remember { mutableFloatStateOf(52f) }
  var selectedColor by remember { mutableStateOf(Color(0xFF38BDF8)) }
  var isImmersiveFullScreen by remember { mutableStateOf(false) }

  var scrollOffsetPx by remember { mutableFloatStateOf(400f) }
  var marqueeWidthPx by remember { mutableIntStateOf(600) }
  var textWidthPx by remember { mutableIntStateOf(400) }

  var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var burnInStep by remember { mutableIntStateOf(0) }

  val palette =
    listOf(
      Color(0xFF38BDF8),
      Color(0xFF4ADE80),
      Color(0xFFFACC15),
      Color(0xFFFB7185),
      Color(0xFFFFFFFF),
    )

  LaunchedEffect(mode, speedPxPerFrame, bannerText, marqueeWidthPx, textWidthPx) {
    if (mode == LedClockMode.MARQUEE) {
      while (true) {
        scrollOffsetPx -= speedPxPerFrame
        if (scrollOffsetPx < -textWidthPx.toFloat() - 32f) {
          scrollOffsetPx = marqueeWidthPx.toFloat() + 24f
        }
        delay(16L)
      }
    }
  }

  LaunchedEffect(mode) {
    if (mode == LedClockMode.DESK_CLOCK) {
      while (true) {
        nowMs = System.currentTimeMillis()
        burnInStep = ((nowMs / 30_000L) % 4L).toInt()
        delay(1000L)
      }
    }
  }

  val burnInOffset =
    when (burnInStep) {
      0 -> IntOffset(0, 0)
      1 -> IntOffset(6, 4)
      2 -> IntOffset(-6, 6)
      else -> IntOffset(-4, -5)
    }

  if (isImmersiveFullScreen) {
    PredictiveBackContainer(onBack = { isImmersiveFullScreen = false }) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable { isImmersiveFullScreen = false }
            .testTag("led_immersive_stage"),
      ) {
        if (mode == LedClockMode.MARQUEE) {
          Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
              Modifier.fillMaxSize()
                .clipToBounds()
                .onSizeChanged { marqueeWidthPx = it.width },
          ) {
            Text(
              text = bannerText.ifBlank { "TactKit" },
              color = selectedColor,
              fontSize = (fontSizeSp * 1.45f).sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false,
              modifier =
                Modifier.onSizeChanged { textWidthPx = it.width }
                  .offset { IntOffset(scrollOffsetPx.roundToInt(), 0) },
            )
          }
        } else {
          DeskClockFace(nowMs = nowMs, accentColor = selectedColor, offset = burnInOffset)
        }

        IconButton(
          onClick = { isImmersiveFullScreen = false },
          modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
        ) {
          Icon(Icons.Rounded.FullscreenExit, contentDescription = "退出全屏", tint = Color.White.copy(alpha = 0.65f))
        }
      }
    }
    return
  }

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
          title = { Text("LED 弹幕与桌面时钟", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("led_clock_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                isImmersiveFullScreen = true
              },
              modifier = Modifier.testTag("led_fullscreen_button"),
            ) {
              Icon(Icons.Rounded.Fullscreen, contentDescription = "全屏显示")
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          item(key = "led_mode_tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              LedClockMode.entries.forEach { tab ->
                TactileModeChip(
                  selected = mode == tab,
                  label = tab.label,
                  onClick = { mode = tab },
                  modifier = Modifier.testTag("led_mode_${tab.name}"),
                )
              }
            }
          }

          item(key = "led_preview_stage") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = Color(0xFF0B0D12),
              modifier = Modifier.fillMaxWidth().height(210.dp).testTag("led_preview_stage"),
            ) {
              if (mode == LedClockMode.MARQUEE) {
                Box(
                  contentAlignment = Alignment.CenterStart,
                  modifier =
                    Modifier.fillMaxSize()
                      .clipToBounds()
                      .onSizeChanged { marqueeWidthPx = it.width },
                ) {
                  Text(
                    text = bannerText.ifBlank { "TactKit" },
                    color = selectedColor,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier =
                      Modifier.onSizeChanged { textWidthPx = it.width }
                        .offset { IntOffset(scrollOffsetPx.roundToInt(), 0) },
                  )
                }
              } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                  DeskClockFace(nowMs = nowMs, accentColor = selectedColor, offset = burnInOffset)
                }
              }
            }
          }

          item(key = "led_controls_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                if (mode == LedClockMode.MARQUEE) {
                  OutlinedTextField(
                    value = bannerText,
                    onValueChange = { bannerText = it },
                    label = { Text("弹幕内容") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("led_text_input"),
                  )
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                      items = listOf("加油！", "我在出站口", "请勿打扰 · 专注中", "简工坊 TactKit"),
                      key = { it },
                      contentType = { "led_preset_chip" },
                    ) { preset ->
                      FilterChip(
                        selected = bannerText == preset,
                        onClick = { bannerText = preset },
                        label = { Text(preset) },
                      )
                    }
                  }
                  Text("滚动速度：${speedPxPerFrame.toInt()}", style = MaterialTheme.typography.labelMedium)
                  Slider(value = speedPxPerFrame, onValueChange = { speedPxPerFrame = it }, valueRange = 1f..14f)
                  Text("字号大小：${fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                  Slider(value = fontSizeSp, onValueChange = { fontSizeSp = it }, valueRange = 28f..88f)
                } else {
                  Text(
                    text = "OLED 防烧屏保护已开启：每 30 秒自动进行轻微像素位移。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Text("灯珠色彩", style = MaterialTheme.typography.labelMedium)
                  palette.forEach { color ->
                    Box(
                      modifier =
                        Modifier.size(if (selectedColor == color) 32.dp else 26.dp)
                          .clip(CircleShape)
                          .background(color)
                          .clickable {
                            haptics.tick()
                            selectedColor = color
                          },
                    )
                  }
                }

                TactileActionButton(
                  onClick = { isImmersiveFullScreen = true },
                  modifier = Modifier.fillMaxWidth().testTag("led_enter_fullscreen_button"),
                ) {
                  Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("沉浸全屏展示（点击屏幕退出）", fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeskClockFace(
  nowMs: Long,
  accentColor: Color,
  offset: IntOffset,
) {
  val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs))
  val dateStr = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(Date(nowMs))
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.offset { offset },
  ) {
    Text(
      text = timeStr,
      fontFamily = TactKitMono,
      fontSize = 60.sp,
      fontWeight = FontWeight.Bold,
      color = accentColor,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = dateStr,
      style = MaterialTheme.typography.titleMedium,
      color = Color.White.copy(alpha = 0.72f),
    )
  }
}

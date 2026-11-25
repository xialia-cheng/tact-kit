package com.xialia.tactkit.ui.sensor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.MorseTorchEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorseTorchScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  val hasFlash = remember(context) { MorseTorchEngine.findFlashCameraId(context) != null }
  var isTorchLit by remember { mutableStateOf(false) }
  var isSendingMorse by remember { mutableStateOf(false) }
  var isSosLoop by remember { mutableStateOf(false) }
  var inputText by remember { mutableStateOf("SOS") }
  var unitMs by remember { mutableFloatStateOf(160f) }
  var blinkJob by remember { mutableStateOf<Job?>(null) }

  val morsePreview = remember(inputText) { MorseTorchEngine.encodeTextToMorse(inputText) }

  fun stopAllBlink() {
    blinkJob?.cancel()
    blinkJob = null
    isSendingMorse = false
    isSosLoop = false
    isTorchLit = false
    MorseTorchEngine.setTorch(context, false)
  }

  DisposableEffect(context) {
    onDispose {
      stopAllBlink()
    }
  }

  val beaconColor by animateColorAsState(
    targetValue =
      if (isTorchLit) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surfaceContainerHigh,
    label = "morse_beacon",
  )

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
          title = { Text("摩斯电码手电", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("morse_back_button"),
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
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          if (!hasFlash) {
            item(key = "no_flash") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "当前设备未检测到闪光灯硬件，已保留屏幕光信号指示供测试使用。",
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }

          item(key = "torch_control_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("morse_torch_card"),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(18.dp),
              ) {
                PetalIconBadge(
                  icon = if (isTorchLit) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                  size = 64.dp,
                  iconSize = 30.dp,
                  containerColor = beaconColor,
                  contentColor =
                    if (isTorchLit) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  TactileActionButton(
                    onClick = {
                      if (isSendingMorse || isSosLoop) {
                        stopAllBlink()
                      } else {
                        val next = !isTorchLit
                        MorseTorchEngine.setTorch(context, next)
                        isTorchLit = next
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("torch_switch_button"),
                  ) {
                    Icon(
                      imageVector = if (isTorchLit) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTorchLit) "关闭照明" else "常亮手电", fontWeight = FontWeight.SemiBold)
                  }

                  TactileActionButton(
                    onClick = {
                      if (isSosLoop) {
                        stopAllBlink()
                      } else {
                        stopAllBlink()
                        isSosLoop = true
                        blinkJob =
                          scope.launch {
                            MorseTorchEngine.transmitMorsePattern(
                              context = context,
                              morseCode = "... --- ...",
                              unitMs = unitMs.toLong(),
                              repeatLoop = true,
                              onStep = { lit -> isTorchLit = lit },
                            )
                          }
                      }
                    },
                    containerColor =
                      if (isSosLoop) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                      if (isSosLoop) MaterialTheme.colorScheme.onError
                      else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f).testTag("sos_loop_button"),
                  ) {
                    Icon(Icons.Rounded.Sos, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSosLoop) "停止 SOS" else "一键 SOS 求救", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          item(key = "morse_encoder_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text("文本转摩斯电码并闪烁发射", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                  value = inputText,
                  onValueChange = { inputText = it },
                  label = { Text("英文字母或数字") },
                  placeholder = { Text("例如：HELP 或 TACTKIT 2026") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("morse_text_input"),
                )

                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = morsePreview.ifBlank { "输入字母或数字生成电码" },
                    fontFamily = TactKitMono,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp).testTag("morse_preview_text"),
                  )
                }

                Column {
                  Text(
                    text = "拍发基准节奏：${unitMs.toInt()} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Slider(
                    value = unitMs,
                    onValueChange = { unitMs = it },
                    valueRange = 80f..350f,
                  )
                }

                TactileActionButton(
                  enabled = morsePreview.isNotBlank(),
                  onClick = {
                    if (isSendingMorse) {
                      stopAllBlink()
                    } else {
                      stopAllBlink()
                      isSendingMorse = true
                      blinkJob =
                        scope.launch {
                          try {
                            MorseTorchEngine.transmitMorsePattern(
                              context = context,
                              morseCode = morsePreview,
                              unitMs = unitMs.toLong(),
                              repeatLoop = false,
                              onStep = { lit -> isTorchLit = lit },
                            )
                          } finally {
                            isSendingMorse = false
                          }
                        }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("morse_transmit_button"),
                ) {
                  Text(
                    text = if (isSendingMorse) "停止发射" else "用闪光灯拍发此文本",
                    fontWeight = FontWeight.SemiBold,
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

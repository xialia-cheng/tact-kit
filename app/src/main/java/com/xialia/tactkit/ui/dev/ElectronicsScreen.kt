package com.xialia.tactkit.ui.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.ElectronicsEngine
import com.xialia.tactkit.engine.ResistorBandColor
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.Locale

private enum class ElectronicsTab(val label: String) {
  RESISTOR("色环电阻"),
  DIVIDER("分压计算"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectronicsScreen(
  onNavigateBack: () -> Unit,
) {
  val haptics = rememberAppHaptics()
  var activeTab by remember { mutableStateOf(ElectronicsTab.RESISTOR) }

  // Resistor states
  var isFiveBand by remember { mutableStateOf(false) }
  var activeSlot by remember { mutableIntStateOf(0) }
  var band1 by remember { mutableStateOf(ResistorBandColor.BROWN) }
  var band2 by remember { mutableStateOf(ResistorBandColor.BLACK) }
  var band3 by remember { mutableStateOf(ResistorBandColor.BLACK) }
  var multiplier by remember { mutableStateOf(ResistorBandColor.RED) }
  var tolerance by remember { mutableStateOf(ResistorBandColor.GOLD) }

  // Voltage divider states
  var vInText by remember { mutableStateOf("5.0") }
  var r1Text by remember { mutableStateOf("10000") }
  var r2Text by remember { mutableStateOf("10000") }

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
          title = { Text("电子工程计算器", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("electronics_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding).widthIn(max = 600.dp),
      ) {
        item(key = "elec_tabs") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ElectronicsTab.entries, key = { it.name }) { tab ->
              TactileModeChip(
                selected = activeTab == tab,
                label = tab.label,
                onClick = { activeTab = tab },
                modifier = Modifier.testTag("electronics_tab_${tab.name}"),
              )
            }
          }
        }

        if (activeTab == ElectronicsTab.RESISTOR) {
          item(key = "resistor_card") {
            val result =
              remember(isFiveBand, band1, band2, band3, multiplier, tolerance) {
                if (isFiveBand) {
                  ElectronicsEngine.calculate5BandResistor(band1, band2, band3, multiplier, tolerance)
                } else {
                  ElectronicsEngine.calculate4BandResistor(band1, band2, multiplier, tolerance)
                }
              }
            val slots =
              if (isFiveBand) {
                listOf("第1环" to band1, "第2环" to band2, "第3环" to band3, "倍率" to multiplier, "精度" to tolerance)
              } else {
                listOf("第1环" to band1, "第2环" to band2, "倍率" to multiplier, "精度" to tolerance)
              }

            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(
                    selected = !isFiveBand,
                    onClick = {
                      isFiveBand = false
                      activeSlot = activeSlot.coerceAtMost(3)
                    },
                    label = { Text("4 环电阻", maxLines = 1, softWrap = false) },
                  )
                  FilterChip(
                    selected = isFiveBand,
                    onClick = { isFiveBand = true },
                    label = { Text("5 环精密", maxLines = 1, softWrap = false) },
                  )
                }

                val bandColors = remember(slots) { slots.map { Color(it.second.argb) } }
                val bandPositions =
                  remember(isFiveBand) {
                    if (isFiveBand) floatArrayOf(0.14f, 0.28f, 0.42f, 0.58f, 0.80f)
                    else floatArrayOf(0.16f, 0.34f, 0.52f, 0.78f)
                  }
                val bodyCornerRadius = remember { CornerRadius(24f, 24f) }

                // Live resistor preview Canvas
                Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                  val leadY = size.height / 2f
                  drawLine(
                    color = Color(0xFFB0BEC5),
                    start = Offset(12f, leadY),
                    end = Offset(size.width - 12f, leadY),
                    strokeWidth = 10f,
                  )
                  val bodyW = size.width * 0.68f
                  val bodyH = size.height * 0.56f
                  val bodyLeft = (size.width - bodyW) / 2f
                  val bodyTop = (size.height - bodyH) / 2f
                  drawRoundRect(
                    color = if (isFiveBand) Color(0xFF4FC3F7) else Color(0xFFE7CFA6),
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyW, bodyH),
                    cornerRadius = bodyCornerRadius,
                  )
                  val bandW = bodyW * 0.08f
                  val bandSize = Size(bandW, bodyH)
                  for (idx in bandColors.indices) {
                    val bx = bodyLeft + bodyW * bandPositions[idx]
                    drawRect(
                      color = bandColors[idx],
                      topLeft = Offset(bx, bodyTop),
                      size = bandSize,
                    )
                  }
                }

                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(14.dp),
                  ) {
                    Text(
                      text = "${result.formattedResistance}  ±${result.tolerancePercent}%",
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Bold,
                      fontFamily = TactKitMono,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.testTag("resistor_result_text"),
                    )
                  }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(
                    count = slots.size,
                    key = { idx -> "slot_${slots[idx].first}" },
                    contentType = { "resistor_slot" },
                  ) { idx ->
                    val (title, col) = slots[idx]
                    FilterChip(
                      selected = activeSlot == idx,
                      onClick = {
                        haptics.tick()
                        activeSlot = idx
                      },
                      label = { Text("$title·${col.label}", maxLines = 1, softWrap = false) },
                    )
                  }
                }

                val palette =
                  when {
                    activeSlot == 0 -> ResistorBandColor.firstDigitColors
                    (!isFiveBand && activeSlot == 1) || (isFiveBand && activeSlot in 1..2) ->
                      ResistorBandColor.digitColors
                    (!isFiveBand && activeSlot == 2) || (isFiveBand && activeSlot == 3) ->
                      ResistorBandColor.multiplierColors
                    else -> ResistorBandColor.toleranceColors
                  }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(palette, key = { it.name }, contentType = { "band_color" }) { bandCol ->
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier =
                        Modifier.size(48.dp)
                          .clip(CircleShape)
                          .background(Color(bandCol.argb))
                          .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                          .clickable {
                            haptics.tick()
                            when {
                              activeSlot == 0 -> band1 = bandCol
                              activeSlot == 1 -> band2 = bandCol
                              isFiveBand && activeSlot == 2 -> band3 = bandCol
                              (!isFiveBand && activeSlot == 2) || (isFiveBand && activeSlot == 3) ->
                                multiplier = bandCol
                              else -> tolerance = bandCol
                            }
                          },
                    ) {
                      Text(
                        text = bandCol.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (bandCol.isLightText) Color.White else Color.Black,
                      )
                    }
                  }
                }
              }
            }
          }
        } else {
          item(key = "divider_card") {
            val vIn = vInText.toDoubleOrNull()
            val r1 = r1Text.toDoubleOrNull()
            val r2 = r2Text.toDoubleOrNull()
            val dividerRes =
              remember(vIn, r1, r2) {
                if (vIn != null && r1 != null && r2 != null) {
                  runCatching { ElectronicsEngine.calculateVoltageDivider(vIn, r1, r2) }
                } else null
              }
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text(
                  text = "串联分压：Vout = Vin × R2 / (R1 + R2)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                  value = vInText,
                  onValueChange = { vInText = it },
                  label = { Text("输入电压 Vin (V)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("divider_vin"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedTextField(
                    value = r1Text,
                    onValueChange = { r1Text = it },
                    label = { Text("上偏电阻 R1 (Ω)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("divider_r1"),
                  )
                  OutlinedTextField(
                    value = r2Text,
                    onValueChange = { r2Text = it },
                    label = { Text("下偏电阻 R2 (Ω)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("divider_r2"),
                  )
                }

                dividerRes?.onSuccess { res ->
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                      modifier = Modifier.padding(14.dp),
                    ) {
                      Text(
                        text = String.format(Locale.US, "输出电压 Vout：%.3f V", res.vOut),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TactKitMono,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                      Text(
                        text =
                          String.format(
                            Locale.US,
                            "回路电流：%.3f mA · R1 功耗：%.2f mW · R2 功耗：%.2f mW",
                            res.currentMa,
                            res.powerR1Mw,
                            res.powerR2Mw,
                          ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TactKitMono,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                    }
                  }
                }?.onFailure { err ->
                  Text(
                    text = err.message ?: "输入无效",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
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

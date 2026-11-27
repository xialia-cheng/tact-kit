package com.xialia.tactkit.ui.daily

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.xialia.tactkit.ui.components.TactileSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xialia.tactkit.HardwareVolumeKeyDispatcher
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.engine.DailyUtilityEngine
import com.xialia.tactkit.engine.PomodoroNotificationHelper
import com.xialia.tactkit.engine.UnitCategory
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.delay

enum class DailyToolTab(val label: String) {
  COUNTER("持久化计数器"),
  UNIT_AA("换算与 AA 分摊"),
  POMODORO("番茄钟与倒计时"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyToolsScreen(
  initialTabName: String? = null,
  preferences: UserPreferences,
  onStepCounter: (Int) -> Unit,
  onResetCounter: () -> Unit,
  onSetCounterStep: (Int) -> Unit,
  onSetCounterLabel: (String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var activeTab by
    remember(initialTabName) {
      mutableStateOf(
        DailyToolTab.entries.firstOrNull { it.name == initialTabName } ?: DailyToolTab.COUNTER
      )
    }

  // Hardware volume key capture while Counter tab is active
  var volumeKeyControlEnabled by remember { mutableStateOf(true) }
  DisposableEffect(activeTab, volumeKeyControlEnabled) {
    if (activeTab == DailyToolTab.COUNTER && volumeKeyControlEnabled) {
      HardwareVolumeKeyDispatcher.onVolumeKey = { delta ->
        if (delta > 0) haptics.confirm() else haptics.tick()
        onStepCounter(delta)
      }
    } else {
      HardwareVolumeKeyDispatcher.onVolumeKey = null
    }
    onDispose { HardwareVolumeKeyDispatcher.onVolumeKey = null }
  }

  // Unit & AA state
  var unitCat by remember { mutableStateOf(UnitCategory.LENGTH) }
  var fromUnitId by remember(unitCat) { mutableStateOf(DailyUtilityEngine.unitsFor(unitCat).first().id) }
  var toUnitId by
    remember(unitCat) {
      mutableStateOf(DailyUtilityEngine.unitsFor(unitCat).getOrElse(2) { DailyUtilityEngine.unitsFor(unitCat).last() }.id)
    }
  var unitValueText by remember { mutableStateOf("1") }

  var billSubtotalText by remember { mutableStateOf("180") }
  var billPeopleText by remember { mutableStateOf("4") }
  var billTipText by remember { mutableStateOf("0") }
  var billDiscountText by remember { mutableStateOf("0") }

  // Pomodoro state
  var focusMinutes by remember { mutableIntStateOf(25) }
  var breakMinutes by remember { mutableIntStateOf(5) }
  var isFocusPhase by remember { mutableStateOf(true) }
  var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
  var totalPhaseSeconds by remember { mutableIntStateOf(25 * 60) }
  var isTimerRunning by remember { mutableStateOf(false) }

  val notificationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        PomodoroNotificationHelper.postTimerNotification(
          context = context,
          title = if (isFocusPhase) "简工坊 · 专注计时" else "简工坊 · 休息时间",
          phaseLabel = if (isFocusPhase) "保持专注" else "放松片刻",
          remainingSeconds = remainingSeconds,
          totalSeconds = totalPhaseSeconds,
          isRunning = isTimerRunning,
          isFocusPhase = isFocusPhase,
        )
      }
    }

  LaunchedEffect(isTimerRunning, remainingSeconds, isFocusPhase) {
    if (isTimerRunning) {
      val hasNotifPerm =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (hasNotifPerm) {
        PomodoroNotificationHelper.postTimerNotification(
          context = context,
          title = if (isFocusPhase) "简工坊 · 专注计时" else "简工坊 · 休息时间",
          phaseLabel = if (isFocusPhase) "保持专注" else "放松片刻",
          remainingSeconds = remainingSeconds,
          totalSeconds = totalPhaseSeconds,
          isRunning = true,
          isFocusPhase = isFocusPhase,
        )
      }
      if (remainingSeconds > 0) {
        delay(1_000L)
        remainingSeconds -= 1
      } else {
        haptics.confirm()
        isFocusPhase = !isFocusPhase
        val nextTotal = (if (isFocusPhase) focusMinutes else breakMinutes) * 60
        totalPhaseSeconds = nextTotal
        remainingSeconds = nextTotal
      }
    }
  }

  var counterLabelInput by remember(preferences.counterLabel) { mutableStateOf(preferences.counterLabel) }

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
              "日常高频工具箱",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("daily_tools"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("daily_back_button"),
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
        item(key = "daily_tabs") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DailyToolTab.entries, key = { it.name }) { tab ->
              TactileModeChip(
                selected = activeTab == tab,
                label = tab.label,
                onClick = { activeTab = tab },
                modifier = Modifier.testTag("daily_tab_${tab.name}"),
              )
            }
          }
        }

        when (activeTab) {
          DailyToolTab.COUNTER -> {
            item(key = "counter_main_card") {
              Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(14.dp),
                  modifier = Modifier.padding(20.dp),
                ) {
                  OutlinedTextField(
                    value = counterLabelInput,
                    onValueChange = {
                      counterLabelInput = it
                      onSetCounterLabel(it)
                    },
                    label = { Text("计数项目名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  )

                  Text(
                    text = "${preferences.counterValue}",
                    fontSize = 64.sp,
                    lineHeight = 72.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = TactKitMono,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("counter_value_text"),
                  )

                  TactileActionButton(
                    onClick = {
                      haptics.confirm()
                      onStepCounter(1)
                    },
                    modifier = Modifier.fillMaxWidth().height(72.dp).testTag("counter_plus_btn"),
                  ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记一次 (+${preferences.counterStep})", style = MaterialTheme.typography.titleMedium)
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      onClick = {
                        haptics.tick()
                        onStepCounter(-1)
                      },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f).testTag("counter_minus_btn"),
                    ) {
                      Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("-${preferences.counterStep}")
                    }

                    TactileActionButton(
                      onClick = {
                        haptics.tick()
                        onResetCounter()
                      },
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                      contentColor = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f).testTag("counter_reset_btn"),
                    ) {
                      Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("归零")
                    }
                  }

                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1, 2, 5, 10, 50)) { step ->
                      FilterChip(
                        selected = preferences.counterStep == step,
                        onClick = {
                          haptics.tick()
                          onSetCounterStep(step)
                        },
                        label = { Text("步进 $step", maxLines = 1, softWrap = false) },
                      )
                    }
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text("实体音量键计数", style = MaterialTheme.typography.titleSmall)
                      Text(
                        text = "开启后在本页面按音量加/减键可盲操增减计数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    TactileSwitch(
                      checked = volumeKeyControlEnabled,
                      onCheckedChange = { volumeKeyControlEnabled = it },
                    )
                  }
                }
              }
            }
          }

          DailyToolTab.UNIT_AA -> {
            item(key = "unit_converter_card") {
              val units = remember(unitCat) { DailyUtilityEngine.unitsFor(unitCat) }
              val inputVal = unitValueText.toDoubleOrNull()
              val converted =
                remember(unitCat, inputVal, fromUnitId, toUnitId) {
                  inputVal?.let { DailyUtilityEngine.convert(unitCat, it, fromUnitId, toUnitId) }
                }
              val toSymbol = units.firstOrNull { it.id == toUnitId }?.symbol.orEmpty()

              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    PetalIconBadge(icon = Icons.Rounded.Calculate, size = 42.dp)
                    Text("全能单位换算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  }

                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UnitCategory.entries, key = { it.name }) { cat ->
                      FilterChip(
                        selected = unitCat == cat,
                        onClick = { unitCat = cat },
                        label = { Text(cat.label, maxLines = 1, softWrap = false) },
                      )
                    }
                  }

                  OutlinedTextField(
                    value = unitValueText,
                    onValueChange = { unitValueText = it },
                    label = { Text("输入数值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("unit_value_input"),
                  )

                  Text("从：", style = MaterialTheme.typography.labelMedium)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(units, key = { "from_${it.id}" }) { u ->
                      FilterChip(
                        selected = fromUnitId == u.id,
                        onClick = { fromUnitId = u.id },
                        label = { Text("${u.label} (${u.symbol})", maxLines = 1, softWrap = false) },
                      )
                    }
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text("换算为：", style = MaterialTheme.typography.labelMedium)
                    IconButton(
                      onClick = {
                        val tmp = fromUnitId
                        fromUnitId = toUnitId
                        toUnitId = tmp
                      },
                    ) {
                      Icon(Icons.Rounded.SwapHoriz, contentDescription = "交换单位")
                    }
                  }
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(units, key = { "to_${it.id}" }) { u ->
                      FilterChip(
                        selected = toUnitId == u.id,
                        onClick = { toUnitId = u.id },
                        label = { Text("${u.label} (${u.symbol})", maxLines = 1, softWrap = false) },
                      )
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text =
                        converted?.let { "${DailyUtilityEngine.formatNumber(it)} $toSymbol" }
                          ?: "请输入合法数字",
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Bold,
                      fontFamily = TactKitMono,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(14.dp).testTag("unit_result_text"),
                    )
                  }
                }
              }
            }

            item(key = "aa_splitter_card") {
              val sub = billSubtotalText.toDoubleOrNull() ?: 0.0
              val ppl = (billPeopleText.toIntOrNull() ?: 1).coerceAtLeast(1)
              val tip = billTipText.toDoubleOrNull() ?: 0.0
              val disc = billDiscountText.toDoubleOrNull() ?: 0.0
              val splitRes =
                remember(sub, ppl, tip, disc) {
                  DailyUtilityEngine.splitBill(sub, ppl, tip, disc)
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
                  Text("账单 AA 分摊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = billSubtotalText,
                      onValueChange = { billSubtotalText = it },
                      label = { Text("消费总额 (¥)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                      value = billPeopleText,
                      onValueChange = { billPeopleText = it },
                      label = { Text("人数") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(0.7f),
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = billTipText,
                      onValueChange = { billTipText = it },
                      label = { Text("服务费 (%)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                      value = billDiscountText,
                      onValueChange = { billDiscountText = it },
                      label = { Text("减免优惠 (¥)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.weight(1f),
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = splitRes.summaryText,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(14.dp).testTag("aa_split_summary"),
                    )
                  }
                }
              }
            }
          }

          DailyToolTab.POMODORO -> {
            item(key = "pomodoro_card") {
              val mm = remainingSeconds / 60
              val ss = remainingSeconds % 60
              val progress =
                1f - (remainingSeconds.toFloat() / totalPhaseSeconds.coerceAtLeast(1).toFloat())

              Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(14.dp),
                  modifier = Modifier.padding(20.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    PetalIconBadge(icon = Icons.Rounded.Timer, size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = if (isFocusPhase) "专注阶段" else "休息阶段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text =
                          if (Build.VERSION.SDK_INT >= 36) "支持通知栏实况进度条显示"
                          else "支持通知栏实时倒计时进度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }

                  com.xialia.tactkit.ui.components.RollingNumberText(
                    text = String.format("%02d:%02d", mm, ss),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 64.sp),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = TactKitMono,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("pomodoro_clock_text"),
                  )

                  LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                  )

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      onClick = {
                        if (!isTimerRunning) {
                          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                          ) {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                          }
                          isTimerRunning = true
                          haptics.confirm()
                        } else {
                          isTimerRunning = false
                          haptics.tick()
                        }
                      },
                      modifier = Modifier.weight(1f).testTag("pomodoro_toggle_btn"),
                    ) {
                      Icon(
                        imageVector = if (isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(if (isTimerRunning) "暂停" else "开始计时")
                    }

                    TactileActionButton(
                      onClick = {
                        isTimerRunning = false
                        val resetSec = (if (isFocusPhase) focusMinutes else breakMinutes) * 60
                        totalPhaseSeconds = resetSec
                        remainingSeconds = resetSec
                        PomodoroNotificationHelper.cancelTimerNotification(context)
                        haptics.tick()
                      },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Rounded.Refresh, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("重置")
                    }
                  }

                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(15, 25, 45, 60)) { min ->
                      FilterChip(
                        selected = isFocusPhase && focusMinutes == min,
                        onClick = {
                          isTimerRunning = false
                          isFocusPhase = true
                          focusMinutes = min
                          totalPhaseSeconds = min * 60
                          remainingSeconds = min * 60
                        },
                        label = { Text("专注 ${min}m", maxLines = 1, softWrap = false) },
                      )
                    }
                    items(listOf(5, 10)) { brk ->
                      FilterChip(
                        selected = !isFocusPhase && breakMinutes == brk,
                        onClick = {
                          isTimerRunning = false
                          isFocusPhase = false
                          breakMinutes = brk
                          totalPhaseSeconds = brk * 60
                          remainingSeconds = brk * 60
                        },
                        label = { Text("休息 ${brk}m", maxLines = 1, softWrap = false) },
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
  }
}

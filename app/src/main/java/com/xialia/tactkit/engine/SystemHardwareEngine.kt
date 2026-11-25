package com.xialia.tactkit.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import kotlin.math.abs

data class SystemHardwareSnapshot(
  val batteryLevelPct: Int,
  val isCharging: Boolean,
  val chargeSourceLabel: String,
  val batteryHealthLabel: String,
  val currentMa: Float,
  val voltageMv: Int,
  val watts: Float,
  val temperatureC: Float,
  val refreshRateHz: Float,
  val densityDpi: Int,
  val resolutionLabel: String,
  val totalRamMb: Long,
  val availRamMb: Long,
  val cpuCores: Int,
  val abiLabel: String,
  val androidRelease: String,
  val deviceModel: String,
)

object SystemHardwareEngine {

  /**
   * Normalizes OEM current readings (which are usually microamperes, though some older kernels
   * expose milliamperes) into milliamperes and computes charging/discharging wattage.
   */
  fun estimateWatts(rawCurrentMicroOrMilliAmp: Long, voltageMv: Int): Pair<Float, Float> {
    if (voltageMv <= 0) return 0f to 0f
    val absRaw = abs(rawCurrentMicroOrMilliAmp).toFloat()
    // Most Android devices report uA (e.g. 1_850_000 uA = 1850 mA); values < 20_000 are already mA.
    val ma = if (absRaw > 20_000f) absRaw / 1000f else absRaw
    val signedMa = if (rawCurrentMicroOrMilliAmp < 0) -ma else ma
    val watts = (ma / 1000f) * (voltageMv / 1000f)
    return signedMa to watts
  }

  @Suppress("DEPRECATION")
  fun captureSnapshot(context: Context): SystemHardwareSnapshot {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct =
      if (level >= 0 && scale > 0) (level * 100f / scale).toInt()
      else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0

    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging =
      status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val sourceLabel =
      when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC 充电器"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB 供电"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
        else -> if (isCharging) "充电中" else "电池供电"
      }

    val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    val healthLabel =
      when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "温度偏高"
        BatteryManager.BATTERY_HEALTH_COLD -> "温度偏低"
        BatteryManager.BATTERY_HEALTH_DEAD -> "老化/损耗"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压保护"
        else -> "正常"
      }

    val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val rawCurrent = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
    val (currentMa, watts) = estimateWatts(rawCurrent, voltageMv)

    val dm = context.resources.displayMetrics
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val refreshHz =
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          context.display?.refreshRate ?: wm?.defaultDisplay?.refreshRate ?: 60f
        } else {
          wm?.defaultDisplay?.refreshRate ?: 60f
        }
      }.getOrDefault(60f)

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)

    return SystemHardwareSnapshot(
      batteryLevelPct = pct,
      isCharging = isCharging,
      chargeSourceLabel = sourceLabel,
      batteryHealthLabel = healthLabel,
      currentMa = currentMa,
      voltageMv = voltageMv,
      watts = watts,
      temperatureC = tempTenths / 10f,
      refreshRateHz = refreshHz,
      densityDpi = dm.densityDpi,
      resolutionLabel = "${dm.widthPixels} × ${dm.heightPixels}",
      totalRamMb = memInfo.totalMem / (1024L * 1024L),
      availRamMb = memInfo.availMem / (1024L * 1024L),
      cpuCores = Runtime.getRuntime().availableProcessors(),
      abiLabel = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
      androidRelease = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
    )
  }
}

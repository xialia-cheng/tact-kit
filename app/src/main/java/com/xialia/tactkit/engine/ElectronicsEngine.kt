package com.xialia.tactkit.engine

import java.util.Locale
import kotlin.math.abs

enum class ResistorBandColor(
  val label: String,
  val argb: Long,
  val isLightText: Boolean,
  val digit: Int?,
  val multiplier: Double?,
  val tolerancePercent: Double?,
) {
  BLACK("黑", 0xFF1D1F24, true, 0, 1.0, null),
  BROWN("棕", 0xFF795548, true, 1, 10.0, 1.0),
  RED("红", 0xFFD32F2F, true, 2, 100.0, 2.0),
  ORANGE("橙", 0xFFF57C00, true, 3, 1_000.0, null),
  YELLOW("黄", 0xFFFBC02D, false, 4, 10_000.0, null),
  GREEN("绿", 0xFF388E3C, true, 5, 100_000.0, 0.5),
  BLUE("蓝", 0xFF1976D2, true, 6, 1_000_000.0, 0.25),
  VIOLET("紫", 0xFF7B1FA2, true, 7, 10_000_000.0, 0.1),
  GRAY("灰", 0xFF757575, true, 8, 100_000_000.0, 0.05),
  WHITE("白", 0xFFF5F5F5, false, 9, 1_000_000_000.0, null),
  GOLD("金", 0xFFD4AF37, false, null, 0.1, 5.0),
  SILVER("银", 0xFFB0BEC5, false, null, 0.01, 10.0),
  ;

  companion object {
    val digitColors = entries.filter { it.digit != null }
    val firstDigitColors = entries.filter { it.digit != null && it.digit > 0 }
    val multiplierColors = entries.filter { it.multiplier != null }
    val toleranceColors = entries.filter { it.tolerancePercent != null }
  }
}

data class ResistorResult(
  val ohms: Double,
  val formattedResistance: String,
  val tolerancePercent: Double,
)

data class VoltageDividerResult(
  val vOut: Double,
  val currentMa: Double,
  val powerR1Mw: Double,
  val powerR2Mw: Double,
  val totalResistanceOhms: Double,
)

object ElectronicsEngine {

  fun calculate4BandResistor(
    band1: ResistorBandColor,
    band2: ResistorBandColor,
    multiplier: ResistorBandColor,
    tolerance: ResistorBandColor,
  ): ResistorResult {
    val d1 = band1.digit ?: 0
    val d2 = band2.digit ?: 0
    val mul = multiplier.multiplier ?: 1.0
    val tol = tolerance.tolerancePercent ?: 5.0
    val ohms = (d1 * 10 + d2) * mul
    return ResistorResult(
      ohms = ohms,
      formattedResistance = formatOhms(ohms),
      tolerancePercent = tol,
    )
  }

  fun calculate5BandResistor(
    band1: ResistorBandColor,
    band2: ResistorBandColor,
    band3: ResistorBandColor,
    multiplier: ResistorBandColor,
    tolerance: ResistorBandColor,
  ): ResistorResult {
    val d1 = band1.digit ?: 0
    val d2 = band2.digit ?: 0
    val d3 = band3.digit ?: 0
    val mul = multiplier.multiplier ?: 1.0
    val tol = tolerance.tolerancePercent ?: 1.0
    val ohms = (d1 * 100 + d2 * 10 + d3) * mul
    return ResistorResult(
      ohms = ohms,
      formattedResistance = formatOhms(ohms),
      tolerancePercent = tol,
    )
  }

  fun calculateVoltageDivider(vIn: Double, r1Ohms: Double, r2Ohms: Double): VoltageDividerResult {
    require(r1Ohms >= 0.0 && r2Ohms >= 0.0) { "阻值不能为负数" }
    val totalR = r1Ohms + r2Ohms
    require(totalR > 0.0) { "R1 与 R2 之和必须大于 0 Ω" }
    val vOut = vIn * (r2Ohms / totalR)
    val currentAmps = vIn / totalR
    val p1Watts = currentAmps * currentAmps * r1Ohms
    val p2Watts = currentAmps * currentAmps * r2Ohms
    return VoltageDividerResult(
      vOut = vOut,
      currentMa = currentAmps * 1000.0,
      powerR1Mw = p1Watts * 1000.0,
      powerR2Mw = p2Watts * 1000.0,
      totalResistanceOhms = totalR,
    )
  }

  fun formatOhms(ohms: Double): String {
    val absOhms = abs(ohms)
    val (value, unit) =
      when {
        absOhms >= 1_000_000_000.0 -> ohms / 1_000_000_000.0 to "GΩ"
        absOhms >= 1_000_000.0 -> ohms / 1_000_000.0 to "MΩ"
        absOhms >= 1_000.0 -> ohms / 1_000.0 to "kΩ"
        else -> ohms to "Ω"
      }
    val formatted =
      if (abs(value - value.toLong()) < 0.0001) {
        value.toLong().toString()
      } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
      }
    return "$formatted $unit"
  }
}

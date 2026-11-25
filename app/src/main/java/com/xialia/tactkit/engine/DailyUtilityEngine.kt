package com.xialia.tactkit.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

enum class UnitCategory(val label: String) {
  LENGTH("长度"),
  AREA("面积"),
  MASS("质量"),
  TEMPERATURE("温度"),
  STORAGE("数据存储"),
  SPEED("速度"),
  PRESSURE("压强"),
}

data class UnitDefinition(
  val id: String,
  val label: String,
  val symbol: String,
  val factorToBase: Double, // For non-temperature linear units
)

data class BillSplitResult(
  val finalTotal: Double,
  val perPersonBase: Double,
  val remainderCents: Int,
  val summaryText: String,
)

object DailyUtilityEngine {

  private val unitMap: Map<UnitCategory, List<UnitDefinition>> =
    mapOf(
      UnitCategory.LENGTH to
        listOf(
          UnitDefinition("mm", "毫米", "mm", 0.001),
          UnitDefinition("cm", "厘米", "cm", 0.01),
          UnitDefinition("m", "米", "m", 1.0),
          UnitDefinition("km", "千米", "km", 1000.0),
          UnitDefinition("inch", "英寸", "in", 0.0254),
          UnitDefinition("ft", "英尺", "ft", 0.3048),
          UnitDefinition("mile", "英里", "mi", 1609.344),
        ),
      UnitCategory.AREA to
        listOf(
          UnitDefinition("cm2", "平方厘米", "cm²", 0.0001),
          UnitDefinition("m2", "平方米", "m²", 1.0),
          UnitDefinition("mu", "亩", "亩", 666.6666667),
          UnitDefinition("ha", "公顷", "ha", 10_000.0),
          UnitDefinition("km2", "平方千米", "km²", 1_000_000.0),
          UnitDefinition("sqft", "平方英尺", "ft²", 0.09290304),
        ),
      UnitCategory.MASS to
        listOf(
          UnitDefinition("mg", "毫克", "mg", 0.000001),
          UnitDefinition("g", "克", "g", 0.001),
          UnitDefinition("jin", "斤", "斤", 0.5),
          UnitDefinition("kg", "千克", "kg", 1.0),
          UnitDefinition("t", "吨", "t", 1000.0),
          UnitDefinition("oz", "盎司", "oz", 0.028349523125),
          UnitDefinition("lb", "磅", "lb", 0.45359237),
        ),
      UnitCategory.TEMPERATURE to
        listOf(
          UnitDefinition("c", "摄氏度", "°C", 1.0),
          UnitDefinition("f", "华氏度", "°F", 1.0),
          UnitDefinition("k", "开尔文", "K", 1.0),
        ),
      UnitCategory.STORAGE to
        listOf(
          UnitDefinition("bit", "比特", "bit", 1.0 / 8.0),
          UnitDefinition("b", "字节", "B", 1.0),
          UnitDefinition("kb", "KB (1000)", "KB", 1000.0),
          UnitDefinition("kib", "KiB (1024)", "KiB", 1024.0),
          UnitDefinition("mb", "MB (10⁶)", "MB", 1_000_000.0),
          UnitDefinition("mib", "MiB (1024²)", "MiB", 1024.0 * 1024.0),
          UnitDefinition("gb", "GB (10⁹)", "GB", 1_000_000_000.0),
          UnitDefinition("gib", "GiB (1024³)", "GiB", 1024.0 * 1024.0 * 1024.0),
          UnitDefinition("tb", "TB (10¹²)", "TB", 1_000_000_000_000.0),
          UnitDefinition("tib", "TiB (1024⁴)", "TiB", 1024.0 * 1024.0 * 1024.0 * 1024.0),
        ),
      UnitCategory.SPEED to
        listOf(
          UnitDefinition("mps", "米/秒", "m/s", 1.0),
          UnitDefinition("kmh", "千米/时", "km/h", 1.0 / 3.6),
          UnitDefinition("mph", "英里/时", "mph", 0.44704),
          UnitDefinition("knot", "节", "kn", 0.5144444444),
        ),
      UnitCategory.PRESSURE to
        listOf(
          UnitDefinition("pa", "帕斯卡", "Pa", 1.0),
          UnitDefinition("kpa", "千帕", "kPa", 1000.0),
          UnitDefinition("mpa", "兆帕", "MPa", 1_000_000.0),
          UnitDefinition("bar", "巴", "bar", 100_000.0),
          UnitDefinition("atm", "标准大气压", "atm", 101_325.0),
          UnitDefinition("psi", "磅/平方英寸", "psi", 6894.75729),
        ),
    )

  fun unitsFor(category: UnitCategory): List<UnitDefinition> = unitMap[category].orEmpty()

  fun convert(
    category: UnitCategory,
    value: Double,
    fromId: String,
    toId: String,
  ): Double {
    if (category == UnitCategory.TEMPERATURE) {
      val celsius =
        when (fromId) {
          "c" -> value
          "f" -> (value - 32.0) * 5.0 / 9.0
          "k" -> value - 273.15
          else -> value
        }
      return when (toId) {
        "c" -> celsius
        "f" -> celsius * 9.0 / 5.0 + 32.0
        "k" -> celsius + 273.15
        else -> celsius
      }
    }
    val list = unitsFor(category)
    val from = list.first { it.id == fromId }
    val to = list.first { it.id == toId }
    val base = value * from.factorToBase
    return base / to.factorToBase
  }

  fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    if (abs(value) >= 1e12 || (abs(value) > 0 && abs(value) < 1e-6)) {
      return String.format(Locale.US, "%.6e", value)
    }
    val bd = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
    return bd.toPlainString()
  }

  fun splitBill(
    subtotal: Double,
    people: Int,
    tipPercent: Double = 0.0,
    discountAmount: Double = 0.0,
  ): BillSplitResult {
    require(people >= 1) { "人数至少为 1" }
    val finalTotal = ((subtotal * (1.0 + tipPercent / 100.0)) - discountAmount).coerceAtLeast(0.0)
    val totalCents = kotlin.math.round(finalTotal * 100.0).toInt()
    val perPersonCents = totalCents / people
    val remainder = totalCents % people
    val perPerson = perPersonCents / 100.0

    val summary =
      buildString {
        append(String.format(Locale.US, "总计 ¥%.2f ÷ %d 人 = ¥%.2f / 人", finalTotal, people, perPerson))
        if (remainder > 0) {
          append(String.format(Locale.US, "（余 %d 分，前 %d 人多付 ¥0.01）", remainder, remainder))
        }
      }
    return BillSplitResult(
      finalTotal = finalTotal,
      perPersonBase = perPerson,
      remainderCents = remainder,
      summaryText = summary,
    )
  }
}

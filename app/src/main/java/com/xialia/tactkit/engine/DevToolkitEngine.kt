package com.xialia.tactkit.engine

import android.util.Base64
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

data class UrlCleanResult(
  val cleanedUrl: String,
  val removedParams: List<String>,
)

data class JwtDecodedResult(
  val headerPretty: String,
  val payloadPretty: String,
  val signature: String,
  val expiresAtFormatted: String?,
  val isExpired: Boolean?,
)

data class RegexMatchInfo(
  val index: Int,
  val start: Int,
  val end: Int,
  val matchedText: String,
  val groups: List<String>,
)

data class RegexTestResult(
  val isValid: Boolean,
  val errorMessage: String? = null,
  val matches: List<RegexMatchInfo> = emptyList(),
)

data class RadixConversionResult(
  val binary: String,
  val octal: String,
  val decimal: String,
  val hex: String,
)

data class Ipv4CidrResult(
  val cidr: String,
  val networkAddress: String,
  val broadcastAddress: String,
  val netmask: String,
  val wildcardMask: String,
  val firstUsableHost: String,
  val lastUsableHost: String,
  val totalAddresses: Long,
  val usableHosts: Long,
  val binaryMask: String,
)

object DevToolkitEngine {

  private val TRACKING_EXACT_KEYS =
    setOf(
      "fbclid",
      "gclid",
      "dclid",
      "gbraid",
      "wbraid",
      "yclid",
      "mc_cid",
      "mc_eid",
      "spm",
      "si",
      "igshid",
      "ref_src",
      "ref_url",
      "vd_source",
      "share_token",
      "share_source",
      "share_medium",
      "from_source",
      "msclkid",
      "ttclid",
      "_ga",
      "_gl",
    )

  private val TRACKING_PREFIXES = listOf("utm_", "__hs", "oly_", "vero_", "mkt_")

  fun encodeBase64(text: String, urlSafe: Boolean = false): String {
    val flags = if (urlSafe) Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING else Base64.NO_WRAP
    return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), flags)
  }

  fun decodeBase64(encoded: String, urlSafe: Boolean = false): String {
    val clean = encoded.trim()
    val flags = if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT
    val bytes = Base64.decode(clean, flags)
    return String(bytes, Charsets.UTF_8)
  }

  fun urlEncode(text: String): String = URLEncoder.encode(text, "UTF-8")

  fun urlDecode(text: String): String = URLDecoder.decode(text, "UTF-8")

  fun stripTrackingParams(rawUrl: String): UrlCleanResult {
    val trimmed = rawUrl.trim()
    val uri = URI(trimmed)
    val query = uri.rawQuery
    if (query.isNullOrBlank()) {
      return UrlCleanResult(cleanedUrl = trimmed, removedParams = emptyList())
    }
    val kept = mutableListOf<String>()
    val removed = mutableListOf<String>()
    query.split("&").filter { it.isNotEmpty() }.forEach { pair ->
      val keyRaw = pair.substringBefore("=")
      val keyLower = runCatching { URLDecoder.decode(keyRaw, "UTF-8") }.getOrDefault(keyRaw).lowercase()
      val isTracking =
        keyLower in TRACKING_EXACT_KEYS || TRACKING_PREFIXES.any { keyLower.startsWith(it) }
      if (isTracking) {
        removed += keyLower
      } else {
        kept += pair
      }
    }
    val newQuery = if (kept.isEmpty()) null else kept.joinToString("&")
    val rebuilt =
      URI(uri.scheme, uri.rawAuthority, uri.rawPath, newQuery, uri.rawFragment).toASCIIString()
    return UrlCleanResult(cleanedUrl = rebuilt, removedParams = removed)
  }

  fun decodeJwt(token: String): JwtDecodedResult {
    val parts = token.trim().split(".")
    require(parts.size >= 2) { "JWT 至少需要 Header 与 Payload 两段" }
    val headerJson = decodeBase64UrlSegment(parts[0])
    val payloadJson = decodeBase64UrlSegment(parts[1])
    val signature = parts.getOrNull(2).orEmpty()

    val headerPretty = JSONObject(headerJson).toString(2)
    val payloadObj = JSONObject(payloadJson)
    val payloadPretty = payloadObj.toString(2)

    val expSeconds = payloadObj.optLong("exp", -1L)
    val (expText, expired) =
      if (expSeconds > 0L) {
        val instant = Instant.ofEpochSecond(expSeconds)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        formatter.format(instant) to (System.currentTimeMillis() / 1000L > expSeconds)
      } else {
        null to null
      }

    return JwtDecodedResult(
      headerPretty = headerPretty,
      payloadPretty = payloadPretty,
      signature = signature,
      expiresAtFormatted = expText,
      isExpired = expired,
    )
  }

  private fun decodeBase64UrlSegment(segment: String): String {
    val padded =
      when (segment.length % 4) {
        2 -> "$segment=="
        3 -> "$segment="
        else -> segment
      }
    val bytes = Base64.decode(padded, Base64.URL_SAFE)
    return String(bytes, Charsets.UTF_8)
  }

  fun testRegex(
    pattern: String,
    input: String,
    ignoreCase: Boolean = false,
    multiline: Boolean = false,
  ): RegexTestResult {
    if (pattern.isEmpty()) return RegexTestResult(isValid = true, matches = emptyList())
    return try {
      val options = buildSet {
        if (ignoreCase) add(RegexOption.IGNORE_CASE)
        if (multiline) add(RegexOption.MULTILINE)
      }
      val regex = Regex(pattern, options)
      val matches =
        regex.findAll(input).mapIndexed { index, matchResult ->
          val groups = matchResult.groupValues.drop(1)
          RegexMatchInfo(
            index = index + 1,
            start = matchResult.range.first,
            end = matchResult.range.last + 1,
            matchedText = matchResult.value,
            groups = groups,
          )
        }.toList()
      RegexTestResult(isValid = true, matches = matches)
    } catch (e: Exception) {
      RegexTestResult(isValid = false, errorMessage = e.message ?: "正则表达式语法错误")
    }
  }

  fun convertRadix(input: String, fromBase: Int): RadixConversionResult {
    val clean =
      input.trim().let { raw ->
        when {
          fromBase == 16 && (raw.startsWith("0x", ignoreCase = true)) -> raw.substring(2)
          fromBase == 2 && (raw.startsWith("0b", ignoreCase = true)) -> raw.substring(2)
          fromBase == 8 && (raw.startsWith("0o", ignoreCase = true)) -> raw.substring(2)
          else -> raw
        }
      }.replace("_", "").replace(" ", "")
    require(clean.isNotEmpty()) { "请输入数值" }
    val number = BigInteger(clean, fromBase)
    return RadixConversionResult(
      binary = number.toString(2),
      octal = number.toString(8),
      decimal = number.toString(10),
      hex = number.toString(16).uppercase(),
    )
  }

  fun calculateIpv4Cidr(cidrInput: String): Ipv4CidrResult {
    val trimmed = cidrInput.trim()
    val parts = trimmed.split("/")
    require(parts.size == 2) { "格式应为 IPv4/前缀，例如 192.168.1.10/24" }
    val prefix = parts[1].toIntOrNull() ?: throw IllegalArgumentException("掩码前缀必须为 0..32")
    require(prefix in 0..32) { "掩码前缀范围必须为 0..32" }

    val octets = parts[0].split(".")
    require(octets.size == 4) { "IPv4 地址必须包含 4 段" }
    var ipInt = 0L
    for (octetStr in octets) {
      val octet = octetStr.toIntOrNull() ?: throw IllegalArgumentException("非法 IP 段: $octetStr")
      require(octet in 0..255) { "IP 段超出 0..255: $octet" }
      ipInt = (ipInt shl 8) or octet.toLong()
    }

    val maskInt = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    val wildcardInt = maskInt xor 0xFFFFFFFFL
    val networkInt = ipInt and maskInt
    val broadcastInt = networkInt or wildcardInt
    val total = 1L shl (32 - prefix)
    val usable =
      when (prefix) {
        32 -> 1L
        31 -> 2L
        else -> (total - 2L).coerceAtLeast(0L)
      }
    val firstHost = if (prefix >= 31) networkInt else networkInt + 1L
    val lastHost = if (prefix >= 31) broadcastInt else broadcastInt - 1L

    return Ipv4CidrResult(
      cidr = "${longToIpv4(networkInt)}/$prefix",
      networkAddress = longToIpv4(networkInt),
      broadcastAddress = longToIpv4(broadcastInt),
      netmask = longToIpv4(maskInt),
      wildcardMask = longToIpv4(wildcardInt),
      firstUsableHost = longToIpv4(firstHost),
      lastUsableHost = longToIpv4(lastHost),
      totalAddresses = total,
      usableHosts = usable,
      binaryMask =
        (0..3).joinToString(".") { idx ->
          val byteVal = ((maskInt ushr ((3 - idx) * 8)) and 0xFFL).toInt()
          byteVal.toString(2).padStart(8, '0')
        },
    )
  }

  private fun longToIpv4(value: Long): String {
    return listOf(
      (value ushr 24) and 0xFFL,
      (value ushr 16) and 0xFFL,
      (value ushr 8) and 0xFFL,
      value and 0xFFL,
    ).joinToString(".")
  }
}

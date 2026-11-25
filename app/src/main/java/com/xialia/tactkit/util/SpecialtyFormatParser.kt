package com.xialia.tactkit.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class CalendarEventInfo(
  val title: String,
  val startRaw: String,
  val endRaw: String,
  val startEpochMs: Long?,
  val endEpochMs: Long?,
  val isAllDay: Boolean,
  val location: String,
  val description: String,
  val organizer: String,
)

data class CertificateEntryInfo(
  val alias: String,
  val subject: String,
  val issuer: String,
  val serialHex: String,
  val validFrom: String,
  val validUntil: String,
  val isCurrentlyValid: Boolean,
  val signatureAlgorithm: String,
  val sha256Fingerprint: String,
)

data class ApkInspectionInfo(
  val appLabel: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val minSdk: Int?,
  val targetSdk: Int?,
  val permissions: List<String>,
  val dexCount: Int,
  val entryCount: Int,
)

data class VCardContactInfo(
  val fullName: String,
  val organization: String,
  val title: String,
  val phones: List<String>,
  val emails: List<String>,
  val address: String,
  val note: String,
)

data class ConfigKvEntry(
  val section: String,
  val key: String,
  val value: String,
  val isSensitive: Boolean,
)

data class LogSummaryInfo(
  val totalLines: Int,
  val errorCount: Int,
  val warnCount: Int,
  val infoCount: Int,
  val previewLines: List<String>,
)

object SpecialtyFormatParser {

  private val sensitiveKeyRegex =
    Regex("(secret|password|passwd|token|api_?key|private_?key|credential|auth)", RegexOption.IGNORE_CASE)

  fun parseIcsEvents(rawText: String): List<CalendarEventInfo> {
    val lines = unfoldRfcLines(rawText)
    val events = mutableListOf<CalendarEventInfo>()
    var inEvent = false
    var summary = ""
    var dtStartRaw = ""
    var dtEndRaw = ""
    var startMs: Long? = null
    var endMs: Long? = null
    var isAllDay = false
    var location = ""
    var description = ""
    var organizer = ""

    for (line in lines) {
      val trimmed = line.trim()
      when {
        trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> {
          inEvent = true
          summary = ""
          dtStartRaw = ""
          dtEndRaw = ""
          startMs = null
          endMs = null
          isAllDay = false
          location = ""
          description = ""
          organizer = ""
        }
        trimmed.equals("END:VEVENT", ignoreCase = true) && inEvent -> {
          events.add(
            CalendarEventInfo(
              title = summary.ifBlank { "未命名日程" },
              startRaw = dtStartRaw,
              endRaw = dtEndRaw,
              startEpochMs = startMs,
              endEpochMs = endMs,
              isAllDay = isAllDay,
              location = location,
              description = description,
              organizer = organizer,
            )
          )
          inEvent = false
        }
        inEvent -> {
          val colonIdx = trimmed.indexOf(':')
          if (colonIdx > 0) {
            val left = trimmed.substring(0, colonIdx)
            val propName = left.substringBefore(';').uppercase(Locale.ROOT)
            val value = unescapeIcalValue(trimmed.substring(colonIdx + 1))
            when (propName) {
              "SUMMARY" -> summary = value
              "DTSTART" -> {
                val parsed = parseIcalDateTime(left, value)
                dtStartRaw = parsed.first
                startMs = parsed.second
                if (parsed.third) isAllDay = true
              }
              "DTEND" -> {
                val parsed = parseIcalDateTime(left, value)
                dtEndRaw = parsed.first
                endMs = parsed.second
              }
              "LOCATION" -> location = value
              "DESCRIPTION" -> description = value
              "ORGANIZER" -> organizer = value.removePrefix("mailto:")
            }
          }
        }
      }
    }
    return events
  }

  private fun parseIcalDateTime(paramLeft: String, rawVal: String): Triple<String, Long?, Boolean> {
    val clean = rawVal.trim()
    val tzId =
      paramLeft.split(';').firstOrNull { it.startsWith("TZID=", ignoreCase = true) }?.substringAfter('=')
    val zone =
      runCatching { if (!tzId.isNullOrBlank()) ZoneId.of(tzId) else ZoneId.systemDefault() }
        .getOrDefault(ZoneId.systemDefault())
    val displayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    return runCatching {
      when {
        clean.length == 8 && clean.all { it.isDigit() } -> {
          val date = LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE)
          val ms = date.atStartOfDay(zone).toInstant().toEpochMilli()
          Triple(date.toString(), ms, true)
        }
        clean.endsWith("Z", ignoreCase = true) && clean.length >= 16 -> {
          val base = clean.dropLast(1).take(15)
          val ldt = LocalDateTime.parse(base, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
          val instant = ldt.toInstant(ZoneOffset.UTC)
          val localFormatted = instant.atZone(ZoneId.systemDefault()).format(displayFmt)
          Triple(localFormatted, instant.toEpochMilli(), false)
        }
        clean.contains('T') && clean.length >= 15 -> {
          val base = clean.take(15)
          val ldt = LocalDateTime.parse(base, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
          val instant = ldt.atZone(zone).toInstant()
          Triple(ldt.format(displayFmt), instant.toEpochMilli(), false)
        }
        else -> Triple(clean, null, false)
      }
    }.getOrElse { Triple(clean, null, false) }
  }

  fun parseCertificatesFromBytes(bytes: ByteArray): List<CertificateEntryInfo> {
    val factory = CertificateFactory.getInstance("X.509")
    val certs =
      ByteArrayInputStream(bytes).use { stream ->
        factory.generateCertificates(stream).filterIsInstance<X509Certificate>()
      }
    return certs.mapIndexed { idx, cert ->
      cert.toEntryInfo(alias = "certificate_${idx + 1}")
    }
  }

  fun inspectKeyStoreInMemory(
    bytes: ByteArray,
    fileName: String,
    passwordChars: CharArray,
  ): List<CertificateEntryInfo> {
    val lower = fileName.lowercase(Locale.ROOT)
    val candidateTypes =
      if (lower.endsWith(".jks")) listOf("JKS", "PKCS12", "BKS")
      else listOf("PKCS12", "JKS", "BKS")

    var lastError: Throwable? = null
    try {
      for (type in candidateTypes) {
        val attempt =
          runCatching {
            val ks = KeyStore.getInstance(type)
            ByteArrayInputStream(bytes).use { ks.load(it, passwordChars) }
            val result = mutableListOf<CertificateEntryInfo>()
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
              val alias = aliases.nextElement()
              val cert = ks.getCertificate(alias) as? X509Certificate
              if (cert != null) {
                result.add(cert.toEntryInfo(alias = alias))
              }
            }
            result
          }
        if (attempt.isSuccess) return attempt.getOrThrow()
        lastError = attempt.exceptionOrNull()
      }
      throw IllegalArgumentException("密钥库密码错误或格式不支持：${lastError?.message ?: "无法解锁"}")
    } finally {
      passwordChars.fill('\u0000')
    }
  }

  private fun X509Certificate.toEntryInfo(alias: String): CertificateEntryInfo {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
    val now = Date()
    val valid = !now.before(notBefore) && !now.after(notAfter)
    val sha256 =
      MessageDigest.getInstance("SHA-256")
        .digest(encoded)
        .joinToString(":") { "%02X".format(it) }
    return CertificateEntryInfo(
      alias = alias,
      subject = subjectX500Principal?.name.orEmpty(),
      issuer = issuerX500Principal?.name.orEmpty(),
      serialHex = serialNumber.toString(16).uppercase(Locale.ROOT),
      validFrom = dateFmt.format(notBefore),
      validUntil = dateFmt.format(notAfter),
      isCurrentlyValid = valid,
      signatureAlgorithm = sigAlgName.orEmpty(),
      sha256Fingerprint = sha256,
    )
  }

  fun inspectApkUri(context: Context, uri: Uri): ApkInspectionInfo {
    val tempApk = File.createTempFile("tactkit_apk_inspect_", ".apk", context.cacheDir)
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        tempApk.outputStream().use { out -> input.copyTo(out) }
      } ?: error("无法读取 APK 文件")

      var dexCount = 0
      var entryCount = 0
      runCatching {
        ZipInputStream(tempApk.inputStream()).use { zis ->
          var entry = zis.nextEntry
          while (entry != null) {
            entryCount++
            if (entry.name.endsWith(".dex", ignoreCase = true)) dexCount++
            entry = zis.nextEntry
          }
        }
      }

      val pm = context.packageManager
      val pkgInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageArchiveInfo(
            tempApk.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageArchiveInfo(tempApk.absolutePath, PackageManager.GET_PERMISSIONS)
        } ?: error("未能解析 APK 清单信息（可能非有效 APK）")

      val appInfo = pkgInfo.applicationInfo
      appInfo?.sourceDir = tempApk.absolutePath
      appInfo?.publicSourceDir = tempApk.absolutePath
      val label =
        runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull().orEmpty()
          .ifBlank { pkgInfo.packageName }

      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          pkgInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          pkgInfo.versionCode.toLong()
        }

      return ApkInspectionInfo(
        appLabel = label,
        packageName = pkgInfo.packageName.orEmpty(),
        versionName = pkgInfo.versionName.orEmpty().ifBlank { "未知" },
        versionCode = versionCode,
        minSdk = appInfo?.minSdkVersion,
        targetSdk = appInfo?.targetSdkVersion,
        permissions = pkgInfo.requestedPermissions?.toList().orEmpty(),
        dexCount = dexCount,
        entryCount = entryCount,
      )
    } finally {
      tempApk.delete()
    }
  }

  fun parseVCardContacts(rawText: String): List<VCardContactInfo> {
    val lines = unfoldRfcLines(rawText.replace("=\r\n", "").replace("=\n", ""))
    val contacts = mutableListOf<VCardContactInfo>()
    var inCard = false
    var fullName = ""
    var fallbackName = ""
    var org = ""
    var title = ""
    val phones = mutableListOf<String>()
    val emails = mutableListOf<String>()
    var address = ""
    var note = ""

    for (line in lines) {
      val trimmed = line.trim()
      when {
        trimmed.equals("BEGIN:VCARD", ignoreCase = true) -> {
          inCard = true
          fullName = ""
          fallbackName = ""
          org = ""
          title = ""
          phones.clear()
          emails.clear()
          address = ""
          note = ""
        }
        trimmed.equals("END:VCARD", ignoreCase = true) && inCard -> {
          contacts.add(
            VCardContactInfo(
              fullName = fullName.ifBlank { fallbackName }.ifBlank { "未命名联系人" },
              organization = org,
              title = title,
              phones = phones.toList(),
              emails = emails.toList(),
              address = address,
              note = note,
            )
          )
          inCard = false
        }
        inCard -> {
          val colon = trimmed.indexOf(':')
          if (colon > 0) {
            val left = trimmed.substring(0, colon)
            val prop = left.substringBefore(';').uppercase(Locale.ROOT)
            val rawVal = trimmed.substring(colon + 1)
            val decoded =
              if (left.contains("QUOTED-PRINTABLE", ignoreCase = true)) {
                decodeQuotedPrintable(rawVal)
              } else {
                unescapeIcalValue(rawVal)
              }
            when (prop) {
              "FN" -> fullName = decoded
              "N" -> fallbackName = decoded.split(';').filter { it.isNotBlank() }.joinToString(" ")
              "ORG" -> org = decoded.replace(';', ' ').trim()
              "TITLE" -> title = decoded
              "TEL" -> if (decoded.isNotBlank()) phones.add(decoded)
              "EMAIL" -> if (decoded.isNotBlank()) emails.add(decoded)
              "ADR" -> address = decoded.split(';').filter { it.isNotBlank() }.joinToString(" ")
              "NOTE" -> note = decoded
            }
          }
        }
      }
    }
    return contacts
  }

  fun parseEnvOrTomlConfig(rawText: String, isToml: Boolean): List<ConfigKvEntry> {
    val result = mutableListOf<ConfigKvEntry>()
    var currentSection = if (isToml) "root" else "ENV"
    for (rawLine in rawText.lines()) {
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
      if (isToml && trimmed.startsWith("[") && trimmed.endsWith("]")) {
        currentSection = trimmed.removePrefix("[").removeSuffix("]").trim()
        continue
      }
      val cleanLine = if (!isToml && trimmed.startsWith("export ")) trimmed.removePrefix("export ").trim() else trimmed
      val eqIdx = cleanLine.indexOf('=')
      if (eqIdx > 0) {
        val key = cleanLine.substring(0, eqIdx).trim()
        val value =
          cleanLine.substring(eqIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        result.add(
          ConfigKvEntry(
            section = currentSection,
            key = key,
            value = value,
            isSensitive = sensitiveKeyRegex.containsMatchIn(key),
          )
        )
      }
    }
    return result
  }

  fun summarizeLogText(rawText: String, maxPreviewLines: Int = 250): LogSummaryInfo {
    val lines = rawText.lines()
    var errors = 0
    var warns = 0
    var infos = 0
    lines.forEach { line ->
      when {
        line.contains("ERROR", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) -> errors++
        line.contains("WARN", ignoreCase = true) -> warns++
        line.contains("INFO", ignoreCase = true) -> infos++
      }
    }
    return LogSummaryInfo(
      totalLines = lines.size,
      errorCount = errors,
      warnCount = warns,
      infoCount = infos,
      previewLines = lines.take(maxPreviewLines),
    )
  }

  private fun unfoldRfcLines(raw: String): List<String> {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
    val out = mutableListOf<String>()
    for (line in normalized.split('\n')) {
      if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
        out[out.lastIndex] = out.last() + line.drop(1)
      } else {
        out.add(line)
      }
    }
    return out
  }

  private fun unescapeIcalValue(value: String): String {
    return value
      .replace("\\n", "\n")
      .replace("\\N", "\n")
      .replace("\\,", ",")
      .replace("\\;", ";")
      .replace("\\\\", "\\")
      .trim()
  }

  private fun decodeQuotedPrintable(input: String): String {
    val baos = ByteArrayOutputStream()
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      if (ch == '=' && i + 2 < input.length) {
        val hex = input.substring(i + 1, i + 3)
        val byteVal = hex.toIntOrNull(16)
        if (byteVal != null) {
          baos.write(byteVal)
          i += 3
          continue
        }
      }
      baos.write(ch.code and 0xFF)
      i++
    }
    return String(baos.toByteArray(), Charsets.UTF_8)
  }
}

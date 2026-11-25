package com.xialia.tactkit.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.xialia.tactkit.engine.DocxEngine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SpecialtyFileKind {
  STANDARD,
  DOCX,
  CALENDAR_ICS,
  CERTIFICATE,
  KEYSTORE_SECRET,
  APK_PACKAGE,
  VCARD_CONTACT,
  CONFIG_KV,
  LOG_FILE,
}

data class QuickLookFileInspection(
  val uriString: String?,
  val fileName: String,
  val fileSizeBytes: Long,
  val mimeType: String,
  val sha256: String,
  val textPreview: String?,
  val isTextTruncated: Boolean = false,
  val imagePreview: Bitmap? = null,
  val specialtyKind: SpecialtyFileKind = SpecialtyFileKind.STANDARD,
  val calendarEvents: List<CalendarEventInfo> = emptyList(),
  val certificates: List<CertificateEntryInfo> = emptyList(),
  val vcards: List<VCardContactInfo> = emptyList(),
  val configEntries: List<ConfigKvEntry> = emptyList(),
  val logSummary: LogSummaryInfo? = null,
  val apkInfo: ApkInspectionInfo? = null,
  val specialtyError: String? = null,
)

object FileInspectionHelper {

  private const val BUFFER_SIZE = 8 * 1024
  private const val MAX_TEXT_PREVIEW_BYTES = 32 * 1024
  private const val DOCX_MIME =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

  private val TEXT_EXTENSIONS =
    setOf(
      "txt",
      "md",
      "markdown",
      "json",
      "xml",
      "yaml",
      "yml",
      "csv",
      "tsv",
      "log",
      "kt",
      "kts",
      "java",
      "py",
      "js",
      "ts",
      "html",
      "css",
      "sh",
      "ini",
      "toml",
      "env",
      "properties",
      "c",
      "cpp",
      "h",
      "rs",
      "go",
      "sql",
      "ics",
      "vcs",
      "vcf",
    )

  /**
   * Computes SHA-256 over any InputStream using a fixed 8KB buffer so large files never cause OOM.
   */
  fun computeSha256(inputStream: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
      digest.update(buffer, 0, bytesRead)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** Computes SHA-256 for raw UTF-8 text snippets (e.g. ACTION_SEND plain text). */
  fun computeTextSha256(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知大小"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
  }

  fun isTextReadable(mimeType: String, fileName: String): Boolean {
    val normalizedMime = mimeType.lowercase(Locale.US)
    if (
      normalizedMime.startsWith("text/") ||
        normalizedMime == "application/json" ||
        normalizedMime == "application/xml" ||
        normalizedMime == "application/javascript" ||
        normalizedMime == "application/x-yaml"
    ) {
      return true
    }
    val lowerName = fileName.lowercase(Locale.US)
    if (lowerName == ".env" || lowerName.startsWith(".env.")) return true
    val ext = lowerName.substringAfterLast('.', "")
    return ext in TEXT_EXTENSIONS
  }

  fun readTextPreview(
    inputStream: InputStream,
    maxBytes: Int = MAX_TEXT_PREVIEW_BYTES,
  ): Pair<String, Boolean> {
    val buffer = ByteArray(BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    var remaining = maxBytes
    var truncated = false

    while (remaining > 0) {
      val toRead = minOf(buffer.size, remaining)
      val read = inputStream.read(buffer, 0, toRead)
      if (read == -1) break
      output.write(buffer, 0, read)
      remaining -= read
    }
    if (remaining == 0 && inputStream.read() != -1) {
      truncated = true
    }
    return String(output.toByteArray(), Charsets.UTF_8) to truncated
  }

  suspend fun inspectUri(
    contentResolver: ContentResolver,
    uri: Uri,
    context: Context? = null,
  ): QuickLookFileInspection =
    withContext(Dispatchers.IO) {
      var fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件"
      var fileSize = -1L

      contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
          if (nameIndex >= 0) {
            cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { fileName = it }
          }
          if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
            fileSize = cursor.getLong(sizeIndex)
          }
        }
      }

      val mimeType =
        contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
          ?: guessMimeFromName(fileName)

      var sha256 = "无法读取摘要"
      var countedBytes = 0L
      contentResolver.openInputStream(uri)?.use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
          digest.update(buffer, 0, read)
          countedBytes += read
        }
        sha256 = digest.digest().joinToString("") { "%02x".format(it) }
      }
      if (fileSize < 0 && countedBytes > 0) {
        fileSize = countedBytes
      }

      val lowerName = fileName.lowercase(Locale.US)
      val ext = lowerName.substringAfterLast('.', "")
      val lowerMime = mimeType.lowercase(Locale.US)

      val specialtyKind = detectSpecialtyKind(lowerName, ext, lowerMime)

      var textPreview: String? = null
      var isTruncated = false
      var calendarEvents: List<CalendarEventInfo> = emptyList()
      var certificates: List<CertificateEntryInfo> = emptyList()
      var vcards: List<VCardContactInfo> = emptyList()
      var configEntries: List<ConfigKvEntry> = emptyList()
      var logSummary: LogSummaryInfo? = null
      var apkInfo: ApkInspectionInfo? = null
      var specialtyError: String? = null

      when (specialtyKind) {
        SpecialtyFileKind.DOCX -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
              DocxEngine.extractQuickLookText(stream)
            }
          }.onSuccess { text ->
            textPreview = text
          }.onFailure { err ->
            specialtyError = "读取 .docx 正文失败：${err.message ?: "文件损坏"}"
          }
        }
        SpecialtyFileKind.CALENDAR_ICS -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { readTextPreview(it, maxBytes = 256 * 1024).first }
              .orEmpty()
          }.onSuccess { raw ->
            textPreview = raw.take(MAX_TEXT_PREVIEW_BYTES)
            calendarEvents = SpecialtyFormatParser.parseIcsEvents(raw)
          }.onFailure { err ->
            specialtyError = "解析日历文件失败：${err.message}"
          }
        }
        SpecialtyFileKind.CERTIFICATE -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
          }.onSuccess { bytes ->
            runCatching { SpecialtyFormatParser.parseCertificatesFromBytes(bytes) }
              .onSuccess { certs -> certificates = certs }
              .onFailure { err -> specialtyError = "解析 X.509 证书失败：${err.message}" }
          }.onFailure { err ->
            specialtyError = "读取证书文件失败：${err.message}"
          }
        }
        SpecialtyFileKind.KEYSTORE_SECRET -> {
          // Never auto-decrypt or retain keystores; QuickLookActivity unlocks ephemerally in memory.
          textPreview = null
        }
        SpecialtyFileKind.APK_PACKAGE -> {
          if (context != null) {
            runCatching { SpecialtyFormatParser.inspectApkUri(context, uri) }
              .onSuccess { apkInfo = it }
              .onFailure { err -> specialtyError = "解析 APK 清单失败：${err.message}" }
          } else {
            specialtyError = "需要应用上下文以读取 APK 清单"
          }
        }
        SpecialtyFileKind.VCARD_CONTACT -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { readTextPreview(it, maxBytes = 256 * 1024).first }
              .orEmpty()
          }.onSuccess { raw ->
            vcards = SpecialtyFormatParser.parseVCardContacts(raw)
            textPreview = raw.take(MAX_TEXT_PREVIEW_BYTES)
          }.onFailure { err ->
            specialtyError = "解析 vCard 名片失败：${err.message}"
          }
        }
        SpecialtyFileKind.CONFIG_KV -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { readTextPreview(it, maxBytes = 128 * 1024) }
          }.onSuccess { pair ->
            if (pair != null) {
              configEntries =
                SpecialtyFormatParser.parseEnvOrTomlConfig(pair.first, isToml = ext == "toml")
              textPreview = pair.first.take(MAX_TEXT_PREVIEW_BYTES)
              isTruncated = pair.second
            }
          }.onFailure { err ->
            specialtyError = "解析配置文件失败：${err.message}"
          }
        }
        SpecialtyFileKind.LOG_FILE -> {
          runCatching {
            contentResolver.openInputStream(uri)?.use { readTextPreview(it, maxBytes = 256 * 1024) }
          }.onSuccess { pair ->
            if (pair != null) {
              logSummary = SpecialtyFormatParser.summarizeLogText(pair.first)
              textPreview = pair.first.take(MAX_TEXT_PREVIEW_BYTES)
              isTruncated = pair.second
            }
          }.onFailure { err ->
            specialtyError = "读取日志文件失败：${err.message}"
          }
        }
        SpecialtyFileKind.STANDARD -> {
          if (isTextReadable(mimeType, fileName)) {
            contentResolver.openInputStream(uri)?.use { stream ->
              val (text, truncated) = readTextPreview(stream)
              textPreview = text
              isTruncated = truncated
            }
          }
        }
      }

      var imagePreview: Bitmap? = null
      if (lowerMime.startsWith("image/")) {
        imagePreview = decodeSampledBitmap(contentResolver, uri, maxDimension = 960)
      }

      QuickLookFileInspection(
        uriString = uri.toString(),
        fileName = fileName,
        fileSizeBytes = fileSize.coerceAtLeast(0L),
        mimeType = mimeType,
        sha256 = sha256,
        textPreview = textPreview,
        isTextTruncated = isTruncated,
        imagePreview = imagePreview,
        specialtyKind = specialtyKind,
        calendarEvents = calendarEvents,
        certificates = certificates,
        vcards = vcards,
        configEntries = configEntries,
        logSummary = logSummary,
        apkInfo = apkInfo,
        specialtyError = specialtyError,
      )
    }

  private fun detectSpecialtyKind(
    lowerName: String,
    ext: String,
    lowerMime: String,
  ): SpecialtyFileKind {
    return when {
      ext == "docx" || lowerMime == DOCX_MIME -> SpecialtyFileKind.DOCX
      ext == "ics" || ext == "vcs" || lowerMime == "text/calendar" -> SpecialtyFileKind.CALENDAR_ICS
      ext in setOf("pem", "crt", "cer") ||
        lowerMime == "application/x-x509-ca-cert" ||
        lowerMime == "application/pkix-cert" -> SpecialtyFileKind.CERTIFICATE
      ext in setOf("jks", "p12", "pfx") || lowerMime == "application/x-pkcs12" ->
        SpecialtyFileKind.KEYSTORE_SECRET
      ext == "apk" || lowerMime == "application/vnd.android.package-archive" ->
        SpecialtyFileKind.APK_PACKAGE
      ext == "vcf" || lowerMime == "text/vcard" || lowerMime == "text/x-vcard" ->
        SpecialtyFileKind.VCARD_CONTACT
      ext == "toml" || ext == "env" || lowerName == ".env" || lowerName.startsWith(".env.") ->
        SpecialtyFileKind.CONFIG_KV
      ext == "log" -> SpecialtyFileKind.LOG_FILE
      else -> SpecialtyFileKind.STANDARD
    }
  }

  private fun decodeSampledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDimension: Int,
  ): Bitmap? {
    return try {
      val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, boundsOptions)
      }
      var inSampleSize = 1
      val width = boundsOptions.outWidth
      val height = boundsOptions.outHeight
      if (width > maxDimension || height > maxDimension) {
        val halfW = width / 2
        val halfH = height / 2
        while (halfW / inSampleSize >= maxDimension && halfH / inSampleSize >= maxDimension) {
          inSampleSize *= 2
        }
      }
      val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
      contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun guessMimeFromName(fileName: String): String {
    val lower = fileName.lowercase(Locale.US)
    if (lower == ".env" || lower.startsWith(".env.")) return "text/plain"
    return when (lower.substringAfterLast('.', "")) {
      "txt", "log", "env", "toml" -> "text/plain"
      "md", "markdown" -> "text/markdown"
      "json" -> "application/json"
      "xml" -> "application/xml"
      "yaml", "yml" -> "application/x-yaml"
      "csv" -> "text/csv"
      "pdf" -> "application/pdf"
      "docx" -> DOCX_MIME
      "ics", "vcs" -> "text/calendar"
      "vcf" -> "text/vcard"
      "pem", "crt", "cer" -> "application/x-x509-ca-cert"
      "p12", "pfx" -> "application/x-pkcs12"
      "apk" -> "application/vnd.android.package-archive"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "webp" -> "image/webp"
      "zip" -> "application/zip"
      "7z" -> "application/x-7z-compressed"
      "tar" -> "application/x-tar"
      "gz" -> "application/gzip"
      "xz" -> "application/x-xz"
      else -> "application/octet-stream"
    }
  }
}

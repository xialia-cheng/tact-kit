package com.xialia.tactkit.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuickLookFileInspection(
  val uriString: String?,
  val fileName: String,
  val fileSizeBytes: Long,
  val mimeType: String,
  val sha256: String,
  val textPreview: String?,
  val isTextTruncated: Boolean = false,
  val imagePreview: Bitmap? = null,
)

object FileInspectionHelper {

  private const val BUFFER_SIZE = 8 * 1024
  private const val MAX_TEXT_PREVIEW_BYTES = 32 * 1024

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
      "properties",
      "c",
      "cpp",
      "h",
      "rs",
      "go",
      "sql",
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
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
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

      var textPreview: String? = null
      var isTruncated = false
      if (isTextReadable(mimeType, fileName)) {
        contentResolver.openInputStream(uri)?.use { stream ->
          val (text, truncated) = readTextPreview(stream)
          textPreview = text
          isTruncated = truncated
        }
      }

      var imagePreview: Bitmap? = null
      if (mimeType.lowercase(Locale.US).startsWith("image/")) {
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
      )
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
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
      "txt", "log" -> "text/plain"
      "md", "markdown" -> "text/markdown"
      "json" -> "application/json"
      "pdf" -> "application/pdf"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "webp" -> "image/webp"
      "zip" -> "application/zip"
      else -> "application/octet-stream"
    }
  }
}

package com.xialia.tactkit.engine

import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class FileCategory(
  val label: String,
  val shortLabel: String,
  val icon: ImageVector,
  val extensions: Set<String>,
) {
  DOCUMENTS(
    label = "文档与办公",
    shortLabel = "文档",
    icon = Icons.Rounded.Description,
    extensions = setOf(
      "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
      "txt", "md", "rtf", "csv", "odt", "epub",
    ),
  ),
  CODE_CONFIG(
    label = "代码与配置",
    shortLabel = "代码",
    icon = Icons.Rounded.Code,
    extensions = setOf(
      "json", "yaml", "yml", "xml", "toml", "ini", "env", "log",
      "kt", "java", "py", "js", "ts", "html", "css", "sh", "sql",
      "c", "cpp", "h",
    ),
  ),
  IMAGES(
    label = "图片",
    shortLabel = "图片",
    icon = Icons.Rounded.Image,
    extensions = setOf(
      "jpg", "jpeg", "png", "webp", "gif", "bmp",
      "heic", "heif", "svg", "raw", "dng",
    ),
  ),
  AUDIO_VIDEO(
    label = "音视频",
    shortLabel = "音视频",
    icon = Icons.Rounded.Movie,
    extensions = setOf(
      "mp4", "mov", "mkv", "avi", "webm", "flv", "ts",
      "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus",
    ),
  ),
  ARCHIVES(
    label = "压缩包",
    shortLabel = "压缩包",
    icon = Icons.Rounded.FolderZip,
    extensions = setOf(
      "zip", "7z", "rar", "tar", "gz", "tgz", "xz", "bz2", "zst",
    ),
  ),
  PACKAGES(
    label = "安装包",
    shortLabel = "安装包",
    icon = Icons.Rounded.Android,
    extensions = setOf("apk", "apks", "xapk", "aab"),
  ),
  KEYS_CERTS_CALENDAR(
    label = "密钥与日程",
    shortLabel = "密钥/日程",
    icon = Icons.Rounded.Key,
    extensions = setOf(
      "jks", "keystore", "pem", "crt", "cer", "key",
      "p12", "pfx", "ics", "vcs", "vcf", "tactenc",
    ),
  ),
  OTHER(
    label = "其他",
    shortLabel = "其他",
    icon = Icons.Rounded.InsertDriveFile,
    extensions = emptySet(),
  ),
}

object FileCategoryClassifier {

  private val extensionToCategory: Map<String, FileCategory> =
    buildMap {
      FileCategory.entries.forEach { category ->
        category.extensions.forEach { ext ->
          put(ext.lowercase(Locale.ROOT), category)
        }
      }
    }

  private val customMimeTypes: Map<String, String> =
    mapOf(
      "pdf" to "application/pdf",
      "doc" to "application/msword",
      "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "xls" to "application/vnd.ms-excel",
      "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "ppt" to "application/vnd.ms-powerpoint",
      "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "txt" to "text/plain",
      "md" to "text/markdown",
      "rtf" to "application/rtf",
      "csv" to "text/csv",
      "odt" to "application/vnd.oasis.opendocument.text",
      "epub" to "application/epub+zip",
      "json" to "application/json",
      "yaml" to "text/yaml",
      "yml" to "text/yaml",
      "xml" to "application/xml",
      "toml" to "text/plain",
      "ini" to "text/plain",
      "env" to "text/plain",
      "log" to "text/plain",
      "kt" to "text/plain",
      "java" to "text/x-java-source",
      "py" to "text/x-python",
      "js" to "application/javascript",
      "ts" to "text/typescript",
      "html" to "text/html",
      "css" to "text/css",
      "sh" to "application/x-sh",
      "sql" to "application/sql",
      "c" to "text/x-c",
      "cpp" to "text/x-c++src",
      "h" to "text/x-chdr",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "png" to "image/png",
      "webp" to "image/webp",
      "gif" to "image/gif",
      "bmp" to "image/bmp",
      "heic" to "image/heic",
      "heif" to "image/heif",
      "svg" to "image/svg+xml",
      "raw" to "image/x-dcraw",
      "dng" to "image/x-adobe-dng",
      "mp4" to "video/mp4",
      "mov" to "video/quicktime",
      "mkv" to "video/x-matroska",
      "avi" to "video/x-msvideo",
      "webm" to "video/webm",
      "flv" to "video/x-flv",
      "ts" to "video/mp2t",
      "mp3" to "audio/mpeg",
      "m4a" to "audio/mp4",
      "aac" to "audio/aac",
      "wav" to "audio/wav",
      "flac" to "audio/flac",
      "ogg" to "audio/ogg",
      "opus" to "audio/opus",
      "zip" to "application/zip",
      "7z" to "application/x-7z-compressed",
      "rar" to "application/vnd.rar",
      "tar" to "application/x-tar",
      "gz" to "application/gzip",
      "tgz" to "application/gzip",
      "xz" to "application/x-xz",
      "bz2" to "application/x-bzip2",
      "zst" to "application/zstd",
      "apk" to "application/vnd.android.package-archive",
      "apks" to "application/octet-stream",
      "xapk" to "application/octet-stream",
      "aab" to "application/octet-stream",
      "jks" to "application/octet-stream",
      "keystore" to "application/octet-stream",
      "pem" to "application/x-pem-file",
      "crt" to "application/x-x509-ca-cert",
      "cer" to "application/pkix-cert",
      "key" to "application/octet-stream",
      "p12" to "application/x-pkcs12",
      "pfx" to "application/x-pkcs12",
      "ics" to "text/calendar",
      "vcs" to "text/x-vcalendar",
      "vcf" to "text/vcard",
      "tactenc" to "application/octet-stream",
    )

  fun extractExtension(fileNameOrExt: String): String {
    val trimmed = fileNameOrExt.trim()
    if (trimmed.isEmpty()) return ""
    val leaf = trimmed.substringAfterLast('/').substringAfterLast('\\')
    val dotIndex = leaf.lastIndexOf('.')
    return if (dotIndex >= 0 && dotIndex < leaf.length - 1) {
      leaf.substring(dotIndex + 1).lowercase(Locale.ROOT)
    } else if (!leaf.contains('.')) {
      leaf.lowercase(Locale.ROOT)
    } else {
      ""
    }
  }

  /**
   * Resolves a human-readable extension pill such as "tar.gz" or "pdf" from a filename.
   */
  fun extractDisplayExtension(fileName: String): String {
    val lower = fileName.trim().lowercase(Locale.ROOT)
    return when {
      lower.endsWith(".tar.gz") -> "tar.gz"
      lower.endsWith(".tar.xz") -> "tar.xz"
      else -> extractExtension(fileName)
    }
  }

  fun classify(fileNameOrExt: String): FileCategory {
    val ext = extractExtension(fileNameOrExt)
    return extensionToCategory[ext] ?: FileCategory.OTHER
  }

  fun guessMimeType(fileName: String, isDirectory: Boolean = false): String {
    if (isDirectory) return "vnd.android.document/directory"
    val ext = extractExtension(fileName)
    if (ext.isEmpty()) return "application/octet-stream"
    customMimeTypes[ext]?.let { return it }
    return runCatching {
      MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }.getOrNull() ?: "application/octet-stream"
  }

  fun isVideoFile(fileName: String): Boolean {
    val ext = extractExtension(fileName)
    return ext in setOf("mp4", "mov", "mkv", "avi", "webm", "flv", "ts")
  }

  fun isAudioFile(fileName: String): Boolean {
    val ext = extractExtension(fileName)
    return ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
  }
}

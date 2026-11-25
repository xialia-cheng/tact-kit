package com.xialia.tactkit.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

enum class PackArchiveFormat(
  val label: String,
  val ext: String,
  val mime: String,
) {
  ZIP("ZIP (支持 AES-256 加密)", "zip", "application/zip"),
  SEVEN_Z("7Z 高压缩", "7z", "application/x-7z-compressed"),
  TAR("TAR 归档", "tar", "application/x-tar"),
  TAR_GZ("TAR.GZ (GZIP)", "tar.gz", "application/gzip"),
  TAR_XZ("TAR.XZ (XZ)", "tar.xz", "application/x-xz"),
}

enum class DetectedArchiveType(val label: String) {
  ZIP("ZIP"),
  SEVEN_Z("7Z"),
  TAR("TAR"),
  TAR_GZ("TAR.GZ"),
  TAR_XZ("TAR.XZ"),
  GZ("GZ 单文件"),
  XZ("XZ 单文件"),
}

data class ArchiveEntryNode(
  val path: String,
  val name: String,
  val sizeBytes: Long,
  val compressedSizeBytes: Long,
  val isDirectory: Boolean,
  val lastModifiedMs: Long,
  val isEncrypted: Boolean,
)

data class ArchiveInspection(
  val fileName: String,
  val archiveType: DetectedArchiveType,
  val entries: List<ArchiveEntryNode>,
  val requiresPassword: Boolean,
)

object ArchiveEngine {

  private const val BUFFER_SIZE = 16 * 1024

  suspend fun inspectArchive(context: Context, uri: Uri): ArchiveInspection =
    withContext(Dispatchers.IO) {
      val fileName = resolveDisplayName(context, uri) ?: "archive"
      val type = detectArchiveType(fileName)
      val tempFile = copyUriToTempFile(context, uri, "inspect_${type.name.lowercase(Locale.ROOT)}")
      try {
        inspectArchiveFile(tempFile, fileName, type)
      } finally {
        tempFile.delete()
      }
    }

  fun inspectArchiveFile(
    file: File,
    fileName: String,
    type: DetectedArchiveType = detectArchiveType(fileName),
  ): ArchiveInspection {
    return when (type) {
      DetectedArchiveType.ZIP -> {
        val zip = ZipFile(file)
        val headers = zip.fileHeaders
        val requiresPwd = zip.isEncrypted || headers.any { it.isEncrypted }
        val nodes =
          headers.mapNotNull { h ->
            val cleanPath = sanitizeRelativePath(h.fileName) ?: return@mapNotNull null
            ArchiveEntryNode(
              path = cleanPath,
              name = cleanPath.substringAfterLast('/'),
              sizeBytes = h.uncompressedSize.coerceAtLeast(0L),
              compressedSizeBytes = h.compressedSize.coerceAtLeast(0L),
              isDirectory = h.isDirectory,
              lastModifiedMs = h.lastModifiedTimeEpoch,
              isEncrypted = h.isEncrypted,
            )
          }
        ArchiveInspection(fileName, type, nodes, requiresPwd)
      }
      DetectedArchiveType.SEVEN_Z -> {
        SevenZFile.Builder().setFile(file).get().use { sevenZ ->
          val nodes =
            sevenZ.entries.mapNotNull { entry ->
              val cleanPath = sanitizeRelativePath(entry.name) ?: return@mapNotNull null
              ArchiveEntryNode(
                path = cleanPath,
                name = cleanPath.substringAfterLast('/'),
                sizeBytes = entry.size.coerceAtLeast(0L),
                compressedSizeBytes = -1L,
                isDirectory = entry.isDirectory,
                lastModifiedMs =
                  if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0L,
                isEncrypted = false,
              )
            }
          ArchiveInspection(fileName, type, nodes, requiresPassword = false)
        }
      }
      DetectedArchiveType.TAR,
      DetectedArchiveType.TAR_GZ,
      DetectedArchiveType.TAR_XZ -> {
        openTarStream(file, type).use { tar ->
          val nodes = mutableListOf<ArchiveEntryNode>()
          var entry = tar.nextEntry
          while (entry != null) {
            val cleanPath = sanitizeRelativePath(entry.name)
            if (cleanPath != null) {
              nodes.add(
                ArchiveEntryNode(
                  path = cleanPath,
                  name = cleanPath.substringAfterLast('/'),
                  sizeBytes = entry.size.coerceAtLeast(0L),
                  compressedSizeBytes = -1L,
                  isDirectory = entry.isDirectory,
                  lastModifiedMs = entry.lastModifiedDate?.time ?: 0L,
                  isEncrypted = false,
                )
              )
            }
            entry = tar.nextEntry
          }
          ArchiveInspection(fileName, type, nodes, requiresPassword = false)
        }
      }
      DetectedArchiveType.GZ,
      DetectedArchiveType.XZ -> {
        val innerName = fileName.removeSuffix(".gz").removeSuffix(".xz").ifBlank { "extracted_file" }
        val node =
          ArchiveEntryNode(
            path = innerName,
            name = innerName,
            sizeBytes = -1L,
            compressedSizeBytes = file.length(),
            isDirectory = false,
            lastModifiedMs = file.lastModified(),
            isEncrypted = false,
          )
        ArchiveInspection(fileName, type, listOf(node), requiresPassword = false)
      }
    }
  }

  suspend fun extractSingleEntryToDirectory(
    context: Context,
    archiveUri: Uri,
    entryPath: String,
    targetTreeUri: Uri,
    password: String? = null,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = resolveDisplayName(context, archiveUri) ?: "archive"
        val type = detectArchiveType(fileName)
        val cleanTarget =
          sanitizeRelativePath(entryPath) ?: error("非法压缩包条目路径：$entryPath")
        val tempFile = copyUriToTempFile(context, archiveUri, "single_extract")
        try {
          val leafName = cleanTarget.substringAfterLast('/')
          extractSingleFromFile(tempFile, type, cleanTarget, password) { input ->
            val createdUri =
              createSafDocumentInTree(
                context = context,
                treeUri = targetTreeUri,
                relativeDir = cleanTarget.substringBeforeLast('/', ""),
                displayName = leafName,
              )
            context.contentResolver.openOutputStream(createdUri, "wt")?.use { out ->
              input.copyTo(out, BUFFER_SIZE)
              out.flush()
            } ?: error("无法写入目标文件：$leafName")
          }
          leafName
        } finally {
          tempFile.delete()
        }
      }
    }

  suspend fun extractAllToDirectory(
    context: Context,
    archiveUri: Uri,
    targetTreeUri: Uri,
    password: String? = null,
  ): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = resolveDisplayName(context, archiveUri) ?: "archive"
        val type = detectArchiveType(fileName)
        val tempFile = copyUriToTempFile(context, archiveUri, "all_extract")
        try {
          var count = 0
          extractAllFromFile(tempFile, fileName, type, password) { relPath, stream ->
            val clean = sanitizeRelativePath(relPath) ?: return@extractAllFromFile
            val dir = clean.substringBeforeLast('/', "")
            val leaf = clean.substringAfterLast('/')
            val outUri = createSafDocumentInTree(context, targetTreeUri, dir, leaf)
            context.contentResolver.openOutputStream(outUri, "wt")?.use { out ->
              stream.copyTo(out, BUFFER_SIZE)
              out.flush()
            } ?: error("无法写入文件：$leaf")
            count++
          }
          count
        } finally {
          tempFile.delete()
        }
      }
    }

  fun extractSingleFromFile(
    file: File,
    type: DetectedArchiveType,
    cleanTargetPath: String,
    password: String? = null,
    onStream: (InputStream) -> Unit,
  ) {
    when (type) {
      DetectedArchiveType.ZIP -> {
        val zip = ZipFile(file)
        if (!password.isNullOrEmpty()) {
          zip.setPassword(password.toCharArray())
        }
        val header =
          zip.fileHeaders.firstOrNull {
            !it.isDirectory && sanitizeRelativePath(it.fileName) == cleanTargetPath
          } ?: error("未找到条目：$cleanTargetPath")
        if (header.isEncrypted && password.isNullOrEmpty()) {
          error("该条目受密码保护，请先输入解压密码")
        }
        zip.getInputStream(header).use { onStream(it) }
      }
      DetectedArchiveType.SEVEN_Z -> {
        SevenZFile.Builder().setFile(file).get().use { sevenZ ->
          var entry: SevenZArchiveEntry? = sevenZ.nextEntry
          var found = false
          while (entry != null) {
            if (!entry.isDirectory && sanitizeRelativePath(entry.name) == cleanTargetPath) {
              val stream =
                object : InputStream() {
                  override fun read(): Int = sevenZ.read()
                  override fun read(b: ByteArray, off: Int, len: Int): Int =
                    sevenZ.read(b, off, len)
                }
              onStream(stream)
              found = true
              break
            }
            entry = sevenZ.nextEntry
          }
          if (!found) error("7Z 中未找到条目：$cleanTargetPath")
        }
      }
      DetectedArchiveType.TAR,
      DetectedArchiveType.TAR_GZ,
      DetectedArchiveType.TAR_XZ -> {
        openTarStream(file, type).use { tar ->
          var entry = tar.nextEntry
          var found = false
          while (entry != null) {
            if (!entry.isDirectory && sanitizeRelativePath(entry.name) == cleanTargetPath) {
              onStream(tar)
              found = true
              break
            }
            entry = tar.nextEntry
          }
          if (!found) error("归档中未找到条目：$cleanTargetPath")
        }
      }
      DetectedArchiveType.GZ -> {
        GzipCompressorInputStream(BufferedInputStream(file.inputStream())).use { onStream(it) }
      }
      DetectedArchiveType.XZ -> {
        XZCompressorInputStream(BufferedInputStream(file.inputStream())).use { onStream(it) }
      }
    }
  }

  fun extractAllFromFile(
    file: File,
    fileName: String,
    type: DetectedArchiveType,
    password: String? = null,
    onEntry: (relativePath: String, stream: InputStream) -> Unit,
  ) {
    when (type) {
      DetectedArchiveType.ZIP -> {
        val zip = ZipFile(file)
        if (!password.isNullOrEmpty()) {
          zip.setPassword(password.toCharArray())
        }
        val headers = zip.fileHeaders.filter { !it.isDirectory }
        if (headers.any { it.isEncrypted } && password.isNullOrEmpty()) {
          error("压缩包已加密，请输入密码后再解压")
        }
        headers.forEach { header ->
          val clean = sanitizeRelativePath(header.fileName) ?: return@forEach
          zip.getInputStream(header).use { onEntry(clean, it) }
        }
      }
      DetectedArchiveType.SEVEN_Z -> {
        SevenZFile.Builder().setFile(file).get().use { sevenZ ->
          var entry = sevenZ.nextEntry
          while (entry != null) {
            val clean = sanitizeRelativePath(entry.name)
            if (!entry.isDirectory && clean != null) {
              val stream =
                object : InputStream() {
                  override fun read(): Int = sevenZ.read()
                  override fun read(b: ByteArray, off: Int, len: Int): Int =
                    sevenZ.read(b, off, len)
                }
              onEntry(clean, stream)
            }
            entry = sevenZ.nextEntry
          }
        }
      }
      DetectedArchiveType.TAR,
      DetectedArchiveType.TAR_GZ,
      DetectedArchiveType.TAR_XZ -> {
        openTarStream(file, type).use { tar ->
          var entry = tar.nextEntry
          while (entry != null) {
            val clean = sanitizeRelativePath(entry.name)
            if (!entry.isDirectory && clean != null) {
              onEntry(clean, tar)
            }
            entry = tar.nextEntry
          }
        }
      }
      DetectedArchiveType.GZ -> {
        val target = fileName.removeSuffix(".gz").ifBlank { "output.bin" }
        GzipCompressorInputStream(BufferedInputStream(file.inputStream())).use {
          onEntry(target, it)
        }
      }
      DetectedArchiveType.XZ -> {
        val target = fileName.removeSuffix(".xz").ifBlank { "output.bin" }
        XZCompressorInputStream(BufferedInputStream(file.inputStream())).use {
          onEntry(target, it)
        }
      }
    }
  }

  suspend fun packFilesToArchive(
    context: Context,
    inputUris: List<Uri>,
    outputUri: Uri,
    format: PackArchiveFormat,
    zipPassword: String? = null,
  ): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(inputUris.isNotEmpty()) { "请至少选择一个要打包的文件" }
        val stagedFiles = mutableListOf<Pair<String, File>>()
        val tempOut = File.createTempFile("tactkit_pack_", ".${format.ext}", context.cacheDir)
        try {
          val usedNames = mutableSetOf<String>()
          inputUris.forEachIndexed { idx, uri ->
            val baseName = resolveDisplayName(context, uri) ?: "file_${idx + 1}"
            val uniqueName =
              if (usedNames.add(baseName)) baseName else "${idx + 1}_$baseName"
            val tmp = copyUriToTempFile(context, uri, "pack_item_${idx}")
            stagedFiles.add(uniqueName to tmp)
          }

          packLocalFiles(stagedFiles, tempOut, format, zipPassword)

          context.contentResolver.openOutputStream(outputUri, "wt")?.use { out ->
            tempOut.inputStream().use { input ->
              input.copyTo(out, BUFFER_SIZE)
              out.flush()
            }
          } ?: error("无法写入目标压缩包")

          stagedFiles.size
        } finally {
          stagedFiles.forEach { (_, f) -> f.delete() }
          tempOut.delete()
        }
      }
    }

  fun packLocalFiles(
    namedFiles: List<Pair<String, File>>,
    outputFile: File,
    format: PackArchiveFormat,
    zipPassword: String? = null,
  ) {
    if (outputFile.exists()) outputFile.delete()
    when (format) {
      PackArchiveFormat.ZIP -> {
        val zip =
          if (!zipPassword.isNullOrEmpty()) {
            ZipFile(outputFile, zipPassword.toCharArray())
          } else {
            ZipFile(outputFile)
          }
        val params =
          ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
            if (!zipPassword.isNullOrEmpty()) {
              isEncryptFiles = true
              encryptionMethod = EncryptionMethod.AES
              aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
          }
        namedFiles.forEach { (entryName, file) ->
          val itemParams = ZipParameters(params).apply { fileNameInZip = entryName }
          file.inputStream().use { stream -> zip.addStream(stream, itemParams) }
        }
      }
      PackArchiveFormat.SEVEN_Z -> {
        SevenZOutputFile(outputFile).use { sevenZ ->
          namedFiles.forEach { (entryName, file) ->
            val entry = sevenZ.createArchiveEntry(file, entryName)
            sevenZ.putArchiveEntry(entry)
            file.inputStream().use { input ->
              val buf = ByteArray(BUFFER_SIZE)
              var read: Int
              while (input.read(buf).also { read = it } != -1) {
                sevenZ.write(buf, 0, read)
              }
            }
            sevenZ.closeArchiveEntry()
          }
          sevenZ.finish()
        }
      }
      PackArchiveFormat.TAR -> {
        writeTarEntries(BufferedOutputStream(outputFile.outputStream()), namedFiles)
      }
      PackArchiveFormat.TAR_GZ -> {
        GzipCompressorOutputStream(BufferedOutputStream(outputFile.outputStream())).use { gz ->
          writeTarEntries(gz, namedFiles)
        }
      }
      PackArchiveFormat.TAR_XZ -> {
        XZCompressorOutputStream(BufferedOutputStream(outputFile.outputStream())).use { xz ->
          writeTarEntries(xz, namedFiles)
        }
      }
    }
  }

  private fun writeTarEntries(
    baseOut: OutputStream,
    namedFiles: List<Pair<String, File>>,
  ) {
    TarArchiveOutputStream(baseOut).use { tar ->
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
      namedFiles.forEach { (entryName, file) ->
        val entry = TarArchiveEntry(file, entryName)
        tar.putArchiveEntry(entry)
        file.inputStream().use { it.copyTo(tar, BUFFER_SIZE) }
        tar.closeArchiveEntry()
      }
      tar.finish()
    }
  }

  private fun openTarStream(file: File, type: DetectedArchiveType): TarArchiveInputStream {
    val raw = BufferedInputStream(file.inputStream())
    return when (type) {
      DetectedArchiveType.TAR -> TarArchiveInputStream(raw)
      DetectedArchiveType.TAR_GZ -> TarArchiveInputStream(GzipCompressorInputStream(raw))
      DetectedArchiveType.TAR_XZ -> TarArchiveInputStream(XZCompressorInputStream(raw))
      else -> error("非 TAR 归档类型：$type")
    }
  }

  fun detectArchiveType(fileName: String): DetectedArchiveType {
    val lower = fileName.trim().lowercase(Locale.ROOT)
    return when {
      lower.endsWith(".rar") -> error("暂不支持 RAR 格式，当前支持 ZIP / 7Z / TAR / GZ / XZ")
      lower.endsWith(".zip") -> DetectedArchiveType.ZIP
      lower.endsWith(".7z") -> DetectedArchiveType.SEVEN_Z
      lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> DetectedArchiveType.TAR_GZ
      lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> DetectedArchiveType.TAR_XZ
      lower.endsWith(".tar") -> DetectedArchiveType.TAR
      lower.endsWith(".gz") -> DetectedArchiveType.GZ
      lower.endsWith(".xz") -> DetectedArchiveType.XZ
      else -> error("未识别的压缩格式（支持 ZIP / 7Z / TAR / GZ / XZ）")
    }
  }

  internal fun sanitizeRelativePath(rawPath: String?): String? {
    if (rawPath.isNullOrBlank()) return null
    val parts =
      rawPath
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "." }
    if (parts.isEmpty() || parts.any { it == ".." }) return null
    return parts.joinToString("/")
  }

  private fun createSafDocumentInTree(
    context: Context,
    treeUri: Uri,
    relativeDir: String,
    displayName: String,
  ): Uri {
    val resolver = context.contentResolver
    var currentParentDocUri =
      DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
      )

    if (relativeDir.isNotBlank()) {
      for (segment in relativeDir.split('/').filter { it.isNotBlank() }) {
        val existingFolder = findChildDocument(context, treeUri, currentParentDocUri, segment)
        currentParentDocUri =
          existingFolder
            ?: DocumentsContract.createDocument(
              resolver,
              currentParentDocUri,
              DocumentsContract.Document.MIME_TYPE_DIR,
              segment,
            )
            ?: error("无法创建子目录：$segment")
      }
    }

    return DocumentsContract.createDocument(
      resolver,
      currentParentDocUri,
      "application/octet-stream",
      displayName,
    ) ?: error("无法创建目标文件：$displayName")
  }

  private fun findChildDocument(
    context: Context,
    treeUri: Uri,
    parentDocUri: Uri,
    childName: String,
  ): Uri? {
    val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    return runCatching {
      context.contentResolver.query(
        childrenUri,
        arrayOf(
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ),
        null,
        null,
        null,
      )?.use { cursor ->
        while (cursor.moveToNext()) {
          if (cursor.getString(1) == childName) {
            return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
          }
        }
        null
      }
    }.getOrNull()
  }

  private fun copyUriToTempFile(context: Context, uri: Uri, prefix: String): File {
    val temp = File.createTempFile("${prefix}_", ".tmp", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
      temp.outputStream().use { output ->
        input.copyTo(output, BUFFER_SIZE)
        output.flush()
      }
    } ?: run {
      temp.delete()
      error("无法打开选中的压缩文件")
    }
    return temp
  }

  private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
      }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
  }
}
